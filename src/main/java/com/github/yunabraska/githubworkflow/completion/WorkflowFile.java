package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.WORKFLOW_CACHE;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.mapToLookupElements;
import static java.util.Optional.ofNullable;

public class WorkflowFile {
    private final YamlNode yaml;

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


    public List<LookupElement> nodesToLookupElement(final String nodeName, final Predicate<YamlNode> childFilter, final Function<YamlNode, String> key, final Function<YamlNode, String> value) {
        return mapToLookupElements(nodesToMap(nodeName, childFilter, key, value), 5, true);
    }

    public Map<String, String> nodesToMap(final String nodeName, final Predicate<YamlNode> childFilter, final Function<YamlNode, String> key, final Function<YamlNode, String> value) {
        return this.yaml().getNodes(inputs -> nodeName.equals(inputs.name)).stream()
                .flatMap(inputs -> inputs.children.stream())
                .filter(childFilter)
                .collect(Collectors.toMap(key, value));
    }

    public Optional<Map<String, String>> getUses() {
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

    public List<YamlNode> filter(final String... nodePath) {
        return yaml != null ? filterRecursive(this.yaml, 0, new ArrayList<>(), nodePath) : new ArrayList<>();
    }

    private List<YamlNode> filterRecursive(final YamlNode node, final int level, final List<YamlNode> result, final String... nodePath) {
        if (nodePath[level].equals(node.name)) {
            if (FIELD_INPUTS.equals(node.name)) {
                result.addAll(node.children);
            } else if (FIELD_STEPS.equals(node.name)) {
                result.addAll(node.children);
            } else {
                result.add(node);
            }
        } else if (level + 1 != nodePath.length) {
            node.children.forEach(child -> filterRecursive(child, level + 1, result, nodePath));
        }
        return result;
    }

    private static YamlNode getLastChild(final YamlNode yamlNode) {
        if (yamlNode.children().isEmpty()) {
            return yamlNode;
        }
        return getLastChild(yamlNode.children().get(yamlNode.children().size() - 1));
    }

    protected WorkflowFile(final YamlNode yaml) {
        this.yaml = yaml;
    }
}
