package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.config.GitHubActionCache;
import com.github.yunabraska.githubworkflow.highlights.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.quickfixes.QuickFixExecution;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.config.GitHubActionCache.triggerSyntaxHighlightingForActiveFiles;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.*;
import static com.github.yunabraska.githubworkflow.highlights.SyntaxAnnotation.createAnnotation;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

public class PsiElementHelper {

    private PsiElementHelper() {
        // static class
    }

    public static List<SimpleElement> listJobOutputs(final YAMLKeyValue job) {
        //JOB OUTPUTS
        final List<SimpleElement> jobOutputs = ofNullable(job)
                .flatMap(j -> getChildWithKey(j, FIELD_OUTPUTS)
                        .map(PsiElementHelper::getKvChildren)
                        .map(children -> children.stream().map(child -> getText(child).map(value -> completionItemOf(child.getKeyText(), value, ICON_OUTPUT)).orElse(null)).filter(Objects::nonNull).toList())
                ).orElseGet(Collections::emptyList);

        //JOB USES OUTPUTS
        return Stream.concat(jobOutputs.stream(), getUsesOutputs(job).stream()).toList();
    }

    public static List<SimpleElement> listStepOutputs(final YAMLSequenceItem step) {
        //STEP RUN OUTPUTS
        final List<SimpleElement> stepOutputs = ofNullable(step).flatMap(s -> getChildWithKey(s, FIELD_RUN)
                .map(PsiElementHelper::parseOutputVariables)
                .map(outputs -> outputs.stream().map(output -> completionItemOf(output.key(), output.text(), ICON_TEXT_VARIABLE)).toList())
        ).orElseGet(Collections::emptyList);

        //STEP USES OUTPUTS
        return Stream.concat(stepOutputs.stream(), getUsesOutputs(step).stream()).toList();
    }

