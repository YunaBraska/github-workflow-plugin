package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.IconRenderer;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.*;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.*;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;
import static com.intellij.lang.annotation.HighlightSeverity.INFORMATION;
import static java.util.Optional.ofNullable;

public class HighlightAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
        if (psiElement.isValid()) {
            processPsiElement(holder, psiElement);
            variableElementHandler(holder, psiElement);
            highlightRunOutputs(holder, psiElement);
        }
    }

    public static void processPsiElement(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            switch (element.getKeyText()) {
                case FIELD_USES -> usesHandler(holder, element);
                case FIELD_WITH -> withHandler(holder, element);
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

    @SuppressWarnings("DataFlowIssue")
    private static void usesHandler(final AnnotationHolder holder, final YAMLKeyValue element) {
        final List<SyntaxAnnotation> result = new ArrayList<>();
        ofNullable(element)
                .map(GitHubActionCache::getAction)
                .ifPresentOrElse(action -> {
                    if (action.isResolved() && !action.isLocal()) {
                        result.add(newReloadAction(action));
                    }
                    if (action.isResolved() && action.isLocal()) {
                        final String tooltip = String.format("Open declaration (%s)", Arrays.stream(KeymapUtil.getActiveKeymapShortcuts("GotoDeclaration").getShortcuts())
                                .limit(2)
                                .map(KeymapUtil::getShortcutText)
                                .collect(Collectors.joining(", "))
                        );
                        holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                                .range(getTextElement(element).orElse(null))
                                .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                                .tooltip(tooltip)
                                .create();
                        result.add(newJumpToFile(action));
                    }
                    if (!action.isResolved()) {
                        result.add(newSuppressAction(action));
                        if (!action.isSuppressed()) {
                            result.add(action.isLocal() ? deleteInvalidAction(element) : newUnresolvedAction(element));
                        }
                    }
                }, () -> result.add(newUnresolvedAction(element))); //FIXME: is this a valid state?
        addAnnotation(holder, element, result);
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

    private static void withHandler(final AnnotationHolder holder, final PsiElement element) {
        getParentStepOrJob(element)
                .flatMap(step -> PsiElementHelper.getChild(step, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(GitHubAction::isResolved)
                .map(GitHubAction::freshInputs)
                .map(Map::keySet)
                .ifPresent(inputs -> PsiElementHelper.getChildren(element).forEach(kvInput -> {
                    if (!inputs.contains(kvInput.getKeyText())) {
                        addAnnotation(holder, kvInput, new SyntaxAnnotation(
                                "Delete invalid input [" + kvInput.getKeyText() + "]",
                                null,
                                deleteElementAction(kvInput.getTextRange())
                        ));
                    }
                }));
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
                                case FIELD_INPUTS ->
                                        ifEnoughItems(holder, element, parts, 2, 2, inputId -> isDefinedItem0(element, holder, inputId, listInputs(element).stream().map(SimpleElement::key).toList()));
                                case FIELD_SECRETS -> ifEnoughItems(holder, element, parts, 2, 2, secretId -> {
                                    // SECRETS ARE NOT ALLOWED IN IF STATEMENT
                                    if (parentIf.isPresent()) {
                                        final TextRange range = psiElement.getTextRange();
                                        final TextRange textRange = new TextRange(range.getStartOffset() + parts[0].startIndexOffset(), range.getStartOffset() + parts[parts.length - 1].endIndexOffset());
                                        new SyntaxAnnotation(
                                                "Remove [" + simpleElement.text() + "] - Secrets are not valid in `if` statements",
                                                null,
                                                deleteElementAction(textRange)
                                        ).createAnnotation(psiElement, textRange, holder);
                                    }
                                    final List<String> secrets = listSecrets(element).stream().map(SimpleElement::key).toList();
                                    if (!secrets.contains(secretId.text())) {
                                        final TextRange textRange = simpleTextRange(element, secretId);
                                        createAnnotation(element, textRange, holder, secrets.stream().map(secret -> new SyntaxAnnotation(
                                                "Replace [" + secretId.text() + "] with [" + secret + "] - if it is not provided at runtime",
                                                null,
                                                HighlightSeverity.WEAK_WARNING,
                                                ProblemHighlightType.WEAK_WARNING,
                                                replaceAction(textRange, secret),
                                                true
                                        )).toList());
                                    }
                                });
                                case FIELD_ENVS ->
                                        ifEnoughItems(holder, element, parts, 2, -1, envId -> isDefinedItem0(element, holder, envId, listEnvs(element).stream().map(SimpleElement::key).toList()));
                                case FIELD_GITHUB ->
                                        ifEnoughItems(holder, element, parts, 2, -1, envId -> isDefinedItem0(element, holder, envId, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get().keySet())));
                                case FIELD_RUNNER ->
                                        ifEnoughItems(holder, element, parts, 2, 2, runnerId -> isDefinedItem0(element, holder, runnerId, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_RUNNER).get().keySet())));
                                case FIELD_STEPS -> {
                                    if (parts.length > 2 && List.of(FIELD_CONCLUSION, FIELD_OUTCOME).contains(parts[2].text())) {
                                        ifEnoughStepItems(holder, element, parts, 3, VALID_STEP_FIELDS);
                                    } else {
                                        ifEnoughStepItems(holder, element, parts, 4, VALID_OUTPUT_FIELDS);
                                    }
                                }
                                case FIELD_JOBS -> ifEnoughItems(holder, element, parts, 4, 4, jobId -> {
                                    final List<YAMLKeyValue> jobs = listJobs(element);
                                    if (isDefinedItem0(element, holder, jobId, jobs.stream().map(YAMLKeyValue::getKeyText).toList()) && isField2Valid(element, holder, parts[2])) {
                                        final List<String> outputs = listJobOutputs(jobs.stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                                        isValidItem3(element, holder, parts[3], outputs);
                                    }
                                });
                                case FIELD_NEEDS -> ifEnoughItems(holder, element, parts, 4, 4, jobId -> {
                                    final List<String> jobIds = listJobNeeds(element);
                                    if (isDefinedItem0(element, holder, jobId, jobIds) && isField2Valid(element, holder, parts[2])) {
                                        final List<String> outputs = listJobOutputs(listAllJobs(element).stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                                        isValidItem3(element, holder, parts[3], outputs);
                                    }
                                });
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

    private static void ifEnoughStepItems(final AnnotationHolder holder, final PsiElement element, final SimpleElement[] parts, final int numberOfItems, final List<String> validFields) {
        ifEnoughItems(holder, element, parts, numberOfItems, numberOfItems, stepId -> {
            final List<YAMLSequenceItem> steps = listSteps(element);
            if (isDefinedItem0(element, holder, stepId, steps.stream().map(step -> getText(step, FIELD_ID).orElse(null)).filter(Objects::nonNull).toList()) && isField2Valid(element, holder, parts[2], validFields)) {
                final List<String> outputs = listStepOutputs(steps.stream().filter(step -> getText(step, FIELD_ID).filter(id -> id.equals(stepId.text())).isPresent()).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                if (parts.length > 3) {
                    isValidItem3(element, holder, parts[3], outputs);
                }
            }
        });
    }
}
