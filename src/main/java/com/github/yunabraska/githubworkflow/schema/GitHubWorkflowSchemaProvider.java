package com.github.yunabraska.githubworkflow.schema;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;
import java.util.Optional;

public class GitHubWorkflowSchemaProvider implements JsonSchemaFileProvider {

    private final JsonSchemaFileProvider jsonSchemaFileProvider;

    public GitHubWorkflowSchemaProvider(@NotNull final Project project) {
        jsonSchemaFileProvider = JsonSchemaService.Impl.get(project).getAllUserVisibleSchemas().stream()
                .map(JsonSchemaInfo::getProvider)
                .filter(jsonSchemaInfo -> jsonSchemaInfo != null && "GitHub Workflow".equals(jsonSchemaInfo.getName()))
                .findFirst().orElse(null);
    }

    @Override
    public boolean isAvailable(@NotNull final VirtualFile file) {
        return Optional.of(file).map(VirtualFile::getPath).map(Paths::get).filter(GitHubWorkflowUtils::isWorkflowPath).isPresent();
    }

    @NotNull
    @Override
    public String getName() {
        return "GitHub Workflow [YAML]";
    }


    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
        return jsonSchemaFileProvider.getSchemaFile();
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
        return jsonSchemaFileProvider.getSchemaType();
    }
}
