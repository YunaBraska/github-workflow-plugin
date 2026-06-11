package com.github.yunabraska.githubworkflow.syntax;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_GITHUB;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUNNER;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowMetadataTest {

    @Test
    public void githubContextContainsCurrentDocumentedKeys() {
        assertThat(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get().keySet())
                .containsAll(List.of(
                        "action",
                        "action_path",
                        "action_ref",
                        "action_repository",
                        "action_status",
                        "actor",
                        "actor_id",
                        "api_url",
                        "base_ref",
                        "env",
                        "event",
                        "event_name",
                        "event_path",
                        "graphql_url",
                        "head_ref",
                        "job",
                        "path",
                        "ref",
                        "ref_name",
                        "ref_protected",
                        "ref_type",
                        "repository",
                        "repository_id",
                        "repository_owner",
                        "repository_owner_id",
                        "repositoryUrl",
                        "retention_days",
                        "run_id",
                        "run_number",
                        "run_attempt",
                        "secret_source",
                        "server_url",
                        "sha",
                        "token",
                        "triggering_actor",
                        "workflow",
                        "workflow_ref",
                        "workflow_sha",
                        "workspace"
                ));
    }

    @Test
    public void githubContextMatchesGeneratedDocsSnapshot() throws Exception {
        assertThat(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get().keySet())
                .containsExactlyElementsOf(resourceKeys("/github-docs/github-context.tsv"));
    }

    @Test
    public void defaultEnvironmentVariablesContainCurrentDocumentedKeys() {
        assertThat(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get().keySet())
                .containsAll(List.of(
                        "CI",
                        "GITHUB_ACTION",
                        "GITHUB_ACTION_PATH",
                        "GITHUB_ACTION_REPOSITORY",
                        "GITHUB_ACTIONS",
                        "GITHUB_ACTOR",
                        "GITHUB_ACTOR_ID",
                        "GITHUB_API_URL",
                        "GITHUB_BASE_REF",
                        "GITHUB_ENV",
                        "GITHUB_EVENT_NAME",
                        "GITHUB_EVENT_PATH",
                        "GITHUB_GRAPHQL_URL",
                        "GITHUB_HEAD_REF",
                        "GITHUB_JOB",
                        "GITHUB_OUTPUT",
                        "GITHUB_PATH",
                        "GITHUB_REF",
                        "GITHUB_REF_NAME",
                        "GITHUB_REF_PROTECTED",
                        "GITHUB_REF_TYPE",
                        "GITHUB_REPOSITORY",
                        "GITHUB_REPOSITORY_ID",
                        "GITHUB_REPOSITORY_OWNER",
                        "GITHUB_REPOSITORY_OWNER_ID",
                        "GITHUB_RETENTION_DAYS",
                        "GITHUB_RUN_ATTEMPT",
                        "GITHUB_RUN_ID",
                        "GITHUB_RUN_NUMBER",
                        "GITHUB_SERVER_URL",
                        "GITHUB_SHA",
                        "GITHUB_STEP_SUMMARY",
                        "GITHUB_TRIGGERING_ACTOR",
                        "GITHUB_WORKFLOW",
                        "GITHUB_WORKFLOW_REF",
                        "GITHUB_WORKFLOW_SHA",
                        "GITHUB_WORKSPACE",
                        "RUNNER_ARCH",
                        "RUNNER_DEBUG",
                        "RUNNER_ENVIRONMENT",
                        "RUNNER_NAME",
                        "RUNNER_OS",
                        "RUNNER_TEMP",
                        "RUNNER_TOOL_CACHE"
                ));
    }

    @Test
    public void defaultEnvironmentVariablesMatchGeneratedDocsSnapshot() throws Exception {
        assertThat(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get().keySet())
                .containsExactlyElementsOf(resourceKeys("/github-docs/default-env.tsv"));
    }

    @Test
    public void refProtectionDescriptionsMentionRulesets() {
        assertThat(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get().get("ref_protected"))
                .contains("rulesets");
        assertThat(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get().get("GITHUB_REF_PROTECTED"))
                .contains("rulesets");
    }

    @Test
    public void runnerDebugDescriptionMatchesDocumentedMeaning() {
        final Map<String, String> runnerItems = DEFAULT_VALUE_MAP.get(FIELD_RUNNER).get();

        assertThat(runnerItems.get("debug"))
                .contains("debug logging")
                .contains("1")
                .doesNotContain("preinstalled tools");
    }

    private static List<String> resourceKeys(final String path) throws Exception {
        try (InputStream stream = Objects.requireNonNull(WorkflowMetadataTest.class.getResourceAsStream(path));
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .map(line -> line.split("\t", 2)[0])
                    .toList();
        }
    }

    @Test
    public void detectsWorkflowFilesOnlyUnderGithubWorkflowsDirectory() {
        assertThat(WorkflowYaml.isWorkflowFile(Path.of("repo", ".github", "workflows", "build.yml"))).isTrue();
        assertThat(WorkflowYaml.isWorkflowFile(Path.of("repo", ".github", "workflows", "build.yaml"))).isTrue();
        assertThat(WorkflowYaml.isWorkflowFile(Path.of("repo", ".gitea", "workflows", "build.yml"))).isTrue();
        assertThat(WorkflowYaml.isGithubWorkflowFile(Path.of("repo", ".github", "workflows", "build.yml"))).isTrue();
        assertThat(WorkflowYaml.isGithubWorkflowFile(Path.of("repo", ".gitea", "workflows", "build.yml"))).isFalse();
        assertThat(WorkflowYaml.isGiteaWorkflowFile(Path.of("repo", ".gitea", "workflows", "build.yml"))).isTrue();
        assertThat(WorkflowYaml.isGiteaWorkflowFile(Path.of("repo", ".github", "workflows", "build.yml"))).isFalse();
        assertThat(WorkflowYaml.isWorkflowFile(Path.of("repo", ".github", "not-workflows", "build.yml"))).isFalse();
        assertThat(WorkflowYaml.isWorkflowFile(Path.of("repo", "workflows", "build.yml"))).isFalse();
    }

    @Test
    public void invalidVirtualFilePathTextIsRejectedWithoutThrowing() {
        assertThat(WorkflowPsi.toPath("<36ba1c43-b8f1-4f54-ace0-cef443d1e8f0>/etc/php/8.1/apache2/php.ini")).isEmpty();
    }

    @Test
    public void serializedVirtualFilePathTextIsRejectedWithoutThrowing() {
        assertThat(WorkflowPsi.toPath("{\"sessionId\":\"2cc03ab1-37d6-47cd-980d-1bb135073b4d\"}")).isEmpty();
    }

    @Test
    public void detectsActionMetadataFilesByName() {
        assertThat(WorkflowYaml.isActionFile(Path.of("repo", "action.yml"))).isTrue();
        assertThat(WorkflowYaml.isActionFile(Path.of("repo", "nested", "ACTION.YAML"))).isTrue();
        assertThat(WorkflowYaml.isActionFile(Path.of("repo", "workflow.yml"))).isFalse();
    }

    @Test
    public void detectsSchemaTargetFiles() {
        assertThat(WorkflowYaml.isDependabotFile(Path.of("repo", ".github", "dependabot.yml"))).isTrue();
        assertThat(WorkflowYaml.isFoundingFile(Path.of("repo", ".github", "FUNDING.yml"))).isTrue();
        assertThat(WorkflowYaml.isIssueForms(Path.of("repo", ".github", "ISSUE_TEMPLATE", "bug.yml"))).isTrue();
        assertThat(WorkflowYaml.isDiscussionFile(Path.of("repo", ".github", "DISCUSSION_TEMPLATE", "question.yaml"))).isTrue();
    }

    @Test
    public void rejectsIssueConfigOutsideIssueTemplateDirectory() {
        assertThat(WorkflowYaml.isIssueConfigFile(Path.of("repo", ".github", "ISSUE_TEMPLATE", "config.yml"))).isTrue();
        assertThat(WorkflowYaml.isIssueConfigFile(Path.of("repo", ".github", "workflow-templates", "config.yml"))).isFalse();
    }
}
