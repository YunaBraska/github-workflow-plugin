package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import com.intellij.openapi.project.Project;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

public class SchemaProvider implements JsonSchemaProviderFactory {

    protected static final List<JsonSchemaFileProvider> SCHEMA_FILE_PROVIDERS = Stream.<JsonSchemaFileProvider>of(
            new GitHubSchemaProvider("dependabot-2.0", "Dependabot [Auto]", GitHubWorkflowHelper::isDependabotFile),
            new GitHubSchemaProvider("github-action", "GitHub Action [Auto]", GitHubWorkflowHelper::isActionFile),
            new GitHubSchemaProvider("github-funding", "GitHub Funding [Auto]", GitHubWorkflowHelper::isFoundingFile),
            new GitHubSchemaProvider("github-workflow", "GitHub Workflow [Auto]", GitHubWorkflowHelper::isWorkflowFile),
            new GitHubSchemaProvider("github-discussion", "GitHub Discussion [Auto]", GitHubWorkflowHelper::isDiscussionFile),
            new GitHubSchemaProvider("github-issue-forms", "GitHub Issue Forms [Auto]", GitHubWorkflowHelper::isIssueForms),
            new GitHubSchemaProvider("github-issue-config", "GitHub Workflow Issue Template configuration [Auto]", GitHubWorkflowHelper::isIssueConfigFile),
            new GitHubSchemaProvider("github-workflow-template-properties", "GitHub Workflow Template Properties [Auto]", GitHubWorkflowHelper::isWorkflowTemplatePropertiesFile)
        )
        .distinct()
        .toList();

    @NotNull
    @Override
    public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
        return SCHEMA_FILE_PROVIDERS;
    }
}
