package com.github.yunabraska.githubworkflow.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GitHubDeviceAuthTest {
    @Test
    public void deviceAuthorizationAndRefreshRotateCredentials() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicInteger polls = new AtomicInteger();
        server.createContext("/login/device/code", exchange -> respond(exchange,
                "{\"device_code\":\"device\",\"user_code\":\"ABCD-EFGH\",\"interval\":1,\"expires_in\":30}"));
        server.createContext("/login/oauth/access_token", exchange -> {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("grant_type=refresh_token")) {
                assertThat(body).contains("refresh_token=refresh-one");
                respond(exchange, "{\"access_token\":\"access-two\",\"refresh_token\":\"refresh-two\",\"expires_in\":28800,\"token_type\":\"bearer\"}");
            } else if (polls.getAndIncrement() == 0) {
                respond(exchange, "{\"error\":\"authorization_pending\"}");
            } else {
                respond(exchange, "{\"access_token\":\"access-one\",\"refresh_token\":\"refresh-one\",\"expires_in\":28800,\"token_type\":\"bearer\"}");
            }
        });
        server.start();
        try {
            final var client = new GitHubDeviceAuth.Client(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "client");
            final var device = client.start();
            assertThat(device.userCode()).isEqualTo("ABCD-EFGH");
            final var initial = client.poll(device);
            assertThat(initial.accessToken()).isEqualTo("access-one");
            assertThat(GitHubDeviceAuth.decode(initial.encode())).contains(initial);
            final var rotated = client.refresh(initial.refreshToken());
            assertThat(rotated.accessToken()).isEqualTo("access-two");
            assertThat(rotated.refreshToken()).isEqualTo("refresh-two");
            assertThat(polls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void deniedLoginFailsWithoutCreatingCredentials() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login/device/code", exchange -> respond(exchange,
                "{\"device_code\":\"device\",\"user_code\":\"ABCD-EFGH\",\"interval\":1,\"expires_in\":30}"));
        server.createContext("/login/oauth/access_token", exchange -> respond(exchange, "{\"error\":\"access_denied\"}"));
        server.start();
        try {
            final var client = new GitHubDeviceAuth.Client(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "client");
            assertThatThrownBy(() -> client.poll(client.start())).hasMessageContaining("access_denied");
            assertThat(GitHubDeviceAuth.decode("ordinary-personal-access-token")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsInvalidDeviceResponses() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login/device/code", exchange -> respond(exchange,
                "{\"device_code\":\"device\",\"user_code\":\"invalid\",\"interval\":1,\"expires_in\":30}"));
        server.start();
        try {
            assertThatThrownBy(() -> TestClients.local(server.getAddress().getPort()).start())
                    .hasMessageContaining("Invalid device response");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void devicePollingCanBeCancelled() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login/device/code", exchange -> respond(exchange,
                "{\"device_code\":\"device\",\"user_code\":\"ABCD-EFGH\",\"interval\":30,\"expires_in\":60}"));
        server.start();
        final var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            final var client = TestClients.local(server.getAddress().getPort());
            final var code = client.start();
            final var started = new java.util.concurrent.CountDownLatch(1);
            final var future = pool.submit(() -> { started.countDown(); return client.poll(code); });
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(future.cancel(true)).isTrue();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
            server.stop(0);
        }
    }

    private static void respond(final HttpExchange exchange, final String response) throws java.io.IOException {
        final byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
