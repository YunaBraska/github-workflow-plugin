package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;
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
                    return getWorkflowFile(psiElement).isEmpty() ? PsiReference.EMPTY_ARRAY : Optional.of(psiElement)
                        .filter(PsiElementHelper::isTextElement)
                        .flatMap(element -> {
                                final String text = element.getText().replace("IntellijIdeaRulezzz ", "").replace("IntellijIdeaRulezzz", "");
                                return referenceGithubAction(element)
                                    .or(() -> referenceNeeds(element, text))
                                    ;
                            }
                        )
                        .orElse(PsiReference.EMPTY_ARRAY);
                }
            }
        );
    }
}
