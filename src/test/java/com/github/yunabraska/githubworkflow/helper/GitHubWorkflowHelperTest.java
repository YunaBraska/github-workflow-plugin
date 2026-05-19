package com.github.yunabraska.githubworkflow.helper;

import org.junit.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubWorkflowHelperTest {

    @Test
    public void detectsWorkflowFilesOnlyUnderGithubWorkflowsDirectory() {
        assertThat(GitHubWorkflowHelper.isWorkflowFile(Path.of("repo", ".github", "workflows", "build.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isWorkflowFile(Path.of("repo", ".github", "workflows", "build.yaml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isWorkflowFile(Path.of("repo", ".gitea", "workflows", "build.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isWorkflowFile(Path.of("repo", ".github", "not-workflows", "build.yml"))).isFalse();
        assertThat(GitHubWorkflowHelper.isWorkflowFile(Path.of("repo", "workflows", "build.yml"))).isFalse();
    }

    @Test
    public void invalidVirtualFilePathTextIsRejectedWithoutThrowing() {
        assertThat(PsiElementHelper.toPath("<36ba1c43-b8f1-4f54-ace0-cef443d1e8f0>/etc/php/8.1/apache2/php.ini")).isEmpty();
    }

    @Test
    public void serializedVirtualFilePathTextIsRejectedWithoutThrowing() {
        assertThat(PsiElementHelper.toPath("{\"sessionId\":\"2cc03ab1-37d6-47cd-980d-1bb135073b4d\"}")).isEmpty();
    }

    @Test
    public void detectsActionMetadataFilesByName() {
        assertThat(GitHubWorkflowHelper.isActionFile(Path.of("repo", "action.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isActionFile(Path.of("repo", "nested", "ACTION.YAML"))).isTrue();
        assertThat(GitHubWorkflowHelper.isActionFile(Path.of("repo", "workflow.yml"))).isFalse();
    }

    @Test
    public void detectsSchemaTargetFiles() {
        assertThat(GitHubWorkflowHelper.isDependabotFile(Path.of("repo", ".github", "dependabot.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isFoundingFile(Path.of("repo", ".github", "FUNDING.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isIssueForms(Path.of("repo", ".github", "ISSUE_TEMPLATE", "bug.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isDiscussionFile(Path.of("repo", ".github", "DISCUSSION_TEMPLATE", "question.yaml"))).isTrue();
    }

    @Test
    public void rejectsIssueConfigOutsideIssueTemplateDirectory() {
        assertThat(GitHubWorkflowHelper.isIssueConfigFile(Path.of("repo", ".github", "ISSUE_TEMPLATE", "config.yml"))).isTrue();
        assertThat(GitHubWorkflowHelper.isIssueConfigFile(Path.of("repo", ".github", "workflow-templates", "config.yml"))).isFalse();
    }
}
