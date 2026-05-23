package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getAllElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStep;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getText;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElement;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_ROOT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_STEP;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class Envs {

    public static void highLightEnvs(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, envId -> isDefinedItem0(element, holder, envId, listEnvs(element).stream().map(SimpleElement::key).toList()));
    }

    public static List<SimpleElement> listEnvs(final PsiElement psiElement) {
        final List<SimpleElement> result = new ArrayList<>();

        // CURRENT STEP TEXT ENVS [jobs.job_id.steps.step_id.run:key=value]
        addRunEnvs(psiElement, result);

        // CURRENT STEP ENVS [step.env.env_key:env_value]
        addStepEnvs(psiElement, result);

        // CURRENT JOB ENVS [jobs.job_id.envs.env_id:env_value]
        addJobEnvs(psiElement, result);

        // WORKFLOW ENVS
        addWorkflowEnvs(psiElement, result);

        //DEFAULT ENVS
        addDefaultEnvs(result);

        return result;
    }

    private static void addRunEnvs(final PsiElement psiElement, final List<SimpleElement> result) {
        final TextRange currentRange = psiElement.getTextRange();
        result.addAll(completionItemsOf(
                getAllElements(psiElement.getContainingFile(), FIELD_RUN).stream()
                        // only FIELD_RUN from previous FIELD_STEP
                        .filter(keyValue -> getParentStep(keyValue).map(PsiElement::getTextRange).map(TextRange::getStartOffset).orElse(currentRange.getEndOffset()) < currentRange.getStartOffset())
                        .map(PsiElementHelper::parseEnvVariables)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toMap(SimpleElement::key, SimpleElement::textNoQuotes, (existing, replacement) -> existing))
                , ICON_TEXT_VARIABLE
        ));
    }

    private static void addWorkflowEnvs(final PsiElement psiElement, final List<SimpleElement> result) {
        getChild(psiElement.getContainingFile(), FIELD_ENVS)
                .map(PsiElementHelper::getChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_ROOT))
                .ifPresent(result::addAll);
    }

    private static void addJobEnvs(final PsiElement psiElement, final List<SimpleElement> result) {
        getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_ENVS))
                .map(PsiElementHelper::getChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_JOB))
                .ifPresent(result::addAll);
    }

    private static void addStepEnvs(final PsiElement psiElement, final List<SimpleElement> result) {
        getParentStep(psiElement)
                .flatMap(step -> getChild(step, FIELD_ENVS))
                .map(PsiElementHelper::getChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_STEP))
                .ifPresent(result::addAll);
    }

    private static Function<List<YAMLKeyValue>, Map<String, String>> toMapWithKeyAndText() {
        return elements -> elements.stream()
                .filter(keyValue -> getTextElement(keyValue).isPresent())
                .collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue).orElse(""), (existing, replacement) -> existing));
    }

    private static void addDefaultEnvs(final List<SimpleElement> result) {
        result.addAll(completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get(), ICON_ENV));
    }

    private Envs() {
        // static helper class
    }
}
