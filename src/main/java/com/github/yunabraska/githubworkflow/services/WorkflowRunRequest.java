package com.github.yunabraska.githubworkflow.services;

import java.util.Map;

/**
 * Request data needed to dispatch and observe one GitHub Actions workflow run.
 *
 * @param apiUrl GitHub REST API base URL
 * @param owner repository owner
 * @param repo repository name
 * @param workflowPath workflow file path or file name
 * @param ref branch or tag used for workflow dispatch
 * @param inputs workflow_dispatch input values
 * @param tokenEnvVar optional environment variable used only after IDE GitHub accounts fail or are unavailable
 */
public record WorkflowRunRequest(
        String apiUrl,
        String owner,
        String repo,
        String workflowPath,
        String ref,
        Map<String, String> inputs,
        String tokenEnvVar
) {

    public WorkflowRunRequest {
        inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
    }

    public String repositorySlug() {
        return owner + "/" + repo;
    }

}
