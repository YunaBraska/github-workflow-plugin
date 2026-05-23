package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_GITHUB;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ID;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOB;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_MATRIX;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_PORTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SERVICES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_STRATEGY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getText;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.removeQuotes;
import static com.github.yunabraska.githubworkflow.logic.Inputs.listInputsRaw;
import static com.github.yunabraska.githubworkflow.logic.JobContext.getService;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listAllJobs;
import static com.github.yunabraska.githubworkflow.logic.Needs.getJobNeed;
import static com.github.yunabraska.githubworkflow.logic.Steps.listSteps;
import static com.github.yunabraska.githubworkflow.services.HighlightAnnotator.splitToElements;
import static com.github.yunabraska.githubworkflow.services.HighlightAnnotator.toSimpleElements;
import static java.util.Optional.ofNullable;

final class ExpressionReferenceTargets {

    static List<ExpressionReferenceTarget> resolve(final PsiElement psiElement) {
        return toSimpleElements(psiElement).stream()
                .flatMap(source -> resolveSource(psiElement, source).stream())
                .toList();
    }

    static List<ExpressionReferenceTarget> resolveAt(final PsiElement psiElement, final int offsetInElement) {
        return resolve(psiElement).stream()
                .filter(target -> contains(target.segment(), offsetInElement))
                .toList();
    }

    static Optional<SimpleElement> segmentAt(final PsiElement psiElement, final int offsetInElement) {
        return toSimpleElements(psiElement).stream()
                .filter(source -> contains(source, offsetInElement))
                .flatMap(source -> Stream.of(splitToElements(source)))
                .filter(segment -> contains(segment, offsetInElement))
                .findFirst();
    }

    private static boolean contains(final SimpleElement segment, final int offsetInElement) {
        return segment.startIndexOffset() - 1 <= offsetInElement && offsetInElement <= segment.endIndexOffset();
    }

    private static List<ExpressionReferenceTarget> resolveSource(final PsiElement psiElement, final SimpleElement source) {
        final SimpleElement[] parts = splitToElements(source);
        if (parts.length < 2) {
            return List.of();
        }
        final List<ExpressionReferenceTarget> result = new ArrayList<>();
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

    private static Optional<ExpressionReferenceTarget> resolveInput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement inputId
    ) {
        return listInputsRaw(psiElement).stream()
                .filter(input -> inputId.text().equals(input.getKeyText()))
                .findFirst()
                .map(input -> new ExpressionReferenceTarget("input", source, inputId, input));
    }

    private static Optional<ExpressionReferenceTarget> resolveSecret(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement secretId
    ) {
        return getChild(psiElement.getContainingFile(), FIELD_ON)
                .stream()
                .flatMap(on -> PsiElementHelper.getAllElements(on, FIELD_SECRETS).stream())
                .flatMap(secrets -> PsiElementHelper.getChildren(secrets).stream())
                .filter(secret -> secretId.text().equals(secret.getKeyText()))
                .findFirst()
                .map(secret -> new ExpressionReferenceTarget("secret", source, secretId, secret));
    }

    private static Optional<ExpressionReferenceTarget> resolveEnv(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement envId
    ) {
        return Stream.of(stepEnv(psiElement, envId), jobEnv(psiElement, envId), workflowEnv(psiElement, envId))
                .flatMap(Optional::stream)
                .findFirst()
                .map(env -> new ExpressionReferenceTarget("env", source, envId, env));
    }

