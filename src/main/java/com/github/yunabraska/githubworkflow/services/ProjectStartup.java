package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.ListenerService;
import com.github.yunabraska.githubworkflow.helper.PsiElementChangeListener;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;
import static com.intellij.openapi.util.io.NioFiles.toPath;


public class ProjectStartup implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        final Disposable listenerDisposable = Disposer.newDisposable();
        Disposer.register(ListenerService.getInstance(project), listenerDisposable);

        // ON PsiElement Change
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiElementChangeListener(), listenerDisposable);

        // AFTER STARTUP
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        for (final VirtualFile openedFile : fileEditorManager.getOpenFiles()) {
            asyncInitAllActionsAfterInit(project, openedFile);
        }

        final MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
                asyncInitAllActionsAfterInit(project, file);
            }
        });


        // CLEANUP ACTION CACHE SCHEDULER
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> getActionCache().cleanUp(), 0, 30, TimeUnit.MINUTES);

        // Ensure the executor is shut down when the project is disposed
        Disposer.register(ListenerService.getInstance(project), () -> {
            executorService.shutdown();
            unregisterAction(project);
        });
        return null;
    }

    private void unregisterAction(final Project project) {
        final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
        for (final String oldId : actionManager.getActionIdList("GHWP_" + project.getLocationHash())) {
            actionManager.unregisterAction(oldId);
        }
    }


    public static void asyncInitAllActionsAfterInit(final Project project, final VirtualFile virtualFile) {
        if (!DumbService.isDumb(project)) {
            asyncInitAllActions(project, virtualFile);
        } else {
            DumbService.getInstance(project).runWhenSmart(() -> asyncInitAllActions(project, virtualFile));
        }
    }

    private static void asyncInitAllActions(final Project project, final VirtualFile virtualFile) {
        if (virtualFile != null && (GitHubWorkflowHelper.isWorkflowPath(toPath(virtualFile.getPath())))) {
            final List<GitHubAction> actions = new ArrayList<>();
            // READ CONTEXT
            ApplicationManager.getApplication().runReadAction(() -> Optional.of(PsiManager.getInstance(project))
                    .map(psiManager -> psiManager.findFile(virtualFile))
                    .map(psiFile -> PsiElementHelper.getAllElements(psiFile, FIELD_USES))
                    .ifPresent(usesList -> usesList.stream().map(GitHubActionCache::getAction).filter(action -> !action.isSuppressed()).filter(action -> !action.isResolved()).forEach(actions::add))
            );

            // ASYNC HTTP CONTEXT
            GitHubActionCache.resolveActionsAsync(actions);
        }
    }
}
