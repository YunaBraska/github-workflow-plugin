package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.LocalActionReferenceResolver;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.services.GitHubActionCache;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.*;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.*;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.*;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.*;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.triggerSyntaxHighlightingForActiveFiles;
import static com.github.yunabraska.githubworkflow.services.ReferenceContributor.ACTION_KEY;
import static java.util.Optional.ofNullable;

public class Action {

    // ########## SYNTAX HIGHLIGHTING ##########
    public static void highLightAction(final AnnotationHolder holder, final YAMLKeyValue element) {
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
        return getAction(stepItem).map(action -> newSuppressOutput(action, part.text())).map(List::of).orElseGet(Collections::emptyList);
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

    // ########## REFERENCE RESOLVER ##########
    public static Optional<PsiReference[]> referenceGithubAction(final PsiElement psiElement) {
        return getParent(psiElement, FIELD_USES)
            .map(GitHubActionCache::getAction)
            .filter(GitHubAction::isResolved)
            .filter(action -> !action.isSuppressed())
            .map(action -> {
                    psiElement.putUserData(ACTION_KEY, action);
                    return action.isLocal() ? new PsiReference[]{new LocalActionReferenceResolver(psiElement)} : new WebReference[]{new WebReference(psiElement, action.githubUrl())};
                }
            );
    }

    private static void highlightLocalActions(final AnnotationHolder holder, final YAMLKeyValue element, final GitHubAction action, final List<SyntaxAnnotation> result) {
        if (action.isResolved() && action.isLocal()) {
            final String tooltip = goToDeclarationString();
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

    // ########## CODE COMPLETION ##########
    public static List<SimpleElement> listActionsOutputs(final PsiElement psiElement) {
        return getAction(psiElement)
            .map(GitHubAction::freshOutputs)
            .map(map -> completionItemsOf(map, ICON_OUTPUT))
            .orElseGet(Collections::emptyList);
    }

    // ########## COMMONS ##########
    private static Optional<GitHubAction> getAction(final PsiElement psiElement) {
        return ofNullable(psiElement)
            .flatMap(element -> getChild(element, FIELD_USES))
            .map(GitHubActionCache::getAction);
    }


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
