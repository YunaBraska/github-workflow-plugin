package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunProcessHandlerTest extends BasePlatformTestCase {

    public void testProcessStreamsJobLogDeltasWithoutAuthStrategyNoise() throws Exception {
        final AtomicInteger statusCalls = new AtomicInteger(0);
        final AtomicInteger jobCalls = new AtomicInteger(0);
        final AtomicInteger logCalls = new AtomicInteger(0);
        final WorkflowRun client = new WorkflowRun(
                request -> responseFor(request, statusCalls, jobCalls, logCalls),
                request -> List.of(RemoteActionProviders.Authorizations.Authorization.anonymous())
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10)
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                    output.append(event.getText());
                }
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output.toString())
                .contains(".github/workflows/test.yml")
                .contains("acme/tool")
                .contains("main")
                .contains("Status: in_progress")
                .contains("Status: completed/success")
                .contains("URL: job-url")
                .contains("Job: [RUN] build [in_progress")
                .contains("Job: [OK] build [completed/success")
                .contains("Workflow run [----------] 0/1 done, 1 running")
                .contains("Workflow run [##########] 1/1 done, 0 running")
                .contains("first line")
                .contains("second line")
                .doesNotContain("Trying IDE GitHub accounts");
        assertThat(logCalls.get()).isLessThanOrEqualTo(2);
    }

    public void testDestroyCancelsRemoteRunAndTerminates() throws Exception {
        final CountDownLatch statusSeen = new CountDownLatch(1);
        final CountDownLatch cancelSeen = new CountDownLatch(1);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> cancellationResponseFor(request, statusSeen, cancelSeen),
                request -> List.of(RemoteActionProviders.Authorizations.Authorization.anonymous())
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(1_000, 1_000, 10),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                output.append(event.getText());
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();
        assertThat(statusSeen.await(5, TimeUnit.SECONDS)).isTrue();
        handler.destroyProcess();

        assertThat(cancelSeen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output.toString()).contains("Cancel requested: 42.");
        assertThat(jobConsole.finished()).containsExactly("42:cancelled");
    }

    public void testDeleteRemoteRunUsesCompletedRunIdAndReportsToWorkflowConsole() throws Exception {
        final CountDownLatch deleteSeen = new CountDownLatch(1);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> completedRunWithDeleteResponseFor(request, deleteSeen),
                request -> List.of(RemoteActionProviders.Authorizations.Authorization.anonymous())
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        handler.deleteRemoteRun();

        assertThat(deleteSeen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(waitForWorkflowOutput(jobConsole, "Run 42 deleted.")).isTrue();
        assertThat(waitForDeletedRun(jobConsole, 42L)).isTrue();
        assertThat(jobConsole.workflowOutput()).contains("Deleting run 42.", "Run 42 deleted.");
        assertThat(jobConsole.deleted()).containsExactly(42L);
    }

    public void testRerunRemoteRunUsesCompletedRunIdAndReportsToWorkflowConsole() throws Exception {
        final CountDownLatch rerunAllSeen = new CountDownLatch(1);
        final CountDownLatch rerunFailedSeen = new CountDownLatch(1);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> completedRunWithRerunResponseFor(request, rerunAllSeen, rerunFailedSeen),
                request -> List.of(RemoteActionProviders.Authorizations.Authorization.anonymous())
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        handler.rerunRemoteRun(false);
        handler.rerunRemoteRun(true);

        assertThat(rerunAllSeen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rerunFailedSeen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(waitForWorkflowOutput(jobConsole, "Rerun queued: 42.")).isTrue();
        assertThat(waitForWorkflowOutput(jobConsole, "Failed jobs queued: 42.")).isTrue();
    }

    public void testProcessRoutesEachJobLogToSeparateJobConsole() throws Exception {
        final AtomicInteger statusCalls = new AtomicInteger(0);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> multiJobResponseFor(request, statusCalls),
                request -> List.of(RemoteActionProviders.Authorizations.Authorization.anonymous())
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder mainOutput = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                    mainOutput.append(event.getText());
                }
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mainOutput.toString())
                .contains("Job: [OK] Node Test / test (ubuntu-latest) [completed/success")
                .contains("Job: [OK] Node Test / test (windows-latest) [completed/success")
                .doesNotContain("ubuntu log")
                .doesNotContain("windows log");
        assertThat(jobConsole.output(100)).contains("URL: ubuntu-url", "Status: [OK] completed/success", "0001 | ubuntu log");
        assertThat(jobConsole.output(200)).contains("URL: windows-url", "Status: [OK] completed/success", "0001 | windows log");
        assertThat(jobConsole.output(100)).doesNotContain("windows log");
        assertThat(jobConsole.output(200)).doesNotContain("ubuntu log");
    }

    public void testProcessDefersLiveLogPermissionFailuresUntilFinalLogIsAvailable() throws Exception {
        final AtomicInteger statusCalls = new AtomicInteger(0);
        final AtomicInteger jobCalls = new AtomicInteger(0);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> adminLiveLogResponseFor(request, statusCalls, jobCalls),
                request -> List.of(new RemoteActionProviders.Authorizations.Authorization("github.com", "Bearer account-token"))
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                output.append(event.getText());
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output.toString()).doesNotContain("Log download failed");
        assertThat(jobConsole.output(100))
                .contains("Logs will appear when GitHub publishes them.", "Status: [RUN] in_progress", "Status: [OK] completed/success", "final log after completion")
                .doesNotContain("Log download failed");
    }

    public void testProcessFetchesCompletedJobLogAfterLiveFailureBeforeRunCompletes() throws Exception {
        final AtomicInteger statusCalls = new AtomicInteger(0);
        final AtomicInteger jobCalls = new AtomicInteger(0);
        final AtomicInteger completedLogFetchedAtStatusCall = new AtomicInteger(-1);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> completedJobLogAfterLiveFailureResponseFor(request, statusCalls, jobCalls, completedLogFetchedAtStatusCall),
                request -> List.of(new RemoteActionProviders.Authorizations.Authorization("github.com", "Bearer account-token"))
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10, 60_000),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                output.append(event.getText());
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(completedLogFetchedAtStatusCall.get()).isEqualTo(2);
        assertThat(output.toString()).doesNotContain("Log download failed");
        assertThat(jobConsole.output(100))
                .contains("Status: [RUN] in_progress", "Status: [OK] completed/success", "job log before run completed")
                .doesNotContain("Log download failed");
    }

    public void testProcessUsesEnterpriseWorkflowUrlInDispatchMessage() throws Exception {
        final AtomicInteger statusCalls = new AtomicInteger(0);
        final AtomicInteger jobCalls = new AtomicInteger(0);
        final AtomicInteger logCalls = new AtomicInteger(0);
        final WorkflowRun client = new WorkflowRun(
                request -> responseFor(request, statusCalls, jobCalls, logCalls),
                request -> List.of(RemoteActionProviders.Authorizations.Authorization.anonymous())
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://github.acme.test/api/v3",
                "tools",
                "workflow-box",
                ".github/workflows/test.yml",
                "feature/live-logs",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(10, 10, 10)
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                output.append(event.getText());
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output.toString())
                .contains("https://github.acme.test/tools/workflow-box/blob/feature/live-logs/.github/workflows/test.yml")
                .contains("tools/workflow-box")
                .contains("feature/live-logs");
    }

    public void testProcessRetriesLiveLogAfterDeferredHttpFailure() throws Exception {
        final AtomicInteger statusCalls = new AtomicInteger(0);
        final AtomicInteger logCalls = new AtomicInteger(0);
        final CapturingJobConsole jobConsole = new CapturingJobConsole();
        final WorkflowRun client = new WorkflowRun(
                request -> retryableLiveLogResponseFor(request, statusCalls, logCalls),
                request -> List.of(new RemoteActionProviders.Authorizations.Authorization("github.com", "Bearer account-token"))
        );
        final WorkflowRun.Request request = new WorkflowRun.Request(
                "https://api.github.test",
                "acme",
                "tool",
                ".github/workflows/test.yml",
                "main",
                Map.of(),
                ""
        );
        final WorkflowRunProcessHandler handler = new WorkflowRunProcessHandler(
                getProject(),
                request,
                client,
                new WorkflowRunProcessHandler.PollSettings(20, 1, 10, 1),
                jobConsole
        );
        final CountDownLatch terminated = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final com.intellij.openapi.util.Key outputType) {
                output.append(event.getText());
            }

            @Override
            public void processTerminated(@NotNull final ProcessEvent event) {
                terminated.countDown();
            }
        });

        handler.startNotify();

        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(logCalls.get()).isGreaterThanOrEqualTo(2);
        assertThat(output.toString()).doesNotContain("Log download failed");
        assertThat(jobConsole.output(100))
                .contains("live log after retry")
                .doesNotContain("Log download failed");
    }

    private static HttpResponse<String> responseFor(
            final HttpRequest request,
            final AtomicInteger statusCalls,
            final AtomicInteger jobCalls,
            final AtomicInteger logCalls
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            final int call = jobCalls.incrementAndGet();
            final String status = call == 1 ? "in_progress" : "completed";
            final String conclusion = call == 1 ? "" : ",\"conclusion\":\"success\"";
            return response(request, 200, "{\"jobs\":[{\"id\":100,\"name\":\"build\",\"status\":\"" + status + "\"" + conclusion + ",\"html_url\":\"job-url\"}]}");
        }
        if (path.endsWith("/jobs/100/logs")) {
            final int call = logCalls.incrementAndGet();
            return response(request, 200, call == 1 ? "first line\n" : "first line\nsecond line\n");
        }
        if (path.endsWith("/runs/42")) {
            final int call = statusCalls.incrementAndGet();
            return response(request, 200, call == 1
                    ? "{\"id\":42,\"status\":\"in_progress\",\"html_url\":\"html-run\"}"
                    : "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> multiJobResponseFor(
            final HttpRequest request,
            final AtomicInteger statusCalls
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            return response(request, 200, """
                    {"jobs":[
                      {"id":100,"name":"Node Test / test (ubuntu-latest)","status":"completed","conclusion":"success","html_url":"ubuntu-url"},
                      {"id":200,"name":"Node Test / test (windows-latest)","status":"completed","conclusion":"success","html_url":"windows-url"}
                    ]}
                    """);
        }
        if (path.endsWith("/jobs/100/logs")) {
            return response(request, 200, "ubuntu log\n");
        }
        if (path.endsWith("/jobs/200/logs")) {
            return response(request, 200, "windows log\n");
        }
        if (path.endsWith("/runs/42")) {
            statusCalls.incrementAndGet();
            return response(request, 200, "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> cancellationResponseFor(
            final HttpRequest request,
            final CountDownLatch statusSeen,
            final CountDownLatch cancelSeen
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/cancel")) {
            cancelSeen.countDown();
            return response(request, 202, "{}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            return response(request, 200, "{\"jobs\":[]}");
        }
        if (path.endsWith("/runs/42")) {
            statusSeen.countDown();
            return response(request, 200, "{\"id\":42,\"status\":\"in_progress\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> completedRunWithDeleteResponseFor(
            final HttpRequest request,
            final CountDownLatch deleteSeen
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if ("DELETE".equals(request.method()) && path.endsWith("/runs/42")) {
            deleteSeen.countDown();
            return response(request, 204, "");
        }
        if (path.endsWith("/runs/42/jobs")) {
            return response(request, 200, "{\"jobs\":[]}");
        }
        if (path.endsWith("/runs/42")) {
            return response(request, 200, "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> completedRunWithRerunResponseFor(
            final HttpRequest request,
            final CountDownLatch rerunAllSeen,
            final CountDownLatch rerunFailedSeen
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/rerun")) {
            rerunAllSeen.countDown();
            return response(request, 201, "{}");
        }
        if (path.endsWith("/runs/42/rerun-failed-jobs")) {
            rerunFailedSeen.countDown();
            return response(request, 201, "{}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            return response(request, 200, "{\"jobs\":[]}");
        }
        if (path.endsWith("/runs/42")) {
            return response(request, 200, "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> adminLiveLogResponseFor(
            final HttpRequest request,
            final AtomicInteger statusCalls,
            final AtomicInteger jobCalls
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            final int call = jobCalls.incrementAndGet();
            final String status = call == 1 ? "in_progress" : "completed";
            final String conclusion = call == 1 ? "" : ",\"conclusion\":\"success\"";
            return response(request, 200, "{\"jobs\":[{\"id\":100,\"name\":\"build\",\"status\":\"" + status + "\"" + conclusion + ",\"html_url\":\"job-url\"}]}");
        }
        if (path.endsWith("/jobs/100/logs")) {
            return jobCalls.get() == 1
                    ? response(request, 403, "{\"message\":\"Must have admin rights to Repository.\"}")
                    : response(request, 200, "final log after completion\n");
        }
        if (path.endsWith("/runs/42")) {
            final int call = statusCalls.incrementAndGet();
            return response(request, 200, call == 1
                    ? "{\"id\":42,\"status\":\"in_progress\",\"html_url\":\"html-run\"}"
                    : "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> completedJobLogAfterLiveFailureResponseFor(
            final HttpRequest request,
            final AtomicInteger statusCalls,
            final AtomicInteger jobCalls,
            final AtomicInteger completedLogFetchedAtStatusCall
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            final int call = jobCalls.incrementAndGet();
            final String status = call == 1 ? "in_progress" : "completed";
            final String conclusion = call == 1 ? "" : ",\"conclusion\":\"success\"";
            return response(request, 200, "{\"jobs\":[{\"id\":100,\"name\":\"build\",\"status\":\"" + status + "\"" + conclusion + "}]}");
        }
        if (path.endsWith("/jobs/100/logs")) {
            if (jobCalls.get() == 1) {
                return response(request, 403, "{\"message\":\"Must have admin rights to Repository.\"}");
            }
            completedLogFetchedAtStatusCall.compareAndSet(-1, statusCalls.get());
            return response(request, 200, "job log before run completed\n");
        }
        if (path.endsWith("/runs/42")) {
            final int call = statusCalls.incrementAndGet();
            return response(request, 200, call < 3
                    ? "{\"id\":42,\"status\":\"in_progress\",\"html_url\":\"html-run\"}"
                    : "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static HttpResponse<String> retryableLiveLogResponseFor(
            final HttpRequest request,
            final AtomicInteger statusCalls,
            final AtomicInteger logCalls
    ) {
        final String path = request.uri().getPath();
        if (path.endsWith("/dispatches")) {
            return response(request, 200, "{\"workflow_run_id\":42,\"html_url\":\"html-run\"}");
        }
        if (path.endsWith("/runs/42/jobs")) {
            final boolean completed = statusCalls.get() >= 4;
            final String status = completed ? "completed" : "in_progress";
            final String conclusion = completed ? ",\"conclusion\":\"success\"" : "";
            return response(request, 200, "{\"jobs\":[{\"id\":100,\"name\":\"build\",\"status\":\"" + status + "\"" + conclusion + "}]}");
        }
        if (path.endsWith("/jobs/100/logs")) {
            final int call = logCalls.incrementAndGet();
            if (call == 1) {
                return response(request, 504, "<html>GitHub timeout</html>");
            }
            return response(request, 200, call == 2
                    ? "live log after retry\n"
                    : "live log after retry\nfinal log\n");
        }
        if (path.endsWith("/runs/42")) {
            final int call = statusCalls.incrementAndGet();
            return response(request, 200, call < 4
                    ? "{\"id\":42,\"status\":\"in_progress\",\"html_url\":\"html-run\"}"
                    : "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
        }
        return response(request, 404, "{}");
    }

    private static Response response(final HttpRequest request, final int status, final String body) {
        return new Response(request, status, body);
    }

    private static boolean waitForWorkflowOutput(final CapturingJobConsole console, final String text) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (console.workflowOutput().contains(text)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }

    private static boolean waitForDeletedRun(final CapturingJobConsole console, final long runId) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (console.deleted().contains(runId)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }

    private record Response(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (left, right) -> true);
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }
    }

    private static final class CapturingJobConsole implements WorkflowRunProcessHandler.JobConsole {
        private final Object lock = new Object();
        private final Map<Long, StringBuilder> output = new HashMap<>();
        private final StringBuilder workflowOutput = new StringBuilder();
        private final java.util.ArrayList<String> finished = new java.util.ArrayList<>();
        private final java.util.ArrayList<Long> deleted = new java.util.ArrayList<>();

        @Override
        public boolean jobStatus(final WorkflowRun.JobStatus job, final String text) {
            append(job, text);
            return true;
        }

        @Override
        public boolean jobStdout(final WorkflowRun.JobStatus job, final String text) {
            append(job, text);
            return true;
        }

        @Override
        public boolean jobStderr(final WorkflowRun.JobStatus job, final String text) {
            append(job, text);
            return true;
        }

        @Override
        public boolean jobLog(final WorkflowRun.JobStatus job, final String text) {
            WorkflowRunView.LogRenderer.renderOnce(text).forEach(segment -> append(job, segment.text()));
            return true;
        }

        @Override
        public void workflowStatus(final String text, final boolean error) {
            synchronized (lock) {
                workflowOutput.append(text);
            }
        }

        @Override
        public void runFinished(final long runId, final String conclusion) {
            synchronized (lock) {
                finished.add(runId + ":" + conclusion);
            }
        }

        @Override
        public void runDeleted(final long runId) {
            synchronized (lock) {
                deleted.add(runId);
            }
        }

        private void append(final WorkflowRun.JobStatus job, final String text) {
            synchronized (lock) {
                output.computeIfAbsent(job.id(), ignored -> new StringBuilder()).append(text);
            }
        }

        private String output(final long jobId) {
            synchronized (lock) {
                return output.getOrDefault(jobId, new StringBuilder()).toString();
            }
        }

        private String workflowOutput() {
            synchronized (lock) {
                return workflowOutput.toString();
            }
        }

        private List<String> finished() {
            synchronized (lock) {
                return List.copyOf(finished);
            }
        }

        private List<Long> deleted() {
            synchronized (lock) {
                return List.copyOf(deleted);
            }
        }
    }
}
