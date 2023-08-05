package com.github.yunabraska.githubworkflow.quickfixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ReplaceTextIntentionAction implements IntentionAction {
    private final boolean delete;
    private final String newText;
    private final TextRange textRange;

    public ReplaceTextIntentionAction(final TextRange textRange, final String newText, final boolean delete) {
        this.delete = delete;
        this.newText = newText;
        this.textRange = textRange;
    }

    @NotNull
    @Override
    public String getText() {
        return (delete ? "Delete " : "Replace with ") + newText;
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "ReplaceText";
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        if (element != null) {
            final Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
            if (document != null) {
                WriteCommandAction.runWriteCommandAction(project, () -> document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), delete ? "" : newText));
            }
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}

