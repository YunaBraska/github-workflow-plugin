package com.github.yunabraska.githubworkflow.quickfixes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class CustomClickAction extends AnAction {
    private final QuickFix quickFix;
    private final PsiElement psiElement;

    public CustomClickAction(final QuickFix quickFix, final PsiElement psiElement) {
        this.quickFix = quickFix;
        this.psiElement = psiElement;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        if (quickFix.isAvailable(psiElement.getProject(), null, psiElement.getContainingFile())) {
            quickFix.invoke(psiElement.getProject(), null, psiElement.getContainingFile());
        }
    }
}
