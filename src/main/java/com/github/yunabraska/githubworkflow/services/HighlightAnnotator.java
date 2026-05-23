package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.model.IconRenderer;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteElementAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.getFirstChild;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.replaceAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.simpleTextRange;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getAllElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.goToDeclarationString;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStep;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getText;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElement;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.parseEnvVariables;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.parseOutputVariables;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.toYAMLKeyValue;
import static com.github.yunabraska.githubworkflow.logic.Action.highLightAction;
import static com.github.yunabraska.githubworkflow.logic.Action.highlightActionInput;
import static com.github.yunabraska.githubworkflow.logic.Envs.highLightEnvs;
import static com.github.yunabraska.githubworkflow.logic.GitHub.highLightGitea;
import static com.github.yunabraska.githubworkflow.logic.GitHub.highLightGitHub;
import static com.github.yunabraska.githubworkflow.logic.Inputs.highLightInputs;
import static com.github.yunabraska.githubworkflow.logic.JobContext.highlightJob;
import static com.github.yunabraska.githubworkflow.logic.Jobs.highLightJobs;
import static com.github.yunabraska.githubworkflow.logic.Matrix.highlightMatrix;
import static com.github.yunabraska.githubworkflow.logic.Needs.highlightNeeds;
import static com.github.yunabraska.githubworkflow.logic.Runner.highlightRunner;
import static com.github.yunabraska.githubworkflow.logic.Secrets.highLightSecrets;
import static com.github.yunabraska.githubworkflow.logic.Steps.highlightSteps;
import static com.github.yunabraska.githubworkflow.logic.Strategy.highlightStrategy;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.intellij.lang.annotation.HighlightSeverity.INFORMATION;
import static java.util.Optional.ofNullable;

