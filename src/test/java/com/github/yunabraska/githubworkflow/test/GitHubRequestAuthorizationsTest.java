package com.github.yunabraska.githubworkflow.test;

import com.github.yunabraska.githubworkflow.client.GitHubRequestAuthorizations;
import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubRequestAuthorizationsTest extends TestCase {

    public void testStandardEnvironmentTokensAreTriedBeforeAnonymous() {
        final List<GitHubRequestAuthorizations.Authorization> authorizations = GitHubRequestAuthorizations.forApiUrl(
                "https://api.example.test",
                "",
                null,
                Map.of("GITHUB_TOKEN", "env-token")
        );

        assertThat(authorizations)
                .extracting(GitHubRequestAuthorizations.Authorization::source)
                .containsSubsequence("GITHUB_TOKEN", "anonymous");
        assertThat(authorizations)
                .extracting(GitHubRequestAuthorizations.Authorization::authorizationHeader)
                .contains("Bearer env-token");
    }

    public void testExplicitEnvironmentTokenIsTriedBeforeStandardEnvironmentTokens() {
        final List<GitHubRequestAuthorizations.Authorization> authorizations = GitHubRequestAuthorizations.forApiUrl(
                "https://github.acme.test/api/v3",
                "ACME_GITHUB_TOKEN",
                null,
                Map.of("ACME_GITHUB_TOKEN", "enterprise-token", "GITHUB_TOKEN", "default-token")
        );

        assertThat(authorizations)
                .extracting(GitHubRequestAuthorizations.Authorization::source)
                .containsSubsequence("ACME_GITHUB_TOKEN", "GITHUB_TOKEN", "anonymous");
    }

    public void testMissingEnvironmentTokensFallBackToAnonymous() {
        final List<GitHubRequestAuthorizations.Authorization> authorizations = GitHubRequestAuthorizations.forApiUrl(
                "https://github.acme.test/api/v3",
                "ACME_GITHUB_TOKEN",
                null,
                Map.of()
        );

        assertThat(authorizations)
                .extracting(GitHubRequestAuthorizations.Authorization::source)
                .containsExactly("anonymous");
    }
}
