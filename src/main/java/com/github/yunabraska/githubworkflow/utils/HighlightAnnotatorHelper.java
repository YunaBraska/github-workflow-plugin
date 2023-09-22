package com.github.yunabraska.githubworkflow.utils;

import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.services.GitHubActionCache;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.QuickFixExecution;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.triggerSyntaxHighlightingForActiveFiles;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.JUMP_TO_IMPLEMENTATION;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SETTINGS;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_OFF;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;
import static com.github.yunabraska.githubworkflow.utils.PsiElementHelper.removeQuotes;
import static java.util.Optional.ofNullable;

public class HighlightAnnotatorHelper {

    private HighlightAnnotatorHelper() {
        // static helper class
    }

    public static void addAnnotation(final AnnotationHolder holder, final PsiElement element, final SyntaxAnnotation result) {
        addAnnotation(holder, element, List.of(result));
    }

    public static void addAnnotation(final AnnotationHolder holder, final PsiElement element, final List<SyntaxAnnotation> result) {
        if (holder != null) {
            result.forEach(annotation -> annotation.createAnnotation(element, holder));
        }
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

    public static TextRange simpleTextRange(@NotNull final PsiElement psiElement, final Matcher matcher, final String itemId) {
        return simpleTextRange(psiElement, matcher, itemId, false);
    }

    public static TextRange simpleTextRange(@NotNull final PsiElement psiElement, final Matcher matcher, final String itemId, final boolean lastIndex) {
        final int start = psiElement.getTextRange().getStartOffset() + (lastIndex ? psiElement.getText().lastIndexOf(itemId, matcher.end(0)) : psiElement.getText().indexOf(itemId, matcher.start(0)));
        return new TextRange(start, start + itemId.length());
    }

    public static <T> T getFirstChild(final List<T> children) {
        return children != null && !children.isEmpty() ? children.get(0) : null;
    }

    private static void resolveAction(final YAMLKeyValue element) {
        ApplicationManager.getApplication().invokeLater(() -> ofNullable(element)
                .filter(PsiElement::isValid)
                .flatMap(psiElement -> PsiElementHelper.getParent(psiElement, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> !action.isResolved())
                .map(List::of)
                .ifPresent(GitHubActionCache::resolveActionsAsync), ModalityState.defaultModalityState());
    }
}
