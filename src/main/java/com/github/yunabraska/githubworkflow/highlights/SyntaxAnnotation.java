package com.github.yunabraska.githubworkflow.highlights;

import com.github.yunabraska.githubworkflow.config.NodeIcon;
import com.github.yunabraska.githubworkflow.quickfixes.QuickFixExecution;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@SuppressWarnings("OptionalOfNullableMisuse")
public class SyntaxAnnotation implements IntentionAction {

    private final String text;
    private final NodeIcon icon;
    private final HighlightSeverity level;
    private final ProblemHighlightType type;
    private final Consumer<QuickFixExecution> execute;

    public SyntaxAnnotation(
            final String text,
            final NodeIcon icon,
            final HighlightSeverity level,
            final ProblemHighlightType type,
            final Consumer<QuickFixExecution> execute
    ) {
        this.text = text;
        this.icon = icon;
        this.level = level;
        this.type = type;
        this.execute = execute;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        ofNullable(execute).ifPresent(projectConsumer -> projectConsumer.accept(new QuickFixExecution(this, project, editor, file)));
    }

    public void createAnnotation(
            final PsiElement psiElement,
            final AnnotationHolder holder
    ) {
        createAnnotation(psiElement, psiElement.getTextRange(), holder);
    }

    public void createAnnotation(
            final PsiElement psiElement,
            final TextRange range,
            final AnnotationHolder holder
    ) {
        createAnnotation(psiElement, range, holder, List.of(this));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void createAnnotation(
            final PsiElement psiElement,
            final TextRange range,
            final AnnotationHolder holder,
            final List<SyntaxAnnotation> fixes
    ) {
        if (fixes != null && !fixes.isEmpty() && holder != null && psiElement != null && psiElement.isValid()) {
            fixes.stream().collect(Collectors.groupingBy(f -> f.level)).forEach((level, group) -> {
                final SyntaxAnnotation firstItem = group.get(0);
                final AnnotationBuilder annotation = holder.newAnnotation(level, firstItem.text);
                final AnnotationBuilder silentAnnotation = holder.newSilentAnnotation(level);
                ofNullable(range != null ? range : psiElement.getTextRange()).ifPresent(silentAnnotation::range);
                ofNullable(range != null ? range : psiElement.getTextRange()).ifPresent(annotation::range);
                ofNullable(firstItem.type).ifPresent(annotation::highlightType);
                ofNullable(firstItem.text).ifPresent(annotation::tooltip);
                ofNullable(firstItem.icon).map(i -> new IconRenderer(firstItem, psiElement, firstItem.icon)).ifPresent(annotation::gutterIconRenderer);
                group.forEach(fix -> {
                    ofNullable(fix.execute).ifPresent(exec -> annotation.withFix(fix));
                    ofNullable(fix.execute).ifPresent(exec -> silentAnnotation.withFix(fix));

                });
                annotation.create();
                silentAnnotation.create();
            });
        }
    }

    @SuppressWarnings("unused")
    public Icon icon() {
        return icon.icon();
    }

    @NotNull
    @Override
    public String getText() {
        return text;
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SyntaxAnnotation syntaxAnnotation = (SyntaxAnnotation) o;
        return Objects.equals(getText(), syntaxAnnotation.getText());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getText());
    }
}
