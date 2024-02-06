package com.github.yunabraska.githubworkflow;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

public class Definition extends WorkflowObject {

    private final String key;
    private final String value;

    public Definition(final PsiElement element, final String key, final String value) {
        super(element);
        this.key = key;
        this.value = value;
    }

    public Definition(final TextRange range, final String key, final String value) {
        super(range);
        this.key = key;
        this.value = value;
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "Definition{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
