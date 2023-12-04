package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.impl.source.tree.LeafPsiElement;

import java.util.ArrayList;
import java.util.List;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_RUNNER;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class Runner {

    public static void highlightRunner(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, 2, runnerId -> isDefinedItem0(element, holder, runnerId, new ArrayList<>(FIELD_RUNNER_MAP.keySet())));
    }

    public static List<SimpleElement> codeCompletionRunner() {
        return completionItemsOf(FIELD_RUNNER_MAP, ICON_RUNNER);
    }

    private Runner() {
        // static helper class
    }
}
