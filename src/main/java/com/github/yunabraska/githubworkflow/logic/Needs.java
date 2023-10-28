package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isField2Valid;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isValidItem3;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElements;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listAllJobs;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listJobOutputs;
import static java.util.Optional.ofNullable;

public class Needs {

    public static void highlightNeeds(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 4, 4, jobId -> {
            final List<String> jobIds = listJobNeeds(element);
            if (isDefinedItem0(element, holder, jobId, jobIds) && isField2Valid(element, holder, parts[2])) {
                final List<String> outputs = listJobOutputs(listAllJobs(element).stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                isValidItem3(element, holder, parts[3], outputs);
            }
        });
    }

    public static List<SimpleElement> codeCompletionNeeds(final PsiElement psiElement) {
        final List<YAMLKeyValue> jobs = getParentJob(psiElement).map(job -> listAllJobs(psiElement).stream().takeWhile(j -> !j.getKeyText().equals(job.getKeyText())).toList()).orElseGet(Collections::emptyList);
        return listJobNeeds(psiElement).stream()
                .map(need -> need.replace("IntellijIdeaRulezzz ", ""))
                .map(need -> need.replace("IntellijIdeaRulezzz", ""))
                .map(need -> jobs.stream().filter(job -> job.getKeyText().equals(need)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .map(Jobs::jobToCompletionItem)
                .toList();
    }

    public static List<SimpleElement> codeCompletionNeeds(final String jobId, final PsiElement position) {
        return listJobOutputs(listAllJobs(position).stream().filter(job -> job.getKeyText().equals(jobId)).findFirst().orElse(null));
    }

    public static List<String> listJobNeeds(final PsiElement psiElement) {
        return getJobNeed(psiElement)
                .map(needs -> getTextElements(needs)
                        .stream().map(PsiElement::getText)
                        .map(PsiElementHelper::removeQuotes)
                        .filter(PsiElementHelper::hasText)
                        .toList()
                ).orElseGet(Collections::emptyList);
    }

    @NotNull
    public static Optional<YAMLKeyValue> getJobNeed(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .flatMap(PsiElementHelper::getParentJob)
                .flatMap(job -> getChild(job, FIELD_NEEDS));
    }

    private Needs() {
        // static helper class
    }
}
