package com.github.yunabraska.githubworkflow.quickfixes;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static java.util.Optional.ofNullable;

public class JumpToFile extends QuickFix {

    private final GitHubAction action;

    public JumpToFile(final GitHubAction action, final Icon icon) {
        super(icon);
        this.action = action;
    }

    @NotNull
    @Override
    public String getText() {
        return "Navigate to [" + ofNullable(action.slug()).orElseGet(action::uses) + "]";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        // Get VirtualFile for target
        ofNullable(action)
                .map(a -> a.getLocalPath(project))
                .map(path -> LocalFileSystem.getInstance().findFileByPath(path))
                .map(target -> PsiManager.getInstance(project).findFile(target))
                .ifPresent(psiFile -> {
                    // Navigate to PsiElement
                    PsiNavigationSupport.getInstance().createNavigatable(project, psiFile.getVirtualFile(), 0).navigate(true);
                });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
