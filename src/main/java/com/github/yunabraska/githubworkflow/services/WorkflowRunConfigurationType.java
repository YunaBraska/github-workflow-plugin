package com.github.yunabraska.githubworkflow.services;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Run configuration type used to dispatch GitHub Actions workflows from the IDE.
 */
public final class WorkflowRunConfigurationType implements ConfigurationType {

    public static final String ID = "GitHubWorkflow.RunConfiguration";

    private final ConfigurationFactory factory = new WorkflowRunConfigurationFactory(this);

    public static WorkflowRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(WorkflowRunConfigurationType.class);
    }

    @Override
    public String getDisplayName() {
        return GitHubWorkflowBundle.message("workflow.run.configuration.display");
    }

    @Override
    public String getConfigurationTypeDescription() {
        return GitHubWorkflowBundle.message("workflow.run.configuration.description");
    }

    @Override
    public Icon getIcon() {
        return AllIcons.Actions.Execute;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{factory};
    }

    public ConfigurationFactory factory() {
        return factory;
    }

    private static final class WorkflowRunConfigurationFactory extends ConfigurationFactory {
        private WorkflowRunConfigurationFactory(final ConfigurationType type) {
            super(type);
        }

        @Override
        public @NotNull String getId() {
            return ID + ".Factory";
        }

        @Override
        public RunConfiguration createTemplateConfiguration(@NotNull final Project project) {
            return new WorkflowRunConfiguration(project, this, GitHubWorkflowBundle.message("workflow.run.configuration.display"));
        }
    }
}
