package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.services.WorkflowPsi;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.services.WorkflowContextCatalog.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_INPUT;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class Inputs {

    public static void highLightInputs(
            final AnnotationHolder holder,
            final LeafPsiElement element,
            final SimpleElement[] parts
    ) {
        ifEnoughItems(holder, element, parts, 2, 2, inputId -> isDefinedItem0(element, holder, inputId, listInputs(element).stream().map(SimpleElement::key).toList()));
    }

    public static List<SimpleElement> listInputs(final PsiElement psiElement) {
        final Map<String, String> result = new HashMap<>();
        listInputsRaw(psiElement).forEach(input -> {
            final String description = getText(psiElement, "description").orElse("");
            final String previousDescription = result.computeIfAbsent(input.getKeyText(), value -> description);
            if (previousDescription.length() < description.length()) {
                result.put(input.getKeyText(), description);
            }
        });
        return completionItemsOf(result, ICON_INPUT);
    }

    @NotNull
    public static List<YAMLKeyValue> listInputsRaw(final PsiElement psiElement) {
        return getAllElements(psiElement.getContainingFile(), FIELD_INPUTS).stream()
                .map(WorkflowPsi::getChildren)
                .flatMap(Collection::stream)
                .toList();
    }

    private Inputs() {
        // static helper class
    }
}
