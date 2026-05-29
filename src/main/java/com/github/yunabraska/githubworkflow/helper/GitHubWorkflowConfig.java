package com.github.yunabraska.githubworkflow.helper;


import com.github.yunabraska.githubworkflow.services.GitHubWorkflowBundle;

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
    public static final String FIELD_SHELL = "shell";
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

    /**
     * Returns shell completion values with descriptions localized at call time.
     *
     * @return immutable shell command descriptions for the current plugin language setting
     */
    public static Map<String, String> shells() {
        return initShells();
    }

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

    private static Map<String, String> initShells() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("bash", message("completion.shell.bash"));
        result.put("sh", message("completion.shell.sh"));
        result.put("pwsh", message("completion.shell.pwsh"));
        result.put("powershell", message("completion.shell.powershell"));
        result.put("cmd", message("completion.shell.cmd"));
        result.put("python", message("completion.shell.python"));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> getRunnerItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("name", message("completion.runner.name"));
        result.put("os", message("completion.runner.os"));
        result.put("arch", message("completion.runner.arch"));
        result.put("temp", message("completion.runner.temp"));
        result.put("tool_cache", message("completion.runner.toolCache"));
        result.put("debug", message("completion.runner.debug"));
        result.put("environment", message("completion.runner.environment"));
        return result;
    }

    private static Map<String, String> getJobItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("status", message("completion.job.status"));
        result.put("check_run_id", message("completion.job.checkRunId"));
        result.put("container", message("completion.job.container"));
        result.put("services", message("completion.job.services"));
        result.put("workflow_ref", message("completion.job.workflowRef"));
        result.put("workflow_sha", message("completion.job.workflowSha"));
        result.put("workflow_repository", message("completion.job.workflowRepository"));
        result.put("workflow_file_path", message("completion.job.workflowFilePath"));
        return result;
    }

    private static Map<String, String> getStrategyItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("fail-fast", message("completion.strategy.failFast"));
        result.put("job-index", message("completion.strategy.jobIndex"));
        result.put("job-total", message("completion.strategy.jobTotal"));
        result.put("max-parallel", message("completion.strategy.maxParallel"));
        return result;
    }

    private static Map<String, String> getCaretBracketItems() {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put(FIELD_INPUTS, message("completion.context.inputs"));
        result.put(FIELD_SECRETS, message("completion.context.secrets"));
        result.put(FIELD_JOB, message("completion.context.job"));
        result.put(FIELD_JOBS, message("completion.context.jobs"));
        result.put(FIELD_MATRIX, message("completion.context.matrix"));
        result.put(FIELD_STRATEGY, message("completion.context.strategy"));
        result.put(FIELD_STEPS, message("completion.context.steps"));
        result.put(FIELD_ENVS, message("completion.context.env"));
        result.put(FIELD_VARS, message("completion.context.vars"));
        result.put(FIELD_NEEDS, message("completion.context.needs"));
        result.put(FIELD_GITHUB, message("completion.context.github"));
        result.put(FIELD_GITEA, message("completion.context.gitea"));
        result.put(FIELD_RUNNER, message("completion.context.runner"));
        return result;
    }

    private static String message(final String key) {
        return GitHubWorkflowBundle.message(key);
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
