package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.WorkflowFile;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_WORKFLOW_KEY;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

@Service(Service.Level.APP)
@SuppressWarnings({"java:S6548"})
public final class GitHubWorkflowService implements Disposable {
    private static GitHubWorkflowService instance;
    private final Map<VirtualFile, Long> fileQueue = new ConcurrentHashMap<>();
    private final ScheduledExecutorService queueWorker = Executors.newSingleThreadScheduledExecutor();
    private final MessageBusConnection eventListener = ApplicationManager.getApplication().getMessageBus().connect();
    public static final Key<String> GHA_SERVICE_KEY = new Key<>("GHA_SERVICE_KEY");

    public static synchronized GitHubWorkflowService getInstance() {
        if (instance == null) {
            instance = new GitHubWorkflowService();
        }
        return instance;
    }

    public void addToQueue(final VirtualFile file) {
        if (isWorkflowPath(file)) {
            fileQueue.put(file, System.currentTimeMillis());
        }
    }

    public void processFile(final Project project, final VirtualFile file) {
        getPsiFile(project, file).flatMap(psiFile -> PsiElementHelper.getChild(psiFile, YAMLMapping.class)).ifPresent(yamlElement ->
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "GHW - Analysing [" + file.getPresentableName() + "]") {
                    @Override
                    public void run(@NotNull final ProgressIndicator indicator) {
                        ApplicationManager.getApplication().runReadAction(() -> {
                            System.out.println("[Processor] start " + file);
                            ofNullable(yamlElement.getContainingFile()).ifPresent(psiFile -> psiFile.putUserData(GHW_WORKFLOW_KEY, new WorkflowFile(yamlElement)));
                            triggerAnnotator(file);
                        });
                    }
                })
        );
    }

    private Optional<PsiFile> getPsiFile(final Project project, final VirtualFile file) {
        return Optional.ofNullable(PsiManager.getInstance(project).findFile(file));

    }

    private Optional<Project> getProject(final VirtualFile file) {
        for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (FileEditorManager.getInstance(project).isFileOpen(file)) {
                return of(project);
            }
        }
        return Optional.empty();
    }

    @Override
    public void dispose() {
        eventListener.disconnect();
        queueWorker.shutdownNow();
    }

    private void processQueue() {
        final long currentTime = System.currentTimeMillis();
        final Set<Project> projects = new HashSet<>();
        fileQueue.forEach((file, timestamp) -> {
            if (currentTime - timestamp > 1000) {
                if (file.isValid()) {
                    ApplicationManager.getApplication().runReadAction(() -> {
                        // Read access is allowed from inside read-action (or EDT) only (see com.intellij.openapi.application.Application.runReadAction())
                        Optional.of(file).filter(GitHubWorkflowService::isWorkflowPath).flatMap(this::getProject).ifPresent(project -> {
                            processFile(project, file);
                            projects.add(project);
                        });
                    });
                }
                fileQueue.remove(file);
            }
        });
        if (!projects.isEmpty()) {
            triggerReferenceContributor(projects);
        }
    }

    private static void triggerReferenceContributor(final Collection<Project> projects) {
        ApplicationManager.getApplication().invokeLater(() ->
                projects.forEach(project -> PsiManager.getInstance(project).dropPsiCaches())
        );

    }

    private GitHubWorkflowService() {
        queueWorker.scheduleWithFixedDelay(this::processQueue, 1000, 500, TimeUnit.MILLISECONDS);
        subscribeOnFocus();
        subscribeOnTyping(); // FIXME: on Typing or on Element Change (addPsiTreeChangeListener)?
        subscribeOnFileChange();
    }

    private void triggerAnnotator(final VirtualFile file) {
        ApplicationManager.getApplication().invokeLater(() ->
                Stream.of(ProjectManager.getInstance().getOpenProjects()).forEach(project -> Stream.of(FileEditorManager.getInstance(project).getSelectedFiles()).filter(VirtualFile::isValid)
                        .filter(virtualFile -> Objects.equals(virtualFile, file))
                        .forEach(virtualFile ->
                                ofNullable(PsiManager.getInstance(project).findFile(virtualFile))
                                        .filter(PsiFile::isValid)
                                        .ifPresent(psiFile -> {
                                            final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
                                            if (daemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
                                                daemonCodeAnalyzer.restart(psiFile);
                                            }
                                        })
                        )
                ));
    }

    private void subscribeOnFileChange() {
        eventListener.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull final List<? extends VFileEvent> events) {
                for (final VFileEvent event : events) {
                    addToQueue(event.getFile());
                }
            }
        });
    }

    private void subscribeOnTyping() {
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull final DocumentEvent event) {
                Optional.of(event.getDocument()).map(document -> FileDocumentManager.getInstance().getFile(document)).ifPresent(GitHubWorkflowService.this::addToQueue);
            }
        }, this);
    }

    private void subscribeOnFocus() {
        eventListener.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull final FileEditorManagerEvent event) {
                addToQueue(event.getNewFile());
            }
        });
    }

    public static boolean isWorkflowPath(final VirtualFile file) {
        return file != null && (isActionFile(file) || isWorkflowFile(file) || ApplicationManager.getApplication().isUnitTestMode());
    }

    public static boolean isActionFile(final VirtualFile file) {
        return file != null && file.getParent() != null
                && (file.getName().equalsIgnoreCase("action.yml")
                || file.getName().equalsIgnoreCase("action.yaml"));
    }

    public static boolean isWorkflowFile(final VirtualFile file) {
        return file != null && file.getParent() != null && file.getParent().getParent() != null
                && isYamlFile(file)
                && file.getParent().getName().equalsIgnoreCase("workflows")
                && file.getParent().getParent().getName().equalsIgnoreCase(".github");
    }

    private static boolean isYamlFile(final VirtualFile file) {
        return file.getName().endsWith(".yml") || file.getName().endsWith(".yaml");
    }
}

