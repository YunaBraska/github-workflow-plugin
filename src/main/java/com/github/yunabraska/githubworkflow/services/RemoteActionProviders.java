package com.github.yunabraska.githubworkflow.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RemoteActionProviders {

    private static final Logger LOG = Logger.getInstance(RemoteActionProviders.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static Optional<RemoteActionResolution> resolve(final String usesValue) {
        return RemoteServerSettings.getInstance().enabledServers().stream()
                .map(server -> resolve(server, usesValue))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static List<String> latestRefs(final String usesBase, final int limit) {
        if (limit < 1) {
            return List.of();
        }
        return RemoteServerSettings.getInstance().enabledServers().stream()
                .map(server -> RemoteUses.parseBase(server, usesBase)
                        .map(uses -> latestRefs(server, uses, limit))
                        .orElseGet(List::of))
                .filter(refs -> !refs.isEmpty())
                .findFirst()
                .orElseGet(List::of);
    }

    public static Map<String, String> searchUses(final String usesPrefix, final int limit) {
        if (limit < 1) {
            return Map.of();
        }
        return RemoteServerSettings.getInstance().enabledServers().stream()
                .map(server -> RemoteUsesPrefix.parse(server, usesPrefix)
                        .map(prefix -> searchUses(server, prefix, limit))
                        .orElseGet(Map::of))
                .filter(items -> !items.isEmpty())
                .findFirst()
                .orElseGet(Map::of);
    }

    private static Optional<RemoteActionResolution> resolve(final RemoteServerSettings.Server server, final String usesValue) {
        return RemoteUses.parse(server, usesValue).flatMap(remoteUses -> resolve(server, remoteUses));
    }

    private static Optional<RemoteActionResolution> resolve(final RemoteServerSettings.Server server, final RemoteUses uses) {
        for (final String metadataPath : metadataPaths(server, uses)) {
            final Optional<ContentResponse> content = getContent(server, uses.owner(), uses.repo(), metadataPath, uses.ref());
            if (content.isPresent()) {
                final List<String> refs = listRefs(server, uses.owner(), uses.repo());
                return Optional.of(new RemoteActionResolution(
                        uses.usesValue(),
                        uses.owner() + "/" + uses.repo(),
                        content.get().downloadUrl(),
                        htmlUrl(server, uses, metadataPath),
                        content.get().content(),
                        !isWorkflowPath(metadataPath),
                        refs
                ));
            }
        }
        return Optional.empty();
    }

    private static List<String> metadataPaths(final RemoteServerSettings.Server server, final RemoteUses uses) {
        if (isWorkflowPath(uses.path())) {
            return List.of(uses.path());
        }
        final String base = uses.path().isBlank() ? "" : uses.path() + "/";
        return List.of(base + "action.yml", base + "action.yaml");
    }

    private static boolean isWorkflowPath(final String path) {
        final String normalized = path.replace('\\', '/');
        return normalized.contains(".github/workflows/")
                && (normalized.endsWith(".yml") || normalized.endsWith(".yaml"));
    }

    private static Optional<ContentResponse> getContent(
            final RemoteServerSettings.Server server,
            final String owner,
            final String repo,
            final String path,
            final String ref
    ) {
        final String url = server.apiUrl + "/repos/" + encode(owner) + "/" + encode(repo) + "/contents/" + encodePath(path) + "?ref=" + encode(ref);
        return getJson(server, url).flatMap(json -> contentFromJson(json, url));
    }

    private static List<String> listRefs(final RemoteServerSettings.Server server, final String owner, final String repo) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String endpoint : List.of("branches", "tags")) {
            final String url = server.apiUrl + "/repos/" + encode(owner) + "/" + encode(repo) + "/" + endpoint;
            getJson(server, url).ifPresent(json -> namesFromJson(json).forEach(result::add));
        }
        return List.copyOf(result);
    }

    private static List<String> latestRefs(final RemoteServerSettings.Server server, final RemoteUses uses, final int limit) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String endpoint : List.of("tags", "branches")) {
            final String url = server.apiUrl + "/repos/" + encode(uses.owner()) + "/" + encode(uses.repo()) + "/" + endpoint + "?per_page=" + limit;
            getJson(server, url).ifPresent(json -> namesFromJson(json).forEach(result::add));
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream().limit(limit).toList();
    }

    private static Map<String, String> searchUses(final RemoteServerSettings.Server server, final RemoteUsesPrefix prefix, final int limit) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final String endpoint : List.of("users", "orgs")) {
            final String url = server.apiUrl + "/" + endpoint + "/" + encode(prefix.owner()) + "/repos?per_page=" + limit;
            getJson(server, url).ifPresent(json -> repoCompletionsFromJson(json, prefix, limit).forEach(result::putIfAbsent));
            if (result.size() >= limit) {
                break;
            }
        }
        return result.entrySet().stream()
                .limit(limit)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static Optional<JsonElement> getJson(final RemoteServerSettings.Server server, final String url) {
        for (final GitHubRequestAuthorizations.Authorization authorization : GitHubRequestAuthorizations.forApiUrl(server.apiUrl, server.tokenEnvVar, null)) {
            try {
                final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .header("Accept", "application/json")
                        .header("User-Agent", "GitHub-Workflow-Plugin");
                if (authorization.authenticated()) {
                    builder.header("Authorization", authorization.authorizationHeader());
                }
                final HttpResponse<String> response = CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() / 100 == 2) {
                    return Optional.of(JsonParser.parseString(response.body()));
                }
                if (!shouldTryNextAuthorization(response.statusCode())) {
                    return Optional.empty();
                }
            } catch (final IOException exception) {
                LOG.warn("Remote request failed [" + url + "]", exception);
                return Optional.empty();
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (final RuntimeException exception) {
                LOG.warn("Remote response failed [" + url + "]", exception);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean shouldTryNextAuthorization(final int statusCode) {
        return statusCode == 401 || statusCode == 403 || statusCode == 404 || statusCode == 429;
    }

    private static Optional<ContentResponse> contentFromJson(final JsonElement json, final String fallbackDownloadUrl) {
        if (!json.isJsonObject()) {
            return Optional.empty();
        }
        final JsonObject object = json.getAsJsonObject();
        final Optional<String> rawContent = stringValue(object, "content");
        if (rawContent.isEmpty()) {
            return Optional.empty();
        }
        final String content = new String(Base64.getMimeDecoder().decode(rawContent.get()), StandardCharsets.UTF_8);
        final String downloadUrl = stringValue(object, "download_url").orElse(fallbackDownloadUrl);
        return Optional.of(new ContentResponse(content, downloadUrl));
    }

    private static List<String> namesFromJson(final JsonElement json) {
        final List<String> result = new ArrayList<>();
        if (json.isJsonArray()) {
            final JsonArray array = json.getAsJsonArray();
            for (final JsonElement element : array) {
                if (element.isJsonObject()) {
                    stringValue(element.getAsJsonObject(), "name").ifPresent(result::add);
                }
            }
        }
        return result;
    }

    private static Map<String, String> repoCompletionsFromJson(final JsonElement json, final RemoteUsesPrefix prefix, final int limit) {
        final Map<String, String> result = new LinkedHashMap<>();
        if (json.isJsonArray()) {
            final JsonArray array = json.getAsJsonArray();
            for (final JsonElement element : array) {
                if (element.isJsonObject()) {
                    final JsonObject object = element.getAsJsonObject();
                    final Optional<String> name = stringValue(object, "name");
                    final Optional<String> fullName = stringValue(object, "full_name");
                    if (name.filter(value -> value.startsWith(prefix.repoPrefix())).isPresent()) {
                        result.putIfAbsent(
                                fullName.orElse(prefix.owner() + "/" + name.get()),
                                stringValue(object, "description").orElse(GitHubWorkflowBundle.message("completion.remote.repository"))
                        );
                    }
                }
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private static Optional<String> stringValue(final JsonObject object, final String name) {
        return Optional.ofNullable(object.get(name))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .filter(value -> !value.isBlank());
    }

    private static String htmlUrl(final RemoteServerSettings.Server server, final RemoteUses uses, final String metadataPath) {
        final String base = server.webUrl + "/" + uses.owner() + "/" + uses.repo();
        if (isWorkflowPath(metadataPath)) {
            return base + "/blob/" + uses.ref() + "/" + metadataPath;
        }
        final String actionPath = metadataPath.endsWith("/action.yml")
                ? metadataPath.substring(0, metadataPath.length() - "/action.yml".length())
                : metadataPath.endsWith("/action.yaml")
                ? metadataPath.substring(0, metadataPath.length() - "/action.yaml".length())
                : "";
        final String suffix = actionPath.isBlank() ? "" : "/" + actionPath;
        return base + "/tree/" + uses.ref() + suffix + "#readme";
    }

    private static String encodePath(final String path) {
        return List.of(path.split("/")).stream().map(RemoteActionProviders::encode).reduce((left, right) -> left + "/" + right).orElse("");
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record ContentResponse(String content, String downloadUrl) {
    }

    private record RemoteUses(String usesValue, String owner, String repo, String path, String ref) {

        static Optional<RemoteUses> parse(final RemoteServerSettings.Server server, final String value) {
            if (value == null || value.isBlank() || value.startsWith(".")) {
                return Optional.empty();
            }
            final String stripped = stripServerPrefix(server, value.trim()).orElse(null);
            if (stripped == null) {
                return Optional.empty();
            }
            final int atIndex = stripped.lastIndexOf('@');
            if (atIndex < 0 || atIndex == stripped.length() - 1) {
                return Optional.empty();
            }
            final String path = stripped.substring(0, atIndex);
            final String ref = stripped.substring(atIndex + 1);
            final String[] parts = path.split("/", 3);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RemoteUses(value.trim(), parts[0], parts[1], parts.length == 3 ? parts[2] : "", ref));
        }

        static Optional<RemoteUses> parseBase(final RemoteServerSettings.Server server, final String value) {
            if (value == null || value.isBlank() || value.startsWith(".")) {
                return Optional.empty();
            }
            final String stripped = stripServerPrefix(server, value.trim()).orElse(null);
            if (stripped == null) {
                return Optional.empty();
            }
            final int atIndex = stripped.lastIndexOf('@');
            final String path = atIndex < 0 ? stripped : stripped.substring(0, atIndex);
            final String[] parts = path.split("/", 3);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RemoteUses(value.trim(), parts[0], parts[1], parts.length == 3 ? parts[2] : "", ""));
        }

        private static Optional<String> stripServerPrefix(final RemoteServerSettings.Server server, final String value) {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                final String prefix = server.webUrl + "/";
                return value.startsWith(prefix) ? Optional.of(value.substring(prefix.length())) : Optional.empty();
            }
            return Optional.of(value);
        }
    }

    private record RemoteUsesPrefix(String owner, String repoPrefix) {

        static Optional<RemoteUsesPrefix> parse(final RemoteServerSettings.Server server, final String value) {
            if (value == null || value.isBlank() || value.startsWith(".") || value.contains("@")) {
                return Optional.empty();
            }
            final String stripped = RemoteUses.stripServerPrefix(server, value.trim()).orElse(null);
            if (stripped == null) {
                return Optional.empty();
            }
            final String[] parts = stripped.split("/", 3);
            if (parts.length < 2 || parts[0].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RemoteUsesPrefix(parts[0], parts[1]));
        }
    }

    private RemoteActionProviders() {
        // static helper
    }
}
