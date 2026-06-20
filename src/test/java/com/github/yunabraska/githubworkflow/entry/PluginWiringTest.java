package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.run.WorkflowRunConfiguration;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.github.yunabraska.githubworkflow.settings.GitHubWorkflowSettingsConfigurable;
import com.github.yunabraska.githubworkflow.settings.GiteaSettingsConfigurable;

import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;

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

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    public void testSchemaProviderNameUsesConfiguredPluginLanguageOverride() {
        final GitHubWorkflowBundle.Settings settings = GitHubWorkflowBundle.Settings.getInstance();
        final String previousLanguage = settings.languageTag();
        try {
            final GitHubSchemaProvider schema = new GitHubSchemaProvider("github-workflow", "GitHub Workflow", path -> true);

            assertThat(schema.getName()).isEqualTo("GitHub Workflow [Auto]");

            settings.languageTag("de");

            assertThat(schema.getName()).isEqualTo("GitHub Workflow [Automatisch]");
        } finally {
            settings.languageTag(previousLanguage);
        }
    }

    public void testWorkflowRunConfigurationIsRegistered() {
        final WorkflowRunConfiguration.Type type = ConfigurationTypeUtil.findConfigurationType(WorkflowRunConfiguration.Type.class);

        assertThat(type.getId()).isEqualTo(WorkflowRunConfiguration.Type.ID);
        assertThat(type.getConfigurationFactories()).hasSize(1);
    }

    public void testSettingsConfigurableUsesLocalizedPluginXmlKey() throws IOException {
        final String pluginXml = Files.readString(Path.of(System.getProperty("user.dir"), "src", "main", "resources", "META-INF", "plugin.xml"));

        assertThat(pluginXml)
                .contains("key=\"settings.displayName\"")
                .contains("key=\"settings.gitea.displayName\"")
                .contains("id=\"github.workflow.gitea.settings\"")
                .contains("<projectConfigurable instance=\"com.github.yunabraska.githubworkflow.settings.GiteaSettingsConfigurable\"")
                .contains("parentId=\"project.propVCSSupport.Mappings\"")
                .contains("bundle=\"messages.GitHubWorkflowBundle\"")
                .doesNotContain("displayName=\"GitHub Workflow\"");
    }

    public void testSettingsConfigurableRefreshesVisibleTextsAfterLanguageApply() {
        final GitHubWorkflowBundle.Settings settings = GitHubWorkflowBundle.Settings.getInstance();
        final String previousLanguage = settings.languageTag();
        final GitHubWorkflowSettingsConfigurable configurable = new GitHubWorkflowSettingsConfigurable();
        try {
            settings.languageTag(GitHubWorkflowBundle.Settings.SYSTEM_LANGUAGE);
            final JComponent component = configurable.createComponent();

            assertThat(visibleTexts(component)).contains("Language:");

            selectComboItem(component, "Deutsch");
            configurable.apply();

            assertThat(visibleTexts(component))
                    .contains(GitHubWorkflowBundle.message("settings.language.label"))
                    .contains(GitHubWorkflowBundle.message("settings.cache.refresh"))
                    .contains(GitHubWorkflowBundle.message("settings.cache.title"));
        } finally {
            configurable.disposeUIResources();
            settings.languageTag(previousLanguage);
        }
    }

    public void testGiteaSettingsConfigurableIsLocalizedUnderVersionControl() {
        final GitHubWorkflowBundle.Settings settings = GitHubWorkflowBundle.Settings.getInstance();
        final String previousLanguage = settings.languageTag();
        final GiteaSettingsConfigurable configurable = new GiteaSettingsConfigurable();
        try {
            settings.languageTag("de");
            final JComponent component = configurable.createComponent();

            assertThat(configurable.getDisplayName()).isEqualTo("Gitea");
            assertThat(visibleTexts(component))
                    .contains(GitHubWorkflowBundle.message("settings.gitea.title"))
                    .contains(GitHubWorkflowBundle.message("settings.gitea.add"))
                    .contains(GitHubWorkflowBundle.message("settings.gitea.remove"))
                    .contains(GitHubWorkflowBundle.message("settings.gitea.setToken"))
                    .contains(GitHubWorkflowBundle.message("settings.gitea.clearToken"));
        } finally {
            configurable.disposeUIResources();
            settings.languageTag(previousLanguage);
        }
    }

    public void testGiteaSettingsInvalidRowsKeepApplyAvailable() {
        final RemoteActionProviders.Settings remoteSettings = RemoteActionProviders.Settings.getInstance();
        final List<RemoteActionProviders.Server> previousServers = remoteSettings.customServers();
        final GiteaSettingsConfigurable configurable = new GiteaSettingsConfigurable();
        try {
            remoteSettings.setCustomServers(List.of());
            final JComponent component = configurable.createComponent();
            findButton(component, GitHubWorkflowBundle.message("settings.gitea.add")).doClick();
            final JTable table = findTable(component);

            table.setValueAt("", 0, 3);

            assertThat(configurable.isModified()).isTrue();
        } finally {
            configurable.disposeUIResources();
            remoteSettings.setCustomServers(previousServers);
        }
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

    private static void selectComboItem(final Component root, final String text) {
        for (final Component component : components(root)) {
            if (component instanceof JComboBox<?> comboBox) {
                for (int index = 0; index < comboBox.getItemCount(); index++) {
                    if (text.equals(String.valueOf(comboBox.getItemAt(index)))) {
                        comboBox.setSelectedIndex(index);
                        return;
                    }
                }
            }
        }
        throw new AssertionError("Combo item not found: " + text);
    }

    private static AbstractButton findButton(final Component root, final String text) {
        for (final Component component : components(root)) {
            if (component instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static JTable findTable(final Component root) {
        for (final Component component : components(root)) {
            if (component instanceof JTable table) {
                return table;
            }
        }
        throw new AssertionError("Table not found");
    }

    private static List<String> visibleTexts(final Component root) {
        final List<String> result = new ArrayList<>();
        for (final Component component : components(root)) {
            if (component instanceof JLabel label && label.getText() != null && !label.getText().isBlank()) {
                result.add(label.getText());
            }
            if (component instanceof AbstractButton button && button.getText() != null && !button.getText().isBlank()) {
                result.add(button.getText());
            }
            if (component instanceof JComponent swing && swing.getBorder() instanceof TitledBorder border && border.getTitle() != null) {
                result.add(border.getTitle());
            }
        }
        return result;
    }

    private static List<Component> components(final Component root) {
        final List<Component> result = new ArrayList<>();
        collectComponents(root, result);
        return result;
    }

    private static void collectComponents(final Component component, final List<Component> result) {
        result.add(component);
        if (component instanceof Container container) {
            for (final Component child : container.getComponents()) {
                collectComponents(child, result);
            }
        }
    }
}
