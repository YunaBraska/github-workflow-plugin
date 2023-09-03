package com.github.yunabraska.githubworkflow.highlights;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class CustomClickAction2 extends AnAction {
    private final SyntaxAnnotation quickFix;
    private final PsiElement psiElement;

    public CustomClickAction2(final SyntaxAnnotation quickFix, final PsiElement psiElement) {
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
