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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void createAnnotation(
            final PsiElement psiElement,
            final AnnotationHolder holder
    ) {
        if (holder != null && psiElement != null && psiElement.isValid()) {
            final AnnotationBuilder annotation = holder.newAnnotation(level, text);
            final AnnotationBuilder silentAnnotation = holder.newSilentAnnotation(level);
            ofNullable(psiElement.getTextRange()).ifPresent(silentAnnotation::range);
            ofNullable(psiElement.getTextRange()).ifPresent(annotation::range);
            ofNullable(type).ifPresent(annotation::highlightType);
            ofNullable(text).ifPresent(annotation::tooltip);
            ofNullable(icon).map(i -> new IconRenderer2(this, psiElement, icon)).ifPresent(annotation::gutterIconRenderer);
            ofNullable(execute).ifPresent(fix -> annotation.withFix(this));
            ofNullable(execute).ifPresent(fix -> silentAnnotation.withFix(this));

            annotation.create();
            silentAnnotation.create();
        }
    }

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
