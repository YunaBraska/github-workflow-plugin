package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.model.WorkflowContext;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.github.yunabraska.githubworkflow.model.YamlElementHelper;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Optional;

public class FileOpenListener implements FileEditorManagerListener {
    public FileOpenListener() {
    }

    @Override
    public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
        if (GitHubWorkflowUtils.isWorkflowPath(Paths.get(file.getPath()))) {
            Optional.of(source)
                    .map(FileEditorManager::getProject)
                    .map(PsiManager::getInstance)
                    .map(psiManager -> psiManager.findFile(file))
                    .map(PsiFile::getChildren)
                    .map(children -> children.length > 0 ? children[0] : null)
                    .map(YamlElementHelper::yamlOf)
                    .map(YamlElement::context)
                    .ifPresent(WorkflowContext::init);
        }
    }
}
