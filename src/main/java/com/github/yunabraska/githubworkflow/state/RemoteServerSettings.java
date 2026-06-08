package com.github.yunabraska.githubworkflow.state;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provides the ordered list of GitHub-compatible servers used for remote metadata and workflow-run calls.
 */
public class RemoteServerSettings {

    public static final String TYPE_GITHUB = "github";

    private final CopyOnWriteArrayList<Server> testServers = new CopyOnWriteArrayList<>();

    /**
     * Returns the application-wide remote server settings service.
     *
     * @return remote server settings service managed by the IDE
     */
    public static RemoteServerSettings getInstance() {
        return ApplicationManager.getApplication().getService(RemoteServerSettings.class);
    }

    /**
     * Returns enabled GitHub-compatible servers in preferred lookup order.
     *
     * @return normalized custom test servers, IDE GitHub accounts, and the default public GitHub endpoint
     */
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

    /**
     * Replaces custom server entries used by tests or controlled runtime setup.
     *
     * @param servers custom server entries; invalid entries are ignored
     */
    public void setCustomServers(final List<Server> servers) {
        testServers.clear();
        Optional.ofNullable(servers).orElseGet(List::of).stream()
                .map(Server::normalized)
                .filter(Server::isValid)
                .forEach(testServers::add);
    }

    /**
     * Creates the public GitHub server descriptor.
     *
     * @return default github.com web and API endpoint descriptor
     */
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

    /**
     * Immutable description of one GitHub-compatible remote endpoint.
     */
    public static class Server {
        public final String type;
        public final String name;
        public final String webUrl;
        public final String apiUrl;
        public final String tokenEnvVar;
        public final boolean enabled;

        /**
         * Creates a remote server descriptor.
         *
         * @param name display name shown in diagnostics or settings
         * @param webUrl browser-facing server URL
         * @param apiUrl REST API base URL
         * @param tokenEnvVar optional environment variable containing a bearer token
         * @param enabled whether this server participates in remote calls
         */
        public Server(
                final String name,
                final String webUrl,
                final String apiUrl,
                final String tokenEnvVar,
                final boolean enabled
        ) {
            this.type = TYPE_GITHUB;
            this.name = name;
            this.webUrl = webUrl;
            this.apiUrl = apiUrl;
            this.tokenEnvVar = tokenEnvVar;
            this.enabled = enabled;
        }

        /**
         * Reports whether this server should be considered for remote calls.
         *
         * @return true when the server is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Reports whether this server has enough endpoint data to be used.
         *
         * @return true when enabled and both web/API URLs are present
         */
        public boolean isValid() {
            return isEnabled() && hasText(webUrl) && hasText(apiUrl);
        }

        /**
         * Builds the HTTP authorization header from the configured token environment variable.
         *
         * @return bearer authorization header, or an empty string when no token is available
         */
        public String authorizationHeader() {
            return Optional.ofNullable(tokenEnvVar)
                    .filter(RemoteServerSettings::hasText)
                    .map(System::getenv)
                    .filter(RemoteServerSettings::hasText)
                    .map(token -> "Bearer " + token)
                    .orElse("");
        }

        /**
         * Returns a cleaned copy with trimmed URLs and token variable name.
         *
         * @return normalized server descriptor
         */
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

    private static String trimTrailingSlash(final String value) {
        final String trimmed = Optional.ofNullable(value).map(String::trim).orElse("");
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
