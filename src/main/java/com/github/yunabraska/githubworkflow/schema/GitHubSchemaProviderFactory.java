package com.github.yunabraska.githubworkflow.schema;

import com.intellij.openapi.project.Project;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class GitHubSchemaProviderFactory implements JsonSchemaProviderFactory {
    @NotNull
    @Override
    public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
        return Arrays.asList(new GitHubWorkflowSchemaProvider(project));
    }
}
