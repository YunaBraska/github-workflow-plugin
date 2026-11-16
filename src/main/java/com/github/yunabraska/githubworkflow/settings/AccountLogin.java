package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Verifies a token against the configured GitHub or Gitea server. */
public final class AccountLogin {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private AccountLogin() {
    }

    /** Outcome of a token verification request. */
    public enum Status { SUCCESS, MISSING_TOKEN, INVALID_TOKEN, INVALID_SERVER, UNAUTHORIZED, SERVER_ERROR, INVALID_RESPONSE, NETWORK_ERROR }

    /** A verified login name or an explicit failure status. */
    public record Result(Status status, Optional<String> login) {
    }

    /**
     * Verifies a personal access token against the provider's current-user endpoint.
     *
     * @param account provider and API URL selected by the user
     * @param token personal access token, never logged
     * @return the authenticated login name or the failure status
     * @throws InterruptedException if verification is cancelled
     */
    public static Result verify(final RemoteActionProviders.Server account, final String token) throws InterruptedException {
        if (token == null || token.isBlank()) {
            return new Result(Status.MISSING_TOKEN, Optional.empty());
        }
        if (token.chars().anyMatch(character -> character <= 0x20 || character > 0x7e)) {
            return new Result(Status.INVALID_TOKEN, Optional.empty());
        }
        final URI uri;
        try {
            uri = URI.create(account.normalized().apiUrl + "/user");
            if ((!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                return new Result(Status.INVALID_SERVER, Optional.empty());
            }
            for (final String segment : uri.getPath().split("/")) {
                if (segment.equals(".") || segment.equals("..")) {
                    return new Result(Status.INVALID_SERVER, Optional.empty());
                }
            }
        } catch (final IllegalArgumentException | NullPointerException exception) {
            return new Result(Status.INVALID_SERVER, Optional.empty());
        }
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "GitHub-Workflow-Plugin")
                .header("Authorization", (account.isGitea() ? "token " : "Bearer ") + token)
                .GET().build();
        final CompletableFuture<HttpResponse<byte[]>> exchange = CLIENT.sendAsync(request, info -> new LimitedBody());
        try {
            final HttpResponse<byte[]> response = exchange.get(10, TimeUnit.SECONDS);
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    return new Result(Status.UNAUTHORIZED, Optional.empty());
                }
                if (response.statusCode() / 100 != 2) {
                    return new Result(Status.SERVER_ERROR, Optional.empty());
                }
                final byte[] bytes = response.body();
                final var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                final var login = json.isJsonObject() ? json.getAsJsonObject().get("login") : null;
                if (login == null || !login.isJsonPrimitive() || !login.getAsJsonPrimitive().isString()
                        || login.getAsString().isBlank()) {
                    return new Result(Status.INVALID_RESPONSE, Optional.empty());
                }
                return new Result(Status.SUCCESS, Optional.of(login.getAsString()));
        } catch (final InterruptedException exception) {
            exchange.cancel(true);
            throw exception;
        } catch (final ExecutionException | TimeoutException exception) {
            exchange.cancel(true);
            return new Result(Status.NETWORK_ERROR, Optional.empty());
        } catch (final RuntimeException exception) {
            return new Result(Status.INVALID_RESPONSE, Optional.empty());
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private static final int LIMIT = 65_536;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(final Flow.Subscription next) {
            subscription = next;
            next.request(1);
        }
        @Override public void onNext(final List<ByteBuffer> buffers) {
            for (final ByteBuffer buffer : buffers) {
                if (buffer.remaining() > LIMIT - output.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Account response exceeds size limit"));
                    return;
                }
                final byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(final Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(output.toByteArray()); }
    }
}
