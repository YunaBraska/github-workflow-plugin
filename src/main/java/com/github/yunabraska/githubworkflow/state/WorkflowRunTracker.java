package com.github.yunabraska.githubworkflow.state;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks workflow runs started from this project so editor gutter actions can switch between run and stop.
 */
@Service(Service.Level.PROJECT)
public class WorkflowRunTracker {

    private final Project project;
    private final ConcurrentMap<String, ProcessHandler> runs = new ConcurrentHashMap<>();

    /**
     * Creates a run tracker for one project.
     *
     * @param project project owning the workflow run state
     */
    public WorkflowRunTracker(@NotNull final Project project) {
        this.project = project;
    }

    /**
     * Returns the workflow run tracker for a project.
     *
     * @param project project whose workflow runs should be tracked
     * @return project-level workflow run tracker
     */
    public static WorkflowRunTracker getInstance(final Project project) {
        return project.getService(WorkflowRunTracker.class);
    }

    /**
     * Normalizes a workflow path into the tracker key format.
     *
     * @param workflowPath workflow path from the repository root
     * @return normalized key using forward slashes
     */
    public static String key(final String workflowPath) {
        return Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
    }

    /**
     * Reports whether the given workflow currently has a running process.
     *
     * @param workflowPath workflow path from the repository root
     * @return true when a run process is registered
     */
    public boolean isRunning(final String workflowPath) {
        return runs.containsKey(key(workflowPath));
    }

    /**
     * Registers a workflow run process and refreshes gutter actions.
     *
     * @param workflowPath workflow path from the repository root
     * @param processHandler process handler controlling the run
     */
    public void register(final String workflowPath, final ProcessHandler processHandler) {
        runs.put(key(workflowPath), processHandler);
        refreshGutters();
    }

    /**
     * Removes a workflow run process when it completes or is disposed.
     *
     * @param workflowPath workflow path from the repository root
     * @param processHandler process handler previously registered for the run
     */
    public void unregister(final String workflowPath, final ProcessHandler processHandler) {
        runs.remove(key(workflowPath), processHandler);
        refreshGutters();
    }

    /**
     * Stops the running workflow process for a path when one exists.
     *
     * @param workflowPath workflow path from the repository root
     * @return true when a run process was found and stop was requested
     */
    public boolean stop(final String workflowPath) {
        return Optional.ofNullable(runs.get(key(workflowPath)))
                .map(processHandler -> {
                    processHandler.destroyProcess();
                    return true;
                })
                .orElse(false);
    }

    private void refreshGutters() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                DaemonCodeAnalyzer.getInstance(project).settingsChanged();
            }
        });
    }
}
