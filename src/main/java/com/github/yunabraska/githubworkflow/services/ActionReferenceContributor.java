package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.LocalReferenceResolver;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;

public class ActionReferenceContributor extends PsiReferenceContributor {

    public static final Key<GitHubAction> ACTION_KEY = new Key<>("ACTION_KEY");

    @Override
    public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiElement.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(
                            @NotNull final PsiElement psiElement,
                            @NotNull final ProcessingContext context
                    ) {
                        return getWorkflowFile(psiElement).isEmpty() ? PsiReference.EMPTY_ARRAY : Optional.of(psiElement)
                                .filter(PsiElementHelper::isTextElement)
                                .flatMap(element -> PsiElementHelper.getParent(psiElement, FIELD_USES).map(GitHubActionCache::getAction))
                                .filter(GitHubAction::isResolved)
                                .filter(action -> !action.isSuppressed())
                                .map(action -> {
                                    psiElement.putUserData(ACTION_KEY, action);
                                    return getPsiReferences(psiElement, action);
                                })
                                //TODO: future - need to work on own elements
//                                .or(() -> Optional.of(psiElement)
//                                        .filter(isElementWithVariables(getParent(psiElement, FIELD_IF).orElse(null)))
//                                        .map(HighlightAnnotator::toSimpleElements)
//                                        .map(simpleElements -> {
//                                            psiElement.putUserData(VARIABLE_ELEMENTS, simpleElements.toArray(new SimpleElement[0]));
//                                            return new PsiReference[]{new VariableReferenceResolver(psiElement)};
//                                        })
//                                )
                                .orElse(PsiReference.EMPTY_ARRAY);
                    }
                }
        );
    }

    @NotNull
    private static PsiReference[] getPsiReferences(final PsiElement psiElement, final GitHubAction action) {
        return action.isLocal()
                ? new PsiReference[]{new LocalReferenceResolver(psiElement)}
                : new WebReference[]{new WebReference(psiElement, action.githubUrl())};
    }
}
