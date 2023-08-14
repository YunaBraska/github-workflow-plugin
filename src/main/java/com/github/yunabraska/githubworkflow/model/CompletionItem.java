package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.config.NodeIcon;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getDescription;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.orEmpty;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.*;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.hasText;
import static java.util.Optional.ofNullable;

public class CompletionItem {

    private final String key;
    private final String text;
    private final NodeIcon icon;

    public CompletionItem(final String key, final String text, final NodeIcon icon) {
        this.key = key;
        this.text = text;
        this.icon = icon != null ? icon : NodeIcon.ICON_NODE;
    }

    public String key() {
        return key;
    }

    public LookupElement toLookupElement() {
        return GitHubWorkflowUtils.toLookupElement(icon, Character.MIN_VALUE, key, text);
    }

    public static List<CompletionItem> listSteps(final YamlElement position) {
        //StepList position == step?    list previous steps in current job
        //StepList position == outputs? list all      steps in current job
        final int stepOffset = position.findParentStep().map(YamlElement::startIndexAbs).orElse(-1);
        return position.findParentJob()
                .map(YamlElement::listSteps)
                .map(steps -> steps.stream()
                        .filter(step -> hasText(step.id()))
                        .filter(step -> stepOffset == -1 || step.startIndexAbs() < stepOffset)
                        .collect(Collectors.toMap(step -> ofNullable(step.id()).orElseGet(() -> "step_" + step.childIndex()), YamlElement::usesOrName, (existing, replacement) -> existing))
                )
                .map(map -> completionItemsOf(map, ICON_STEP))
                .orElseGet(ArrayList::new);
    }

    public static List<CompletionItem> listStepOutputs(final Project project, final YamlElement position, final int cursorAbs, final String stepId) {
        return position
                .findParentJob()
                .flatMap(job -> job.child(FIELD_STEPS))
                .flatMap(steps -> steps.childId(stepId))
                //ALL STEPS IF [job.job_id.outputs.key:value] else only steps before current step [job.job_id.steps:*]
                .filter(step -> position.findParentOutput().isPresent() || step.endIndexAbs() < cursorAbs)
                .map(step -> {
                    //STEP OUTPUTS FROM USES [ACTION/WORKFLOW]
                    final List<CompletionItem> result = ofNullable(step.uses()).map(GitHubAction::getGitHubAction).map(action -> action.outputs(project)).map(map -> completionItemsOf(map, ICON_OUTPUT)).orElseGet(ArrayList::new);
                    //STEP OUTPUTS FROM TEXT
                    position.context().runOutputs().values().stream()
                            .filter(run -> stepId != null && run.findParentStep().filter(parent -> stepId.equals(parent.id())).isPresent())
                            .map(run -> completionItemOf(run.key(), run.textOrChildTextNoQuotes(), ICON_TEXT_VARIABLE))
                            .forEach(result::add);
                    return result;
                }).orElseGet(ArrayList::new);
    }

    public static List<CompletionItem> listJobs(final YamlElement position) {
        //JobList is only valid in Workflow outputs
        //TODO: cut "uses" to a smaller size like "workflow@tag"
        return position
                .findParentOutput()
                .map(YamlElement::findParentOn)
                .map(on -> position.context().jobs().values().stream().collect(Collectors.toMap(YamlElement::key, job -> ofNullable(job.usesOrName()).orElse("job_" + job.childIndex()), (existing, replacement) -> existing)))
                .map(map -> completionItemsOf(map, ICON_JOB))
                .orElseGet(ArrayList::new);
    }

    public static List<CompletionItem> listJobOutputs(final Project project, final YamlElement position, final String jobId) {
        final List<CompletionItem> result = new ArrayList<>();
        final Optional<YamlElement> jobNode = position.context().jobs().values().stream().filter(job -> jobId != null && jobId.equals(job.key())).findFirst();

        //JOB OUTPUTS
        jobNode.flatMap(job -> job.child(FIELD_OUTPUTS).map(YamlElement::children))
                .ifPresent(outputs -> outputs.stream().filter(hasKey()).map(output -> completionItemOf(output.key(), output.textOrChildTextNoQuotes(), ICON_OUTPUT)).forEach(result::add));


        //JOB USES OUTPUTS
        jobNode.flatMap(job -> job.child(FIELD_USES).map(YamlElement::textOrChildTextNoQuotes))
                .map(GitHubAction::getGitHubAction)
                .map(action -> action.outputs(project))
                .map(childList -> completionItemsOf(childList, ICON_OUTPUT))
                .ifPresent(result::addAll);
        return result;
    }

