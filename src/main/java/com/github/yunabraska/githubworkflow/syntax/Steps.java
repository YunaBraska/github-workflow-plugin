package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_CONCLUSION;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ID;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTCOME;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUNS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.VALID_OUTPUT_FIELDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.VALID_STEP_FIELDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isField2Valid;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isValidItem3;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.syntax.Action.highlightActionOutputs;
import static com.github.yunabraska.githubworkflow.syntax.Action.listActionsOutputs;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_STEP;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static java.util.Optional.ofNullable;

public class Steps {

    // ########## SYNTAX HIGHLIGHTING ##########
    public static void highlightSteps(final AnnotationHolder holder, final LeafPsiElement psiElement, final SimpleElement[] parts) {
        if (parts.length > 2 && List.of(FIELD_CONCLUSION, FIELD_OUTCOME).contains(parts[2].text())) {
            ifEnoughStepItems(holder, psiElement, parts, 3, VALID_STEP_FIELDS);
        } else {
            ifEnoughStepItems(holder, psiElement, parts, 4, VALID_OUTPUT_FIELDS);
        }
    }

    private static void ifEnoughStepItems(final AnnotationHolder holder, final PsiElement psiElement, final SimpleElement[] parts, final int numberOfItems, final List<String> validFields) {
        ifEnoughItems(holder, psiElement, parts, numberOfItems, numberOfItems, stepId -> {
            final List<YAMLSequenceItem> steps = listSteps(psiElement);
            if (isDefinedItem0(psiElement, holder, stepId, steps.stream().map(step -> getText(step, FIELD_ID).orElse(null)).filter(Objects::nonNull).toList()) && isField2Valid(psiElement, holder, parts[2], validFields)) {
                final YAMLSequenceItem stepItem = steps.stream().filter(step -> getText(step, FIELD_ID).filter(id -> id.equals(stepId.text())).isPresent()).findFirst().orElse(null);
                final List<String> runOutputs = listRunOutputs(stepItem).stream().map(SimpleElement::key).toList();
                final List<String> actionOutputs = listActionsOutputs(stepItem).stream().map(SimpleElement::key).toList();
                if (parts.length > 3) {
                    highlightActionOutputs(holder, psiElement, stepItem, parts[3]);
                    isValidItem3(psiElement, holder, parts[3], Stream.concat(runOutputs.stream(), actionOutputs.stream()).toList());
                }
            }
        });
    }

    // ########## CODE COMPLETION ##########
    public static List<SimpleElement> codeCompletionSteps(final PsiElement psiElement) {
        return listSteps(psiElement).stream().map(item -> {
            final List<YAMLKeyValue> children = WorkflowPsi.getChildren(item);
            return children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).map(stepId -> completionItemOf(
                    stepId,
                    children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).orElse(null)),
                    ICON_STEP
            )).orElse(null);
        }).filter(Objects::nonNull).toList();
    }

    public static List<SimpleElement> codeCompletionSteps(final String stepId, final PsiElement position) {
        return listStepOutputs(listSteps(position).stream().filter(step -> getText(step, FIELD_ID).filter(id -> id.equals(stepId)).isPresent()).findFirst().orElse(null));
    }

    // ########## COMMONS ##########
    public static List<YAMLSequenceItem> listSteps(final PsiElement psiElement) {
        // StepList position == step?    list previous steps in current job
        // StepList position == outputs? list all      steps in current job
        return getParentJob(psiElement).map(job -> {
            final YAMLSequenceItem currentStep = getParentStep(psiElement).orElse(null);
            final boolean isOutput = getParent(psiElement, FIELD_OUTPUTS).isPresent();
            return getChildSteps(job).stream().takeWhile(step -> isOutput || step != currentStep).toList();
        }).orElseGet(() -> getParent(psiElement, FIELD_RUNS)
                // Composite action [runs.steps]
                .flatMap(runs -> getChild(runs, FIELD_STEPS))
                .map(steps -> {
                    final YAMLSequenceItem currentStep = getParentStep(psiElement).orElse(null);
                    final boolean isOutput = getParent(psiElement, FIELD_OUTPUTS).isPresent();
                    return getChildSteps(steps).stream().takeWhile(step -> isOutput || step != currentStep).toList();
                })
                .orElseGet(() -> getParent(psiElement, FIELD_OUTPUTS)
                //Action.yaml [runs.steps]
                .map(outputs -> psiElement.getContainingFile())
                .flatMap(psiFile -> getChild(psiFile, FIELD_RUNS))
                .flatMap(runs -> getChild(runs, FIELD_STEPS))
                .map(WorkflowPsi::getChildSteps)
                .orElseGet(Collections::emptyList))
        );
    }

    public static List<SimpleElement> listStepOutputs(final YAMLSequenceItem step) {
        // Run file-command outputs and action metadata outputs are both valid step outputs.
        return Stream.concat(listRunOutputs(step).stream(), listActionsOutputs(step).stream()).toList();
    }

    @NotNull
    private static List<SimpleElement> listRunOutputs(final YAMLSequenceItem step) {
        return ofNullable(step).flatMap(s -> getChild(s, FIELD_RUN)
                .map(WorkflowPsi::parseOutputVariables)
                .map(outputs -> outputs.stream().map(output -> completionItemOf(output.key(), output.text(), ICON_TEXT_VARIABLE)).toList())
        ).orElseGet(Collections::emptyList);
    }

    private Steps() {
        // static helper class
    }
}
