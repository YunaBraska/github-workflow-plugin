package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.*;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.*;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_SECRET_WORKFLOW;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;

public class Secrets {

    private Secrets() {
        // static helper class
    }

    public static void highLightSecrets(
        final AnnotationHolder holder,
        final PsiElement psiElement,
        final LeafPsiElement element, final SimpleElement simpleElement,
        final SimpleElement[] parts,
        final YAMLKeyValue parentIf
    ) {
        ifEnoughItems(holder, element, parts, 2, 2, secretId -> {
            // SECRETS ARE NOT ALLOWED IN IF STATEMENT
            if (parentIf != null) {
                final TextRange range = psiElement.getTextRange();
                final TextRange textRange = new TextRange(range.getStartOffset() + parts[0].startIndexOffset(), range.getStartOffset() + parts[parts.length - 1].endIndexOffset());
                new SyntaxAnnotation(
                    "Remove [" + simpleElement.text() + "] - Secrets are not valid in `if` statements",
                    null,
                    deleteElementAction(textRange)
                ).createAnnotation(psiElement, textRange, holder);
            }
            final List<String> secrets = listSecrets(element).stream().map(SimpleElement::key).toList();
            if (!secrets.contains(secretId.text())) {
                final TextRange textRange = simpleTextRange(element, secretId);
                createAnnotation(element, textRange, holder, secrets.stream().map(secret -> new SyntaxAnnotation(
                    "Replace [" + secretId.text() + "] with [" + secret + "] - if it is not provided at runtime",
                    null,
                    HighlightSeverity.WEAK_WARNING,
                    ProblemHighlightType.WEAK_WARNING,
                    replaceAction(textRange, secret),
                    true
                )).toList());
            }
        });
    }

    public static List<SimpleElement> listSecrets(final PsiElement psiElement) {
        //WORKFLOW SECRETS
        return getParent(psiElement, FIELD_IF).isPresent() ? Collections.emptyList() : getChild(psiElement.getContainingFile(), FIELD_ON)
            .map(on -> getAllElements(on, FIELD_SECRETS))
            .map(secrets -> secrets.stream().flatMap(secret -> getChildren(secret).stream()).collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue, "description").orElse(""), (existing, replacement) -> existing)))
            .map(map -> completionItemsOf(map, ICON_SECRET_WORKFLOW))
            .orElseGet(ArrayList::new);
    }
}
