package com.github.yunabraska.githubworkflow.services;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

public final class GitHubWorkflowBundle {

    @NonNls
    private static final String BUNDLE = "messages.GitHubWorkflowBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(GitHubWorkflowBundle.class, BUNDLE);

    public static String message(@PropertyKey(resourceBundle = BUNDLE) final String key, final Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    private GitHubWorkflowBundle() {
        // static bundle
    }
}
