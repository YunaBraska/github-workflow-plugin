package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.psi.PsiElement;

public record ExpressionReferenceTarget(String kind, SimpleElement source, SimpleElement segment, PsiElement target) {
}
