package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
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
}
