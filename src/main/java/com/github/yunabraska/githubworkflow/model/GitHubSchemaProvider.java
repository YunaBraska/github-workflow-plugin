package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class GitHubSchemaProvider implements JsonSchemaFileProvider {

    private final String schemaName;
    private final String displayName;
    private final Predicate<Path> validatePath;

    public GitHubSchemaProvider(final String schemaName, final String displayName, final Predicate<Path> validatePath) {
        this.schemaName = schemaName;
        this.displayName = displayName;
        this.validatePath = validatePath;
    }

    @NotNull
    @Override
    public String getName() {
        return GitHubWorkflowBundle.message("schema.auto", displayName);
    }

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return Optional.of(file).flatMap(WorkflowPsi::toPath).filter(validatePath).isPresent();
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
        return new LightVirtualFile("github_workflow_plugin_" + schemaName + "_schema.json", JsonFileType.INSTANCE, schemaContent());
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
        return Objects.equals(schemaName, that.schemaName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaName);
    }

    private String schemaContent() {
        try (InputStream stream = getClass().getResourceAsStream("/schemas/" + schemaName + ".json")) {
            return stream == null ? "{}" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            return "{}";
        }
    }
}
