package com.github.yunabraska.githubworkflow.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
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
        return new RunStatus(
                longValue(json, "id").orElse(runId),
                stringValue(json, "status").orElse("unknown"),
                stringValue(json, "conclusion").orElse(""),
                stringValue(json, "html_url").orElse("")
        );
    }

    public CancelResult cancel(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "POST",
                runUrl(request, runId) + "/cancel",
                "",
                "GitHub workflow cancel"
        );
        return new CancelResult(response.statusCode(), response.statusCode() / 100 == 2);
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
        return Optional.ofNullable(json.get("workflow_runs"))
                .filter(JsonElement::isJsonArray)
                .map(JsonElement::getAsJsonArray)
                .filter(runs -> !runs.isEmpty())
                .map(runs -> runs.get(0))
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(run -> new RunStatus(
                        longValue(run, "id").orElse(-1L),
                        stringValue(run, "status").orElse("unknown"),
                        stringValue(run, "conclusion").orElse(""),
                        stringValue(run, "html_url").orElse("")
                ))
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

    public List<JobStatus> jobs(final WorkflowRunRequest request, final long runId) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(
                request,
                "GET",
                runUrl(request, runId) + "/jobs",
                "",
                "GitHub workflow jobs"
        );
        final JsonObject json = parseObject(response.body());
        final List<JobStatus> result = new ArrayList<>();
        Optional.ofNullable(json.get("jobs"))
                .filter(JsonElement::isJsonArray)
                .map(JsonElement::getAsJsonArray)
                .ifPresent(jobs -> jobs.forEach(job -> {
                    if (job.isJsonObject()) {
                        final JsonObject object = job.getAsJsonObject();
                        result.add(new JobStatus(
                                longValue(object, "id").orElse(-1L),
                                stringValue(object, "name").orElse("job"),
                                stringValue(object, "status").orElse("unknown"),
                                stringValue(object, "conclusion").orElse(""),
                                stringValue(object, "html_url").orElse("")
                        ));
                    }
                }));
        return result.stream().filter(job -> job.id() >= 0).toList();
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
        WorkflowRunHttpException lastFailure = null;
        boolean authenticatedRateLimitFailure = false;
        final String authorizationCacheKey = authorizationCacheKey(workflow);
        for (final GitHubRequestAuthorizations.Authorization authorization : authorizations(workflow, authorizationCacheKey)) {
            if (!authorization.authenticated() && authenticatedRateLimitFailure) {
                break;
            }
            final HttpResponse<String> response = transport.send(request(workflow, method, url, body, authorization));
            if (response.statusCode() / 100 == 2) {
                if (authorization.authenticated()) {
                    successfulAuthorizations.put(authorizationCacheKey, authorization);
                }
                return response;
            }
            lastFailure = failure(operation, response);
            if (authorization.authenticated() && rateLimitExceeded(response)) {
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
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private static WorkflowRunHttpException failure(final String operation, final HttpResponse<String> response) {
        final boolean accountActionRecommended = needsAccountAction(response);
        final String hint = accountActionRecommended
                ? "\nAdd or refresh GitHub accounts in " + GitHubRequestAuthorizations.settingsHint() + "."
                : "";
        final String summary = responseSummary(response);
        return new WorkflowRunHttpException(
                operation + " failed with HTTP " + response.statusCode() + (summary.isEmpty() ? "" : ": " + summary) + hint,
                response.statusCode(),
                response.body(),
                accountActionRecommended
        );
    }

    private static String responseSummary(final HttpResponse<String> response) {
        final String body = Optional.ofNullable(response.body()).orElse("").strip();
        if (body.isEmpty()) {
            return "";
        }
        final String contentType = response.headers()
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

    private static boolean rateLimitExceeded(final HttpResponse<String> response) {
        if (response.statusCode() != 403 && response.statusCode() != 429) {
            return false;
        }
        if (response.headers()
                .firstValue("x-ratelimit-remaining")
                .map(String::trim)
                .filter("0"::equals)
                .isPresent()) {
            return true;
        }
        return Optional.ofNullable(response.body())
                .map(body -> body.toLowerCase(Locale.ROOT))
                .filter(body -> body.contains("rate limit"))
                .isPresent();
    }

    private static boolean needsAccountAction(final HttpResponse<String> response) {
        if (response.statusCode() == 401 || response.statusCode() == 429) {
            return true;
        }
        if (response.statusCode() != 403) {
            return false;
        }
        return !mustHaveAdminRights(response) || rateLimitExceeded(response);
    }

    private static boolean mustHaveAdminRights(final HttpResponse<String> response) {
        return mustHaveAdminRights(response.body());
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
    }

    interface AuthorizationProvider {
        List<GitHubRequestAuthorizations.Authorization> authorizations(WorkflowRunRequest request);
    }

    private record JdkHttpTransport(HttpClient client) implements HttpTransport {
        @Override
        public HttpResponse<String> send(final HttpRequest request) throws IOException, InterruptedException {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    public record JobStatus(long id, String name, String status, String conclusion, String htmlUrl) {
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
