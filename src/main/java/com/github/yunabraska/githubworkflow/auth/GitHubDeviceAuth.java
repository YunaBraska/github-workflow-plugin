package com.github.yunabraska.githubworkflow.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
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

/** GitHub App device authorization and rotating user credentials. */
public final class GitHubDeviceAuth {
    private static final String CLIENT_ID = "Iv23liLiYVUHIaRkamuI";
    private static final String PREFIX = "github-workflow-device-v1:";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private GitHubDeviceAuth() {
    }

    /** @return a client restricted to GitHub.com */
    public static Client github() {
        return new Client(URI.create("https://github.com"), CLIENT_ID);
    }

    /**
     * @param value stored credential
     * @return whether the value is owned by the device flow
     */
    public static boolean isDeviceCredential(final String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * @param value stored credential
     * @return the device credential, or an empty result for a personal or invalid token
     */
    public static Optional<Token> decode(final String value) {
        if (value == null || !value.startsWith(PREFIX)) return Optional.empty();
        try {
            final JsonObject object = JsonParser.parseString(value.substring(PREFIX.length())).getAsJsonObject();
            final String access = object.get("access_token").getAsString();
            final String refresh = object.get("refresh_token").getAsString();
            final long expires = object.get("expires_at").getAsLong();
            if (access.isBlank() || refresh.isBlank() || expires <= 0) return Optional.empty();
            return Optional.of(new Token(access, refresh, expires));
        } catch (final RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Credentials serialized only for the IDE password store.
     * @param accessToken GitHub user access token
     * @param refreshToken token used to rotate the access token
     * @param expiresAtMillis access token expiry in epoch milliseconds
     */
    public record Token(String accessToken, String refreshToken, long expiresAtMillis) {
        @Override public String toString() { return "Token[redacted]"; }
        /** @return the credential representation for the IDE password store */
        public String encode() {
            final JsonObject object = new JsonObject();
            object.addProperty("access_token", accessToken);
            object.addProperty("refresh_token", refreshToken);
            object.addProperty("expires_at", expiresAtMillis);
            return PREFIX + object;
        }
    }

    /**
     * Short-lived verification code displayed to the user.
     * @param deviceCode secret device grant identifier
     * @param userCode code the user enters at GitHub
     * @param intervalSeconds minimum time between polling requests
     * @param expiresAtMillis code expiry in epoch milliseconds
     */
    public record DeviceCode(String deviceCode, String userCode, int intervalSeconds, long expiresAtMillis) {
        @Override public String toString() { return "DeviceCode[redacted]"; }
    }

    /** Client for the device authorization and token rotation endpoints. */
    public static final class Client {
        private final URI base;
        private final String clientId;

        Client(final URI base, final String clientId) {
            this.base = base;
            this.clientId = clientId;
        }

        /**
         * @return a code to enter at GitHub's device authorization page
         * @throws IOException if GitHub cannot provide a valid code
         * @throws InterruptedException if the request is cancelled
         */
        public DeviceCode start() throws IOException, InterruptedException {
            final JsonObject response = post("/login/device/code", "client_id=" + encode(clientId) + "&scope=offline_access");
            try {
                final String device = response.get("device_code").getAsString();
                final String user = response.get("user_code").getAsString();
                final int interval = response.get("interval").getAsInt();
                final int expires = response.get("expires_in").getAsInt();
                if (device.isBlank() || !user.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")
                        || interval < 1 || interval > 60 || expires < 1 || expires > 3600) throw new IOException("Invalid device response");
                return new DeviceCode(device, user, interval, System.currentTimeMillis() + expires * 1000L);
            } catch (final RuntimeException exception) {
                throw new IOException("Invalid device response", exception);
            }
        }

        /**
         * @param device the device code to authorize
         * @return the authorized user credential
         * @throws IOException if authorization fails or expires
         * @throws InterruptedException if polling is cancelled
         */
        public Token poll(final DeviceCode device) throws IOException, InterruptedException {
            int interval = device.intervalSeconds();
            while (System.currentTimeMillis() + interval * 1000L < device.expiresAtMillis()) {
                Thread.sleep(interval * 1000L);
                final JsonObject response = post("/login/oauth/access_token", "client_id=" + encode(clientId)
                        + "&device_code=" + encode(device.deviceCode())
                        + "&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code");
                if (response.has("error")) {
                    final String error = response.get("error").getAsString();
                    if ("authorization_pending".equals(error)) continue;
                    if ("slow_down".equals(error)) { interval = Math.min(interval + 5, 120); continue; }
                    throw new IOException("GitHub authorization failed: " + error.replaceAll("[^a-z_]", ""));
                }
                return token(response);
            }
            throw new IOException("GitHub authorization expired");
        }

        /**
         * @param refreshToken the current refresh token
         * @return a rotated credential
         * @throws IOException if GitHub rejects or cannot complete the refresh
         * @throws InterruptedException if the request is cancelled
         */
        public Token refresh(final String refreshToken) throws IOException, InterruptedException {
            return token(post("/login/oauth/access_token", "client_id=" + encode(clientId)
                    + "&grant_type=refresh_token&refresh_token=" + encode(refreshToken)));
        }

        private JsonObject post(final String path, final String form) throws IOException, InterruptedException {
            final HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "GitHub-Workflow-Plugin")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            final CompletableFuture<HttpResponse<byte[]>> exchange = HTTP.sendAsync(request, info -> new LimitedBody());
            try {
                final HttpResponse<byte[]> response = exchange.get(10, TimeUnit.SECONDS);
                if (response.statusCode() != 200) throw new IOException("GitHub authorization request failed: " + response.statusCode());
                try {
                    return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (final RuntimeException exception) {
                    throw new IOException("Invalid GitHub authorization response", exception);
                }
            } catch (final InterruptedException exception) {
                exchange.cancel(true);
                throw exception;
            } catch (final ExecutionException | TimeoutException exception) {
                exchange.cancel(true);
                throw new IOException("GitHub authorization request failed", exception);
            }
        }

        private static Token token(final JsonObject response) throws IOException {
            try {
                final String access = response.get("access_token").getAsString();
                final String refresh = response.get("refresh_token").getAsString();
                final long expires = response.get("expires_in").getAsLong();
                if (access.isBlank() || refresh.isBlank() || expires < 1 || expires > 86_400
                        || !"bearer".equalsIgnoreCase(response.get("token_type").getAsString())) throw new IOException("Invalid GitHub token response");
                return new Token(access, refresh, System.currentTimeMillis() + expires * 1000);
            } catch (final RuntimeException exception) {
                throw new IOException("Invalid GitHub token response", exception);
            }
        }

        private static String encode(final String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(final Flow.Subscription next) { subscription = next; next.request(1); }
        @Override public void onNext(final List<ByteBuffer> buffers) {
            for (final ByteBuffer buffer : buffers) {
                if (buffer.remaining() > 65_536 - output.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("GitHub authorization response is too large"));
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
