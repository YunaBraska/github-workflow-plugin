package com.github.yunabraska.githubworkflow.helper;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SuppressWarnings("java:S2386")
public class GitHubWorkflowConfig {

    public static final Pattern PATTERN_GITHUB_OUTPUT = Pattern.compile("(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_OUTPUT\\}?\"?");
    public static final Pattern PATTERN_GITHUB_OUTPUT_TEE = Pattern.compile("(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*\\|\\s*tee\\s+(?:-[A-Za-z]+\\s+)*.*\\$\\w*:?\\{?GITHUB_OUTPUT\\}?");
    public static final Pattern PATTERN_GITHUB_ENV = Pattern.compile("(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_ENV\\}?\"?");
    public static final Pattern PATTERN_GITHUB_OUTPUT_MULTILINE = Pattern.compile("(?:echo\\s+)?[\"']?([A-Za-z_][A-Za-z0-9_-]*)<<[^\"'\\r\\n]+[\"']?");
    public static final Pattern PATTERN_GITHUB_ENV_MULTILINE = Pattern.compile("(?:echo\\s+)?[\"']?([A-Za-z_][A-Za-z0-9_-]*)<<[^\"'\\r\\n]+[\"']?");
    public static final long CACHE_ONE_DAY = 24L * 60 * 60 * 1000;
    public static final String FIELD_ON = "on";
    public static final String FIELD_IF = "if";
    public static final String FIELD_ID = "id";
    public static final String FIELD_ENVS = "env";
    public static final String FIELD_RUN = "run";
    public static final String FIELD_RUNS = "runs";
    public static final String FIELD_JOB = "job";
    public static final String FIELD_JOBS = "jobs";
    public static final String FIELD_MATRIX = "matrix";
    public static final String FIELD_STRATEGY = "strategy";
    public static final String FIELD_VARS = "vars";
    public static final String FIELD_WITH = "with";
    public static final String FIELD_USES = "uses";
    public static final String FIELD_NEEDS = "needs";
    public static final String FIELD_STEPS = "steps";
    public static final String FIELD_SERVICES = "services";
    public static final String FIELD_RUNNER = "runner";
    public static final String FIELD_GITHUB = "github";
    public static final String FIELD_GITEA = "gitea";
    public static final String FIELD_DEFAULT = "${{}}";
    public static final String FIELD_INPUTS = "inputs";
    public static final String FIELD_OUTPUTS = "outputs";
    public static final String FIELD_SECRETS = "secrets";
    public static final String FIELD_PORTS = "ports";
    public static final String FIELD_RESULT = "result";
    public static final String FIELD_CONCLUSION = "conclusion";
    public static final String FIELD_OUTCOME = "outcome";
    public static final Map<String, Supplier<Map<String, String>>> DEFAULT_VALUE_MAP = initProcessorMap();

    private static Map<String, Supplier<Map<String, String>>> initProcessorMap() {
        final Map<String, Supplier<Map<String, String>>> result = new LinkedHashMap<>();
        result.put(FIELD_GITHUB, GitHubWorkflowConfig::getGitHubContextEnvs);
        result.put(FIELD_GITEA, GitHubWorkflowConfig::getGitHubContextEnvs);
        result.put(FIELD_JOB, GitHubWorkflowConfig::getJobItems);
        result.put(FIELD_ENVS, GitHubWorkflowConfig::getGitHubEnvs);
        result.put(FIELD_RUNNER, GitHubWorkflowConfig::getRunnerItems);
        result.put(FIELD_STRATEGY, GitHubWorkflowConfig::getStrategyItems);
        result.put(FIELD_DEFAULT, GitHubWorkflowConfig::getCaretBracketItems);
        return result;
    }

    private static Map<String, String> getRunnerItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("name", "The name of the runner executing the job.");
        result.put("os", "The operating system of the runner executing the job. Possible values are Linux, Windows, or macOS.");
        result.put("arch", "The architecture of the runner executing the job. Possible values are X86, X64, ARM, or ARM64.");
        result.put("temp", "The path to a temporary directory on the runner. This directory is emptied at the beginning and end of each job. Note that files will not be removed if the runner's user account does not have permission to delete them.");
        result.put("tool_cache", "The path to the directory containing preinstalled tools for GitHub-hosted runners.");
        result.put("debug", "This is set only if debug logging is enabled, and always has the value of 1.");
        result.put("environment", "The environment of the runner executing the job. Possible values are github-hosted or self-hosted.");
        return result;
    }

    private static Map<String, String> getJobItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "The current status of the job.");
        result.put("check_run_id", "The check run ID of the current job.");
        result.put("container", "Information about the job's container.");
        result.put("services", "The service containers created for a job.");
        result.put("workflow_ref", "The full ref of the workflow file that defines the current job.");
        result.put("workflow_sha", "The commit SHA of the workflow file that defines the current job.");
        result.put("workflow_repository", "The owner/repo of the repository containing the workflow file that defines the current job.");
        result.put("workflow_file_path", "The workflow file path, relative to the repository root.");
        return result;
    }

    private static Map<String, String> getStrategyItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("fail-fast", "Whether all in-progress jobs are canceled if any matrix job fails.");
        result.put("job-index", "The zero-based index of the current job in the matrix.");
        result.put("job-total", "The total number of jobs in the matrix.");
        result.put("max-parallel", "The maximum number of matrix jobs that can run simultaneously.");
        return result;
    }

    private static Map<String, String> getCaretBracketItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put(FIELD_INPUTS, "Workflow inputs e.g. from workflow_dispatch, workflow_call");
        result.put(FIELD_SECRETS, "Workflow secrets");
        result.put(FIELD_JOB, "Information about the currently running job");
        result.put(FIELD_JOBS, "Workflow jobs");
        result.put(FIELD_MATRIX, "Matrix properties defined for the current matrix job");
        result.put(FIELD_STRATEGY, "Matrix execution strategy information for the current job");
        result.put(FIELD_STEPS, "steps with 'id' of the current job");
        result.put(FIELD_ENVS, "Environment variables from jobs amd steps");
        result.put(FIELD_VARS, "The vars context contains custom configuration variables set at the organization, repository, and environment levels. For more information about defining configuration variables for use in multiple workflows");
        result.put(FIELD_NEEDS, "Identifies any jobs that must complete successfully before this job will run. It can be a string or array of strings. If a job fails, all jobs that need it are skipped unless the jobs use a conditional statement that causes the job to continue.");
        result.put(FIELD_GITHUB, "Information about the workflow run and the event that triggered the run. You can also read most of the github context data in environment variables. For more information about environment variables");
        result.put(FIELD_GITEA, "Information about the Gitea Actions workflow run. Gitea keeps many GitHub-compatible context names and also exposes gitea.* in Gitea workflows.");
        result.put(FIELD_RUNNER, "Information about the runner that is executing the current job");
        return result;
    }

    private static Map<String, String> getGitHubContextEnvs() {
        return loadGeneratedItems("/github-docs/github-context.tsv");
    }

    private static Map<String, String> getGitHubEnvs() {
        return loadGeneratedItems("/github-docs/default-env.tsv");
    }

    private static Map<String, String> loadGeneratedItems(final String resourcePath) {
        try (InputStream stream = GitHubWorkflowConfig.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Map.of();
            }
            return readGeneratedItems(stream);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private static Map<String, String> readGeneratedItems(final InputStream stream) throws IOException {
        final Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                final String[] parts = line.split("\t", 2);
                if (parts.length == 2 && !parts[0].isBlank()) {
                    result.put(parts[0], parts[1]);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private GitHubWorkflowConfig() {
    }
}
