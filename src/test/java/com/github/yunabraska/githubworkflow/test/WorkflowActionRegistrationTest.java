package com.github.yunabraska.githubworkflow.test;

import com.github.yunabraska.githubworkflow.entry.RestoreActionWarningsAction;

import com.github.yunabraska.githubworkflow.entry.ClearActionCacheAction;

import com.github.yunabraska.githubworkflow.entry.RefreshActionCacheAction;

import com.github.yunabraska.githubworkflow.entry.WorkflowRunConfigurationType;

import com.github.yunabraska.githubworkflow.state.PluginSettings;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowActionRegistrationTest extends BasePlatformTestCase {

    public void testCacheActionGroupIsRegisteredFromPluginXml() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.Tools");

        assertThat(action).isInstanceOf(ActionGroup.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("GitHub Workflow");
        assertThat(action.getTemplatePresentation().getDescription()).isEqualTo("GitHub Workflow plugin tools");
    }

    public void testRefreshActionCacheActionIsRegisteredAndLocalized() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.RefreshActionCache");

        assertThat(action).isInstanceOf(RefreshActionCacheAction.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("Refresh Action Cache");
        assertThat(action.getTemplatePresentation().getDescription())
                .isEqualTo("Refresh resolved remote GitHub Actions and reusable workflow metadata");
    }

    public void testClearActionCacheActionIsRegisteredAndLocalized() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.ClearActionCache");

        assertThat(action).isInstanceOf(ClearActionCacheAction.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("Clear Action Cache");
        assertThat(action.getTemplatePresentation().getDescription())
                .isEqualTo("Clear cached GitHub Actions and reusable workflow metadata");
    }

    public void testRestoreActionWarningsActionIsRegisteredAndLocalized() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.RestoreActionWarnings");

        assertThat(action).isInstanceOf(RestoreActionWarningsAction.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("Restore Action Warnings");
        assertThat(action.getTemplatePresentation().getDescription())
                .isEqualTo("Restore suppressed action, input, and output validation warnings");
    }

    public void testActionUpdateUsesConfiguredPluginLanguageOverride() {
        final PluginSettings settings = PluginSettings.getInstance();
        final String previousLanguage = settings.languageTag();
        try {
            settings.languageTag("de");
            final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.RefreshActionCache");
            final Presentation presentation = new Presentation();

            action.update(AnActionEvent.createEvent(
                    action,
                    DataContext.EMPTY_CONTEXT,
                    presentation,
                    "GithubWorkflowPluginTest",
                    ActionUiKind.NONE,
                    null
            ));

            assertThat(presentation.getText()).isEqualTo("Action-Cache aktualisieren");
            assertThat(presentation.getDescription()).contains("entfernter GitHub Actions");
        } finally {
            settings.languageTag(previousLanguage);
        }
    }

    public void testWorkflowRunConfigurationTypeIsRegistered() {
        final WorkflowRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(WorkflowRunConfigurationType.class);

        assertThat(type.getId()).isEqualTo(WorkflowRunConfigurationType.ID);
        assertThat(type.getConfigurationFactories()).hasSize(1);
    }
}
