package com.github.yunabraska.githubworkflow.services;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;

/**
 * Keeps workflow auto-popup completion available in sparse YAML positions, such as the line after {@code on:}.
 */
public final class WorkflowCompletionConfidence extends CompletionConfidence {

    @Override
    public @NotNull ThreeState shouldSkipAutopopup(
            final Editor editor,
            final PsiElement contextElement,
            final PsiFile psiFile,
            final int offset
    ) {
        return getWorkflowFile(psiFile).isPresent() || getWorkflowFile(contextElement).isPresent()
                ? ThreeState.NO
                : ThreeState.UNSURE;
    }
}
