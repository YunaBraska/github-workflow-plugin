package com.github.yunabraska.githubworkflow.schema;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GitHubSchemaProviderFactory implements JsonSchemaProviderFactory {

    public static final Map<String, VirtualFile> GITHUB_SCHEMA_CACHE = new ConcurrentHashMap<>();

    @NotNull
    @Override
    public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
        return Arrays.asList(
                new GitHubActionSchemaProvider(),
                new GitHubFoundingSchemaProvider(),
                new GitHubWorkflowSchemaProvider(),
                new GitHubDiscussionSchemaProvider(),
                new GitHubIssueFormsSchemaProvider(),
                new GitHubIssueConfigSchemaProvider(),
                new GitHubWorkflowTemplateSchemaProvider()
        );
    }
}
