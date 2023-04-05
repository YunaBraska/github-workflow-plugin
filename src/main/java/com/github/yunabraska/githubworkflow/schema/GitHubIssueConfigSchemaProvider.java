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
import java.util.function.Predicate;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadSchema;
import static com.github.yunabraska.githubworkflow.schema.GitHubSchemaProviderFactory.GITHUB_SCHEMA_CACHE;

public class GitHubIssueConfigSchemaProvider implements JsonSchemaFileProvider {

    private static final String NAME = "issue-config";
    private static final String SCHEMA_URL = "https://json.schemastore.org/github-" + NAME;

    public GitHubIssueConfigSchemaProvider() {
    }

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return Optional.of(file)
                .map(VirtualFile::getPath)
                .map(Paths::get)
                .filter(GitHubWorkflowUtils::isYamlFile)
                .filter(validatePath()).isPresent();
    }

    public Predicate<Path> validatePath() {
        return path -> path.getNameCount() > 2
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflow-templates")
                && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("config.yml")
                || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("config.yaml"));
    }

    @NotNull
    @Override
    public String getName() {
        return "GitHub Workflow Issue Template configuration [Auto]";
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
