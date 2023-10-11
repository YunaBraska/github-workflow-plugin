package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getProject;
import static com.github.yunabraska.githubworkflow.model.GitHubAction.createGithubAction;
import static com.github.yunabraska.githubworkflow.model.GitHubAction.findActionYaml;
import static java.util.Optional.ofNullable;

@SuppressWarnings("UnusedReturnValue")
@State(name = "GitHubActionCache", storages = {@Storage("githubActionCache.xml")})
public class GitHubActionCache implements PersistentStateComponent<GitHubActionCache.State> {

    public static class State {
        public final Map<String, GitHubAction> actions = new ConcurrentHashMap<>();
    }

    private final State state = new State();

    public static GitHubActionCache getActionCache() {
        return ApplicationManager.getApplication().getService(GitHubActionCache.class);
    }

    @Nullable
    @Override
    public State getState() {
        return this.state;
    }

    @Override
    public void loadState(@NotNull final State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public void cleanUp() {
        final long currentTime = System.currentTimeMillis();
        state.actions.entrySet().removeIf(entry -> currentTime > entry.getValue().expiryTime()
                || isUnresolvedAndOlderThen30Min(entry, currentTime)
        );
    }

    protected GitHubAction get(final Project project, final String usesValue) {
        final String usesCleaned = usesValue.replace("IntellijIdeaRulezzz", "");
        final boolean isLocal = !usesCleaned.contains("@");
        final String path = getAbsolutePath(isLocal, usesCleaned, project);
        return ofNullable(path)
                .map(state.actions::get)
                .filter(action -> !action.isSchema())
                .filter(action -> System.currentTimeMillis() < action.expiryTime())
                .orElseGet(() -> {
                    final GitHubAction result = createGithubAction(isLocal, usesCleaned, path);
                    state.actions.put(path, result);
                    return result;
                });
    }

    public boolean isAvailable(final String key) {
        return state.actions.containsKey(key);
    }

    public String remove(final String usesValue) {
        ofNullable(usesValue).ifPresent(state.actions::remove);
        return usesValue;
    }

    public GitHubAction reloadAsync(final Project project, final String usesValue) {
        return project == null ? null : ofNullable(usesValue)
                .map(this::remove)
                .map(uses -> get(project, uses))
                .map(action -> {
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        action.resolve();
                        triggerSyntaxHighlightingForActiveFiles();
                    });
                    return action;
                })
                .orElse(null);
    }

    // !!! Performs Network and File Operations !!!
    public void resolveAsync(final Collection<GitHubAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        new Task.Backgroundable(null, "Resolving github actions", false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                try {
                    final AtomicInteger index = new AtomicInteger(0);
                    final double totalActions = actions.size();
                    indicator.setIndeterminate(false);
                    actions.forEach(action -> {
                        final int i = index.incrementAndGet();
                        action.resolve();
                        indicator.setFraction(i / totalActions);
                        indicator.setText("Resolving " + (action.isAction() ? "action" : "workflow") + action.name());
                    });
                    triggerSyntaxHighlightingForActiveFiles();
                } catch (final Exception e) {
                    // Proceed action even on issues within the progress bar
                    state.actions.values().forEach(GitHubActionCache::removeAction);
                    triggerSyntaxHighlightingForActiveFiles();
                    throw e;
                }
            }
        }.queue();
    }

    public static void triggerSyntaxHighlightingForActiveFiles() {
        ApplicationManager.getApplication().invokeLater(() ->
                Stream.of(ProjectManager.getInstance().getOpenProjects()).forEach(project -> Stream.of(FileEditorManager.getInstance(project).getSelectedFiles()).filter(VirtualFile::isValid)
                        .filter(virtualFile -> Optional.of(virtualFile.getPath()).map(Paths::get).map(GitHubWorkflowHelper::isWorkflowPath).orElse(false))
                        .forEach(virtualFile -> ofNullable(PsiManager.getInstance(project).findFile(virtualFile))
                                .filter(PsiFile::isValid)
                                .ifPresent(psiFile -> DaemonCodeAnalyzer.getInstance(project).restart(psiFile)))
                )
        );
    }

    public static void resolveActionsAsync(final Collection<GitHubAction> actions) {
        getActionCache().resolveAsync(actions);
    }

    public static GitHubAction reloadActionAsync(final Project project, final String usesValue) {
        return getActionCache().reloadAsync(project, usesValue);
    }

    public static GitHubAction getAction(final PsiElement psiElement) {
        return getUsesString(psiElement).map(usesValue -> getActionCache().get(getProject(psiElement), usesValue)).orElse(null);
    }

    @SuppressWarnings("unused")
    public static PsiElement removeAction(final PsiElement psiElement) {
        getUsesString(psiElement).ifPresent(GitHubActionCache::removeAction);
        return psiElement;
    }

    public static GitHubAction removeAction(final GitHubAction action) {
        ofNullable(action).map(GitHubAction::downloadUrl).ifPresent(GitHubActionCache::removeAction);
        return action;
    }

    public static String removeAction(final String usesValue) {
        ofNullable(usesValue).ifPresent(value -> getActionCache().remove(value));
        return usesValue;
    }

    public static Optional<YAMLKeyValue> isUseElement(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .filter(PsiElement::isValid)
                .filter(YAMLKeyValue.class::isInstance)
                .map(YAMLKeyValue.class::cast)
                .filter(keyValue -> FIELD_USES.equals(keyValue.getKeyText()));
    }

    private String getAbsolutePath(final boolean isLocal, final String subPath, final Project project) {
        return !isLocal ? subPath : ofNullable(project)
                .map(ProjectUtil::guessProjectDir)
                .map(projectDir -> findActionYaml(subPath, projectDir))
                .map(VirtualFile::getPath)
                .map(Paths::get)
                .map(Path::toString)
                .orElse(subPath);
    }

    private static Optional<String> getUsesString(final PsiElement psiElement) {
        return ofNullable(psiElement).filter(PsiElement::isValid)
                .flatMap(GitHubActionCache::getUsesValue)
                .or(() -> getChildWithUsesValue(psiElement));
    }

    private static Optional<String> getUsesValue(final PsiElement psiElement) {
        return isUseElement(psiElement).flatMap(PsiElementHelper::getText);
    }

    private static Optional<String> getChildWithUsesValue(final PsiElement psiElement) {
        return ofNullable(psiElement).filter(PsiElement::isValid).flatMap(element -> PsiElementHelper.getChild(element, FIELD_USES)).flatMap(PsiElementHelper::getText);
    }

    private static boolean isUnresolvedAndOlderThen30Min(final Map.Entry<String, GitHubAction> entry, final long currentTime) {
        final long time = currentTime + ((CACHE_ONE_DAY * 14) - 30);
        return !entry.getValue().isResolved() && !entry.getValue().isSuppressed() && time > entry.getValue().expiryTime();
    }

}
