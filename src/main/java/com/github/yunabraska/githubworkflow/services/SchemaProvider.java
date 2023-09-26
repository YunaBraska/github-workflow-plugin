package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.schema.DependabotSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubActionSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubDiscussionSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubFoundingSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubIssueConfigSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubIssueFormsSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubWorkflowSchemaProvider;
import com.github.yunabraska.githubworkflow.model.schema.GitHubWorkflowTemplateSchemaProvider;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;

@SuppressWarnings("java:S2386")
public class SchemaProvider implements JsonSchemaProviderFactory {

    public static final Map<String, VirtualFile> GITHUB_SCHEMA_CACHE = new ConcurrentHashMap<>();
    public static final List<JsonSchemaFileProvider> SCHEMA_FILE_PROVIDERS = Arrays.asList(
            new DependabotSchemaProvider(),
            new GitHubActionSchemaProvider(),
            new GitHubFoundingSchemaProvider(),
            new GitHubWorkflowSchemaProvider(),
            new GitHubDiscussionSchemaProvider(),
            new GitHubIssueFormsSchemaProvider(),
            new GitHubIssueConfigSchemaProvider(),
            new GitHubWorkflowTemplateSchemaProvider()
    );

    @NotNull
    @Override
    public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
        return SCHEMA_FILE_PROVIDERS;
    }

    public static VirtualFile getSchema(final String url, final String name) {
        try {
            final VirtualFile newVirtualFile = new LightVirtualFile("github_workflow_plugin_" + name + "_schema.json", JsonFileType.INSTANCE, "");
            VfsUtil.saveText(newVirtualFile, getActionCache().getSchema(url));
            return newVirtualFile;
        } catch (final Exception ignored) {
            return null;
        }
    }
}
