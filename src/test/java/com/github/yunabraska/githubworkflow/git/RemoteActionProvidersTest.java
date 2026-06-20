package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.test.FakeRemoteServer;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteActionProvidersTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance();
            settings.customServers().forEach(settings::clearGiteaToken);
            settings.setCustomServers(List.of());
        } finally {
            super.tearDown();
        }
    }

    public void testConfiguredGithubEnterpriseServerResolvesActionMetadataFromFakeApi() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "tool", "action.yml", "v1", """
                    name: Enterprise Tool
                    inputs:
                      token:
                        description: Token
                    outputs:
                      artifact:
                        description: Artifact
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            server.setBranches("acme", "tool", List.of("main"));
            server.setTags("acme", "tool", List.of("v1"));
            useServer(server, "/api/v3");

            final GitHubAction action = GitHubAction.createGithubAction(false, "acme/tool@v1", "acme/tool@v1").resolve();

            assertThat(action.isResolved()).isTrue();
            assertThat(action.githubUrl()).isEqualTo(server.webUrl() + "/acme/tool/tree/v1#readme");
            assertThat(action.freshInputs()).containsKey("token");
            assertThat(action.freshOutputs()).containsKey("artifact");
            assertThat(action.remoteRefs()).containsExactly("main", "v1");
            assertThat(server.requests()).contains("/api/v3/repos/acme/tool/contents/action.yml?ref=v1");
        }
    }

    public void testGithubEnterpriseAbsoluteUrlResolvesActionMetadataFromFakeApi() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "tool", "action.yml", "main", """
                    name: Enterprise Tool
                    inputs:
                      path:
                        description: Path
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            useServer(server, "/api/v3");

            final GitHubAction action = GitHubAction.createGithubAction(false, server.webUrl() + "/acme/tool@main", server.webUrl() + "/acme/tool@main").resolve();

            assertThat(action.isResolved()).isTrue();
            assertThat(action.githubUrl()).isEqualTo(server.webUrl() + "/acme/tool/tree/main#readme");
            assertThat(action.freshInputs()).containsKey("path");
        }
    }

    public void testResolveTriesActionYamlWhenActionYmlIsMissing() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("JetBrains", "qodana-action", "action.yaml", "v2023.3.1", """
                    name: Qodana
                    inputs:
                      args:
                        description: Args
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            useServer(server, "/api/v3");

            final GitHubAction action = GitHubAction.createGithubAction(false, "JetBrains/qodana-action@v2023.3.1", "JetBrains/qodana-action@v2023.3.1").resolve();

            assertThat(action.isResolved()).isTrue();
            assertThat(action.freshInputs()).containsKey("args");
            assertThat(server.requests()).contains(
                    "/api/v3/repos/JetBrains/qodana-action/contents/action.yml?ref=v2023.3.1",
                    "/api/v3/repos/JetBrains/qodana-action/contents/action.yaml?ref=v2023.3.1"
            );
        }
    }

    public void testApiNotFoundKeepsActionUnresolvedWithoutThrowing() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            useServer(server, "/api/v3");

            final String usesValue = server.webUrl() + "/missing/tool@main";
            final GitHubAction action = GitHubAction.createGithubAction(false, usesValue, usesValue).resolve();

            assertThat(action.isResolved()).isFalse();
            assertThat(action.freshInputs()).isEmpty();
            assertThat(action.freshOutputs()).isEmpty();
        }
    }

    public void testResolvedRemoteActionKeepsCachedMetadataWhenServerIsOffline() throws Exception {
        final String usesValue;
        final GitHubAction action;
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "tool", "action.yml", "v1", """
                    name: Enterprise Tool
                    inputs:
                      token:
                        description: Token
                    outputs:
                      artifact:
                        description: Artifact
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            useServer(server, "/api/v3");
            usesValue = server.webUrl() + "/acme/tool@v1";
            action = GitHubAction.createGithubAction(false, usesValue, usesValue).resolve();
        }

        action.expiryTime(0);
        final GitHubAction refreshed = action.resolve();

        assertThat(refreshed).isSameAs(action);
        assertThat(refreshed.isResolved()).isTrue();
        assertThat(refreshed.freshInputs()).containsKey("token");
        assertThat(refreshed.freshOutputs()).containsKey("artifact");
        assertThat(refreshed.expiryTime()).isGreaterThan(System.currentTimeMillis());
    }

    public void testGithubReusableWorkflowPathIsDetectedAsWorkflowMetadata() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "automation", ".github/workflows/reuse.yml", "main", """
                    name: Reuse
                    on:
                      workflow_call:
                        inputs:
                          config:
                            type: string
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo ok
                    """);
            useServer(server, "/api/v3");

            final GitHubAction action = GitHubAction.createGithubAction(false, server.webUrl() + "/acme/automation/.github/workflows/reuse.yml@main", server.webUrl()).resolve();

            assertThat(action.isResolved()).isTrue();
            assertThat(action.isAction()).isFalse();
            assertThat(action.freshInputs()).containsKey("config");
            assertThat(action.githubUrl()).isEqualTo(server.webUrl() + "/acme/automation/blob/main/.github/workflows/reuse.yml");
        }
    }

    public void testGiteaReusableWorkflowPathIsDetectedAsWorkflowMetadata() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "automation", ".gitea/workflows/reuse.yml", "main", """
                    name: Reuse
                    on:
                      workflow_call:
                        inputs:
                          config:
                            type: string
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo ok
                    """);
            useGiteaServer(server, "");

            final String usesValue = server.webUrl() + "/acme/automation/.gitea/workflows/reuse.yml@main";
            final GitHubAction action = GitHubAction.createGithubAction(false, usesValue, server.webUrl()).resolve();

            assertThat(action.isResolved()).isTrue();
            assertThat(action.isAction()).isFalse();
            assertThat(action.freshInputs()).containsKey("config");
            assertThat(action.githubUrl()).isEqualTo(server.webUrl() + "/acme/automation/blob/main/.gitea/workflows/reuse.yml");
            assertThat(server.requests()).contains("/api/v1/repos/acme/automation/contents/.gitea/workflows/reuse.yml?ref=main");
        }
    }

    public void testLatestRefsReturnsLatestTenTagsBeforeBranches() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.setTags("acme", "tool", List.of("v10", "v9", "v8", "v7", "v6", "v5", "v4", "v3", "v2", "v1", "v0"));
            server.setBranches("acme", "tool", List.of("main"));
            useServer(server, "/api/v3");

            final List<String> refs = RemoteActionProviders.latestRefs("acme/tool", 10);

            assertThat(refs).containsExactly("v10", "v9", "v8", "v7", "v6", "v5", "v4", "v3", "v2", "v1");
            assertThat(server.requests()).contains("/api/v3/repos/acme/tool/tags?per_page=10");
        }
    }

    public void testLatestRefsFallsBackToBranchesWhenTagsAreEmpty() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.setBranches("acme", "tool", List.of("main", "release"));
            useServer(server, "/api/v3");

            final List<String> refs = RemoteActionProviders.latestRefs("acme/tool", 10);

            assertThat(refs).containsExactly("main", "release");
        }
    }

    public void testSearchUsesReturnsMatchingRepositoriesFromConfiguredServer() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.setRepositories("actions", Map.of(
                    "checkout", "Checkout repository",
                    "setup-java", "Set up Java",
                    "cache", "Cache dependencies"
            ));
            useServer(server, "/api/v3");

            final Map<String, String> completions = RemoteActionProviders.searchUses("actions/set", 10);

            assertThat(completions).containsEntry("actions/setup-java", "Set up Java");
            assertThat(completions).doesNotContainKey("actions/checkout");
            assertThat(server.requests()).contains("/api/v3/users/actions/repos?per_page=10");
        }
    }

    public void testStandardEnvironmentTokensAreTriedBeforeAnonymous() {
        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forApiUrl(
                "https://api.example.test",
                "",
                null,
                Map.of("GITHUB_TOKEN", "env-token")
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsSubsequence("GITHUB_TOKEN", "anonymous");
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                .contains("Bearer env-token");
    }

    public void testGiteaEnvironmentTokensUseTokenAuthorizationScheme() {
        final RemoteActionProviders.Server server = RemoteActionProviders.Server.gitea(
                "Local Gitea",
                "http://gitea.local",
                "http://gitea.local/api/v1",
                "LOCAL_GITEA_TOKEN",
                true
        );

        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forServer(
                server,
                null,
                Map.of("LOCAL_GITEA_TOKEN", "local-token", "GITEA_TOKEN", "default-token", "GITHUB_TOKEN", "wrong-for-gitea")
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsExactly("LOCAL_GITEA_TOKEN", "GITEA_TOKEN", "anonymous");
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                .containsExactly("token local-token", "token default-token", "");
    }

    public void testGiteaWorkflowRunEnvironmentTokensUseTokenAuthorizationScheme() {
        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forWorkflowRun(
                "http://gitea.local/api/v1",
                ".gitea/workflows/build.yml",
                "LOCAL_GITEA_TOKEN",
                null,
                Map.of("LOCAL_GITEA_TOKEN", "local-token", "GITEA_TOKEN", "default-token", "GITHUB_TOKEN", "wrong-for-gitea")
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsExactly("LOCAL_GITEA_TOKEN", "GITEA_TOKEN", "anonymous");
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                .containsExactly("token local-token", "token default-token", "");
    }

    public void testGiteaStoredTokenIsTriedBeforeEnvironmentTokens() {
        final RemoteActionProviders.Server server = RemoteActionProviders.Server.gitea(
                "Local Gitea",
                "http://gitea.local",
                "http://gitea.local/api/v1",
                "LOCAL_GITEA_TOKEN",
                true
        );
        final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance()
                .setCustomServers(List.of(server))
                .setGiteaToken(server, "stored-token");

        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forServer(
                server,
                null,
                Map.of("LOCAL_GITEA_TOKEN", "local-token", "GITEA_TOKEN", "default-token")
        );

        assertThat(settings.hasGiteaToken(server)).isTrue();
        assertThat(settings.customServers().getFirst().tokenStored).isTrue();
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsExactly("Local Gitea", "LOCAL_GITEA_TOKEN", "GITEA_TOKEN", "anonymous");
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                .containsExactly("token stored-token", "token local-token", "token default-token", "");
    }

    public void testGiteaStoredTokenForMatchingServerBeatsOtherGiteaTokens() {
        final RemoteActionProviders.Server first = RemoteActionProviders.Server.gitea(
                "First Gitea",
                "http://first.local",
                "http://first.local/api/v1",
                "",
                true
        );
        final RemoteActionProviders.Server second = RemoteActionProviders.Server.gitea(
                "Second Gitea",
                "http://second.local",
                "http://second.local/api/v1",
                "",
                true
        );
        RemoteActionProviders.Settings.getInstance()
                .setCustomServers(List.of(first, second))
                .setGiteaToken(first, "first-token")
                .setGiteaToken(second, "second-token");

        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forWorkflowRun(
                "http://second.local/api/v1",
                ".gitea/workflows/build.yml",
                "",
                null,
                Map.of()
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsExactly("Second Gitea", "First Gitea", "anonymous");
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                .containsExactly("token second-token", "token first-token", "");
    }

    public void testGiteaStoredTokenIsNotSerializedInSettingsState() {
        final RemoteActionProviders.Server server = RemoteActionProviders.Server.gitea(
                "Local Gitea",
                "http://gitea.local",
                "http://gitea.local/api/v1",
                "LOCAL_GITEA_TOKEN",
                true
        );

        final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance()
                .setCustomServers(List.of(server))
                .setGiteaToken(server, "stored-token");

        assertThat(settings.getState().servers)
                .hasSize(1)
                .allSatisfy(state -> assertThat(List.of(
                        state.type,
                        state.name,
                        state.webUrl,
                        state.apiUrl,
                        state.tokenEnvVar
                )).doesNotContain("stored-token"));
        assertThat(settings.getState().servers.getFirst().tokenEnvVar).isEqualTo("LOCAL_GITEA_TOKEN");
        assertThat(settings.getState().servers.getFirst().tokenStored).isTrue();
    }

    public void testGiteaStoredTokenMarkerIsClearedWithToken() {
        final RemoteActionProviders.Server server = RemoteActionProviders.Server.gitea(
                "Local Gitea",
                "http://gitea.local",
                "http://gitea.local/api/v1",
                "LOCAL_GITEA_TOKEN",
                true
        );
        final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance()
                .setCustomServers(List.of(server))
                .setGiteaToken(server, "stored-token");

        settings.clearGiteaToken(server);

        assertThat(settings.hasGiteaToken(server)).isFalse();
        assertThat(settings.customServers().getFirst().tokenStored).isFalse();
    }

    public void testDisabledGiteaServerIsStoredButNotUsed() {
        final RemoteActionProviders.Server server = RemoteActionProviders.Server.gitea(
                "Quiet Gitea",
                "http://gitea.local",
                "http://gitea.local/api/v1",
                "LOCAL_GITEA_TOKEN",
                false
        );
        final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance()
                .setCustomServers(List.of(server));

        assertThat(settings.customServers()).containsExactly(server.normalized());
        assertThat(settings.enabledServers())
                .noneMatch(candidate -> "http://gitea.local/api/v1".equals(candidate.apiUrl));
    }

    public void testGithubEnvironmentTokensStillUseBearerAuthorizationScheme() {
        final RemoteActionProviders.Server server = new RemoteActionProviders.Server(
                "GitHub",
                "https://github.com",
                "https://api.github.com",
                "LOCAL_GITHUB_TOKEN",
                true
        );

        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forServer(
                server,
                null,
                Map.of("LOCAL_GITHUB_TOKEN", "local-token", "GITHUB_TOKEN", "default-token", "GITEA_TOKEN", "wrong-for-github")
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsSubsequence("LOCAL_GITHUB_TOKEN", "GITHUB_TOKEN", "anonymous");
        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::authorizationHeader)
                .contains("Bearer local-token", "Bearer default-token");
    }

    public void testExplicitEnvironmentTokenIsTriedBeforeStandardEnvironmentTokens() {
        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forApiUrl(
                "https://github.acme.test/api/v3",
                "ACME_GITHUB_TOKEN",
                null,
                Map.of("ACME_GITHUB_TOKEN", "enterprise-token", "GITHUB_TOKEN", "default-token")
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsSubsequence("ACME_GITHUB_TOKEN", "GITHUB_TOKEN", "anonymous");
    }

    public void testMissingEnvironmentTokensFallBackToAnonymous() {
        final List<RemoteActionProviders.Authorizations.Authorization> authorizations = RemoteActionProviders.Authorizations.forApiUrl(
                "https://github.acme.test/api/v3",
                "ACME_GITHUB_TOKEN",
                null,
                Map.of()
        );

        assertThat(authorizations)
                .extracting(RemoteActionProviders.Authorizations.Authorization::source)
                .containsExactly("anonymous");
    }

    private static void useServer(final FakeRemoteServer server, final String apiPrefix) {
        RemoteActionProviders.Settings.getInstance().setCustomServers(List.of(new RemoteActionProviders.Server(
                "Fake",
                server.webUrl(),
                server.apiUrl(apiPrefix),
                "",
                true
        )));
    }

    private static void useGiteaServer(final FakeRemoteServer server, final String tokenEnvVar) {
        RemoteActionProviders.Settings.getInstance().setCustomServers(List.of(RemoteActionProviders.Server.gitea(
                "Fake Gitea",
                server.webUrl(),
                server.apiUrl("/api/v1"),
                tokenEnvVar,
                true
        )));
    }
}
