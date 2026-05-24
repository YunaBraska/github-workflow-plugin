package com.github.yunabraska.githubworkflow.services;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IDE process facade that dispatches, polls, logs, and cancels a remote GitHub workflow run.
 */
public final class WorkflowRunProcessHandler extends ProcessHandler {

    private final WorkflowRunRequest request;
    private final WorkflowRunClient client;
    private final Project project;
    private final PollSettings pollSettings;
    private WorkflowRunJobConsole jobConsole = WorkflowRunJobConsole.none();
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicBoolean deleteRequested = new AtomicBoolean(false);
    private final AtomicBoolean rerunAllRequested = new AtomicBoolean(false);
    private final AtomicBoolean rerunFailedRequested = new AtomicBoolean(false);
    private final AtomicInteger artifactAvailability = new AtomicInteger(-1);
    private final AtomicLong runId = new AtomicLong(-1);
    private final AtomicReference<Future<?>> task = new AtomicReference<>();

    WorkflowRunProcessHandler(final Project project, final WorkflowRunRequest request, final WorkflowRunClient client) {
        this(project, request, client, PollSettings.defaults());
    }

    WorkflowRunProcessHandler(
            final Project project,
            final WorkflowRunRequest request,
            final WorkflowRunClient client,
            final Executor executor
    ) {
        this(project, request, client, PollSettings.defaults());
        this.jobConsole = new WorkflowRunConsoleTabs(project, executor, this);
    }

    WorkflowRunProcessHandler(
            final Project project,
            final WorkflowRunRequest request,
            final WorkflowRunClient client,
            final PollSettings pollSettings
    ) {
        this.project = project;
        this.request = request;
        this.client = client;
        this.pollSettings = pollSettings;
    }

    WorkflowRunProcessHandler(
            final Project project,
            final WorkflowRunRequest request,
            final WorkflowRunClient client,
            final PollSettings pollSettings,
            final WorkflowRunJobConsole jobConsole
    ) {
        this(project, request, client, pollSettings);
        this.jobConsole = jobConsole == null ? WorkflowRunJobConsole.none() : jobConsole;
    }

    @Override
    public void startNotify() {
        super.startNotify();
        WorkflowRunTracker.getInstance(project).register(request.workflowPath(), this);
        task.set(ApplicationManager.getApplication().executeOnPooledThread(this::runWorkflow));
    }

