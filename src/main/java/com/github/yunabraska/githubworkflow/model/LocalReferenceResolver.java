package com.github.yunabraska.githubworkflow.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final PsiElement targetElement;

    public LocalReferenceResolver(@NotNull final PsiElement souceElement, final PsiElement targetElement) {
        super(souceElement);
        this.targetElement = targetElement;
    }

    @Override
    public @NotNull ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
        if (targetElement != null) {
            return new ResolveResult[]{new PsiElementResolveResult(targetElement)};
        }
        return ResolveResult.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        final ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @Override
    public @NotNull String getCanonicalText() {
        return targetElement.getText();
    }
}
