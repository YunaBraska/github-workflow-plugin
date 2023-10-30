package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Predicate;

import static java.util.Optional.ofNullable;

public class GitHubSchemaProvider implements JsonSchemaFileProvider {

    private final String displayName;
    private final VirtualFile schemaFile;
    private final Predicate<Path> validatePath;

    public GitHubSchemaProvider(final String schemaName, final String displayName, final Predicate<Path> validatePath) {
        this.displayName = displayName;
        this.validatePath = validatePath;

        schemaFile = ofNullable(getClass().getResourceAsStream("/schemas/" + schemaName + ".json"))
                .map(schemaStream -> {
                    try (final Scanner scanner = new Scanner(schemaStream, StandardCharsets.UTF_8)) {
                        final String schemaContent = scanner.useDelimiter("\\A").next();
                        return new LightVirtualFile("github_workflow_plugin_" + schemaName + "_schema.json", JsonFileType.INSTANCE, schemaContent);
                    }
                })
                .orElse(null);
    }

    @NotNull
    @Override
    public String getName() {
        return displayName;
    }

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return Optional.of(file).flatMap(PsiElementHelper::toPath).filter(validatePath).isPresent();
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
        return schemaFile;
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
        return SchemaType.schema;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GitHubSchemaProvider that = (GitHubSchemaProvider) o;
        return Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
}
