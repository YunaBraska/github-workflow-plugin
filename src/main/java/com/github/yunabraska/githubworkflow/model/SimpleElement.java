package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.utils.GitHubWorkflowUtils;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.utils.GitHubWorkflowUtils.orEmpty;
import static com.github.yunabraska.githubworkflow.utils.PsiElementHelper.removeQuotes;
import static java.util.Optional.ofNullable;

public record SimpleElement(String key, String text, TextRange range, NodeIcon icon) {

    public SimpleElement(final String key, final String text, final NodeIcon icon) {
        this(key, text, null, icon);
    }

    public SimpleElement(final String key, final String text, final TextRange range) {
        this(key, text, range, null);
    }

    @SuppressWarnings("unused")
    public NodeIcon icon() {
        return icon != null ? icon : NodeIcon.ICON_NODE;
    }

    public String textNoQuotes() {
        return removeQuotes(text);
    }

    public int startIndexOffset() {
        return ofNullable(range).map(TextRange::getStartOffset).orElse(-1);
    }

    public int endIndexOffset() {
        return ofNullable(range).map(TextRange::getEndOffset).orElse(-1);
    }

    public LookupElement toLookupElement() {
        return GitHubWorkflowUtils.toLookupElement(icon, Character.MIN_VALUE, key, text);
    }

    public static List<SimpleElement> completionItemsOf(final Map<String, String> map, final NodeIcon icon) {
        return map == null ? new ArrayList<>() : map.entrySet().stream()
                .map(item -> completionItemOf(item.getKey(), item.getValue(), icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static SimpleElement completionItemOf(final String key, final String text, final NodeIcon icon) {
        return key == null ? null : new SimpleElement(key, orEmpty(text), icon);
    }
}
