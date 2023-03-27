package com.github.yunabraska.githubworkflowplugin.completion;

import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class YamlNode {
    final String name;
    final String value;
    final YamlNode parent;
    final YamlNode jobParent;
    final YamlNode stepParent;
    final boolean isJobsNode;
    final boolean isStepsNode;
    final List<YamlNode> children = new ArrayList<>();

    //TODO implement search with predicate
    //TODO isJobs
    //TODO isSteps
    //TODO isWorkflow_call
    //TODO isWorkflow_dispatch
    //TODO isInputList
    //TODO isSecretList

    public static YamlNode loadYaml(final Path path) {
        try (final FileReader fileReader = new FileReader(path.toFile())) {
            return yamlNodeOf(new Yaml().load(fileReader));
        } catch (IOException ignored) {
            //ignored
        }
        return null;
    }

    public List<YamlNode> filter(final Predicate<YamlNode> filter) {
        return filterNodesRecursive(this, filter, new ArrayList<>());
    }

    private YamlNode(
            final String name,
            final String value,
            final YamlNode parent
    ) {
        this.name = name;
        this.value = value;
        this.parent = parent;
        this.jobParent = getParentWith(parent, YamlNode::getJobParent, p -> p.isJobsNode);
        this.stepParent = getParentWith(parent, YamlNode::getStepParent, p -> p.isStepsNode);
        this.isJobsNode = jobParent == null && "jobs".equalsIgnoreCase(name);
        this.isStepsNode = stepParent == null && "steps".equalsIgnoreCase(name);
    }

    private static YamlNode yamlNodeOf(final Object node) {
        return yamlNodeOf(null, null, node);
    }

    private static YamlNode yamlNodeOf(final YamlNode parent, final Object key, final Object value) {
        if (value instanceof Map) {
            final YamlNode currentNode = new YamlNode(key == null ? null : key.toString(), null, parent);
            currentNode.children.addAll(yamlNodesOf(currentNode, (Map<Object, Object>) value));
            return currentNode;
        }
        if (value instanceof Collection) {
            final YamlNode currentNode = new YamlNode(key == null ? null : key.toString(), null, parent);
            currentNode.children.addAll(yamlNodesOf(currentNode, (Collection<Object>) value));
            return currentNode;
        }
        return new YamlNode(key == null ? null : key.toString(), value == null ? null : value.toString(), parent);
    }

    public static List<YamlNode> yamlNodesOf(final YamlNode parent, final Map<Object, Object> map) {
        return map.entrySet().stream().map(item -> yamlNodeOf(parent, item.getKey(), item.getValue())).collect(Collectors.toList());
    }

    public static List<YamlNode> yamlNodesOf(final YamlNode parent, final Collection<Object> collection) {
        return collection.stream().filter(Objects::nonNull).map(item -> yamlNodeOf(parent, item.toString(), null)).collect(Collectors.toList());
    }

    private static YamlNode getJobParent(final YamlNode parent) {
        if (parent != null && parent.jobParent != null) {
            return parent.jobParent;
        }
        return parent != null && parent.isJobsNode ? parent : null;
    }

    private static YamlNode getStepParent(final YamlNode parent) {
        if (parent != null && parent.stepParent != null) {
            return parent.stepParent;
        }
        return parent != null && parent.isStepsNode ? parent : null;
    }

    private static YamlNode getParentWith(final YamlNode parent, final Function<YamlNode, YamlNode> mapParent, final Predicate<YamlNode> orGetParentIf) {
        return Optional.ofNullable(parent)
                .map(mapParent)
                .or(() -> Optional.ofNullable(parent).filter(orGetParentIf).map(yamlNode -> yamlNode.parent))
                .orElse(null);
    }

    private static List<YamlNode> filterNodesRecursive(final YamlNode currentNode, final Predicate<YamlNode> filter, final List<YamlNode> resultNodes) {
        if (filter.test(currentNode)) {
            resultNodes.add(currentNode);
        }
        for (YamlNode child : currentNode.children) {
            filterNodesRecursive(child, filter, resultNodes);
        }
        return resultNodes;
    }
}
