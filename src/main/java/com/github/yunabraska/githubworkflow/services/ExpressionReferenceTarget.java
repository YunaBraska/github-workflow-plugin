package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.psi.PsiElement;

record ExpressionReferenceTarget(String kind, SimpleElement source, SimpleElement segment, PsiElement target) {
}
