package com.github.yunabraska.githubworkflow.test;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

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
    public void testLocaleBundleValuesAreTranslatedAndKeepPlaceholders() throws IOException {
        final Properties defaultBundle = loadBundle("");
        final Set<String> technicalKeysAllowedToMatchEnglish = Set.of(
                "workflow.run.field.apiUrl",
                "workflow.run.field.ref",
                "workflow.run.auth.settings",
                "workflow.log.error",
                "workflow.run.status",
                "workflow.run.job.status",
                "workflow.run.job.url",
                "workflow.run.state.ok",
                "error.report.ide",
                "error.report.os",
                "error.report.stacktrace",
                "completion.shell.powershell"
        );
        final Pattern placeholder = Pattern.compile("\\{\\d+\\}");
        final Pattern leakedTranslationToken = Pattern.compile("XG[A-Z]*[HT]\\d+X|QZQARG\\d+QZQ", Pattern.CASE_INSENSITIVE);
        for (final String suffix : LOCALE_SUFFIXES) {
            final Properties localizedBundle = loadBundle("_" + suffix);
            for (final String key : defaultBundle.stringPropertyNames()) {
                final String localized = localizedBundle.getProperty(key);
                assertThat(localized)
                        .as("Locale suffix [%s] key [%s] is real text", suffix, key)
                        .doesNotContain("Ã")
                        .doesNotContain("Â")
                        .doesNotContain("�");
                assertThat(leakedTranslationToken.matcher(localized).find())
                        .as("Locale suffix [%s] key [%s] has no leaked placeholder token", suffix, key)
                        .isFalse();
                assertThat(placeholder.matcher(localized).results().map(match -> match.group()).toList())
                        .as("Locale suffix [%s] key [%s] keeps placeholders", suffix, key)
                        .containsExactlyInAnyOrderElementsOf(placeholder.matcher(defaultBundle.getProperty(key)).results().map(match -> match.group()).toList());
                if (!technicalKeysAllowedToMatchEnglish.contains(key)) {
                    assertThat(localized)
                            .as("Locale suffix [%s] key [%s] is not the default English fallback", suffix, key)
                            .isNotEqualTo(defaultBundle.getProperty(key));
                }
            }
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
    public void testWorkflowRunMessagesStayShortAndResolvedForEveryLocale() throws IOException {
        assertThat(loadBundle("").stringPropertyNames())
                .doesNotContain(
                        "workflow.run.delete.button",
                        "workflow.run.download.log.button",
                        "workflow.run.download.artifacts.button"
                );
        final List<String> keys = List.of(
                "workflow.run.gutter.stop",
                "workflow.run.gutter.stop.description",
                "workflow.run.cancel.requested",
                "workflow.run.stop.before.id",
                "workflow.run.cancel.http",
                "workflow.run.cancel.failed",
                "workflow.run.discovery",
                "workflow.run.discovery.none",
                "workflow.run.delete.tooltip",
                "workflow.run.delete.noRun",
                "workflow.run.delete.requested",
                "workflow.run.delete.done",
                "workflow.run.delete.http",
                "workflow.run.delete.failed",
                "workflow.run.rerun.all.tooltip",
                "workflow.run.rerun.failed.tooltip",
                "workflow.run.rerun.noRun",
                "workflow.run.rerun.all.requested",
                "workflow.run.rerun.failed.requested",
                "workflow.run.rerun.all.done",
                "workflow.run.rerun.failed.done",
                "workflow.run.rerun.http",
                "workflow.run.rerun.failed",
                "workflow.run.download.log.tooltip",
                "workflow.run.download.artifacts.tooltip",
                "workflow.run.download.noRun",
                "workflow.run.download.log.requested",
                "workflow.run.download.log.done",
                "workflow.run.download.artifacts.requested",
                "workflow.run.download.artifacts.empty",
                "workflow.run.download.artifact.expired",
                "workflow.run.download.artifact.done",
                "workflow.run.download.failed"
        );
        for (final String suffix : LOCALE_SUFFIXES) {
            final Locale locale = Locale.forLanguageTag(suffix.replace('_', '-'));
            for (final String key : keys) {
                final String message = GitHubWorkflowBundle.messageFor(locale, key, 42, "artifact.zip");
                assertThat(message)
                        .as("Locale suffix [%s] key [%s]", suffix, key)
                        .isNotBlank()
                        .doesNotContain("GitHub accepted failed-job re-run for workflow run")
                        .doesNotContain("there is nothing")
                        .hasSizeLessThanOrEqualTo(80);
            }
        }
    }

    @Test
    public void testWorkflowSyntaxCompletionDescriptionsResolveForEveryLocale() {
        final List<String> keys = List.of(
                "completion.workflow.top.on",
                "completion.workflow.top.permissions",
                "completion.workflow.event.push",
                "completion.workflow.event.workflow_dispatch",
                "completion.workflow.event.workflow_call",
                "completion.workflow.event.image_version",
                "completion.workflow.eventFilter.types",
                "completion.workflow.eventFilter.branches",
                "completion.workflow.permission.contents",
                "completion.workflow.permission.artifact-metadata",
                "completion.workflow.permission.code-quality",
                "completion.workflow.permission.id-token",
                "completion.workflow.permission.models",
                "completion.workflow.permission.vulnerability-alerts",
                "completion.workflow.permission.value.read",
                "completion.workflow.permission.value.write",
                "completion.workflow.permission.value.none",
                "completion.workflow.permission.shorthand.read-all",
                "completion.workflow.permission.shorthand.write-all",
                "completion.workflow.permission.shorthand.empty",
                "completion.workflow.job.runs-on",
                "completion.workflow.job.steps",
                "completion.workflow.step.uses",
                "completion.workflow.step.run",
                "completion.workflow.defaultsRun.shell",
                "completion.workflow.concurrency.group",
                "completion.workflow.concurrency.cancel-in-progress",
                "completion.workflow.environment.name",
                "completion.workflow.environment.url",
                "completion.workflow.strategy.matrix",
                "completion.workflow.matrix.include",
                "completion.workflow.container.image",
                "completion.workflow.service.image",
                "completion.workflow.credentials.username",
                "completion.workflow.credentials.password",
                "completion.workflow.inputType.choice",
                "completion.workflow.boolean.true",
                "completion.workflow.runner.ubuntu-latest"
        );
        for (final String suffix : LOCALE_SUFFIXES) {
            final Locale locale = Locale.forLanguageTag(suffix.replace('_', '-'));
            for (final String key : keys) {
                assertThat(GitHubWorkflowBundle.messageFor(locale, key))
                        .as("Locale suffix [%s] key [%s]", suffix, key)
                        .isNotBlank()
                        .isNotEqualTo(GitHubWorkflowBundle.messageFor(locale, "completion.workflow.syntax"));
            }
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

    @Test
    public void testCoreInspectionMessagesAreLocalizedForEveryLocale() throws IOException {
        final Properties defaultBundle = loadBundle("");
        final List<String> keys = List.of(
                "inspection.action.reload",
                "inspection.warning.toggle",
                "inspection.action.update.major",
                "inspection.statement.incomplete",
                "inspection.invalid.suffix.remove",
                "inspection.replace.with",
                "inspection.invalid.remove",
                "inspection.action.unresolved",
                "inspection.action.jump",
                "inspection.secret.invalid.if",
                "inspection.secret.replace.runtime",
                "inspection.needs.invalid.job",
                "inspection.workflow.syntax.unknownTopLevelKey",
                "inspection.workflow.syntax.unknownEventKey",
                "inspection.workflow.syntax.unknownTriggerKey",
                "inspection.workflow.syntax.unknownTriggerFilter",
                "inspection.workflow.syntax.unknownTriggerValue",
                "inspection.workflow.syntax.unknownPermission",
                "inspection.workflow.syntax.unknownPermissionValue",
                "inspection.workflow.syntax.unknownJobKey",
                "inspection.workflow.syntax.unknownStepKey",
                "workflow.run.gutter.stop",
                "workflow.run.gutter.stop.description",
                "workflow.cache.progress.title"
        );
        for (final String suffix : LOCALE_SUFFIXES) {
            final Properties localizedBundle = loadBundle("_" + suffix);
            for (final String key : keys) {
                assertThat(localizedBundle.getProperty(key))
                        .as("Locale suffix [%s] key [%s] is not the default English fallback", suffix, key)
                        .isNotEqualTo(defaultBundle.getProperty(key));
            }
        }
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
