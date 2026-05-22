package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

public class IconRenderer extends GutterIconRenderer {

    private final NodeIcon icon;
    private final List<SyntaxAnnotation> quickFixes;
    private final PsiElement psiElement;

    public IconRenderer(final SyntaxAnnotation quickFix, final PsiElement psiElement, final NodeIcon icon) {
        this(quickFix, psiElement, icon, quickFix == null ? List.of() : List.of(quickFix));
    }

    public IconRenderer(final SyntaxAnnotation quickFix, final PsiElement psiElement, final NodeIcon icon, final List<SyntaxAnnotation> quickFixes) {
        this.icon = icon;
        this.quickFixes = quickFixes == null ? List.of() : quickFixes.stream()
                .filter(Objects::nonNull)
                .filter(SyntaxAnnotation::hasExecution)
                .distinct()
                .toList();
        this.psiElement = psiElement;
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
        return quickFixes.size() == 1 ? new CustomClickAction(quickFixes.get(0), psiElement) : null;
    }

    @Override
    public @Nullable ActionGroup getPopupMenuActions() {
        if (quickFixes.size() <= 1) {
            return null;
        }
        final DefaultActionGroup group = new DefaultActionGroup();
        quickFixes.forEach(fix -> group.add(new CustomClickAction(fix, psiElement)));
        return group;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return icon.icon();
    }

    @Override
    public boolean isNavigateAction() {
        return quickFixes.size() == 1;
    }

    @Override
    @Nullable
    public String getTooltipText() {
        return quickFixes.isEmpty()
                ? null
                : quickFixes.stream().map(SyntaxAnnotation::getText).distinct().reduce((left, right) -> left + "\n" + right).orElse(null);
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof IconRenderer; // You can add more specific conditions if needed
    }

    @Override
    public int hashCode() {
        return IconRenderer.class.hashCode();
    }
}
