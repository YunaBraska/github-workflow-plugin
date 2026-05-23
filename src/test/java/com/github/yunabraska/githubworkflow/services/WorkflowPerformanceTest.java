package com.github.yunabraska.githubworkflow.services;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowPerformanceTest extends EditorFeatureTestCase {

    public void testLargeWorkflowHighlightingCompletesWithinBoundedTime() {
        configureWorkflowProjectFile(largeWorkflow());

        final long started = System.nanoTime();
        myFixture.doHighlighting();
        final long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertThat(elapsedMillis).isLessThan(10_000L);
    }

    private static String largeWorkflow() {
        final StringBuilder workflow = new StringBuilder("""
                name: Large Workflow
                on:
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                env:
                  TOP_LEVEL: production
                jobs:
                """);
        for (int job = 0; job < 40; job++) {
            workflow.append("  build_").append(job).append(":\n");
            if (job > 0) {
                workflow.append("    needs: build_").append(job - 1).append("\n");
            }
            workflow.append("""
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ inputs.deploy-target }} ${{ env.TOP_LEVEL }} ${{ matrix.os }} ${{ steps.package.outputs.artifact }}"
                    """);
        }
        return workflow.toString();
    }
}
