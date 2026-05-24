package com.github.yunabraska.githubworkflow.services;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub Actions workflow syntax completion tables from the public workflow syntax reference.
 */
final class WorkflowSyntaxSchema {

    private WorkflowSyntaxSchema() {
    }

    static Map<String, String> topLevelKeys() {
        return mapWithBundle(
                "completion.workflow.top.",
                "name",
                "run-name",
                "on",
                "permissions",
                "env",
                "defaults",
                "concurrency",
                "jobs"
        );
    }

    static Map<String, String> eventKeys() {
        return mapWithBundle(
                "completion.workflow.event.",
                "branch_protection_rule",
                "check_run",
                "check_suite",
                "create",
                "delete",
                "deployment",
                "deployment_status",
                "discussion",
                "discussion_comment",
                "fork",
                "gollum",
                "image_version",
                "issue_comment",
                "issues",
                "label",
                "merge_group",
                "milestone",
                "page_build",
                "project",
                "project_card",
                "project_column",
                "public",
                "pull_request",
                "pull_request_review",
                "pull_request_review_comment",
                "pull_request_target",
                "push",
                "registry_package",
                "release",
                "repository_dispatch",
                "schedule",
                "status",
                "watch",
                "workflow_call",
                "workflow_dispatch",
                "workflow_run"
        );
    }

    static Map<String, String> eventFilterKeys() {
        return mapWithBundle(
                "completion.workflow.eventFilter.",
                "types",
                "branches",
                "branches-ignore",
                "tags",
                "tags-ignore",
                "paths",
                "paths-ignore",
                "workflows",
                "cron"
        );
    }

    static Map<String, String> eventFilterKeysFor(final String event) {
        return switch (event) {
            case "schedule" -> mapWithBundle("completion.workflow.eventFilter.", "cron");
            case "workflow_run" -> mapWithBundle("completion.workflow.eventFilter.", "workflows", "types", "branches", "branches-ignore");
            case "push" -> mapWithBundle("completion.workflow.eventFilter.", "branches", "branches-ignore", "tags", "tags-ignore", "paths", "paths-ignore");
            case "pull_request", "pull_request_target" -> mapWithBundle("completion.workflow.eventFilter.", "types", "branches", "branches-ignore", "paths", "paths-ignore");
            default -> eventFilterKeys();
        };
    }

    static Map<String, String> eventActivityTypesFor(final String event) {
        return switch (event) {
            case "branch_protection_rule" -> activityTypes("created", "deleted");
            case "check_run" -> activityTypes("created", "rerequested", "completed", "requested_action");
            case "check_suite" -> activityTypes("completed");
            case "discussion" -> activityTypes(
                    "created", "edited", "deleted", "transferred", "pinned", "unpinned", "labeled", "unlabeled",
                    "locked", "unlocked", "category_changed", "answered", "unanswered"
            );
            case "discussion_comment", "issue_comment", "pull_request_review_comment" -> activityTypes("created", "edited", "deleted");
            case "issues" -> activityTypes(
                    "opened", "edited", "deleted", "transferred", "pinned", "unpinned", "closed", "reopened",
                    "assigned", "unassigned", "labeled", "unlabeled", "locked", "unlocked", "milestoned", "demilestoned"
            );
            case "label" -> activityTypes("created", "edited", "deleted");
            case "merge_group" -> activityTypes("checks_requested");
            case "milestone" -> activityTypes("created", "closed", "opened", "edited", "deleted");
            case "pull_request", "pull_request_target" -> activityTypes(
                    "assigned", "unassigned", "labeled", "unlabeled", "opened", "edited", "closed", "reopened",
                    "synchronize", "converted_to_draft", "locked", "unlocked", "enqueued", "dequeued",
                    "milestoned", "demilestoned", "ready_for_review", "review_requested", "review_request_removed",
                    "auto_merge_enabled", "auto_merge_disabled"
            );
            case "pull_request_review" -> activityTypes("submitted", "edited", "dismissed");
            case "registry_package" -> activityTypes("published", "updated");
            case "release" -> activityTypes("published", "unpublished", "created", "edited", "deleted", "prereleased", "released");
            case "watch" -> activityTypes("started");
            case "workflow_run" -> activityTypes("completed", "requested", "in_progress");
            default -> java.util.Collections.emptyMap();
        };
    }

    static Map<String, String> permissionScopes() {
        return mapWithBundle(
                "completion.workflow.permission.",
                "actions",
                "artifact-metadata",
                "attestations",
                "checks",
                "code-quality",
                "contents",
                "deployments",
                "discussions",
                "id-token",
                "issues",
                "models",
                "packages",
                "pages",
                "pull-requests",
                "security-events",
                "statuses",
                "vulnerability-alerts"
        );
    }

    static Map<String, String> permissionValues() {
        return mapWithBundle("completion.workflow.permission.value.", "read", "write", "none");
    }

