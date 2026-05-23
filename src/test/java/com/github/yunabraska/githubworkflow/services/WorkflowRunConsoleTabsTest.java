package com.github.yunabraska.githubworkflow.services;

import junit.framework.TestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunConsoleTabsTest extends TestCase {

    public void testMatrixStyleJobNameIsGroupedLikeJUnitClassAndMethod() {
        final WorkflowRunConsoleTabs.JobDisplayName name = WorkflowRunConsoleTabs.splitJobName("Node Test / test (ubuntu-latest)");

        assertThat(name.group()).isEqualTo("Node Test");
        assertThat(name.name()).isEqualTo("test (ubuntu-latest)");
    }

    public void testPlainJobNameStaysUnderWorkflowRoot() {
        final WorkflowRunConsoleTabs.JobDisplayName name = WorkflowRunConsoleTabs.splitJobName("build");

        assertThat(name.group()).isEmpty();
        assertThat(name.name()).isEqualTo("build");
    }
}
