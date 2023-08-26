package com.github.yunabraska.githubworkflow.schema;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadSchema;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.isYamlFile;
import static com.github.yunabraska.githubworkflow.schema.GitHubSchemaProviderFactory.GITHUB_SCHEMA_CACHE;

public class GitHubWorkflowSchemaProvider implements JsonSchemaFileProvider {

    private static final String NAME = "workflow";
    private static final String SCHEMA_URL = "https://json.schemastore.org/github-" + NAME;

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return Optional.of(file).map(VirtualFile::getPath).map(Paths::get).filter(GitHubWorkflowUtils::isWorkflowPath).isPresent();
    }

    public static boolean isWorkflowYaml(final Path path) {
        return path != null && path.getNameCount() > 2
                && isYamlFile(path)
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflows")
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github");
    }

    @NotNull
    @Override
    public String getName() {
        return "GitHub Workflow [Auto]";
    }


    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
        return GITHUB_SCHEMA_CACHE.computeIfAbsent(SCHEMA_URL, key -> downloadSchema(SCHEMA_URL, NAME));
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
        return SchemaType.schema;
    }

    @Nullable
    @NonNls
    @Override
    public String getRemoteSource() {
        return SCHEMA_URL;
    }
}
