package com.github.yunabraska.githubworkflow.helper;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.QuickFixExecution;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.services.GitHubActionCache;
import com.intellij.codeInspection.ProblemHighlightType;
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
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_CONCLUSION;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTCOME;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.removeQuotes;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.JUMP_TO_IMPLEMENTATION;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SETTINGS;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;
import static java.util.Optional.ofNullable;

public class HighlightAnnotatorHelper {

    public static final List<String> VALID_OUTPUT_FIELDS = List.of(FIELD_OUTPUTS);
    public static final List<String> VALID_STEP_FIELDS = List.of(FIELD_OUTPUTS, FIELD_CONCLUSION, FIELD_OUTCOME);

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
            final SimpleElement[] parts,
            final int min,
            final int max,
            final Consumer<SimpleElement> then
    ) {
        if (parts.length < min || parts.length < 2) {
            final TextRange range = psiElement.getTextRange();
            new SyntaxAnnotation(
                    "Incomplete statement [" + Arrays.stream(parts).map(SimpleElement::text).collect(Collectors.joining(".")) + "]",
                    null,
                    null
            ).createAnnotation(psiElement, new TextRange(range.getStartOffset() + parts[0].startIndexOffset(), range.getStartOffset() + parts[parts.length - 1].endIndexOffset()), holder);
        } else if (max != -1 && parts.length > max) {
            final TextRange range = psiElement.getTextRange();
            final SimpleElement[] tooLongPart = Arrays.copyOfRange(parts, max, parts.length);
            final TextRange textRange = new TextRange(range.getStartOffset() + tooLongPart[0].startIndexOffset(), range.getStartOffset() + tooLongPart[tooLongPart.length - 1].endIndexOffset());
            new SyntaxAnnotation(
                    "Remove invalid suffix [" + Arrays.stream(tooLongPart).map(SimpleElement::text).collect(Collectors.joining(".")) + "]",
                    null,
                    deleteElementAction(textRange)
            ).createAnnotation(psiElement, textRange, holder);
        } else {
            then.accept(parts[1]);
        }
    }

    public static boolean isDefinedItem0(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, @NotNull final SimpleElement itemId, final Collection<String> items) {
        if (isEmpty(items, itemId, psiElement, holder)) {
            return false;
        } else if (!items.contains(itemId.text())) {
            final TextRange textRange = simpleTextRange(psiElement, itemId);
            createAnnotation(psiElement, textRange, holder, items.stream().map(item -> new SyntaxAnnotation(
                    "Replace with [" + item + "]",
                    null,
                    replaceAction(textRange, item)
            )).toList());
            return false;
        }
        return true;
    }

    public static boolean isField2Valid(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final SimpleElement itemId) {
        return isField2Valid(psiElement, holder, itemId, VALID_OUTPUT_FIELDS);
    }

    public static boolean isField2Valid(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final SimpleElement itemId, final List<String> validFields) {
        if (!validFields.contains(itemId.text())) {
            final TextRange textRange = simpleTextRange(psiElement, itemId);
            new SyntaxAnnotation(
                    "Remove invalid [" + itemId + "]",
                    null,
                    deleteElementAction(textRange)
            ).createAnnotation(psiElement, textRange, holder);
            return false;
        }
        return true;
    }

    public static void isValidItem3(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder, final SimpleElement itemId, final List<String> outputs) {
        if (!isEmpty(outputs, itemId, psiElement, holder) && itemId != null && !outputs.contains(itemId.text())) {
            final TextRange textRange = simpleTextRange(psiElement, itemId);
            createAnnotation(psiElement, textRange, holder, outputs.stream().filter(PsiElementHelper::hasText).map(item -> new SyntaxAnnotation(
                    "Replace with [" + item + "]",
                    null,
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

    public static SyntaxAnnotation deleteInvalidAction(final YAMLKeyValue element) {
        final TextRange textRange = ofNullable(element.getValue()).map(PsiElement::getTextRange).orElseGet(element::getTextRange);
        return new SyntaxAnnotation(
                "Remove invalid [" + element.getValueText() + "]",
                null,
                HighlightSeverity.WEAK_WARNING,
                ProblemHighlightType.WEAK_WARNING,
                deleteElementAction(textRange)
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
                f -> jumpToFile(action, f.project()),
                false
        );
    }

    public static void jumpToFile(final GitHubAction action, final Project project) {
        ofNullable(project)
                .map(p -> action)
                .flatMap(a -> a.getLocalPath(project))
                .map(path -> LocalFileSystem.getInstance().findFileByPath(path))
                .map(target -> PsiManager.getInstance(project).findFile(target))
                .ifPresent(psiFile -> PsiNavigationSupport.getInstance().createNavigatable(project, psiFile.getVirtualFile(), 0).navigate(true));
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

    public static <T> T getFirstChild(final List<T> children) {
        return children != null && !children.isEmpty() ? children.get(0) : null;
    }

    public static TextRange simpleTextRange(@NotNull final PsiElement psiElement, @NotNull final SimpleElement item) {
        final TextRange textRange = psiElement.getTextRange();
        final int startOffset = textRange.getStartOffset();
        return new TextRange(
                Math.max(startOffset + item.startIndexOffset(), startOffset),
                Math.min(startOffset + item.endIndexOffset(), textRange.getEndOffset())
        );
    }

    private static boolean isEmpty(final Collection<String> items, final SimpleElement itemId, @NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
        if (itemId != null && items.isEmpty()) {
            final TextRange textRange = simpleTextRange(psiElement, itemId);
            createAnnotation(psiElement, textRange, holder, List.of(new SyntaxAnnotation(
                    "Delete invalid [" + itemId.text() + "]",
                    null,
                    deleteElementAction(textRange)
            )));
            return true;
        }
        return false;
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
