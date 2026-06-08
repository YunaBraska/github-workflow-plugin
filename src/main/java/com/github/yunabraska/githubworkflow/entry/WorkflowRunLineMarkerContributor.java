package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.state.WorkflowRunTracker;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.git.WorkflowRepositoryResolver;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adds the standard Run gutter action to workflow_dispatch entries.
 */
public class WorkflowRunLineMarkerContributor extends RunLineMarkerContributor {

    private static final RepositoryAvailability DEFAULT_REPOSITORY_AVAILABILITY =
            (project, file) -> new WorkflowRepositoryResolver().resolve(project, file).isPresent();
    private static final AtomicReference<RepositoryAvailability> repositoryAvailability =
            new AtomicReference<>(DEFAULT_REPOSITORY_AVAILABILITY);

    @Override
    public @Nullable Info getInfo(final PsiElement element) {
        if (!(element instanceof LeafPsiElement) || !"workflow_dispatch".equals(element.getText())) {
            return null;
        }
        if (!(element.getParent() instanceof YAMLKeyValue keyValue) || !"workflow_dispatch".equals(keyValue.getKeyText())) {
            return null;
        }
        final Optional<String> workflowPath = Optional.ofNullable(element.getContainingFile())
                .map(file -> file.getVirtualFile())
                .flatMap(file -> WorkflowRunConfigurationProducer.workflowPath(element.getProject(), file)
                        .or(() -> PsiElementHelper.toPath(file).map(path -> path.getFileName().toString())));
        final boolean workflowFile = Optional.ofNullable(element.getContainingFile())
                .map(file -> file.getVirtualFile())
                .flatMap(PsiElementHelper::toPath)
                .filter(GitHubWorkflowHelper::isWorkflowPath)
                .isPresent();
        if (!workflowFile || workflowPath.isEmpty()) {
            return null;
        }
        if (WorkflowRunTracker.getInstance(element.getProject()).isRunning(workflowPath.get())) {
            return new Info(AllIcons.Actions.Suspend, new AnAction[]{new StopWorkflowRunAction(workflowPath.get())}, item -> GitHubWorkflowBundle.message("workflow.run.gutter.stop"));
        }
        final boolean repositoryAvailable = Optional.ofNullable(element.getContainingFile())
                .map(file -> file.getVirtualFile())
                .map(file -> repositoryAvailability.get().available(element.getProject(), file))
                .orElse(false);
        if (!repositoryAvailable) {
            return null;
        }
        return withExecutorActions(AllIcons.Actions.Execute);
    }

    public static RepositoryAvailability useRepositoryAvailabilityForTests(final RepositoryAvailability availability) {
        return repositoryAvailability.getAndSet(availability == null ? DEFAULT_REPOSITORY_AVAILABILITY : availability);
    }

    public interface RepositoryAvailability {
        boolean available(Project project, VirtualFile file);
    }

    private static class StopWorkflowRunAction extends AnAction {

        private final String workflowPath;

        private StopWorkflowRunAction(final String workflowPath) {
            super(GitHubWorkflowBundle.message("workflow.run.gutter.stop.text"), GitHubWorkflowBundle.message("workflow.run.gutter.stop.description"), AllIcons.Actions.Suspend);
            this.workflowPath = workflowPath;
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent event) {
            Optional.ofNullable(event.getProject())
                    .map(WorkflowRunTracker::getInstance)
                    .ifPresent(tracker -> tracker.stop(workflowPath));
        }
    }
}
