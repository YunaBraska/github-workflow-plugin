package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.model.WorkflowContext;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.github.yunabraska.githubworkflow.model.YamlElementHelper;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.isWorkflowPath;

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
                .ifPresent(file -> {
                    if (isWorkflowPath(Paths.get(file.getPath()))) {
                        alarm.cancelAllRequests();
                        alarm.addRequest(() -> {
                            if (!project.isDisposed()) {
                                processChange(file);
                            }
                        }, DEBOUNCE_DELAY_MS);
                    }
                });
    }

    private void processChange(final VirtualFile file) {
        Optional.of(PsiManager.getInstance(project))
                .map(psiManager -> psiManager.findFile(file))
                .map(PsiElement::getChildren)
                .map(children -> children.length > 0 ? children[0] : null)
                .map(YamlElementHelper::yamlOf)
                .map(YamlElement::context)
                .ifPresent(WorkflowContext::init);
    }
}
