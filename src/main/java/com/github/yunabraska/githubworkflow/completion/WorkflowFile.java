package com.github.yunabraska.githubworkflow.completion;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.WORKFLOW_CACHE;
import static java.util.Optional.ofNullable;

public class WorkflowFile {
    private final YamlNode yaml;
    private YamlNode currentNode;

    public static WorkflowFile workflowFileOf(final String key, final String text) {
        try {
            final WorkflowFile workflowFile = YamlNode.yamlNodeOf(null, null, new Yaml().load(text), 0).toWorkflowFile();
            if (key != null) {
                WORKFLOW_CACHE.put(key, workflowFile);
            }
            return workflowFile;
        } catch (Exception e) {
            final WorkflowFile defaultValue = new YamlNode(null, null, null, 0).toWorkflowFile();
            return key == null ? defaultValue : WORKFLOW_CACHE.getOrDefault(key, defaultValue);
        }
    }

    public boolean isOutputTriggerNode() {
        return ofNullable(getCurrentNode().parent())
                .map(YamlNode::parent)
                .filter(node -> FIELD_OUTPUTS.equals(node.name()))
                .map(YamlNode::parent)
                .filter(node -> node.hasParent("on") || node.hasParent("true")).isPresent();
    }

    public boolean isOutputJobNode() {
        return getCurrentNode().hasParent(FIELD_OUTPUTS);
    }

    public Optional<WorkflowFile> getParentJob(final WorkflowFile fullContext) {
        return getParent(node -> node.hasParent(FIELD_JOBS))
                .map(job -> fullContext == null ? job : fullContext.getJobById(job.name()).orElse(job))
                .map(YamlNode::toWorkflowFile);
    }

    public Optional<WorkflowFile> getParentStep(final WorkflowFile fullContext) {
        return getParent(node -> node.hasParent(FIELD_STEPS))
                .map(step -> fullContext == null
                        ? step
                        : step.toWorkflowFile().getParentJob(null)
                        .flatMap(job -> fullContext.getJobById(job.yaml().name()))
                        .flatMap(job -> job.toWorkflowFile().getStepByIndex(step.index()))
                        .orElse(step))
                .map(YamlNode::toWorkflowFile);
    }

    public List<YamlNode> children() {
        return ofNullable(yaml).map(node -> node.children).orElse(new ArrayList<>());
    }

    public Map<String, String> nodesToMap(final String nodeName, final Predicate<YamlNode> childFilter, final Function<YamlNode, String> key, final Function<YamlNode, String> value) {
        //HashMap cause of duplication possibilities
        final Map<String, String> result = new HashMap<>();
        this.yaml().getAllChildren(inputs -> nodeName.equals(inputs.name)).stream()
                .flatMap(inputs -> inputs.children.stream())
                .filter(childFilter)
                .forEach(node -> result.put(key.apply(node), value.apply(node)));
        return result;
    }

    public Optional<Map<String, String>> getActionOutputs(final String jobId, final String stepId) {
        return getStepById(jobId, stepId).flatMap(step -> step.getChild(FIELD_USES)
                .map(YamlNode::value)
                .map(GitHubAction::getGitHubAction)
                .map(GitHubAction::outputs)
        );
    }

    public Optional<YamlNode> getStepById(final String jobId, final String stepId) {
        return stepId == null ? Optional.empty() : getJobById(jobId)
                .flatMap(job -> job.getChild(FIELD_STEPS))
                .flatMap(steps -> steps.children().stream().filter(step -> stepId.equals(step
                        .getChild("id")
                        .map(YamlNode::value)
                        .orElse(null)
                )).findFirst());
    }

    public Optional<YamlNode> getStepByIndex(final int index) {
        return getLastChild(yaml()).toWorkflowFile().getParentJob(null)
                .flatMap(job -> job.yaml().getChild(FIELD_STEPS))
                .flatMap(steps -> steps.getChildByIndex(index));
    }

    public Optional<YamlNode> getJobById(final String jobId) {
        return getAnyChild(yaml(), jobNode -> jobId != null && jobId.equals(jobNode.name()) && jobNode.hasParent(FIELD_JOBS));
    }

    public Optional<Map<String, String>> getActionInputs() {
        final YamlNode lastChild = getLastChild(yaml());
        final YamlNode withChild = Optional.of(lastChild).filter(n -> "with".equals(n.name())).orElseGet(() -> Optional.ofNullable(lastChild.parent()).filter(n -> "with".equals(n.name())).orElse(null));
        return ofNullable(withChild)
                .map(YamlNode::parent)
                .flatMap(n -> n.getChild(FIELD_USES))
                .map(YamlNode::value)
                .map(GitHubAction::getGitHubAction)
                .map(GitHubAction::inputs)
                .filter(map -> !map.isEmpty());
    }

    public YamlNode yaml() {
        return yaml;
    }

    public Optional<YamlNode> getParent(final Predicate<YamlNode> filter) {
        return getParent(yaml(), filter);
    }

    public YamlNode getCurrentNode() {
        if (currentNode == null) {
            currentNode = getLastChild(this.yaml());
        }
        return currentNode;
    }

    private static YamlNode getLastChild(final YamlNode yamlNode) {
        if (yamlNode.children().isEmpty()) {
            return yamlNode;
        }
        return getLastChild(yamlNode.children().get(yamlNode.children().size() - 1));
    }

    private static Optional<YamlNode> getAnyChild(final YamlNode yamlNode, final Predicate<YamlNode> filter) {
        if (filter.test(yamlNode)) {
            return Optional.of(yamlNode);
        } else {
            return yamlNode.children().stream().map(child -> getAnyChild(child, filter).orElse(null)).filter(Objects::nonNull).findFirst();
        }
    }

    private static Optional<YamlNode> getParent(final YamlNode yamlNode, final Predicate<YamlNode> filter) {
        if (filter.test(yamlNode)) {
            return Optional.of(yamlNode);
        } else if (yamlNode.parent() != null) {
            return getParent(yamlNode.parent(), filter);
        } else {
            return Optional.empty();
        }
    }

    protected WorkflowFile(final YamlNode yaml) {
        this.yaml = yaml;
    }
}
