package com.github.yunabraska.githubworkflow.quickfixes;

import com.intellij.codeInsight.intention.IntentionAction;

import javax.swing.*;
import java.util.Objects;
import java.util.StringJoiner;

public abstract class QuickFix implements IntentionAction {

    private final Icon icon;

    protected QuickFix(final Icon icon) {
        this.icon = icon;
    }

    public Icon icon() {
        return icon;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final QuickFix quickFix = (QuickFix) o;
        return Objects.equals(icon, quickFix.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(icon);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", QuickFix.class.getSimpleName() + "[", "]")
                .add("icon=" + icon)
                .toString();
    }
}
