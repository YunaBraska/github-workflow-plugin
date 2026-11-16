package com.github.yunabraska.githubworkflow.git;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.github.yunabraska.githubworkflow.run.WorkflowRun;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class GithubAccountIntegrationTest extends BasePlatformTestCase {
    public void testWorkflowRunFallsBackToTheNextStoredAccount() throws Exception {
        final var headers = new CopyOnWriteArrayList<String>();
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            headers.add(authorization == null ? "" : authorization);
            exchange.sendResponseHeaders("Bearer second-token".equals(authorization) ? 202 : 401, -1);
            exchange.close();
        });
        http.start();
        final String base = "http://127.0.0.1:" + http.getAddress().getPort();
        final String id = UUID.randomUUID().toString();
        final var first = github("first-" + id, base, base + "/api");
        final var second = github("second-" + id, base, base + "/api");
        final var settings = RemoteActionProviders.Settings.getInstance();
        final var previous = settings.customServers();
        try {
            settings.setCustomServers(List.of(first, second)).setGithubToken(first, "first-token").setGithubToken(second, "second-token");
            final var request = new WorkflowRun.Request(base + "/api", "owner", "repo", "build.yml", "main", Map.of(), "");
            final var result = new WorkflowRun().cancel(request, 42);
            assertThat(result.accepted()).isTrue();
            assertThat(result.statusCode()).isEqualTo(202);
            assertThat(headers).containsExactly("Bearer first-token", "Bearer second-token");
        } finally {
            settings.clearGithubToken(first).clearGithubToken(second).setCustomServers(previous);
            http.stop(0);
        }
    }

    public void testStoredAccountsForTheTargetServerAreTriedBeforeEnvironmentAndAnonymous() {
        final var settings = RemoteActionProviders.Settings.getInstance();
        final String id = UUID.randomUUID().toString();
        final var first = github("first-" + id, "https://enterprise.example.test", "https://enterprise.example.test/api/v3");
        final var second = github("second-" + id, "https://enterprise.example.test", "https://enterprise.example.test/api/v3");
        final var foreign = github("foreign-" + id, "https://other.example.test", "https://other.example.test/api/v3");
        final var previous = settings.customServers();
        try {
            settings.setCustomServers(List.of(first, second, foreign));
            settings.setGithubToken(first, "first-token").setGithubToken(second, "second-token").setGithubToken(foreign, "foreign-token");
            assertThat(RemoteActionProviders.Authorizations.forApiUrl(first.apiUrl, "CUSTOM_TOKEN", getProject(), Map.of("CUSTOM_TOKEN", "env-token")))
                    .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                    .containsExactly("Bearer first-token", "Bearer second-token", "Bearer env-token", "");
        } finally {
            settings.clearGithubToken(first).clearGithubToken(second).clearGithubToken(foreign).setCustomServers(previous);
        }
    }

    public void testCredentialsAreNotSentToOtherPortsProtocolsOrApiPaths() {
        final var settings = RemoteActionProviders.Settings.getInstance();
        final var server = github("path-" + UUID.randomUUID(), "https://enterprise.example.test:8443/first", "https://enterprise.example.test:8443/first/api/v3");
        final var previous = settings.customServers();
        try {
            settings.setCustomServers(List.of(server)).setGithubToken(server, "secret-token");
            for (final String target : List.of("http://enterprise.example.test:8443/first/api/v3", "https://enterprise.example.test:9443/first/api/v3", "https://enterprise.example.test:8443/second/api/v3", "https://enterprise.example.test:8443/first/api/v3/%2e%2e/other")) {
                assertThat(RemoteActionProviders.Authorizations.forApiUrl(target, "", getProject(), Map.of()))
                        .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                        .doesNotContain("Bearer secret-token");
            }
            assertThat(RemoteActionProviders.Authorizations.forApiUrl(server.apiUrl + "/repos", "", getProject(), Map.of()))
                    .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader).contains("Bearer secret-token");
        } finally {
            settings.clearGithubToken(server).setCustomServers(previous);
        }
    }

    public void testClearingAnAccountRemovesOnlyItsOwnToken() {
        final var settings = RemoteActionProviders.Settings.getInstance();
        final String id = UUID.randomUUID().toString();
        final var first = github("first-" + id, "https://github.com", "https://api.github.com");
        final var second = github("second-" + id, "https://github.com", "https://api.github.com");
        final var previous = settings.customServers();
        try {
            settings.setCustomServers(List.of(first, second)).setGithubToken(first, "one").setGithubToken(second, "two");
            settings.clearGithubToken(first);
            assertThat(RemoteActionProviders.Authorizations.forApiUrl("https://api.github.com:443", "", getProject(), Map.of()))
                    .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                    .containsExactly("Bearer two", "");
        } finally {
            settings.clearGithubToken(first).clearGithubToken(second).setCustomServers(previous);
        }
    }

    private static RemoteActionProviders.Server github(final String name, final String webUrl, final String apiUrl) {
        return new RemoteActionProviders.Server(name, webUrl, apiUrl, "", true).normalized();
    }
}
