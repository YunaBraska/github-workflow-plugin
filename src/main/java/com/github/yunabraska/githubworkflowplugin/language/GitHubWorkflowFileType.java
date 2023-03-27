package com.github.yunabraska.githubworkflowplugin.language;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GitHubWorkflowFileType extends LanguageFileType {
    public static final GitHubWorkflowFileType INSTANCE = new GitHubWorkflowFileType();


    private GitHubWorkflowFileType() {
        super(GitHubWorkflowLanguage.INSTANCE);
        System.out.println("GitHubWorkflowFileType initialized");
    }

    @NotNull
    @Override
    public String getName() {
        return "GitHubWorkflow";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "GitHub Workflow File";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "yml";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Yaml;
    }
}
