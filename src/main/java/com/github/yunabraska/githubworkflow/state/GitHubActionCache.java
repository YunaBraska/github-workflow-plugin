package com.github.yunabraska.githubworkflow.state;

import com.github.yunabraska.githubworkflow.entry.ProjectStartup;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.client.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.Application;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getProject;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.toPath;
import static com.github.yunabraska.githubworkflow.model.GitHubAction.createGithubAction;
import static com.github.yunabraska.githubworkflow.model.GitHubAction.findActionYaml;
import static com.github.yunabraska.githubworkflow.entry.ProjectStartup.threadPoolExec;
import static java.util.Optional.ofNullable;

/**
 * Stores resolved action and reusable workflow metadata for editor completion, validation, and documentation.
 */
@SuppressWarnings("UnusedReturnValue")
@State(name = "GitHubActionCache", storages = {@Storage("githubActionCache.xml")})
public class GitHubActionCache implements PersistentStateComponent<GitHubActionCache.State> {

    private static final String DEFAULT_REMOTE_REF = "main";

    /**
     * Serialized cache state persisted by the IntelliJ platform.
     */
    public static class State {
        public final Map<String, GitHubAction> actions = new ConcurrentHashMap<>();
    }

    private final State state = new State();
    private final java.util.Set<String> inFlightResolutions = ConcurrentHashMap.newKeySet();
    private final AtomicReference<ActionResolver> actionResolver = new AtomicReference<>(GitHubAction::resolve);

    /**
     * Strategy used by cache refresh operations to resolve action metadata.
     */
    @FunctionalInterface
    public interface ActionResolver {
        /**
         * Resolves remote or local action metadata for cache refresh operations.
         *
         * @param action action metadata object to resolve
         * @return the resolved action object, usually the same instance
         */
        GitHubAction resolve(GitHubAction action);
    }

    /**
     * Returns the application-wide action metadata cache service.
     *
     * @return action cache service managed by the IDE
     */
    public static GitHubActionCache getActionCache() {
        return ApplicationManager.getApplication().getService(GitHubActionCache.class);
    }

    /**
     * Returns the serialized cache state for IDE persistence.
     *
     * @return current cache state
     */
    @Nullable
    @Override
    public State getState() {
        return this.state;
    }