    public static List<SimpleElement> getUsesOutputs(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .flatMap(element -> getChildWithKey(element, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .map(GitHubAction::freshOutputs)
                .map(map -> completionItemsOf(map, ICON_OUTPUT))
                .orElseGet(Collections::emptyList);
    }

    public static List<YAMLKeyValue> listJobs(final PsiElement psiElement) {
        //JobList is only valid in Workflow outputs
        return getKvParent(psiElement, FIELD_OUTPUTS)
                .flatMap(outputs -> getKvParent(psiElement, FIELD_ON))
                .map(PsiElementHelper::listAllJobs)
                .orElseGet(Collections::emptyList);
    }

    public static List<YAMLKeyValue> listAllJobs(final PsiElement psiElement) {
        return ofNullable(psiElement).map(element -> getAllElements(element.getContainingFile(), FIELD_JOBS).stream().flatMap(jobs -> getChildren(jobs, YAMLKeyValue.class).stream()).toList()).orElseGet(Collections::emptyList);
    }

    public static List<String> listJobNeeds(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .flatMap(PsiElementHelper::getParentJob)
                .flatMap(job -> getChildWithKey(job, FIELD_NEEDS))
                .map(needs -> getTextElements(needs)
                        .stream().map(PsiElement::getText)
                        .map(PsiElementHelper::removeQuotes)
                        .filter(PsiElementHelper::hasText)
                        .toList()
                ).orElseGet(Collections::emptyList);
    }

    public static List<YAMLSequenceItem> listSteps(final PsiElement psiElement) {
        //StepList position == step?    list previous steps in current job
        //StepList position == outputs? list all      steps in current job
        return getParentJob(psiElement).map(job -> {
            final YAMLSequenceItem currentStep = getParentStep(psiElement).orElse(null);
            final boolean isOutput = getKvParent(psiElement, FIELD_OUTPUTS).isPresent();
            return getChildSteps(job).stream().takeWhile(step -> isOutput || step != currentStep).toList();
        }).orElseGet(Collections::emptyList);
    }

    public static Optional<YAMLKeyValue> getParentJob(final PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_JOBS, YAMLKeyValue.class);
    }

    public static boolean isField2Valid(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final Matcher matcher, final String itemId) {
        if (!FIELD_OUTPUTS.equals(itemId)) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId);
            new SyntaxAnnotation(
                    "Remove invalid [" + itemId + "]",
                    null,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    deleteElementAction(textRange)
            ).createAnnotation(psiElement, textRange, holder);
            return false;
        }
        return true;
    }

    public static List<SimpleElement> listEnvs(final PsiElement psiElement) {
        //CURRENT STEP TEXT ENVS [jobs.job_id.steps.step_id.run:key=value]
        final TextRange currentRange = psiElement.getTextRange();
        final List<SimpleElement> result = new ArrayList<>(completionItemsOf(
                getAllElements(psiElement.getContainingFile(), FIELD_RUN).stream()
                        // only FIELD_RUN from previous FIELD_STEP
                        .filter(keyValue -> getParentStep(keyValue).map(PsiElement::getTextRange).map(TextRange::getStartOffset).orElse(currentRange.getEndOffset()) < currentRange.getStartOffset())
                        .map(PsiElementHelper::parseEnvVariables)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toMap(SimpleElement::key, SimpleElement::textNoQuotes, (existing, replacement) -> existing))
                , ICON_TEXT_VARIABLE
        ));

        //CURRENT STEP ENVS [step.env.env_key:env_value]
        getParentStep(psiElement)
                .flatMap(step -> getChildWithKey(step, FIELD_ENVS))
                .map(PsiElementHelper::getKvChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_STEP))
                .ifPresent(result::addAll);

        //CURRENT JOB ENVS [jobs.job_id.envs.env_id:env_value]
        getParentJob(psiElement)
                .flatMap(job -> getChildWithKey(job, FIELD_ENVS))
                .map(PsiElementHelper::getKvChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_JOB))
                .ifPresent(result::addAll);


        //WORKFLOW ENVS
        getChildWithKey(psiElement.getContainingFile(), FIELD_ENVS)
                .map(PsiElementHelper::getKvChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_ROOT))
                .ifPresent(result::addAll);

        //DEFAULT ENVS
        result.addAll(completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get(), ICON_ENV));

        return result;
    }

    public static Function<List<YAMLKeyValue>, Map<String, String>> toMapWithKeyAndText() {
        return elements -> elements.stream()
                .filter(keyValue -> getTextElement(keyValue).isPresent())
                .collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue).orElse(""), (existing, replacement) -> existing));
    }

    public static List<SimpleElement> listSecrets(final PsiElement psiElement) {
        //WORKFLOW SECRETS
        return getChildWithKey(psiElement.getContainingFile(), FIELD_ON)
                .map(on -> getAllElements(on, FIELD_SECRETS))
                .map(secrets -> secrets.stream().flatMap(secret -> getKvChildren(secret).stream()).collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue, "description").orElse(""), (existing, replacement) -> existing)))
                .map(map -> completionItemsOf(map, ICON_SECRET_WORKFLOW))
                .orElseGet(ArrayList::new);
    }


    public static List<SimpleElement> listInputs(final PsiElement psiElement) {
        final Map<String, String> result = new HashMap<>();
        getAllElements(psiElement.getContainingFile(), FIELD_INPUTS).stream()
                .map(PsiElementHelper::getKvChildren)
                .flatMap(Collection::stream)
                .forEach(input -> {
                    final String description = getText(psiElement, "description").orElse("");
                    final String previousDescription = result.computeIfAbsent(input.getKeyText(), value -> description);
                    if (previousDescription.length() < description.length()) {
                        result.put(input.getKeyText(), description);
                    }
                });
        return completionItemsOf(result, ICON_INPUT);
    }


    public static void ifEnoughItems(
            final AnnotationHolder holder,
            final PsiElement psiElement,
            final String[] parts,
            final int min,
            final int max,
            final Consumer<String> then
    ) {
        if (parts.length < min || parts.length < 2) {
            final String unfinishedStatement = String.join(".", parts);
            final int startOffset = psiElement.getTextRange().getStartOffset() + psiElement.getText().indexOf(unfinishedStatement);
            final TextRange textRange = new TextRange(startOffset, startOffset + unfinishedStatement.length());
            new SyntaxAnnotation(
                    "Incomplete statement [" + unfinishedStatement + "]",
                    null,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    null
            ).createAnnotation(psiElement, textRange, holder);
        } else if (max != -1 && parts.length > max) {
            final String fullStatement = String.join(".", parts);
            final String longPart = "." + String.join(".", (Arrays.copyOfRange(parts, max, parts.length)));
            final int statementStartIndex = psiElement.getText().indexOf(fullStatement);
            final int startOffset = psiElement.getTextRange().getStartOffset() + statementStartIndex + fullStatement.lastIndexOf(longPart);
            final TextRange textRange = new TextRange(startOffset, startOffset + longPart.length());
            new SyntaxAnnotation(
                    "Remove invalid suffix [" + longPart + "]",
                    null,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    deleteElementAction(textRange)
            ).createAnnotation(psiElement, textRange, holder);
        } else {
            then.accept(parts[1]);
        }
    }

    public static boolean isDefinedItem0(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final Matcher matcher, final String itemId, final Collection<String> items) {
        if (!items.contains(itemId)) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId);
            createAnnotation(psiElement, textRange, holder, items.stream().map(item -> new SyntaxAnnotation(
                    "Replace with [" + item + "]",
                    null,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    replaceAction(textRange, item)
            )).toList());
            return false;
        }
        return true;
    }

    public static void isValidItem3(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final Matcher matcher, final String itemId, final List<String> outputs) {
        if (itemId != null && outputs.isEmpty()) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId, true);
            createAnnotation(psiElement, textRange, holder, List.of(new SyntaxAnnotation(
                    "Delete invalid [" + itemId + "]",
                    null,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    deleteElementAction(textRange)
            )));
        } else if (itemId != null && !outputs.contains(itemId)) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId, true);
            createAnnotation(psiElement, textRange, holder, outputs.stream().map(item -> new SyntaxAnnotation(
                    "Replace with [" + item + "]",
                    null,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    replaceAction(textRange, item)
            )).toList());
        }
    }

    public static TextRange simpleTextRange(@NotNull final PsiElement psiElement, final Matcher matcher, final String itemId) {
        return simpleTextRange(psiElement, matcher, itemId, false);
    }

    public static TextRange simpleTextRange(@NotNull final PsiElement psiElement, final Matcher matcher, final String itemId, final boolean lastIndex) {
        final int start = psiElement.getTextRange().getStartOffset() + (lastIndex? psiElement.getText().lastIndexOf(itemId, matcher.end(0)) : psiElement.getText().indexOf(itemId, matcher.start(0)));
        return new TextRange(start, start + itemId.length());
    }

    public static List<SimpleElement> parseEnvVariables(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, GitHubWorkflowUtils::toGithubEnvs);
    }

    public static List<SimpleElement> parseOutputVariables(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, GitHubWorkflowUtils::toGithubOutputs);
    }

    public static List<SimpleElement> parseVariables(final PsiElement psiElement, final Function<String, Map<String, String>> method) {
        final List<SimpleElement> lineElements = getLineElements(psiElement);
        return lineElements.stream().flatMap(line -> method.apply(line.text()).entrySet().stream().map(env -> new SimpleElement(env.getKey(), env.getValue(), line.range()))).toList();
    }

    public static List<SimpleElement> getLineElements(final PsiElement psiElement) {
        return getChild(psiElement, YAMLBlockScalarImpl.class).map(psi -> {
            final TextRange parentRange = psi.getTextRange();
            return psi.getContentRanges().stream().map(textRange -> new SimpleElement(
                    null,
                    removeQuotes(psi.getText().substring(textRange.getStartOffset(), textRange.getEndOffset())),
                    new TextRange(parentRange.getStartOffset() + textRange.getStartOffset(), parentRange.getStartOffset() + textRange.getEndOffset())
            )).filter(element -> element.startIndexOffset() < element.endIndexOffset()).filter(element -> hasText(element.text())).toList();
        }).orElseGet(Collections::emptyList);
    }

    public static <T> T getFirstChild(final T[] children) {
        return children != null && children.length > 0 ? children[0] : null;
    }

    public static <T> T getFirstChild(final List<T> children) {
        return children != null && !children.isEmpty() ? children.get(0) : null;
    }


    public static <T> Optional<T> getChild(final PsiElement psiElement, final Class<T> clazz) {
        return getFirstElement(getChildren(psiElement, clazz));
    }

    public static <T> Optional<T> getFirstElement(final List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }


    @NotNull
    public static Consumer<QuickFixExecution> deleteElementAction(final TextRange textRange) {
        return replaceAction(textRange, "");
    }

    @NotNull
    public static Consumer<QuickFixExecution> replaceAction(final TextRange textRange, final String newValue) {
        return fix -> {
            final PsiElement psiElement = fix.file().findElementAt(fix.editor().getCaretModel().getOffset());
            if (psiElement != null) {
                final Document document = PsiDocumentManager.getInstance(fix.project()).getDocument(psiElement.getContainingFile());
                if (document != null) {
                    WriteCommandAction.runWriteCommandAction(fix.project(), () -> document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), newValue));
                }
            }
        };
    }


    public static List<YAMLKeyValue> getAllJobs(final PsiElement psiElement) {
        final List<YAMLKeyValue> result = new ArrayList<>();
        getAllJobs(result, ofNullable(psiElement).map(PsiElement::getContainingFile).orElse(null));
        return unmodifiableList(result);
    }

    public static void getAllJobs(final List<YAMLKeyValue> result, final PsiElement element) {
        if (result == null || element == null) {
            return;
        }
        if (element instanceof final YAMLKeyValue keyValue && FIELD_JOBS.equals(keyValue.getKeyText())) {
            result.addAll(getKvChildren(keyValue));
        } else {
            Arrays.stream(element.getChildren()).forEach(child -> getAllJobs(result, child));
        }
    }

    public static void addAnnotation(final AnnotationHolder holder, final PsiElement element, final SyntaxAnnotation result) {
        addAnnotation(holder, element, List.of(result));
    }

    public static void addAnnotation(final AnnotationHolder holder, final PsiElement element, final List<SyntaxAnnotation> result) {
        if (holder != null) {
            result.forEach(annotation -> annotation.createAnnotation(element, holder));
        }
    }

    public static List<YAMLKeyValue> getKvChildren(final PsiElement psiElement) {
        return getChildren(psiElement, YAMLKeyValue.class);
    }

    public static Optional<String> getText(final PsiElement psiElement) {
        return getTextElements(psiElement).stream().map(PsiElement::getText).map(PsiElementHelper::removeQuotes).filter(PsiElementHelper::hasText).findFirst();
    }

    public static Optional<String> getText(final PsiElement psiElement, final String key) {
        return getChildWithKey(psiElement, key).flatMap(PsiElementHelper::getText);
    }


    public static Optional<PsiElement> getTextElement(final PsiElement psiElement) {
        final List<PsiElement> textValues = getTextElements(psiElement);
        return textValues.isEmpty() ? Optional.empty() : Optional.of(textValues.get(0));
    }

    public static List<PsiElement> getTextElements(final PsiElement psiElement) {
        final ArrayList<PsiElement> result = new ArrayList<>();
        getTextElements(result, psiElement);
        return unmodifiableList(result);
    }

    public static void getTextElements(final List<PsiElement> result, final PsiElement psiElement) {
        ofNullable(psiElement).ifPresent(element -> {
            if (element instanceof YAMLPlainTextImpl || element instanceof YAMLQuotedText) {
                if (hasText(element.getText())) {
                    result.add(element);
                }
            } else {
                Arrays.stream(element.getChildren()).forEach(child -> getTextElements(result, child));
            }
        });
    }

    public static List<YAMLKeyValue> getAllElements(final PsiElement psiElement, final String keyName) {
        return psiElement == null || keyName == null ? Collections.emptyList() : unmodifiableList(getAllElements(new ArrayList<>(), psiElement, keyName));
    }

    public static List<YAMLKeyValue> getAllElements(final List<YAMLKeyValue> result, final PsiElement psiElement, final String keyName) {
        if (psiElement instanceof final YAMLKeyValue keyValue && keyName.equals(keyValue.getKeyText())) {
            result.add(keyValue);
        }

        for (final PsiElement child : psiElement.getChildren()) {
            getAllElements(result, child, keyName);
        }

        return result;
    }

    public static Optional<PsiElement> getParentStepOrJob(final PsiElement psiElement) {
        return getParentStep(psiElement).map(PsiElement.class::cast).or(() -> getParentJob(psiElement));
    }

    public static Optional<YAMLSequenceItem> getParentStep(final PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_STEPS, YAMLSequenceItem.class);
    }

    //TOTO: getChildWithKey(psiElement, FIELD_STEPS)???
    public static List<YAMLSequenceItem> getChildSteps(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .map(element -> element instanceof final YAMLKeyValue keyValue && FIELD_STEPS.equals(keyValue.getKeyText()) ? List.of(keyValue) : getAllElements(element, FIELD_STEPS))
                .map(yamlKeyValues -> yamlKeyValues.stream().flatMap(steps -> getChildren(steps, YAMLSequenceItem.class).stream().filter(Objects::nonNull)).toList())
                .orElseGet(Collections::emptyList);
    }

    public static <T> List<T> getChildren(final PsiElement psiElement, final Class<T> clazz) {
        return ofNullable(psiElement)
                .map(PsiElement::getChildren)
                .map(psiElements -> Arrays.stream(psiElements).filter(clazz::isInstance).map(clazz::cast).toList())
                .filter(children -> !children.isEmpty())
                .or(() -> ofNullable(psiElement)
                        .map(PsiElement::getChildren)
                        .flatMap(psiElements -> Arrays.stream(psiElements).map(child -> getChildren(child, clazz)).filter(children -> !children.isEmpty()).findFirst())
                )
                .orElseGet(Collections::emptyList);
    }

    public static Optional<YAMLKeyValue> getChildWithKey(final PsiElement psiElement, final String childKey) {
        return psiElement == null || childKey == null ? Optional.empty() : Optional.of(psiElement)
                .map(PsiElementHelper::getKvChildren)
                .flatMap(children -> children.stream()
                        .filter(Objects::nonNull)
                        .filter(child -> childKey.equals(child.getKeyText()))
                        .findFirst()
                );
    }

    public static <T extends PsiElement> Optional<T> getElementUnderParent(final PsiElement psiElement, final String keyName, final Class<T> clazz) {
        return psiElement == null || keyName == null ? Optional.empty() : getKvParent(psiElement, yamlKeyValue -> keyName.equals(yamlKeyValue.getKeyText()))
                .flatMap(yamlKeyValue -> getClosestChild(psiElement, yamlKeyValue, clazz));
    }

    public static <T extends PsiElement> Optional<T> getClosestChild(final PsiElement from, final YAMLKeyValue to, final Class<T> clazz) {
        return listAllParents(from, to).stream()
                .filter(Objects::nonNull)
                .filter(parent -> !(parent instanceof YAMLBlockSequenceImpl))
                .filter(clazz::isInstance)
                .findFirst()
                .map(clazz::cast);
    }

    public static List<PsiElement> listAllParents(final PsiElement from, final PsiElement to) {
        final List<PsiElement> result = new ArrayList<>();
        listAllParents(result, from.getParent(), to);
        Collections.reverse(result);
        return result;
    }

    public static void listAllParents(final List<PsiElement> result, final PsiElement from, final PsiElement to) {
        if (from != null && from != to) {
            result.add(from);
            listAllParents(result, from.getParent(), to);
        }
    }

    public static Optional<YAMLKeyValue> getKvParent(final PsiElement psiElement, final String fieldKey) {
        return psiElement == null || fieldKey == null ? Optional.empty() : getKvParent(psiElement, parent -> fieldKey.equals(parent.getKeyText()));
    }

    public static Optional<YAMLKeyValue> getKvParent(final PsiElement psiElement, final Predicate<YAMLKeyValue> filter) {
        return psiElement == null || filter == null ? Optional.empty() : Optional.of(psiElement)
                .flatMap(PsiElementHelper::toYAMLKeyValue)
                .filter(filter)
                .or(() -> Optional.of(psiElement)
                        .map(PsiElement::getParent)
                        .flatMap(parent -> getKvParent(parent, filter))
                );
    }

    public static Optional<YAMLKeyValue> toYAMLKeyValue(final PsiElement psiElement) {
        if (psiElement instanceof final YAMLKeyValue element) {
            return Optional.of(element);
        }
        return Optional.empty();
    }

    @NotNull
    public static SyntaxAnnotation newReloadAction(final GitHubAction action) {
        return new SyntaxAnnotation(
                "Reload [" + action.name() + "]",
                RELOAD,
                HighlightSeverity.INFORMATION,
                ProblemHighlightType.INFORMATION,
                f -> GitHubActionCache.reloadActionAsync(f.project(), action.usesValue())
        );
    }

    @NotNull
    public static SyntaxAnnotation newUnresolvedAction(final YAMLKeyValue element) {
        return new SyntaxAnnotation(
                "Unresolved [" + removeQuotes(element.getValueText()) + "] - you may need to connect your GitHub",
                SETTINGS,
                HighlightSeverity.WEAK_WARNING,
                ProblemHighlightType.WEAK_WARNING,
                f -> {
                    ShowSettingsUtil.getInstance().showSettingsDialog(f.project(), "GitHub");
                    resolveAction(element);
                }
        );
    }

    @NotNull
    public static SyntaxAnnotation newSuppressAction(final GitHubAction action) {
        final boolean suppressed = action.isSuppressed();
        return new SyntaxAnnotation(
                "Toggle warnings [" + (suppressed ? "on" : "off") + "] for [" + action.name() + "]",
                suppressed ? SUPPRESS_OFF : null,
                HighlightSeverity.INFORMATION,
                suppressed ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION,
                f -> {
                    action.isSuppressed(!suppressed);
                    triggerSyntaxHighlightingForActiveFiles();
                }
        );
    }

    @NotNull
    public static SyntaxAnnotation newJumpToFile(final GitHubAction action) {
        //TODO: List Workflows connected to the action file
        return new SyntaxAnnotation(
                "Jump to file [" + action.name() + "]",
                JUMP_TO_IMPLEMENTATION,
                HighlightSeverity.INFORMATION,
                ProblemHighlightType.INFORMATION,
                f -> Optional.of(action)
                        .flatMap(a -> a.getLocalPath(f.project()))
                        .map(path -> LocalFileSystem.getInstance().findFileByPath(path))
                        .map(target -> PsiManager.getInstance(f.project()).findFile(target))
                        .ifPresent(psiFile -> {
                            // Navigate to PsiElement
                            PsiNavigationSupport.getInstance().createNavigatable(f.project(), psiFile.getVirtualFile(), 0).navigate(true);
                        })
        );
    }

    @NotNull
    public static SyntaxAnnotation newOpenInBrowserFix(final String text, final String url) {
        return new SyntaxAnnotation(
                text,
                null,
                HighlightSeverity.INFORMATION,
                ProblemHighlightType.INFORMATION,
                quickFixExecution -> BrowserUtil.browse(url)
        );
    }

    public static String getDescription(final PsiElement psiElement) {
        return psiElement == null ? "" : "r[" + getText(psiElement, "required").map(Boolean::parseBoolean).orElse(false) + "]"
                + getText(psiElement, "default").map(def -> " def[" + def + "]").orElse("")
                + getText(psiElement, "description").or(() -> getText(psiElement, "desc")).map(desc -> " " + desc).orElse("");
    }

    public static Project getProject(final PsiElement psiElement) {
        return psiElement != null && psiElement.isValid() ? psiElement.getProject() : null;
    }

    public static void readPsiElement(final Project project, final String fileName, final String fileContent, final Consumer<PsiFile> action) {
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                ofNullable(action).ifPresent(consumer -> consumer.accept(PsiFileFactory.getInstance(project).createFileFromText(fileName, YAMLFileType.YML, fileContent.replaceAll("\r?\\n|\\r", "\n"))));
            } catch (final Exception ignored) {
                // ignored
            }
        });
    }

    public static String removeQuotes(final String result) {
        return removeBrackets(result, '"', '\'');
    }

    public static String removeBrackets(final String text, final char... chars) {
        if (text != null && text.length() > 1) {
            for (final char c : chars) {
                if (text.charAt(0) == c && text.charAt(text.length() - 1) == (c == '[' ? ']' : validateRoundBracket(c))) {
                    return text.substring(1, text.length() - 1);
                }
            }
        }
        return text;
    }

    private static char validateRoundBracket(final char c) {
        return c == '(' ? ')' : c;
    }

    public static boolean hasText(final String str) {
        return (str != null && !str.isEmpty() && containsText(str));
    }

    private static boolean containsText(final CharSequence str) {
        final int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void resolveAction(final YAMLKeyValue element) {
        ApplicationManager.getApplication().invokeLater(() -> ofNullable(element)
                .filter(PsiElement::isValid)
                .flatMap(psiElement -> PsiElementHelper.getKvParent(psiElement, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> !action.isResolved())
                .map(List::of)
                .ifPresent(GitHubActionCache::resolveActionsAsync), ModalityState.defaultModalityState());
    }
}
