package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.psi.PsiFile;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;

public class WorkflowGutterActionTest extends EditorFeatureTestCase {

    public void testSuppressActionGutterActionTogglesResolvedAction() {
        final GitHubAction action = seedRemoteAction("owner/tool@v1", Map.of(), Map.of());
        configureWorkflowProjectFile("""
                name: Gutter
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                """);

        clickGutterActionContaining("Toggle warnings [off]");

        assertThat(action.isSuppressed()).isTrue();
    }

    public void testSuppressInputGutterActionTogglesResolvedInput() {
        final GitHubAction action = seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());
        configureWorkflowProjectFile("""
                name: Gutter
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          known-input: ok
                """);

        clickGutterActionContaining("known-input");

        assertThat(action.ignoredInputs()).contains("known-input");
    }

    public void testJumpToFileGutterActionOpensLocalActionFile() {
        final PsiFile actionFile = myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        seedLocalAction("./.github/actions/local", actionFile);
        configureWorkflowProjectFile("""
                name: Gutter
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./.github/actions/local
                """);

        clickGutterActionContaining("Jump to file");
    }

    public void testReloadRemoteActionGutterActionUsesResolverBoundaryWithoutSleeping() throws Exception {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of());
        final CountDownLatch resolved = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger(0);
        final GitHubActionCache.ActionResolver previous = getActionCache().useActionResolverForTests(action -> {
            calls.incrementAndGet();
            action.displayName("Reloaded Tool").isResolved(true);
            resolved.countDown();
            return action;
        });
        try {
            configureWorkflowProjectFile("""
                    name: Gutter
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: owner/tool@v1
                    """);

            clickGutterActionContaining("Reload [owner/tool]");

            assertThat(resolved.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(getActionCache().getState().actions.get("owner/tool@v1").displayName()).isEqualTo("Reloaded Tool");
        } finally {
            getActionCache().useActionResolverForTests(previous);
        }
    }
}
