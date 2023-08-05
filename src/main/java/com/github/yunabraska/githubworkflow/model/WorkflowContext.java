package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.completion.GitHubAction;
import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.model.YamlElement.CURSOR_STRING;
import static java.util.Comparator.comparingInt;
import static java.util.Optional.ofNullable;

public class WorkflowContext {

    public static final Map<String, WorkflowContext> WORKFLOW_CONTEXT_MAP = new ConcurrentHashMap<>();

    protected final AtomicReference<YamlElement> position = new AtomicReference<>(null);
    protected final AtomicInteger cursorAbs = new AtomicInteger(-1);
    protected final AtomicReference<YamlElement> root = new AtomicReference<>(null);
    protected final AtomicReference<String> path = new AtomicReference<>(null);
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
        ofNullable(root).
                map(YamlElement::node)
                .map(YamlElementHelper::getPsiFile)
                .map(yamlFile -> Optional.of(yamlFile.getOriginalFile()).map(PsiFile::getVirtualFile).orElseGet(yamlFile::getVirtualFile))
                .map(VirtualFile::getPath)
                .ifPresent(path::set);
    }

    public YamlElement position() {
        return position.get();
    }

    public YamlElement position(final String text) {
        return position(text != null ? text.indexOf(CURSOR_STRING) : -1);
    }

    public YamlElement position(final int offset) {
        cursorAbs.set(offset != -1 ? offset : cursorAbs.get());
        position.set(getClosestElement(offset).orElseGet(position::get));
        return position.get();
    }

    public Optional<YamlElement> getClosestElement(final int offset) {
        return Optional.of(offset)
                .filter(o -> o != -1)
                .flatMap(cursorIndexAbs -> root().allElements()
                        .filter(element -> element.startIndexAbs() <= offset && element.endIndexAbs() >= offset)
                        .min(comparingInt(element -> getDistanceToIndex(element, offset))));
    }

    public int cursorAbs() {
        return cursorAbs.get();
    }

    public YamlElement root() {
        return root.get();
    }

    public String path() {
        return path.get();
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
                    case FIELD_ENVS:
                        e.children().forEach(item -> envs.put(item.path(), item));
                        break;
                    case FIELD_STEPS:
                        e.children().forEach(item -> steps.put(item.path(), item));
                        break;
                    case FIELD_JOBS:
                        //if position is trigger node "ON" list all jobs
                        //if position is needs
                        //list job only when it has an output OR
                        e.children().stream().filter(item -> item.key() != null).forEach(item -> jobs.put(item.path(), item));
                        break;
                    case FIELD_INPUTS:
                        e.children().stream().filter(child -> child.key() != null).forEach(child -> inputs.put(child.path(), child));
                        break;
                    case FIELD_OUTPUTS:
                        e.children().stream().filter(child -> child.key() != null).forEach(child -> outputs.put(child.path(), child));
                        break;
                    case FIELD_SECRETS:
                        e.children().stream().filter(child -> child.key() != null).forEach(child -> secrets.put(child.path(), child));
                        break;
                    case FIELD_NEEDS:
                        //String
                        ofNullable(e.childTextNoQuotes()).ifPresent(n -> needs.put(e.path() + "/" + e.childTextNoQuotes(), e));
                        //Array[String]
                        e.children().forEach(n -> needs.put(e.path() + "/" + n.childTextNoQuotes(), n));
                        break;
                    case FIELD_RUN:
                        e.findParentStep().ifPresent(step -> e.children().forEach(line -> {
                            parseEnvs(step, line);
                            parseOutputs(step, line);
                        }));
                        break;
                    case FIELD_USES:
                        final String uses = e.childTextNoQuotes();
                        //TODO: resolve TAGS & Branches
                        actions.put(e.path() + "/" + uses, GitHubAction.getGitHubAction(uses));
                        break;
                    default:
                        break;
                }
            }));
        }
        position.set(new YamlElement(-1, -1, null, null, null, null, null));
        ofNullable(path.get()).ifPresent(path -> WORKFLOW_CONTEXT_MAP.put(path, this));
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
                root().node(),
                step,
                null
        );
    }


    //TODO: context item
    //  cursor position, needs, jobs, steps, env, secrets, vars,...
    //  full context only on certain cursor positions like workflow & jobs outputs

}
