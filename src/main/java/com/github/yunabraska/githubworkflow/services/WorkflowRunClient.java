package com.github.yunabraska.githubworkflow.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Small GitHub Actions REST client for workflow dispatch, status polling, cancellation, and logs.
 */
public final class WorkflowRunClient {

    private static final String API_VERSION = "2026-03-10";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpTransport transport;
    private final AuthorizationProvider authorizationProvider;
    private final ConcurrentMap<String, GitHubRequestAuthorizations.Authorization> successfulAuthorizations = new ConcurrentHashMap<>();

    public WorkflowRunClient() {
        this((Project) null);
    }

    public WorkflowRunClient(final Project project) {
        this(new JdkHttpTransport(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()), request -> GitHubRequestAuthorizations.forApiUrl(request.apiUrl(), request.tokenEnvVar(), project));
    }

    WorkflowRunClient(final HttpTransport transport) {
        this(transport, request -> GitHubRequestAuthorizations.forApiUrl(request.apiUrl(), request.tokenEnvVar(), null));
    }

    WorkflowRunClient(final HttpTransport transport, final AuthorizationProvider authorizationProvider) {
        this.transport = transport;
        this.authorizationProvider = authorizationProvider;
    }

    public DispatchResult dispatch(final WorkflowRunRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "POST",
                workflowUrl(request) + "/dispatches",
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

    public RunStatus status(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
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

    public CancelResult cancel(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
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
            final WorkflowRunRequest request,
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
    public DeleteResult delete(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "DELETE",
                runUrl(request, runId),
                "",
                "GitHub workflow run delete"
        );
        return new DeleteResult(response.statusCode(), accepted(response));
    }

    public Optional<RunStatus> latestRun(final WorkflowRunRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                workflowUrl(request) + "/runs?branch=" + encode(request.ref()) + "&event=workflow_dispatch&per_page=1",
                "",
                "GitHub workflow run discovery"
        );
        final JsonObject json = parseObject(response.body());
        return objects(json, "workflow_runs")
                .findFirst()
                .map(run -> runStatus(run, -1L))
                .filter(run -> run.runId() >= 0);
    }

    public String logs(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
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
    public List<ArtifactStatus> artifacts(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                runUrl(request, runId) + "/artifacts?per_page=100",
                "",
                "GitHub workflow artifacts"
        );
        final JsonObject json = parseObject(response.body());
        return objects(json, "artifacts")
                .map(WorkflowRunClient::artifactStatus)
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
    public byte[] artifactZip(final WorkflowRunRequest request, final long artifactId) throws IOException, InterruptedException {
        final HttpResponse<byte[]> response = sendBytes(
                request,
                "GET",
                request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/artifacts/" + artifactId + "/zip",
                "",
                "GitHub workflow artifact download"
        );
        return response.body();
    }

    public List<JobStatus> jobs(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                runUrl(request, runId) + "/jobs",
                "",
                "GitHub workflow jobs"
        );
        final JsonObject json = parseObject(response.body());
        return objects(json, "jobs")
                .map(WorkflowRunClient::jobStatus)
                .filter(job -> job.id() >= 0)
                .toList();
    }

    public String jobLogs(final WorkflowRunRequest request, final long jobId) throws IOException, InterruptedException {
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
            final WorkflowRunRequest workflow,
            final String method,
            final String url,
            final String body,
            final String operation
    ) throws IOException, InterruptedException {
        return sendWithAuthorizations(workflow, method, url, body, operation, transport::send, WorkflowRunClient::failure, HttpResponse::body);
    }

    private HttpResponse<byte[]> sendBytes(
            final WorkflowRunRequest workflow,
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
                WorkflowRunClient::failureBytes,
                response -> new String(Optional.ofNullable(response.body()).orElseGet(() -> new byte[0]), StandardCharsets.UTF_8)
        );
    }

