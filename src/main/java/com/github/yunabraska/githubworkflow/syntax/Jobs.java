package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
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

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ID;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RESULT;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isField2Valid;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isValidItem3;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildren;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.syntax.Action.listActionsOutputs;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static java.util.Optional.ofNullable;

public class Jobs {

    public static void highLightJobs(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 3, 4, jobId -> {
            final List<YAMLKeyValue> jobs = listJobs(element);
            if (isDefinedItem0(element, holder, jobId, jobs.stream().map(YAMLKeyValue::getKeyText).toList()) && isField2Valid(element, holder, parts[2], List.of(FIELD_OUTPUTS, FIELD_RESULT))) {
                if (FIELD_RESULT.equals(parts[2].text())) {
                    return;
                }
                final List<String> outputs = listJobOutputs(jobs.stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                if (parts.length > 3) {
                    isValidItem3(element, holder, parts[3], outputs);
                }
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
                        .map(WorkflowPsi::getChildren)
                        .map(children -> children.stream().map(child -> getText(child).map(value -> completionItemOf(child.getKeyText(), value, ICON_OUTPUT)).orElse(null)).filter(Objects::nonNull).toList())
                ).orElseGet(Collections::emptyList);

        //JOB USES OUTPUTS
        return Stream.concat(jobOutputs.stream(), listActionsOutputs(job).stream()).toList();
    }

    public static SimpleElement jobToCompletionItem(final YAMLKeyValue item) {
        final List<YAMLKeyValue> children = WorkflowPsi.getChildren(item);
        final YAMLKeyValue usesOrName = children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().orElse(null));
        return completionItemOf(
                children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).orElse(item.getKeyText()),
                ofNullable(usesOrName).flatMap(WorkflowPsi::getText).orElse(""),
                NodeIcon.ICON_NEEDS
        );
    }

    private Jobs() {
        // static helper class
    }
}
