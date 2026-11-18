package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.auth.GitHubDeviceAuth;
import com.github.yunabraska.githubworkflow.auth.TestClients;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GitHubDeviceCredentialIntegrationTest extends BasePlatformTestCase {
    public void testBrowserCredentialCannotBeStoredForAnotherServer() {
        final var settings = RemoteActionProviders.Settings.getInstance();
        final var server = new RemoteActionProviders.Server("test-" + java.util.UUID.randomUUID(),
                "https://example.test", "https://example.test/api/v3", "", true);
        final String credential = new GitHubDeviceAuth.Token("access", "refresh", System.currentTimeMillis() + 60_000).encode();
        try {
            assertThatThrownBy(() -> settings.setGithubToken(server, credential)).isInstanceOf(IllegalArgumentException.class);
            assertThat(settings.githubCredential(server)).isEmpty();
        } finally {
            settings.clearGithubToken(server);
        }
    }

    public void testConcurrentRequestsShareOneTokenRefresh() throws Exception {
        final var settings = RemoteActionProviders.Settings.getInstance();
        final var server = new RemoteActionProviders.Server("test-" + java.util.UUID.randomUUID(),
                "https://github.com", "https://api.github.com", "", true);
        final var previous = settings.customServers();
        final AtomicInteger requests = new AtomicInteger();
        final CountDownLatch arrived = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/login/oauth/access_token", exchange -> {
            requests.incrementAndGet();
            arrived.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (final InterruptedException exception) { Thread.currentThread().interrupt(); }
            final byte[] body = "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":28800,\"token_type\":\"bearer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        final var pool = Executors.newFixedThreadPool(6);
        try {
            settings.setCustomServers(List.of(server));
            settings.setGithubToken(server, new GitHubDeviceAuth.Token("old-access", "old-refresh", 1).encode());
            final var client = TestClients.local(http.getAddress().getPort());
            final var results = new ArrayList<java.util.concurrent.Future<java.util.Optional<String>>>();
            for (int index = 0; index < 6; index++) results.add(pool.submit(() -> settings.githubTokenForRequest(server, client)));
            assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (final var result : results) assertThat(result.get(10, TimeUnit.SECONDS)).contains("new-access");
            assertThat(requests).hasValue(1);
            assertThat(settings.githubToken(server)).contains("new-access");
            assertThat(GitHubDeviceAuth.decode(settings.githubCredential(server).orElseThrow()).orElseThrow().refreshToken())
                    .isEqualTo("new-refresh");
        } finally {
            release.countDown();
            pool.shutdownNow();
            http.stop(0);
            settings.clearGithubToken(server).setCustomServers(previous);
        }
    }

    public void testDeletingAnAccountDuringRefreshDoesNotRestoreItsToken() throws Exception {
        final var settings = RemoteActionProviders.Settings.getInstance();
        final var server = new RemoteActionProviders.Server("test-" + java.util.UUID.randomUUID(),
                "https://github.com", "https://api.github.com", "", true);
        final var previous = settings.customServers();
        final CountDownLatch arrived = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/login/oauth/access_token", exchange -> {
            arrived.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (final InterruptedException exception) { Thread.currentThread().interrupt(); }
            final byte[] body = "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":28800,\"token_type\":\"bearer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        final var pool = Executors.newSingleThreadExecutor();
        try {
            settings.setCustomServers(List.of(server));
            settings.setGithubToken(server, new GitHubDeviceAuth.Token("old-access", "old-refresh", 1).encode());
            final var result = pool.submit(() -> settings.githubTokenForRequest(server, TestClients.local(http.getAddress().getPort())));
            assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
            settings.clearGithubToken(server);
            release.countDown();
            assertThat(result.get(10, TimeUnit.SECONDS)).isEmpty();
            assertThat(settings.githubCredential(server)).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
            http.stop(0);
            settings.clearGithubToken(server).setCustomServers(previous);
        }
    }
}
