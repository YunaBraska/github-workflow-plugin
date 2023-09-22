package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IconRenderer extends GutterIconRenderer {

    private final NodeIcon icon;
    private final SyntaxAnnotation quickFix;
    private final PsiElement psiElement;

    public IconRenderer(final SyntaxAnnotation quickFix, final PsiElement psiElement, final NodeIcon icon) {
        this.icon = icon;
        this.quickFix = quickFix;
        this.psiElement = psiElement;
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
        return icon != null && quickFix != null ? new CustomClickAction(quickFix, psiElement) : null;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return icon.icon();
    }

    @Override
    public boolean isNavigateAction() {
        return quickFix != null;
    }

    @Override
    @Nullable
    public String getTooltipText() {
        return quickFix == null ? null : quickFix.getText();
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
