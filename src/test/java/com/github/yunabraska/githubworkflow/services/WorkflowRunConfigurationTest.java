package com.github.yunabraska.githubworkflow.services;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunConfigurationTest extends EditorFeatureTestCase {

    public void testResetUsesOnlyTheSelectedConfigurationInputs() throws Exception {
        final WorkflowRunConfiguration.Editor editor = new WorkflowRunConfiguration.Editor();
        final WorkflowRunConfiguration first = configuration("first").inputsText("old_key=old-value\n");
        final WorkflowRunConfiguration second = configuration("second").inputsText("new_key=new-value\n");

        editor.resetFrom(first);
        editor.applyTo(first);
        editor.resetFrom(second);
        editor.applyTo(second);

        assertThat(second.toRequest().inputs())
                .containsEntry("new_key", "new-value")
                .doesNotContainKey("old_key");
    }

    public void testParseDispatchInputsWithDefaults() {
        final WorkflowRun.DispatchInputs inputs = new WorkflowRun.DispatchInputs();

        assertThat(inputs.parse("""
                name: Dispatch
                on:
                  workflow_dispatch:
                    inputs:
                      ref:
                        description: Branch
                        type: string
                        required: true
                        default: main
                      dry_run:
                        type: boolean
                        default: "true"
                      environment:
                        description: Target
                        type: choice
                        options:
                          - dev
                          - prod
                jobs:
                  build:
                    runs-on: ubuntu-latest
                """)).containsExactly(
                new WorkflowRun.DispatchInputs.Input("ref", "string", true, "main", "Branch"),
                new WorkflowRun.DispatchInputs.Input("dry_run", "boolean", false, "true", ""),
                new WorkflowRun.DispatchInputs.Input("environment", "choice", false, "", "Target", List.of("dev", "prod"))
        );
    }

    public void testDefaultsTextBuildsPlainKeyValueLines() {
        final WorkflowRun.DispatchInputs inputs = new WorkflowRun.DispatchInputs();

        assertThat(inputs.defaultsText("""
                on:
                  workflow_dispatch:
                    inputs:
                      ref:
                        description: Branch
                        type: choice
                        required: true
                        default: main
                        options: [main, "release, candidate"]
                """)).isEqualTo("ref=main\n");
    }

    public void testKeyValueInputTextIgnoresCommentsAndBlankLines() {
        assertThat(WorkflowRun.DispatchInputs.parseKeyValueText("""
                # ignored
                ref=main

                dry_run=true
                """)).containsEntry("ref", "main").containsEntry("dry_run", "true");
    }

    private WorkflowRunConfiguration configuration(final String name) {
        return new WorkflowRunConfiguration(getProject(), WorkflowRunConfiguration.Type.getInstance().factory(), name);
    }
}
