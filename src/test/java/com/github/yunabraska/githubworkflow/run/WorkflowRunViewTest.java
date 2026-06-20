package com.github.yunabraska.githubworkflow.run;

import junit.framework.TestCase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunViewTest extends TestCase {

    public void testMatrixStyleJobNameIsGroupedLikeJUnitClassAndMethod() {
        final WorkflowRunView.JobDisplayName name = WorkflowRunView.splitJobName("Node Test / test (ubuntu-latest)");

        assertThat(name.group()).isEqualTo("Node Test");
        assertThat(name.name()).isEqualTo("test (ubuntu-latest)");
    }

    public void testPlainJobNameStaysUnderWorkflowRoot() {
        final WorkflowRunView.JobDisplayName name = WorkflowRunView.splitJobName("build");

        assertThat(name.group()).isEmpty();
        assertThat(name.name()).isEqualTo("build");
    }

    public void testRenderStripsGithubTimestampAndFormatsGroupsAndCommands() {
        final List<WorkflowRunView.LogRenderer.Segment> segments = WorkflowRunView.LogRenderer.renderOnce("""
                2026-05-22T13:38:12.0538840Z ##[group]Run actions/checkout@main
                2026-05-22T13:38:12.0539220Z ##[command]/usr/bin/git version
                2026-05-22T13:38:12.0539420Z ##[/group]
                """);

        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::text)
                .containsExactly(
                        "== Run actions/checkout@main ==\n",
                        "0001 | run: /usr/bin/git version\n"
                );
        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::kind)
                .containsExactly(
                        WorkflowRunView.LogRenderer.Kind.SYSTEM,
                        WorkflowRunView.LogRenderer.Kind.SYSTEM
                );
    }

    public void testRenderClassifiesGithubWarningsAndErrors() {
        final List<WorkflowRunView.LogRenderer.Segment> segments = WorkflowRunView.LogRenderer.renderOnce("""
                ##[warning]old input
                ##[error file=build.gradle,line=7]broken build
                ::warning::soft problem
                ::error::hard problem
                """);

        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::text)
                .containsExactly(
                        "0001 | warning: old input\n",
                        "0002 | error: broken build\n",
                        "0003 | warning: soft problem\n",
                        "0004 | error: hard problem\n"
                );
        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::kind)
                .containsExactly(
                        WorkflowRunView.LogRenderer.Kind.WARNING,
                        WorkflowRunView.LogRenderer.Kind.ERROR,
                        WorkflowRunView.LogRenderer.Kind.WARNING,
                        WorkflowRunView.LogRenderer.Kind.ERROR
                );
    }

    public void testRenderInfersCommonWarningAndErrorPrefixes() {
        final List<WorkflowRunView.LogRenderer.Segment> segments = WorkflowRunView.LogRenderer.renderOnce("""
                npm warn deprecated old-package
                warning: check this
                fatal: repository not found
                normal output
                """);

        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::kind)
                .containsExactly(
                        WorkflowRunView.LogRenderer.Kind.WARNING,
                        WorkflowRunView.LogRenderer.Kind.WARNING,
                        WorkflowRunView.LogRenderer.Kind.ERROR,
                        WorkflowRunView.LogRenderer.Kind.NORMAL
                );
    }

    public void testRenderPlainKeepsReadableTextOnly() {
        assertThat(WorkflowRunView.LogRenderer.renderPlainOnce("""
                2026-05-22T13:38:12.0538840Z ##[group]Install
                ##[command]npm ci
                ##[warning]deprecated
                """))
                .isEqualTo("""
                        == Install ==
                        0001 | run: npm ci
                        0002 | warning: deprecated
                        """);
    }

    public void testRenderKeepsGroupLineNumbersAcrossChunksAndResetsPerGroup() {
        final WorkflowRunView.LogRenderer renderer = new WorkflowRunView.LogRenderer();

        assertThat(renderer.renderPlain("""
                ##[group]Install
                first
                """))
                .isEqualTo("""
                        == Install ==
                        0001 | first
                        """);
        assertThat(renderer.renderPlain("""
                second
                ##[endgroup]
                ##[group]Test
                again
                """))
                .isEqualTo("""
                        0002 | second

                        == Test ==
                        0001 | again
                        """);
    }

    public void testRenderStripsAnsiAndMapsCommonColors() {
        final List<WorkflowRunView.LogRenderer.Segment> segments = WorkflowRunView.LogRenderer.renderOnce("""
                \u001B[36;1mnpm ci && npm run test\u001B[0m
                \u001B[33mcareful\u001B[0m
                \u001B[31mboom\u001B[0m
                """);

        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::text)
                .containsExactly(
                        "0001 | npm ci && npm run test\n",
                        "0002 | careful\n",
                        "0003 | boom\n"
                );
        assertThat(segments)
                .extracting(WorkflowRunView.LogRenderer.Segment::kind)
                .containsExactly(
                        WorkflowRunView.LogRenderer.Kind.SYSTEM,
                        WorkflowRunView.LogRenderer.Kind.WARNING,
                        WorkflowRunView.LogRenderer.Kind.ERROR
                );
    }
}
