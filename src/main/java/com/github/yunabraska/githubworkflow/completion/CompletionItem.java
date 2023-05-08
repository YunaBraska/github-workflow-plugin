package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.lookup.LookupElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getDescription;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.orEmpty;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.toGithubEnvs;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.toGithubOutputs;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.*;
import static java.util.Collections.singletonList;

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


    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listSteps(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final YamlNode currentNode = part.get().getCurrentNode();
        return completionItemsOf(currentNode.toWorkflowFile().getParentJob(part.get().isOutputJobNode() ? full.get() : null).map(job -> job.nodesToMap(
                FIELD_STEPS,
                step -> step.getChild("id").isPresent(),
                step -> step.getChild("id").map(YamlNode::value).orElse(""),
                step -> step.getChild(FIELD_USES).map(YamlNode::value).orElseGet(() -> step.getChild("name").map(YamlNode::value).orElse(""))
        )).orElse(null), ICON_STEP);
    }

    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listStepOutputs(final String stepId, final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final List<CompletionItem> result = new ArrayList<>();

        //STEP OUTPUTS FROM USES [ACTION/WORKFLOW]
        final WorkflowFile workflowFile = part.get().isOutputJobNode() ? full.get() : part.get();
        final String jobId = part.get().getCurrentNode().toWorkflowFile().getParentJob(null).map(WorkflowFile::yaml).map(YamlNode::name).orElse("####");
        workflowFile.getActionOutputs(jobId, stepId)
                .map(outputs -> completionItemsOf(outputs, ICON_OUTPUT))
                .ifPresent(result::addAll);

        //STEP OUTPUTS FROM TEXT
        workflowFile.getStepById(jobId, stepId)
                .map(node -> node.getAllChildren(child -> child.hasName(FIELD_RUN)))
                .map(runList -> runList.stream().filter(node -> node.value() != null).map(node -> toGithubOutputs(node.value())).collect(Collectors.toList()))
                .ifPresent(envMapList -> envMapList.forEach(envMap -> result.addAll(completionItemsOf(envMap, ICON_TEXT_VARIABLE))));
        return result;
    }

    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listJobs(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        return completionItemsOf((part.get().isOutputTriggerNode() ? full.get() : part.get()).nodesToMap(
                FIELD_JOBS,
                job -> job.name() != null && job.getChild(FIELD_OUTPUTS).isPresent(),
                job -> orEmpty(job.name()),
                job -> job.getChild("name").map(YamlNode::value).orElse("")
        ), ICON_JOB);
    }

    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listJobOutputs(final String jobId, final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final List<CompletionItem> result = new ArrayList<>();
        final Optional<YamlNode> jobNode = (part.get().isOutputTriggerNode() ? full.get() : part.get()).getJobById(jobId);
        //JOB OUTPUTS
        jobNode
                .flatMap(job -> job.getChild(FIELD_OUTPUTS))
                .map(YamlNode::children)
                .map(cildList -> completionItemsOf(cildList, ICON_OUTPUT))
                .ifPresent(result::addAll);

        //JOB USES OUTPUTS
        jobNode.flatMap(node -> node.getChild(FIELD_USES))
                .map(YamlNode::value)
                .map(GitHubAction::getGitHubAction)
                .map(GitHubAction::outputs)
                .map(childList -> completionItemsOf(childList, ICON_OUTPUT))
                .ifPresent(result::addAll);
        return result;
    }

    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listNeeds(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final Optional<WorkflowFile> currentJob = part.get().getCurrentNode().toWorkflowFile().getParentJob(null);
        return completionItemsOf(part.get().nodesToMap(
                FIELD_JOBS,
                job -> job.name() != null && currentJob.map(currentJobNode -> !job.hasName(currentJobNode.yaml().name())).orElse(true),
                job -> orEmpty(job.name()),
                job -> job.getChild("name").map(YamlNode::value).orElse("")
        ), ICON_NEEDS);
    }

    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listJobNeeds(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        return part.get().getCurrentNode().toWorkflowFile()
                .getParentJob(full.get())
                .flatMap(job -> job.children().stream().filter(child -> child.hasName(FIELD_NEEDS)).findFirst())
                .map(needs -> needs.value() != null ? singletonList(needs.value()) : needs.children().stream().map(YamlNode::value).filter(Objects::nonNull).collect(Collectors.toList()))
                .map(needs -> needs.stream()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(need -> part.get().getJobById(need).orElse(null))
                        .filter(Objects::nonNull)
                        .map(need -> completionItemOf(need.name(), getDescription(need), ICON_NEEDS))
                        .collect(Collectors.toList())
                ).orElse(new ArrayList<>());

    }

    @SuppressWarnings({"java:S1142", "unused"})
    public static List<CompletionItem> listInputs(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final Map<String, String> result = new HashMap<>();
        part.get().yaml().getAllChildren(node -> node.hasName(FIELD_INPUTS)).forEach(inputs -> inputs.children().forEach(input -> {
            if (input.name() != null) {
                final String description = getDescription(input);
                final String in = result.computeIfAbsent(input.name(), value -> description);
                if (in.length() < description.length()) {
                    result.put(input.name(), description);
                }
            }
        }));
        return completionItemsOf(result, ICON_INPUT);
    }

    public static List<CompletionItem> listSecrets(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final List<CompletionItem> result = new ArrayList<>();
        //JOB SECRETS
        result.addAll(part.get()
                .getCurrentNode()
                .toWorkflowFile()
                .getParentJob(full.get())
                .flatMap(job -> job.children().stream().filter(node -> node.hasName(FIELD_SECRETS)).findFirst())
                .map(envs -> completionItemsOf(envs.children(), ICON_SECRET_JOB))
                .orElse(new ArrayList<>()));

        //WORKFLOW SECRETS
        result.addAll(part.get().children().stream()
                .filter(node -> node.hasName("on") || node.hasName("true"))
                .map(node -> node.toWorkflowFile().nodesToMap(
                        FIELD_SECRETS,
                        secret -> secret.name() != null,
                        secret -> orEmpty(secret.name()),
                        GitHubWorkflowUtils::getDescription
                ))
                .flatMap(map -> completionItemsOf(map, ICON_SECRET_WORKFLOW).stream()).collect(Collectors.toList()));
        return result;
    }

    public static List<CompletionItem> listEnvs(final Supplier<WorkflowFile> part, final Supplier<WorkflowFile> full) {
        final List<CompletionItem> result = new ArrayList<>();
        //CURRENT STEP TEXT ENVS [jobs.job_id.steps.step_id.run:value]
        part.get().getCurrentNode()
                .toWorkflowFile()
                .getParentJob(null)
                .map(job -> job.yaml().getAllChildren(child -> child.hasName(FIELD_RUN)))
                .map(runList -> runList.stream().filter(node -> node.value() != null).map(node -> toGithubEnvs(node.value())).collect(Collectors.toList()))
                .ifPresent(envMapList -> envMapList.forEach(envMap -> result.addAll(completionItemsOf(envMap, ICON_TEXT_VARIABLE))));

        //CURRENT STEP ENVS [step.env.env_id:env_value]
        result.addAll(part.get()
                .getCurrentNode()
                .toWorkflowFile()
                .getParentStep(full.get())
                .flatMap(step -> step.yaml().getChild(FIELD_ENVS))
                .map(envs -> completionItemsOf(envs.children(), ICON_ENV_STEP))
                .orElse(new ArrayList<>())
        );
        //CURRENT JOB ENVS [jobs.job_id.envs.env_id:env_value]
        result.addAll(part.get()
                .getCurrentNode()
                .toWorkflowFile()
                .getParentJob(full.get())
                .flatMap(job -> job.children().stream().filter(node -> node.hasName(FIELD_ENVS)).findFirst())
                .map(envs -> completionItemsOf(envs.children(), ICON_ENV_JOB))
                .orElse(new ArrayList<>())
        );
        //DEFAULT ENVS
        result.addAll(completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get(), ICON_ENV));
        return result;
    }

    public static List<CompletionItem> completionItemsOf(final Map<String, String> map, final NodeIcon icon) {
        return map == null ? new ArrayList<>() : map.entrySet().stream()
                .map(item -> completionItemOf(item.getKey(), item.getValue(), icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<CompletionItem> completionItemsOf(final List<YamlNode> nodes, final NodeIcon icon) {
        return nodes.stream()
                .map(node -> completionItemOf(node.name(), node.value(), icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static CompletionItem completionItemOf(final String key, final String text, final NodeIcon icon) {
        return key == null ? null : new CompletionItem(key, orEmpty(text), icon);
    }
}
