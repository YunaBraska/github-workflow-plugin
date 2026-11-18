package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.test.EditorFeatureTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import org.junit.Assume;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class JetBrainsAuthoringIntegrationTest extends EditorFeatureTestCase {
    public void testNativeActionReloadIntentionRemainsAvailable() throws Exception {
        final var descriptor = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.plugins.github"));
        Assume.assumeTrue("Bundled GitHub plugin is unavailable", descriptor != null);
        try {
            descriptor.getPluginClassLoader().loadClass(
                    "com.intellij.vcs.github.ultimate.features.ide.intentions.ReloadActionIntention");
        } catch (final ClassNotFoundException unavailable) {
            Assume.assumeNoException("Bundled GitHub Actions intention is unavailable", unavailable);
            return;
        }
        Assume.assumeTrue("GitHub Actions authoring is disabled", AdvancedSettings.getBoolean("github.actions.support.enabled"));
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of());
        configureWorkflowProjectFile("""
                name: Reload action
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/<caret>tool@v1
                """);

        assertThat(myFixture.getAvailableIntentions())
                .anyMatch(action -> action.getText().equals("Reload action/workflow"));
    }

    public void testNativeCyclicJobInspectionRemainsAvailable() throws Exception {
        final var descriptor = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.plugins.github"));
        Assume.assumeTrue("Bundled GitHub plugin is unavailable", descriptor != null);
        final Class<?> inspection;
        try {
            inspection = descriptor.getPluginClassLoader().loadClass(
                    "com.intellij.vcs.github.ultimate.features.ide.inspections.workflow.CyclicJobDependencyInspection");
        } catch (final ClassNotFoundException unavailable) {
            Assume.assumeNoException("Bundled GitHub Actions inspection is unavailable", unavailable);
            return;
        }
        Assume.assumeTrue("GitHub Actions authoring is disabled", AdvancedSettings.getBoolean("github.actions.support.enabled"));
        myFixture.enableInspections((LocalInspectionTool) inspection.getDeclaredConstructor().newInstance());
        configureWorkflowProjectFile("""
                name: Cyclic jobs
                on: push
                jobs:
                  alpha:
                    needs: beta
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo alpha
                  beta:
                    needs: alpha
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo beta
                """);

        assertThat(myFixture.doHighlighting().stream()
                .map(info -> info.getDescription())
                .filter(Objects::nonNull))
                .anyMatch(message -> message.contains("Cyclic job dependency"));
    }
}
