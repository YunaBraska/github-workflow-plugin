package com.github.yunabraska.githubworkflow.model;

import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubActionTest {

    @Test
    public void createGithubActionBuildsRemoteActionUrls() {
        final GitHubAction action = GitHubAction.createGithubAction(false, "actions/setup-java@v4", "actions/setup-java@v4");

        assertThat(action.name()).isEqualTo("actions/setup-java");
        assertThat(action.usesValue()).isEqualTo("actions/setup-java@v4");
        assertThat(action.downloadUrl()).isEqualTo("https://raw.githubusercontent.com/actions/setup-java/v4/action.yml");
        assertThat(action.githubUrl()).isEqualTo("https://github.com/actions/setup-java/tree/v4#readme");
        assertThat(action.isLocal()).isFalse();
        assertThat(action.isAction()).isTrue();
        assertThat(action.isResolved()).isFalse();
    }

    @Test
    public void createGithubActionBuildsNestedRemoteActionUrls() {
        final GitHubAction action = GitHubAction.createGithubAction(false, "owner/repo/path/to/action@main", "owner/repo/path/to/action@main");

        assertThat(action.name()).isEqualTo("owner/repo");
        assertThat(action.downloadUrl()).isEqualTo("https://raw.githubusercontent.com/owner/repo/main/path/to/action/action.yml");
        assertThat(action.githubUrl()).isEqualTo("https://github.com/owner/repo/tree/main/path/to/action#readme");
        assertThat(action.isAction()).isTrue();
    }

    @Test
    public void createGithubActionBuildsReusableWorkflowUrls() {
        final GitHubAction action = GitHubAction.createGithubAction(false, "owner/repo/.github/workflows/reuse.yml@main", "owner/repo/.github/workflows/reuse.yml@main");

        assertThat(action.name()).isEqualTo("owner/repo");
        assertThat(action.downloadUrl()).isEqualTo("https://raw.githubusercontent.com/owner/repo/main/.github/workflows/reuse.yml");
        assertThat(action.githubUrl()).isEqualTo("https://github.com/owner/repo/blob/main/.github/workflows/reuse.yml");
        assertThat(action.isAction()).isFalse();
    }

    @Test
    public void createGithubActionTreatsLocalWorkflowFileAsReusableWorkflow() {
        final GitHubAction action = GitHubAction.createGithubAction(true, "./.github/workflows/reusable.yml", "/tmp/project/.github/workflows/reusable.yml");

        assertThat(action.isLocal()).isTrue();
        assertThat(action.isAction()).isFalse();
    }

    @Test
    public void createGithubActionTreatsLocalActionDirectoryAsAction() {
        final GitHubAction action = GitHubAction.createGithubAction(true, "./.github/actions/local", "/tmp/project/.github/actions/local/action.yml");

        assertThat(action.isLocal()).isTrue();
        assertThat(action.isAction()).isTrue();
    }

    @Test
    public void settersIgnoreNullMapsAndKeepFluentApi() {
        final GitHubAction action = new GitHubAction()
                .setInputs(null)
                .setOutputs(null)
                .setSecrets(null)
                .setMetaData(null)
                .setInputs(Map.of("input", "description"))
                .setOutputs(Map.of("output", "description"))
                .setSecrets(Map.of("secret", "description"))
                .setMetaData(Map.of("name", "demo", "ignoredInputs", "manual-input", "ignoredOutputs", "manual-output"));

        assertThat(action.getInputs()).containsEntry("input", "description");
        assertThat(action.getOutputs()).containsEntry("output", "description");
        assertThat(action.getSecrets()).containsEntry("secret", "description");
        assertThat(action.freshSecrets()).containsEntry("secret", "description");
        assertThat(action.name()).isEqualTo("demo");
        assertThat(action.ignoredInputs()).contains("manual-input");
        assertThat(action.ignoredOutputs()).contains("manual-output");
    }

    @Test
    public void suppressedItemsCanBeRemovedAgain() {
        final GitHubAction action = new GitHubAction()
                .suppressInput("manual-input", true)
                .suppressOutput("manual-output", true);

        action.suppressInput("manual-input", false);
        action.suppressOutput("manual-output", false);

        assertThat(action.ignoredInputs()).doesNotContain("manual-input");
        assertThat(action.ignoredOutputs()).doesNotContain("manual-output");
        assertThat(action.getMetaData()).containsEntry("ignoredInputs", "").containsEntry("ignoredOutputs", "");
    }
}
