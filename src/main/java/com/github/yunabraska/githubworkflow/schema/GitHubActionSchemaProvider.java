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

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getSchema;
import static com.github.yunabraska.githubworkflow.schema.GitHubSchemaProviderFactory.GITHUB_SCHEMA_CACHE;

public class GitHubActionSchemaProvider implements JsonSchemaFileProvider {

    private static final String NAME = "action";
    private static final String SCHEMA_URL = "https://json.schemastore.org/github-" + NAME;

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return Optional.of(file)
                .map(VirtualFile::getPath)
                .map(Paths::get)
                .filter(GitHubWorkflowUtils::isYamlFile)
                .filter(validatePath()).isPresent();
    }

    public Predicate<Path> validatePath() {
        return GitHubActionSchemaProvider::isActionYaml;
    }

    public static boolean isActionYaml(final Path path) {
        return path.getNameCount() > 1
                && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("action.yml")
                || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("action.yaml"));
    }

    @NotNull
    @Override
    public String getName() {
        return "GitHub Action [Auto]";
    }


    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
        return GITHUB_SCHEMA_CACHE.computeIfAbsent(SCHEMA_URL, key -> getSchema(SCHEMA_URL, NAME));
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
