package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.services.GitHubActionCache;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.addAnnotation;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteElementAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteInvalidAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newJumpToFile;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newReloadAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newUnresolvedAction;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElement;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.toYAMLKeyValue;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.EMPTY;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.IGNORED;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_OFF;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.triggerSyntaxHighlightingForActiveFiles;
import static java.util.Optional.ofNullable;

public class Action {

    public static void highLightAction(final AnnotationHolder holder, final YAMLKeyValue element) {
        //TODO: suppress warnings for whole action
        //TODO: suppress warnings for single input / outputs action

        final List<SyntaxAnnotation> result = new ArrayList<>();
        ofNullable(element)
                .map(GitHubActionCache::getAction)
                .ifPresentOrElse(action -> {
                    if (action.isResolved() && !action.isLocal()) {
                        result.add(newReloadAction(action));
                    }
                    result.add(newSuppressAction(action));
                    highlightLocalActions(holder, element, action, result);
                    if (element != null && !action.isResolved() && (!action.isSuppressed())) {
                        result.add(action.isLocal() ? deleteInvalidAction(element) : newUnresolvedAction(element));
                    }
                }, () -> result.add(newUnresolvedAction(element))); //FIXME: is this a valid state?
        addAnnotation(holder, element, result);
    }

    private static void highlightLocalActions(final AnnotationHolder holder, final YAMLKeyValue element, final GitHubAction action, final List<SyntaxAnnotation> result) {
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

    public static void highlightActionInput(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement)
                .filter(withItem -> getParent(withItem.getParent(), FIELD_WITH).isPresent())
                .ifPresent(withItem -> getAction(getParentStepOrJob(withItem).orElse(null))
                        .filter(GitHubAction::isResolved)
                        .ifPresent(action -> {
                            final Set<String> inputs = action.freshInputs().keySet();
                            final String inputId = withItem.getKeyText();
                            newSuppressInput(action, inputId).createAnnotation(withItem, ofNullable(withItem.getKey()).map(PsiElement::getTextRange).orElseGet(withItem::getTextRange), holder);
                            if (!inputs.contains(inputId)) {
                                addAnnotation(holder, withItem, new SyntaxAnnotation(
                                        "Delete invalid input [" + inputId + "]",
                                        null,
                                        deleteElementAction(withItem.getTextRange())
                                ));
                            }

                        }));
    }

    public static List<SyntaxAnnotation> highlightActionOutputs(final YAMLSequenceItem stepItem, final SimpleElement part) {
        return getAction(stepItem).map(action -> {
//            final String outputId = part.text();
//            final boolean isSuppressed = action.ignoredInputs().contains(outputId);
//            final int startRange = element.getTextRange().getStartOffset();
//            final String message = toggleText(outputId, isSuppressed);
//            holder.newAnnotation(HighlightSeverity.INFORMATION, message)
//                    .tooltip(message)
//                    .range(new TextRange(startRange + part.range().getStartOffset(), startRange + part.range().getEndOffset()))
//                    .withFix(new SyntaxAnnotation(message, IGNORED, fix -> {
//                        action.suppressOutput(outputId, !isSuppressed);
//                        triggerSyntaxHighlightingForActiveFiles();
//                    }))
//                    .create();
//            newSuppressOutput(action, outputId).createAnnotation(element, new TextRange(startRange + part.range().getStartOffset(), startRange + part.range().getEndOffset()), holder);
                    return newSuppressOutput(action, part.text());
                })
                .map(List::of)
                .orElseGet(Collections::emptyList);
    }

    public static void highlightActionOutputs(final AnnotationHolder holder, final PsiElement psiElement, final YAMLSequenceItem stepItem, final SimpleElement part) {
        getAction(stepItem).ifPresent(action -> {
            final String outputId = part.text();
            final int startRange = psiElement.getTextRange().getStartOffset();
            if (!action.freshOutputs(false).containsKey(outputId)) {
                newSuppressOutput(action, outputId).createAnnotation(psiElement, new TextRange(startRange + part.range().getStartOffset(), startRange + part.range().getEndOffset()), holder);
            }
        });
    }

    public static List<SimpleElement> listActionsOutputs(final PsiElement psiElement) {
        return getAction(psiElement)
                .map(GitHubAction::freshOutputs)
                .map(map -> completionItemsOf(map, ICON_OUTPUT))
                .orElseGet(Collections::emptyList);
    }

    @NotNull
    private static Optional<GitHubAction> getAction(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .flatMap(element -> getChild(element, FIELD_USES))
                .map(GitHubActionCache::getAction);
    }


    @NotNull
    private static SyntaxAnnotation newSuppressAction(final GitHubAction action) {
        final boolean suppressed = action.isSuppressed();
        return new SyntaxAnnotation(
                toggleText(action.name(), suppressed),
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
    private static SyntaxAnnotation newSuppressInput(final GitHubAction action, final String id) {
        final boolean suppressed = action.ignoredInputs().contains(id);
        return new SyntaxAnnotation(
                toggleText(id, suppressed),
                suppressed ? IGNORED : EMPTY,
                HighlightSeverity.INFORMATION,
                suppressed ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION,
                f -> {
                    action.suppressInput(id, !suppressed);
                    triggerSyntaxHighlightingForActiveFiles();
                }
        );
    }

    @NotNull
    private static SyntaxAnnotation newSuppressOutput(final GitHubAction action, final String id) {
        final boolean suppressed = action.ignoredOutputs().contains(id);
        final HighlightSeverity level = action.freshOutputs().containsKey(id) ? HighlightSeverity.INFORMATION : HighlightSeverity.ERROR;
        return new SyntaxAnnotation(
                toggleText(id, suppressed),
                null,
                level,
                suppressed ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION,
                f -> {
                    action.suppressOutput(id, !suppressed);
                    triggerSyntaxHighlightingForActiveFiles();
                }
        );
    }

    @NotNull
    private static String toggleText(final String id, final boolean suppressed) {
        return "Toggle warnings [" + (suppressed ? "on" : "off") + "] for [" + id + "]";
    }

    private Action() {
        // static helper class
    }
}
