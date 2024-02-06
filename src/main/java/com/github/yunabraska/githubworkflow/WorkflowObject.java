package com.github.yunabraska.githubworkflow;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_ELEMENT_REFERENCE_KEY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_ANNOTATION_KEY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_WORKFLOW_KEY;
import static java.util.Optional.ofNullable;

public class WorkflowObject {

    //TODO: handle inputs like "steps.step_id.output.key

    private final SmartPsiElementPointer<PsiElement> elementPointer;
    private final TextRange range;

    public WorkflowObject(final PsiElement element) {
        this(element, null);
    }

    public WorkflowObject(final TextRange range) {
        this(null, range);
    }

    public WorkflowObject(final PsiElement element, final TextRange range) {
        this.elementPointer = element == null ? null : SmartPointerManager.createPointer(element);
        this.range = range;
        resetTags();
    }

    public Optional<PsiElement> element() {
        return ofNullable(elementPointer).map(SmartPsiElementPointer::getElement).filter(PsiElement::isValid);
    }

    public SmartPsiElementPointer<PsiElement> elementPointer() {
        return elementPointer;
    }

    public Optional<TextRange> range() {
        return range != null ? Optional.of(range) : element().map(PsiElement::getTextRange);
    }

    public WorkflowObject resetTags() {
        element().ifPresent(element -> {
            element.putUserData(GHW_WORKFLOW_KEY, null);
            element.putUserData(GHW_ANNOTATION_KEY, null);
            element.putUserData(GHW_ELEMENT_REFERENCE_KEY, null);
        });
        return this;
    }
}
