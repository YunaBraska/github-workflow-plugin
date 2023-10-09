package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.rd.util.AtomicReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.psiFileToPath;
import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;

public class GitHubSchemaProvider implements JsonSchemaFileProvider {

    private final String displayName;
    private final String schemaName;
    private final String schemaUrl;
    private final AtomicReference<VirtualFile> file = new AtomicReference<>(null);
    private final Predicate<Path> validatePath;

    public GitHubSchemaProvider(final String schemaName, final String displayName, final Predicate<Path> validatePath) {
        this.schemaName = schemaName;
        this.displayName = displayName;
        this.validatePath = validatePath;
        this.schemaUrl = "https://json.schemastore.org/" + schemaName;
    }

    @NotNull
    @Override
    public String getName() {
        return displayName;
    }

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return getVirtualFile().isPresent();
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
        return getVirtualFile().orElseGet(() -> {
            final VirtualFile schema = getActionCache().getSchema(schemaUrl, schemaName);
            file.getAndSet(schema);
            return schema;
        });
    }

    private Optional<VirtualFile> getVirtualFile() {
        final VirtualFile result = file.get();
        return result != null && result.isValid() ? Optional.of(result) : Optional.empty();
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
        return schemaUrl;
    }

    public boolean isValidFile(final PsiElement element) {
        return psiFileToPath(element).filter(validatePath).isPresent();
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
