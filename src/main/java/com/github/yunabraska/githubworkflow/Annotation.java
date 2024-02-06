package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.sortByProximity;

public class Annotation {

    final String message;
    final List<String> replaceWith;
    final TextRange range;
    final NodeIcon icon;

    // VARIABLE REMOVE / RE{LACE
    public Annotation(final String key, final List<String> replaceWith, final String message) {
        this(key, replaceWith, message, null, null);
    }

    // VARIABLE DEFINITION
    public Annotation(final String definition, final NodeIcon icon) {
        this(null, null, definition, null, icon);
    }


    public Annotation(final String key, final List<String> replaceWith, final String message, final TextRange range, NodeIcon icon) {
        this.message = message;
        this.range = range;
        this.icon = icon;
        this.replaceWith = key == null || replaceWith == null ? null : sortByProximity(key, replaceWith);
    }

    public void toAnnotation(@NotNull final AnnotationHolder holder, final PsiElement element) {
        if (replaceWith != null) {
            final AtomicInteger order = new AtomicInteger(1);
            final AtomicReference<AnnotationBuilder> builder = new AtomicReference<>(holder.newAnnotation(HighlightSeverity.ERROR, message).range(range == null ? element.getTextRange() : range).highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            replaceWith.forEach(newValue -> builder.set(builder.get().withFix(new ReplaceQuickFix(order.getAndIncrement(), element, newValue))));
            builder.get().create();
        } else if (icon != null) {
            // TODO: show at sidebar
        }
    }
}
