package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.IconRenderer;
import com.github.yunabraska.githubworkflow.model.LocalActionReferenceResolver;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.addAnnotation;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteElementAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteInvalidAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newJumpToFile;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newReloadAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newUnresolvedAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.replaceAction;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElement;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.goToDeclarationString;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.toYAMLKeyValue;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.EMPTY;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.IGNORED;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.JUMP_TO_IMPLEMENTATION;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_OFF;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.triggerSyntaxHighlightingForActiveFiles;
import static com.github.yunabraska.githubworkflow.entry.ReferenceContributor.ACTION_KEY;
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
                        newerMajorActionRef(element, action).ifPresent(result::add);
                    }
                    result.add(newSuppressAction(action));
                    highlightResolvedActionReference(holder, element, action, result);
                    if (element != null && !action.isResolved() && (!action.isSuppressed())) {
                        result.add(action.isLocal() ? deleteInvalidAction(element) : newUnresolvedAction(element));
                    }
                }, () -> ofNullable(element).ifPresent(value -> result.add(newUnresolvedAction(value))));
        addAnnotation(holder, element, result);
    }

    public static void highlightActionInput(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement)
                .filter(withItem -> getParent(withItem.getParent(), FIELD_WITH).isPresent())
                .ifPresent(withItem -> highlightCallableParameter(holder, withItem, FIELD_WITH));
        toYAMLKeyValue(psiElement)
                .filter(secretItem -> getParent(secretItem.getParent(), FIELD_SECRETS).filter(secrets -> getParent(secrets, FIELD_ON).isEmpty()).isPresent())
                .ifPresent(secretItem -> highlightCallableParameter(holder, secretItem, FIELD_SECRETS));
    }

    private static void highlightCallableParameter(final AnnotationHolder holder, final YAMLKeyValue item, final String parameterType) {
        getAction(getParentStepOrJob(item).orElse(null))
                .filter(GitHubAction::isResolved)
                .ifPresent(action -> {
                    final Set<String> validIds = FIELD_SECRETS.equals(parameterType) ? action.freshSecrets().keySet() : action.freshInputs().keySet();
                    final String id = item.getKeyText();
                    if (FIELD_WITH.equals(parameterType)) {
                        newSuppressInput(action, id).createAnnotation(item, ofNullable(item.getKey()).map(PsiElement::getTextRange).orElseGet(item::getTextRange), holder);
                    }
                    if (!validIds.contains(id)) {
                        final String label = FIELD_SECRETS.equals(parameterType)
                                ? GitHubWorkflowBundle.message("inspection.parameter.secret")
                                : GitHubWorkflowBundle.message("inspection.parameter.input");
                        addAnnotation(holder, item, new SyntaxAnnotation(
                                GitHubWorkflowBundle.message("inspection.action.delete.invalid", label, id),
                                SUPPRESS_ON,
                                deleteElementAction(item.getTextRange())
                        ));
                    }
                });
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

    private static void highlightResolvedActionReference(final AnnotationHolder holder, final YAMLKeyValue element, final GitHubAction action, final List<SyntaxAnnotation> result) {
        if (action.isResolved() && !action.isSuppressed()) {
            final String tooltip = goToDeclarationString();
            getTextElement(element).ifPresent(textElement -> {
                final AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                        .range(textElement)
                        .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                        .tooltip(tooltip);
                if (action.isLocal()) {
                    final SyntaxAnnotation jumpToFile = newJumpToFile(action);
                    result.add(jumpToFile);
                    annotation.gutterIconRenderer(new IconRenderer(jumpToFile, element, JUMP_TO_IMPLEMENTATION));
                }
                annotation.create();
            });
        }
    }

    private static Optional<SyntaxAnnotation> newerMajorActionRef(final YAMLKeyValue element, final GitHubAction action) {
        final String usesValue = action.usesValue();
        final int separator = usesValue.lastIndexOf('@');
        if (separator < 1 || separator == usesValue.length() - 1 || action.remoteRefs().isEmpty()) {
            return Optional.empty();
        }
        final String currentRef = usesValue.substring(separator + 1);
        final Optional<Integer> currentMajor = majorRef(currentRef);
        if (currentMajor.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> latestRef = action.remoteRefs().stream()
                .filter(ref -> majorRef(ref).map(major -> major > currentMajor.get()).orElse(false))
                .max((left, right) -> Integer.compare(majorRef(left).orElse(0), majorRef(right).orElse(0)));
        if (latestRef.isEmpty()) {
            return Optional.empty();
        }
        final String newUsesValue = usesValue.substring(0, separator + 1) + latestRef.get();
        final TextRange range = getTextElement(element).map(PsiElement::getTextRange).orElseGet(element::getTextRange);
        return Optional.of(new SyntaxAnnotation(
                GitHubWorkflowBundle.message("inspection.action.update.major", usesValue, newUsesValue),
                RELOAD,
                HighlightSeverity.WEAK_WARNING,
                ProblemHighlightType.WEAK_WARNING,
                replaceAction(range, newUsesValue)
        ));
    }

    private static Optional<Integer> majorRef(final String ref) {
        final String normalized = ofNullable(ref).orElse("").trim();
        final String digits = normalized.startsWith("v") || normalized.startsWith("V")
                ? normalized.substring(1)
                : normalized;
        return digits.matches("\\d+")
                ? Optional.of(Integer.parseInt(digits))
                : Optional.empty();
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
                suppressed ? SUPPRESS_OFF : SUPPRESS_ON,
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
                suppressed ? IGNORED : SUPPRESS_ON,
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
                suppressed ? IGNORED : SUPPRESS_ON,
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
        return GitHubWorkflowBundle.message("inspection.warning.toggle", GitHubWorkflowBundle.message(suppressed ? "inspection.warning.on" : "inspection.warning.off"), id);
    }

    private Action() {
        // static helper class
    }
}
