package com.github.yunabraska.githubworkflow.listeners;

import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.github.yunabraska.githubworkflow.listeners.ApplicationStartup.asyncInitWorkflowFile;

public class FileFocusListener implements FileEditorManagerListener {

    private final Project project;

    public FileFocusListener(final Project project) {
        this.project = project;
    }

    @Override
    public void selectionChanged(@NotNull final FileEditorManagerEvent event) {
        asyncInitWorkflowFile(project, event.getNewFile());
    }

}

