package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.ListenerService;
import com.github.yunabraska.githubworkflow.helper.PsiElementChangeListener;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;
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
            asyncInitAllActions(project, openedFile);
        }

        final MessageBusConnection connection = project.getMessageBus().connect(listenerDisposable);
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
                asyncInitAllActions(project, file);
            }
        });


        // CLEANUP ACTION CACHE SCHEDULER
        final ScheduledFuture<?> cleanupTask = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(() -> getActionCache().cleanUp(), 0, 30, TimeUnit.MINUTES);

        // Ensure the executor is shut down when the project is disposed
        Disposer.register(ListenerService.getInstance(project), () -> {
            cleanupTask.cancel(false);
        });
        return null;
    }

    private static void asyncInitAllActions(final Project project, final VirtualFile virtualFile) {
        final Runnable task = () -> {
            if (virtualFile != null && virtualFile.isValid() && (GitHubWorkflowHelper.isWorkflowPath(toPath(virtualFile.getPath())))) {
                ReadAction.nonBlocking(() -> unresolvedActions(project, virtualFile))
                        .inSmartMode(project)
                        .submit(AppExecutorUtil.getAppExecutorService())
                        .onSuccess(GitHubActionCache::resolveActionsAsync);
            }
        };

        threadPoolExec(project, task);
    }

    private static List<GitHubAction> unresolvedActions(final Project project, final VirtualFile virtualFile) {
        final List<GitHubAction> actions = new ArrayList<>();
        Optional.of(PsiManager.getInstance(project))
                .map(psiManager -> psiManager.findFile(virtualFile))
                .map(psiFile -> PsiElementHelper.getAllElements(psiFile, FIELD_USES))
                .ifPresent(usesList -> usesList.stream()
                        .map(GitHubActionCache::getAction)
                        .filter(Objects::nonNull)
                        .filter(action -> !action.isSuppressed())
                        .filter(action -> !action.isResolved())
                        .forEach(actions::add));
        return actions;
    }

    public static void threadPoolExec(final Project project, final Runnable task) {
        if (!DumbService.isDumb(project)) {
            AppExecutorUtil.getAppExecutorService().execute(task);
        } else {
            DumbService.getInstance(project).runWhenSmart(() -> AppExecutorUtil.getAppExecutorService().execute(task));
        }
    }
}
