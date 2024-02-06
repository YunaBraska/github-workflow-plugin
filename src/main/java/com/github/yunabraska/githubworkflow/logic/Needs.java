package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.LocalReferenceResolver;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.addAnnotation;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteElementAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isField2Valid;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isValidItem3;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newJumpToFile;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElement;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.goToDeclarationString;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listAllJobs;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listJobOutputs;
import static java.util.Optional.ofNullable;

public class Needs {

    // ########## SYNTAX HIGHLIGHTING ##########

    private Needs() {
        // static helper class
    }

    // variable field
    public static void highlightNeeds(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 4, 4, jobId -> {
            final List<String> jobIds = listJobNeeds(element);
            if (isDefinedItem0(element, holder, jobId, jobIds) && isField2Valid(element, holder, parts[2])) {
                // TODO: find target for highlighting reference
                // TODO: implement reference ... does highlighting comes after reference which could add an reference indicator?
                final List<String> outputs = listJobOutputs(listAllJobs(element).stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                isValidItem3(element, holder, parts[3], outputs);
            }
        });
    }

    // needs field
    public static void highlightNeeds(final AnnotationHolder holder, final PsiElement psiElement) {
        ofNullable(psiElement)
                .filter(PsiElementHelper::isTextElement)
                .filter(element -> getParent(element, FIELD_NEEDS).isPresent())
                .ifPresent(element -> {
                    final List<String> jobsNames = listJobs(psiElement).stream().map(YAMLKeyValue::getKeyText).toList();
                    if (!jobsNames.contains(element.getText())) {
                        // INVALID JOB_ID
                        addAnnotation(holder, psiElement, new SyntaxAnnotation(
                                "Remove invalid jobId [" + element.getText() + "] - this jobId doesn't match any previous job",
                                null,
                                deleteElementAction(psiElement.getTextRange())
                        ));
                    } else {
                        final String tooltip = goToDeclarationString();
                        holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                                .range(psiElement)
                                .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                                .tooltip(tooltip)
                                .create();
                    }
                });
    }

    // ########## CODE COMPLETION ##########
    public static List<SimpleElement> codeCompletionNeeds(final PsiElement psiElement) {
        final List<YAMLKeyValue> jobs = listJobs(psiElement);
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

    // ########## REFERENCE RESOLVER ##########
    public static Optional<PsiReference[]> referenceNeeds(final PsiElement psiElement, final String need) {
        return getParent(psiElement, FIELD_NEEDS)
                .flatMap(needs -> listJobs(needs).stream().filter(keyValue -> keyValue.getKeyText().equals(need)).findFirst())
                .map(job -> new PsiReference[]{new LocalReferenceResolver(psiElement, job)});
    }

    private static void highlightLocalActions( // todo not used???
                                               final AnnotationHolder holder,
                                               final YAMLKeyValue element,
                                               final GitHubAction action,
                                               final List<SyntaxAnnotation> result
    ) {
        if (action.isResolved() && action.isLocal()) {
            final String tooltip = String.format("Open declaration (%s)", Arrays.stream(KeymapUtil.getActiveKeymapShortcuts("GotoDeclaration").getShortcuts())
                    .limit(2)
                    .map(KeymapUtil::getShortcutText)
                    .collect(Collectors.joining(", "))
            );
            getTextElement(element).ifPresent(textElement -> {
                holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                        .range(textElement)
                        .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                        .tooltip(tooltip)
                        .create();
                result.add(newJumpToFile(action));
            });
        }
    }

    // ########## COMMONS ##########
    private static List<YAMLKeyValue> listJobs(final PsiElement psiElement) {
        return getParentJob(psiElement).map(job -> listAllJobs(psiElement).stream().takeWhile(j -> !j.getKeyText().equals(job.getKeyText())).toList()).orElseGet(Collections::emptyList);
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
}
