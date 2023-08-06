package com.github.yunabraska.githubworkflow.listeners;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.isWorkflowPath;
import static com.github.yunabraska.githubworkflow.listeners.ApplicationListeners.asyncInitWorkflowFile;

public class FileChangeListener implements DocumentListener {

    private static final int DEBOUNCE_DELAY_MS = 1000;
    private final Project project;
    private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public FileChangeListener(final Project project) {
        this.project = project;
    }

    @Override
    public void documentChanged(@NotNull final DocumentEvent event) {
        Optional.of(event.getDocument())
                .map(document -> FileDocumentManager.getInstance().getFile(document))
                .ifPresent(virtualFile -> {
                    if (isWorkflowPath(Paths.get(virtualFile.getPath()))) {
                        alarm.cancelAllRequests();
                        alarm.addRequest(() -> {
                            if (!project.isDisposed()) {
                                asyncInitWorkflowFile(project, virtualFile);
                            }
                        }, DEBOUNCE_DELAY_MS);
                    }
                });
    }
}
