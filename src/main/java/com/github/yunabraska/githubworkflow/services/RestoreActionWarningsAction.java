package com.github.yunabraska.githubworkflow.services;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public final class RestoreActionWarningsAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        final long restored = GitHubActionCache.getActionCache().restoreWarnings();
        notify(event, GitHubWorkflowBundle.message("notification.warnings.restored", restored));
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        localize(event.getPresentation());
        final GitHubActionCache.CacheSummary summary = GitHubActionCache.getActionCache().summary();
        event.getPresentation().setEnabled(summary.suppressed() > 0);
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
        presentation.setText(GitHubWorkflowBundle.message("action.GitHubWorkflow.RestoreActionWarnings.text"));
        presentation.setDescription(GitHubWorkflowBundle.message("action.GitHubWorkflow.RestoreActionWarnings.description"));
    }
}
