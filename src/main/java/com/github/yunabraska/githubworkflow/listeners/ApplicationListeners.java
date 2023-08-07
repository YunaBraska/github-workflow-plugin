package com.github.yunabraska.githubworkflow.listeners;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.model.WorkflowContext;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.github.yunabraska.githubworkflow.model.YamlElementHelper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;
import java.util.Optional;


public class ApplicationListeners implements ProjectActivity {

    private final Disposable disposable = Disposer.newDisposable();

    @Nullable
    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {

        // ON TYPING (with delay)
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new FileChangeListener(project), disposable);

        // SWITCH TABS
        project.getMessageBus().connect(disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileFocusListener(project));

        // AFTER STARTUP
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        for (final VirtualFile openedFile : fileEditorManager.getOpenFiles()) {
            asyncInitWorkflowFile(project, openedFile);
        }
        return null;
    }

    public static void asyncInitWorkflowFile(final Project project, final VirtualFile virtualFile) {
        if (virtualFile != null && (GitHubWorkflowUtils.isWorkflowPath(Paths.get(virtualFile.getPath())))) {
            //ASYNC
            ApplicationManager.getApplication().runReadAction(() -> Optional.of(PsiManager.getInstance(project))
                    .map(psiManager -> psiManager.findFile(virtualFile))
                    .map(PsiElement::getChildren)
                    .map(children -> children.length > 0 ? children[0] : null)
                    .map(YamlElementHelper::yamlOf)
                    .map(YamlElement::context)
                    .ifPresent(WorkflowContext::init));
        }
    }
}
