package com.github.yunabraska.githubworkflow.services;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;

/**
 * Opens workflow completion while the user types YAML structure and expression separators.
 */
public final class WorkflowAutoPopupTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result checkAutoPopup(
            final char typeChar,
            @NotNull final Project project,
            @NotNull final Editor editor,
            @NotNull final PsiFile file
    ) {
        // Structural workflow completion is scheduled after the typed character lands in the document.
        return Result.CONTINUE;
    }

    @Override
    public @NotNull Result charTyped(
            final char typeChar,
            @NotNull final Project project,
            @NotNull final Editor editor,
            @NotNull final PsiFile file
    ) {
        if (shouldAutoPopup(typeChar, editor, file)) {
            scheduleWorkflowPopup(project, editor);
        }
        return Result.CONTINUE;
    }

    static void scheduleWorkflowPopup(final Project project, final Editor editor) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || editor.isDisposed()) {
                return;
            }
            final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            documentManager.commitDocument(editor.getDocument());
            final PsiFile file = documentManager.getPsiFile(editor.getDocument());
            if (file != null && getWorkflowFile(file).isPresent()) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            }
        });
    }

    static boolean shouldAutoPopup(final char typeChar, final Editor editor, final PsiFile file) {
        if (!CodeCompletion.workflowCompletionTrigger(typeChar) || editor == null || file == null) {
            return false;
        }
        final int textLength = file.getTextLength();
        if (textLength <= 0) {
            return getWorkflowFile(file).isPresent();
        }
        final int offset = Math.max(0, Math.min(editor.getCaretModel().getOffset(), textLength - 1));
        final PsiElement element = Optional.ofNullable(file.findElementAt(offset)).orElse(file);
        return getWorkflowFile(element).isPresent();
    }
}
