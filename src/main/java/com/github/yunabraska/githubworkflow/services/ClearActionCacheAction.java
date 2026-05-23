package com.github.yunabraska.githubworkflow.services;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public final class ClearActionCacheAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        final GitHubActionCache.CacheSummary before = GitHubActionCache.getActionCache().summary();
        GitHubActionCache.getActionCache().clear();
        notify(event, GitHubWorkflowBundle.message("notification.cache.cleared", before.total()));
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        localize(event.getPresentation());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static void notify(final AnActionEvent event, final String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("GitHub Workflow")
                .createNotification(content, NotificationType.INFORMATION)
                .notify(event.getProject());
    }

    private static void localize(final Presentation presentation) {
        presentation.setText(GitHubWorkflowBundle.message("action.GitHubWorkflow.ClearActionCache.text"));
        presentation.setDescription(GitHubWorkflowBundle.message("action.GitHubWorkflow.ClearActionCache.description"));
    }
}
