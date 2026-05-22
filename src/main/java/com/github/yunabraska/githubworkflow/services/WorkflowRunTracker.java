package com.github.yunabraska.githubworkflow.services;

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
public final class WorkflowRunTracker {

    private final Project project;
    private final ConcurrentMap<String, ProcessHandler> runs = new ConcurrentHashMap<>();

    public WorkflowRunTracker(@NotNull final Project project) {
        this.project = project;
    }

    public static WorkflowRunTracker getInstance(final Project project) {
        return project.getService(WorkflowRunTracker.class);
    }

    public static String key(final String workflowPath) {
        return Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
    }

    public boolean isRunning(final String workflowPath) {
        return runs.containsKey(key(workflowPath));
    }

    public void register(final String workflowPath, final ProcessHandler processHandler) {
        runs.put(key(workflowPath), processHandler);
        refreshGutters();
    }

    public void unregister(final String workflowPath, final ProcessHandler processHandler) {
        runs.remove(key(workflowPath), processHandler);
        refreshGutters();
    }

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