    /**
     * Loads persisted action metadata into the cache.
     *
     * @param state persisted cache state supplied by the IDE
     */
    @Override
    public void loadState(@NotNull final State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    /**
     * Removes expired cache entries while preserving manually suppressed warnings.
     */
    public void cleanUp() {
        final long currentTime = System.currentTimeMillis();
        new HashMap<>(state.actions).forEach((key, action) -> {
            if (currentTime > action.expiryTime()) {
                if (action.isSuppressed() || !action.ignoredInputs().isEmpty() || !action.ignoredOutputs().isEmpty()) {
                    saveNewAction(ProjectManager.getInstance().getDefaultProject(), action);
                } else {
                    state.actions.remove(key);
                }
            }
        });
    }

    /**
     * Returns cached metadata for a workflow `uses` value, creating and queuing a refresh when needed.
     *
     * @param project project used to resolve local action paths
     * @param usesValue raw `uses` value from workflow YAML
     * @return cached or newly created action metadata
     */
    public GitHubAction get(final Project project, final String usesValue) {
        final String usesCleaned = usesValue.replace("IntellijIdeaRulezzz", "");
        final boolean isLocal = isLocalUses(usesCleaned);
        final String normalizedUses = normalizeUsesValue(usesCleaned, isLocal);
        final String path = getAbsolutePath(isLocal, normalizedUses, project);
        return ofNullable(path)
                .map(absolutePath -> ofNullable(state.actions.get(absolutePath))
                        .or(() -> ofNullable(state.actions.get(normalizedUses)))
                        .or(() -> ofNullable(state.actions.get(usesCleaned)))
                        .orElse(null))
                .map(action -> cachedOrRefresh(normalizedUses, path, isLocal, action))
                .orElseGet(() -> createdOrQueuedRemote(normalizedUses, path, isLocal, null));
    }

    private GitHubAction cachedOrRefresh(final String usesValue, final String path, final boolean isLocal, final GitHubAction action) {
        if (!isLocal && !action.isResolved()) {
            return queueRefresh(action);
        }
        if (System.currentTimeMillis() < action.expiryTime()) {
            return action;
        }
        if (action.isResolved() && !isLocal) {
            queueRefresh(action);
            return action;
        }
        return createdOrQueuedRemote(usesValue, path, isLocal, action);
    }

    private GitHubAction queueRefresh(final GitHubAction action) {
        if (action != null) {
            resolveInBackground(List.of(action));
        }
        return action;
    }

    /**
     * Removes one cache entry by key.
     *
     * @param usesValue cache key or `uses` value to remove
     * @return the same value supplied by the caller for fluent quick-fix use
     */
    public String remove(final String usesValue) {
        ofNullable(usesValue).ifPresent(state.actions::remove);
        return usesValue;
    }

    /**
     * Calculates a compact summary of the current cache.
     *
     * @return cache entry counts grouped by useful settings UI categories
     */
    public CacheSummary summary() {
        final List<GitHubAction> actions = state.actions.values().stream().distinct().toList();
        final long resolved = actions.stream().filter(GitHubAction::isResolved).count();
        final long remote = actions.stream().filter(action -> !action.isLocal()).count();
        final long expired = actions.stream().filter(action -> System.currentTimeMillis() >= action.expiryTime()).count();
        final long suppressed = actions.stream().filter(GitHubAction::hasSuppressedWarnings).count();
        return new CacheSummary(actions.size(), resolved, remote, expired, suppressed);
    }

    /**
     * Returns cache entries sorted for display in settings.
     *
     * @return immutable sorted cache entry list
     */
    public List<CacheEntry> entries() {
        final long now = System.currentTimeMillis();
        return state.actions.entrySet().stream()
                .map(entry -> CacheEntry.from(entry.getKey(), entry.getValue(), now))
                .sorted((left, right) -> left.key().compareToIgnoreCase(right.key()))
                .toList();
    }

    /**
     * Removes all cache entries matching the supplied keys.
     *
     * @param keys cache keys to remove
     * @return cache summary after removal
     */
    public CacheSummary removeAll(final Collection<String> keys) {
        ofNullable(keys).stream()
                .flatMap(Collection::stream)
                .filter(PsiElementHelper::hasText)
                .forEach(state.actions::remove);
        triggerSyntaxHighlightingForActiveFiles();
        return summary();
    }

    /**
     * Estimates serialized cache payload size.
     *
     * @return approximate cache payload size in bytes
     */
    public long estimatedSizeBytes() {
        return state.actions.entrySet().stream()
                .mapToLong(entry -> estimate(entry.getKey()) + estimate(entry.getValue()))
                .sum();
    }

    /**
     * Exports cache metadata to a portable line-based file.
     *
     * @param output target file path
     * @return current cache summary after export
     * @throws IOException when the file cannot be written
     */
    public CacheSummary exportCache(final Path output) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("github-workflow-cache-v1");
            writer.newLine();
            for (final Map.Entry<String, GitHubAction> entry : new LinkedHashMap<>(state.actions).entrySet()) {
                writer.write(encode(entry.getKey()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getMetaData()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getInputs()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getOutputs()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getSecrets()));
                writer.newLine();
            }
        }
        return summary();
    }

    /**
     * Imports cache metadata from a file produced by {@link #exportCache(Path)}.
     *
     * @param input source file path
     * @return cache summary after import
     * @throws IOException when the file cannot be read or decoded
     */
    public CacheSummary importCache(final Path input) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            final String header = reader.readLine();
            if (!"github-workflow-cache-v1".equals(header)) {
                throw new IOException(GitHubWorkflowBundle.message("settings.cache.import.unsupported"));
            }
            String line;
            while ((line = reader.readLine()) != null) {
                final String[] parts = line.split("\\t", -1);
                if (parts.length != 5) {
                    throw new IOException(GitHubWorkflowBundle.message("settings.cache.import.brokenLine"));
                }
                final GitHubAction action = new GitHubAction()
                        .setMetaData(decode(parts[1]))
                        .setInputs(decode(parts[2]))
                        .setOutputs(decode(parts[3]))
                        .setSecrets(decode(parts[4]));
                final String key = decode(parts[0]).getOrDefault("key", "");
                if (key.isBlank()) {
                    throw new IOException(GitHubWorkflowBundle.message("settings.cache.import.brokenKey"));
                }
                state.actions.put(key, action);
            }
        }
        triggerSyntaxHighlightingForActiveFiles();
        return summary();
    }

    /**
     * Clears all cached action metadata and queued refresh markers.
     *
     * @return empty cache summary after clearing
     */
    public CacheSummary clear() {
        state.actions.clear();
        inFlightResolutions.clear();
        triggerSyntaxHighlightingForActiveFiles();
        return summary();
    }

    /**
     * Queues a refresh for all currently resolved remote actions.
     *
     * @return cache summary before the background refresh completes
     */
    public CacheSummary refreshResolvedRemoteActions() {
        final List<GitHubAction> actions = state.actions.values().stream()
                .distinct()
                .filter(GitHubAction::isResolved)
                .filter(action -> !action.isLocal())
                .peek(action -> action.expiryTime(0))
                .toList();
        resolveAsync(actions);
        return summary();
    }

    /**
     * Restores all warnings previously suppressed on cached actions.
     *
     * @return number of action entries whose warnings were restored
     */
    public long restoreWarnings() {
        final List<GitHubAction> suppressedActions = state.actions.values().stream()
                .distinct()
                .filter(GitHubAction::hasSuppressedWarnings)
                .toList();
        suppressedActions.forEach(GitHubAction::restoreWarnings);
        if (!suppressedActions.isEmpty()) {
            triggerSyntaxHighlightingForActiveFiles();
        }
        return suppressedActions.size();
    }

    /**
     * Returns likely refs for a remote action or reusable workflow base reference.
     *
     * @param usesBase remote `owner/repo/path` value without the `@ref` suffix
     * @param limit maximum number of refs to return
     * @return cached or freshly queried refs, newest provider order first
     */
    public List<String> remoteRefsFor(final String usesBase, final int limit) {
        if (usesBase == null || usesBase.isBlank() || limit < 1) {
            return List.of();
        }
        final List<String> cachedRefs = cachedRemoteRefsFor(usesBase, limit);
        if (!cachedRefs.isEmpty()) {
            return cachedRefs;
        }
        final List<String> refs = RemoteActionProviders.latestRefs(usesBase, limit);
        if (!refs.isEmpty()) {
            final GitHubAction action = createGithubAction(false, usesBase + "@" + refs.get(0), usesBase + "@" + refs.get(0))
                    .remoteRefs(refs)
                    .expiryTime(System.currentTimeMillis() + CACHE_ONE_DAY);
            state.actions.put("refs:" + usesBase, action);
        }
        return refs;
    }

    private List<String> cachedRemoteRefsFor(final String usesBase, final int limit) {
        return state.actions.values().stream()
                .distinct()
                .filter(action -> !action.isLocal())
                .filter(action -> remoteBase(action.usesValue()).filter(usesBase::equals).isPresent())
                .flatMap(action -> action.remoteRefs().stream())
                .distinct()
                .limit(limit)
                .toList();
    }

    private static Optional<String> remoteBase(final String usesValue) {
        final int refSeparator = ofNullable(usesValue).orElse("").lastIndexOf('@');
        return refSeparator > 0 ? Optional.of(usesValue.substring(0, refSeparator)) : Optional.empty();
    }

    /**
     * Queues a refresh for one cached action.
     *
     * @param project project used for local path context
     * @param usesValue cache key or `uses` value to reload
     * @return action queued for reload, or null when no matching cache entry exists
     */
    public GitHubAction reloadAsync(final Project project, final String usesValue) {
        return project == null ? null : ofNullable(usesValue)
                .map(state.actions::get)
                .map(oldAction -> saveNewAction(project, oldAction))
                .map(action -> {
                    threadPoolExec(project, () -> {
                        actionResolver.get().resolve(action);
                        triggerSyntaxHighlightingForActiveFiles();
                    });
                    return action;
                })
                .orElse(null);
    }

    /**
     * Resolves action metadata in a visible background task.
     *
     * @param actions action entries to resolve; null and duplicate in-flight entries are ignored
     */
    public void resolveAsync(final Collection<GitHubAction> actions) {
        final List<GitHubAction> queuedActions = queuedActions(actions);
        if (queuedActions.isEmpty()) {
            return;
        }
        new Task.Backgroundable(null, GitHubWorkflowBundle.message("workflow.cache.progress.title"), false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                try {
                    final AtomicInteger index = new AtomicInteger(0);
                    final double totalActions = queuedActions.size();
                    indicator.setIndeterminate(false);
                    queuedActions.forEach(action -> {
                        final int i = index.incrementAndGet();
                        try {
                            indicator.setText(GitHubWorkflowBundle.message(
                                    "workflow.cache.progress.text",
                                    GitHubWorkflowBundle.message(action.isAction() ? "workflow.cache.kind.action" : "workflow.cache.kind.workflow"),
                                    action.name()
                            ));
                            resolveQueuedAction(action);
                        } catch (final Exception ignored) {
                            // Keep the cache stable when a remote action fails to answer.
                        } finally {
                            inFlightResolutions.remove(action.usesValue());
                            indicator.setFraction(i / totalActions);
                        }
                    });
                    triggerSyntaxHighlightingForActiveFiles();
                } catch (final Exception e) {
                    queuedActions.forEach(action -> inFlightResolutions.remove(action.usesValue()));
                    triggerSyntaxHighlightingForActiveFiles();
                    throw e;
                }
            }
        }.queue();
    }

    private void resolveInBackground(final Collection<GitHubAction> actions) {
        final List<GitHubAction> queuedActions = queuedActions(actions);
        if (queuedActions.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            queuedActions.forEach(action -> {
                try {
                    resolveQueuedAction(action);
                } catch (final Exception ignored) {
                    // Automatic refresh must never block editing because a network target misbehaved.
                } finally {
                    inFlightResolutions.remove(action.usesValue());
                }
            });
            triggerSyntaxHighlightingForActiveFiles();
        });
    }

    private List<GitHubAction> queuedActions(final Collection<GitHubAction> actions) {
        return ofNullable(actions).stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(action -> inFlightResolutions.add(action.usesValue()))
                .toList();
    }

    private void resolveQueuedAction(final GitHubAction action) {
        jitterBeforeRemoteRequest(action);
        actionResolver.get().resolve(action);
        if (action.isResolved()) {
            action.expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 14));
        }
    }

    /**
     * Replaces the action resolver used by cache refresh operations.
     *
     * @param resolver replacement resolver, or null to restore the production resolver
     * @return previously configured resolver so tests can restore it
     */
    public ActionResolver useActionResolverForTests(final ActionResolver resolver) {
        return actionResolver.getAndSet(ofNullable(resolver).orElse(GitHubAction::resolve));
    }

    private static void jitterBeforeRemoteRequest(final GitHubAction action) {
        if (action == null || action.isLocal()) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(75, 251));
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Refreshes highlighting for currently selected workflow files in open projects.
     */
    public static void triggerSyntaxHighlightingForActiveFiles() {
        final Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode()) {
            return;
        }
        application.invokeLater(() ->
                Stream.of(ProjectManager.getInstance().getOpenProjects()).forEach(GitHubActionCache::triggerSyntaxHighlightingForActiveFiles)
        );
    }

    private static void triggerSyntaxHighlightingForActiveFiles(final Project project) {
        final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
        final boolean hasActiveWorkflowFile = Stream.of(FileEditorManager.getInstance(project).getSelectedFiles())
                .filter(VirtualFile::isValid)
                .filter(virtualFile -> toPath(virtualFile).map(GitHubWorkflowHelper::isWorkflowPath).orElse(false))
                .map(virtualFile -> PsiManager.getInstance(project).findFile(virtualFile))
                .filter(Objects::nonNull)
                .filter(PsiFile::isValid)
                .anyMatch(daemonCodeAnalyzer::isHighlightingAvailable);
        if (hasActiveWorkflowFile) {
            daemonCodeAnalyzer.settingsChanged();
        }
    }

    /**
     * Resolves action metadata through the shared cache service in the background.
     *
     * @param actions action entries to resolve
     */
    public static void resolveActionsAsync(final Collection<GitHubAction> actions) {
        getActionCache().resolveInBackground(actions);
    }

    /**
     * Queues a reload for one action through the shared cache service.
     *
     * @param project project used for local path context
     * @param usesValue cache key or `uses` value to reload
     * @return action queued for reload, or null when no matching cache entry exists
     */
    public static GitHubAction reloadActionAsync(final Project project, final String usesValue) {
        return getActionCache().reloadAsync(project, usesValue);
    }

    /**
     * Resolves cached action metadata for a PSI element related to a workflow `uses` value.
     *
     * @param psiElement workflow PSI element
     * @return matching action metadata, or null when no `uses` value is available
     */
    public static GitHubAction getAction(final PsiElement psiElement) {
        return getUsesString(psiElement).map(usesValue -> getActionCache().get(getProject(psiElement), usesValue)).orElse(null);
    }

    @SuppressWarnings("unused")
    /**
     * Removes cached metadata referenced by a workflow PSI element.
     *
     * @param psiElement workflow PSI element related to a `uses` value
     * @return the same PSI element for quick-fix chaining
     */
    public static PsiElement removeAction(final PsiElement psiElement) {
        getUsesString(psiElement).ifPresent(GitHubActionCache::removeAction);
        return psiElement;
    }

    /**
     * Removes cached metadata for an action object.
     *
     * @param action action whose download URL is used as cache key
     * @return the same action object for quick-fix chaining
     */
    public static GitHubAction removeAction(final GitHubAction action) {
        ofNullable(action).map(GitHubAction::downloadUrl).ifPresent(GitHubActionCache::removeAction);
        return action;
    }

    /**
     * Removes cached metadata for a raw `uses` value.
     *
     * @param usesValue cache key or raw `uses` value
     * @return the same value supplied by the caller
     */
    public static String removeAction(final String usesValue) {
        ofNullable(usesValue).ifPresent(value -> getActionCache().remove(value));
        return usesValue;
    }

    /**
     * Checks whether a PSI element is a YAML `uses` key/value.
     *
     * @param psiElement workflow PSI element
     * @return matching YAML key/value when the element is a `uses` entry
     */
    public static Optional<YAMLKeyValue> isUseElement(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .filter(PsiElement::isValid)
                .filter(YAMLKeyValue.class::isInstance)
                .map(YAMLKeyValue.class::cast)
                .filter(keyValue -> FIELD_USES.equals(keyValue.getKeyText()));
    }

    private GitHubAction saveNewAction(final Project project, final GitHubAction oldAction) {
        final boolean isLocal = isLocalUses(oldAction.usesValue());
        final String normalizedUses = normalizeUsesValue(oldAction.usesValue(), isLocal);
        return saveNewAction(normalizedUses, getAbsolutePath(isLocal, normalizedUses, project), isLocal, oldAction);
    }

    private GitHubAction createdOrQueuedRemote(final String usesValue, final String path, final boolean isLocal, final GitHubAction oldAction) {
        final GitHubAction action = saveNewAction(usesValue, path, isLocal, oldAction);
        if (!isLocal && action != null && !action.isResolved()) {
            queueRefresh(action);
        }
        return action;
    }

    private GitHubAction saveNewAction(final String usesValue, final String path, final boolean isLocal, final GitHubAction oldAction) {
        return ofNullable(path).map(absolutePath -> {
            final GitHubAction newAction = createGithubAction(isLocal, usesValue, path);

            // RESTORE MANUAL SAVED VALUES
            if (oldAction != null) {
                newAction.isSuppressed(oldAction.isSuppressed());
                oldAction.ignoredInputs().forEach(input -> newAction.suppressInput(input, true));
                oldAction.ignoredOutputs().forEach(input -> newAction.suppressOutput(input, true));
            }
            state.actions.put(absolutePath, newAction);
            return newAction;
        }).orElse(null);
    }

    private String getAbsolutePath(final boolean isLocal, final String subPath, final Project project) {
        return !isLocal ? subPath : ofNullable(project)
                .map(ProjectUtil::guessProjectDir)
                .map(projectDir -> findActionYaml(subPath, projectDir))
                .flatMap(PsiElementHelper::toPath)
                .map(Path::toString)
                .orElse(subPath);
    }

    private static boolean isLocalUses(final String usesValue) {
        final String normalized = ofNullable(usesValue).orElse("").replace('\\', '/').trim();
        return normalized.startsWith("./")
                || normalized.startsWith("../")
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:/.*");
    }

    private static String normalizeUsesValue(final String usesValue, final boolean isLocal) {
        final String normalized = ofNullable(usesValue).orElse("").trim();
        if (isLocal || normalized.contains("@") || normalized.startsWith("docker://") || normalized.isBlank()) {
            return normalized;
        }
        return normalized + "@" + DEFAULT_REMOTE_REF;
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

    private static long estimate(final String value) {
        return value == null ? 0 : value.length() * 2L;
    }

    private static long estimate(final GitHubAction action) {
        return estimate(action.getMetaData()) + estimate(action.getInputs()) + estimate(action.getOutputs()) + estimate(action.getSecrets());
    }

    private static long estimate(final Map<String, String> values) {
        return values.entrySet().stream()
                .mapToLong(entry -> estimate(entry.getKey()) + estimate(entry.getValue()))
                .sum();
    }

    private static String encode(final String key) throws IOException {
        return encode(Map.of("key", key));
    }

    private static String encode(final Map<String, String> values) throws IOException {
        final Properties properties = new Properties();
        properties.putAll(values);
        final StringWriter writer = new StringWriter();
        properties.store(writer, null);
        return Base64.getEncoder().encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> decode(final String value) throws IOException {
        final Properties properties = new Properties();
        properties.load(new StringReader(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)));
        final Map<String, String> result = new LinkedHashMap<>();
        properties.stringPropertyNames().forEach(key -> result.put(key, properties.getProperty(key)));
        return result;
    }

    /**
     * Cache counters displayed in settings and action feedback.
     *
     * @param total number of distinct cache entries
     * @param resolved number of entries with resolved metadata
     * @param remote number of remote entries
     * @param expired number of expired entries
     * @param suppressed number of entries with suppressed warnings
     */
    public record CacheSummary(long total, long resolved, long remote, long expired, long suppressed) {
    }

    /**
     * Cache row displayed in the settings cache table.
     *
     * @param key persisted cache key
     * @param name human-readable action name
     * @param usesValue workflow `uses` value
     * @param local true when the entry points at a local action
     * @param resolved true when remote metadata is available
     * @param expired true when the entry should be refreshed
     * @param suppressed true when warnings are hidden for this entry
     * @param expiryTime epoch millis when this cache entry expires
     */
    public record CacheEntry(
            String key,
            String name,
            String usesValue,
            boolean local,
            boolean resolved,
            boolean expired,
            boolean suppressed,
            long expiryTime
    ) {
        static CacheEntry from(final String key, final GitHubAction action, final long now) {
            return new CacheEntry(
                    key,
                    action.displayName().isBlank() ? action.name() : action.displayName(),
                    action.usesValue(),
                    action.isLocal(),
                    action.isResolved(),
                    now >= action.expiryTime(),
                    action.hasSuppressedWarnings(),
                    action.expiryTime()
            );
        }
    }
}
