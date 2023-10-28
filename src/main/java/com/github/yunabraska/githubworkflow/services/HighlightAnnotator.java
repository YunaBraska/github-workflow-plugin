package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.IconRenderer;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.addAnnotation;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteElementAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.getFirstChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.*;
import static com.github.yunabraska.githubworkflow.logic.Action.highLightAction;
import static com.github.yunabraska.githubworkflow.logic.Action.highlightActionInput;
import static com.github.yunabraska.githubworkflow.logic.Envs.highLightEnvs;
import static com.github.yunabraska.githubworkflow.logic.GitHub.highLightGitHub;
import static com.github.yunabraska.githubworkflow.logic.Inputs.highLightInputs;
import static com.github.yunabraska.githubworkflow.logic.Jobs.highLightJobs;
import static com.github.yunabraska.githubworkflow.logic.Needs.highlightNeeds;
import static com.github.yunabraska.githubworkflow.logic.Runner.highlightRunner;
import static com.github.yunabraska.githubworkflow.logic.Secrets.highLightSecrets;
import static com.github.yunabraska.githubworkflow.logic.Steps.highlightSteps;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.intellij.lang.annotation.HighlightSeverity.INFORMATION;
import static java.util.Optional.ofNullable;

public class HighlightAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
        //it's needed to handle single elements instead of bulk wise from parent. Parent elements are doesn't update so often.
        if (psiElement.isValid()) {
            processPsiElement(holder, psiElement);
            variableElementHandler(holder, psiElement);
            highlightRunOutputs(holder, psiElement);
            // HIGHLIGHT ACTION INPUTS
            highlightActionInput(holder, psiElement);
        }
    }

    //TODO: handle single elements instead of bulk updates for more reliability
    public static void processPsiElement(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            switch (element.getKeyText()) {
                case FIELD_USES -> highLightAction(holder, element);
                case FIELD_NEEDS -> needsHandler(holder, element);
                case FIELD_OUTPUTS -> outputsHandler(holder, element);
                default -> {
                    // No Action
                }
            }
        });
    }

    private static void highlightRunOutputs(final AnnotationHolder holder, final PsiElement psiElement) {
        // SHOW Output Env && Output Variable declaration
        Optional.of(psiElement)
                .filter(LeafPsiElement.class::isInstance)
                .map(LeafPsiElement.class::cast)
                .filter(element -> PsiElementHelper.getParent(element, FIELD_RUN).isPresent())
                .ifPresent(element -> Stream.of(
                        parseEnvVariables(element),
                        parseOutputVariables(element)
                ).flatMap(Collection::stream).collect(Collectors.groupingBy(SimpleElement::startIndexOffset)).forEach((integer, elements) -> ofNullable(getFirstChild(elements)).ifPresent(lineElement -> holder
                        .newSilentAnnotation(INFORMATION)
                        .range(lineElement.range())
                        .gutterIconRenderer(new IconRenderer(null, element, ICON_TEXT_VARIABLE))
                        .create()
                )));
    }

    private static void needsHandler(final AnnotationHolder holder, final YAMLKeyValue element) {
        final List<PsiElement> neededJobs = getTextElements(element);
        if (!neededJobs.isEmpty()) {
            final String currentJobName = getParentJob(element).map(YAMLKeyValue::getKeyText).orElse("");
            final List<String> previousJobNames = getAllJobs(element).stream().map(YAMLKeyValue::getKeyText).takeWhile(jobName -> !currentJobName.equals(jobName)).toList();
            neededJobs.forEach(neededJob -> {
                final String jobId = removeQuotes(neededJob.getText());
                if (!previousJobNames.contains(jobId)) {
                    // INVALID JOB_ID
                    addAnnotation(holder, neededJob, new SyntaxAnnotation(
                            "Remove invalid jobId [" + jobId + "] - this jobId doesn't match any previous job",
                            null,
                            deleteElementAction(neededJob.getTextRange())
                    ));
                }
            });

        }

    }


    private static void outputsHandler(final AnnotationHolder holder, final PsiElement psiElement) {
        getParentJob(psiElement).ifPresent(job -> {
            final List<YAMLKeyValue> outputs = PsiElementHelper.getChildren(psiElement).stream().toList();
            final String workflowText = PsiElementHelper.getChild(psiElement.getContainingFile(), FIELD_JOBS).map(PsiElement::getText).orElse("");
            final List<String> workflowOutputs = PsiElementHelper.getChild(psiElement.getContainingFile(), FIELD_ON)
                    .map(on -> getAllElements(on, FIELD_OUTPUTS))
                    .map(list -> list.stream().flatMap(keyValue -> PsiElementHelper.getChildren(keyValue).stream().map(output -> getText(output, "value").orElse(""))).toList())
                    .orElseGet(Collections::emptyList);
            outputs.stream().filter(output -> {
                final String outputKey = output.getKeyText();
                return workflowOutputs.stream().noneMatch(
                        wo -> wo.contains(FIELD_JOBS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey + " ") || wo.contains(FIELD_JOBS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey + "}")
                ) && !workflowText.contains(FIELD_NEEDS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey + " ") && !workflowText.contains(FIELD_NEEDS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey + "}");
            }).forEach(output -> new SyntaxAnnotation(
                    "Unused [" + output.getKeyText() + "]",
                    null,
                    HighlightSeverity.WEAK_WARNING,
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    deleteElementAction(output.getTextRange()),
                    true
            ).createAnnotation(output, output.getTextRange(), holder));

        });
    }


    private static void variableElementHandler(final AnnotationHolder holder, final PsiElement psiElement) {
        final Optional<YAMLKeyValue> parentIf = getParent(psiElement, FIELD_IF);
        Optional.of(psiElement)
                .filter(LeafPsiElement.class::isInstance)
                .map(LeafPsiElement.class::cast)
                .filter(isElementWithVariables(parentIf.orElse(null)))
                .ifPresent(element -> toSimpleElements(element).forEach(simpleElement -> {
                            final SimpleElement[] parts = splitToElements(simpleElement);
                            switch (parts.length > 0 ? parts[0].text() : "N/A") {
                                case FIELD_INPUTS -> highLightInputs(holder, element, parts);
                                case FIELD_SECRETS -> highLightSecrets(holder, psiElement, element, simpleElement, parts, parentIf.orElse(null));
                                case FIELD_ENVS -> highLightEnvs(holder, element, parts);
                                case FIELD_GITHUB -> highLightGitHub(holder, element, parts);
                                case FIELD_RUNNER -> highlightRunner(holder, element, parts);
                                case FIELD_STEPS -> highlightSteps(holder, element, parts);
                                case FIELD_JOBS -> highLightJobs(holder, element, parts);
                                case FIELD_NEEDS -> highlightNeeds(holder, element, parts);
                                default -> {
                                    // ignored
                                }
                            }
                        })
                );
    }

    @NotNull
    public static Predicate<PsiElement> isElementWithVariables(final YAMLKeyValue parentIf) {
        return element -> ofNullable(parentIf)
                .or(() -> getParent(element, FIELD_RUN))
                .or(() -> getParent(element, FIELD_ID))
                .or(() -> getParent(element, "name"))
                .or(() -> getParent(element, "value").isPresent() ? getParent(element, FIELD_OUTPUTS) : Optional.empty())
                .or(() -> getParent(element, FIELD_WITH))
                .or(() -> getParent(element, FIELD_ENVS))
                .or(() -> getParent(element, FIELD_OUTPUTS))
                .isPresent();
    }

    public static final Key<SimpleElement[]> VARIABLE_ELEMENTS = new Key<>("com.github.yunabraska.githubworkflow.VariableElements");

    @NotNull
    public static List<SimpleElement> toSimpleElements(final PsiElement element) {
        return Arrays.stream(element.getText().split("\\R"))
                .map(String::trim).filter(PsiElementHelper::hasText)
                // EXCLUDE COMMENT LINES
                .filter(s -> !s.startsWith("#"))
                .flatMap(s -> findDottedExpressions(s).stream())
                .toList();
    }

    @NotNull
    public static SimpleElement[] splitToElements(final SimpleElement simpleElement) {
        final AtomicInteger start = new AtomicInteger(simpleElement.range().getStartOffset());
        return Arrays.stream(simpleElement.text().split("\\."))
                .map(s -> s.replace("{", ""))
                .map(s -> s.replace("}", ""))
                .map(String::trim)
                .filter(PsiElementHelper::hasText)
                .map(part -> {
                    final int length = part.length();
                    final TextRange range = new TextRange(start.get(), start.get() + length);
                    start.addAndGet(length + 1);
                    return new SimpleElement(part, range);
                })
                .toArray(SimpleElement[]::new);
    }

    public static List<SimpleElement> findDottedExpressions(final String text) {
        final List<SimpleElement> elements = new ArrayList<>();
        final StringBuilder currentElement = new StringBuilder();
        int elementStart = -1;
        char previousChar = ' ';

        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            final boolean letterOrDigit = Character.isLetterOrDigit(ch);


            if (elementStart == -1 && letterOrDigit && (Character.isWhitespace(previousChar) || previousChar != '{')) {
                // START
                elementStart = i;
                currentElement.setLength(0);
                currentElement.append(ch);
            } else if (elementStart != -1 && (letterOrDigit || ch == '_' || ch == '-' || ch == '.')) {
                // MIDDLE
                currentElement.append(ch);
                // LAST ITEM
                if (i + 1 == text.length()) {
                    elementStart = validateAndAddElement(currentElement, elements, elementStart, i);
                }
            } else if (elementStart != -1 && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i)) || text.charAt(i + 1) == '}')) {
                // END
                elementStart = validateAndAddElement(currentElement, elements, elementStart, i);
            }
            previousChar = ch;
        }
        return elements;
    }

    private static int validateAndAddElement(final StringBuilder currentElement, final List<SimpleElement> elements, int elementStart, final int i) {
        if (!currentElement.isEmpty() && currentElement.length() > 1 && currentElement.toString().contains(".")) {
            elements.add(new SimpleElement(currentElement.toString(), new TextRange(elementStart, i)));
        }
        elementStart = -1;
        currentElement.setLength(0);
        return elementStart;
    }


}
