package com.github.yunabraska.githubworkflow;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_ANNOTATION_KEY;

public class ReplaceQuickFix implements IntentionAction {
    private final PsiElement element;
    private final String newValue;
    private final int index;

    ReplaceQuickFix(final int index, final PsiElement element, final String newValue) {
        this.index = Math.max(index, 1);
        this.element = element;
        this.newValue = newValue;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getText() {
        return newValue == null || newValue.isEmpty() || newValue.isBlank() ? "Remove" : String.format("%02d: Replace with [%s]", index, newValue);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
        return "Replace fixes";
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile psiFile) {
        return element.isValid();
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
        final Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
        if (document != null) {
            element.putUserData(GHW_ANNOTATION_KEY, null);
            WriteCommandAction.runWriteCommandAction(project, () -> document.replaceString(
                    element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(), newValue
            ));
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
