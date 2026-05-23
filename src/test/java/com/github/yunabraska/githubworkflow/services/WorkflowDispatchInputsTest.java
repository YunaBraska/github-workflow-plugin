package com.github.yunabraska.githubworkflow.services;

import junit.framework.TestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowDispatchInputsTest extends TestCase {

    public void testParseWorkflowDispatchInputsWithDefaults() {
        final WorkflowDispatchInputs inputs = new WorkflowDispatchInputs();

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
                new WorkflowDispatchInputs.Input("ref", "string", true, "main", "Branch"),
                new WorkflowDispatchInputs.Input("dry_run", "boolean", false, "true", ""),
                new WorkflowDispatchInputs.Input("environment", "choice", false, "", "Target", java.util.List.of("dev", "prod"))
        );
    }

    public void testDefaultsTextBuildsPlainKeyValueLines() {
        final WorkflowDispatchInputs inputs = new WorkflowDispatchInputs();

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
        assertThat(WorkflowDispatchInputs.parseKeyValueText("""
                # ignored
                ref=main

                dry_run=true
                """)).containsEntry("ref", "main").containsEntry("dry_run", "true");
    }

}