    @Override
    protected void destroyProcessImpl() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        final long id = runId.get();
        stdout(id > 0
                ? GitHubWorkflowBundle.message("workflow.run.cancel.requested", id) + "\n"
                : GitHubWorkflowBundle.message("workflow.run.stop.before.id") + "\n");
        final Future<?> runningTask = task.get();
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> cancelRemoteRun(id));
    }

    private void cancelRemoteRun(final long id) {
        if (id > 0) {
            try {
                final WorkflowRunClient.CancelResult result = client.cancel(request, id);
                stderr(GitHubWorkflowBundle.message("workflow.run.cancel.http", result.statusCode()) + "\n");
            } catch (final IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                stderr(GitHubWorkflowBundle.message("workflow.run.cancel.failed", exception.getMessage()) + "\n");
            }
        }
        terminate(1, "cancelled");
    }

    @Override
    protected void detachProcessImpl() {
        stopping.set(true);
        if (terminated.compareAndSet(false, true)) {
            WorkflowRunTracker.getInstance(project).unregister(request.workflowPath(), this);
            jobConsole.close();
            notifyProcessDetached();
        }
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
        return null;
    }

    private void runWorkflow() {
        try {
            stdout(dispatchMessage() + "\n");
            final WorkflowRunClient.DispatchResult dispatch = client.dispatch(request);
            final long id = resolveRunId(dispatch);
            if (id > 0) {
                runId.set(id);
            }
            final String conclusion = poll(id);
            final String terminalConclusion = stopping.get()
                    ? "cancelled"
                    : hasText(conclusion) ? conclusion : "success";
            terminate(successful(terminalConclusion) ? 0 : 1, terminalConclusion);
        } catch (final IOException | RuntimeException exception) {
            if (exception instanceof WorkflowRunClient.WorkflowRunHttpException httpException && httpException.accountActionRecommended()) {
                notifyAuthenticationHelp();
            }
            stderr(exception.getMessage() + "\n");
            terminate(1, "failure");
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!stopping.get()) {
                stderr(GitHubWorkflowBundle.message("workflow.run.interrupted") + "\n");
            }
            terminate(1, stopping.get() ? "cancelled" : "failure");
        }
    }

    private long resolveRunId(final WorkflowRunClient.DispatchResult dispatch) throws IOException, InterruptedException {
        if (hasText(dispatch.htmlUrl())) {
            stdout(GitHubWorkflowBundle.message("workflow.run.link", dispatch.htmlUrl()) + "\n");
        }
        if (dispatch.runId() > 0) {
            return dispatch.runId();
        }
        stdout(GitHubWorkflowBundle.message("workflow.run.discovery") + "\n");
        for (int attempt = 0; attempt < 12 && !stopping.get(); attempt++) {
            final var latest = client.latestRun(request);
            if (latest.isPresent()) {
                final WorkflowRunClient.RunStatus run = latest.get();
                if (hasText(run.htmlUrl())) {
                    stdout(GitHubWorkflowBundle.message("workflow.run.link", run.htmlUrl()) + "\n");
                }
                return run.runId();
            }
            TimeUnit.MILLISECONDS.sleep(pollSettings.runDiscoveryMillis());
        }
        stdout(GitHubWorkflowBundle.message("workflow.run.discovery.none") + "\n");
        return -1;
    }

    private String poll(final long id) throws IOException, InterruptedException {
        if (id <= 0) {
            return "";
        }
        WorkflowRunClient.RunStatus previous = new WorkflowRunClient.RunStatus(id, "", "", "");
        final Map<Long, JobLogState> jobLogs = new LinkedHashMap<>();
        while (!stopping.get()) {
            final WorkflowRunClient.RunStatus status = client.status(request, id);
            if (!status.status().equals(previous.status()) || !status.conclusion().equals(previous.conclusion())) {
                stdout(GitHubWorkflowBundle.message("workflow.run.status", status.status(), suffix(status.conclusion())) + "\n");
                previous = status;
            }
            if (status.completed()) {
                streamJobLogs(id, jobLogs, true);
                return hasText(status.conclusion()) ? status.conclusion() : "success";
            }
            streamJobLogs(id, jobLogs, false);
            TimeUnit.MILLISECONDS.sleep(pollSettings.statusPollMillis());
        }
        return "cancelled";
    }

    private void streamJobLogs(final long id, final Map<Long, JobLogState> jobLogs, final boolean finalPass) throws IOException, InterruptedException {
        final long now = System.currentTimeMillis();
        boolean changed = false;
        for (final WorkflowRunClient.JobStatus job : client.jobs(request, id)) {
            final JobLogState state = jobLogs.computeIfAbsent(job.id(), ignored -> new JobLogState());
            if (!job.status().equals(state.status) || !job.conclusion().equals(state.conclusion)) {
                printJobHeader(job, state);
                updateTiming(state, job, now);
                final String status = GitHubWorkflowBundle.message(
                        "workflow.run.job.main",
                        statePrefix(job),
                        job.name(),
                        job.status(),
                        suffix(job.conclusion()),
                        durationSuffix(state, now)
                ) + "\n";
                stdout(status);
                jobConsole.jobStatus(job, GitHubWorkflowBundle.message(
                        "workflow.run.job.status",
                        statePrefix(job),
                        job.status(),
                        suffix(job.conclusion()),
                        durationSuffix(state, now)
                ) + "\n");
                state.status = job.status();
                state.conclusion = job.conclusion();
                state.name = job.name();
                changed = true;
            }
            if (shouldFetchLog(job, state, now, finalPass)) {
                fetchJobLog(job, state, now, finalPass);
            }
        }
        if (changed) {
            stdout(overview(jobLogs, now));
        }
    }

    private boolean shouldFetchLog(
            final WorkflowRunClient.JobStatus job,
            final JobLogState state,
            final long now,
            final boolean finalPass
    ) {
        if (!"in_progress".equals(job.status()) && !"completed".equals(job.status())) {
            return false;
        }
        if (finalPass || "completed".equals(job.status())) {
            return !state.finalLogFetched;
        }
        if (now < state.nextLiveLogFetchMillis) {
            return false;
        }
        return now - state.lastLogFetchMillis >= pollSettings.logPollMillis();
    }

    private void fetchJobLog(
            final WorkflowRunClient.JobStatus job,
            final JobLogState state,
            final long now,
            final boolean finalPass
    ) throws InterruptedException {
        state.lastLogFetchMillis = now;
        try {
            final String logs = client.jobLogs(request, job.id());
            if (hasText(logs)) {
                printLogDelta(job, state, logs);
            }
            if (finalPass || "completed".equals(job.status())) {
                state.finalLogFetched = true;
            }
        } catch (final IOException exception) {
            if (shouldDeferLiveLogFailure(exception, finalPass)) {
                if (!state.liveLogNoticeShown) {
                    final String notice = GitHubWorkflowBundle.message("workflow.run.logs.later") + "\n";
                    if (!jobConsole.jobStatus(job, notice)) {
                        stdout(GitHubWorkflowBundle.message("workflow.run.job.logs.later", job.name(), notice));
                    }
                    state.liveLogNoticeShown = true;
                }
                state.nextLiveLogFetchMillis = now + pollSettings.liveLogFailureRetryMillis();
                return;
            }
            if (finalPass || !state.logErrorShown) {
                final String message = GitHubWorkflowBundle.message("workflow.run.log.failed", exception.getMessage()) + "\n";
                if (!jobConsole.jobStderr(job, message)) {
                    stderr(GitHubWorkflowBundle.message("workflow.run.log.failed.job", job.name(), exception.getMessage()) + "\n");
                }
                state.logErrorShown = true;
            }
        }
    }

    private void printLogDelta(final WorkflowRunClient.JobStatus job, final JobLogState state, final String logs) {
        final String text = logs.stripTrailing();
        if (text.length() <= state.printedLength) {
            return;
        }
        final String delta = text.substring(state.printedLength).stripLeading();
        if (hasText(delta)) {
            if (!jobConsole.jobLog(job, delta + "\n")) {
                final String rendered = state.fallbackRenderer.renderPlain(delta + "\n");
                final String fallbackText = "\n== " + job.name() + " ==\n" + rendered;
                stdout(fallbackText);
            }
        }
        state.printedLength = text.length();
    }

    private void printJobHeader(final WorkflowRunClient.JobStatus job, final JobLogState state) {
        if (state.headerPrinted) {
            return;
        }
        final String url = hasText(job.htmlUrl()) ? GitHubWorkflowBundle.message("workflow.run.job.url", job.htmlUrl()) + "\n" : "";
        final String header = GitHubWorkflowBundle.message("workflow.run.job.header", job.name()) + "\n" + url;
        stdout(header);
        jobConsole.jobStatus(job, header);
        state.headerPrinted = true;
    }

    private static void updateTiming(final JobLogState state, final WorkflowRunClient.JobStatus job, final long now) {
        if (state.firstSeenMillis == 0) {
            state.firstSeenMillis = now;
        }
        if ("in_progress".equals(job.status()) && state.startedMillis == 0) {
            state.startedMillis = now;
        }
        if ("completed".equals(job.status()) && state.completedMillis == 0) {
            state.completedMillis = now;
        }
    }

    private static String overview(final Map<Long, JobLogState> states, final long now) {
        final long total = states.size();
        final long done = states.values().stream().filter(state -> "completed".equals(state.status)).count();
        final long running = states.values().stream().filter(state -> "in_progress".equals(state.status)).count();
        final StringBuilder result = new StringBuilder()
                .append(GitHubWorkflowBundle.message("workflow.run.overview", progressBar(done, total), done, total, running))
                .append("\n");
        int index = 0;
        for (final JobLogState state : states.values()) {
            final boolean last = ++index == states.size();
            result.append(last ? "`-- " : "|-- ")
                    .append(statePrefix(state))
                    .append(" ")
                    .append(state.name)
                    .append(durationSuffix(state, now))
                    .append("\n");
        }
        return result.toString();
    }

    private static String progressBar(final long done, final long total) {
        if (total <= 0) {
            return "[----------]";
        }
        final int width = 10;
        final int filled = (int) Math.min(width, Math.max(0, done * width / total));
        return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "]";
    }

    private static String durationSuffix(final JobLogState state, final long now) {
        final long start = state.startedMillis > 0 ? state.startedMillis : state.firstSeenMillis;
        final long end = state.completedMillis > 0 ? state.completedMillis : now;
        if (start <= 0 || end < start) {
            return "";
        }
        return " " + formatDuration(end - start);
    }

    private static String formatDuration(final long millis) {
        final long seconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(millis));
        final long minutes = seconds / 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60);
    }

    private static String statePrefix(final WorkflowRunClient.JobStatus job) {
        return statePrefix(job.status(), job.conclusion());
    }

    private static String statePrefix(final JobLogState state) {
        return statePrefix(state.status, state.conclusion);
    }

    private static String statePrefix(final String status, final String conclusion) {
        if ("completed".equals(status)) {
            return successful(conclusion)
                    ? GitHubWorkflowBundle.message("workflow.run.state.ok")
                    : GitHubWorkflowBundle.message("workflow.run.state.fail");
        }
        if ("in_progress".equals(status)) {
            return GitHubWorkflowBundle.message("workflow.run.state.running");
        }
        return GitHubWorkflowBundle.message("workflow.run.state.waiting");
    }

    private static boolean successful(final String conclusion) {
        return "success".equals(conclusion) || "skipped".equals(conclusion) || "neutral".equals(conclusion);
    }

    private String dispatchMessage() {
        final String[] verbs = GitHubWorkflowBundle.message("workflow.run.dispatch.verbs").split("\\|");
        final String[] objects = GitHubWorkflowBundle.message("workflow.run.dispatch.objects").split("\\|");
        final String verb = verbs[ThreadLocalRandom.current().nextInt(verbs.length)];
        final String object = objects[ThreadLocalRandom.current().nextInt(objects.length)];
        return GitHubWorkflowBundle.message("workflow.run.dispatch", verb, object, request.workflowPath(), workflowUrl(), request.repositorySlug(), request.ref());
    }

    private String workflowUrl() {
        final String webUrl = request.apiUrl().equals("https://api.github.com")
                ? "https://github.com"
                : request.apiUrl().replaceFirst("/api/v3/?$", "");
        return " (" + webUrl + "/" + request.owner() + "/" + request.repo() + "/blob/" + request.ref() + "/" + request.workflowPath() + ")";
    }

    private static boolean shouldDeferLiveLogFailure(final IOException exception, final boolean finalPass) {
        return !finalPass && exception instanceof WorkflowRunClient.WorkflowRunHttpException;
    }

    private static String suffix(final String conclusion) {
        return conclusion == null || conclusion.isBlank() ? "" : "/" + conclusion;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    void deleteRemoteRun() {
        final long id = runId.get();
        if (id <= 0) {
            workflowStatus(GitHubWorkflowBundle.message("workflow.run.delete.noRun") + "\n", true);
            return;
        }
        if (!deleteRequested.compareAndSet(false, true)) {
            return;
        }
        workflowStatus(GitHubWorkflowBundle.message("workflow.run.delete.requested", id) + "\n", false);
        inBackground("workflow.run.delete.failed", exception -> {
            jobConsole.runDeleteFailed(id);
            deleteRequested.set(false);
        }, () -> {
            final WorkflowRunClient.DeleteResult result = client.delete(request, id);
            final String message = result.accepted()
                    ? GitHubWorkflowBundle.message("workflow.run.delete.done", id)
                    : GitHubWorkflowBundle.message("workflow.run.delete.http", result.statusCode());
            workflowStatus(message + "\n", !result.accepted());
            if (result.accepted()) {
                jobConsole.runDeleted(id);
            } else {
                jobConsole.runDeleteFailed(id);
                deleteRequested.set(false);
            }
        });
    }

    void rerunRemoteRun(final boolean failedOnly) {
        final long id = runId.get();
        if (id <= 0) {
            workflowStatus(GitHubWorkflowBundle.message("workflow.run.rerun.noRun") + "\n", true);
            return;
        }
        final AtomicBoolean gate = failedOnly ? rerunFailedRequested : rerunAllRequested;
        if (!gate.compareAndSet(false, true)) {
            return;
        }
        workflowStatus(GitHubWorkflowBundle.message(failedOnly
                ? "workflow.run.rerun.failed.requested"
                : "workflow.run.rerun.all.requested", id) + "\n", false);
        inBackground("workflow.run.rerun.failed", ignored -> gate.set(false), () -> {
            final WorkflowRunClient.RerunResult result = client.rerun(request, id, failedOnly);
            final String message = result.accepted()
                    ? GitHubWorkflowBundle.message(failedOnly
                            ? "workflow.run.rerun.failed.done"
                            : "workflow.run.rerun.all.done", id)
                    : GitHubWorkflowBundle.message("workflow.run.rerun.http", result.statusCode());
            workflowStatus(message + "\n", !result.accepted());
            gate.set(false);
        });
    }

    int artifactAvailability() {
        return artifactAvailability.get();
    }

    void refreshArtifactAvailability(final Consumer<Boolean> callback) {
        final long id = runId.get();
        if (id <= 0) {
            artifactAvailability.set(0);
            callback.accept(false);
            return;
        }
        final int known = artifactAvailability.get();
        if (known >= 0) {
            callback.accept(known == 1);
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean available = false;
            try {
                available = client.artifacts(request, id).stream().anyMatch(artifact -> !artifact.expired());
            } catch (final IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            artifactAvailability.set(available ? 1 : 0);
            callback.accept(available);
        });
    }

    void downloadJobLog(final long jobId, final String jobName) {
        final long id = runId.get();
        if (id <= 0 || jobId <= 0) {
            workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.noRun") + "\n", true);
            return;
        }
        workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.log.requested", jobName) + "\n", false);
        inBackground("workflow.run.download.failed", () -> {
            final String log = client.jobLogs(request, jobId);
            final Path file = WorkflowRunDownloads.writeJobLog(request, id, jobId, jobName, log);
            workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.log.done", file) + "\n", false);
            WorkflowRunDownloads.reveal(file);
        });
    }

    void downloadArtifacts() {
        final long id = runId.get();
        if (id <= 0) {
            workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.noRun") + "\n", true);
            return;
        }
        workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.artifacts.requested") + "\n", false);
        inBackground("workflow.run.download.failed", () -> {
            final List<WorkflowRunClient.ArtifactStatus> artifacts = client.artifacts(request, id);
            if (artifacts.isEmpty()) {
                artifactAvailability.set(0);
                workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.artifacts.empty") + "\n", false);
                return;
            }
            Path lastFile = null;
            int downloaded = 0;
            for (final WorkflowRunClient.ArtifactStatus artifact : artifacts) {
                if (artifact.expired()) {
                    workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.artifact.expired", artifact.name()) + "\n", false);
                    continue;
                }
                final byte[] zip = client.artifactZip(request, artifact.id());
                lastFile = WorkflowRunDownloads.writeArtifact(request, id, artifact, zip);
                downloaded++;
                workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.artifact.done", artifact.name(), lastFile) + "\n", false);
            }
            if (downloaded == 0) {
                artifactAvailability.set(0);
                workflowStatus(GitHubWorkflowBundle.message("workflow.run.download.artifacts.empty") + "\n", false);
                return;
            }
            artifactAvailability.set(1);
            if (lastFile != null) {
                WorkflowRunDownloads.reveal(lastFile.getParent());
            }
        });
    }

    private void inBackground(final String failureKey, final RemoteWork work) {
        inBackground(failureKey, ignored -> {
        }, work);
    }

    private void inBackground(final String failureKey, final Consumer<Exception> onFailure, final RemoteWork work) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                work.run();
            } catch (final IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                workflowStatus(GitHubWorkflowBundle.message(failureKey, exception.getMessage()) + "\n", true);
                onFailure.accept(exception);
            }
        });
    }

    private void workflowStatus(final String text, final boolean error) {
        jobConsole.workflowStatus(text, error);
        if (!isProcessTerminated()) {
            notifyTextAvailable(text, error ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
        }
    }

    private void terminate(final int exitCode, final String conclusion) {
        if (terminated.compareAndSet(false, true)) {
            WorkflowRunTracker.getInstance(project).unregister(request.workflowPath(), this);
            jobConsole.runFinished(runId.get(), conclusion);
            jobConsole.close();
            notifyProcessTerminated(exitCode);
        }
    }

    private void notifyAuthenticationHelp() {
        final var notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("GitHub Workflow")
                .createNotification(
                        GitHubWorkflowBundle.message("workflow.run.notification.auth", GitHubRequestAuthorizations.settingsHint()),
                        NotificationType.WARNING
                );
        notification.addAction(NotificationAction.createSimple(GitHubWorkflowBundle.message("workflow.run.notification.openSettings"), () ->
                ApplicationManager.getApplication().invokeLater(() ->
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, "GitHub"))));
        notification.notify(project);
    }

    private void stdout(final String text) {
        notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
    }

    private void stderr(final String text) {
        notifyTextAvailable(text, ProcessOutputTypes.STDERR);
    }

    record PollSettings(long statusPollMillis, long logPollMillis, long runDiscoveryMillis, long liveLogFailureRetryMillis) {

        PollSettings(final long statusPollMillis, final long logPollMillis, final long runDiscoveryMillis) {
            this(statusPollMillis, logPollMillis, runDiscoveryMillis, Math.max(logPollMillis, 60_000));
        }

        private static PollSettings defaults() {
            return new PollSettings(10_000, 30_000, 2_000, 60_000);
        }
    }

    private static final class JobLogState {
        private String name = "job";
        private String status = "";
        private String conclusion = "";
        private long firstSeenMillis = 0;
        private long startedMillis = 0;
        private long completedMillis = 0;
        private int printedLength = 0;
        private long lastLogFetchMillis = 0;
        private long nextLiveLogFetchMillis = 0;
        private final WorkflowRunLogRenderer fallbackRenderer = new WorkflowRunLogRenderer();
        private boolean finalLogFetched = false;
        private boolean logErrorShown = false;
        private boolean headerPrinted = false;
        private boolean liveLogNoticeShown = false;
    }

    private interface RemoteWork {
        void run() throws IOException, InterruptedException;
    }
}
