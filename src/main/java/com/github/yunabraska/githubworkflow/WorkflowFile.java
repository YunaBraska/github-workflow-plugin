package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.intellij.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLScalarListImpl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.toLinkedHashMap;

public class WorkflowFile extends WorkflowObject{

    private final String name;
    private final String runName;
    private final Map<String, Input> inputs;
    private final Map<String, Secret> secrets;
    private final Map<String, Output> outputs;
    private final Map<String, Env> envs;
    private final Map<String, Job> jobs;

    public WorkflowFile(final PsiElement element) {
        super(element);
        final Optional<YAMLKeyValue> on = PsiElementHelper.getChild(element, FIELD_ON);
        name = PsiElementHelper.getKvChildren(element).stream().filter(kv -> "name".equals(kv.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).orElse(null);
        runName = PsiElementHelper.getKvChildren(element).stream().filter(kv -> "run-name".equals(kv.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).orElse(null);
        //TODO: merge inputs & secrets from workflow_call && workflow_dispatch && workflow_run
        inputs = on.flatMap(kv -> PsiElementHelper.getChild(kv, FIELD_INPUTS)).map(PsiElementHelper::getKvChildren)
                .map(children -> children.stream().map(Input::new).collect(toLinkedHashMap(Input::id, input -> input)))
                .orElse(Collections.emptyMap());
        //TODO: merge inputs & secrets from workflow_call && workflow_dispatch && workflow_run
        outputs = on.flatMap(kv -> PsiElementHelper.getChild(kv, FIELD_OUTPUTS)).map(PsiElementHelper::getKvChildren)
                .map(children -> children.stream().map(Output::new).collect(toLinkedHashMap(Output::id, input -> input)))
                .orElse(Collections.emptyMap());
        secrets = on.flatMap(kv -> PsiElementHelper.getChild(kv, FIELD_SECRETS)).map(PsiElementHelper::getKvChildren)
                .map(children -> children.stream().map(Secret::new).collect(toLinkedHashMap(Secret::id, secret -> secret)))
                .orElse(Collections.emptyMap());
        envs = PsiElementHelper.getChild(element, FIELD_ENVS).map(PsiElementHelper::getKvChildren).map(children -> children.stream().map(Env::new).collect(toLinkedHashMap(Env::id, env -> env)))
                .orElse(Collections.emptyMap());
        jobs = PsiElementHelper.getChild(element, FIELD_JOBS).map(PsiElementHelper::getKvChildren)
                .map(children -> children.stream().map(Job::new).collect(toLinkedHashMap(Job::id, job -> job)))
                .orElse(Collections.emptyMap());
        validate();
    }

    private void validate() {
        jobs.values().forEach(job -> job.validate(this));
        //TODO
        //  REF_ELEMENT = navigates to element
        //  UNUSED = grey, mark as delete
        //  INVALID = red, mark as delete
        //  INCOMPLETE = red, mark as delete
        //  REPLACE_WITH = red, adds possibilities
        //  GITHUB_ACTION = for actions (disable validations, etc.)
    }

    public String name() {
        return name;
    }

    public String runName() {
        return runName;
    }

    public Map<String, Input> inputs() {
        return inputs;
    }

    public Map<String, Secret> secrets() {
        return secrets;
    }

    public Map<String, Output> outputs() {
        return outputs;
    }

    public Map<String, Env> envs() {
        return envs;
    }

    public Map<String, Job> jobs() {
        return jobs;
    }
}
