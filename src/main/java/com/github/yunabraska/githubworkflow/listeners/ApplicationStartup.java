package com.github.yunabraska.githubworkflow.listeners;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.model.WorkflowContext;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.github.yunabraska.githubworkflow.model.YamlElementHelper;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.io.NioFiles.toPath;


public class ApplicationStartup implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        final Disposable listenerDisposable = Disposer.newDisposable();
        Disposer.register(ListenerService.getInstance(project), listenerDisposable);

        // ON TYPING (with delay)
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new FileChangeListener(project), listenerDisposable);

        // SWITCH TABS
        project.getMessageBus().connect(listenerDisposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileFocusListener(project));

        // AFTER STARTUP
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        for (final VirtualFile openedFile : fileEditorManager.getOpenFiles()) {
            asyncInitWorkflowFile(project, openedFile);
        }

        Disposer.register(ListenerService.getInstance(project), () -> unregisterAction(project));
        return null;
    }

    private void unregisterAction(final Project project) {
        final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
        for (final String oldId : actionManager.getActionIdList("GHWP_" + project.getLocationHash())) {
            actionManager.unregisterAction(oldId);
        }
    }


    public static void asyncInitWorkflowFile(final Project project, final VirtualFile virtualFile) {
        if (virtualFile != null && (GitHubWorkflowUtils.isWorkflowPath(toPath(virtualFile.getPath())))) {

            // READ CONTEXT
            final AtomicReference<WorkflowContext> context = readContext(project, virtualFile);

            // ASYNC HTTP CONTEXT
            if (context.get() != null) {
                downloadWorkflows(project, virtualFile, context.get());
            }
        }
    }

    public static void triggerSyntaxHighLightingRefresh(final Project project, final VirtualFile virtualFile) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (virtualFile.isValid()) {
                // Trigger re-highlighting or any other UI related action
                final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile != null) {
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
                }
            }
        });
    }

    @NotNull
    private static AtomicReference<WorkflowContext> readContext(final Project project, final VirtualFile virtualFile) {
        final AtomicReference<WorkflowContext> context = new AtomicReference<>(null);
        ApplicationManager.getApplication().runReadAction(() -> Optional.of(PsiManager.getInstance(project))
                .map(psiManager -> psiManager.findFile(virtualFile))
                .filter(YAMLFile.class::isInstance)
                .map(PsiElement::getChildren)
                .map(children -> children.length > 0 ? children[0] : null)
                .map(YamlElementHelper::yamlOf)
                .map(YamlElement::context)
                .ifPresent(context::set));
        return context;
    }

    private static void downloadWorkflows(final Project project, final VirtualFile virtualFile, final WorkflowContext context) {
        context.actions().values().forEach(action -> new Task.Backgroundable(project, "Resolving " + (action.isAction() ? "action" : "workflow") + action.slug(), false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    indicator.setFraction(0.3);
                    indicator.setText("Resolving " + (action.isAction() ? "action" : "workflow") + action.slug());
                    action.resolve(project);
                    indicator.setText("Done resolving " + (action.isAction() ? "action" : "workflow") + action.slug());
                    indicator.setFraction(0.8);
                    triggerSyntaxHighLightingRefresh(project, virtualFile);
                } catch (final Exception e) {
                    // Proceed action even on issues within the progress bar
                    action.resolve(project);
                    triggerSyntaxHighLightingRefresh(project, virtualFile);
                    throw e;
                }
            }
        }.queue());
    }
}
