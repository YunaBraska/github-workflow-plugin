package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ID;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isField2Valid;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isValidItem3;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getAllElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChildren;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getText;
import static com.github.yunabraska.githubworkflow.logic.Action.listActionsOutputs;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static java.util.Optional.ofNullable;

public class Jobs {

    private Jobs() {
        // static helper class
    }

    public static void highLightJobs(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 4, 4, jobId -> {
            final List<YAMLKeyValue> jobs = listJobs(element);
            if (isDefinedItem0(element, holder, jobId, jobs.stream().map(YAMLKeyValue::getKeyText).toList()) && isField2Valid(element, holder, parts[2])) {
                final List<String> outputs = listJobOutputs(jobs.stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                isValidItem3(element, holder, parts[3], outputs);
            }
        });
    }

    public static List<SimpleElement> codeCompletionJobs(final PsiElement psiElement) {
        return listJobs(psiElement).stream().map(Jobs::jobToCompletionItem).toList();
    }

    public static List<SimpleElement> codeCompletionJobs(final String jobId, final PsiElement position) {
        return listJobOutputs(listJobs(position).stream().filter(job -> job.getKeyText().equals(jobId)).findFirst().orElse(null));
    }

    public static List<YAMLKeyValue> listJobs(final PsiElement psiElement) {
        //JobList is only valid in Workflow outputs
        return getParent(psiElement, FIELD_OUTPUTS)
                .flatMap(outputs -> getParent(psiElement, FIELD_ON))
                .map(Jobs::listAllJobs)
                .orElseGet(Collections::emptyList);
    }

    public static List<YAMLKeyValue> listAllJobs(final PsiElement psiElement) {
        return ofNullable(psiElement).map(element -> getAllElements(element.getContainingFile(), FIELD_JOBS).stream().flatMap(jobs -> getChildren(jobs, YAMLKeyValue.class).stream()).toList()).orElseGet(Collections::emptyList);
    }

    public static List<SimpleElement> listJobOutputs(final YAMLKeyValue job) {
        //JOB OUTPUTS
        final List<SimpleElement> jobOutputs = ofNullable(job)
                .flatMap(j -> getChild(j, FIELD_OUTPUTS)
                        .map(PsiElementHelper::getChildren)
                        .map(children -> children.stream().map(child -> getText(child).map(value -> completionItemOf(child.getKeyText(), value, ICON_OUTPUT)).orElse(null)).filter(Objects::nonNull).toList())
                ).orElseGet(Collections::emptyList);

        //JOB USES OUTPUTS
        return Stream.concat(jobOutputs.stream(), listActionsOutputs(job).stream()).toList();
    }

    public static SimpleElement jobToCompletionItem(final YAMLKeyValue item) {
        final List<YAMLKeyValue> children = PsiElementHelper.getChildren(item);
        final YAMLKeyValue usesOrName = children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().orElse(null));
        return completionItemOf(
                children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).orElse(item.getKeyText()),
                ofNullable(usesOrName).flatMap(PsiElementHelper::getText).orElse(""),
                NodeIcon.ICON_NEEDS
        );
    }
}