    private <T> HttpResponse<T> sendWithAuthorizations(
            final WorkflowRunRequest workflow,
            final String method,
            final String url,
            final String body,
            final String operation,
            final ResponseSender<T> sender,
            final BiFunction<String, HttpResponse<T>, WorkflowRunHttpException> failureFactory,
            final Function<HttpResponse<T>, String> bodyText
    ) throws IOException, InterruptedException {
        WorkflowRunHttpException lastFailure = null;
        boolean authenticatedRateLimitFailure = false;
        final String authorizationCacheKey = authorizationCacheKey(workflow);
        for (final GitHubRequestAuthorizations.Authorization authorization : authorizations(workflow, authorizationCacheKey)) {
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
            lastFailure = failureFactory.apply(operation, response);
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

    private List<GitHubRequestAuthorizations.Authorization> authorizations(
            final WorkflowRunRequest workflow,
            final String authorizationCacheKey
    ) {
        final List<GitHubRequestAuthorizations.Authorization> result = new ArrayList<>();
        Optional.ofNullable(successfulAuthorizations.get(authorizationCacheKey)).ifPresent(result::add);
        final List<GitHubRequestAuthorizations.Authorization> authorizations = authorizationProvider.authorizations(workflow);
        if (authorizations == null || authorizations.isEmpty()) {
            result.add(GitHubRequestAuthorizations.Authorization.anonymous());
        } else {
            result.addAll(authorizations);
        }
        return result.stream()
                .filter(WorkflowRunClient::knownAuthorization)
                .distinct()
                .toList();
    }

    private static boolean knownAuthorization(final GitHubRequestAuthorizations.Authorization authorization) {
        return authorization != null;
    }

    private static HttpRequest request(
            final WorkflowRunRequest workflow,
            final String method,
            final String url,
            final String body,
            final GitHubRequestAuthorizations.Authorization authorization
    ) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "GitHub-Workflow-Plugin");
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

    private static WorkflowRunHttpException failure(final String operation, final HttpResponse<String> response) {
        return failure(operation, response.statusCode(), response.headers(), response.body());
    }

    private static WorkflowRunHttpException failureBytes(final String operation, final HttpResponse<byte[]> response) {
        final String body = new String(Optional.ofNullable(response.body()).orElseGet(() -> new byte[0]), StandardCharsets.UTF_8);
        return failure(operation, response.statusCode(), response.headers(), body);
    }

    private static WorkflowRunHttpException failure(
            final String operation,
            final int statusCode,
            final HttpHeaders headers,
            final String body
    ) {
        final boolean accountActionRecommended = needsAccountAction(statusCode, headers, body);
        final String hint = accountActionRecommended
                ? "\nAdd or refresh GitHub accounts in " + GitHubRequestAuthorizations.settingsHint() + "."
                : "";
        final String summary = responseSummary(statusCode, headers, body);
        return new WorkflowRunHttpException(
                operation + " failed with HTTP " + statusCode + (summary.isEmpty() ? "" : ": " + summary) + hint,
                statusCode,
                body,
                accountActionRecommended
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
            return "GitHub returned an HTML error page instead of API data.";
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

    private static String workflowUrl(final WorkflowRunRequest request) {
        return request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/workflows/" + encode(workflowId(request.workflowPath()));
    }

    private static String authorizationCacheKey(final WorkflowRunRequest request) {
        return Optional.ofNullable(request.apiUrl()).orElse("") + "|" + Optional.ofNullable(request.tokenEnvVar()).orElse("");
    }

    private static String runUrl(final WorkflowRunRequest request, final long runId) {
        return request.apiUrl() + "/repos/" + encode(request.owner()) + "/" + encode(request.repo()) + "/actions/runs/" + runId;
    }

    private static String workflowId(final String workflowPath) {
        final String normalized = Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
        final int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static String dispatchBody(final WorkflowRunRequest request) {
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
                .filter(WorkflowRunClient::hasText);
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
        List<GitHubRequestAuthorizations.Authorization> authorizations(WorkflowRunRequest request);
    }

    private interface ResponseSender<T> {
        HttpResponse<T> send(HttpRequest request) throws IOException, InterruptedException;
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

    public record DispatchResult(long runId, String runUrl, String htmlUrl) {
    }

    public record RunStatus(long runId, String status, String conclusion, String htmlUrl) {
        public boolean completed() {
            return "completed".equals(status);
        }
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

    public static final class WorkflowRunHttpException extends IOException {

        private final int statusCode;
        private final String body;
        private final boolean accountActionRecommended;

        public WorkflowRunHttpException(
                final String message,
                final int statusCode,
                final String body,
                final boolean accountActionRecommended
        ) {
            super(message);
            this.statusCode = statusCode;
            this.body = body;
            this.accountActionRecommended = accountActionRecommended;
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
    }
}
