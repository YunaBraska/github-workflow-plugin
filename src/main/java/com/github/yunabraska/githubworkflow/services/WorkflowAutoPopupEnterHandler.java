package com.github.yunabraska.githubworkflow.services;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;

/**
 * Opens workflow key completion after pressing Enter below YAML mapping keys.
 */
public final class WorkflowAutoPopupEnterHandler extends EnterHandlerDelegateAdapter {

    @Override
    public @NotNull Result postProcessEnter(
            @NotNull final PsiFile file,
            @NotNull final Editor editor,
            @NotNull final DataContext dataContext
    ) {
        if (shouldAutoPopupAfterEnter(editor, file)) {
            WorkflowAutoPopupTypedHandler.scheduleWorkflowPopup(file.getProject(), editor);
        }
        return Result.Continue;
    }

    static boolean shouldAutoPopupAfterEnter(final Editor editor, final PsiFile file) {
        if (editor == null || file == null || getWorkflowFile(file).isEmpty()) {
            return false;
        }
        final String textBeforeCaret = editor.getDocument()
                .getImmutableCharSequence()
                .subSequence(0, Math.min(editor.getCaretModel().getOffset(), editor.getDocument().getTextLength()))
                .toString();
        final int currentLineStart = textBeforeCaret.lastIndexOf('\n');
        if (currentLineStart <= 0) {
            return false;
        }
        final int previousLineStart = textBeforeCaret.lastIndexOf('\n', currentLineStart - 1) + 1;
        final String previousLine = textBeforeCaret.substring(previousLineStart, currentLineStart).trim();
        return !previousLine.startsWith("#") && previousLine.endsWith(":");
    }
}
