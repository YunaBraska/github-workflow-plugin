package com.github.yunabraska.githubworkflow.services;

/**
 * Receives workflow job status and logs for a Run tool-window workflow view.
 */
interface WorkflowRunJobConsole {

    boolean jobStatus(WorkflowRunClient.JobStatus job, String text);

    boolean jobStdout(WorkflowRunClient.JobStatus job, String text);

    boolean jobStderr(WorkflowRunClient.JobStatus job, String text);

    default boolean jobLog(final WorkflowRunClient.JobStatus job, final String text) {
        return jobStdout(job, text);
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
