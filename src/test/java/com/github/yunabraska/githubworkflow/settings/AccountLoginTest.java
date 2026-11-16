package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountLoginTest {
    @Test
    public void githubLoginValidatesTheTokenAndReturnsTheRealAccountName() throws Exception {
        final AtomicReference<String> authorization = new AtomicReference<>();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            final byte[] body = "{\"login\":\"octocat\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort();
            final var account = new RemoteActionProviders.Server("GitHub", url, url, "", true);
            final var result = AccountLogin.verify(account, "test-token");
            assertThat(result.status()).isEqualTo(AccountLogin.Status.SUCCESS);
            assertThat(result.login()).contains("octocat");
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void giteaLoginUsesItsApiAndTokenScheme() throws Exception {
        final AtomicReference<String> authorization = new AtomicReference<>();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/user", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            final byte[] body = "{\"login\":\"gitea-user\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort();
            final var account = RemoteActionProviders.Server.gitea("Gitea", url, url + "/api/v1", "", true);
            final var result = AccountLogin.verify(account, "test-token");
            assertThat(result.status()).isEqualTo(AccountLogin.Status.SUCCESS);
            assertThat(result.login()).contains("gitea-user");
            assertThat(authorization.get()).isEqualTo("token test-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void invalidCredentialsProduceAnExplicitFailure() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort();
            final var result = AccountLogin.verify(new RemoteActionProviders.Server("GitHub", url, url, "", true), "invalid");
            assertThat(result.status()).isEqualTo(AccountLogin.Status.UNAUTHORIZED);
            assertThat(result.login()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void missingTokenFailsWithoutConnecting() throws Exception {
        final var account = new RemoteActionProviders.Server("GitHub", "http://127.0.0.1:1", "http://127.0.0.1:1", "", true);
        assertThat(AccountLogin.verify(account, "").status()).isEqualTo(AccountLogin.Status.MISSING_TOKEN);
    }

    @Test
    public void tokenContainingControlCharactersFailsWithoutConnecting() throws Exception {
        final var account = new RemoteActionProviders.Server("GitHub", "http://127.0.0.1:1", "http://127.0.0.1:1", "", true);
        assertThat(AccountLogin.verify(account, "invalid\ntoken").status()).isEqualTo(AccountLogin.Status.INVALID_TOKEN);
    }

    @Test
    public void authenticationRedirectDoesNotSendTokenToAnotherServer() throws Exception {
        final AtomicReference<String> leakedAuthorization = new AtomicReference<>();
        final HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/user", exchange -> {
            leakedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        target.start();
        final HttpServer source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/user", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + target.getAddress().getPort() + "/user");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        source.start();
        try {
            final String url = "http://127.0.0.1:" + source.getAddress().getPort();
            final var account = new RemoteActionProviders.Server("GitHub", url, url, "", true);
            assertThat(AccountLogin.verify(account, "test-token").status()).isEqualTo(AccountLogin.Status.SERVER_ERROR);
            assertThat(leakedAuthorization.get()).isNull();
        } finally {
            source.stop(0);
            target.stop(0);
        }
    }

    @Test
    public void malformedProfileIsNotAccepted() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user", exchange -> {
            final byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(AccountLogin.verify(new RemoteActionProviders.Server("GitHub", url, url, "", true), "test-token").status())
                    .isEqualTo(AccountLogin.Status.INVALID_RESPONSE);
        } finally {
            server.stop(0);
        }
    }
}
