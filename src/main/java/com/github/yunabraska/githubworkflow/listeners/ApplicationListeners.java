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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
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
import java.util.concurrent.atomic.AtomicReference;


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

            // READ CONTEXT
            final AtomicReference<WorkflowContext> context = readContext(project, virtualFile);

            // ASYNC HTTP CONTEXT
            if (context.get() != null) {
                downloadWorkflows(project, context.get());
            }
        }
    }

    @NotNull
    private static AtomicReference<WorkflowContext> readContext(final Project project, final VirtualFile virtualFile) {
        final AtomicReference<WorkflowContext> context = new AtomicReference<>(null);
        ApplicationManager.getApplication().runReadAction(() -> Optional.of(PsiManager.getInstance(project))
                .map(psiManager -> psiManager.findFile(virtualFile))
                .map(PsiElement::getChildren)
                .map(children -> children.length > 0 ? children[0] : null)
                .map(YamlElementHelper::yamlOf)
                .map(YamlElement::context)
                .map(WorkflowContext::init)
                .ifPresent(context::set));
        return context;
    }

    private static void downloadWorkflows(final Project project, final WorkflowContext context) {
        context.actions().values().forEach(action -> new Task.Backgroundable(project, "Resolving " + (action.isAction() ? "action" : "workflow") + action.slug(), false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setFraction(0.3);
                indicator.setText("Resolving " + (action.isAction() ? "action" : "workflow") + action.slug());
                action.resolve();
                indicator.setText("Done resolving " + (action.isAction() ? "action" : "workflow") + action.slug());
                indicator.setFraction(0.8);
            }
        }.queue());
    }
}
