package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.VariableReferenceResolver;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.removeQuotes;
import static com.github.yunabraska.githubworkflow.logic.Action.referenceGithubAction;
import static com.github.yunabraska.githubworkflow.logic.Needs.referenceNeeds;

public class ReferenceContributor extends PsiReferenceContributor {

    public static final Key<GitHubAction> ACTION_KEY = new Key<>("ACTION_KEY");

    @Override
    public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiElement.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull final PsiElement psiElement,
                            @NotNull final ProcessingContext context
                    ) {
                        return getWorkflowFile(psiElement).isEmpty() ? PsiReference.EMPTY_ARRAY : textElement(psiElement)
                                .flatMap(element -> {
                                            final String text = removeQuotes(element.getText().replace("IntellijIdeaRulezzz ", "").replace("IntellijIdeaRulezzz", ""));
                                            return referenceGithubAction(element)
                                                    .or(() -> referenceNeeds(element, text))
                                                    .or(() -> referenceVariables(element));
                                        }
                                )
                                .orElse(PsiReference.EMPTY_ARRAY);
                    }
                }
        );
    }

    private static Optional<PsiElement> textElement(final PsiElement psiElement) {
        PsiElement current = psiElement;
        while (current != null && current.getParent() != current) {
            if (PsiElementHelper.isTextElement(current)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static Optional<PsiReference[]> referenceVariables(final PsiElement psiElement) {
        final PsiReference[] references = ExpressionReferenceTargets.resolve(psiElement).stream()
                .map(target -> new VariableReferenceResolver(
                        psiElement,
                        new TextRange(target.segment().startIndexOffset(), target.segment().endIndexOffset()),
                        target.target()
                ))
                .toArray(PsiReference[]::new);
        if (references.length == 0) {
            return Optional.empty();
        }
        return Optional.of(references);
    }
}
