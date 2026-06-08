package com.github.yunabraska.githubworkflow.test;

import com.github.yunabraska.githubworkflow.entry.WorkflowRunConfigurationProducer;
import com.github.yunabraska.githubworkflow.entry.WorkflowRunLineMarkerContributor;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import com.github.yunabraska.githubworkflow.state.WorkflowRunTracker;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;

public class WorkflowGutterActionTest extends EditorFeatureTestCase {

    public void testSuppressActionQuickFixTogglesResolvedAction() {
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

        invokeHighlightFixContaining("Toggle warnings [off]");

        assertThat(action.isSuppressed()).isTrue();
    }

    public void testSuppressInputQuickFixTogglesResolvedInput() {
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

        invokeHighlightFixContaining("known-input");

        assertThat(action.ignoredInputs()).contains("known-input");
    }

    public void testJumpToFileQuickFixOpensLocalActionFile() {
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

        assertThat(gutterIcons()).anySatisfy(gutter -> assertThat(gutter.getTooltipText()).contains("Jump to file"));
        invokeHighlightFixContaining("Jump to file");
    }

    public void testRunOutputAndEnvDeclarationsKeepReferenceGutterIndicators() {
        configureWorkflowProjectFile("""
                name: Gutter
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "ACTION_STATE=yellow" >> "$GITHUB_ENV"
                      - run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """);

        assertThat(gutterIcons().stream().map(gutter -> gutter.getIcon()).toList())
                .contains(ICON_ENV.icon(), ICON_TEXT_VARIABLE.icon());
    }

    public void testReloadRemoteActionQuickFixUsesResolverBoundaryWithoutSleeping() throws Exception {
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

            invokeHighlightFixContaining("Reload [owner/tool]");

            assertThat(resolved.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(getActionCache().getState().actions.get("owner/tool@v1").displayName()).isEqualTo("Reloaded Tool");
        } finally {
            getActionCache().useActionResolverForTests(previous);
        }
    }

    public void testResolvedActionFixesStayOutOfTheEditorGutter() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of()).remoteRefs(List.of("v1", "v2"));
        configureWorkflowProjectFile("""
                name: Gutter
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                """);

        final List<String> tooltips = gutterIcons().stream()
                .map(gutter -> gutter.getTooltipText() == null ? "" : gutter.getTooltipText())
                .filter(tooltip -> tooltip.contains("owner/tool"))
                .toList();

        assertThat(tooltips).isEmpty();
    }

    public void testWorkflowDispatchShowsRunLineMarker() throws Exception {
        configureWorkflowProjectFile("""
                name: Gutter
                on:
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
        final WorkflowRunLineMarkerContributor.RepositoryAvailability previous =
                WorkflowRunLineMarkerContributor.useRepositoryAvailabilityForTests((project, file) -> true);
        try {
            final YAMLKeyValue dispatch = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), YAMLKeyValue.class)
                    .stream()
                    .filter(keyValue -> "workflow_dispatch".equals(keyValue.getKeyText()))
                    .findFirst()
                    .orElseThrow();

            final RunLineMarkerContributor.Info info = new WorkflowRunLineMarkerContributor().getInfo(dispatch.getKey());

            assertThat(info).isNotNull();
            assertThat(info.actions).isNotEmpty();
        } finally {
            WorkflowRunLineMarkerContributor.useRepositoryAvailabilityForTests(previous);
        }
    }

    public void testWorkflowDispatchDoesNotShowRunLineMarkerWithoutGitRepository() {
        configureWorkflowProjectFile("""
                name: Gutter
                on:
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        final YAMLKeyValue dispatch = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), YAMLKeyValue.class)
                .stream()
                .filter(keyValue -> "workflow_dispatch".equals(keyValue.getKeyText()))
                .findFirst()
                .orElseThrow();

        final RunLineMarkerContributor.Info info = new WorkflowRunLineMarkerContributor().getInfo(dispatch.getKey());

        assertThat(info).isNull();
    }

    public void testWorkflowDispatchLineMarkerSwitchesToStopWhenRunIsTracked() {
        configureWorkflowProjectFile("""
                name: Gutter
                on:
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
        final String workflowPath = WorkflowRunConfigurationProducer.workflowPath(getProject(), myFixture.getFile().getVirtualFile())
                .orElseThrow();
        final DummyProcessHandler processHandler = new DummyProcessHandler();
        WorkflowRunTracker.getInstance(getProject()).register(workflowPath, processHandler);
        try {
            final YAMLKeyValue dispatch = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), YAMLKeyValue.class)
                    .stream()
                    .filter(keyValue -> "workflow_dispatch".equals(keyValue.getKeyText()))
                    .findFirst()
                    .orElseThrow();

            final RunLineMarkerContributor.Info info = new WorkflowRunLineMarkerContributor().getInfo(dispatch.getKey());

            assertThat(info).isNotNull();
            assertThat(info.icon).isEqualTo(AllIcons.Actions.Suspend);
            assertThat(info.actions).singleElement()
                    .satisfies(action -> assertThat(action.getTemplatePresentation().getText()).isEqualTo("Stop workflow run"));
        } finally {
            WorkflowRunTracker.getInstance(getProject()).unregister(workflowPath, processHandler);
        }
    }

    private static class DummyProcessHandler extends ProcessHandler {

        @Override
        protected void destroyProcessImpl() {
            notifyProcessTerminated(1);
        }

        @Override
        protected void detachProcessImpl() {
            notifyProcessDetached();
        }

        @Override
        public boolean detachIsDefault() {
            return false;
        }

        @Override
        public @Nullable OutputStream getProcessInput() {
            return null;
        }
    }
}
