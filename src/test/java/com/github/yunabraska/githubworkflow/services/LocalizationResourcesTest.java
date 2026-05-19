package com.github.yunabraska.githubworkflow.services;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
