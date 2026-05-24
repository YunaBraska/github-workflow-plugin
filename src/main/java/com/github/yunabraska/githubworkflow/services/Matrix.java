package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.services.WorkflowPsi;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.services.WorkflowContextCatalog.FIELD_MATRIX;
import static com.github.yunabraska.githubworkflow.services.WorkflowContextCatalog.FIELD_STRATEGY;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class Matrix {

    public static void highlightMatrix(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, field -> isDefinedItem0(element, holder, field, listMatrix(element).stream().map(SimpleElement::key).toList()));
    }

    public static List<SimpleElement> listMatrix(final PsiElement psiElement) {
        return completionItemsOf(listMatrixRaw(psiElement), ICON_NODE);
    }

    private static Map<String, String> listMatrixRaw(final PsiElement psiElement) {
        final Map<String, String> result = new LinkedHashMap<>();
        getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_STRATEGY))
                .flatMap(strategy -> getChild(strategy, FIELD_MATRIX))
                .ifPresent(matrix -> {
                    WorkflowPsi.getChildren(matrix).stream()
                            .filter(Matrix::isMatrixProperty)
                            .forEach(property -> result.putIfAbsent(property.getKeyText(), WorkflowPsi.getText(property).orElse("")));
                    getChild(matrix, "include")
                            .map(include -> WorkflowPsi.getChildren(include, YAMLSequenceItem.class))
                            .stream()
                            .flatMap(List::stream)
                            .flatMap(item -> WorkflowPsi.getChildren(item).stream())
                            .forEach(property -> result.putIfAbsent(property.getKeyText(), WorkflowPsi.getText(property).orElse("")));
                });
        return result;
    }

    private static boolean isMatrixProperty(final YAMLKeyValue keyValue) {
        final String key = keyValue.getKeyText();
        return !"include".equals(key) && !"exclude".equals(key);
    }

    private Matrix() {
        // static helper class
    }
}
