package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.github.yunabraska.githubworkflow.utils.PsiElementHelper.getProject;


public class CustomClickAction extends AnAction {
    private final SyntaxAnnotation quickFix;
    private final PsiElement psiElement;

    public CustomClickAction(final SyntaxAnnotation quickFix, final PsiElement psiElement) {
        this.quickFix = quickFix;
        this.psiElement = psiElement;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        if (quickFix.isAvailable(getProject(psiElement), null, psiElement.getContainingFile())) {
            quickFix.invoke(getProject(psiElement), null, psiElement.getContainingFile());
        }
    }
}
