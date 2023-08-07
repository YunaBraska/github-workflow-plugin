package com.github.yunabraska.githubworkflow.highlights;

import com.github.yunabraska.githubworkflow.model.CompletionItem;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.WorkflowContext;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.github.yunabraska.githubworkflow.quickfixes.OpenSettingsIntentionAction;
import com.github.yunabraska.githubworkflow.quickfixes.OpenUrlIntentionAction;
import com.github.yunabraska.githubworkflow.quickfixes.ReplaceTextIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.TMP_DIR;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listEnvs;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listInputs;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listJobOutputs;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listJobs;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listSecrets;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listStepOutputs;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.listSteps;
import static com.github.yunabraska.githubworkflow.model.WorkflowContext.WORKFLOW_CONTEXT_MAP;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.getPath;
import static java.util.Optional.ofNullable;

public class HighlightAnnotator implements Annotator {

    public static final Pattern CARET_BRACKET_ITEM_PATTERN = Pattern.compile("\\b(\\w++(?:\\.\\w++)++)\\b");

    @Override
    public void annotate(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
        if (psiElement.getLanguage() instanceof YAMLLanguage) {
            ofNullable(WORKFLOW_CONTEXT_MAP.get(getPath(psiElement))).map(WorkflowContext::root).map(root -> toYamlElement(psiElement, root)).ifPresent(element -> {
                if (FIELD_USES.equals(element.key())) {
                    ofNullable(element.childTextNoQuotes()).map(GitHubAction::getGitHubAction).filter(GitHubAction::isAvailable).ifPresent(gitHubAction -> {
                        final List<IntentionAction> quickFixes = gitHubAction.isAction()
                                ? Arrays.asList(new OpenUrlIntentionAction(gitHubAction.marketplaceUrl(), "Open Marketplace [" + gitHubAction.marketplaceUrl() + "]"), new OpenUrlIntentionAction(gitHubAction.toUrl(), "Open file in Browser [" + gitHubAction.toUrl() + "]"))
                                : List.of(new OpenUrlIntentionAction(gitHubAction.toUrl(), "Open File [" + gitHubAction.toUrl() + "]"));
                        create(
                                holder,
                                HighlightSeverity.INFORMATION,
                                ProblemHighlightType.INFORMATION,
                                quickFixes,
                                psiElement.getTextRange(),
                                "Open Action in Browser [" + gitHubAction.slug() + "]"
                        );
                    });
                }
                //VALIDATE ACTION INPUTS
                if (element.key() != null && ofNullable(element.parent()).map(YamlElement::key).filter(FIELD_WITH::equals).isPresent()) {
                    element.findParentStep().map(YamlElement::uses).map(GitHubAction::getGitHubAction).map(GitHubAction::inputs).map(Map::keySet).ifPresent(inputs -> {
                        if (!inputs.contains(element.key())) {
                            create(
                                    holder,
                                    HighlightSeverity.ERROR,
                                    ProblemHighlightType.GENERIC_ERROR,
                                    List.of(new ReplaceTextIntentionAction(psiElement.getTextRange(), element.key(), true)),
                                    psiElement.getTextRange(),
                                    "Invalid [" + element.key() + "] - (Cache folder: " + TMP_DIR + ")"
                            );
                        }
                    });
                }
                if (element.findParent(FIELD_USES).isPresent() && ofNullable(ACTION_CACHE.get(element.textOrChildText())).filter(GitHubAction::isAvailable).isEmpty()) {
                    create(
                            holder,
                            HighlightSeverity.WEAK_WARNING,
                            ProblemHighlightType.WEAK_WARNING,
                            List.of(new OpenSettingsIntentionAction()),
                            element.node().getTextRange(),
                            "Unresolved [" + element.textOrChildTextNoQuotes() + "]."
                    );
                } else if (element.parent() != null && (FIELD_RUN.equals(element.parent().key())
                        || "if".equals(element.parent().key())
                        || "name".equals(element.parent().key())
                        || ("value".equals(element.parent().key()) && element.findParentOutput().isPresent())
                        || (element.parent() != null && element.parent().parent() != null && FIELD_WITH.equals(element.parent().parent().key()))
                        || (element.parent() != null && element.parent().parent() != null && FIELD_ENVS.equals(element.parent().parent().key()))
                        || (element.parent() != null && element.parent().parent() != null && FIELD_OUTPUTS.equals(element.parent().parent().key()))
                )) {
                    //TODO: Find solution for undetected items with only one '.' e.g. [inputs.]
                    //  MAYBE: regex needs to have '${{ }}', only 'if' content is different
                    processBracketItems(psiElement, holder, element);
                } else if (FIELD_NEEDS.equals(element.key())) {
                    element.findParentJob().ifPresent(job -> {
                        final List<String> jobs = element.context().jobs().values().stream().filter(j -> j.startIndexAbs() < job.startIndexAbs()).map(YamlElement::key).toList();
                        element.children().forEach(jobChild -> {
                            final String jobId = jobChild.textOrChildTextNoQuotes().trim();
                            final TextRange range = newRange(psiElement, jobChild.startIndexAbs(), jobChild.startIndexAbs() + jobId.length());
                            if (!jobs.contains(jobId)) {
                                //INVALID JOB_ID
                                create(
                                        holder,
                                        HighlightSeverity.ERROR,
                                        ProblemHighlightType.GENERIC_ERROR,
                                        jobs.stream().map(need -> new ReplaceTextIntentionAction(range, need, false)).map(ia -> (IntentionAction) ia).toList(),
                                        range,
                                        "Invalid [" + jobId + "] - needs to be a valid jobId from previous jobs"
                                );
                            } else {
                                ofNullable(job.node()).map(PsiElement::getText).ifPresent(jobText -> {
                                    //UNUSED JOB_ID
                                    if (!jobText.contains(FIELD_NEEDS + "." + jobId + ".")) {
                                        create(
                                                holder,
                                                HighlightSeverity.WEAK_WARNING,
                                                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                List.of(new ReplaceTextIntentionAction(range, jobId, true)),
                                                range,
                                                "Unused [" + jobId + "]"
                                        );
                                    }
                                });
                            }
                        });
                    });
                } else if (FIELD_OUTPUTS.equals(element.key())) {
                    //CHECK FOR UNUSED JOB OUTPUTS
                    element.findParentJob().map(YamlElement::key).ifPresent(jobId -> {
                        final List<String> usedOutputs = element.context().outputs().values().stream()
                                .filter(output -> output.findParentOn().isPresent())
                                .map(output -> output.child("value").orElse(null))
                                .filter(Objects::nonNull)
                                .map(YamlElement::textOrChildText)
                                .flatMap(value -> {
                                    final List<String[]> result = new ArrayList<>();
                                    final Matcher matcher = CARET_BRACKET_ITEM_PATTERN.matcher(value);
                                    while (matcher.find()) {
                                        result.add(matcher.group().split("\\."));
                                    }
                                    return result.stream();
                                })
                                .filter(parts -> parts.length == 4)
                                .filter(parts -> FIELD_JOBS.equals(parts[0]))
                                .filter(parts -> jobId.equals(parts[1]))
                                .filter(parts -> FIELD_OUTPUTS.equals(parts[2]))
                                .map(parts -> parts[3])
                                .toList();
                        element.children().stream().filter(output -> output.key() != null).filter(output -> !usedOutputs.contains(output.key())).forEach(unusedOutput -> {
                            final TextRange range = newRange(psiElement, unusedOutput.startIndexAbs(), unusedOutput.children().stream().mapToInt(YamlElement::endIndexAbs).max().orElseGet(unusedOutput::endIndexAbs));
                            create(
                                    holder,
                                    HighlightSeverity.WEAK_WARNING,
                                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                    List.of(new ReplaceTextIntentionAction(range, unusedOutput.key(), true)),
                                    range,
                                    "Unused [" + unusedOutput.key() + "]"
                            );
                        });
                    });
                }
            });
        }
    }