    private static Optional<YAMLKeyValue> stepEnv(final PsiElement psiElement, final SimpleElement envId) {
        return PsiElementHelper.getParentStep(psiElement)
                .flatMap(step -> getChild(step, FIELD_ENVS))
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<YAMLKeyValue> jobEnv(final PsiElement psiElement, final SimpleElement envId) {
        return PsiElementHelper.getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_ENVS))
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<YAMLKeyValue> workflowEnv(final PsiElement psiElement, final SimpleElement envId) {
        return getChild(psiElement.getContainingFile(), FIELD_ENVS)
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<ExpressionReferenceTarget> resolveMatrix(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement matrixId
    ) {
        return PsiElementHelper.getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_STRATEGY))
                .flatMap(strategy -> getChild(strategy, FIELD_MATRIX))
                .flatMap(matrix -> matrixProperty(matrix, matrixId.text()))
                .map(matrix -> new ExpressionReferenceTarget("matrix", source, matrixId, matrix));
    }

    private static Optional<ExpressionReferenceTarget> resolveJobContext(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length >= 3 && FIELD_SERVICES.equals(parts[1].text())) {
            if (parts.length >= 5 && FIELD_PORTS.equals(parts[3].text())) {
                return getService(psiElement, parts[2].text())
                        .flatMap(service -> getChild(service, FIELD_PORTS))
                        .map(ports -> new ExpressionReferenceTarget("service-port", source, parts[4], ports));
            }
            return getService(psiElement, parts[2].text())
                    .map(service -> new ExpressionReferenceTarget("service", source, parts[2], service));
        }
        if (parts.length >= 3 && "container".equals(parts[1].text())) {
            return PsiElementHelper.getParentJob(psiElement)
                    .flatMap(job -> getChild(job, "container"))
                    .map(container -> new ExpressionReferenceTarget("container", source, parts[2], container));
        }
        return Optional.empty();
    }

    private static Optional<YAMLKeyValue> matrixProperty(final YAMLKeyValue matrix, final String key) {
        return Stream.concat(
                        PsiElementHelper.getChildren(matrix).stream()
                                .filter(ExpressionReferenceTargets::isDirectMatrixProperty),
                        getChild(matrix, "include")
                                .stream()
                                .flatMap(include -> PsiElementHelper.getChildren(include, YAMLSequenceItem.class).stream())
                                .flatMap(item -> PsiElementHelper.getChildren(item).stream())
                )
                .filter(property -> key.equals(property.getKeyText()))
                .findFirst();
    }

    private static boolean isDirectMatrixProperty(final YAMLKeyValue keyValue) {
        final String key = keyValue.getKeyText();
        return !"include".equals(key) && !"exclude".equals(key);
    }

    private static Optional<ExpressionReferenceTarget> resolveStep(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement stepId
    ) {
        return listSteps(psiElement).stream()
                .map(step -> getChild(step, FIELD_ID).orElse(null))
                .filter(Objects::nonNull)
                .filter(id -> getText(id).filter(stepId.text()::equals).isPresent())
                .findFirst()
                .map(step -> new ExpressionReferenceTarget("step", source, stepId, step));
    }

    private static Optional<ExpressionReferenceTarget> resolveStepOutput(
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
                .map(output -> new ExpressionReferenceTarget("step-output", source, parts[3], output));
    }

    private static Optional<PsiElement> stepOutputTarget(final YAMLSequenceItem step, final String outputId) {
        return getChild(step, FIELD_RUN)
                .filter(run -> PsiElementHelper.parseOutputVariables(run).stream().anyMatch(output -> outputId.equals(output.key())))
                .map(PsiElement.class::cast)
                .or(() -> getChild(step, FIELD_USES)
                        .filter(uses -> com.github.yunabraska.githubworkflow.logic.Action.listActionsOutputs(step).stream()
                                .anyMatch(output -> outputId.equals(output.key())))
                        .map(PsiElement.class::cast));
    }

    private static Optional<ExpressionReferenceTarget> resolveNeed(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement needId
    ) {
        return getJobNeed(psiElement).stream()
                .flatMap(need -> getTextElements(need).stream())
                .filter(need -> needId.text().equals(removeQuotes(need.getText())))
                .findFirst()
                .map(need -> new ExpressionReferenceTarget("need", source, needId, need));
    }

    private static Optional<ExpressionReferenceTarget> resolveNeedOutput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return jobById(psiElement, parts[1].text())
                .flatMap(job -> jobOutput(job, parts[3].text()))
                .map(output -> new ExpressionReferenceTarget("need-output", source, parts[3], output));
    }

    private static Optional<ExpressionReferenceTarget> resolveJob(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement jobId
    ) {
        return jobById(psiElement, jobId.text())
                .map(job -> new ExpressionReferenceTarget("job", source, jobId, job));
    }

    private static Optional<ExpressionReferenceTarget> resolveJobOutput(
            final PsiElement psiElement,
            final SimpleElement source,
            final SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return jobById(psiElement, parts[1].text())
                .flatMap(job -> jobOutput(job, parts[3].text()))
                .map(output -> new ExpressionReferenceTarget("job-output", source, parts[3], output));
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
                .flatMap(element -> PsiElementHelper.getChildren(element).stream())
                .filter(child -> key.equals(child.getKeyText()))
                .findFirst();
    }

    private ExpressionReferenceTargets() {
        // static helper class
    }
}
