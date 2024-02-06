package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.logic.Action;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.LocalReferenceResolver;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.services.GitHubWorkflowService.GHA_SERVICE_KEY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_ELEMENT_REFERENCE_KEY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;

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

                        final LocalReferenceResolver localReference = Optional.ofNullable(psiElement.getUserData(GHW_ELEMENT_REFERENCE_KEY))
                                .map(SmartPsiElementPointer::getElement)
                                .filter(PsiElement::isValid)
                                .map(target -> new LocalReferenceResolver(psiElement, target)).orElse(null);

                        if (localReference != null) {
                            System.out.println("[PsiReferenceContributor] add reference to element " + psiElement.getText());
                            return new PsiReference[]{localReference};
                        } else {
                            try {
                                psiElement.getContainingFile().getVirtualFile().putUserData(GHA_SERVICE_KEY, null);
                            } catch (final Exception ignored) {

                            }
                            return getWorkflowFile(psiElement).isEmpty() ? PsiReference.EMPTY_ARRAY : Optional.of(psiElement)
                                    .filter(PsiElementHelper::isTextElement)
                                    .flatMap(Action::referenceGithubAction)
                                    .orElse(PsiReference.EMPTY_ARRAY);
                        }
                    }
                }
        );
    }
}
