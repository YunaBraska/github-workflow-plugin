package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.run.WorkflowRunConfiguration;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginWiringTest extends BasePlatformTestCase {

    private static final List<String> SCHEMA_NAMES = List.of(
            "dependabot-2.0",
            "github-action",
            "github-funding",
            "github-workflow",
            "github-discussion",
            "github-issue-forms",
            "github-issue-config",
            "github-workflow-template-properties"
    );

    public void testCacheActionGroupIsRegisteredFromPluginXml() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.Tools");

        assertThat(action).isInstanceOf(ActionGroup.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("GitHub Workflow");
        assertThat(action.getTemplatePresentation().getDescription()).isEqualTo("GitHub Workflow plugin tools");
    }

    public void testGitHubActionCacheRefreshActionIsRegisteredAndLocalized() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.RefreshActionCache");

        assertThat(action).isInstanceOf(GitHubActionCache.RefreshAction.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("Refresh Action Cache");
        assertThat(action.getTemplatePresentation().getDescription())
                .isEqualTo("Refresh resolved remote GitHub Actions and reusable workflow metadata");
    }

    public void testGitHubActionCacheClearActionIsRegisteredAndLocalized() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.ClearActionCache");

        assertThat(action).isInstanceOf(GitHubActionCache.ClearAction.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("Clear Action Cache");
        assertThat(action.getTemplatePresentation().getDescription())
                .isEqualTo("Clear cached GitHub Actions and reusable workflow metadata");
    }

    public void testGitHubActionCacheRestoreWarningsActionIsRegisteredAndLocalized() {
        final AnAction action = ActionManager.getInstance().getAction("GitHubWorkflow.RestoreActionWarnings");

        assertThat(action).isInstanceOf(GitHubActionCache.RestoreWarningsAction.class);
        assertThat(action.getTemplatePresentation().getText()).isEqualTo("Restore Action Warnings");
        assertThat(action.getTemplatePresentation().getDescription())
                .isEqualTo("Restore suppressed action, input, and output validation warnings");
    }

    public void testActionUpdateUsesConfiguredPluginLanguageOverride() {
        final GitHubWorkflowBundle.Settings settings = GitHubWorkflowBundle.Settings.getInstance();
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

    public void testWorkflowRunConfigurationIsRegistered() {
        final WorkflowRunConfiguration.Type type = ConfigurationTypeUtil.findConfigurationType(WorkflowRunConfiguration.Type.class);

        assertThat(type.getId()).isEqualTo(WorkflowRunConfiguration.Type.ID);
        assertThat(type.getConfigurationFactories()).hasSize(1);
    }

    public void testPackagedSchemasArePresentAndNonEmpty() throws IOException {
        final Path directory = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "schemas");

        for (final String schemaName : SCHEMA_NAMES) {
            final Path schema = directory.resolve(schemaName + ".json");
            assertThat(schema).exists().isRegularFile();
            assertThat(Files.readString(schema))
                    .startsWith("{")
                    .contains("\"$schema\"")
                    .contains("\"$id\"");
        }
    }
}
