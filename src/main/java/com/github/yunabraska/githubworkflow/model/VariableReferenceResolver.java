package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Optional.ofNullable;

public class VariableReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final PsiElement targetElement;

    public VariableReferenceResolver(
            @NotNull final PsiElement element,
            @NotNull final TextRange rangeInElement,
            @NotNull final PsiElement targetElement
    ) {
        super(element, rangeInElement);
        this.targetElement = targetElement;
    }

    @Override
    public @NotNull ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
        return ofNullable(targetElement)
                .map(PsiElementResolveResult::new)
                .map(result -> new ResolveResult[]{result})
                .orElse(ResolveResult.EMPTY_ARRAY);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        final ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @Override
    public @NotNull String getCanonicalText() {
        return myElement.getText();
    }

}
