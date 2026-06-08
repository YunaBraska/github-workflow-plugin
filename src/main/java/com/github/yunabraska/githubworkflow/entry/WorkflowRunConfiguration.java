package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.settings.WorkflowRunSettingsEditor;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.client.WorkflowRunClient;
import com.github.yunabraska.githubworkflow.run.WorkflowRunProcessHandler;
import com.github.yunabraska.githubworkflow.run.WorkflowRunRequest;
import com.github.yunabraska.githubworkflow.syntax.WorkflowDispatchInputs;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Run configuration that dispatches a workflow_dispatch event and follows the resulting run.
 */
public class WorkflowRunConfiguration extends RunConfigurationBase<Object> {

    private String apiUrl = "https://api.github.com";
    private String owner = "";
    private String repo = "";
    private String workflowPath = "";
    private String ref = "main";
    private String tokenEnvVar = "";
    private String inputsText = "";

    public WorkflowRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new WorkflowRunSettingsEditor();
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment environment) {
        return new CommandLineState(environment) {
            @Override
            protected @NotNull ProcessHandler startProcess() throws ExecutionException {
                return new WorkflowRunProcessHandler(getProject(), toRequest(), new WorkflowRunClient(getProject()), environment.getExecutor());
            }
        };
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (isBlank(apiUrl)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.apiUrl"));
        }
        if (isBlank(owner) || isBlank(repo)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.repository"));
        }
        if (isBlank(workflowPath)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.workflow"));
        }
        if (isBlank(ref)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.ref"));
        }
        if (WorkflowDispatchInputs.parseKeyValueText(inputsText).size() > 25) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.inputs"));
        }
    }

    @Override
    public void readExternal(@NotNull final Element element) throws InvalidDataException {
        super.readExternal(element);
        apiUrl = value(element, "apiUrl", apiUrl);
        owner = value(element, "owner", owner);
        repo = value(element, "repo", repo);
        workflowPath = value(element, "workflowPath", workflowPath);
        ref = value(element, "ref", ref);
        tokenEnvVar = value(element, "tokenEnvVar", tokenEnvVar);
        inputsText = value(element, "inputsText", inputsText);
    }

    @Override
    public void writeExternal(@NotNull final Element element) {
        super.writeExternal(element);
        element.setAttribute("apiUrl", apiUrl);
        element.setAttribute("owner", owner);
        element.setAttribute("repo", repo);
        element.setAttribute("workflowPath", workflowPath);
        element.setAttribute("ref", ref);
        element.setAttribute("tokenEnvVar", tokenEnvVar);
        element.setAttribute("inputsText", inputsText);
    }

    public WorkflowRunRequest toRequest() {
        final Map<String, String> inputs = WorkflowDispatchInputs.parseKeyValueText(inputsText);
        return new WorkflowRunRequest(apiUrl, owner, repo, workflowPath, ref, inputs, tokenEnvVar);
    }

    public String apiUrl() {
        return apiUrl;
    }

    public WorkflowRunConfiguration apiUrl(final String apiUrl) {
        this.apiUrl = clean(apiUrl);
        return this;
    }

    public String owner() {
        return owner;
    }

    public WorkflowRunConfiguration owner(final String owner) {
        this.owner = clean(owner);
        return this;
    }

    public String repo() {
        return repo;
    }

    public WorkflowRunConfiguration repo(final String repo) {
        this.repo = clean(repo);
        return this;
    }

    public String workflowPath() {
        return workflowPath;
    }

    public WorkflowRunConfiguration workflowPath(final String workflowPath) {
        this.workflowPath = clean(workflowPath);
        return this;
    }

    public String ref() {
        return ref;
    }

    public WorkflowRunConfiguration ref(final String ref) {
        this.ref = clean(ref);
        return this;
    }

    public String tokenEnvVar() {
        return tokenEnvVar;
    }

    public WorkflowRunConfiguration tokenEnvVar(final String tokenEnvVar) {
        this.tokenEnvVar = clean(tokenEnvVar);
        return this;
    }

    public String inputsText() {
        return inputsText;
    }

    public WorkflowRunConfiguration inputsText(final String inputsText) {
        this.inputsText = inputsText == null ? "" : inputsText;
        return this;
    }

    private static String value(final Element element, final String name, final String fallback) {
        final String value = element.getAttributeValue(name);
        return value == null ? fallback : value;
    }

    private static String clean(final String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
