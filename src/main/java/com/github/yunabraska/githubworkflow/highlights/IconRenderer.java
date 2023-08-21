package com.github.yunabraska.githubworkflow.highlights;

import com.github.yunabraska.githubworkflow.quickfixes.CustomClickAction;
import com.github.yunabraska.githubworkflow.quickfixes.QuickFix;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static java.util.Optional.ofNullable;

public class IconRenderer extends GutterIconRenderer {

    private final Icon icon;
    private final QuickFix quickFix;
    private final PsiElement psiElement;

    public IconRenderer(final QuickFix quickFix, final PsiElement psiElement, final Icon icon) {
        this.icon = icon;
        this.quickFix = quickFix;
        this.psiElement = psiElement;
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
        return ofNullable(icon).map(ico -> new CustomClickAction(quickFix, psiElement)).orElse(null);
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return icon; // Replace this with your custom icon
    }

    @Override
    public boolean isNavigateAction() {
        return icon != null;
    }

    @Override
    @Nullable
    public String getTooltipText() {
        return icon == null ? null : quickFix.getText();
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
