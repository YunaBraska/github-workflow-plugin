package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.services.GitHubWorkflowBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.services.WorkflowContextCatalog.FIELD_IF;
import static com.github.yunabraska.githubworkflow.services.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.services.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.deleteElementAction;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.replaceAction;
import static com.github.yunabraska.githubworkflow.services.WorkflowAnnotations.simpleTextRange;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getChildren;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.services.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_SECRET_WORKFLOW;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;

public class Secrets {

    private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

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
                        GitHubWorkflowBundle.message("inspection.secret.invalid.if", simpleElement.text()),
                        SUPPRESS_ON,
                        deleteElementAction(textRange)
                ).createAnnotation(psiElement, textRange, holder);
            }
            final List<String> secrets = listSecrets(element).stream().map(SimpleElement::key).toList();
            if (!secrets.contains(secretId.text())) {
                final TextRange textRange = simpleTextRange(element, secretId);
                createAnnotation(element, textRange, holder, secrets.stream().map(secret -> new SyntaxAnnotation(
                        GitHubWorkflowBundle.message("inspection.secret.replace.runtime", secretId.text(), secret),
                        RELOAD,
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
        if (getParent(psiElement, FIELD_IF).isPresent()) {
            return Collections.emptyList();
        }
        final Map<String, String> result = getChild(psiElement.getContainingFile(), FIELD_ON)
                .map(on -> getAllElements(on, FIELD_SECRETS))
                .map(secrets -> secrets.stream().flatMap(secret -> getChildren(secret).stream()).collect(Collectors.toMap(
                        YAMLKeyValue::getKeyText,
                        keyValue -> getText(keyValue, "description").orElse(""),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                )))
                .orElseGet(LinkedHashMap::new);
        result.putIfAbsent(GITHUB_TOKEN, GitHubWorkflowBundle.message("completion.secret.githubToken"));
        return completionItemsOf(result, ICON_SECRET_WORKFLOW);
    }

    private Secrets() {
        // static helper class
    }
}
