package com.github.yunabraska.githubworkflow.state;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubActionCacheTest extends BasePlatformTestCase {

    public void testLocalActionSerializationAndDeserialization() throws IOException {
        // GIVEN
        final String actionPath = localActionPath();
        final GitHubActionCache originalCache = new GitHubActionCache();
        final GitHubAction javaAction = originalCache.get(null, actionPath).resolve();

        // THEN EXPECT
        validateResolvedLocalAction(javaAction, actionPath);
        assertThat(originalCache.get(null, actionPath).expiryTime()).isEqualTo(javaAction.expiryTime());

        // WHEN SERIALIZATION
        final GitHubActionCache.State serializedState = originalCache.getState();

        // WHEN DESERIALIZATION
        final GitHubActionCache newCache = new GitHubActionCache();
        assertThat(newCache.getState().actions).isEmpty();
        newCache.loadState(serializedState);
        assertThat(newCache.getState().actions).isNotEmpty();

        // THEN EXPECT
        assertThat(newCache).isNotEqualTo(originalCache);
        final GitHubAction newJavaAction = newCache.get(null, actionPath);
        validateResolvedLocalAction(newJavaAction, actionPath);
        assertThat(newJavaAction.expiryTime()).isEqualTo(originalCache.get(null, actionPath).expiryTime());
        assertThat(newJavaAction.isResolved()).isEqualTo(originalCache.get(null, actionPath).isResolved());
    }

    public void testExpiredLocalActionKeepsManualSuppressions() throws IOException {
        // GIVEN
        final String actionPath = localActionPath();
        final GitHubActionCache cache = new GitHubActionCache();
        final GitHubAction action = cache.get(null, actionPath).resolve();
        action.suppressInput("manual-input", true);
        action.suppressOutput("manual-output", true);
        action.expiryTime(0);

        // WHEN
        final GitHubAction refreshed = cache.get(null, actionPath);

        // THEN
        assertThat(refreshed).isNotSameAs(action);
        assertThat(refreshed.ignoredInputs()).contains("manual-input");
        assertThat(refreshed.ignoredOutputs()).contains("manual-output");
        assertThat(refreshed.expiryTime()).isGreaterThan(System.currentTimeMillis());
    }

    public void testSummaryCountsDistinctResolvedRemoteAndExpiredActions() throws IOException {
        final GitHubActionCache cache = new GitHubActionCache();
        final GitHubAction remoteAction = GitHubAction.createGithubAction(false, "actions/checkout@v4", "actions/checkout@v4")
                .isResolved(true)
                .expiryTime(0);
        final GitHubAction localAction = GitHubAction.createGithubAction(true, localActionPath(), localActionPath())
                .isResolved(false)
                .expiryTime(System.currentTimeMillis() + 60_000);

        cache.getState().actions.put("actions/checkout@v4", remoteAction);
        cache.getState().actions.put("duplicate-actions/checkout@v4", remoteAction);
        cache.getState().actions.put("local", localAction);

        final GitHubActionCache.CacheSummary summary = cache.summary();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.resolved()).isEqualTo(1);
        assertThat(summary.remote()).isEqualTo(1);
        assertThat(summary.expired()).isEqualTo(1);
        assertThat(summary.suppressed()).isZero();
    }

    public void testClearRemovesCachedActionsAndReturnsEmptySummary() throws IOException {
        final GitHubActionCache cache = new GitHubActionCache();
        final GitHubAction action = GitHubAction.createGithubAction(true, localActionPath(), localActionPath())
                .isResolved(true);
        cache.getState().actions.put(action.usesValue(), action);

        final GitHubActionCache.CacheSummary summary = cache.clear();

        assertThat(summary.total()).isZero();
        assertThat(summary.resolved()).isZero();
        assertThat(summary.remote()).isZero();
        assertThat(summary.expired()).isZero();
        assertThat(summary.suppressed()).isZero();
        assertThat(cache.getState().actions).isEmpty();
    }

    public void testEntriesRemoveAllAndPortableExportImportRoundTrip() throws IOException {
        final GitHubActionCache cache = new GitHubActionCache();
        final GitHubAction action = GitHubAction.createGithubAction(false, "actions/checkout@v4", "actions/checkout@v4")
                .displayName("Checkout")
                .description("Checkout action")
                .isResolved(true)
                .setInputs(java.util.Map.of("fetch-depth", "Fetch depth"))
                .setOutputs(java.util.Map.of("ref", "Checked out ref"));
        cache.getState().actions.put("actions/checkout@v4", action);

        assertThat(cache.entries())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.key()).isEqualTo("actions/checkout@v4");
                    assertThat(entry.name()).isEqualTo("Checkout");
                    assertThat(entry.local()).isFalse();
                    assertThat(entry.resolved()).isTrue();
                });
        assertThat(cache.estimatedSizeBytes()).isPositive();

        final Path export = Files.createTempFile("github-workflow-cache", ".txt");
        cache.exportCache(export);

        final GitHubActionCache imported = new GitHubActionCache();
        imported.importCache(export);

        assertThat(imported.getState().actions).containsKey("actions/checkout@v4");
        assertThat(imported.getState().actions.get("actions/checkout@v4").displayName()).isEqualTo("Checkout");
        assertThat(imported.getState().actions.get("actions/checkout@v4").getInputs()).containsEntry("fetch-depth", "Fetch depth");
        assertThat(imported.removeAll(java.util.List.of("actions/checkout@v4")).total()).isZero();
    }

    public void testRestoreWarningsClearsActionInputAndOutputSuppressions() throws IOException {
        final GitHubActionCache cache = new GitHubActionCache();
        final GitHubAction action = GitHubAction.createGithubAction(true, localActionPath(), localActionPath())
                .isResolved(true)
                .isSuppressed(true)
                .suppressInput("manual-input", true)
                .suppressOutput("manual-output", true);
        cache.getState().actions.put(action.usesValue(), action);

        final long restored = cache.restoreWarnings();

        assertThat(restored).isEqualTo(1);
        assertThat(action.isSuppressed()).isFalse();
        assertThat(action.ignoredInputs()).isEmpty();
        assertThat(action.ignoredOutputs()).isEmpty();
        assertThat(cache.summary().suppressed()).isZero();
    }

    public void testLoadedEmptySuppressionMetadataDoesNotMarkActionSuppressed() {
        final GitHubAction action = new GitHubAction().setMetaData(Map.of(
                "usesValue", "actions/checkout@v4",
                "isSuppressed", "false",
                "ignoredInputs", "",
                "ignoredOutputs", ""
        ));

        assertThat(action.hasSuppressedWarnings()).isFalse();
        assertThat(action.ignoredInputs()).isEmpty();
        assertThat(action.ignoredOutputs()).isEmpty();
    }

    public void testRemoteActionWithoutRefUsesMainAndQueuesResolution() throws Exception {
        final GitHubActionCache cache = new GitHubActionCache();
        final CountDownLatch resolved = new CountDownLatch(1);
        final GitHubActionCache.ActionResolver previous = cache.useActionResolverForTests(action -> {
            action.displayName("Checkout").isResolved(true);
            resolved.countDown();
            return action;
        });
        try {
            final GitHubAction action = cache.get(getProject(), "actions/checkout");

            assertThat(action.isLocal()).isFalse();
            assertThat(action.usesValue()).isEqualTo("actions/checkout@main");
            assertThat(action.isSuppressed()).isFalse();
            assertThat(cache.getState().actions).containsKey("actions/checkout@main");
            assertThat(cache.getState().actions).doesNotContainKey("actions/checkout");
            assertThat(resolved.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(action.isResolved()).isTrue();
        } finally {
            cache.useActionResolverForTests(previous);
        }
    }

    private static String localActionPath() throws IOException {
        final Path actionPath = Files.createTempDirectory("github-workflow-action").resolve("action.yml");
        Files.writeString(actionPath, """
                name: Local Action
                inputs:
                  deep:
                    description: Deep input
                outputs:
                  java_version:
                    description: Java version
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        return actionPath.toString();
    }

    private static void validateResolvedLocalAction(final GitHubAction javaAction, final String actionPath) {
        assertThat(javaAction.getInputs()).isNotEmpty();
        assertThat(javaAction.getOutputs()).isNotEmpty();
        assertThat(javaAction.getInputs()).containsKey("deep");
        assertThat(javaAction.getOutputs()).containsKey("java_version");
        assertThat(javaAction.isLocal()).isTrue();
        assertThat(javaAction.isAction()).isTrue();
        assertThat(javaAction.isResolved()).isTrue();
        assertThat(javaAction.githubUrl()).isEmpty();
        assertThat(javaAction.name()).isEqualTo(actionPath);
        assertThat(javaAction.downloadUrl()).isEqualTo(actionPath);
        assertThat(javaAction.expiryTime()).isGreaterThan(System.currentTimeMillis());
    }
}
