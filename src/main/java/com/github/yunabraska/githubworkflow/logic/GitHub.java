package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.impl.source.tree.LeafPsiElement;

import java.util.ArrayList;
import java.util.List;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_GITHUB_MAP;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class GitHub {

    private GitHub() {
        // static helper class
    }

    public static void highLightGitHub(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, envId -> isDefinedItem0(element, holder, envId, new ArrayList<>(FIELD_GITHUB_MAP.keySet())));
    }

    public static List<SimpleElement> codeCompletionGithub() {
        return completionItemsOf(FIELD_GITHUB_MAP, ICON_ENV);
    }
}
