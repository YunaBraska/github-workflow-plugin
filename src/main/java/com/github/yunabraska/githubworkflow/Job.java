package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLArrayImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_IF;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_ANNOTATION_KEY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.GHW_ELEMENT_REFERENCE_KEY;
import static java.util.stream.Collectors.toMap;

public class Job extends WorkflowObject {

    final String id;
    // https://help.github.com/en/github/automating-your-workflow-with-github-actions/workflow-syntax-for-github-actions#jobsjob_idruns-on
    final List<String> runsOn;
    final Environment environment;
    private final String condition;
    private final int timeoutMinutes;
    private final Map<String, Env> envs;
    private final Map<String, Step> steps;
    private final boolean continueOnError;
    private final Map<String, Output> outputs;
    private final Map<String, Definition> needs = new LinkedHashMap<>();

    //TODO: steps https://help.github.com/en/actions/automating-your-workflow-with-github-actions/workflow-syntax-for-github-actions#jobsjob_idsteps
    //TODO: strategy https://help.github.com/en/actions/automating-your-workflow-with-github-actions/workflow-syntax-for-github-actions#jobsjob_idstrategy

    // container https://help.github.com/en/actions/automating-your-workflow-with-github-actions/workflow-syntax-for-github-actions#jobsjob_idcontainer
    // services https://help.github.com/en/actions/automating-your-workflow-with-github-actions/workflow-syntax-for-github-actions#jobsjob_idservices
    // concurrency https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions#jobsjob_idconcurrency

    public Job(final YAMLKeyValue element) {
        super(element);
        this.id = element.getKeyText();
        this.runsOn = PsiElementHelper.getChild(element, "runs-on").map(PsiElementHelper::getTexts).orElse(null);
        this.outputs = PsiElementHelper.getChild(element, FIELD_OUTPUTS).map(PsiElementHelper::getKvChildren).map(children -> children.stream().map(Output::new).collect(toMap(Output::id, input -> input))).orElse(Collections.emptyMap());
        this.condition = PsiElementHelper.getText(element, FIELD_IF).orElse(null);
        this.timeoutMinutes = PsiElementHelper.getInt(element, "timeout-minutes").orElse(360);
        this.continueOnError = PsiElementHelper.getText(element, "continue-on-error").map(Boolean::parseBoolean).orElse(false);
        this.environment = PsiElementHelper.getChild(element, "environment").map(Environment::new).orElse(null);
        this.envs = PsiElementHelper.getChild(element, FIELD_ENVS).map(PsiElementHelper::getKvChildren).map(children -> children.stream().map(Env::new).collect(toMap(Env::id, env -> env))).orElse(Collections.emptyMap());
        this.steps = PsiElementHelper.getChild(element, "steps").map(stepList -> PsiElementHelper.getKvChildren(stepList, YAMLSequenceItem.class).stream().filter(Objects::nonNull).map(Step::new).collect(toMap(Step::idOrNameOrUses, step -> step))).orElse(Collections.emptyMap());
        PsiElementHelper.getChild(element, FIELD_NEEDS).map(YAMLKeyValue::getValue).ifPresent(need -> {
            // Not performant but there is no way to access child nodes
            if (need instanceof final YAMLArrayImpl list) {
                Arrays.stream(list.getChildren()).map(PsiElementHelper::getTextElement).forEach(txtOpt -> txtOpt.ifPresent(txt -> needs.put(txt.getText(), new Definition(txt, txt.getText(), null))));
            } else {
                PsiElementHelper.getTextElement(need).ifPresent(txt -> needs.put(txt.getText(), new Definition(txt, txt.getText(), null)));
            }
        });
    }

    protected void validate(final WorkflowFile workflow) {
        //TODO
        //  matrix
        //  outputs
        //  condition.validate
        //  steps.validate

        // needs -> job
        // job -> [needs]

        final List<Job> previousJobs = workflow.jobs().values().stream().takeWhile(job -> !this.id().equals(job.id())).toList();
        final List<String> undefinedJobs = previousJobs.stream().map(Job::id).filter(jobId -> !needs.containsKey(jobId)).toList();
        needs.values().forEach(need -> previousJobs.stream().filter(job -> need.key().equals(job.id())).findFirst().ifPresentOrElse(job ->
                need.element().ifPresent(element -> element.putUserData(GHW_ELEMENT_REFERENCE_KEY, job.elementPointer())), () ->
                need.element().ifPresent(element -> element.putUserData(GHW_ANNOTATION_KEY, new Annotation(
                        need.key(),
                        undefinedJobs.isEmpty() ? List.of("") : undefinedJobs,
                        "This job id does not match any previous jobId"
                )))
        ));
        steps.values().forEach(step -> step.validate(workflow));
    }

    public String id() {
        return id;
    }

    public List<String> runsOn() {
        return runsOn;
    }

    public Environment environment() {
        return environment;
    }

    public String condition() {
        return condition;
    }

    public int timeoutMinutes() {
        return timeoutMinutes;
    }

    public Map<String, Env> envs() {
        return envs;
    }

    public boolean continueOnError() {
        return continueOnError;
    }

    public Map<String, Output> outputs() {
        return outputs;
    }

    public Map<String, Step> steps() {
        return steps;
    }
}
