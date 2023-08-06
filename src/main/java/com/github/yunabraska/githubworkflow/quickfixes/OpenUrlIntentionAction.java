package com.github.yunabraska.githubworkflow.quickfixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class OpenUrlIntentionAction implements IntentionAction {
    private final String url;
    private final String text;

    public OpenUrlIntentionAction(final String url, final String text) {
        this.url = url;
        this.text = text;
    }

    @NotNull
    @Override
    public String getText() {
        return text;
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Open URL";
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
        BrowserUtil.browse(url);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