public class HighlightAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
        //it's needed to handle single elements instead of bulk wise from parent. Parent elements are doesn't update so often.
        if (psiElement.isValid()) {
            processPsiElement(holder, psiElement);
            variableElementHandler(holder, psiElement);
            highlightVariableReferences(holder, psiElement);
            highlightDeclarations(holder, psiElement);
            highlightRunOutputs(holder, psiElement);
            highlightRunnerVariables(holder, psiElement);
            highlightScalarLiterals(holder, psiElement);
            validateWorkflowSyntax(holder, psiElement);
            // HIGHLIGHT ACTION INPUTS
            highlightActionInput(holder, psiElement);
            highlightNeeds(holder, psiElement);
        }
    }

    public static void processPsiElement(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            switch (element.getKeyText()) {
                case FIELD_USES -> highLightAction(holder, element);
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
                        parseEnvVariables(element).stream().map(variable -> withIcon(variable, ICON_ENV)).toList(),
                        parseOutputVariables(element).stream().map(variable -> withIcon(variable, ICON_TEXT_VARIABLE)).toList()
                ).flatMap(Collection::stream).collect(Collectors.groupingBy(SimpleElement::startIndexOffset)).forEach((integer, elements) -> ofNullable(getFirstChild(elements)).ifPresent(lineElement -> holder
                        .newSilentAnnotation(INFORMATION)
                        .range(lineElement.range())
                        .textAttributes(WorkflowTextAttributes.DECLARATION)
                        .gutterIconRenderer(new IconRenderer(null, element, lineElement.icon()))
                        .create()
                )));
    }

    private static SimpleElement withIcon(final SimpleElement element, final NodeIcon icon) {
        return new SimpleElement(element.key(), element.text(), element.range(), icon);
    }

    private static void highlightRunnerVariables(final AnnotationHolder holder, final PsiElement psiElement) {
        Optional.of(psiElement)
                .filter(LeafPsiElement.class::isInstance)
                .map(LeafPsiElement.class::cast)
                .filter(element -> getParent(element, FIELD_RUN).isPresent())
                .ifPresent(element -> DEFAULT_VALUE_MAP.get(FIELD_ENVS).get().keySet().forEach(name -> highlightWord(holder, element, name, WorkflowTextAttributes.RUNNER_VARIABLE)));
    }

    private static void highlightWord(
            final AnnotationHolder holder,
            final PsiElement element,
            final String word,
            final com.intellij.openapi.editor.colors.TextAttributesKey attributes
    ) {
        final String text = element.getText();
        int index = text.indexOf(word);
        while (index >= 0) {
            final int end = index + word.length();
            final boolean before = index == 0 || !isIdentifierChar(text.charAt(index - 1));
            final boolean after = end >= text.length() || !isIdentifierChar(text.charAt(end));
            if (before && after) {
                holder.newSilentAnnotation(INFORMATION)
                        .range(new TextRange(element.getTextRange().getStartOffset() + index, element.getTextRange().getStartOffset() + end))
                        .textAttributes(attributes)
                        .create();
            }
            index = text.indexOf(word, end);
        }
    }

    private static void highlightScalarLiterals(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement)
                .flatMap(PsiElementHelper::getTextElement)
                .filter(text -> text.getText().matches("true|false|-?\\d+(?:\\.\\d+)?"))
                .ifPresent(text -> holder.newSilentAnnotation(INFORMATION)
                        .range(text)
                        .textAttributes(WorkflowTextAttributes.SCALAR_LITERAL)
                        .create());
    }

    private static void validateWorkflowSyntax(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement)
                .filter(HighlightAnnotator::shouldValidateWorkflowSyntax)
                .ifPresent(element -> validateWorkflowKeyValue(holder, element));
    }

    private static boolean shouldValidateWorkflowSyntax(final YAMLKeyValue element) {
        return GitHubWorkflowHelper.getWorkflowFile(element)
                .filter(path -> GitHubWorkflowHelper.isWorkflowFile(path) || isUnitTestWorkflowFile(element))
                .isPresent();
    }

    private static boolean isUnitTestWorkflowFile(final YAMLKeyValue element) {
        return ApplicationManager.getApplication().isUnitTestMode()
                && PsiElementHelper.getChild(element.getContainingFile(), "runs").isEmpty();
    }

    private static void validateWorkflowKeyValue(final AnnotationHolder holder, final YAMLKeyValue element) {
        final String key = element.getKeyText();
        final List<String> path = yamlPath(element);
        if (path.isEmpty()) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.topLevelKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_ON)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.eventKeys(), "inspection.workflow.syntax.unknownEventKey");
            return;
        }
        if (pathMatches(path, FIELD_ON, "workflow_dispatch")) {
            validateKnownKey(holder, element, mapOf(FIELD_INPUTS), "inspection.workflow.syntax.unknownTriggerKey");
            return;
        }
        if (pathMatches(path, FIELD_ON, "workflow_call")) {
            validateKnownKey(holder, element, mapOf(FIELD_INPUTS, FIELD_OUTPUTS, FIELD_SECRETS), "inspection.workflow.syntax.unknownTriggerKey");
            return;
        }
        if (isChildOf(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                || isChildOf(path, FIELD_ON, "workflow_call", FIELD_INPUTS)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.workflowInputPropertyKeys(), "inspection.workflow.syntax.unknownTriggerKey");
            validateWorkflowInputPropertyValue(holder, element, path);
            return;
        }
        if (isChildOf(path, FIELD_ON, "workflow_call", FIELD_OUTPUTS)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.workflowOutputPropertyKeys(), "inspection.workflow.syntax.unknownTriggerKey");
            return;
        }
        if (isChildOf(path, FIELD_ON, "workflow_call", FIELD_SECRETS)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.workflowSecretPropertyKeys(), "inspection.workflow.syntax.unknownTriggerKey");
            if ("required".equals(key)) {
                validateKnownValue(holder, element, WorkflowSyntaxSchema.booleanValues(), "inspection.workflow.syntax.unknownTriggerValue");
            }
            return;
        }
        if (pathMatches(path, FIELD_ON, "*")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.eventFilterKeysFor(path.get(path.size() - 1)), "inspection.workflow.syntax.unknownTriggerFilter");
            if ("types".equals(key)) {
                validateKnownValue(holder, element, WorkflowSyntaxSchema.eventActivityTypesFor(path.get(1)), "inspection.workflow.syntax.unknownTriggerValue");
            }
            return;
        }
        if (pathEndsWith(path, "permissions")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.permissionScopes(), "inspection.workflow.syntax.unknownPermission");
            validateKnownValue(holder, element, WorkflowSyntaxSchema.permissionValuesFor(element.getKeyText()), "inspection.workflow.syntax.unknownPermissionValue");
            return;
        }
        if (pathMatches(path, "defaults", FIELD_RUN) || pathMatches(path, FIELD_JOBS, "*", "defaults", FIELD_RUN)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.defaultsRunKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, "concurrency") || pathMatches(path, FIELD_JOBS, "*", "concurrency")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.concurrencyKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", FIELD_STRATEGY)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.strategyKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", "environment")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.environmentKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", "container")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.containerKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", "container", "credentials")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.credentialsKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", FIELD_SERVICES, "*")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.serviceKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", FIELD_SERVICES, "*", "credentials")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.credentialsKeys(), "inspection.workflow.syntax.unknownTopLevelKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*")) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.jobKeys(), "inspection.workflow.syntax.unknownJobKey");
            return;
        }
        if (pathMatches(path, FIELD_JOBS, "*", FIELD_STEPS)) {
            validateKnownKey(holder, element, WorkflowSyntaxSchema.stepKeys(), "inspection.workflow.syntax.unknownStepKey");
        }
    }

    private static Map<String, String> mapOf(final String... keys) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final String key : keys) {
            result.put(key, key);
        }
        return result;
    }

    private static void validateWorkflowInputPropertyValue(
            final AnnotationHolder holder,
            final YAMLKeyValue element,
            final List<String> path
    ) {
        if ("type".equals(element.getKeyText())) {
            final Map<String, String> allowedTypes = "workflow_call".equals(path.get(1))
                    ? WorkflowSyntaxSchema.reusableWorkflowInputTypes()
                    : WorkflowSyntaxSchema.workflowInputTypes();
            validateKnownValue(holder, element, allowedTypes, "inspection.workflow.syntax.unknownTriggerValue");
        }
        if ("required".equals(element.getKeyText())) {
            validateKnownValue(holder, element, WorkflowSyntaxSchema.booleanValues(), "inspection.workflow.syntax.unknownTriggerValue");
        }
    }

    private static void validateKnownKey(
            final AnnotationHolder holder,
            final YAMLKeyValue element,
            final Map<String, String> allowed,
            final String messageKey
    ) {
        if (allowed.containsKey(element.getKeyText()) || element.getKeyText().isBlank()) {
            return;
        }
        final TextRange range = Optional.ofNullable(element.getKey())
                .map(PsiElement::getTextRange)
                .orElseGet(element::getTextRange);
        final List<SyntaxAnnotation> fixes = new ArrayList<>();
        fixes.add(new SyntaxAnnotation(
                GitHubWorkflowBundle.message(messageKey, element.getKeyText()),
                null,
                HighlightSeverity.WEAK_WARNING,
                ProblemHighlightType.WEAK_WARNING,
                null
        ));
        allowed.keySet().stream()
                .map(candidate -> new SyntaxAnnotation(
                        GitHubWorkflowBundle.message("inspection.replace.with", candidate),
                        RELOAD,
                        HighlightSeverity.WEAK_WARNING,
                        ProblemHighlightType.WEAK_WARNING,
                        replaceAction(range, candidate)
                ))
                .forEach(fixes::add);
        SyntaxAnnotation.createAnnotation(
                element,
                range,
                holder,
                fixes
        );
    }

    private static void validateKnownValue(
            final AnnotationHolder holder,
            final YAMLKeyValue element,
            final Map<String, String> allowed,
            final String messageKey
    ) {
        final String value = PsiElementHelper.getText(element).orElse("");
        if (allowed.isEmpty()
                || value.isBlank()
                || value.startsWith("${{")
                || !value.matches("[A-Za-z0-9_-]+")
                || allowed.containsKey(value)) {
            return;
        }
        PsiElementHelper.getTextElement(element).ifPresent(valueElement -> {
            final TextRange range = valueElement.getTextRange();
            final List<SyntaxAnnotation> fixes = new ArrayList<>();
            fixes.add(new SyntaxAnnotation(
                    GitHubWorkflowBundle.message(messageKey, value),
                    null,
                    HighlightSeverity.WEAK_WARNING,
                    ProblemHighlightType.WEAK_WARNING,
                    null
            ));
            allowed.keySet().stream()
                    .map(candidate -> new SyntaxAnnotation(
                            GitHubWorkflowBundle.message("inspection.replace.with", candidate),
                            RELOAD,
                            HighlightSeverity.WEAK_WARNING,
                            ProblemHighlightType.WEAK_WARNING,
                            replaceAction(range, candidate)
                    ))
                    .forEach(fixes::add);
            SyntaxAnnotation.createAnnotation(
                    element,
                    range,
                    holder,
                    fixes
            );
        });
    }

    private static List<String> yamlPath(final YAMLKeyValue element) {
        final List<String> result = new ArrayList<>();
        PsiElement current = element.getParent();
        while (current != null && current != element.getContainingFile()) {
            if (current instanceof YAMLKeyValue keyValue) {
                result.add(0, keyValue.getKeyText());
            }
            current = current.getParent();
        }
        return result;
    }

    private static boolean isChildOf(final List<String> path, final String... expectedParent) {
        if (path.size() != expectedParent.length + 1) {
            return false;
        }
        for (int index = 0; index < expectedParent.length; index++) {
            if (!expectedParent[index].equals(path.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean pathMatches(final List<String> path, final String... pattern) {
        if (path.size() != pattern.length) {
            return false;
        }
        for (int index = 0; index < pattern.length; index++) {
            if (!"*".equals(pattern[index]) && !pattern[index].equals(path.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean pathEndsWith(final List<String> path, final String expected) {
        return !path.isEmpty() && expected.equals(path.get(path.size() - 1));
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
                final String reusableOutputReference = FIELD_JOBS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey;
                final String needsOutputReference = FIELD_NEEDS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey;
                return workflowOutputs.stream().noneMatch(value -> containsOutputReference(value, reusableOutputReference))
                        && !containsOutputReference(workflowText, needsOutputReference);
            }).forEach(output -> new SyntaxAnnotation(
                    GitHubWorkflowBundle.message("inspection.output.unused", output.getKeyText()),
                    SUPPRESS_ON,
                    HighlightSeverity.WEAK_WARNING,
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    deleteElementAction(output.getTextRange()),
                    true
            ).createAnnotation(output, output.getTextRange(), holder));

        });
    }

    private static boolean containsOutputReference(final String text, final String reference) {
        int index = ofNullable(text).orElse("").indexOf(reference);
        while (index >= 0) {
            final int end = index + reference.length();
            if (end >= text.length() || !isIdentifierChar(text.charAt(end))) {
                return true;
            }
            index = text.indexOf(reference, end);
        }
        return false;
    }

    @NotNull
    public static Predicate<PsiElement> isElementWithVariables(final YAMLKeyValue parentIf) {
        return element -> ofNullable(parentIf)
                .or(() -> getParent(element, FIELD_RUN))
                .or(() -> getParent(element, FIELD_ID))
                .or(() -> getParent(element, "name"))
                .or(() -> getParent(element, "run-name"))
                .or(() -> getParent(element, "runs-on"))
                .or(() -> getParent(element, "concurrency"))
                .or(() -> getParent(element, "group").filter(group -> getParent(group, "concurrency").isPresent()))
                .or(() -> getParent(element, "default").filter(defaultValue -> getParent(defaultValue, FIELD_INPUTS).isPresent()))
                .or(() -> getParent(element, "credentials"))
                .or(() -> getParent(element, "environment"))
                .or(() -> getParent(element, "fail-fast").filter(failFast -> getParent(failFast, FIELD_STRATEGY).isPresent()))
                .or(() -> getParent(element, "max-parallel").filter(maxParallel -> getParent(maxParallel, FIELD_STRATEGY).isPresent()))
                .or(() -> getParent(element, "shell").filter(shell -> getParent(shell, "defaults").isPresent()))
                .or(() -> getParent(element, "container").filter(container -> getParent(container, "jobs").isPresent()))
                .or(() -> getParent(element, "url").filter(url -> getParent(url, "environment").isPresent()))
                .or(() -> getParent(element, "timeout-minutes"))
                .or(() -> getParent(element, "continue-on-error"))
                .or(() -> getParent(element, "working-directory"))
                .or(() -> getParent(element, "image").filter(image -> getParent(image, "container").isPresent() || getParent(image, "services").isPresent()))
                .or(() -> getParent(element, "value").isPresent() ? getParent(element, FIELD_OUTPUTS) : Optional.empty())
                .or(() -> getParent(element, FIELD_WITH))
                .or(() -> getParent(element, FIELD_ENVS))
                .or(() -> getParent(element, FIELD_OUTPUTS))
                .isPresent();
    }

    @NotNull
    public static List<SimpleElement> toSimpleElements(final PsiElement element) {
        if (getParent(element, FIELD_RUN).isPresent()) {
            return toSimpleElementsInExpressions(element);
        }
        final List<SimpleElement> result = new ArrayList<>();
        final String text = element.getText();
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            final String line = text.substring(lineStart, lineEnd);
            if (PsiElementHelper.hasText(line) && !line.trim().startsWith("#")) {
                final int currentLineStart = lineStart;
                findDottedExpressions(line).stream()
                        .map(expression -> new SimpleElement(
                                expression.text(),
                                new TextRange(
                                        currentLineStart + expression.range().getStartOffset(),
                                        currentLineStart + expression.range().getEndOffset()
                                )
                        ))
                        .forEach(result::add);
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return result;
    }

    @NotNull
    private static List<SimpleElement> toSimpleElementsInExpressions(final PsiElement element) {
        final List<SimpleElement> result = new ArrayList<>();
        final String text = element.getText();
        int index = 0;
        while (index < text.length()) {
            final int expressionStart = text.indexOf("${{", index);
            if (expressionStart < 0) {
                break;
            }
            final int bodyStart = expressionStart + 3;
            final int expressionEnd = text.indexOf("}}", bodyStart);
            if (expressionEnd < 0) {
                break;
            }
            final String body = text.substring(bodyStart, expressionEnd);
            findDottedExpressions(body).stream()
                    .map(expression -> new SimpleElement(
                            expression.text(),
                            new TextRange(
                                    bodyStart + expression.range().getStartOffset(),
                                    bodyStart + expression.range().getEndOffset()
                            )
                    ))
                    .forEach(result::add);
            index = expressionEnd + 2;
        }
        return result;
    }

    @NotNull
    public static SimpleElement[] splitToElements(final SimpleElement simpleElement) {
        final List<SimpleElement> result = new ArrayList<>();
        final AtomicInteger index = new AtomicInteger(0);
        while (index.get() < simpleElement.text().length()) {
            if (isIdentifierChar(simpleElement.text().charAt(index.get()))) {
                result.add(readIdentifier(simpleElement, index));
            } else {
                index.incrementAndGet();
            }
        }
        return result.toArray(SimpleElement[]::new);
    }

    public static List<SimpleElement> findDottedExpressions(final String text) {
        final List<SimpleElement> elements = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (!isContextStart(text, index)) {
                index++;
                continue;
            }
            final int start = index;
            boolean hasSeparator = false;
            index = readIdentifierEnd(text, index);
            while (index < text.length()) {
                final char current = text.charAt(index);
                if (current == '.') {
                    hasSeparator = true;
                    index++;
                    index = readIdentifierEnd(text, index);
                } else if (current == '[') {
                    final int closingBracket = findClosingBracket(text, index);
                    if (closingBracket < 0) {
                        break;
                    }
                    hasSeparator = true;
                    index = closingBracket + 1;
                } else {
                    break;
                }
            }
            if (hasSeparator && start < index) {
                elements.add(new SimpleElement(text.substring(start, index), new TextRange(start, index)));
            }
        }
        return elements;
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
                                case FIELD_SECRETS ->
                                        highLightSecrets(holder, psiElement, element, simpleElement, parts, parentIf.orElse(null));
                                case FIELD_ENVS -> highLightEnvs(holder, element, parts);
                                case FIELD_GITHUB -> highLightGitHub(holder, element, parts);
                                case FIELD_GITEA -> highLightGitea(holder, element, parts);
                                case FIELD_JOB -> highlightJob(holder, element, parts);
                                case FIELD_RUNNER -> highlightRunner(holder, element, parts);
                                case FIELD_MATRIX -> highlightMatrix(holder, element, parts);
                                case FIELD_STRATEGY -> highlightStrategy(holder, element, parts);
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

    private static void highlightVariableReferences(final AnnotationHolder holder, final PsiElement psiElement) {
        Optional.of(psiElement)
                .filter(PsiElementHelper::isTextElement)
                .ifPresent(element -> {
                    toSimpleElements(element).stream()
                            .flatMap(source -> Stream.of(splitToElements(source)))
                            .forEach(segment -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .range(simpleTextRange(element, segment))
                                    .textAttributes(WorkflowTextAttributes.VARIABLE_REFERENCE)
                                    .create());
                    ExpressionReferenceTargets.resolve(element).forEach(target -> {
                    final String tooltip = goToDeclarationString();
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(simpleTextRange(element, target.segment()))
                            .textAttributes(WorkflowTextAttributes.VARIABLE_REFERENCE)
                            .create();
                    holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                            .range(simpleTextRange(element, target.segment()))
                            .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                            .tooltip(tooltip)
                            .create();
                    });
                });
    }

    private static void highlightDeclarations(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            highlightJobDeclaration(holder, element);
            highlightStepDeclaration(holder, element);
        });
    }

    private static void highlightJobDeclaration(final AnnotationHolder holder, final YAMLKeyValue element) {
        getParent(element, FIELD_JOBS)
                .filter(jobs -> isDirectChildOf(element, jobs))
                .flatMap(job -> ofNullable(element.getKey()))
                .ifPresent(key -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(key)
                        .textAttributes(WorkflowTextAttributes.DECLARATION)
                        .create());
    }

    private static boolean isDirectChildOf(final YAMLKeyValue child, final YAMLKeyValue parent) {
        PsiElement current = child.getParent();
        while (current != null && current != parent) {
            if (current instanceof YAMLKeyValue) {
                return false;
            }
            current = current.getParent();
        }
        return current == parent;
    }

    private static void highlightStepDeclaration(final AnnotationHolder holder, final YAMLKeyValue element) {
        if (FIELD_ID.equals(element.getKeyText()) && getParentStep(element).isPresent()) {
            getTextElement(element).ifPresent(text -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(text)
                    .textAttributes(WorkflowTextAttributes.DECLARATION)
                    .create());
        }
    }

    private static SimpleElement readIdentifier(final SimpleElement simpleElement, final AtomicInteger index) {
        final int start = index.get();
        index.set(readIdentifierEnd(simpleElement.text(), start));
        return new SimpleElement(
                simpleElement.text().substring(start, index.get()),
                new TextRange(simpleElement.range().getStartOffset() + start, simpleElement.range().getStartOffset() + index.get())
        );
    }

    private static int readIdentifierEnd(final String text, final int start) {
        int index = start;
        while (index < text.length() && isIdentifierChar(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int findClosingBracket(final String text, final int start) {
        int index = start + 1;
        while (index < text.length()) {
            if (text.charAt(index) == ']') {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static boolean isContextStart(final String text, final int start) {
        return List.of(FIELD_INPUTS, FIELD_SECRETS, FIELD_ENVS, FIELD_GITHUB, FIELD_GITEA, FIELD_JOB, FIELD_RUNNER, FIELD_MATRIX, FIELD_STRATEGY, FIELD_STEPS, FIELD_JOBS, FIELD_NEEDS, FIELD_VARS)
                .stream()
                .anyMatch(context -> text.startsWith(context, start) && hasContextSeparator(text, start + context.length()));
    }

    private static boolean hasContextSeparator(final String text, final int index) {
        return index < text.length() && (text.charAt(index) == '.' || text.charAt(index) == '[');
    }

    private static boolean isIdentifierChar(final char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }


}
