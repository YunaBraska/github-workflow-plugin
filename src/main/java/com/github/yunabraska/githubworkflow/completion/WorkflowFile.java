package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.WORKFLOW_CACHE;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.toLookupElements;
import static java.util.Optional.ofNullable;

public class WorkflowFile {
    private final YamlNode yaml;
    private YamlNode currentNode;

    public static WorkflowFile workflowFileOf(final String key, final String text) {
        try {
            final WorkflowFile workflowFile = YamlNode.yamlNodeOf(null, null, new Yaml().load(text)).toWorkflowFile();
            if (key != null) {
                WORKFLOW_CACHE.put(key, workflowFile);
            }
            return workflowFile;
        } catch (Exception e) {
            final WorkflowFile defaultValue = new YamlNode(null, null, null).toWorkflowFile();
            return key == null ? defaultValue : WORKFLOW_CACHE.getOrDefault(key, defaultValue);
        }
    }

    public List<YamlNode> children() {
        return ofNullable(yaml).map(node -> node.children).orElse(new ArrayList<>());
    }


    public List<LookupElement> nodesToLookupElement(final String nodeName, final Predicate<YamlNode> childFilter, final Function<YamlNode, String> key, final Function<YamlNode, String> value, final NodeIcon icon) {
        return nodesToLookupElement(nodeName, childFilter, key, value, icon, Character.MIN_VALUE);
    }

    public List<LookupElement> nodesToLookupElement(final String nodeName, final Predicate<YamlNode> childFilter, final Function<YamlNode, String> key, final Function<YamlNode, String> value, final NodeIcon icon, final char suffix) {
        return toLookupElements(nodesToMap(nodeName, childFilter, key, value), icon == null ? NodeIcon.ICON_NODE : icon, suffix);
    }

    public Map<String, String> nodesToMap(final String nodeName, final Predicate<YamlNode> childFilter, final Function<YamlNode, String> key, final Function<YamlNode, String> value) {
        return this.yaml().getNodes(inputs -> nodeName.equals(inputs.name)).stream()
                .flatMap(inputs -> inputs.children.stream())
                .filter(childFilter)
                .collect(Collectors.toMap(key, value));
    }

    public Optional<Map<String, String>> getActionOutputs(final String stepId) {
        return getStepById(stepId).flatMap(step -> step.getChild("uses")
                .map(YamlNode::value)
                .map(GitHubAction::getGitHubAction)
                .map(GitHubAction::outputs)
        );
    }

    public Optional<YamlNode> getStepById(final String stepId) {
        final Optional<YamlNode> jobs = getParent(getLastChild(yaml()), node -> FIELD_JOBS.equals(node.name()));
        return jobs.flatMap(node -> node.getNodes(
                n -> n.getChild("id")
                        .map(YamlNode::value)
                        .filter(stepId::equals)
                        .isPresent()
        ).stream().findFirst());
    }

    public Optional<YamlNode> getJobById(final String jobId) {
        return getChild(yaml(), jobNode -> jobId.equals(jobNode.name()) && jobNode.hasParent(FIELD_JOBS));
    }

    public Optional<Map<String, String>> getActionInputs() {
        final YamlNode lastChild = getLastChild(yaml());
        final YamlNode withChild = Optional.of(lastChild).filter(n -> "with".equals(n.name())).orElseGet(() -> Optional.ofNullable(lastChild.parent()).filter(n -> "with".equals(n.name())).orElse(null));
        return ofNullable(withChild)
                .map(YamlNode::parent)
                .flatMap(n -> n.getChild("uses"))
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

    private static Optional<YamlNode> getChild(final YamlNode yamlNode, final Predicate<YamlNode> filter) {
        if (filter.test(yamlNode)) {
            return Optional.of(yamlNode);
        } else {
            return yamlNode.children().stream().map(child -> getChild(child, filter).orElse(null)).filter(Objects::nonNull).findFirst();
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
