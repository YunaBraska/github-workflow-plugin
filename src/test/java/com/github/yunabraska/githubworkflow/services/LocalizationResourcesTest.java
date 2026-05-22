package com.github.yunabraska.githubworkflow.services;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationResourcesTest {

    private static final String BUNDLE_PATH = "messages/GitHubWorkflowBundle";
    private static final List<String> LOCALE_SUFFIXES = List.of(
            "ar",
            "cs",
            "de",
            "es",
            "fr",
            "hi",
            "id",
            "it",
            "ja",
            "ko",
            "nl",
            "pl",
            "pt_BR",
            "ru",
            "sv",
            "th",
            "tr",
            "uk",
            "vi",
            "zh_CN"
    );

    @Test
    public void testDefaultBundleReturnsActionCacheMessages() {
        assertThat(GitHubWorkflowBundle.message("action.GitHubWorkflow.ClearActionCache.text"))
                .isEqualTo("Clear Action Cache");
        assertThat(GitHubWorkflowBundle.message("notification.cache.cleared", 3))
                .isEqualTo("Cleared 3 cached GitHub Workflow entries.");
    }

    @Test
    public void testTopTwentyLocaleBundlesHaveTheSameKeysAsDefaultBundle() throws IOException {
        final Properties defaultBundle = loadBundle("");

        assertThat(LOCALE_SUFFIXES).hasSize(20);
        for (final String suffix : LOCALE_SUFFIXES) {
            final Properties localizedBundle = loadBundle("_" + suffix);
            assertThat(localizedBundle.keySet())
                    .as("Locale suffix [%s] has the same keys", suffix)
                    .containsExactlyInAnyOrderElementsOf(defaultBundle.keySet());
        }
    }

    @Test
    public void testLocaleBundleValuesAreNotBlank() throws IOException {
        for (final String suffix : LOCALE_SUFFIXES) {
            final Properties bundle = loadBundle("_" + suffix);
            assertThat(bundle.stringPropertyNames())
                    .as("Locale suffix [%s] contains keys", suffix)
                    .isNotEmpty()
                    .allSatisfy(key -> assertThat(bundle.getProperty(key))
                            .as("Locale suffix [%s] key [%s]", suffix, key)
                            .isNotBlank());
        }
    }

    @Test
    public void testEveryConfiguredLocaleResolvesSettingsAndInspectionMessages() {
        for (final String suffix : LOCALE_SUFFIXES) {
            final Locale locale = Locale.forLanguageTag(suffix.replace('_', '-'));
            assertThat(GitHubWorkflowBundle.messageFor(locale, "settings.displayName")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "inspection.action.delete.invalid", "input", "bad"))
                    .contains("bad");
            assertThat(GitHubWorkflowBundle.messageFor(locale, "inspection.output.unused", "artifact"))
                    .contains("artifact");
            assertThat(GitHubWorkflowBundle.messageFor(locale, "workflow.run.log.failed", "boom"))
                    .contains("boom");
            assertThat(GitHubWorkflowBundle.messageFor(locale, "workflow.log.command")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "error.report.pluginVersion", "2026.05.22"))
                    .contains("2026.05.22");
            assertThat(GitHubWorkflowBundle.messageFor(locale, "completion.runner.name")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "completion.uses.local.action")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "documentation.context.github.description")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "documentation.step.title", "build"))
                    .contains("build");
            assertThat(GitHubWorkflowBundle.messageFor(locale, "settings.cache.summary", 1, 2, 3, 4, 5, 6))
                    .contains("1");
            assertThat(GitHubWorkflowBundle.messageFor(locale, "workflow.run.tree.done")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "workflow.run.tree.failed")).isNotBlank();
            assertThat(GitHubWorkflowBundle.messageFor(locale, "workflow.run.tree.warn")).isNotBlank();
        }
    }

    @Test
    public void testGermanInspectionAndCacheMessagesAreNotEnglishFallbacks() {
        final Locale locale = Locale.forLanguageTag("de");

        assertThat(GitHubWorkflowBundle.messageFor(locale, "inspection.action.reload", "YunaBraska/YunaBraska"))
                .contains("Neu laden")
                .doesNotContain("Reload");
        assertThat(GitHubWorkflowBundle.messageFor(locale, "inspection.warning.toggle", "aus", "YunaBraska/YunaBraska"))
                .contains("Warnungen")
                .doesNotContain("Toggle warnings");
        assertThat(GitHubWorkflowBundle.messageFor(locale, "settings.cache.summary", 1, 1, 1, 0, 0, 60))
                .contains("Cache: 60 KB")
                .doesNotContain("IDE-Heap")
                .doesNotContain("Heap");
        assertThat(GitHubWorkflowBundle.messageFor(locale, "workflow.run.tree.failed"))
                .contains("fehlgeschlagen")
                .doesNotContain("failed");
    }

    private static Properties loadBundle(final String suffix) throws IOException {
        final String path = BUNDLE_PATH + suffix + ".properties";
        try (InputStream stream = LocalizationResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("Bundle [%s] exists", path).isNotNull();
            final Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        }
    }
}
