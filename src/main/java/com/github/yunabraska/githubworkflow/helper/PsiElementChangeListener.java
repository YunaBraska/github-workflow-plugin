package com.github.yunabraska.githubworkflow.helper;

import com.github.yunabraska.githubworkflow.services.GitHubActionCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static java.util.Optional.ofNullable;

public class PsiElementChangeListener extends PsiTreeChangeAdapter {

    // ON PsiElement CHANGE
    @Override
    public void childReplaced(@NotNull final PsiTreeChangeEvent event) {
        ofNullable(event.getNewChild())
                .filter(psiElement -> getWorkflowFile(psiElement).isPresent())
                .flatMap(psiElement -> PsiElementHelper.getParent(psiElement, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> !action.isResolved())
                .map(List::of)
                .ifPresent(GitHubActionCache::resolveActionsAsync);
    }

    // ON INSERT / PASTE
    @Override
    public void childrenChanged(@NotNull final PsiTreeChangeEvent event) {
        ofNullable(event.getParent())
                .filter(psiElement -> getWorkflowFile(psiElement).isPresent())
                .map(psiElement -> PsiElementHelper.getAllElements(psiElement, FIELD_USES))
                .map(usesList -> usesList.stream().map(GitHubActionCache::getAction).filter(Objects::nonNull).filter(action -> !action.isLocal()).filter(action -> !action.isResolved()).toList())
                .ifPresent(GitHubActionCache::resolveActionsAsync);
    }
}
