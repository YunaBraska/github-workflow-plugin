package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.syntax.WorkflowSyntax;

import com.github.yunabraska.githubworkflow.git.WorkflowLocation;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.intellij.codeInsight.AutoPopupController;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.Steps;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.*;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getStartIndex;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.isActionFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.isWorkflowFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.toLookupElement;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getProject;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.syntax.Envs.listEnvs;
import static com.github.yunabraska.githubworkflow.syntax.Inputs.listInputs;
import static com.github.yunabraska.githubworkflow.syntax.JobContext.codeCompletionJob;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.codeCompletionJobs;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.listJobs;
import static com.github.yunabraska.githubworkflow.syntax.Matrix.listMatrix;
import static com.github.yunabraska.githubworkflow.syntax.Needs.codeCompletionNeeds;
import static com.github.yunabraska.githubworkflow.syntax.Needs.codeCompletionPreviousJobs;
import static com.github.yunabraska.githubworkflow.syntax.Needs.listJobNeeds;
import static com.github.yunabraska.githubworkflow.syntax.Secrets.listSecrets;
import static com.github.yunabraska.githubworkflow.syntax.Steps.codeCompletionSteps;
import static com.github.yunabraska.githubworkflow.syntax.Steps.listSteps;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_RUNNER;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isChildOf;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.currentLineKey;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isValueCompletion;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.keyContextAt;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathEndsWith;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathMatches;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public final class WorkflowCompletion extends CompletionContributor {

    private static final Pattern REMOTE_USES_REF_PATTERN = Pattern.compile(".*\\buses\\s*:\\s*['\"]?([^\\s'\"#]+)@([^\\s'\"]*)$");
    private static final Pattern REMOTE_USES_TARGET_PATTERN = Pattern.compile(".*\\buses\\s*:\\s*['\"]?([^\\s'\"#@]*)$");

    public WorkflowCompletion() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    static boolean workflowCompletionTrigger(final char typeChar) {
        return switch (typeChar) {
            case '\n', ':', '.', '@' -> true;
            default -> false;
        };
    }

    @NotNull
    private static CompletionProvider<CompletionParameters> completionProvider() {
        return new CompletionProvider<>() {
            @Override
            public void addCompletions(
                    @NotNull final CompletionParameters parameters,
                    @NotNull final ProcessingContext processingContext,
                    @NotNull final CompletionResultSet resultSet
            ) {
                completionTrigger(parameters, resultSet).ifPresent(WorkflowCompletion::complete);
            }
        };
    }

    private static Optional<CompletionTrigger> completionTrigger(final CompletionParameters parameters, final CompletionResultSet resultSet) {
        final CompletionPsi completionPsi = completionPsi(parameters);
        final PsiElement position = completionPsi.position();
        if (getWorkflowFile(position).isEmpty()) {
            return Optional.empty();
        }
        final String[] prefix = new String[]{""};
        final Optional<String[]> caretBracketItem = completionPsi.offset() < 1
                ? Optional.of(prefix)
                : getCaretBracketItem(position, completionPsi.offset(), prefix);
        return Optional.of(new CompletionTrigger(
                parameters,
                resultSet,
                completionPsi,
                position,
                caretBracketItem,
                prefix[0]
        ));
    }

    private static void complete(final CompletionTrigger trigger) {
        if (completeWorkflowValue(trigger)
                || completeExpressionPath(trigger)
                || completeRunEnvironment(trigger)
                || stopInsideExecutableRun(trigger)
                || completeNeeds(trigger)
                || completeUses(trigger)
                || completeShell(trigger)
                || completeWorkflowStructure(trigger)
                || completeCallableSecrets(trigger)) {
            return;
        }
        completeCallableInputs(trigger);
    }

    private static boolean completeWorkflowValue(final CompletionTrigger trigger) {
        return workflowValueCompletion(trigger.completionPsi())
                .map(completion -> addStructureCompletion(trigger, completion, getDefaultPrefix(trigger.completionPsi())))
                .orElse(false);
    }

    private static boolean completeExpressionPath(final CompletionTrigger trigger) {
        return trigger.caretBracketItem()
                .map(cbi -> {
                    addWorkflowCompletionItems(trigger.resultSet(), cbi, trigger.position(), trigger.prefix());
                    return true;
                })
                .orElse(false);
    }

    private static boolean completeRunEnvironment(final CompletionTrigger trigger) {
        if (!isCompletingRunEnvironmentVariable(trigger.completionPsi())) {
            return false;
        }
        final Map<String, String> defaults = ofNullable(DEFAULT_VALUE_MAP.get(FIELD_ENVS))
                .map(Supplier::get)
                .orElseGet(Collections::emptyMap);
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(trigger.prefix()),
                defaults,
                NodeIcon.ICON_ENV,
                Character.MIN_VALUE
        );
        return true;
    }

    private static boolean stopInsideExecutableRun(final CompletionTrigger trigger) {
        return isInsideExecutableRunField(trigger.position());
    }

    private static boolean completeNeeds(final CompletionTrigger trigger) {
        if (!isCompletingNeedsField(trigger.parameters(), trigger.position())) {
            return false;
        }
        Optional.of(codeCompletionPreviousJobs(trigger.position()))
                .filter(cil -> !cil.isEmpty())
                .map(WorkflowCompletion::toLookupItems)
                .ifPresent(lookupElements -> addElementsWithPrefix(
                        trigger.resultSet(),
                        getDefaultPrefix(trigger.parameters()),
                        lookupElements
                ));
        return true;
    }

    private static boolean completeUses(final CompletionTrigger trigger) {
        if (!isCompletingUsesField(trigger.parameters(), trigger.position())) {
            return false;
        }
        final Optional<RemoteUsesRef> remoteUsesRef = remoteUsesRef(trigger.parameters());
        if (remoteUsesRef.isPresent()) {
            final RemoteUsesRef ref = remoteUsesRef.get();
            addLookupElements(
                    trigger.resultSet().withPrefixMatcher(ref.prefix()),
                    knownRemoteRefs(trigger.position(), ref.usesBase()),
                    NodeIcon.ICON_NODE,
                    Character.MIN_VALUE
            );
            return true;
        }
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                callableUsesCompletions(trigger.position(), remoteUsesTargetPrefix(trigger.parameters()).orElse("")),
                NodeIcon.ICON_NODE,
                Character.MIN_VALUE
        );
        return true;
    }

    private static boolean completeShell(final CompletionTrigger trigger) {
        if (!isCompletingShellField(trigger.parameters(), trigger.position())) {
            return false;
        }
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                SHELLS,
                NodeIcon.ICON_NODE,
                Character.MIN_VALUE
        );
        return true;
    }

    private static boolean completeWorkflowStructure(final CompletionTrigger trigger) {
        return workflowStructureCompletion(trigger.completionPsi())
                .map(completion -> addStructureCompletion(trigger, completion, getDefaultPrefix(trigger.completionPsi())))
                .orElse(false);
    }

    private static boolean completeCallableSecrets(final CompletionTrigger trigger) {
        if (!isCompletingCallableSecrets(trigger.position())) {
            return false;
        }
        currentCallableAction(trigger.parameters(), trigger.position())
                .map(GitHubAction::freshSecrets)
                .ifPresent(map -> addLookupElements(
                        trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                        map,
                        NodeIcon.ICON_SECRET_WORKFLOW,
                        ':'
                ));
        return true;
    }

    private static boolean completeCallableInputs(final CompletionTrigger trigger) {
        currentCallableAction(trigger.parameters(), trigger.position())
                .filter(GitHubAction::isResolved)
                .map(GitHubAction::freshInputs)
                .ifPresent(map -> addLookupElements(
                        trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                        map,
                        NodeIcon.ICON_INPUT,
                        ':'
                ));
        return true;
    }

    private static boolean addStructureCompletion(
            final CompletionTrigger trigger,
            final StructureCompletion completion,
            final String prefix
    ) {
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(prefix),
                completion.items(),
                ICON_NODE,
                completion.suffix()
        );
        return true;
    }

    private static CompletionPsi completionPsi(final CompletionParameters parameters) {
        final PsiElement position = parameters.getPosition();
        final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(position.getProject());
        final PsiLanguageInjectionHost host = injectionManager.getInjectionHost(position);
        return host == null
                ? new CompletionPsi(position, parameters.getOffset())
                : new CompletionPsi(host, injectionManager.injectedToHost(position, parameters.getOffset()));
    }

    private static boolean isCompletingCallableSecrets(final PsiElement position) {
        return getParent(position, FIELD_SECRETS)
                .filter(secrets -> getParent(secrets, FIELD_ON).isEmpty())
                .isPresent();
    }

    private static boolean isCompletingUsesField(final CompletionParameters parameters, final PsiElement position) {
        if (getParent(position, FIELD_USES).isPresent()) {
            return true;
        }
        final String wholeText = parameters.getOriginalFile().getText();
        final String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        return beforeCaret.matches("\\s*-?\\s*" + FIELD_USES + "\\s*:\\s*.*");
    }

    private static boolean isCompletingShellField(final CompletionParameters parameters, final PsiElement position) {
        if (getParent(position, FIELD_SHELL).isPresent()) {
            return true;
        }
        final String wholeText = parameters.getOriginalFile().getText();
        final String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        return beforeCaret.matches("\\s*" + FIELD_SHELL + "\\s*:\\s*.*");
    }

    private static boolean isCompletingRunEnvironmentVariable(final CompletionPsi completionPsi) {
        if (!isInsideExecutableRunField(completionPsi.position())) {
            return false;
        }
        final String wholeText = completionPsi.position().getContainingFile().getText();
        final String beforeCaret = lineBeforeCaret(wholeText, completionPsi.offset());
        return beforeCaret.matches(".*\\$[A-Za-z0-9_]*$");
    }

    private static boolean isInsideExecutableRunField(final PsiElement position) {
        return getParent(position, FIELD_RUN)
                .filter(run -> getParent(run, "defaults").isEmpty())
                .isPresent();
    }

    private static Optional<StructureCompletion> workflowStructureCompletion(final CompletionPsi completionPsi) {
        final Optional<WorkflowLocation.KeyContext> context = keyContextAt(completionPsi.position(), completionPsi.offset());
        if (context.isEmpty() || isValueCompletion(context.get().currentLine())) {
            return Optional.empty();
        }
        final List<String> path = context.get().path();
        final Optional<Map<String, String>> keys = WorkflowSyntax.completionKeysForPath(path);
        if (keys.isPresent()) {
            return Optional.of(new StructureCompletion(keys.get(), ':'));
        }
        if (pathMatches(path, FIELD_ON, "*", "types")) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.eventActivityTypesFor(path.get(1)), Character.MIN_VALUE));
        }
        if (pathMatches(path, FIELD_ON, "*", "*")) {
            return workflowEventFilterValueCompletion(completionPsi, context.get());
        }
        return Optional.empty();
    }

    private static Optional<StructureCompletion> workflowValueCompletion(final CompletionPsi completionPsi) {
        final Optional<WorkflowLocation.KeyContext> context = keyContextAt(completionPsi.position(), completionPsi.offset());
        if (context.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> key = currentLineKey(context.get().currentLine());
        if (key.isEmpty()) {
            return Optional.empty();
        }
        if (context.get().currentLine().contains("${{")) {
            return Optional.empty();
        }
        final List<String> path = context.get().path();
        final String currentKey = key.get();
        final Optional<StructureCompletion> eventFilterValueCompletion = workflowEventFilterValueCompletion(completionPsi, context.get());
        if (eventFilterValueCompletion.isPresent()) {
            return eventFilterValueCompletion;
        }
        if ("runs-on".equals(currentKey)) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.runnerLabels(), Character.MIN_VALUE));
        }
        if ("permissions".equals(currentKey)) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.permissionShorthandValues(), Character.MIN_VALUE));
        }
        if ("types".equals(currentKey) && pathMatches(path, FIELD_ON, "*")) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.eventActivityTypesFor(path.get(1)), Character.MIN_VALUE));
        }
        if ("type".equals(currentKey)
                && (isChildOf(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                || isChildOf(path, FIELD_ON, "workflow_call", FIELD_INPUTS))) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.workflowInputTypesFor(path.get(1)), Character.MIN_VALUE));
        }
        if (pathEndsWith(path, "permissions")) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.permissionValuesFor(currentKey), Character.MIN_VALUE));
        }
        if ("required".equals(currentKey)
                || "continue-on-error".equals(currentKey)
                || "fail-fast".equals(currentKey)
                || "cancel-in-progress".equals(currentKey)) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.booleanValues(), Character.MIN_VALUE));
        }
        return Optional.empty();
    }

    private static Optional<StructureCompletion> workflowEventFilterValueCompletion(final CompletionPsi completionPsi, final WorkflowLocation.KeyContext context) {
        return eventFilterContext(context)
                .map(eventFilter -> eventFilterValueCompletions(completionPsi.position(), eventFilter.event(), eventFilter.filter()))
                .filter(values -> !values.isEmpty())
                .map(values -> new StructureCompletion(values, Character.MIN_VALUE));
    }

    private static Optional<EventFilterContext> eventFilterContext(final WorkflowLocation.KeyContext context) {
        final List<String> path = context.path();
        final Optional<String> currentKey = currentLineKey(context.currentLine());
        if (currentKey.isPresent() && pathMatches(path, FIELD_ON, "*")) {
            final String event = path.get(1);
            final String filter = currentKey.get();
            return WorkflowSyntax.eventFilterKeysFor(event).containsKey(filter)
                    ? Optional.of(new EventFilterContext(event, filter))
                    : Optional.empty();
        }
        if (pathMatches(path, FIELD_ON, "*", "*")) {
            final String event = path.get(1);
            final String filter = path.get(2);
            return WorkflowSyntax.eventFilterKeysFor(event).containsKey(filter)
                    ? Optional.of(new EventFilterContext(event, filter))
                    : Optional.empty();
        }
        return Optional.empty();
    }

    private static Map<String, String> eventFilterValueCompletions(final PsiElement position, final String event, final String filter) {
        return switch (filter) {
            case "types" -> WorkflowSyntax.eventActivityTypesFor(event);
            case "branches", "branches-ignore" -> localGitRefs(position, "heads", "completion.workflow.eventFilter.branches");
            case "tags", "tags-ignore" -> localGitRefs(position, "tags", "completion.workflow.eventFilter.tags");
            case "paths", "paths-ignore" -> localProjectPaths(position);
            default -> Collections.emptyMap();
        };
    }

    private static Optional<RemoteUsesRef> remoteUsesRef(final CompletionParameters parameters) {
        final String wholeText = parameters.getOriginalFile().getText();
        final String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        final Matcher matcher = REMOTE_USES_REF_PATTERN.matcher(beforeCaret);
        return matcher.matches()
                ? Optional.of(new RemoteUsesRef(matcher.group(1), matcher.group(2)))
                : Optional.empty();
    }

    private static Optional<String> remoteUsesTargetPrefix(final CompletionParameters parameters) {
        final String wholeText = parameters.getOriginalFile().getText();
        final String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        final Matcher matcher = REMOTE_USES_TARGET_PATTERN.matcher(beforeCaret);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Map<String, String> callableUsesCompletions(final PsiElement position, final String usesPrefix) {
        final Map<String, String> result = new LinkedHashMap<>(localUsesCompletions(position));
        knownRemoteUses(position).forEach((usesBase, description) -> result.putIfAbsent(usesBase, description));
        RemoteActionProviders.searchUses(usesPrefix, 10).forEach(result::putIfAbsent);
        return result;
    }

    private static Map<String, String> localUsesCompletions(final PsiElement position) {
        final Project project = getProject(position);
        if (project == null) {
            return Collections.emptyMap();
        }
        final boolean reusableWorkflowUse = isReusableWorkflowUse(position);
        final VirtualFile currentFile = position.getContainingFile().getVirtualFile();
        final Map<String, String> result = new LinkedHashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            toLocalUsesValue(project, currentFile, file, reusableWorkflowUse)
                    .ifPresent(value -> result.putIfAbsent(value, GitHubWorkflowBundle.message(reusableWorkflowUse
                            ? "completion.uses.local.workflow"
                            : "completion.uses.local.action")));
            return true;
        });
        return result;
    }

    private static Map<String, String> localProjectPaths(final PsiElement position) {
        final Project project = getProject(position);
        if (project == null) {
            return Collections.emptyMap();
        }
        final Map<String, String> result = new LinkedHashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            if (!file.isDirectory() && !file.getPath().contains("/.git/")) {
                final VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
                if (contentRoot != null) {
                    final String path = Path.of(contentRoot.getPath()).relativize(Path.of(file.getPath())).toString().replace('\\', '/');
                    if (!path.isBlank()) {
                        result.putIfAbsent(path, GitHubWorkflowBundle.message("completion.workflow.eventFilter.paths"));
                    }
                }
            }
            return result.size() < 200;
        });
        return result;
    }

    private static Map<String, String> localGitRefs(final PsiElement position, final String namespace, final String descriptionKey) {
        final Map<String, String> result = new LinkedHashMap<>();
        repositoryRoot(position)
                .flatMap(WorkflowLocation.RepositoryResolver::gitDir)
                .ifPresent(gitDir -> {
                    readLooseRefs(gitDir.resolve("refs").resolve(namespace), result, descriptionKey);
                    readPackedRefs(gitDir.resolve("packed-refs"), "refs/" + namespace + "/", result, descriptionKey);
                });
        return result;
    }

    private static void readLooseRefs(final Path refRoot, final Map<String, String> result, final String descriptionKey) {
        if (!Files.isDirectory(refRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(refRoot)) {
            paths.filter(Files::isRegularFile)
                    .map(refRoot::relativize)
                    .map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .filter(value -> !value.isBlank())
                    .sorted()
                    .forEach(ref -> result.putIfAbsent(ref, GitHubWorkflowBundle.message(descriptionKey)));
        } catch (final IOException ignored) {
            // Local ref completion is opportunistic.
        }
    }

    private static void readPackedRefs(final Path packedRefs, final String prefix, final Map<String, String> result, final String descriptionKey) {
        if (!Files.isRegularFile(packedRefs)) {
            return;
        }
        try (Stream<String> lines = Files.lines(packedRefs)) {
            lines.map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("^"))
                    .map(line -> line.split("\\s+", 2))
                    .filter(parts -> parts.length == 2 && parts[1].startsWith(prefix))
                    .map(parts -> parts[1].substring(prefix.length()))
                    .filter(value -> !value.isBlank())
                    .forEach(ref -> result.putIfAbsent(ref, GitHubWorkflowBundle.message(descriptionKey)));
        } catch (final IOException ignored) {
            // Local ref completion is opportunistic.
        }
    }

    private static Optional<Path> repositoryRoot(final PsiElement position) {
        Path current = ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> file.getVirtualFile())
                .map(VirtualFile::getPath)
                .map(Path::of)
                .map(Path::getParent)
                .orElse(null);
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git")) || Files.isRegularFile(current.resolve(".git"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return ofNullable(getProject(position))
                .map(Project::getBasePath)
                .map(Path::of)
                .filter(path -> Files.isDirectory(path.resolve(".git")) || Files.isRegularFile(path.resolve(".git")));
    }

    private static Map<String, String> knownRemoteRefs(final PsiElement position, final String usesBase) {
        final Map<String, String> result = new LinkedHashMap<>();
        knownRemoteActions(position).stream()
                .filter(action -> splitRemoteUses(action.usesValue()).map(uses -> usesBase.equals(uses.base())).orElse(false))
                .flatMap(action -> action.remoteRefs().stream())
                .forEach(ref -> result.putIfAbsent(ref, GitHubWorkflowBundle.message("completion.uses.ref.known")));
        knownRemoteUsesValues(position).stream()
                .map(WorkflowCompletion::splitRemoteUses)
                .flatMap(Optional::stream)
                .filter(uses -> usesBase.equals(uses.base()))
                .forEach(uses -> result.putIfAbsent(uses.ref(), GitHubWorkflowBundle.message("completion.uses.ref.known")));
        GitHubActionCache.getActionCache().remoteRefsFor(usesBase, 10)
                .forEach(ref -> result.putIfAbsent(ref, GitHubWorkflowBundle.message("completion.uses.ref.remote")));
        return result;
    }

    private static Map<String, String> knownRemoteUses(final PsiElement position) {
        final Map<String, String> result = new LinkedHashMap<>();
        knownRemoteUsesValues(position).stream()
                .map(WorkflowCompletion::splitRemoteUses)
                .flatMap(Optional::stream)
                .forEach(uses -> result.putIfAbsent(uses.base(), GitHubWorkflowBundle.message("completion.uses.remote.known")));
        return result;
    }

    private static List<String> knownRemoteUsesValues(final PsiElement position) {
        return Stream.concat(
                        knownRemoteActions(position).stream().map(GitHubAction::usesValue),
                        getAllElements(position.getContainingFile(), FIELD_USES).stream()
                                .map(WorkflowPsi::getText)
                                .flatMap(Optional::stream)
                )
                .filter(uses -> uses.contains("@") && !uses.startsWith("."))
                .distinct()
                .toList();
    }

    private static List<GitHubAction> knownRemoteActions(final PsiElement position) {
        return ofNullable(GitHubActionCache.getActionCache().getState())
                .stream()
                .flatMap(state -> state.actions.values().stream())
                .filter(action -> action.usesValue().contains("@") && !action.usesValue().startsWith("."))
                .distinct()
                .toList();
    }

    private static Optional<RemoteUses> splitRemoteUses(final String usesValue) {
        final int refSeparator = usesValue.lastIndexOf('@');
        if (refSeparator < 1 || refSeparator == usesValue.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new RemoteUses(
                usesValue.substring(0, refSeparator),
                usesValue.substring(refSeparator + 1)
        ));
    }

    private static boolean isReusableWorkflowUse(final PsiElement position) {
        return getParentStep(position).isEmpty() && currentJob(position).isPresent();
    }

    private static Optional<String> toLocalUsesValue(
            final Project project,
            final VirtualFile currentFile,
            final VirtualFile file,
            final boolean reusableWorkflowUse
    ) {
        if (file == null || file.isDirectory() || file.equals(currentFile)) {
            return Optional.empty();
        }
        final VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
        if (contentRoot == null) {
            return Optional.empty();
        }
        final Path basePath = Path.of(contentRoot.getPath());
        final Path path = Path.of(file.getPath());
        if (reusableWorkflowUse && isWorkflowFile(path)) {
            return Optional.of("./" + basePath.relativize(path));
        }
        if (!reusableWorkflowUse && isActionFile(path)) {
            final Path actionDirectory = path.getParent();
            final Path relative = basePath.relativize(actionDirectory);
            return Optional.of(relative.toString().isEmpty() ? "./" : "./" + relative);
        }
        return Optional.empty();
    }

    private static Optional<GitHubAction> currentCallableAction(final CompletionParameters parameters, final PsiElement position) {
        return currentCallable(position)
                .map(GitHubActionCache::getAction)
                .filter(GitHubAction::isResolved)
                .or(() -> nearestPreviousUsesValue(parameters)
                        .map(usesValue -> GitHubActionCache.getActionCache().get(getProject(position), usesValue)));
    }

    private static Optional<YAMLKeyValue> currentCallable(final PsiElement position) {
        return getParent(position, FIELD_WITH)
                .flatMap(with -> getParentStepOrJob(with)
                        .flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES))
                )
                .or(() -> currentStep(position).flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES)))
                .or(() -> currentJob(position).flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES)))
                .or(() -> currentStepOrJob(position).flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES)));
    }

    private static Optional<String> nearestPreviousUsesValue(final CompletionParameters parameters) {
        final String wholeText = parameters.getOriginalFile().getText();
        final int offset = WorkflowLocation.boundedOffset(wholeText, parameters.getOffset());
        final int lineStart = WorkflowLocation.currentLineStart(wholeText, offset);
        final String beforeCaret = wholeText.substring(0, Math.min(lineStart, wholeText.length()));
        final String[] lines = beforeCaret.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            final String line = lines[index];
            final String trimmed = line.trim();
            if (trimmed.startsWith(FIELD_USES + ":")) {
                return Optional.of(trimmed.substring((FIELD_USES + ":").length()).trim())
                        .map(WorkflowPsi::removeQuotes)
                        .filter(WorkflowPsi::hasText);
            }
        }
        return Optional.empty();
    }

    private static void addWorkflowCompletionItems(final CompletionResultSet resultSet, final String[] cbi, final PsiElement position, final String prefix) {
        final Map<Integer, List<SimpleElement>> completionResultMap = new HashMap<>();
        for (int i = 0; i < cbi.length; i++) {
            //DON'T AUTO COMPLETE WHEN PREVIOUS ITEM IS NOT VALID
            final List<SimpleElement> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
            final int index = i;
            if (i != 0 && !previousCompletions.isEmpty() && previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index]))) {
                return;
            } else {
                addCompletionItems(cbi, i, position, completionResultMap);
            }
        }
        //ADD LOOKUP ELEMENTS
        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                .map(WorkflowCompletion::toLookupItems)
                .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, prefix, lookupElements));
    }

    private static void addElementsWithPrefix(final CompletionResultSet resultSet, final String prefix, final List<LookupElement> lookupElements) {
        resultSet.withPrefixMatcher(new CamelHumpMatcher(prefix)).addAllElements(lookupElements);
    }

    private static void addCompletionItems(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        if (i == 0) {
            handleFirstItem(cbi, i, position, completionItemMap);
        } else if (i == 1) {
            handleSecondItem(cbi, i, position, completionItemMap);
        } else if (i == 2) {
            handleThirdItem(cbi, i, position, completionItemMap);
        } else if (i == 3) {
            handleFourthItem(cbi, i, position, completionItemMap);
        }
    }

    private static boolean isCompletingNeedsField(final CompletionParameters parameters, final PsiElement position) {
        if (getParent(position, FIELD_NEEDS).isPresent()) {
            return true;
        }
        final String wholeText = parameters.getOriginalFile().getText();
        final String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        return beforeCaret.matches("\\s*" + FIELD_NEEDS + "\\s*:\\s*.*");
    }

    private static Optional<PsiElement> currentStepOrJob(final PsiElement position) {
        return getParentStepOrJob(position)
                .or(() -> currentStep(position).map(PsiElement.class::cast))
                .or(() -> currentJob(position).map(PsiElement.class::cast));
    }

    private static Optional<YAMLSequenceItem> currentStep(final PsiElement position) {
        return ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> currentOrPrevious(position, getAllElements(file, FIELD_STEPS).stream().flatMap(steps -> getChildSteps(steps).stream()))
                        .findFirst()
                        .orElse(null));
    }

    private static Optional<YAMLKeyValue> currentJob(final PsiElement position) {
        return ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> currentOrPrevious(position, getAllElements(file, FIELD_JOBS).stream().flatMap(jobs -> WorkflowPsi.getChildren(jobs, YAMLKeyValue.class).stream()))
                        .findFirst()
                        .orElse(null));
    }

    private static <T extends PsiElement> Stream<T> currentOrPrevious(final PsiElement position, final Stream<T> candidates) {
        final int offset = ofNullable(position.getTextRange()).map(TextRange::getStartOffset).orElse(-1);
        return candidates
                .filter(candidate -> containsOffset(candidate, position) || candidate.getTextRange().getStartOffset() <= offset)
                .reduce((previous, current) -> containsOffset(previous, position) ? previous : current)
                .stream();
    }

    private static boolean containsOffset(final PsiElement candidate, final PsiElement position) {
        final TextRange candidateRange = candidate.getTextRange();
        final TextRange positionRange = position.getTextRange();
        return candidateRange != null && positionRange != null && candidateRange.contains(positionRange.getStartOffset());
    }

    private static void handleThirdItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_JOBS -> completionItemMap.put(i, codeCompletionJobs(cbi[1], position));
            case FIELD_NEEDS -> completionItemMap.put(i, codeCompletionNeeds(cbi[1], position));
            case FIELD_STEPS -> completionItemMap.put(i, Steps.codeCompletionSteps(cbi[1], position));
            case FIELD_JOB -> completionItemMap.put(i, codeCompletionJob(cbi[1], cbi[2], position));
            default -> {
                // ignored
            }
        }
    }

    private static void handleFourthItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        if (FIELD_JOB.equals(cbi[0])) {
            completionItemMap.put(i, codeCompletionJob(cbi[1], cbi[2], cbi[3], position));
        } else if (FIELD_NEEDS.equals(cbi[0]) && FIELD_OUTPUTS.equals(cbi[2])) {
            completionItemMap.put(i, codeCompletionNeeds(cbi[1], position));
        } else if (FIELD_JOBS.equals(cbi[0]) && FIELD_OUTPUTS.equals(cbi[2])) {
            completionItemMap.put(i, codeCompletionJobs(cbi[1], position));
        }
    }

    private static void handleSecondItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, List.of(
                    completionItemOf(FIELD_OUTPUTS, GitHubWorkflowBundle.message("completion.steps.outputs"), ICON_OUTPUT),
                    completionItemOf(FIELD_CONCLUSION, GitHubWorkflowBundle.message("completion.steps.conclusion"), ICON_OUTPUT),
                    completionItemOf(FIELD_OUTCOME, GitHubWorkflowBundle.message("completion.steps.outcome"), ICON_OUTPUT)
            ));
            case FIELD_JOBS, FIELD_NEEDS -> completionItemMap.put(i, List.of(
                    completionItemOf(FIELD_OUTPUTS, GitHubWorkflowBundle.message("completion.jobs.outputs"), ICON_OUTPUT),
                    completionItemOf(FIELD_RESULT, GitHubWorkflowBundle.message("completion.jobs.result"), ICON_OUTPUT)
            ));
            case FIELD_JOB -> completionItemMap.put(i, codeCompletionJob(cbi[1], position));
            default -> {
                // ignored
            }
        }
    }

    private static void handleFirstItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, codeCompletionSteps(position));
            case FIELD_JOBS -> completionItemMap.put(i, codeCompletionJobs(position));
            case FIELD_ENVS -> completionItemMap.put(i, listEnvs(position));
            case FIELD_GITHUB -> completionItemMap.put(i, codeCompletionContext(FIELD_GITHUB, ICON_ENV));
            case FIELD_GITEA -> completionItemMap.put(i, codeCompletionContext(FIELD_GITEA, ICON_ENV));
            case FIELD_JOB -> completionItemMap.put(i, codeCompletionJob());
            case FIELD_MATRIX -> completionItemMap.put(i, listMatrix(position));
            case FIELD_RUNNER -> completionItemMap.put(i, codeCompletionContext(FIELD_RUNNER, ICON_RUNNER));
            case FIELD_STRATEGY -> completionItemMap.put(i, codeCompletionContext(FIELD_STRATEGY, ICON_NODE));
            case FIELD_INPUTS -> completionItemMap.put(i, listInputs(position));
            case FIELD_SECRETS -> completionItemMap.put(i, listSecrets(position));
            case FIELD_NEEDS -> completionItemMap.put(i, codeCompletionNeeds(position));
            default -> {
                // No Selection
                final boolean isOnOutput = getParent(position, FIELD_OUTPUTS).flatMap(outputs -> getParent(position, FIELD_ON)).isPresent();
                // SHOW ONLY JOBS [on.workflow_call.outputs.key.value:xxx]
                if (isOnOutput) {
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                } else if (getParent(position, "runs-on").isEmpty() && getParent(position, "os").isEmpty()) {
                    // DEFAULT
                    addDefaultWorkflowCompletionItems(i, position, completionItemMap);
                }
            }
        }
    }

    private static List<SimpleElement> codeCompletionContext(final String field, final NodeIcon icon) {
        return completionItemsOf(DEFAULT_VALUE_MAP.get(field).get(), icon);
    }

    private static void addDefaultWorkflowCompletionItems(final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        ofNullable(DEFAULT_VALUE_MAP.getOrDefault(FIELD_DEFAULT, null))
                .map(Supplier::get)
                .map(map -> {
                    final Map<String, String> copyMap = new HashMap<>(map);
                    Optional.of(listInputs(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_INPUTS));
                    Optional.of(listSecrets(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_SECRETS));
                    Optional.of(listJobs(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_JOBS));
                    Optional.of(listMatrix(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_MATRIX));
                    Optional.of(listSteps(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_STEPS));
                    Optional.of(listJobNeeds(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_NEEDS));
                    return copyMap;
                })
                .map(map -> completionItemsOf(map, ICON_NODE))
                .ifPresent(items -> completionItemMap.put(i, items));
    }

    private static String getDefaultPrefix(final CompletionParameters parameters) {
        final String wholeText = parameters.getOriginalFile().getText();
        final int caretOffset = parameters.getOffset();
        return getDefaultPrefix(wholeText, caretOffset);
    }

    private static String getDefaultPrefix(final CompletionPsi completionPsi) {
        final String wholeText = completionPsi.position().getContainingFile().getText();
        return getDefaultPrefix(wholeText, completionPsi.offset());
    }

    private static String getDefaultPrefix(final String wholeText, final int caretOffset) {
        final int offset = WorkflowLocation.boundedOffset(wholeText, caretOffset);
        if (offset < 1) {
            return "";
        }
        final int indexStart = getStartIndex(wholeText, offset - 1);
        if (indexStart < 0 || indexStart > offset) {
            return "";
        }
        return wholeText.substring(indexStart, offset)
                .replace("IntellijIdeaRulezzz", "")
                .replaceFirst("^[\\s\\[,'\"]+", "")
                .trim();
    }

    public static String lineBeforeCaret(final String wholeText, final int rawOffset) {
        return WorkflowLocation.lineBeforeCaret(wholeText, rawOffset);
    }

    private static void addLookupElements(final CompletionResultSet resultSet, final Map<String, String> map, final NodeIcon icon, final char suffix) {
        if (!map.isEmpty()) {
            resultSet.addAllElements(toLookupElements(map, icon, suffix));
        }
    }

    private static List<LookupElement> toLookupElements(final Map<String, String> map, final NodeIcon icon, final char suffix) {
        return map.entrySet().stream().map(item -> toLookupElement(icon, suffix, item.getKey(), item.getValue())).toList();
    }

    @NotNull
    private static List<LookupElement> toLookupItems(final List<SimpleElement> items) {
        return items.stream().map(SimpleElement::toLookupElement).toList();
    }

    /**
     * Opens workflow completion while the user types YAML structure and expression separators.
     */
    public static final class TypedAutoPopup extends TypedHandlerDelegate {

        @Override
        public @NotNull Result checkAutoPopup(
                final char typeChar,
                @NotNull final Project project,
                @NotNull final Editor editor,
                @NotNull final PsiFile file
        ) {
            // Structural workflow completion is scheduled after the typed character lands in the document.
            return Result.CONTINUE;
        }

        @Override
        public @NotNull Result charTyped(
                final char typeChar,
                @NotNull final Project project,
                @NotNull final Editor editor,
                @NotNull final PsiFile file
        ) {
            if (shouldAutoPopup(typeChar, editor, file)) {
                scheduleWorkflowPopup(project, editor);
            }
            return Result.CONTINUE;
        }

        static void scheduleWorkflowPopup(final Project project, final Editor editor) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed() || editor.isDisposed()) {
                    return;
                }
                final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                documentManager.commitDocument(editor.getDocument());
                final PsiFile file = documentManager.getPsiFile(editor.getDocument());
                if (file != null && getWorkflowFile(file).isPresent()) {
                    AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                }
            });
        }

        public static boolean shouldAutoPopup(final char typeChar, final Editor editor, final PsiFile file) {
            if (!WorkflowCompletion.workflowCompletionTrigger(typeChar) || editor == null || file == null) {
                return false;
            }
            final int textLength = file.getTextLength();
            if (textLength <= 0) {
                return getWorkflowFile(file).isPresent();
            }
            final int offset = Math.max(0, Math.min(editor.getCaretModel().getOffset(), textLength - 1));
            final PsiElement element = Optional.ofNullable(file.findElementAt(offset)).orElse(file);
            return getWorkflowFile(element).isPresent();
        }
    }

    /**
     * Opens workflow key completion after pressing Enter below YAML mapping keys.
     */
    public static final class EnterAutoPopup extends EnterHandlerDelegateAdapter {

        @Override
        public @NotNull Result postProcessEnter(
                @NotNull final PsiFile file,
                @NotNull final Editor editor,
                @NotNull final DataContext dataContext
        ) {
            if (shouldAutoPopupAfterEnter(editor, file)) {
                TypedAutoPopup.scheduleWorkflowPopup(file.getProject(), editor);
            }
            return Result.Continue;
        }

        public static boolean shouldAutoPopupAfterEnter(final Editor editor, final PsiFile file) {
            if (editor == null || file == null || getWorkflowFile(file).isEmpty()) {
                return false;
            }
            final String textBeforeCaret = editor.getDocument()
                    .getImmutableCharSequence()
                    .subSequence(0, Math.min(editor.getCaretModel().getOffset(), editor.getDocument().getTextLength()))
                    .toString();
            final int currentLineStart = textBeforeCaret.lastIndexOf('\n');
            if (currentLineStart <= 0) {
                return false;
            }
            final int previousLineStart = textBeforeCaret.lastIndexOf('\n', currentLineStart - 1) + 1;
            final String previousLine = textBeforeCaret.substring(previousLineStart, currentLineStart).trim();
            return !previousLine.startsWith("#") && previousLine.endsWith(":");
        }
    }

    /**
     * Keeps workflow auto-popup completion available in sparse YAML positions, such as the line after {@code on:}.
     */
    public static final class Confidence extends CompletionConfidence {

        @Override
        public @NotNull ThreeState shouldSkipAutopopup(
                final Editor editor,
                final PsiElement contextElement,
                final PsiFile psiFile,
                final int offset
        ) {
            return getWorkflowFile(psiFile).isPresent() || getWorkflowFile(contextElement).isPresent()
                    ? ThreeState.NO
                    : ThreeState.UNSURE;
        }
    }

    private record RemoteUses(String base, String ref) {
    }

    private record RemoteUsesRef(String usesBase, String prefix) {
    }

    private record StructureCompletion(Map<String, String> items, char suffix) {
    }

    private record EventFilterContext(String event, String filter) {
    }

    private record CompletionPsi(PsiElement position, int offset) {
    }

    private record CompletionTrigger(
            CompletionParameters parameters,
            CompletionResultSet resultSet,
            CompletionPsi completionPsi,
            PsiElement position,
            Optional<String[]> caretBracketItem,
            String prefix
    ) {
    }
}
