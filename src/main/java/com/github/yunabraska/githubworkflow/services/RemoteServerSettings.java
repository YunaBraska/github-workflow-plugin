package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RemoteServerSettings {

    public static final String TYPE_GITHUB = "github";

    private final CopyOnWriteArrayList<Server> testServers = new CopyOnWriteArrayList<>();

    public static RemoteServerSettings getInstance() {
        return ApplicationManager.getApplication().getService(RemoteServerSettings.class);
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

    void setCustomServers(final List<Server> servers) {
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

    public static final class Server {
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
            this.type = TYPE_GITHUB;
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
                    .filter(RemoteServerSettings::hasText)
                    .map(System::getenv)
                    .filter(RemoteServerSettings::hasText)
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

    private static String trimTrailingSlash(final String value) {
        final String trimmed = Optional.ofNullable(value).map(String::trim).orElse("");
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
