package com.github.yunabraska.githubworkflow.quickfixes;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

public class ClearWorkflowCacheAction implements IntentionAction {

    private final GitHubAction action;

    public ClearWorkflowCacheAction(final GitHubAction action) {
        this.action = action;
    }

    @NotNull
    @Override
    public String getText() {
        return "Clear item cache [" + action.slug() + "]";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "ClearGhaCacheItem";
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        action.deleteCache();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
