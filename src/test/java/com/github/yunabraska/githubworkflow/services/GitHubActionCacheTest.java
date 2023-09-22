package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ALL")
public class GitHubActionCacheTest extends BasePlatformTestCase {

    @Test
    public void testSerializationAndDeserialization() throws InterruptedException {
        // GIVEN
        final Project project = getProject();
        final GitHubActionCache originalCache = getActionCache();
        originalCache.get(project, "actions/checkout@main");
        originalCache.get(project, "actions/setup-python@main");
        final GitHubAction javaAction = originalCache.get(project, "actions/setup-java@main").resolve();

        // THEN EXPECT
        validateResolvedJavaAction(javaAction);
        assertThat(javaAction.expiryTime()).isEqualTo(getActionCache().get(project, "actions/setup-java@main").expiryTime());

        // WHEN SERIALIZATION
        Thread.sleep(25);
        final GitHubActionCache.State serializedState = originalCache.getState();

        // WHEN DESERIALIZATION
        Thread.sleep(25);
        final GitHubActionCache newCache = new GitHubActionCache();
        assertThat(newCache.getState().actions).isEmpty();
        newCache.loadState(serializedState);
        assertThat(newCache.getState().actions).hasSize(3);


        // THEN EXPECT
        assertThat(newCache).isNotEqualTo(originalCache);
        final GitHubAction newJavaAction = newCache.get(project, "actions/setup-java@main");
        validateResolvedJavaAction(newJavaAction);
        assertThat(newJavaAction.expiryTime()).isEqualTo(originalCache.get(project, "actions/setup-java@main").expiryTime());
        assertThat(newJavaAction.isResolved()).isEqualTo(originalCache.get(project, "actions/setup-java@main").isResolved());

        // WHEN RELOAD
        Thread.sleep(25);
        final GitHubAction reloadedAction = originalCache.reloadAsync(project, "actions/setup-java@main");
        validateResolvedJavaAction(reloadedAction);
        assertThat(reloadedAction.expiryTime()).isNotEqualTo(javaAction.expiryTime());
    }

    private static void validateResolvedJavaAction(final GitHubAction javaAction) {
        assertThat(javaAction.getInputs()).isNotEmpty();
        assertThat(javaAction.getOutputs()).isNotEmpty();
        assertThat(javaAction.isLocal()).isFalse();
        assertThat(javaAction.isAction()).isTrue();
        assertThat(javaAction.isResolved()).isTrue();
        assertThat(javaAction.githubUrl()).isNotNull();
        assertThat(javaAction.name()).isEqualTo("actions/setup-java");
        assertThat(javaAction.downloadUrl()).isEqualTo("https://raw.githubusercontent.com/actions/setup-java/main/action.yml");
        assertThat(javaAction.expiryTime()).isGreaterThan(System.currentTimeMillis());
    }
}
