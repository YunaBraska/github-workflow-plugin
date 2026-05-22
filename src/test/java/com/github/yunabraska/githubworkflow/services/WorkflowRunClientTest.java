package com.github.yunabraska.githubworkflow.services;

import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunClientTest extends TestCase {

    public void testDispatchPostsWorkflowDispatchRequest() throws Exception {
        try (FakeWorkflowRunServer server = new FakeWorkflowRunServer()) {
            final WorkflowRunClient client = new WorkflowRunClient();
            final WorkflowRunRequest request = new WorkflowRunRequest(
                    server.apiUrl(),
                    "acme",
                    "tool",
                    ".github/workflows/build.yml",
                    "feature",
                    Map.of("dry_run", "true"),
                    ""
            );

            final WorkflowRunClient.DispatchResult result = client.dispatch(request);

            assertThat(result.runId()).isEqualTo(42);
            assertThat(server.requests()).contains("/repos/acme/tool/actions/workflows/build.yml/dispatches");
            assertThat(server.bodies()).contains("{\"ref\":\"feature\",\"inputs\":{\"dry_run\":\"true\"}}");
        }
    }

    public void testStatusCancelJobsAndLogsUseRunEndpoints() throws Exception {
        try (FakeWorkflowRunServer server = new FakeWorkflowRunServer()) {
            final WorkflowRunClient client = new WorkflowRunClient();
            final WorkflowRunRequest request = new WorkflowRunRequest(server.apiUrl(), "acme", "tool", "build.yml", "main", Map.of(), "");

            final WorkflowRunClient.RunStatus status = client.status(request, 42);
            final WorkflowRunClient.CancelResult cancel = client.cancel(request, 42);
            final WorkflowRunClient.DeleteResult delete = client.delete(request, 42);
            final String logs = client.logs(request, 42);

            assertThat(status.completed()).isTrue();
            assertThat(status.conclusion()).isEqualTo("success");
            assertThat(cancel.accepted()).isTrue();
            assertThat(delete.accepted()).isTrue();
            assertThat(logs).contains("== build [completed/success]", "hello from job log");
            assertThat(server.requests()).contains(
                    "/repos/acme/tool/actions/runs/42",
                    "/repos/acme/tool/actions/runs/42/cancel",
                    "/repos/acme/tool/actions/runs/42/jobs",
                    "/repos/acme/tool/actions/jobs/100/logs"
            );
            assertThat(server.methods()).contains("DELETE");
        }
    }

    public void testDispatchAcceptsLegacyNoContentResponse() throws Exception {
        try (FakeWorkflowRunServer server = new FakeWorkflowRunServer(true)) {
            final WorkflowRunClient client = new WorkflowRunClient();
            final WorkflowRunRequest request = new WorkflowRunRequest(server.apiUrl(), "acme", "tool", "build.yml", "main", Map.of(), "");

            final WorkflowRunClient.DispatchResult result = client.dispatch(request);

            assertThat(result.runId()).isEqualTo(-1);
            assertThat(result.htmlUrl()).isEmpty();
        }
    }

    public void testLatestRunDiscoversNewestWorkflowDispatchRun() throws Exception {
        try (FakeWorkflowRunServer server = new FakeWorkflowRunServer()) {
            final WorkflowRunClient client = new WorkflowRunClient();
            final WorkflowRunRequest request = new WorkflowRunRequest(server.apiUrl(), "acme", "tool", "build.yml", "feature/one", Map.of(), "");

            final var result = client.latestRun(request);

            assertThat(result).isPresent();
            assertThat(result.get().runId()).isEqualTo(77);
            assertThat(result.get().status()).isEqualTo("queued");
            assertThat(result.get().htmlUrl()).isEqualTo("html-latest");
            assertThat(server.requests()).contains("/repos/acme/tool/actions/workflows/build.yml/runs?branch=feature%2Fone&event=workflow_dispatch&per_page=1");
        }
    }

    public void testDispatchRetriesConfiguredAuthorizationsBeforeAnonymous() throws Exception {
        try (FakeWorkflowRunServer server = new FakeWorkflowRunServer(false, 2)) {
            final HttpClient httpClient = HttpClient.newHttpClient();
            final WorkflowRunClient client = new WorkflowRunClient(
                    request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
                    request -> List.of(
                            new GitHubRequestAuthorizations.Authorization("github.com", "Bearer normal-token"),
                            new GitHubRequestAuthorizations.Authorization("enterprise", "Bearer enterprise-token"),
                            GitHubRequestAuthorizations.Authorization.anonymous()
                    )
            );
            final WorkflowRunRequest request = new WorkflowRunRequest(server.apiUrl(), "acme", "tool", "build.yml", "main", Map.of(), "");

            final WorkflowRunClient.DispatchResult result = client.dispatch(request);

            assertThat(result.runId()).isEqualTo(42);
            assertThat(server.authorizations()).containsExactly("Bearer normal-token", "Bearer enterprise-token", "");
        }
    }

    public void testSuccessfulAuthorizationIsReusedWhenProviderLaterCannotLoadAccounts() throws Exception {
        final AtomicInteger providerCalls = new AtomicInteger();
        final List<String> authorizations = new ArrayList<>();
        final WorkflowRunClient client = new WorkflowRunClient(
                request -> {
                    authorizations.add(authorizationHeader(request));
                    if (request.uri().getPath().endsWith("/dispatches")) {
                        return new ClientResponse(request, 200, "application/json", "{\"workflow_run_id\":42}");
                    }
                    if (request.uri().getPath().endsWith("/jobs/100/logs")
                            && "Bearer account-token".equals(authorizationHeader(request))) {
                        return new ClientResponse(request, 200, "text/plain", "cached account log\n");
                    }
                    return new ClientResponse(request, 403, "application/json", "{\"message\":\"API rate limit exceeded\"}");
                },
                request -> providerCalls.getAndIncrement() == 0
                        ? List.of(new GitHubRequestAuthorizations.Authorization("github.com", "Bearer account-token"))
                        : List.of(GitHubRequestAuthorizations.Authorization.anonymous())
        );
        final WorkflowRunRequest request = new WorkflowRunRequest("https://api.github.test", "acme", "tool", "build.yml", "main", Map.of(), "");

        client.dispatch(request);
        final String logs = client.jobLogs(request, 100);

        assertThat(logs).isEqualTo("cached account log\n");
        assertThat(authorizations).containsExactly("Bearer account-token", "Bearer account-token");
    }

    public void testAuthenticatedRateLimitDoesNotFallBackToAnonymous() {
        final List<String> authorizations = new ArrayList<>();
        final WorkflowRunClient client = new WorkflowRunClient(
                request -> {
                    authorizations.add(authorizationHeader(request));
                    if (authorizationHeader(request).isEmpty()) {
                        return new ClientResponse(request, 200, "text/plain", "anonymous log should not be used\n");
                    }
                    return new ClientResponse(request, 403, "application/json", "{\"message\":\"API rate limit exceeded for token\"}");
                },
                request -> List.of(
                        new GitHubRequestAuthorizations.Authorization("github.com", "Bearer limited-token"),
                        GitHubRequestAuthorizations.Authorization.anonymous()
                )
        );
        final WorkflowRunRequest request = new WorkflowRunRequest("https://api.github.test", "acme", "tool", "build.yml", "main", Map.of(), "");

        assertThatExceptionOfType(WorkflowRunClient.WorkflowRunHttpException.class)
                .isThrownBy(() -> client.jobLogs(request, 100))
                .withMessageContaining("GitHub workflow job logs failed with HTTP 403")
                .withMessageContaining("rate limit");
        assertThat(authorizations).containsExactly("Bearer limited-token");
    }

    public void testJobLogsFallBackFromAccountTokenWithoutLogRightsToEnvironmentToken() throws Exception {
        final List<String> authorizations = new ArrayList<>();
        final WorkflowRunClient client = new WorkflowRunClient(
                request -> {
                    authorizations.add(authorizationHeader(request));
                    if ("Bearer env-token".equals(authorizationHeader(request))) {
                        return new ClientResponse(request, 200, "text/plain", "live log from env token\n");
                    }
                    return new ClientResponse(
                            request,
                            403,
                            "application/json",
                            "{\"message\":\"Must have admin rights to Repository.\"}"
                    );
                },
                request -> List.of(
                        new GitHubRequestAuthorizations.Authorization("github.com", "Bearer account-token"),
                        new GitHubRequestAuthorizations.Authorization("GITHUB_TOKEN", "Bearer env-token"),
                        GitHubRequestAuthorizations.Authorization.anonymous()
                )
        );
        final WorkflowRunRequest request = new WorkflowRunRequest("https://api.github.test", "acme", "tool", "build.yml", "main", Map.of(), "");

        final String logs = client.jobLogs(request, 100);

        assertThat(logs).isEqualTo("live log from env token\n");
        assertThat(authorizations).containsExactly("Bearer account-token", "Bearer env-token");
    }

    public void testJobLogAdminFailureDoesNotSuggestRefreshingAccounts() {
        final WorkflowRunClient client = new WorkflowRunClient(
                request -> new ClientResponse(
                        request,
                        403,
                        "application/json",
                        "{\"message\":\"Must have admin rights to Repository.\"}"
                ),
                request -> List.of(new GitHubRequestAuthorizations.Authorization("github.com", "Bearer account-token"))
        );
        final WorkflowRunRequest request = new WorkflowRunRequest("https://api.github.test", "acme", "tool", "build.yml", "main", Map.of(), "");

        assertThatExceptionOfType(WorkflowRunClient.WorkflowRunHttpException.class)
                .isThrownBy(() -> client.jobLogs(request, 100))
                .withMessageContaining("GitHub workflow job logs failed with HTTP 403")
                .withMessageContaining("Must have admin rights")
                .withMessageNotContaining("Settings > Version Control > GitHub");
    }

    public void testDispatchAuthenticationFailureMentionsGithubSettings() throws Exception {
        try (FakeWorkflowRunServer server = new FakeWorkflowRunServer(false, 1)) {
            final HttpClient httpClient = HttpClient.newHttpClient();
            final WorkflowRunClient client = new WorkflowRunClient(
                    request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
                    request -> List.of(GitHubRequestAuthorizations.Authorization.anonymous())
            );
            final WorkflowRunRequest request = new WorkflowRunRequest(server.apiUrl(), "acme", "tool", "build.yml", "main", Map.of(), "");

            assertThatExceptionOfType(WorkflowRunClient.WorkflowRunHttpException.class)
                    .isThrownBy(() -> client.dispatch(request))
                    .withMessageContaining("GitHub workflow dispatch failed with HTTP 401")
                    .withMessageContaining("Settings > Version Control > GitHub");
        }
    }

    public void testJobLogHtmlFailureIsSummarized() {
        final WorkflowRunClient client = new WorkflowRunClient(
                request -> new ClientResponse(
                        request,
                        504,
                        "text/html",
                        "<!DOCTYPE html><html><body><p>We couldn't respond in time.</p><img src=\"data:image/png;base64,large\" /></body></html>"
                ),
                request -> List.of(GitHubRequestAuthorizations.Authorization.anonymous())
        );
        final WorkflowRunRequest request = new WorkflowRunRequest("https://api.github.test", "acme", "tool", "build.yml", "main", Map.of(), "");

        assertThatExceptionOfType(WorkflowRunClient.WorkflowRunHttpException.class)
                .isThrownBy(() -> client.jobLogs(request, 100))
                .withMessageContaining("GitHub workflow job logs failed with HTTP 504")
                .withMessageContaining("GitHub returned an HTML error page")
                .withMessageNotContaining("<!DOCTYPE")
                .withMessageNotContaining("base64");
    }

    private static final class FakeWorkflowRunServer implements AutoCloseable {
        private final HttpServer server;
        private final List<String> requests = new ArrayList<>();
        private final List<String> methods = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();
        private final List<String> authorizations = new ArrayList<>();
        private final boolean legacyDispatch;
        private int dispatchFailures;

        FakeWorkflowRunServer() throws IOException {
            this(false, 0);
        }

        FakeWorkflowRunServer(final boolean legacyDispatch) throws IOException {
            this(legacyDispatch, 0);
        }

        FakeWorkflowRunServer(final boolean legacyDispatch, final int dispatchFailures) throws IOException {
            this.legacyDispatch = legacyDispatch;
            this.dispatchFailures = dispatchFailures;
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                final URI uri = exchange.getRequestURI();
                final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                methods.add(exchange.getRequestMethod());
                requests.add(uri.getPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
                bodies.add(body);
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization") == null
                        ? ""
                        : exchange.getRequestHeaders().getFirst("Authorization"));
                final Response response = responseFor(uri.getPath());
                final byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
                if (response.status() == 204) {
                    exchange.sendResponseHeaders(response.status(), -1);
                    exchange.close();
                    return;
                }
                exchange.sendResponseHeaders(response.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        String apiUrl() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        List<String> requests() {
            return List.copyOf(requests);
        }

        List<String> methods() {
            return List.copyOf(methods);
        }

        List<String> bodies() {
            return List.copyOf(bodies);
        }

        List<String> authorizations() {
            return List.copyOf(authorizations);
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private Response responseFor(final String path) {
            if (path.endsWith("/dispatches")) {
                if (dispatchFailures > 0) {
                    dispatchFailures--;
                    return new Response(401, "application/json", "{\"message\":\"Requires authentication\",\"status\":\"401\"}");
                }
                if (legacyDispatch) {
                    return new Response(204, "application/json", "");
                }
                return new Response(200, "application/json", "{\"workflow_run_id\":42,\"run_url\":\"api-run\",\"html_url\":\"html-run\"}");
            }
            if (path.endsWith("/cancel")) {
                return new Response(202, "application/json", "{}");
            }
            if (path.endsWith("/workflows/build.yml/runs")) {
                return new Response(200, "application/json", "{\"workflow_runs\":[{\"id\":77,\"status\":\"queued\",\"conclusion\":null,\"html_url\":\"html-latest\"}]}");
            }
            if (path.endsWith("/runs/42/jobs")) {
                return new Response(200, "application/json", "{\"jobs\":[{\"id\":100,\"name\":\"build\",\"status\":\"completed\",\"conclusion\":\"success\"}]}");
            }
            if (path.endsWith("/jobs/100/logs")) {
                return new Response(200, "text/plain", "hello from job log\n");
            }
            if (path.endsWith("/runs/42")) {
                return new Response(200, "application/json", "{\"id\":42,\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"html-run\"}");
            }
            return new Response(404, "application/json", "{}");
        }

        private record Response(int status, String contentType, String body) {
        }
    }

    private record ClientResponse(HttpRequest request, int statusCode, String contentType, String body) implements HttpResponse<String> {
        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (left, right) -> true);
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

    private static String authorizationHeader(final HttpRequest request) {
        return request.headers().firstValue("Authorization").orElse("");
    }
}
