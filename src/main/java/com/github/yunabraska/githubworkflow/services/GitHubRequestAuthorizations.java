package com.github.yunabraska.githubworkflow.services;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds authorization candidates for GitHub REST calls.
 */
final class GitHubRequestAuthorizations {

    private static final List<String> DEFAULT_ENV_TOKENS = List.of("GITHUB_TOKEN", "GH_TOKEN", "GITHUB_PAT");

    static List<Authorization> forApiUrl(final String apiUrl, final String tokenEnvVar, final Project project) {
        return forApiUrl(apiUrl, tokenEnvVar, project, System.getenv());
    }

    static List<Authorization> forApiUrl(
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

    static String settingsHint() {
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
                    .filter(GitHubRequestAuthorizations::hasText)
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
                .filter(GitHubRequestAuthorizations::hasText)
                .flatMap(name -> Optional.ofNullable(environment.get(name))
                        .filter(GitHubRequestAuthorizations::hasText)
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

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    record Authorization(String source, String authorizationHeader) {

        static Authorization anonymous() {
            return new Authorization("anonymous", "");
        }

        boolean authenticated() {
            return hasText(authorizationHeader);
        }

        String key() {
            return source + "|" + authorizationHeader;
        }
    }
}
