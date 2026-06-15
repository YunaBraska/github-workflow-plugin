package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
        return (normalized.contains(".github/workflows/") || normalized.contains(".gitea/workflows/"))
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
        for (final RemoteActionProviders.Authorizations.Authorization authorization : RemoteActionProviders.Authorizations.forServer(server, null)) {
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

    @State(name = "GitHubWorkflowRemoteSettings", storages = {@Storage("githubWorkflowRemoteSettings.xml")})
    public static class Settings implements PersistentStateComponent<Settings.StateData> {

        public static final String TYPE_GITHUB = "github";
        public static final String TYPE_GITEA = "gitea";

        public static class StateData {
            public List<ServerState> servers = new ArrayList<>();
        }

        public static class ServerState {
            public String type = TYPE_GITHUB;
            public String name = "";
            public String webUrl = "";
            public String apiUrl = "";
            public String tokenEnvVar = "";
            public boolean enabled = true;
            public boolean tokenStored;

            public ServerState() {
                // XML serializer
            }

            ServerState(final Server server) {
                final Server normalized = server.normalized();
                type = normalized.type;
                name = normalized.name;
                webUrl = normalized.webUrl;
                apiUrl = normalized.apiUrl;
                tokenEnvVar = normalized.tokenEnvVar;
                enabled = normalized.enabled;
                tokenStored = normalized.tokenStored;
            }

            Server server() {
                return new Server(type, name, webUrl, apiUrl, tokenEnvVar, enabled, tokenStored).normalized();
            }
        }

        private final StateData state = new StateData();

        public static Settings getInstance() {
            return ApplicationManager.getApplication().getService(Settings.class);
        }

        @Override
        public @Nullable StateData getState() {
            return state;
        }

        @Override
        public void loadState(@NotNull final StateData state) {
            XmlSerializerUtil.copyBean(state, this.state);
            if (this.state.servers == null) {
                this.state.servers = new ArrayList<>();
            }
        }

        /**
         * Returns user-configured remote providers stored by this plugin.
         *
         * @return normalized configured providers, excluding invalid rows
         */
        public List<Server> customServers() {
            return Optional.ofNullable(state.servers).orElseGet(List::of).stream()
                    .map(ServerState::server)
                    .filter(Server::isValid)
                    .toList();
        }

        /**
         * Replaces the user-configured remote providers stored by this plugin.
         *
         * @param servers provider metadata to persist; invalid rows are ignored
         * @return this settings service
         */
        public Settings setCustomServers(final List<Server> servers) {
            state.servers = Optional.ofNullable(servers).orElseGet(List::of).stream()
                    .map(Server::normalized)
                    .filter(Server::isValid)
                    .map(ServerState::new)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            return this;
        }

        /**
         * Stores or replaces the Gitea token for the supplied provider in the IDE password safe.
         *
         * @param server provider whose normalized API URL identifies the token
         * @param token personal access token
         * @return this settings service
         */
        public Settings setGiteaToken(final Server server, final String token) {
            final Server normalized = Optional.ofNullable(server).orElseGet(Settings::defaultGitHub).normalized();
            if (normalized.isGitea() && hasText(token)) {
                PasswordTokens.set(normalized, token);
                markGiteaTokenStored(normalized, true);
            }
            return this;
        }

        /**
         * Deletes the Gitea token for the supplied provider from the IDE password safe.
         *
         * @param server provider whose normalized API URL identifies the token
         * @return this settings service
         */
        public Settings clearGiteaToken(final Server server) {
            final Server normalized = Optional.ofNullable(server).orElseGet(Settings::defaultGitHub).normalized();
            if (normalized.isGitea()) {
                PasswordTokens.clear(normalized);
                markGiteaTokenStored(normalized, false);
            }
            return this;
        }

        /**
         * Reads the Gitea token for the supplied provider from the IDE password safe.
         *
         * @param server provider whose normalized API URL identifies the token
         * @return token when one is stored and readable
         */
        public Optional<String> giteaToken(final Server server) {
            final Server normalized = Optional.ofNullable(server).orElseGet(Settings::defaultGitHub).normalized();
            return normalized.isGitea() ? PasswordTokens.get(normalized) : Optional.empty();
        }

        /**
         * Reports whether the supplied Gitea provider has an IDE-stored token.
         *
         * @param server provider whose normalized API URL identifies the token
         * @return true when a token is stored
         */
        public boolean hasGiteaToken(final Server server) {
            final Server normalized = Optional.ofNullable(server).orElseGet(Settings::defaultGitHub).normalized();
            if (!normalized.isGitea()) {
                return false;
            }
            return normalized.tokenStored || customServers().stream()
                    .map(Server::normalized)
                    .anyMatch(candidate -> candidate.key().equals(normalized.key()) && candidate.tokenStored);
        }

        private Settings markGiteaTokenStored(final Server server, final boolean stored) {
            state.servers.stream()
                    .filter(state -> state.server().key().equals(server.key()))
                    .forEach(state -> state.tokenStored = stored);
            return this;
        }

        public List<Server> enabledServers() {
            final Map<String, Server> result = new LinkedHashMap<>();
            customServers().stream()
                    .map(Server::normalized)
                    .filter(Server::isValid)
                    .forEach(server -> result.put(server.key(), server));
            jetBrainsGithubServers().forEach(server -> result.putIfAbsent(server.key(), server));
            final Server defaultGitHub = defaultGitHub();
            result.putIfAbsent(defaultGitHub.key(), defaultGitHub);
            return List.copyOf(result.values());
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

        private static class PasswordTokens {

            private static final String SERVICE_PREFIX = "GitHub Workflow Gitea ";

            private static Optional<String> get(final Server server) {
                try {
                    return Optional.ofNullable(PasswordSafe.getInstance().get(attributes(server)))
                            .map(Credentials::getPasswordAsString)
                            .filter(RemoteActionProviders::hasText);
                } catch (final RuntimeException ignored) {
                    return Optional.empty();
                }
            }

            private static void set(final Server server, final String token) {
                write(() -> setNow(server, token));
            }

            private static void clear(final Server server) {
                write(() -> clearNow(server));
            }

            private static void write(final Runnable operation) {
                final Application application = ApplicationManager.getApplication();
                if (application != null && application.isDispatchThread()) {
                    try {
                        application.executeOnPooledThread(operation).get();
                    } catch (final InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Gitea token write interrupted", exception);
                    } catch (final ExecutionException exception) {
                        LOG.warn("Gitea token write failed", exception);
                    }
                    return;
                }
                operation.run();
            }

            private static void setNow(final Server server, final String token) {
                try {
                    PasswordSafe.getInstance().set(attributes(server), new Credentials(server.name, token));
                } catch (final RuntimeException exception) {
                    LOG.warn("Gitea token storage failed for [" + server.apiUrl + "]", exception);
                }
            }

            private static void clearNow(final Server server) {
                try {
                    PasswordSafe.getInstance().set(attributes(server), null);
                } catch (final RuntimeException exception) {
                    LOG.warn("Gitea token deletion failed for [" + server.apiUrl + "]", exception);
                }
            }

            private static CredentialAttributes attributes(final Server server) {
                return new CredentialAttributes(SERVICE_PREFIX + server.normalized().apiUrl);
            }
        }
    }

    public static class Server {
        public final String type;
        public final String name;
        public final String webUrl;
        public final String apiUrl;
        public final String tokenEnvVar;
        public final boolean enabled;
        public final boolean tokenStored;

        public Server(
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled
        ) {
            this(Settings.TYPE_GITHUB, name, webUrl, apiUrl, tokenEnvVar, enabled);
        }

        public Server(
                final String type,
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled
        ) {
            this(type, name, webUrl, apiUrl, tokenEnvVar, enabled, false);
        }

        public Server(
                final String type,
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled,
                final boolean tokenStored
        ) {
            this.type = Optional.ofNullable(type).map(String::trim).filter(RemoteActionProviders::hasText).orElse(Settings.TYPE_GITHUB);
            this.name = name;
            this.webUrl = webUrl;
            this.apiUrl = apiUrl;
            this.tokenEnvVar = tokenEnvVar;
            this.enabled = enabled;
            this.tokenStored = tokenStored;
        }

        /**
         * Creates a configured Gitea remote provider. Gitea uses GitHub-compatible repository endpoints under
         * {@code /api/v1}, but personal access tokens use {@code Authorization: token ...}.
         *
         * @param name readable server name shown in diagnostics
         * @param webUrl browser URL of the Gitea instance
         * @param apiUrl REST API URL, usually {@code <webUrl>/api/v1}
         * @param tokenEnvVar optional environment variable containing a Gitea token
         * @param enabled whether the provider should be used
         * @return a Gitea server configuration
         */
        public static Server gitea(
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled
        ) {
            return gitea(name, webUrl, apiUrl, tokenEnvVar, enabled, false);
        }

        public static Server gitea(
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled,
                final boolean tokenStored
        ) {
            return new Server(Settings.TYPE_GITEA, name, webUrl, apiUrl, tokenEnvVar, enabled, tokenStored);
        }

        /**
         * Infers the remote provider for a workflow-run request. Gitea is detected from its workflow home,
         * {@code /api/v1} API base, or a Gitea-named token variable.
         *
         * @param apiUrl REST API URL configured for the run
         * @param workflowPath workflow file path being dispatched
         * @param tokenEnvVar optional token environment variable configured for the run
         * @return normalized remote provider configuration
         */
        public static Server fromWorkflowRun(
                final String apiUrl,
                final String workflowPath,
                final String tokenEnvVar
        ) {
            final String normalizedApiUrl = trimTrailingSlash(apiUrl);
            final String webUrl = webUrlFromApiUrl(normalizedApiUrl);
            if (looksLikeGitea(normalizedApiUrl, workflowPath, tokenEnvVar)) {
                return gitea("Gitea", webUrl, normalizedApiUrl, tokenEnvVar, true).normalized();
            }
            return new Server("GitHub", webUrl, normalizedApiUrl, tokenEnvVar, true).normalized();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isValid() {
            return isEnabled() && hasText(webUrl) && hasText(apiUrl);
        }

        /**
         * Reports whether this provider uses Gitea-compatible request behavior.
         *
         * @return {@code true} for Gitea providers
         */
        public boolean isGitea() {
            return Settings.TYPE_GITEA.equalsIgnoreCase(type);
        }

        public String authorizationHeader() {
            return Settings.getInstance().giteaToken(this)
                    .or(() -> Optional.ofNullable(tokenEnvVar)
                            .filter(RemoteActionProviders::hasText)
                            .map(System::getenv)
                            .filter(RemoteActionProviders::hasText))
                    .map(token -> authorizationPrefix() + token)
                    .orElse("");
        }

        public Server normalized() {
            return new Server(
                    hasText(type) ? type.trim().toLowerCase() : Settings.TYPE_GITHUB,
                    hasText(name) ? name.trim() : webUrl,
                    trimTrailingSlash(webUrl),
                    trimTrailingSlash(apiUrl),
                    Optional.ofNullable(tokenEnvVar).map(String::trim).orElse(""),
                    enabled,
                    tokenStored
            );
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Server server)) {
                return false;
            }
            final Server left = normalized();
            final Server right = server.normalized();
            return left.enabled == right.enabled
                    && left.tokenStored == right.tokenStored
                    && Objects.equals(left.type, right.type)
                    && Objects.equals(left.name, right.name)
                    && Objects.equals(left.webUrl, right.webUrl)
                    && Objects.equals(left.apiUrl, right.apiUrl)
                    && Objects.equals(left.tokenEnvVar, right.tokenEnvVar);
        }

        @Override
        public int hashCode() {
            final Server normalized = normalized();
            return Objects.hash(normalized.type, normalized.name, normalized.webUrl, normalized.apiUrl, normalized.tokenEnvVar, normalized.enabled, normalized.tokenStored);
        }

        private String authorizationPrefix() {
            return isGitea() ? "token " : "Bearer ";
        }

        private String key() {
            final Server normalized = normalized();
            return normalized.type + "|" + normalized.webUrl + "|" + normalized.apiUrl;
        }
    }

    public static class Authorizations {

        private static final List<String> DEFAULT_GITHUB_ENV_TOKENS = List.of("GITHUB_TOKEN", "GH_TOKEN", "GITHUB_PAT");
        private static final List<String> DEFAULT_GITEA_ENV_TOKENS = List.of("GITEA_TOKEN", "GITEA_PAT");

        public static List<Authorization> forApiUrl(final String apiUrl, final String tokenEnvVar, final Project project) {
            return forApiUrl(apiUrl, tokenEnvVar, project, System.getenv());
        }

        public static List<Authorization> forApiUrl(
                final String apiUrl,
                final String tokenEnvVar,
                final Project project,
                final Map<String, String> environment
        ) {
            return forGithubApiUrl(apiUrl, tokenEnvVar, project, environment);
        }

        /**
         * Returns authorizations for a workflow-run request. Gitea run requests use Gitea token variables with
         * {@code token ...}; GitHub requests keep JetBrains GitHub account and {@code Bearer ...} token priority.
         *
         * @param apiUrl REST API URL configured for the run
         * @param workflowPath workflow file path being dispatched
         * @param tokenEnvVar optional token environment variable configured for the run
         * @param project project used when JetBrains GitHub tokens need to be requested
         * @return ordered authorizations ending with anonymous access
         */
        public static List<Authorization> forWorkflowRun(
                final String apiUrl,
                final String workflowPath,
                final String tokenEnvVar,
                final Project project
        ) {
            return forWorkflowRun(apiUrl, workflowPath, tokenEnvVar, project, System.getenv());
        }

        /**
         * Returns authorizations for a workflow-run request using the supplied environment map.
         *
         * @param apiUrl REST API URL configured for the run
         * @param workflowPath workflow file path being dispatched
         * @param tokenEnvVar optional token environment variable configured for the run
         * @param project project used when JetBrains GitHub tokens need to be requested
         * @param environment environment variables used for explicit and default tokens
         * @return ordered authorizations ending with anonymous access
         */
        public static List<Authorization> forWorkflowRun(
                final String apiUrl,
                final String workflowPath,
                final String tokenEnvVar,
                final Project project,
                final Map<String, String> environment
        ) {
            return forServer(Server.fromWorkflowRun(apiUrl, workflowPath, tokenEnvVar), project, environment);
        }

        public static List<Authorization> forServer(final Server server, final Project project) {
            return forServer(server, project, System.getenv());
        }

        /**
         * Returns authorizations for a configured remote provider. GitHub providers reuse JetBrains GitHub accounts
         * and GitHub token environment variables. Gitea providers use only configured or Gitea-specific environment
         * tokens because JetBrains has no compatible Gitea account store here.
         *
         * @param server configured remote provider
         * @param project project used when JetBrains GitHub tokens need to be requested
         * @param environment environment variables used for explicit and default tokens
         * @return ordered authorizations ending with anonymous access
         */
        public static List<Authorization> forServer(
                final Server server,
                final Project project,
                final Map<String, String> environment
        ) {
            final Server normalized = Optional.ofNullable(server).orElseGet(Settings::defaultGitHub).normalized();
            if (normalized.isGitea()) {
                final LinkedHashMap<String, Authorization> result = new LinkedHashMap<>();
                giteaAccountAuthorizations(normalized)
                        .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
                envAuthorizations(normalized.tokenEnvVar, environment, DEFAULT_GITEA_ENV_TOKENS, "token ")
                        .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
                result.putIfAbsent(Authorization.anonymous().key(), Authorization.anonymous());
                return List.copyOf(result.values());
            }
            return forGithubApiUrl(normalized.apiUrl, normalized.tokenEnvVar, project, environment);
        }

        private static List<Authorization> forGithubApiUrl(
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
            envAuthorizations(tokenEnvVar, environment, DEFAULT_GITHUB_ENV_TOKENS, "Bearer ")
                    .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
            result.putIfAbsent(Authorization.anonymous().key(), Authorization.anonymous());
            return List.copyOf(result.values());
        }

        public static String settingsHint(final Server server) {
            final Server normalized = Optional.ofNullable(server).orElseGet(Settings::defaultGitHub).normalized();
            return normalized.isGitea()
                    ? GitHubWorkflowBundle.message("workflow.run.auth.settings.gitea")
                    : GitHubWorkflowBundle.message("workflow.run.auth.settings.github");
        }

        public static String settingsHint() {
            return GitHubWorkflowBundle.message("workflow.run.auth.settings.github");
        }

        private static List<Authorization> giteaAccountAuthorizations(final Server server) {
            final Settings settings = Settings.getInstance();
            return settings.enabledServers().stream()
                    .filter(Server::isGitea)
                    .sorted(Comparator
                            .comparingInt((Server candidate) -> giteaAccountPriority(candidate, server))
                            .thenComparing(candidate -> candidate.apiUrl)
                            .thenComparing(candidate -> candidate.name))
                    .map(candidate -> settings.giteaToken(candidate)
                            .map(token -> new Authorization(candidate.name, "token " + token)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        private static int giteaAccountPriority(final Server candidate, final Server server) {
            if (candidate.normalized().apiUrl.equals(server.normalized().apiUrl)) {
                return 0;
            }
            return sameHost(candidate.apiUrl, server.apiUrl) ? 1 : 2;
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

        private static List<Authorization> envAuthorizations(
                final String tokenEnvVar,
                final Map<String, String> environment,
                final List<String> defaultTokenEnvVars,
                final String authorizationPrefix
        ) {
            final LinkedHashMap<String, Authorization> result = new LinkedHashMap<>();
            final Map<String, String> values = Optional.ofNullable(environment).orElseGet(Map::of);
            envAuthorization(tokenEnvVar, values, authorizationPrefix).ifPresent(authorization -> result.putIfAbsent(authorization.key(), authorization));
            defaultTokenEnvVars.stream()
                    .filter(name -> !name.equals(tokenEnvVar))
                    .map(name -> envAuthorization(name, values, authorizationPrefix))
                    .flatMap(Optional::stream)
                    .forEach(authorization -> result.putIfAbsent(authorization.key(), authorization));
            return List.copyOf(result.values());
        }

        private static Optional<Authorization> envAuthorization(
                final String tokenEnvVar,
                final Map<String, String> environment,
                final String authorizationPrefix
        ) {
            return Optional.ofNullable(tokenEnvVar)
                    .map(String::trim)
                    .filter(RemoteActionProviders::hasText)
                    .flatMap(name -> Optional.ofNullable(environment.get(name))
                            .filter(RemoteActionProviders::hasText)
                            .map(token -> new Authorization(name, authorizationPrefix + token)));
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

    private static String webUrlFromApiUrl(final String apiUrl) {
        final String trimmed = trimTrailingSlash(apiUrl);
        if (trimmed.equals("https://api.github.com")) {
            return "https://github.com";
        }
        return trimmed.replaceFirst("/api/v(?:1|3)$", "");
    }

    private static boolean looksLikeGitea(
            final String apiUrl,
            final String workflowPath,
            final String tokenEnvVar
    ) {
        final String normalizedWorkflowPath = Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
        final String normalizedTokenEnvVar = Optional.ofNullable(tokenEnvVar).orElse("").toUpperCase();
        return normalizedWorkflowPath.startsWith(".gitea/workflows/")
                || trimTrailingSlash(apiUrl).endsWith("/api/v1")
                || normalizedTokenEnvVar.contains("GITEA");
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
