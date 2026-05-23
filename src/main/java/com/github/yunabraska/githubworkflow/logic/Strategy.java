package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.impl.source.tree.LeafPsiElement;

import java.util.ArrayList;
import java.util.List;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_STRATEGY;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class Strategy {

    public static void highlightStrategy(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, 2, field -> isDefinedItem0(element, holder, field, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_STRATEGY).get().keySet())));
    }

    public static List<SimpleElement> codeCompletionStrategy() {
        return completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_STRATEGY).get(), ICON_NODE);
    }

    private Strategy() {
        // static helper class
    }
}
