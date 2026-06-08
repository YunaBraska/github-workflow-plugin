package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

public class RemoteActionProviders {

    private static final Logger LOG = Logger.getInstance(RemoteActionProviders.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static Optional<Resolution> resolve(final String usesValue) {
        return firstPresent(server -> resolve(server, usesValue));
    }

    public record Resolution(
            String usesValue,
            String name,
            String downloadUrl,
            String githubUrl,
            String content,
            boolean action,
            List<String> refs
    ) {
    }

    public static List<String> latestRefs(final String usesBase, final int limit) {
        if (limit < 1) {
            return List.of();
        }
        return firstUseful(
                server -> RemoteUses.parseBase(server, usesBase)
                        .map(uses -> latestRefs(server, uses, limit))
                        .orElseGet(List::of),
                refs -> !refs.isEmpty(),
                List.of()
        );
    }

    public static Map<String, String> searchUses(final String usesPrefix, final int limit) {
        if (limit < 1) {
            return Map.of();
        }
        return firstUseful(
                server -> RemoteUsesPrefix.parse(server, usesPrefix)
                        .map(prefix -> searchUses(server, prefix, limit))
                        .orElseGet(Map::of),
                items -> !items.isEmpty(),
                Map.of()
        );
    }

    private static <T> Optional<T> firstPresent(final Function<Server, Optional<T>> resolver) {
        return Settings.getInstance().enabledServers().stream()
                .map(resolver)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static <T> T firstUseful(
            final Function<Server, T> resolver,
            final Predicate<T> useful,
            final T empty
    ) {
        return Settings.getInstance().enabledServers().stream()
                .map(resolver)
                .filter(useful)
                .findFirst()
                .orElse(empty);
    }

    private static Optional<Resolution> resolve(final Server server, final String usesValue) {
        return RemoteUses.parse(server, usesValue).flatMap(remoteUses -> resolve(server, remoteUses));
    }

    private static Optional<Resolution> resolve(final Server server, final RemoteUses uses) {
        for (final String metadataPath : metadataPaths(server, uses)) {
            final Optional<ContentResponse> content = getContent(server, uses.owner(), uses.repo(), metadataPath, uses.ref());
            if (content.isPresent()) {
                final List<String> refs = listRefs(server, uses.owner(), uses.repo());
                return Optional.of(new Resolution(
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

    private static List<String> metadataPaths(final Server server, final RemoteUses uses) {
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
            final Server server,
            final String owner,
            final String repo,
            final String path,
            final String ref
    ) {
        final String url = server.apiUrl + "/repos/" + encode(owner) + "/" + encode(repo) + "/contents/" + encodePath(path) + "?ref=" + encode(ref);
        return getJson(server, url).flatMap(json -> contentFromJson(json, url));
    }

    private static List<String> listRefs(final Server server, final String owner, final String repo) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String endpoint : List.of("branches", "tags")) {
            final String url = server.apiUrl + "/repos/" + encode(owner) + "/" + encode(repo) + "/" + endpoint;
            getJson(server, url).ifPresent(json -> namesFromJson(json).forEach(result::add));
        }
        return List.copyOf(result);
    }

    private static List<String> latestRefs(final Server server, final RemoteUses uses, final int limit) {
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

    private static Map<String, String> searchUses(final Server server, final RemoteUsesPrefix prefix, final int limit) {
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

    private static Optional<JsonElement> getJson(final Server server, final String url) {
        for (final RemoteActionProviders.Authorizations.Authorization authorization : RemoteActionProviders.Authorizations.forApiUrl(server.apiUrl, server.tokenEnvVar, null)) {
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

    private static String htmlUrl(final Server server, final RemoteUses uses, final String metadataPath) {
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

        static Optional<RemoteUses> parse(final Server server, final String value) {
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

        static Optional<RemoteUses> parseBase(final Server server, final String value) {
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

        private static Optional<String> stripServerPrefix(final Server server, final String value) {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                final String prefix = server.webUrl + "/";
                return value.startsWith(prefix) ? Optional.of(value.substring(prefix.length())) : Optional.empty();
            }
            return Optional.of(value);
        }
    }

    private record RemoteUsesPrefix(String owner, String repoPrefix) {

        static Optional<RemoteUsesPrefix> parse(final Server server, final String value) {
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

    public static class Settings {

        public static final String TYPE_GITHUB = "github";

        private final CopyOnWriteArrayList<Server> testServers = new CopyOnWriteArrayList<>();

        public static Settings getInstance() {
            return ApplicationManager.getApplication().getService(Settings.class);
        }

        public List<Server> enabledServers() {
            final Map<String, Server> result = new LinkedHashMap<>();
            testServers.stream()
                    .map(Server::normalized)
                    .filter(Server::isValid)
                    .forEach(server -> result.put(server.key(), server));
            jetBrainsGithubServers().forEach(server -> result.putIfAbsent(server.key(), server));
            final Server defaultGitHub = defaultGitHub();
            result.putIfAbsent(defaultGitHub.key(), defaultGitHub);
            return List.copyOf(result.values());
        }

        public void setCustomServers(final List<Server> servers) {
            testServers.clear();
            Optional.ofNullable(servers).orElseGet(List::of).stream()
                    .map(Server::normalized)
                    .filter(Server::isValid)
                    .forEach(testServers::add);
        }

        public static Server defaultGitHub() {
            return new Server("GitHub", "https://github.com", "https://api.github.com", "", true);
        }

        private static List<Server> jetBrainsGithubServers() {
            try {
                return GHAccountsUtil.getAccounts().stream()
                        .sorted((left, right) -> {
                            final int order = Integer.compare(accountOrder(left), accountOrder(right));
                            return order == 0 ? left.getName().compareTo(right.getName()) : order;
                        })
                        .map(account -> new Server(
                                account.getName(),
                                account.getServer().toUrl(),
                                account.getServer().toApiUrl(),
                                "",
                                true
                        ))
                        .map(Server::normalized)
                        .filter(Server::isValid)
                        .toList();
            } catch (final RuntimeException ignored) {
                return List.of();
            }
        }

        private static int accountOrder(final GithubAccount account) {
            return account.getServer().isGithubDotCom() ? 0 : 1;
        }
    }

    public static class Server {
        public final String type;
        public final String name;
        public final String webUrl;
        public final String apiUrl;
        public final String tokenEnvVar;
        public final boolean enabled;

        public Server(
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled
        ) {
            this.type = Settings.TYPE_GITHUB;
            this.name = name;
            this.webUrl = webUrl;
            this.apiUrl = apiUrl;
            this.tokenEnvVar = tokenEnvVar;
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isValid() {
            return isEnabled() && hasText(webUrl) && hasText(apiUrl);
        }

        public String authorizationHeader() {
            return Optional.ofNullable(tokenEnvVar)
                    .filter(RemoteActionProviders::hasText)
                    .map(System::getenv)
                    .filter(RemoteActionProviders::hasText)
                    .map(token -> "Bearer " + token)
                    .orElse("");
        }

        public Server normalized() {
            return new Server(
                    hasText(name) ? name.trim() : webUrl,
                    trimTrailingSlash(webUrl),
                    trimTrailingSlash(apiUrl),
                    Optional.ofNullable(tokenEnvVar).map(String::trim).orElse(""),
                    enabled
            );
        }

        private String key() {
            final Server normalized = normalized();
            return normalized.type + "|" + normalized.webUrl + "|" + normalized.apiUrl;
        }
    }

    public static class Authorizations {

        private static final List<String> DEFAULT_ENV_TOKENS = List.of("GITHUB_TOKEN", "GH_TOKEN", "GITHUB_PAT");

        public static List<Authorization> forApiUrl(final String apiUrl, final String tokenEnvVar, final Project project) {
            return forApiUrl(apiUrl, tokenEnvVar, project, System.getenv());
        }

        public static List<Authorization> forApiUrl(
                final String apiUrl,
                final String tokenEnvVar,
                final Project project,
                final Map<String, String> environment
        ) {
            final LinkedHashMap<String, Authorization> result = new LinkedHashMap<>();
            orderedAccountsFor(apiUrl).stream()
                    .map(account -> authorization(account, project))
                    .flatMap(Optional::stream)
                    .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
            envAuthorizations(tokenEnvVar, environment)
                    .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
            result.putIfAbsent(Authorization.anonymous().key(), Authorization.anonymous());
            return List.copyOf(result.values());
        }

        public static String settingsHint() {
            return GitHubWorkflowBundle.message("workflow.run.auth.settings");
        }

        private static List<GithubAccount> orderedAccountsFor(final String apiUrl) {
            return accounts().stream()
                    .sorted(Comparator
                            .comparingInt((GithubAccount account) -> accountPriority(account, apiUrl))
                            .thenComparing(account -> account.getServer().toApiUrl())
                            .thenComparing(GithubAccount::getName))
                    .toList();
        }

        private static int accountPriority(final GithubAccount account, final String apiUrl) {
            if (sameHost(account.getServer().toApiUrl(), apiUrl)) {
                return 0;
            }
            return account.getServer().isGithubDotCom() ? 1 : 2;
        }

        private static List<GithubAccount> accounts() {
            try {
                return new ArrayList<>(GHAccountsUtil.getAccounts());
            } catch (final RuntimeException ignored) {
                return List.of();
            }
        }

        private static Optional<Authorization> authorization(final GithubAccount account, final Project project) {
            try {
                return Optional.ofNullable(GHCompatibilityUtil.getOrRequestToken(account, project(project)))
                        .filter(RemoteActionProviders::hasText)
                        .map(token -> new Authorization(account.getName(), "Bearer " + token));
            } catch (final RuntimeException ignored) {
                return Optional.empty();
            }
        }

        private static List<Authorization> envAuthorizations(final String tokenEnvVar, final Map<String, String> environment) {
            final LinkedHashMap<String, Authorization> result = new LinkedHashMap<>();
            envAuthorization(tokenEnvVar, environment).ifPresent(authorization -> result.putIfAbsent(authorization.key(), authorization));
            DEFAULT_ENV_TOKENS.stream()
                    .filter(name -> !name.equals(tokenEnvVar))
                    .map(name -> envAuthorization(name, environment))
                    .flatMap(Optional::stream)
                    .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
            return List.copyOf(result.values());
        }

        private static Optional<Authorization> envAuthorization(final String tokenEnvVar, final Map<String, String> environment) {
            return Optional.ofNullable(tokenEnvVar)
                    .map(String::trim)
                    .filter(RemoteActionProviders::hasText)
                    .flatMap(name -> Optional.ofNullable(environment.get(name))
                            .filter(RemoteActionProviders::hasText)
                            .map(token -> new Authorization(name, "Bearer " + token)));
        }

        private static Project project(final Project project) {
            return Optional.ofNullable(project)
                    .or(() -> Optional.ofNullable(ProjectUtil.getActiveProject()))
                    .orElseGet(() -> ProjectManager.getInstance().getDefaultProject());
        }

        private static boolean sameHost(final String left, final String right) {
            final Optional<String> leftHost = host(left);
            final Optional<String> rightHost = host(right);
            return leftHost.isPresent() && leftHost.equals(rightHost);
        }

        private static Optional<String> host(final String value) {
            try {
                return Optional.ofNullable(URI.create(value).getHost())
                        .map(String::toLowerCase);
            } catch (final RuntimeException ignored) {
                return Optional.empty();
            }
        }

        public record Authorization(String source, String authorizationHeader) {

            public static Authorization anonymous() {
                return new Authorization("anonymous", "");
            }

            public boolean authenticated() {
                return hasText(authorizationHeader);
            }

            String key() {
                return source + "|" + authorizationHeader;
            }
        }
    }

    private RemoteActionProviders() {
        // static helper
    }

    private static String trimTrailingSlash(final String value) {
        final String trimmed = Optional.ofNullable(value).map(String::trim).orElse("");
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