    public static List<CompletionItem> listNeeds(final YamlElement position) {
        final Integer positionJob = position.findParentJob().map(YamlElement::startIndexAbs).orElse(-1);
        return position.context().jobs().values()
                .stream()
                .filter(hasKey())
                .filter(job -> job.startIndexAbs() < positionJob)
                .map(job -> completionItemOf(job.key(), ofNullable(job.usesOrName()).orElse("job_" + job.childIndex()), ICON_NEEDS))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<CompletionItem> listJobNeeds(final YamlElement position) {
        final Integer positionJob = position.findParentJob().map(YamlElement::startIndexAbs).orElse(-1);
        final List<String> validJobs = position.context().jobs().values()
                .stream()
                .filter(hasKey())
                .filter(job -> job.startIndexAbs() < positionJob)
                .map(YamlElement::key)
                .collect(Collectors.toCollection(ArrayList::new));

        return position.findParentJob()
                .flatMap(job -> job.child(FIELD_NEEDS))
                .map(needs -> Stream.of(new String[]{ofNullable(needs.textNoQuotes()).orElse("")}, needs.children().stream().map(YamlElement::textOrChildTextNoQuotes).toArray(String[]::new))
                        .flatMap(Arrays::stream)
                        .filter(YamlElementHelper::hasText)
                        .filter(validJobs::contains)
                        .map(need -> completionItemOf(need, "", ICON_NEEDS))
                        .collect(Collectors.toCollection(ArrayList::new))
                )
                .orElse(new ArrayList<>());
    }

    public static List<CompletionItem> listInputs(final YamlElement position) {
        final Map<String, String> result = new HashMap<>();
        position.context().inputs().values().stream()
                .filter(hasKey())
                .forEach(input -> {
                    final String description = getDescription(input);
                    final String previousDescription = result.computeIfAbsent(input.key(), value -> description);
                    if (previousDescription.length() < description.length()) {
                        result.put(input.key(), description);
                    }
                });
        return completionItemsOf(result, ICON_INPUT);
    }

    public static List<CompletionItem> listSecrets(final YamlElement position) {
        //FIXME is this valid? JOB SECRETS
//        result.addAll(part.get()
//                .getCurrentNode()
//                .toWorkflowFile()
//                .getParentJob(full.get())
//                .flatMap(job -> job.children().stream().filter(node -> node.hasName(FIELD_SECRETS)).findFirst())
//                .map(envs -> completionItemsOf(envs.children(), ICON_SECRET_JOB))
//                .orElse(new ArrayList<>()));

        //WORKFLOW SECRETS
        return position.context().root()
                .child(FIELD_ON)
                .map(on -> on.findChildNodes(secrets -> FIELD_SECRETS.equals(secrets.key())))
                .map(secrets -> secrets.stream().flatMap(secret -> secret.children().stream()).filter(hasKey()).collect(Collectors.toMap(YamlElement::key, GitHubWorkflowUtils::getDescription, (existing, replacement) -> existing)))
                .map(map -> completionItemsOf(map, ICON_SECRET_WORKFLOW))
                .orElseGet(ArrayList::new);
    }

    public static List<CompletionItem> listEnvs(final YamlElement position, final int cursorAbs) {
        //CURRENT STEP TEXT ENVS [jobs.job_id.steps.step_id.run:key=value]
        final List<CompletionItem> result = new ArrayList<>(completionItemsOf(position.context().runEnvs().values().stream()
                        .filter(env -> env.startIndexAbs() < cursorAbs && env.endIndexAbs() < cursorAbs)
                        .collect(Collectors.toMap(YamlElement::key, YamlElement::textOrChildTextNoQuotes, (existing, replacement) -> existing))
                , ICON_TEXT_VARIABLE));

        //CURRENT STEP ENVS [step.env.env_key:env_value]
        position
                .findParentStep()
                .flatMap(step -> step.child(FIELD_ENVS))
                .map(YamlElement::children)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_STEP))
                .ifPresent(result::addAll);

        //CURRENT JOB ENVS [jobs.job_id.envs.env_id:env_value]
        position
                .findParentJob()
                .flatMap(step -> step.child(FIELD_ENVS))
                .map(YamlElement::children)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_JOB))
                .ifPresent(result::addAll);

        //WORKFLOW ENVS
        position.context().root()
                .child(FIELD_ENVS)
                .map(YamlElement::children)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_ROOT))
                .ifPresent(result::addAll);

        //DEFAULT ENVS
        result.addAll(completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get(), ICON_ENV));

        return result;
    }

    private static Function<List<YamlElement>, Map<String, String>> toMapWithKeyAndText() {
        return elements -> elements.stream()
                .filter(hasKey())
                .filter(hasTextOrChildText())
                .collect(Collectors.toMap(YamlElement::key, YamlElement::textOrChildTextNoQuotes, (existing, replacement) -> existing));
    }

    private static Predicate<YamlElement> hasKey() {
        return element -> element != null && hasText(element.key());
    }

    private static Predicate<YamlElement> hasTextOrChildText() {
        return element -> element != null && hasText(element.textOrChildTextNoQuotes());
    }

    public static List<CompletionItem> completionItemsOf(final Map<String, String> map, final NodeIcon icon) {
        return map == null ? new ArrayList<>() : map.entrySet().stream()
                .map(item -> completionItemOf(item.getKey(), item.getValue(), icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static CompletionItem completionItemOf(final String key, final String text, final NodeIcon icon) {
        return key == null ? null : new CompletionItem(key, orEmpty(text), icon);
    }
}
