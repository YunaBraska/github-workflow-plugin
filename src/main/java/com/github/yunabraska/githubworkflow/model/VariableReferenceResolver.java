package com.github.yunabraska.githubworkflow.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.jetbrains.rd.util.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ID;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.removeQuotes;
import static com.github.yunabraska.githubworkflow.logic.Inputs.listInputsRaw;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listJobs;
import static com.github.yunabraska.githubworkflow.logic.Needs.getJobNeed;
import static com.github.yunabraska.githubworkflow.logic.Steps.listSteps;
import static com.github.yunabraska.githubworkflow.services.HighlightAnnotator.VARIABLE_ELEMENTS;
import static com.github.yunabraska.githubworkflow.services.HighlightAnnotator.splitToElements;
import static java.util.Optional.ofNullable;

public class VariableReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    public VariableReferenceResolver(@NotNull final PsiElement element) {
        super(element);
    }

    @Override
    public @NotNull ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
        return ofNullable(myElement.getUserData(VARIABLE_ELEMENTS)).map(simpleElements -> {
            final List<ResolveResult> resolveResults = new ArrayList<>();
            for (final SimpleElement simpleElement : simpleElements) {
                final SimpleElement[] parts = splitToElements(simpleElement);
                final AtomicReference<PsiElement> reference = new AtomicReference<>(null);
                switch (parts.length > 1 ? parts[0].text() : "N/A") {
                    case FIELD_INPUTS ->
                            listInputsRaw(myElement).stream().filter(input -> input.getKeyText().equals(parts[1].text())).findFirst().ifPresent(reference::getAndSet);
                    case FIELD_JOBS ->
                            listJobs(myElement).stream().filter(input -> input.getKeyText().equals(parts[1].text())).findFirst().ifPresent(reference::getAndSet);
                    case FIELD_NEEDS ->
                            getJobNeed(myElement).stream().flatMap(need -> getTextElements(need).stream()).filter(need -> removeQuotes(need.getText()).equals(parts[1].text())).findFirst().ifPresent(reference::getAndSet);
                    case FIELD_STEPS ->
                            listSteps(myElement).stream().map(step -> getChild(step, FIELD_ID).orElse(null)).filter(Objects::nonNull).filter(input -> input.getKeyText().equals(parts[1].text())).findFirst().ifPresent(reference::getAndSet);
                    default -> reference.getAndSet(null);
                }
                ofNullable(reference.get()).map(PsiElementResolveResult::new).ifPresent(resolveResults::add);
            }
            return resolveResults.toArray(new ResolveResult[0]);
        }).orElse(ResolveResult.EMPTY_ARRAY);
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
