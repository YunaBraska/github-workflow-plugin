package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.git.WorkflowLocation;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl;

import javax.swing.Icon;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.*;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isChildOf;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathEndsWith;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathMatches;

/**
 * GitHub Actions workflow syntax completion tables from the public workflow syntax reference.
 */
public class WorkflowSyntax {

    private static final String WORKFLOW_SYNTAX_RESOURCE = "/github-docs/workflow-syntax.tsv";
    private static final List<JsonSchemaFileProvider> SCHEMA_FILE_PROVIDERS = Stream.<JsonSchemaFileProvider>of(
                    new GitHubSchemaProvider("dependabot-2.0", "Dependabot", WorkflowYaml::isDependabotFile),
                    new GitHubSchemaProvider("github-action", "GitHub Action", WorkflowYaml::isActionFile),
                    new GitHubSchemaProvider("github-funding", "GitHub Funding", WorkflowYaml::isFoundingFile),
                    new GitHubSchemaProvider("github-workflow", "GitHub Workflow", WorkflowYaml::isWorkflowFile),
                    new GitHubSchemaProvider("github-discussion", "GitHub Discussion", WorkflowYaml::isDiscussionFile),
                    new GitHubSchemaProvider("github-issue-forms", "GitHub Issue Forms", WorkflowYaml::isIssueForms),
                    new GitHubSchemaProvider("github-issue-config", "GitHub Workflow Issue Template configuration", WorkflowYaml::isIssueConfigFile),
                    new GitHubSchemaProvider("github-workflow-template-properties", "GitHub Workflow Template Properties", WorkflowYaml::isWorkflowTemplatePropertiesFile)
            )
            .distinct()
            .toList();
    private static final List<SyntaxRule> KEY_RULES = List.of(
            rule((path, completion) -> path.isEmpty(), "top", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_ON), "event", "inspection.workflow.syntax.unknownEventKey"),
            rule((path, completion) -> pathMatches(path, FIELD_ON, "workflow_dispatch"), "trigger.workflow_dispatch", "inspection.workflow.syntax.unknownTriggerKey"),
            rule((path, completion) -> pathMatches(path, FIELD_ON, "workflow_call"), "trigger.workflow_call", "inspection.workflow.syntax.unknownTriggerKey"),
            rule(
                    (path, completion) -> isChildOf(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                            || isChildOf(path, FIELD_ON, "workflow_call", FIELD_INPUTS),
                    ignored -> workflowInputPropertyKeys(),
                    "inspection.workflow.syntax.unknownTriggerKey"
            ),
            rule(
                    (path, completion) -> isChildOf(path, FIELD_ON, "workflow_call", FIELD_OUTPUTS),
                    ignored -> workflowOutputPropertyKeys(),
                    "inspection.workflow.syntax.unknownTriggerKey"
            ),
            rule(
                    (path, completion) -> isChildOf(path, FIELD_ON, "workflow_call", FIELD_SECRETS),
                    ignored -> workflowSecretPropertyKeys(),
                    "inspection.workflow.syntax.unknownTriggerKey"
            ),
            rule(
                    (path, completion) -> pathMatches(path, FIELD_ON, "*"),
                    path -> eventFilterKeysFor(path.get(path.size() - 1)),
                    "inspection.workflow.syntax.unknownTriggerFilter"
            ),
            rule((path, completion) -> pathEndsWith(path, "permissions"), "permission", "inspection.workflow.syntax.unknownPermission"),
            rule(
                    (path, completion) -> pathMatches(path, "defaults", FIELD_RUN)
                            || pathMatches(path, FIELD_JOBS, "*", "defaults", FIELD_RUN),
                    "defaultsRun",
                    "inspection.workflow.syntax.unknownTopLevelKey"
            ),
            rule(
                    (path, completion) -> pathMatches(path, "concurrency")
                            || pathMatches(path, FIELD_JOBS, "*", "concurrency"),
                    "concurrency",
                    "inspection.workflow.syntax.unknownTopLevelKey"
            ),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", "environment"), "environment", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*"), "job", "inspection.workflow.syntax.unknownJobKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_STRATEGY), "strategy", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> completion && pathMatches(path, FIELD_JOBS, "*", FIELD_STRATEGY, FIELD_MATRIX), "matrix", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", "container"), "container", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", "container", "credentials"), "credentials", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_SERVICES, "*"), "service", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_SERVICES, "*", "credentials"), "credentials", "inspection.workflow.syntax.unknownTopLevelKey"),
            rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_STEPS), "step", "inspection.workflow.syntax.unknownStepKey")
    );

    private WorkflowSyntax() {
    }

    public static class FileIcon extends IconProvider {
        // IconLoader automatically resolves /icons/gitea_dark.svg in dark themes.
        private static final Icon GITEA_ICON = IconLoader.getIcon("/icons/gitea.svg", FileIcon.class);
        private static final String GITEA_WORKFLOW_HOME = ".gitea";

        @Nullable
        @Override
        @SuppressWarnings("java:S2637")
        public Icon getIcon(@NotNull final PsiElement element, final int flags) {
            return Optional.of(element)
                    .filter(PsiFile.class::isInstance)
                    .map(PsiFile.class::cast)
                    .map(PsiFile::getVirtualFile)
                    .flatMap(virtualFile -> SCHEMA_FILE_PROVIDERS.stream()
                            .filter(GitHubSchemaProvider.class::isInstance)
                            .map(GitHubSchemaProvider.class::cast)
                            .filter(schemaProvider -> schemaProvider.isAvailable(virtualFile))
                            .map(schema -> iconFor(virtualFile))
                            .findFirst()
                    )
                    .orElse(null);
        }

        private static Icon iconFor(final VirtualFile virtualFile) {
            return isGiteaWorkflowFile(virtualFile) ? GITEA_ICON : AllIcons.Vcs.Vendors.Github;
        }

        private static boolean isGiteaWorkflowFile(final VirtualFile virtualFile) {
            return WorkflowPsi.toPath(virtualFile)
                    .filter(WorkflowYaml::isWorkflowFile)
                    .filter(FileIcon::isGiteaWorkflowPath)
                    .isPresent();
        }

        private static boolean isGiteaWorkflowPath(final Path path) {
            return path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(GITEA_WORKFLOW_HOME);
        }
    }

    public static class Schema implements JsonSchemaProviderFactory {

        @NotNull
        @Override
        public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
            return SCHEMA_FILE_PROVIDERS;
        }
    }

    public static class RunLanguageInjector implements MultiHostInjector {

        @Override
        public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
            if (!(context instanceof YAMLScalar scalar) || !isRunScalar(scalar)) {
                return;
            }
            languageForShell(scalar)
                    .ifPresent(language -> inject(registrar, scalar, language));
        }

        @Override
        public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
            return List.of(YAMLScalar.class);
        }

        private static boolean isRunScalar(final YAMLScalar scalar) {
            return scalar.getParent() instanceof YAMLKeyValue keyValue && FIELD_RUN.equals(keyValue.getKeyText());
        }

        private static Optional<Language> languageForShell(final YAMLScalar scalar) {
            return shellFor(scalar)
                    .map(RunLanguageInjector::languageId)
                    .flatMap(id -> Optional.ofNullable(Language.findLanguageByID(id)));
        }

        private static Optional<String> shellFor(final YAMLScalar scalar) {
            return getParentStep(scalar)
                    .flatMap(step -> getText(step, "shell"))
                    .or(() -> getParentJob(scalar)
                            .flatMap(job -> getChild(job, "defaults"))
                            .flatMap(defaults -> getChild(defaults, FIELD_RUN))
                            .flatMap(run -> getText(run, "shell")))
                    .or(() -> getChild(scalar.getContainingFile(), "defaults")
                            .flatMap(defaults -> getChild(defaults, FIELD_RUN))
                            .flatMap(run -> getText(run, "shell")))
                    .or(() -> Optional.of("bash"));
        }

        private static String languageId(final String shell) {
            final String normalized = shell.toLowerCase(Locale.ROOT).trim();
            if (normalized.contains("pwsh") || normalized.contains("powershell")) {
                return "PowerShell";
            }
            if (normalized.contains("python")) {
                return "Python";
            }
            if (normalized.contains("node") || normalized.contains("javascript") || normalized.equals("js")) {
                return "JavaScript";
            }
            if (normalized.contains("ruby")) {
                return "Ruby";
            }
            if (normalized.contains("perl")) {
                return "Perl";
            }
            return "Shell Script";
        }

        private static void inject(final MultiHostRegistrar registrar, final YAMLScalar scalar, final Language language) {
            final List<TextRange> ranges = contentRanges(scalar);
            if (ranges.isEmpty()) {
                return;
            }
            registrar.startInjecting(language);
            ranges.forEach(range -> registrar.addPlace(null, null, scalar, range));
            registrar.doneInjecting();
        }

        private static List<TextRange> contentRanges(final YAMLScalar scalar) {
            final List<TextRange> ranges = scalar instanceof YAMLScalarImpl scalarImpl
                    ? scalarImpl.getContentRanges()
                    : fallbackContentRanges(scalar);
            final List<TextRange> withoutExpressions = ranges.stream()
                    .flatMap(range -> excludeWorkflowExpressions(scalar.getText(), range).stream())
                    .toList();
            return subtractRanges(withoutExpressions, hereDocBodyRanges(scalar.getText(), new TextRange(0, scalar.getTextLength()))).stream()
                    .filter(range -> range.getStartOffset() < range.getEndOffset())
                    .toList();
        }

        private static List<TextRange> fallbackContentRanges(final YAMLScalar scalar) {
            final int length = scalar.getTextLength();
            return length == 0 ? List.of() : List.of(new TextRange(0, length));
        }

        private static List<TextRange> excludeWorkflowExpressions(final String text, final TextRange range) {
            final java.util.ArrayList<TextRange> result = new java.util.ArrayList<>();
            int start = range.getStartOffset();
            while (start < range.getEndOffset()) {
                final int expressionStart = text.indexOf("${{", start);
                if (expressionStart < 0 || expressionStart >= range.getEndOffset()) {
                    result.add(new TextRange(start, range.getEndOffset()));
                    break;
                }
                if (start < expressionStart) {
                    result.add(new TextRange(start, expressionStart));
                }
                final int expressionEnd = text.indexOf("}}", expressionStart + 3);
                start = expressionEnd < 0 ? range.getEndOffset() : Math.min(expressionEnd + 2, range.getEndOffset());
            }
            return result;
        }

        private static List<TextRange> hereDocBodyRanges(final String text, final TextRange range) {
            final java.util.ArrayList<TextRange> result = new java.util.ArrayList<>();
            String delimiter = "";
            int bodyStart = -1;
            int lineStart = range.getStartOffset();
            while (lineStart < range.getEndOffset()) {
                final int newline = text.indexOf('\n', lineStart);
                final int lineEnd = newline < 0 ? range.getEndOffset() : Math.min(newline, range.getEndOffset());
                final String line = text.substring(lineStart, lineEnd);
                if (delimiter.isBlank()) {
                    final Optional<String> nextDelimiter = hereDocDelimiter(line);
                    if (nextDelimiter.isPresent()) {
                        delimiter = nextDelimiter.get();
                        bodyStart = Math.min(lineEnd + 1, range.getEndOffset());
                    }
                } else if (line.trim().equals(delimiter)) {
                    if (bodyStart >= 0 && bodyStart < lineStart) {
                        result.add(new TextRange(bodyStart, lineStart));
                    }
                    delimiter = "";
                    bodyStart = -1;
                }
                if (newline < 0 || lineEnd >= range.getEndOffset()) {
                    break;
                }
                lineStart = lineEnd + 1;
            }
            if (!delimiter.isBlank() && bodyStart >= 0 && bodyStart < range.getEndOffset()) {
                result.add(new TextRange(bodyStart, range.getEndOffset()));
            }
            return result;
        }

        private static Optional<String> hereDocDelimiter(final String line) {
            char quote = 0;
            for (int index = 0; index + 1 < line.length(); index++) {
                final char current = line.charAt(index);
                if (quote != 0) {
                    if (current == quote) {
                        quote = 0;
                    }
                    continue;
                }
                if (current == '\'' || current == '"') {
                    quote = current;
                    continue;
                }
                if (current == '<' && line.charAt(index + 1) == '<') {
                    int delimiterStart = index + 2;
                    if (delimiterStart < line.length() && line.charAt(delimiterStart) == '-') {
                        delimiterStart++;
                    }
                    while (delimiterStart < line.length() && Character.isWhitespace(line.charAt(delimiterStart))) {
                        delimiterStart++;
                    }
                    int delimiterEnd = delimiterStart;
                    while (delimiterEnd < line.length() && isDelimiterChar(line.charAt(delimiterEnd))) {
                        delimiterEnd++;
                    }
                    if (delimiterStart < delimiterEnd) {
                        return Optional.of(line.substring(delimiterStart, delimiterEnd));
                    }
                }
            }
            return Optional.empty();
        }

        private static boolean isDelimiterChar(final char character) {
            return Character.isLetterOrDigit(character) || character == '_';
        }

        private static List<TextRange> subtractRanges(final List<TextRange> ranges, final List<TextRange> excludedRanges) {
            List<TextRange> result = ranges;
            for (final TextRange excludedRange : excludedRanges) {
                result = result.stream()
                        .flatMap(range -> subtractRange(range, excludedRange).stream())
                        .toList();
            }
            return result;
        }

        private static List<TextRange> subtractRange(final TextRange range, final TextRange excludedRange) {
            if (!range.intersectsStrict(excludedRange)) {
                return List.of(range);
            }
            final java.util.ArrayList<TextRange> result = new java.util.ArrayList<>();
            if (range.getStartOffset() < excludedRange.getStartOffset()) {
                result.add(new TextRange(range.getStartOffset(), excludedRange.getStartOffset()));
            }
            if (excludedRange.getEndOffset() < range.getEndOffset()) {
                result.add(new TextRange(excludedRange.getEndOffset(), range.getEndOffset()));
            }
            return result;
        }
    }

    static Map<String, String> topLevelKeys() {
        return table("top");
    }

    static Map<String, String> eventKeys() {
        return table("event");
    }

    static Map<String, String> eventFilterKeys() {
        return table("eventFilter");
    }

    public static Map<String, String> eventFilterKeysFor(final String event) {
        final Map<String, String> result = table("eventFilter." + event);
        return result.isEmpty() ? eventFilterKeys() : result;
    }

    public static Optional<Map<String, String>> completionKeysForPath(final List<String> path) {
        return knownKeysForPath(path, true).map(KnownKeys::values);
    }

    public static Optional<String> descriptionForKey(final YAMLKeyValue keyValue) {
        return WorkflowLocation.from(keyValue)
                .filter(WorkflowLocation::workflowFile)
                .flatMap(location -> knownKeysForPath(location.path(), true)
                        .map(KnownKeys::values)
                        .map(values -> values.get(location.keyValue().getKeyText())))
                .filter(value -> !value.isBlank());
    }

    public static Optional<KnownKeys> validationKeysForPath(final List<String> path) {
        return knownKeysForPath(path, false);
    }

    private static Optional<KnownKeys> knownKeysForPath(final List<String> path, final boolean completion) {
        if (pathEndsWith(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                || pathEndsWith(path, FIELD_ON, "workflow_call", FIELD_INPUTS)
                || pathEndsWith(path, FIELD_ON, "workflow_call", FIELD_OUTPUTS)
                || pathEndsWith(path, FIELD_ON, "workflow_call", FIELD_SECRETS)) {
            return Optional.empty();
        }
        return KEY_RULES.stream()
                .flatMap(rule -> rule.known(path, completion).stream())
                .findFirst();
    }

    public static Map<String, String> eventActivityTypesFor(final String event) {
        return table("activity." + event);
    }

    static Map<String, String> permissionScopes() {
        return table("permission");
    }

    static Map<String, String> permissionValues() {
        return table("permissionValue");
    }

    public static Map<String, String> permissionValuesFor(final String permission) {
        final Map<String, String> result = table("permissionValue." + permission);
        return result.isEmpty() ? permissionValues() : result;
    }

    public static Map<String, String> permissionShorthandValues() {
        return table("permissionShorthand");
    }

    static Map<String, String> jobKeys() {
        return table("job");
    }

    static Map<String, String> defaultsRunKeys() {
        return table("defaultsRun");
    }

    static Map<String, String> concurrencyKeys() {
        return table("concurrency");
    }

    static Map<String, String> environmentKeys() {
        return table("environment");
    }

    static Map<String, String> strategyKeys() {
        return table("strategy");
    }

    static Map<String, String> matrixKeys() {
        return table("matrix");
    }

    static Map<String, String> stepKeys() {
        return table("step");
    }

    static Map<String, String> containerKeys() {
        return table("container");
    }

    static Map<String, String> serviceKeys() {
        return table("service");
    }

    static Map<String, String> credentialsKeys() {
        return table("credentials");
    }

    static Map<String, String> workflowInputTypes() {
        return table("inputType.workflow_dispatch");
    }

    static Map<String, String> reusableWorkflowInputTypes() {
        return table("inputType.workflow_call");
    }

    public static Map<String, String> workflowInputTypesFor(final String trigger) {
        return "workflow_call".equals(trigger) ? reusableWorkflowInputTypes() : workflowInputTypes();
    }

    static Map<String, String> workflowDispatchTriggerKeys() {
        return table("trigger.workflow_dispatch");
    }

    static Map<String, String> workflowCallTriggerKeys() {
        return table("trigger.workflow_call");
    }

    static Map<String, String> workflowInputPropertyKeys() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowBundle.message("documentation.description.label"));
        result.put("type", GitHubWorkflowBundle.message("documentation.type", "string | boolean | choice | number | environment"));
        result.put("required", GitHubWorkflowBundle.message("documentation.required", true));
        result.put("default", GitHubWorkflowBundle.message("documentation.default", ""));
        result.put("options", GitHubWorkflowBundle.message("documentation.value.label"));
        return java.util.Collections.unmodifiableMap(result);
    }

    static Map<String, String> workflowOutputPropertyKeys() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowBundle.message("documentation.description.label"));
        result.put("value", GitHubWorkflowBundle.message("documentation.value.label"));
        return java.util.Collections.unmodifiableMap(result);
    }

    static Map<String, String> workflowSecretPropertyKeys() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowBundle.message("documentation.description.label"));
        result.put("required", GitHubWorkflowBundle.message("documentation.required", true));
        return java.util.Collections.unmodifiableMap(result);
    }

    public static Map<String, String> booleanValues() {
        return table("boolean");
    }

    public static Map<String, String> runnerLabels() {
        return table("runner");
    }

    private static Map<String, String> table(final String group) {
        final Map<String, String> keys = Tables.DATA.getOrDefault(group, Collections.emptyMap());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, String> result = new LinkedHashMap<>();
        keys.forEach((key, bundleKey) -> result.put(key, GitHubWorkflowBundle.message(bundleKey)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Map<String, String>> loadTables() {
        final Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try (BufferedReader reader = syntaxReader()) {
            String line = reader.readLine();
            int lineNumber = 1;
            while (line != null) {
                loadTableLine(result, line, lineNumber);
                line = reader.readLine();
                lineNumber++;
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Cannot read " + WORKFLOW_SYNTAX_RESOURCE, exception);
        }
        return immutableTables(result);
    }

    private static BufferedReader syntaxReader() {
        final InputStream stream = WorkflowSyntax.class.getResourceAsStream(WORKFLOW_SYNTAX_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing " + WORKFLOW_SYNTAX_RESOURCE);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static void loadTableLine(final Map<String, Map<String, String>> result, final String rawLine, final int lineNumber) {
        final String line = rawLine.strip();
        if (line.isBlank() || line.startsWith("#")) {
            return;
        }
        final String[] parts = rawLine.split("\t", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalStateException("Invalid " + WORKFLOW_SYNTAX_RESOURCE + " line " + lineNumber);
        }
        result.computeIfAbsent(parts[0], ignored -> new LinkedHashMap<>())
                .put(parts[1], parts[2]);
    }

    private static Map<String, Map<String, String>> immutableTables(final Map<String, Map<String, String>> source) {
        final Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        source.forEach((group, values) -> tables.put(group, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        return Collections.unmodifiableMap(tables);
    }

    private static class Tables {
        private static final Map<String, Map<String, String>> DATA = loadTables();

        private Tables() {
        }
    }

    private static SyntaxRule rule(final PathPredicate predicate, final String table, final String messageKey) {
        return rule(predicate, ignored -> table(table), messageKey);
    }

    private static SyntaxRule rule(final PathPredicate predicate, final ValueProvider values, final String messageKey) {
        return new SyntaxRule(predicate, values, messageKey);
    }

    @FunctionalInterface
    private interface PathPredicate {
        boolean matches(List<String> path, boolean completion);
    }

    @FunctionalInterface
    private interface ValueProvider {
        Map<String, String> values(List<String> path);
    }

    private record SyntaxRule(PathPredicate predicate, ValueProvider values, String messageKey) {
        Optional<KnownKeys> known(final List<String> path, final boolean completion) {
            return predicate.matches(path, completion)
                    ? Optional.of(new KnownKeys(values.values(path), messageKey))
                    : Optional.empty();
        }
    }

    public record KnownKeys(Map<String, String> values, String messageKey) {
    }
}
