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
                        return Optional.of(psiElement)
                                .filter(PsiElementHelper::isTextElement)
                                .flatMap(element -> PsiElementHelper.getParent(psiElement, FIELD_USES).map(GitHubActionCache::getAction))
                                .filter(GitHubAction::isResolved)
                                .filter(action -> !action.isSuppressed())
                                .map(action -> {
                                    psiElement.putUserData(ACTION_KEY, action);
                                    return action.isLocal()
                                            ? new PsiReference[]{new LocalReferenceResolver(psiElement)}
                                            : new WebReference[]{new WebReference(psiElement, action.githubUrl())};
                                })
                                .map(webReferences -> (PsiReference[]) webReferences)
                                .orElse(PsiReference.EMPTY_ARRAY);
                    }
                }
        );
    }
}
