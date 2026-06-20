package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.git.WorkflowLocation;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Small GitHub Actions REST client for workflow dispatch, status polling, cancellation, and logs.
 */
public class WorkflowRun {

    private static final String API_VERSION = "2026-03-10";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpTransport transport;
    private final AuthorizationProvider authorizationProvider;
    private final ConcurrentMap<String, RemoteActionProviders.Authorizations.Authorization> successfulAuthorizations = new ConcurrentHashMap<>();

    public WorkflowRun() {
        this((Project) null);
    }

    public WorkflowRun(final Project project) {
        this(new JdkHttpTransport(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()), request -> RemoteActionProviders.Authorizations.forWorkflowRun(
                request.apiUrl(),
                request.workflowPath(),
                request.tokenEnvVar(),
                project
        ));
    }

    WorkflowRun(final HttpTransport transport) {
        this(transport, request -> RemoteActionProviders.Authorizations.forWorkflowRun(
                request.apiUrl(),
                request.workflowPath(),
                request.tokenEnvVar(),
                null
        ));
    }

    WorkflowRun(final HttpTransport transport, final AuthorizationProvider authorizationProvider) {
        this.transport = transport;
        this.authorizationProvider = authorizationProvider;
    }

    public DispatchResult dispatch(final Request request) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "POST",
                dispatchUrl(request),
                dispatchBody(request),
                "GitHub workflow dispatch"
        );
        final JsonObject json = parseObject(response.body());
        return new DispatchResult(
                longValue(json, "workflow_run_id").orElse(-1L),
                stringValue(json, "run_url").orElse(""),
                stringValue(json, "html_url").orElse("")
        );
    }

    public RunStatus status(final Request request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                runUrl(request, runId),
                "",
                "GitHub workflow status"
        );
        final JsonObject json = parseObject(response.body());
        return runStatus(json, runId);
    }

    public CancelResult cancel(final Request request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "POST",
                runUrl(request, runId) + "/cancel",
                "",
                "GitHub workflow cancel"
        );
        return new CancelResult(response.statusCode(), accepted(response));
    }

    /**
     * Requests GitHub to re-run a completed workflow run.
     *
     * @param request workflow repository and authorization context
     * @param runId GitHub Actions run id
     * @param failedOnly whether only failed jobs should be re-run
     * @return HTTP status and whether GitHub accepted the re-run
     * @throws IOException when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public RerunResult rerun(
            final Request request,
            final long runId,
            final boolean failedOnly
    ) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "POST",
                runUrl(request, runId) + (failedOnly ? "/rerun-failed-jobs" : "/rerun"),
                "",
                failedOnly ? "GitHub workflow failed jobs rerun" : "GitHub workflow rerun"
        );
        return new RerunResult(response.statusCode(), accepted(response));
    }

    /**
     * Deletes one completed workflow run from the remote repository.
     *
     * @param request workflow repository and authorization context
     * @param runId GitHub Actions run id
     * @return HTTP status and whether GitHub accepted the deletion
     * @throws IOException when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public DeleteResult delete(final Request request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "DELETE",
                runUrl(request, runId),
                "",
                "GitHub workflow run delete"
        );
        return new DeleteResult(response.statusCode(), accepted(response));
    }

    /**
     * Finds the most likely run created by a workflow dispatch when the dispatch response did not include a run id.
     *
     * @param request workflow repository and branch context
     * @return the closest matching workflow dispatch run, or an empty result when no safe candidate exists
     * @throws IOException when the remote API rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public Optional<RunStatus> latestRun(final Request request) throws IOException, InterruptedException {
        return latestRun(request, Instant.now());
    }

    /**
     * Finds the most likely run created near one dispatch timestamp.
     *
     * @param request workflow repository and branch context
     * @param dispatchTime local timestamp captured immediately before dispatching the workflow
     * @return the closest same-workflow run near the dispatch timestamp, or an empty result when no candidate fits
     * @throws IOException when the remote API rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public Optional<RunStatus> latestRun(final Request request, final Instant dispatchTime) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                latestRunsUrl(request),
                "",
                "GitHub workflow run discovery"
        );
        final JsonObject json = parseObject(response.body());
        final Instant baseTime = Optional.ofNullable(dispatchTime).orElse(Instant.EPOCH);
        final Instant earliest = baseTime.minusSeconds(30);
        return objects(json, "workflow_runs")
                .map(run -> runCandidate(request, run))
                .filter(candidate -> candidate.status().runId() >= 0)
                .filter(RunCandidate::workflowMatches)
                .filter(candidate -> candidate.timestamp().map(timestamp -> !timestamp.isBefore(earliest)).orElse(true))
                .min(runCandidateComparator(baseTime))
                .map(RunCandidate::status);
    }

    public String logs(final Request request, final long runId) throws IOException, InterruptedException {
        final StringBuilder result = new StringBuilder();
        for (final JobStatus job : jobs(request, runId)) {
            result.append("== ").append(job.name()).append(" [").append(job.status()).append(resultSuffix(job.conclusion())).append("]\n");
            final String logs = jobLogs(request, job.id());
            if (hasText(logs)) {
                result.append(logs.stripTrailing()).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Lists the artifacts produced by one workflow run.
     *
     * @param request workflow repository and authorization context
     * @param runId GitHub Actions run id
     * @return immutable list of artifacts known to GitHub for the run
     * @throws IOException when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public List<ArtifactStatus> artifacts(final Request request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                runUrl(request, runId) + "/artifacts?per_page=100",
                "",
                "GitHub workflow artifacts"
        );
        final JsonObject json = parseObject(response.body());
        return objects(json, "artifacts")
                .map(WorkflowRun::artifactStatus)
                .filter(artifact -> artifact.id() >= 0)
                .toList();
    }

    /**
     * Downloads one workflow artifact archive as bytes.
     *
     * @param request workflow repository and authorization context
     * @param artifactId GitHub Actions artifact id
     * @return zip archive bytes
     * @throws IOException when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public byte[] artifactZip(final Request request, final long artifactId) throws IOException, InterruptedException {
        final HttpResponse<byte[]> response = sendBytes(
                request,
                "GET",
                request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/artifacts/" + artifactId + "/zip",
                "",
                "GitHub workflow artifact download"
        );
        return response.body();
    }

    public List<JobStatus> jobs(final Request request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                runUrl(request, runId) + "/jobs",
                "",
                "GitHub workflow jobs"
        );
        final JsonObject json = parseObject(response.body());
        return objects(json, "jobs")
                .map(WorkflowRun::jobStatus)
                .filter(job -> job.id() >= 0)
                .toList();
    }

    public String jobLogs(final Request request, final long jobId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/jobs/" + jobId + "/logs",
                "",
                "GitHub workflow job logs"
        );
        return response.body();
    }

    private HttpResponse<String> send(
            final Request workflow,
            final String method,
            final String url,
            final String body,
            final String operation
    ) throws IOException, InterruptedException {
        return sendWithAuthorizations(workflow, method, url, body, operation, transport::send, WorkflowRun::failure, HttpResponse::body);
    }

    private HttpResponse<byte[]> sendBytes(
            final Request workflow,
            final String method,
            final String url,
            final String body,
            final String operation
    ) throws IOException, InterruptedException {
        return sendWithAuthorizations(
                workflow,
                method,
                url,
                body,
                operation,
                transport::sendBytes,
                WorkflowRun::failureBytes,
                response -> new String(Optional.ofNullable(response.body()).orElseGet(() -> new byte[0]), StandardCharsets.UTF_8)
        );
    }

    private <T> HttpResponse<T> sendWithAuthorizations(
            final Request workflow,
            final String method,
            final String url,
            final String body,
            final String operation,
            final ResponseSender<T> sender,
            final FailureFactory<T> failureFactory,
            final Function<HttpResponse<T>, String> bodyText
    ) throws IOException, InterruptedException {
        WorkflowRunHttpException lastFailure = null;
        boolean authenticatedRateLimitFailure = false;
        final String authorizationCacheKey = authorizationCacheKey(workflow);
        for (final RemoteActionProviders.Authorizations.Authorization authorization : authorizations(workflow, authorizationCacheKey)) {
            if (!authorization.authenticated() && authenticatedRateLimitFailure) {
                break;
            }
            final HttpResponse<T> response = sender.send(request(workflow, method, url, body, authorization));
            if (accepted(response)) {
                if (authorization.authenticated()) {
                    successfulAuthorizations.put(authorizationCacheKey, authorization);
                }
                return response;
            }
            lastFailure = failureFactory.failure(workflow, operation, response);
            if (authorization.authenticated() && rateLimitExceeded(response.statusCode(), response.headers(), bodyText.apply(response))) {
                authenticatedRateLimitFailure = true;
            }
            if (!shouldTryNextAuthorization(response.statusCode())) {
                throw lastFailure;
            }
        }
        throw lastFailure == null
                ? new IOException(operation + " failed: no authorization candidates were available.")
                : lastFailure;
    }

    private static boolean accepted(final HttpResponse<?> response) {
        return response.statusCode() / 100 == 2;
    }

    private List<RemoteActionProviders.Authorizations.Authorization> authorizations(
            final Request workflow,
            final String authorizationCacheKey
    ) {
        final List<RemoteActionProviders.Authorizations.Authorization> result = new ArrayList<>();
        Optional.ofNullable(successfulAuthorizations.get(authorizationCacheKey)).ifPresent(result::add);
        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = authorizationProvider.authorizations(workflow);
        if (authorizations == null || authorizations.isEmpty()) {
            result.add(RemoteActionProviders.Authorizations.Authorization.anonymous());
        } else {
            result.addAll(authorizations);
        }
        return result.stream()
                .filter(WorkflowRun::knownAuthorization)
                .distinct()
                .toList();
    }

    private static boolean knownAuthorization(final RemoteActionProviders.Authorizations.Authorization authorization) {
        return authorization != null;
    }

    private static HttpRequest request(
            final Request workflow,
            final String method,
            final String url,
            final String body,
            final RemoteActionProviders.Authorizations.Authorization authorization
    ) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "GitHub-Workflow-Plugin");
        if (server(workflow).isGitea()) {
            builder.header("Accept", "application/json");
        } else {
            builder.header("Accept", "application/vnd.github+json");
            builder.header("X-GitHub-Api-Version", API_VERSION);
        }
        if (authorization.authenticated()) {
            builder.header("Authorization", authorization.authorizationHeader());
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private static WorkflowRunHttpException failure(final Request request, final String operation, final HttpResponse<String> response) {
        return failure(request, operation, response.statusCode(), response.headers(), response.body());
    }

    private static WorkflowRunHttpException failureBytes(final Request request, final String operation, final HttpResponse<byte[]> response) {
        final String body = new String(Optional.ofNullable(response.body()).orElseGet(() -> new byte[0]), StandardCharsets.UTF_8);
        return failure(request, operation, response.statusCode(), response.headers(), body);
    }

    private static WorkflowRunHttpException failure(
            final Request request,
            final String operation,
            final int statusCode,
            final HttpHeaders headers,
            final String body
    ) {
        final boolean accountActionRecommended = needsAccountAction(statusCode, headers, body);
        final RemoteActionProviders.Server server = RemoteActionProviders.Server.fromWorkflowRun(
                request.apiUrl(),
                request.workflowPath(),
                request.tokenEnvVar()
        );
        final String hint = accountActionRecommended
                ? "\n" + GitHubWorkflowBundle.message("workflow.run.auth.add", RemoteActionProviders.Authorizations.settingsHint(server))
                : "";
        final String summary = responseSummary(statusCode, headers, body);
        return new WorkflowRunHttpException(
                operation + " failed with HTTP " + statusCode + (summary.isEmpty() ? "" : ": " + summary) + hint,
                statusCode,
                body,
                accountActionRecommended,
                server.isGitea() ? "github.workflow.gitea.settings" : "GitHub"
        );
    }

    private static String responseSummary(final HttpResponse<String> response) {
        return responseSummary(response.statusCode(), response.headers(), response.body());
    }

    private static String responseSummary(final int statusCode, final HttpHeaders headers, final String responseBody) {
        final String body = Optional.ofNullable(responseBody).orElse("").strip();
        if (body.isEmpty()) {
            return "";
        }
        final String contentType = headers
                .firstValue("Content-Type")
                .orElse("")
                .toLowerCase();
        if (contentType.contains("text/html") || body.startsWith("<!DOCTYPE") || body.startsWith("<html")) {
            return GitHubWorkflowBundle.message("workflow.run.error.html");
        }
        final String singleLine = body.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ");
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 497) + "...";
    }

    private static boolean shouldTryNextAuthorization(final int statusCode) {
        return statusCode == 401 || statusCode == 403 || statusCode == 404 || statusCode == 429;
    }

    private static boolean rateLimitExceeded(final int statusCode, final HttpHeaders headers, final String body) {
        if (statusCode != 403 && statusCode != 429) {
            return false;
        }
        if (headers
                .firstValue("x-ratelimit-remaining")
                .map(String::trim)
                .filter("0"::equals)
                .isPresent()) {
            return true;
        }
        return Optional.ofNullable(body)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> value.contains("rate limit"))
                .isPresent();
    }

    private static boolean needsAccountAction(final HttpResponse<String> response) {
        return needsAccountAction(response.statusCode(), response.headers(), response.body());
    }

    private static boolean needsAccountAction(final int statusCode, final HttpHeaders headers, final String body) {
        if (statusCode == 401 || statusCode == 429) {
            return true;
        }
        if (statusCode != 403) {
            return false;
        }
        return !mustHaveAdminRights(body) || rateLimitExceeded(statusCode, headers, body);
    }

    private static boolean mustHaveAdminRights(final String body) {
        return Optional.ofNullable(body)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> value.contains("must have admin rights"))
                .isPresent();
    }

    private static String workflowUrl(final Request request) {
        return request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/workflows/" + encode(workflowId(request.workflowPath()));
    }

    private static String dispatchUrl(final Request request) {
        final String url = workflowUrl(request) + "/dispatches";
        return server(request).isGitea() ? url + "?return_run_details=true" : url;
    }

    private static String latestRunsUrl(final Request request) {
        final RemoteActionProviders.Server server = server(request);
        final String baseUrl = server.isGitea()
                ? request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/runs"
                : workflowUrl(request) + "/runs";
        final String pageLimit = server.isGitea() ? "limit=20" : "per_page=20";
        return baseUrl + "?branch=" + encode(request.ref()) + "&event=workflow_dispatch&" + pageLimit;
    }

    private static String authorizationCacheKey(final Request request) {
        return Optional.ofNullable(request.apiUrl()).orElse("") + "|" + Optional.ofNullable(request.tokenEnvVar()).orElse("");
    }

    private static String runUrl(final Request request, final long runId) {
        return request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/runs/" + runId;
    }

    private static String workflowId(final String workflowPath) {
        final String normalized = Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
        final int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static RemoteActionProviders.Server server(final Request request) {
        return RemoteActionProviders.Server.fromWorkflowRun(request.apiUrl(), request.workflowPath(), request.tokenEnvVar());
    }

    private static String dispatchBody(final Request request) {
        final StringJoiner inputs = new StringJoiner(",");
        request.inputs().entrySet().stream()
                .filter(entry -> hasText(entry.getKey()))
                .limit(25)
                .forEach(entry -> inputs.add(quote(entry.getKey()) + ":" + quote(entry.getValue())));
        final String inputsJson = inputs.length() == 0 ? "" : ",\"inputs\":{" + inputs + "}";
        return "{\"ref\":" + quote(request.ref()) + inputsJson + "}";
    }

    private static String resultSuffix(final String conclusion) {
        return hasText(conclusion) ? "/" + conclusion : "";
    }

    private static JsonObject parseObject(final String body) {
        if (!hasText(body)) {
            return new JsonObject();
        }
        final JsonElement element = JsonParser.parseString(body);
        return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static Stream<JsonObject> objects(final JsonObject object, final String name) {
        return Optional.ofNullable(object.get(name))
                .filter(JsonElement::isJsonArray)
                .stream()
                .flatMap(elements -> java.util.stream.StreamSupport.stream(elements.getAsJsonArray().spliterator(), false))
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject);
    }

    private static RunStatus runStatus(final JsonObject object, final long fallbackRunId) {
        return new RunStatus(
                longValue(object, "id").orElse(fallbackRunId),
                stringValue(object, "status").orElse("unknown"),
                stringValue(object, "conclusion").orElse(""),
                stringValue(object, "html_url").orElse("")
        );
    }

    private static RunCandidate runCandidate(final Request request, final JsonObject object) {
        return new RunCandidate(
                runStatus(object, -1L),
                runTimestamp(object),
                workflowMatches(request, object)
        );
    }

    private static Comparator<RunCandidate> runCandidateComparator(final Instant dispatchTime) {
        return Comparator
                .comparingInt((RunCandidate candidate) -> candidate.timestamp().isPresent() ? 0 : 1)
                .thenComparingLong(candidate -> candidate.timestamp()
                        .map(timestamp -> instantDistanceMillis(timestamp, dispatchTime))
                        .orElse(Long.MAX_VALUE));
    }

    private static long instantDistanceMillis(final Instant left, final Instant right) {
        try {
            return Math.abs(Duration.between(left, right).toMillis());
        } catch (final ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static Optional<Instant> runTimestamp(final JsonObject object) {
        return Stream.of("created_at", "run_started_at", "started_at", "updated_at")
                .flatMap(name -> stringValue(object, name).stream())
                .flatMap(value -> parseInstant(value).stream())
                .filter(timestamp -> timestamp.isAfter(Instant.parse("2000-01-01T00:00:00Z")))
                .findFirst();
    }

    private static Optional<Instant> parseInstant(final String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (final DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static boolean workflowMatches(final Request request, final JsonObject object) {
        if (!server(request).isGitea()) {
            return true;
        }
        return Stream.of("path", "workflow_path", "workflow_file_path")
                .flatMap(name -> stringValue(object, name).stream())
                .findFirst()
                .map(path -> sameWorkflowPath(request.workflowPath(), path))
                .orElse(true);
    }

    private static boolean sameWorkflowPath(final String expected, final String actual) {
        final String normalizedExpected = normalizeWorkflowPath(expected);
        final String normalizedActual = normalizeWorkflowPath(actual);
        return normalizedExpected.equals(normalizedActual)
                || workflowId(normalizedExpected).equals(workflowId(normalizedActual));
    }

    private static String normalizeWorkflowPath(final String value) {
        String normalized = Optional.ofNullable(value).orElse("").replace('\\', '/').strip();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static JobStatus jobStatus(final JsonObject object) {
        return new JobStatus(
                longValue(object, "id").orElse(-1L),
                stringValue(object, "name").orElse("job"),
                stringValue(object, "status").orElse("unknown"),
                stringValue(object, "conclusion").orElse(""),
                stringValue(object, "html_url").orElse("")
        );
    }

    private static ArtifactStatus artifactStatus(final JsonObject object) {
        return new ArtifactStatus(
                longValue(object, "id").orElse(-1L),
                stringValue(object, "name").orElse("artifact"),
                longValue(object, "size_in_bytes").orElse(0L),
                booleanValue(object, "expired").orElse(false),
                stringValue(object, "archive_download_url").orElse("")
        );
    }

    private static Optional<String> stringValue(final JsonObject object, final String name) {
        return Optional.ofNullable(object.get(name))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .filter(WorkflowRun::hasText);
    }

    private static Optional<Long> longValue(final JsonObject object, final String name) {
        return Optional.ofNullable(object.get(name))
                .filter(JsonElement::isJsonPrimitive)
                .map(value -> {
                    try {
                        return value.getAsLong();
                    } catch (final NumberFormatException ignored) {
                        return -1L;
                    }
                })
                .filter(value -> value >= 0);
    }

    private static Optional<Boolean> booleanValue(final JsonObject object, final String name) {
        return Optional.ofNullable(object.get(name))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsBoolean);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(Optional.ofNullable(value).orElse(""), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String quote(final String value) {
        return "\"" + Optional.ofNullable(value).orElse("")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    interface HttpTransport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;

        default HttpResponse<byte[]> sendBytes(final HttpRequest request) throws IOException, InterruptedException {
            throw new IOException("Binary transport is not available.");
        }
    }

    interface AuthorizationProvider {
        List<RemoteActionProviders.Authorizations.Authorization> authorizations(Request request);
    }

    private interface ResponseSender<T> {
        HttpResponse<T> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private interface FailureFactory<T> {
        WorkflowRunHttpException failure(Request request, String operation, HttpResponse<T> response);
    }

    private record JdkHttpTransport(HttpClient client) implements HttpTransport {
        @Override
        public HttpResponse<String> send(final HttpRequest request) throws IOException, InterruptedException {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        @Override
        public HttpResponse<byte[]> sendBytes(final HttpRequest request) throws IOException, InterruptedException {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    /**
     * Request data needed to dispatch and observe one GitHub Actions workflow run.
     *
     * @param apiUrl GitHub REST API base URL
     * @param owner repository owner
     * @param repo repository name
     * @param workflowPath workflow file path or file name
     * @param ref branch or tag used for workflow dispatch
     * @param inputs workflow_dispatch input values
     * @param tokenEnvVar optional environment variable used only after IDE GitHub accounts fail or are unavailable
     */
    public record Request(
            String apiUrl,
            String owner,
            String repo,
            String workflowPath,
            String ref,
            Map<String, String> inputs,
            String tokenEnvVar
    ) {

        public Request {
            inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
        }

        public String repositorySlug() {
            return owner + "/" + repo;
        }
    }

    public static class DispatchInputs {

        public List<Input> parse(final String yaml) {
            final List<Line> lines = lines(yaml);
            final Optional<Integer> workflowDispatchIndex = workflowDispatchIndex(lines);
            if (workflowDispatchIndex.isEmpty()) {
                return List.of();
            }
            final int workflowDispatchIndent = lines.get(workflowDispatchIndex.get()).indent();
            final Optional<Integer> inputsIndex = childIndex(lines, workflowDispatchIndex.get() + 1, workflowDispatchIndent, "inputs");
            if (inputsIndex.isEmpty()) {
                return List.of();
            }
            final int inputsIndent = lines.get(inputsIndex.get()).indent();
            final List<Input> result = new ArrayList<>();
            for (int index = inputsIndex.get() + 1; index < lines.size(); index++) {
                final Line line = lines.get(index);
                if (line.indent() <= inputsIndent) {
                    break;
                }
                if (line.indent() == inputsIndent + 2 && line.keyValue().isPresent()) {
                    result.add(readInput(lines, index, inputsIndent + 2));
                }
            }
            return List.copyOf(result);
        }

        public boolean hasWorkflowDispatch(final String yaml) {
            return workflowDispatchIndex(lines(yaml)).isPresent();
        }

        public String defaultsText(final String yaml) {
            final StringBuilder result = new StringBuilder();
            for (final Input input : parse(yaml)) {
                result.append(input.name()).append("=").append(input.defaultValue()).append("\n");
            }
            return result.toString();
        }

        public static Map<String, String> parseKeyValueText(final String text) {
            final java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
            Optional.ofNullable(text).orElse("").lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> {
                        final int separator = line.indexOf('=');
                        if (separator > 0) {
                            result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                        }
                    });
            return Map.copyOf(result);
        }

        private static Input readInput(final List<Line> lines, final int inputIndex, final int inputIndent) {
            final String name = lines.get(inputIndex).keyValue().orElse("");
            String type = "string";
            String required = "false";
            String defaultValue = "";
            String description = "";
            final List<String> options = new ArrayList<>();
            for (int index = inputIndex + 1; index < lines.size(); index++) {
                final Line line = lines.get(index);
                if (line.indent() <= inputIndent) {
                    break;
                }
                if (line.indent() == inputIndent + 2) {
                    if ("type".equals(line.keyValue().orElse(""))) {
                        type = line.value();
                    } else if ("required".equals(line.keyValue().orElse(""))) {
                        required = line.value();
                    } else if ("default".equals(line.keyValue().orElse(""))) {
                        defaultValue = line.value();
                    } else if ("description".equals(line.keyValue().orElse(""))) {
                        description = line.value();
                    } else if ("options".equals(line.keyValue().orElse(""))) {
                        options.addAll(readOptions(lines, index, inputIndent + 2));
                    }
                }
            }
            return new Input(name, type, Boolean.parseBoolean(required), defaultValue, description, List.copyOf(options));
        }

        private static List<String> readOptions(final List<Line> lines, final int optionsIndex, final int optionsIndent) {
            final List<String> result = new ArrayList<>(inlineOptions(lines.get(optionsIndex).value()));
            for (int index = optionsIndex + 1; index < lines.size(); index++) {
                final Line line = lines.get(index);
                if (line.indent() <= optionsIndent) {
                    break;
                }
                if (line.content().startsWith("- ")) {
                    result.add(stripQuotes(line.content().substring(2).trim()));
                }
            }
            return List.copyOf(result);
        }

        private static List<String> inlineOptions(final String value) {
            final String trimmed = value == null ? "" : value.trim();
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                return List.of();
            }
            final String body = trimmed.substring(1, trimmed.length() - 1);
            if (body.isBlank()) {
                return List.of();
            }
            return splitInlineList(body).stream()
                    .filter(option -> !option.isBlank())
                    .map(DispatchInputs::stripQuotes)
                    .toList();
        }

        private static List<String> splitInlineList(final String body) {
            final List<String> result = new ArrayList<>();
            final StringBuilder current = new StringBuilder();
            char quote = 0;
            for (int index = 0; index < body.length(); index++) {
                final char character = body.charAt(index);
                if (quote != 0) {
                    current.append(character);
                    if (character == quote) {
                        quote = 0;
                    }
                } else if (character == '\'' || character == '"') {
                    quote = character;
                    current.append(character);
                } else if (character == ',') {
                    result.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(character);
                }
            }
            result.add(current.toString().trim());
            return List.copyOf(result);
        }

        private static Optional<Integer> workflowDispatchIndex(final List<Line> lines) {
            for (int index = 0; index < lines.size(); index++) {
                final Line line = lines.get(index);
                if ("workflow_dispatch".equals(line.keyValue().orElse(""))
                        || "on".equals(line.keyValue().orElse("")) && "workflow_dispatch".equals(line.value())) {
                    return Optional.of(index);
                }
                if (line.content().equals("- workflow_dispatch")) {
                    return Optional.of(index);
                }
            }
            return Optional.empty();
        }

        private static Optional<Integer> childIndex(final List<Line> lines, final int start, final int parentIndent, final String key) {
            for (int index = start; index < lines.size(); index++) {
                final Line line = lines.get(index);
                if (line.indent() <= parentIndent) {
                    break;
                }
                if (key.equals(line.keyValue().orElse(""))) {
                    return Optional.of(index);
                }
            }
            return Optional.empty();
        }

        private static List<Line> lines(final String yaml) {
            final List<Line> result = new ArrayList<>();
            Optional.ofNullable(yaml).orElse("").lines()
                    .map(DispatchInputs::line)
                    .filter(line -> !line.content().isBlank())
                    .filter(line -> !line.content().startsWith("#"))
                    .forEach(result::add);
            return result;
        }

        private static Line line(final String raw) {
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') {
                indent++;
            }
            final String content = raw.substring(indent).trim();
            final int separator = content.indexOf(':');
            if (separator < 0) {
                return new Line(indent, content, "", "");
            }
            final String key = content.substring(0, separator).trim();
            final String value = stripQuotes(content.substring(separator + 1).trim());
            return new Line(indent, content, key, value);
        }

        private static String stripQuotes(final String value) {
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        public record Input(String name, String type, boolean required, String defaultValue, String description, List<String> options) {
            public Input(
                    final String name,
                    final String type,
                    final boolean required,
                    final String defaultValue,
                    final String description
            ) {
                this(name, type, required, defaultValue, description, List.of());
            }

            public Input {
                options = options == null ? List.of() : List.copyOf(options);
            }
        }

        private record Line(int indent, String content, String key, String value) {
            Optional<String> keyValue() {
                return key.isBlank() ? Optional.empty() : Optional.of(key);
            }
        }
    }

    /**
     * Tracks workflow runs started from one project so editor gutter actions can switch between run and stop.
     */
    @Service(Service.Level.PROJECT)
    public static class Tracker {

        private final Project project;
        private final ConcurrentMap<String, ProcessHandler> runs = new ConcurrentHashMap<>();

        public Tracker(@NotNull final Project project) {
            this.project = project;
        }

        public static Tracker getInstance(final Project project) {
            return project.getService(Tracker.class);
        }

        public static String key(final String workflowPath) {
            return Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
        }

        public boolean isRunning(final String workflowPath) {
            return runs.containsKey(key(workflowPath));
        }

        public void register(final String workflowPath, final ProcessHandler processHandler) {
            runs.put(key(workflowPath), processHandler);
            refreshGutters();
        }

        public void unregister(final String workflowPath, final ProcessHandler processHandler) {
            runs.remove(key(workflowPath), processHandler);
            refreshGutters();
        }

        public boolean stop(final String workflowPath) {
            return Optional.ofNullable(runs.get(key(workflowPath)))
                    .map(processHandler -> {
                        processHandler.destroyProcess();
                        return true;
                    })
                    .orElse(false);
        }

        private void refreshGutters() {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    DaemonCodeAnalyzer.getInstance(project).settingsChanged();
                }
            });
        }
    }

    public static class LineMarkerContributor extends RunLineMarkerContributor {

        private static final RepositoryAvailability DEFAULT_REPOSITORY_AVAILABILITY =
                (project, file) -> new WorkflowLocation.RepositoryResolver().resolve(project, file).isPresent();
        private static final AtomicReference<RepositoryAvailability> repositoryAvailability =
                new AtomicReference<>(DEFAULT_REPOSITORY_AVAILABILITY);

        @Override
        public @Nullable Info getInfo(final PsiElement element) {
            if (!(element instanceof LeafPsiElement) || !"workflow_dispatch".equals(element.getText())) {
                return null;
            }
            if (!(element.getParent() instanceof YAMLKeyValue keyValue) || !"workflow_dispatch".equals(keyValue.getKeyText())) {
                return null;
            }
            final Optional<String> workflowPath = Optional.ofNullable(element.getContainingFile())
                    .map(file -> file.getVirtualFile())
                    .flatMap(file -> WorkflowRunConfiguration.Producer.workflowPath(element.getProject(), file)
                            .or(() -> WorkflowPsi.toPath(file).map(path -> path.getFileName().toString())));
            final boolean workflowFile = Optional.ofNullable(element.getContainingFile())
                    .map(file -> file.getVirtualFile())
                    .flatMap(WorkflowPsi::toPath)
                    .filter(WorkflowYaml::isWorkflowPath)
                    .isPresent();
            if (!workflowFile || workflowPath.isEmpty()) {
                return null;
            }
            if (Tracker.getInstance(element.getProject()).isRunning(workflowPath.get())) {
                return new Info(
                        AllIcons.Actions.Suspend,
                        new AnAction[]{new StopWorkflowRunAction(workflowPath.get())},
                        item -> GitHubWorkflowBundle.message("workflow.run.gutter.stop")
                );
            }
            final boolean repositoryAvailable = Optional.ofNullable(element.getContainingFile())
                    .map(file -> file.getVirtualFile())
                    .map(file -> repositoryAvailability.get().available(element.getProject(), file))
                    .orElse(false);
            return repositoryAvailable ? withExecutorActions(AllIcons.Actions.Execute) : null;
        }

        static RepositoryAvailability useRepositoryAvailabilityForTests(final RepositoryAvailability availability) {
            return repositoryAvailability.getAndSet(availability == null ? DEFAULT_REPOSITORY_AVAILABILITY : availability);
        }

        interface RepositoryAvailability {
            boolean available(Project project, VirtualFile file);
        }
    }

    private static class StopWorkflowRunAction extends AnAction {

        private final String workflowPath;

        private StopWorkflowRunAction(final String workflowPath) {
            super(
                    GitHubWorkflowBundle.message("workflow.run.gutter.stop.text"),
                    GitHubWorkflowBundle.message("workflow.run.gutter.stop.description"),
                    AllIcons.Actions.Suspend
            );
            this.workflowPath = workflowPath;
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent event) {
            Optional.ofNullable(event.getProject())
                    .map(Tracker::getInstance)
                    .ifPresent(tracker -> tracker.stop(workflowPath));
        }
    }

    public record DispatchResult(long runId, String runUrl, String htmlUrl) {
    }

    public record RunStatus(long runId, String status, String conclusion, String htmlUrl) {
        public boolean completed() {
            return "completed".equals(status);
        }
    }

    private record RunCandidate(RunStatus status, Optional<Instant> timestamp, boolean workflowMatches) {
    }

    public record CancelResult(int statusCode, boolean accepted) {
    }

    public record RerunResult(int statusCode, boolean accepted) {
    }

    public record DeleteResult(int statusCode, boolean accepted) {
    }

    public record JobStatus(long id, String name, String status, String conclusion, String htmlUrl) {
    }

    public record ArtifactStatus(long id, String name, long sizeInBytes, boolean expired, String archiveDownloadUrl) {
    }

    public static class WorkflowRunHttpException extends IOException {

        private final int statusCode;
        private final String body;
        private final boolean accountActionRecommended;
        private final String settingsId;

        public WorkflowRunHttpException(
                final String message,
                final int statusCode,
                final String body,
                final boolean accountActionRecommended,
                final String settingsId
        ) {
            super(message);
            this.statusCode = statusCode;
            this.body = body;
            this.accountActionRecommended = accountActionRecommended;
            this.settingsId = settingsId;
        }

        public int statusCode() {
            return statusCode;
        }

        public String body() {
            return body;
        }

        public boolean accountActionRecommended() {
            return accountActionRecommended;
        }

        public String settingsId() {
            return settingsId;
        }
    }
}
