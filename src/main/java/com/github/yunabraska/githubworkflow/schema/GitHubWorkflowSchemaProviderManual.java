package com.github.yunabraska.githubworkflow.schema;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadContent;

public class GitHubWorkflowSchemaProviderManual implements JsonSchemaFileProvider {

    private static final String SCHEMA_URL = "https://json.schemastore.org/github-workflow";

    private final Project project;
    public GitHubWorkflowSchemaProviderManual(@NotNull final Project project) {
        this.project = project;
        try {
            final VirtualFile jsonSchemaFile = createJSONSchemaFile(ModuleManager.getInstance(project).getModules()[0].getName());
            VfsUtil.saveText(jsonSchemaFile, downloadContent("https://json.schemastore.org/github-workflow"));
            System.out.println(jsonSchemaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        try {
            final VirtualFile jsonSchemaFile = createJSONSchemaFile(ModuleManager.getInstance(project).getModules()[0].getName());
            VfsUtil.saveText(jsonSchemaFile, downloadContent("https://json.schemastore.org/github-workflow"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static VirtualFile createJSONSchemaFile(final String name) throws IOException {
        return new LightVirtualFile(name + "-schema.json", JsonFileType.INSTANCE, "");
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
        return null;
    }
}
