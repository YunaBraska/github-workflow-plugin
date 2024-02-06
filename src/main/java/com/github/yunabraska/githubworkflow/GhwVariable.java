package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import java.util.List;

import static com.github.yunabraska.githubworkflow.services.HighlightAnnotator.splitToElements;
import static com.github.yunabraska.githubworkflow.services.HighlightAnnotator.toSimpleElements;

public class GhwVariable {

    private final TextRange range;
    private final SimpleElement[] parts;

    public GhwVariable(final SimpleElement simpleElement, final PsiElement element) {
        final int startOffset = element.getTextRange().getStartOffset();
        range = new TextRange(startOffset + simpleElement.startIndexOffset(), startOffset + simpleElement.endIndexOffset());
        parts = splitToElements(simpleElement);
    }

    public TextRange range() {
        return range;
    }

    public SimpleElement[] parts() {
        return parts;
    }

    public static List<GhwVariable> ghwVariablesOf(final PsiElement psiElement) {
        return toSimpleElements(psiElement).stream().map(element -> new GhwVariable(element, psiElement)).toList();
    }


}
