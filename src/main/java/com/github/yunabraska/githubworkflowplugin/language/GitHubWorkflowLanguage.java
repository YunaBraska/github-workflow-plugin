package com.github.yunabraska.githubworkflowplugin.language;

import com.intellij.lang.Language;

public class GitHubWorkflowLanguage extends Language {
    public static final GitHubWorkflowLanguage INSTANCE = new GitHubWorkflowLanguage();

    private GitHubWorkflowLanguage() {
        super("GitHubWorkflow");
    }

}
