package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.client.WorkflowRunClient;

/**
 * Receives workflow job status and logs for a Run tool-window workflow view.
 */
public interface WorkflowRunJobConsole {

    boolean jobStatus(WorkflowRunClient.JobStatus job, String text);

    boolean jobStdout(WorkflowRunClient.JobStatus job, String text);

    boolean jobStderr(WorkflowRunClient.JobStatus job, String text);

    default boolean jobLog(final WorkflowRunClient.JobStatus job, final String text) {
        return jobStdout(job, text);
    }

    default void workflowStatus(final String text, final boolean error) {
    }

    default void runFinished(final long runId, final String conclusion) {
    }

    default void runDeleted(final long runId) {
    }

    default void runDeleteFailed(final long runId) {
    }

    default void close() {
    }

    static WorkflowRunJobConsole none() {
        return new WorkflowRunJobConsole() {
            @Override
            public boolean jobStatus(final WorkflowRunClient.JobStatus job, final String text) {
                return false;
            }

            @Override
            public boolean jobStdout(final WorkflowRunClient.JobStatus job, final String text) {
                return false;
            }

            @Override
            public boolean jobStderr(final WorkflowRunClient.JobStatus job, final String text) {
                return false;
            }
        };
    }
}
