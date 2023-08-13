package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_USES;
import static java.util.Comparator.comparingInt;
import static java.util.Optional.ofNullable;

@SuppressWarnings({"unused", "java:S2386"})
public class WorkflowContext {

    public static final Map<String, WorkflowContext> WORKFLOW_CONTEXT_MAP = new ConcurrentHashMap<>();
    protected final AtomicInteger cursorAbs = new AtomicInteger(-1);
    protected final AtomicReference<YamlElement> root = new AtomicReference<>(null);
    protected final Map<String, YamlElement> jobs = new HashMap<>();
    protected final Map<String, YamlElement> needs = new HashMap<>();
    protected final Map<String, YamlElement> steps = new HashMap<>();
    protected final Map<String, YamlElement> inputs = new HashMap<>();
    protected final Map<String, YamlElement> envs = new HashMap<>();
    protected final Map<String, YamlElement> runEnvs = new HashMap<>();
    protected final Map<String, YamlElement> runOutputs = new HashMap<>();
    protected final Map<String, YamlElement> secrets = new HashMap<>();
    protected final Map<String, YamlElement> vars = new HashMap<>();
    protected final Map<String, YamlElement> outputs = new HashMap<>();
    protected final Map<String, GitHubAction> actions = new HashMap<>();

    public WorkflowContext(final YamlElement root) {
        this.root.set(root);
    }

    public Optional<YamlElement> getClosestElement(final int offset) {
        return Optional.of(offset)
                .filter(o -> o != -1)
                .flatMap(cursorIndexAbs -> root().allElements()
                        .filter(element -> element.startIndexAbs() <= offset && element.endIndexAbs() >= offset)
                        .min(comparingInt(element -> getDistanceToIndex(element, offset))))
                .or(() -> getLastElement(offset));
    }

    public Optional<YamlElement> getLastElement(final int offset) {
        return Optional.of(offset)
                .filter(o -> o != -1)
                .flatMap(o -> root().allElements()
                        .filter(element -> element.startIndexAbs() >= offset)
                        .min(Comparator.comparingInt(YamlElement::startIndexAbs)));
    }

    public YamlElement root() {
        return root.get();
    }

    public Map<String, YamlElement> jobs() {
        return jobs;
    }

    public Map<String, YamlElement> needs() {
        return needs;
    }

    public Map<String, YamlElement> steps() {
        return steps;
    }

    public Map<String, YamlElement> inputs() {
        return inputs;
    }

    public Map<String, YamlElement> envs() {
        return envs;
    }

    public Map<String, YamlElement> secrets() {
        return secrets;
    }

    public Map<String, YamlElement> vars() {
        return vars;
    }

    public Map<String, YamlElement> outputs() {
        return outputs;
    }

    public Map<String, YamlElement> runEnvs() {
        return runEnvs;
    }

    public Map<String, YamlElement> runOutputs() {
        return runOutputs;
    }

    public Map<String, GitHubAction> actions() {
        return actions;
    }

    public WorkflowContext init() {
        final YamlElement top = this.root.get();
        if (top != null) {
            Optional.of(top).ifPresent(r -> r.allElements().filter(Objects::nonNull).forEach(e -> {
                switch (ofNullable(e.key()).orElse("#")) {
                    case FIELD_ENVS -> e.children().forEach(item -> envs.put(item.path(), item));
                    case FIELD_STEPS -> e.children().forEach(item -> steps.put(item.path(), item));
                    case FIELD_JOBS ->
                        //if position is trigger node "ON" list all jobs
                        //if position is needs
                        //list job only when it has an output OR
                            e.children().stream().filter(item -> item.key() != null).forEach(item -> jobs.put(item.path(), item));
                    case FIELD_INPUTS ->
                            e.children().stream().filter(child -> child.key() != null).forEach(child -> inputs.put(child.path(), child));
                    case FIELD_OUTPUTS ->
                            e.children().stream().filter(child -> child.key() != null).forEach(child -> outputs.put(child.path(), child));
                    case FIELD_SECRETS ->
                            e.children().stream().filter(child -> child.key() != null).forEach(child -> secrets.put(child.path(), child));
                    case FIELD_NEEDS -> {
                        //String
                        ofNullable(e.childTextNoQuotes()).ifPresent(n -> needs.put(e.path() + "/" + e.childTextNoQuotes(), e));
                        //Array[String]
                        e.children().forEach(n -> needs.put(e.path() + "/" + n.childTextNoQuotes(), n));
                    }
                    case FIELD_RUN -> e.findParentStep().ifPresent(step -> e.children().forEach(line -> {
                        parseEnvs(step, line);
                        parseOutputs(step, line);
                    }));
                    case FIELD_USES -> {
                        final String uses = e.childTextNoQuotes();
                        //TODO: resolve TAGS & Branches
                        actions.put(e.path() + "/" + uses, GitHubAction.getGitHubAction(uses));
                    }
                    default -> {
                        // ignored
                    }
                }
            }));
        }
        return this;
    }

    private static int getDistanceToIndex(final YamlElement element, final int targetIndex) {
        return (targetIndex - element.startIndexAbs) + (element.endIndexAbs - targetIndex);
    }

    private void parseOutputs(final YamlElement step, final YamlElement line) {
        ofNullable(line.text()).map(GitHubWorkflowUtils::toGithubOutputs).ifPresent(outputMap -> outputMap.entrySet().stream().map(output -> createElement(step, line, output)).forEach(output -> this.runOutputs.put(output.path(), output)));
    }

    private void parseEnvs(final YamlElement step, final YamlElement line) {
        ofNullable(line.text()).map(GitHubWorkflowUtils::toGithubEnvs).ifPresent(envsMap -> envsMap.entrySet().stream().map(env -> createElement(step, line, env)).forEach(env -> this.runEnvs.put(env.path(), env)));
    }

    private YamlElement createElement(final YamlElement step, final YamlElement line, final Map.Entry<String, String> kv) {
        return new YamlElement(
                line.startIndexAbs(),
                line.endIndexAbs(),
                kv.getKey(),
                kv.getValue(),
                step,
                null
        );
    }
}