    private static void processBracketItems(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final YamlElement element) {
        final Matcher matcher = CARET_BRACKET_ITEM_PATTERN.matcher(psiElement.getText());
        while (matcher.find()) {
            final String[] parts = matcher.group().split("\\.");
            final String scope = parts[0];
            switch (scope) {
                case FIELD_INPUTS ->
                        ifEnoughItems(holder, psiElement, parts, 2, 2, inputId -> isDefinedItem0(psiElement, holder, matcher, inputId, listInputs(element).stream().map(CompletionItem::key).toList()));
                case FIELD_SECRETS -> ifEnoughItems(holder, psiElement, parts, 2, 2, secretId -> {
                    final List<String> secrets = listSecrets(element).stream().map(CompletionItem::key).toList();
                    if (!secrets.contains(secretId)) {
                        final TextRange textRange = simpleTextRange(psiElement, matcher, secretId);
                        create(
                                holder,
                                HighlightSeverity.WEAK_WARNING,
                                ProblemHighlightType.WEAK_WARNING,
                                secrets.stream().map(output -> new ReplaceTextIntentionAction(textRange, output, false)).map(ia -> (IntentionAction) ia).toList(),
                                textRange,
                                "Undefined [" + secretId + "] - it might be provided at runtime"
                        );
                    }
                });
                case FIELD_ENVS ->
                        ifEnoughItems(holder, psiElement, parts, 2, -1, envId -> isDefinedItem0(psiElement, holder, matcher, envId, listEnvs(element, element.startIndexAbs()).stream().map(CompletionItem::key).toList()));
                case FIELD_GITHUB ->
                        ifEnoughItems(holder, psiElement, parts, 2, -1, envId -> isDefinedItem0(psiElement, holder, matcher, envId, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get().keySet())));
                case FIELD_RUNNER ->
                        ifEnoughItems(holder, psiElement, parts, 2, 2, runnerId -> isDefinedItem0(psiElement, holder, matcher, runnerId, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_RUNNER).get().keySet())));
                case FIELD_STEPS -> ifEnoughItems(holder, psiElement, parts, 4, 4, stepId -> {
                    final List<String> steps = listSteps(element).stream().map(CompletionItem::key).toList();
                    if (isDefinedItem0(psiElement, holder, matcher, stepId, steps) && (!isField2Valid(psiElement, holder, matcher, parts[2]))) {
                        final List<String> outputs = listStepOutputs(element, element.startIndexAbs(), stepId).stream().map(CompletionItem::key).toList();
                        isValidItem3(psiElement, holder, matcher, parts[3], outputs);

                    }
                });
                case FIELD_JOBS ->
                    // TODO: CHECK OUTPUTS FOR JOBS && NEEDS && STEPS e.g. [ if (!FIELD_OUTPUTS.equals(parts[2])) ]
                        ifEnoughItems(holder, psiElement, parts, 4, 4, jobId -> {
                            final List<String> jobs = listJobs(element).stream().map(CompletionItem::key).toList();
                            //noinspection DuplicatedCode
                            if (isDefinedItem0(psiElement, holder, matcher, jobId, jobs) && (!isField2Valid(psiElement, holder, matcher, parts[2]))) {
                                final List<String> outputs = listJobOutputs(element, jobId).stream().map(CompletionItem::key).toList();
                                isValidItem3(psiElement, holder, matcher, parts[3], outputs);
                            }
                        });
                case FIELD_NEEDS ->
                        ifEnoughItems(holder, psiElement, parts, 4, 4, jobId -> element.findParentJob().flatMap(job -> job.child(FIELD_NEEDS)).ifPresent(needElement -> {
                            final Set<String> needs = needElement.needItems();
                            //noinspection DuplicatedCode
                            if (isDefinedItem0(psiElement, holder, matcher, jobId, needs) && (!isField2Valid(psiElement, holder, matcher, parts[2]))) {
                                final List<String> outputs = listJobOutputs(element, jobId).stream().map(CompletionItem::key).toList();
                                isValidItem3(psiElement, holder, matcher, parts[3], outputs);
                            }
                        }));
                default -> {
                    // ignored
                }
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isField2Valid(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final Matcher matcher, final String itemId) {
        if (!FIELD_OUTPUTS.equals(itemId)) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId);
            create(
                    holder,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    List.of(new ReplaceTextIntentionAction(textRange, FIELD_OUTPUTS, false)),
                    textRange,
                    "Invalid [" + itemId + "]"
            );
            return false;
        }
        return true;
    }

    private static void isValidItem3(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final Matcher matcher, final String itemId, final List<String> outputs) {
        if (!outputs.contains(itemId)) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId);
            create(
                    holder,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    outputs.stream().map(output -> new ReplaceTextIntentionAction(textRange, output, false)).map(ia -> (IntentionAction) ia).toList(),
                    textRange,
                    "Undefined [" + itemId + "]"
            );
        }
    }

    private static boolean isDefinedItem0(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final Matcher matcher, final String itemId, final Collection<String> items) {
        if (!items.contains(itemId)) {
            final TextRange textRange = simpleTextRange(psiElement, matcher, itemId);
            create(
                    holder,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    items.stream().map(output -> new ReplaceTextIntentionAction(textRange, output, false)).map(ia -> (IntentionAction) ia).toList(),
                    textRange,
                    "Undefined [" + itemId + "]"
            );
            return false;
        }
        return true;
    }

    private static YamlElement toYamlElement(final PsiElement psiElement, final YamlElement root) {
        return root.allElements().filter(e -> psiElement.equals(e.node())).findFirst().orElse(null);
    }

    @Nullable
    private static TextRange simpleTextRange(@NotNull final PsiElement psiElement, final Matcher matcher, final String itemId) {
        final int start = psiElement.getTextRange().getStartOffset() + psiElement.getText().indexOf(itemId, matcher.start(0));
        return newRange(psiElement, start, start + itemId.length());
    }

    private static TextRange newRange(final PsiElement psiElement, final int start, final int end) {
        final int newStart = Math.max(psiElement.getTextRange().getStartOffset(), start);
        final int newEnd = Math.min(psiElement.getTextRange().getEndOffset(), end);
        return newStart >= newEnd ? null : new TextRange(newStart, newEnd);
    }

    private static void ifEnoughItems(
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
            final TextRange textRange = newRange(psiElement, startOffset, startOffset + unfinishedStatement.length());
            create(
                    holder,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    null,
                    textRange,
                    "Incomplete statement [" + unfinishedStatement + "]"
            );
        } else if (max != -1 && parts.length > max) {
            final String fullStatement = String.join(".", parts);
            final String longPart = "." + String.join(".", (Arrays.copyOfRange(parts, max, parts.length)));
            final int statementStartIndex = psiElement.getText().indexOf(fullStatement);
            final int startOffset = psiElement.getTextRange().getStartOffset() + statementStartIndex + fullStatement.lastIndexOf(longPart);
            final TextRange textRange = newRange(psiElement, startOffset, startOffset + longPart.length());
            create(
                    holder,
                    HighlightSeverity.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    List.of(new ReplaceTextIntentionAction(textRange, longPart, true)),
                    textRange,
                    "Not valid here [" + longPart + "]"
            );
        } else {
            then.accept(parts[1]);
        }
    }

    @SuppressWarnings({"DataFlowIssue", "ResultOfMethodCallIgnored"})
    public static void create(final AnnotationHolder holder, final HighlightSeverity level, final ProblemHighlightType type, final Collection<IntentionAction> quickFixes, final TextRange range, final String message) {
        if (range != null) {
            final AnnotationBuilder annotation = holder.newAnnotation(level, message);
            final AnnotationBuilder silentAnnotation = holder.newSilentAnnotation(level);
            ofNullable(range).ifPresent(annotation::range);
            ofNullable(type).ifPresent(annotation::highlightType);
            ofNullable(message).ifPresent(annotation::tooltip);
            ofNullable(quickFixes).ifPresent(q -> q.forEach(annotation::withFix));

            ofNullable(range).ifPresent(silentAnnotation::range);
            ofNullable(quickFixes).ifPresent(q -> q.forEach(silentAnnotation::withFix));

            annotation.create();
            silentAnnotation.create();
        }
    }
}
