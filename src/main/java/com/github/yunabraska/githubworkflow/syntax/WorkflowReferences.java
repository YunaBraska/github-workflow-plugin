package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.VariableReferenceResolver;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_GITEA;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_GITHUB;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ID;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOB;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_MATRIX;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_PORTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUNNER;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SERVICES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STRATEGY;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_VARS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getTextElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.removeQuotes;
import static com.github.yunabraska.githubworkflow.syntax.Action.referenceGithubAction;
import static com.github.yunabraska.githubworkflow.syntax.Inputs.listInputsRaw;
import static com.github.yunabraska.githubworkflow.syntax.JobContext.getService;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.listAllJobs;
import static com.github.yunabraska.githubworkflow.syntax.Needs.getJobNeed;
import static com.github.yunabraska.githubworkflow.syntax.Needs.referenceNeeds;
import static com.github.yunabraska.githubworkflow.syntax.Steps.listSteps;
import static java.util.Optional.ofNullable;

public final class WorkflowReferences {

    public static final Key<GitHubAction> ACTION_KEY = new Key<>("ACTION_KEY");

    public static final class Contributor extends PsiReferenceContributor {

        @Override
        public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
            registrar.registerReferenceProvider(
                    PlatformPatterns.psiElement(PsiElement.class),
                    new PsiReferenceProvider() {
                        @NotNull
                        @Override
                        public PsiReference @NotNull [] getReferencesByElement(
                                @NotNull final PsiElement psiElement,
                                @NotNull final ProcessingContext context
                        ) {
                            return getWorkflowFile(psiElement).isEmpty() ? PsiReference.EMPTY_ARRAY : textElement(psiElement)
                                    .flatMap(element -> {
                                                final String text = removeQuotes(element.getText().replace("IntellijIdeaRulezzz ", "").replace("IntellijIdeaRulezzz", ""));
                                                return referenceGithubAction(element)
                                                        .or(() -> referenceNeeds(element, text))
                                                        .or(() -> referenceVariables(element));
                                            }
                                    )
                                    .orElse(PsiReference.EMPTY_ARRAY);
                        }
                    }
            );
        }
    }

    public record Target(String kind, SimpleElement source, SimpleElement segment, PsiElement target) {
    }

    public static List<SimpleElement> toSimpleElements(final PsiElement element) {
        if (getParent(element, FIELD_RUN).isPresent()) {
            return toSimpleElementsInExpressions(element);
        }
        final List<SimpleElement> result = new ArrayList<>();
        final String text = element.getText();
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            final String line = text.substring(lineStart, lineEnd);
            if (!line.isBlank() && !line.trim().startsWith("#")) {
                final int currentLineStart = lineStart;
                findDottedExpressions(line).stream()
                        .map(expression -> new SimpleElement(
                                expression.text(),
                                new TextRange(
                                        currentLineStart + expression.range().getStartOffset(),
                                        currentLineStart + expression.range().getEndOffset()
                                )
                        ))
                        .forEach(result::add);
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return result;
    }

    private static Optional<PsiElement> textElement(final PsiElement psiElement) {
        PsiElement current = psiElement;
        while (current != null && current.getParent() != current) {
            if (WorkflowPsi.isTextElement(current)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static Optional<PsiReference[]> referenceVariables(final PsiElement psiElement) {
        final PsiReference[] references = WorkflowReferences.resolve(psiElement).stream()
                .map(target -> new VariableReferenceResolver(
                        psiElement,
                        new TextRange(target.segment().startIndexOffset(), target.segment().endIndexOffset()),
                        target.target()
                ))
                .toArray(PsiReference[]::new);
        return references.length == 0 ? Optional.empty() : Optional.of(references);
    }

    public static SimpleElement[] splitToElements(final SimpleElement simpleElement) {
        final List<SimpleElement> result = new ArrayList<>();
        final AtomicInteger index = new AtomicInteger(0);
        while (index.get() < simpleElement.text().length()) {
            if (isIdentifierChar(simpleElement.text().charAt(index.get()))) {
                result.add(readIdentifier(simpleElement, index));
            } else {
                index.incrementAndGet();
            }
        }
        return result.toArray(SimpleElement[]::new);
    }

    public static List<Target> resolve(final PsiElement psiElement) {
        return toSimpleElements(psiElement).stream()
                .flatMap(source -> resolveSource(psiElement, source).stream())
                .toList();
    }

    public static List<Target> resolveAt(final PsiElement psiElement, final int offsetInElement) {
        return resolve(psiElement).stream()
                .filter(target -> contains(target.segment(), offsetInElement))
                .toList();
    }

    public static Optional<SimpleElement> segmentAt(final PsiElement psiElement, final int offsetInElement) {
        return toSimpleElements(psiElement).stream()
                .filter(source -> contains(source, offsetInElement))
                .flatMap(source -> Stream.of(splitToElements(source)))
                .filter(segment -> contains(segment, offsetInElement))
                .findFirst();
    }

    private static boolean contains(final SimpleElement segment, final int offsetInElement) {
        return segment.startIndexOffset() - 1 <= offsetInElement && offsetInElement <= segment.endIndexOffset();
    }

    private static List<SimpleElement> findDottedExpressions(final String text) {
        final List<SimpleElement> elements = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (!isContextStart(text, index)) {
                index++;
                continue;
            }
            final int start = index;
            boolean hasSeparator = false;
            index = readIdentifierEnd(text, index);
            while (index < text.length()) {
                final char current = text.charAt(index);
                if (current == '.') {
                    hasSeparator = true;
                    index++;
                    index = readIdentifierEnd(text, index);
                } else if (current == '[') {
                    final int closingBracket = findClosingBracket(text, index);
                    if (closingBracket < 0) {
                        break;
                    }
                    hasSeparator = true;
                    index = closingBracket + 1;
                } else {
                    break;
                }
            }
            if (hasSeparator && start < index) {
                elements.add(new SimpleElement(text.substring(start, index), new TextRange(start, index)));
            }
        }
        return elements;
    }

    private static List<SimpleElement> toSimpleElementsInExpressions(final PsiElement element) {
        final List<SimpleElement> result = new ArrayList<>();
        final String text = element.getText();
        int index = 0;
        while (index < text.length()) {
            final int expressionStart = text.indexOf("${{", index);
            if (expressionStart < 0) {
                break;
            }
            final int bodyStart = expressionStart + 3;
            final int expressionEnd = text.indexOf("}}", bodyStart);
            if (expressionEnd < 0) {
                break;
            }
            final String body = text.substring(bodyStart, expressionEnd);
            findDottedExpressions(body).stream()
                    .map(expression -> new SimpleElement(
                            expression.text(),
                            new TextRange(
                                    bodyStart + expression.range().getStartOffset(),
                                    bodyStart + expression.range().getEndOffset()
                            )
                    ))
                    .forEach(result::add);
            index = expressionEnd + 2;
        }
        return result;
    }

    private static SimpleElement readIdentifier(final SimpleElement simpleElement, final AtomicInteger index) {
        final int start = index.get();
        index.set(readIdentifierEnd(simpleElement.text(), start));
        return new SimpleElement(
                simpleElement.text().substring(start, index.get()),
                new TextRange(simpleElement.range().getStartOffset() + start, simpleElement.range().getStartOffset() + index.get())
        );
    }

    private static int readIdentifierEnd(final String text, final int start) {
        int index = start;
        while (index < text.length() && isIdentifierChar(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int findClosingBracket(final String text, final int start) {
        int index = start + 1;
        while (index < text.length()) {
            if (text.charAt(index) == ']') {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static boolean isContextStart(final String text, final int start) {
        return List.of(FIELD_INPUTS, FIELD_SECRETS, FIELD_ENVS, FIELD_GITHUB, FIELD_GITEA, FIELD_JOB, FIELD_RUNNER, FIELD_MATRIX, FIELD_STRATEGY, FIELD_STEPS, FIELD_JOBS, FIELD_NEEDS, FIELD_VARS)
                .stream()
                .anyMatch(context -> text.startsWith(context, start) && hasContextSeparator(text, start + context.length()));
    }

    private static boolean hasContextSeparator(final String text, final int index) {
        return index < text.length() && (text.charAt(index) == '.' || text.charAt(index) == '[');
    }

    public static boolean isIdentifierChar(final char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }

    private static List<Target> resolveSource(final PsiElement psiElement, final SimpleElement source) {
        final SimpleElement[] parts = splitToElements(source);
        if (parts.length < 2) {
            return List.of();
        }
        final List<Target> result = new ArrayList<>();
        switch (parts[0].text()) {
            case FIELD_INPUTS -> resolveInput(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_SECRETS -> resolveSecret(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_ENVS -> resolveEnv(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_MATRIX -> resolveMatrix(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_JOB -> resolveJobContext(psiElement, source, parts).ifPresent(result::add);
            case FIELD_STEPS -> {
                resolveStep(psiElement, source, parts[1]).ifPresent(result::add);
                resolveStepOutput(psiElement, source, parts).ifPresent(result::add);
            }
            case FIELD_NEEDS -> {
                resolveNeed(psiElement, source, parts[1]).ifPresent(result::add);
                resolveNeedOutput(psiElement, source, parts).ifPresent(result::add);
            }
            case FIELD_JOBS -> {
                resolveJob(psiElement, source, parts[1]).ifPresent(result::add);
                resolveJobOutput(psiElement, source, parts).ifPresent(result::add);
            }
            default -> {
                // Built-in contexts without a local declaration stay validated by highlighters, but are not clickable.
            }
        }
        return result;
    }

    private static Optional<Target> resolveInput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement inputId
    ) {
        return listInputsRaw(psiElement).stream()
                .filter(input -> inputId.text().equals(input.getKeyText()))
                .findFirst()
                .map(input -> new Target("input", source, inputId, input));
    }

    private static Optional<Target> resolveSecret(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement secretId
    ) {
        return getChild(psiElement.getContainingFile(), FIELD_ON)
                .stream()
                .flatMap(on -> WorkflowPsi.getAllElements(on, FIELD_SECRETS).stream())
                .flatMap(secrets -> WorkflowPsi.getChildren(secrets).stream())
                .filter(secret -> secretId.text().equals(secret.getKeyText()))
                .findFirst()
                .map(secret -> new Target("secret", source, secretId, secret));
    }

    private static Optional<Target> resolveEnv(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement envId
    ) {
        return Stream.of(stepEnv(psiElement, envId), jobEnv(psiElement, envId), workflowEnv(psiElement, envId))
                .flatMap(Optional::stream)
                .findFirst()
                .map(env -> new Target("env", source, envId, env));
    }

    private static Optional<YAMLKeyValue> stepEnv(final PsiElement psiElement, final SimpleElement envId) {
        return WorkflowPsi.getParentStep(psiElement)
                .flatMap(step -> getChild(step, FIELD_ENVS))
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<YAMLKeyValue> jobEnv(final PsiElement psiElement, final SimpleElement envId) {
        return WorkflowPsi.getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_ENVS))
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<YAMLKeyValue> workflowEnv(final PsiElement psiElement, final SimpleElement envId) {
        return getChild(psiElement.getContainingFile(), FIELD_ENVS)
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<Target> resolveMatrix(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement matrixId
    ) {
        return WorkflowPsi.getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_STRATEGY))
                .flatMap(strategy -> getChild(strategy, FIELD_MATRIX))
                .flatMap(matrix -> matrixProperty(matrix, matrixId.text()))
                .map(matrix -> new Target("matrix", source, matrixId, matrix));
    }

    private static Optional<Target> resolveJobContext(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length >= 3 && FIELD_SERVICES.equals(parts[1].text())) {
            if (parts.length >= 5 && FIELD_PORTS.equals(parts[3].text())) {
                return getService(psiElement, parts[2].text())
                        .flatMap(service -> getChild(service, FIELD_PORTS))
                        .map(ports -> new Target("service-port", source, parts[4], ports));
            }
            return getService(psiElement, parts[2].text())
                    .map(service -> new Target("service", source, parts[2], service));
        }
        if (parts.length >= 3 && "container".equals(parts[1].text())) {
            return WorkflowPsi.getParentJob(psiElement)
                    .flatMap(job -> getChild(job, "container"))
                    .map(container -> new Target("container", source, parts[2], container));
        }
        return Optional.empty();
    }

    private static Optional<YAMLKeyValue> matrixProperty(final YAMLKeyValue matrix, final String key) {
        return Stream.concat(
                        WorkflowPsi.getChildren(matrix).stream()
                                .filter(WorkflowReferences::isDirectMatrixProperty),
                        getChild(matrix, "include")
                                .stream()
                                .flatMap(include -> WorkflowPsi.getChildren(include, YAMLSequenceItem.class).stream())
                                .flatMap(item -> WorkflowPsi.getChildren(item).stream())
                )
                .filter(property -> key.equals(property.getKeyText()))
                .findFirst();
    }

    private static boolean isDirectMatrixProperty(final YAMLKeyValue keyValue) {
        final String key = keyValue.getKeyText();
        return !"include".equals(key) && !"exclude".equals(key);
    }

    private static Optional<Target> resolveStep(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement stepId
    ) {
        return listSteps(psiElement).stream()
                .map(step -> getChild(step, FIELD_ID).orElse(null))
                .filter(Objects::nonNull)
                .filter(id -> getText(id).filter(stepId.text()::equals).isPresent())
                .findFirst()
                .map(step -> new Target("step", source, stepId, step));
    }

    private static Optional<Target> resolveStepOutput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return listSteps(psiElement).stream()
                .filter(step -> getText(step, FIELD_ID).filter(parts[1].text()::equals).isPresent())
                .findFirst()
                .flatMap(step -> stepOutputTarget(step, parts[3].text()))
                .map(output -> new Target("step-output", source, parts[3], output));
    }

    private static Optional<PsiElement> stepOutputTarget(final YAMLSequenceItem step, final String outputId) {
        return getChild(step, FIELD_RUN)
                .filter(run -> WorkflowPsi.parseOutputVariables(run).stream().anyMatch(output -> outputId.equals(output.key())))
                .map(PsiElement.class::cast)
                .or(() -> getChild(step, FIELD_USES)
                        .filter(uses -> com.github.yunabraska.githubworkflow.syntax.Action.listActionsOutputs(step).stream()
                                .anyMatch(output -> outputId.equals(output.key())))
                        .map(PsiElement.class::cast));
    }

    private static Optional<Target> resolveNeed(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement needId
    ) {
        return getJobNeed(psiElement).stream()
                .flatMap(need -> getTextElements(need).stream())
                .filter(need -> needId.text().equals(removeQuotes(need.getText())))
                .findFirst()
                .map(need -> new Target("need", source, needId, need));
    }

    private static Optional<Target> resolveNeedOutput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return jobById(psiElement, parts[1].text())
                .flatMap(job -> jobOutput(job, parts[3].text()))
                .map(output -> new Target("need-output", source, parts[3], output));
    }

    private static Optional<Target> resolveJob(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement jobId
    ) {
        return jobById(psiElement, jobId.text())
                .map(job -> new Target("job", source, jobId, job));
    }

    private static Optional<Target> resolveJobOutput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return jobById(psiElement, parts[1].text())
                .flatMap(job -> jobOutput(job, parts[3].text()))
                .map(output -> new Target("job-output", source, parts[3], output));
    }

    private static Optional<YAMLKeyValue> jobById(final PsiElement psiElement, final String jobId) {
        return listAllJobs(psiElement).stream()
                .filter(job -> jobId.equals(job.getKeyText()))
                .findFirst();
    }

    private static Optional<YAMLKeyValue> jobOutput(final YAMLKeyValue job, final String outputId) {
        return getChild(job, FIELD_OUTPUTS)
                .flatMap(outputs -> childByKey(outputs, outputId));
    }

    private static Optional<YAMLKeyValue> childByKey(final PsiElement parent, final String key) {
        return ofNullable(parent)
                .stream()
                .flatMap(element -> WorkflowPsi.getChildren(element).stream())
                .filter(child -> key.equals(child.getKeyText()))
                .findFirst();
    }

    private WorkflowReferences() {
        // static helper class
    }
}