    static Map<String, String> permissionValuesFor(final String permission) {
        if ("id-token".equals(permission)) {
            return mapWithBundle("completion.workflow.permission.value.", "write", "none");
        }
        if ("models".equals(permission) || "vulnerability-alerts".equals(permission)) {
            return mapWithBundle("completion.workflow.permission.value.", "read", "none");
        }
        return permissionValues();
    }

    static Map<String, String> permissionShorthandValues() {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("read-all", "read-all");
        values.put("write-all", "write-all");
        values.put("{}", "empty");
        return mapWithBundleKeys("completion.workflow.permission.shorthand.", values);
    }

    static Map<String, String> jobKeys() {
        return mapWithBundle(
                "completion.workflow.job.",
                "name",
                "permissions",
                "needs",
                "if",
                "runs-on",
                "snapshot",
                "environment",
                "concurrency",
                "outputs",
                "env",
                "defaults",
                "steps",
                "timeout-minutes",
                "strategy",
                "continue-on-error",
                "container",
                "services",
                "uses",
                "with",
                "secrets"
        );
    }

    static Map<String, String> defaultsRunKeys() {
        return mapWithBundle("completion.workflow.defaultsRun.", "shell", "working-directory");
    }

    static Map<String, String> concurrencyKeys() {
        return mapWithBundle("completion.workflow.concurrency.", "group", "cancel-in-progress");
    }

    static Map<String, String> environmentKeys() {
        return mapWithBundle("completion.workflow.environment.", "name", "url");
    }

    static Map<String, String> strategyKeys() {
        return mapWithBundle("completion.workflow.strategy.", "matrix", "fail-fast", "max-parallel");
    }

    static Map<String, String> matrixKeys() {
        return mapWithBundle("completion.workflow.matrix.", "include", "exclude");
    }

    static Map<String, String> stepKeys() {
        return mapWithBundle(
                "completion.workflow.step.",
                "id",
                "if",
                "name",
                "uses",
                "run",
                "shell",
                "with",
                "env",
                "continue-on-error",
                "timeout-minutes",
                "working-directory"
        );
    }

    static Map<String, String> containerKeys() {
        return mapWithBundle("completion.workflow.container.", "image", "credentials", "env", "ports", "volumes", "options");
    }

    static Map<String, String> serviceKeys() {
        return mapWithBundle("completion.workflow.service.", "image", "credentials", "env", "ports", "volumes", "options");
    }

    static Map<String, String> credentialsKeys() {
        return mapWithBundle("completion.workflow.credentials.", "username", "password");
    }

    static Map<String, String> workflowInputTypes() {
        return mapWithBundle("completion.workflow.inputType.", "string", "boolean", "choice", "number", "environment");
    }

    static Map<String, String> reusableWorkflowInputTypes() {
        return mapWithBundle("completion.workflow.inputType.", "string", "boolean", "number");
    }

    static Map<String, String> workflowInputPropertyKeys() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowBundle.message("documentation.description.label"));
        result.put("type", GitHubWorkflowBundle.message("documentation.type", "string | boolean | choice | number | environment"));
        result.put("required", GitHubWorkflowBundle.message("documentation.required", true));
        result.put("default", GitHubWorkflowBundle.message("documentation.default", ""));
        result.put("options", GitHubWorkflowBundle.message("documentation.value.label"));
        return java.util.Collections.unmodifiableMap(result);
    }

    static Map<String, String> workflowOutputPropertyKeys() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowBundle.message("documentation.description.label"));
        result.put("value", GitHubWorkflowBundle.message("documentation.value.label"));
        return java.util.Collections.unmodifiableMap(result);
    }

    static Map<String, String> workflowSecretPropertyKeys() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowBundle.message("documentation.description.label"));
        result.put("required", GitHubWorkflowBundle.message("documentation.required", true));
        return java.util.Collections.unmodifiableMap(result);
    }

    static Map<String, String> booleanValues() {
        return mapWithBundle("completion.workflow.boolean.", "true", "false");
    }

    static Map<String, String> runnerLabels() {
        return mapWithBundle(
                "completion.workflow.runner.",
                "ubuntu-latest",
                "ubuntu-24.04",
                "ubuntu-22.04",
                "windows-latest",
                "windows-2025",
                "windows-2022",
                "macos-latest",
                "macos-15",
                "macos-14",
                "self-hosted"
        );
    }

    private static Map<String, String> mapWithBundle(final String prefix, final String... keys) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final String key : keys) {
            result.put(key, GitHubWorkflowBundle.message(prefix + key));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, String> mapWithBundleKeys(final String prefix, final Map<String, String> keysToBundleSuffix) {
        final Map<String, String> result = new LinkedHashMap<>();
        keysToBundleSuffix.forEach((key, bundleSuffix) -> result.put(key, GitHubWorkflowBundle.message(prefix + bundleSuffix)));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, String> activityTypes(final String... keys) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final String key : keys) {
            result.put(key, GitHubWorkflowBundle.message("completion.workflow.eventFilter.types"));
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
