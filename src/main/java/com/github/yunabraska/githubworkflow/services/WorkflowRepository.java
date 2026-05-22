package com.github.yunabraska.githubworkflow.services;

/**
 * Resolved GitHub repository endpoint for workflow execution.
 *
 * @param webUrl browser base URL
 * @param apiUrl REST API base URL
 * @param owner repository owner
 * @param repo repository name
 */
public record WorkflowRepository(String webUrl, String apiUrl, String owner, String repo) {
}
