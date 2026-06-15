package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.run.WorkflowRun;
import com.google.gson.JsonObject;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class GiteaDockerIntegrationTest extends BasePlatformTestCase {

    private static final String TEST_SWITCH = "GITEA_DOCKER_TEST";
    private static final String IMAGE = Optional.ofNullable(System.getenv("GITEA_IMAGE"))
            .filter(value -> !value.isBlank())
            .orElse("docker.gitea.com/gitea:1.26.2-rootless");
    private static final String RUNNER_IMAGE = Optional.ofNullable(System.getenv("GITEA_RUNNER_IMAGE"))
            .filter(value -> !value.isBlank())
            .orElse("docker.io/gitea/act_runner:0.6.1");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    protected void tearDown() throws Exception {
        try {
            RemoteActionProviders.Settings.getInstance().setCustomServers(List.of());
        } finally {
            super.tearDown();
        }
    }

    public void testGiteaApiV1ResolvesActionsAndWorkflowsFromEmbeddedContainer() throws Exception {
        if ("false".equalsIgnoreCase(System.getenv(TEST_SWITCH))) {
            return;
        }
        try (GiteaContainer gitea = GiteaContainer.start()) {
            final String token = gitea.createAdminToken();
            gitea.createRepository(token, "action-box");
            gitea.createFile(token, "action-box", "action.yml", """
                    name: Action Box
                    inputs:
                      flavor:
                        description: Test flavor
                    outputs:
                      artifact:
                        description: Test artifact
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            gitea.createFile(token, "action-box", ".gitea/workflows/reuse.yml", """
                    name: Reuse
                    on:
                      workflow_call:
                        inputs:
                          config:
                            type: string
                    jobs:
                      test:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo ok
                    """);
            RemoteActionProviders.Settings.getInstance().setCustomServers(List.of(RemoteActionProviders.Server.gitea(
                    "Embedded Gitea",
                    gitea.webUrl(),
                    gitea.apiUrl(),
                    "",
                    true
            )));

            final GitHubAction action = GitHubAction.createGithubAction(false, gitea.webUrl() + "/test/action-box@main", "gitea-action").resolve();
            final String workflowUses = gitea.webUrl() + "/test/action-box/.gitea/workflows/reuse.yml@main";
            final GitHubAction workflow = GitHubAction.createGithubAction(false, workflowUses, "gitea-workflow").resolve();

            assertThat(action.isResolved()).isTrue();
            assertThat(action.isAction()).isTrue();
            assertThat(action.freshInputs()).containsKey("flavor");
            assertThat(action.freshOutputs()).containsKey("artifact");
            assertThat(action.remoteRefs()).contains("main");
            assertThat(workflow.isResolved()).isTrue();
            assertThat(workflow.isAction()).isFalse();
            assertThat(workflow.freshInputs()).containsKey("config");
            assertThat(workflow.githubUrl()).isEqualTo(gitea.webUrl() + "/test/action-box/blob/main/.gitea/workflows/reuse.yml");
        }
    }

    public void testGiteaWorkflowDispatchRunsJobAndDownloadsLogsWithActRunner() throws Exception {
        if ("false".equalsIgnoreCase(System.getenv(TEST_SWITCH))) {
            return;
        }
        try (GiteaContainer gitea = GiteaContainer.start()) {
            final String token = gitea.createAdminToken();
            gitea.createRepository(token, "runner-smoke");
            gitea.createFile(token, "runner-smoke", ".gitea/workflows/smoke.yml", """
                    name: Runner Smoke
                    on:
                      workflow_dispatch:
                    jobs:
                      smoke:
                        runs-on: ubuntu-latest
                        steps:
                          - name: Marker
                            shell: sh
                            run: echo plugin-gitea-runner-smoke
                    """);
            try (RunnerContainer ignored = gitea.startRunner()) {
                final RemoteActionProviders.Server server = RemoteActionProviders.Server.gitea(
                        "Embedded Gitea",
                        gitea.webUrl(),
                        gitea.apiUrl(),
                        "",
                        true
                );
                RemoteActionProviders.Settings.getInstance()
                        .setCustomServers(List.of(server))
                        .setGiteaToken(server, token);
                final WorkflowRun workflowRun = new WorkflowRun();
                final WorkflowRun.Request request = new WorkflowRun.Request(
                        gitea.apiUrl(),
                        "test",
                        "runner-smoke",
                        ".gitea/workflows/smoke.yml",
                        "main",
                        Map.of(),
                        "GITEA_TOKEN"
                );

                final WorkflowRun.DispatchResult dispatch = workflowRun.dispatch(request);
                final long runId = dispatch.runId() >= 0
                        ? dispatch.runId()
                        : workflowRun.latestRun(request).orElseThrow().runId();
                final WorkflowRun.RunStatus completed = waitForCompletedRun(workflowRun, request, runId);
                final List<WorkflowRun.JobStatus> jobs = workflowRun.jobs(request, runId);
                final String logs = workflowRun.jobLogs(request, jobs.getFirst().id());

                assertThat(completed.conclusion()).isEqualTo("success");
                assertThat(jobs).hasSize(1);
                assertThat(jobs.getFirst().name()).isEqualTo("smoke");
                assertThat(logs).contains("plugin-gitea-runner-smoke");
            }
        }
    }

    private static WorkflowRun.RunStatus waitForCompletedRun(
            final WorkflowRun workflowRun,
            final WorkflowRun.Request request,
            final long runId
    ) throws Exception {
        for (int attempt = 0; attempt < 90; attempt++) {
            final WorkflowRun.RunStatus status = workflowRun.status(request, runId);
            if (status.completed()) {
                return status;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("Gitea workflow run did not complete");
    }

    private record RunnerContainer(String id) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            run("docker", "rm", "-f", id);
        }
    }

    private record GiteaContainer(String id, String webUrl) implements AutoCloseable {

        static GiteaContainer start() throws Exception {
            final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            final String network = "gwplugin-" + suffix;
            final String name = "gwplugin-gitea-" + suffix;
            run("docker", "network", "create", network);
            final String id = run("docker", "run", "--rm", "-d",
                    "--name", name,
                    "--network", network,
                    "-p", "127.0.0.1::3000",
                    "-e", "GITEA__database__DB_TYPE=sqlite3",
                    "-e", "GITEA__database__PATH=/var/lib/gitea/gitea.db",
                    "-e", "GITEA__security__INSTALL_LOCK=true",
                    "-e", "GITEA__service__DISABLE_REGISTRATION=true",
                    "-e", "GITEA__server__HTTP_PORT=3000",
                    "-e", "GITEA__server__ROOT_URL=http://" + name + ":3000/",
                    "-e", "GITEA__actions__ENABLED=true",
                    IMAGE
            ).trim();
            final String port = run("docker", "port", id, "3000/tcp").trim().replaceFirst(".*:", "");
            final GiteaContainer container = new GiteaContainer(id, "http://127.0.0.1:" + port);
            container.waitUntilReady();
            return container;
        }

        String apiUrl() {
            return webUrl + "/api/v1";
        }

        RunnerContainer startRunner() throws Exception {
            final String runnerName = "gwplugin-runner-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            final String runnerToken = run("docker", "exec", id, "gitea", "actions", "generate-runner-token")
                    .lines()
                    .reduce((first, second) -> second)
                    .orElse("")
                    .trim();
            if (runnerToken.isBlank()) {
                throw new IllegalStateException("Gitea runner token was not printed");
            }
            final String network = run("docker", "inspect", "-f", "{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}", id).trim();
            run("docker", "run", "-d",
                    "--name", runnerName,
                    "--network", network,
                    "-e", "GITEA_INSTANCE_URL=http://" + containerName() + ":3000",
                    "-e", "GITEA_RUNNER_REGISTRATION_TOKEN=" + runnerToken,
                    "-e", "GITEA_RUNNER_NAME=plugin-smoke",
                    "-e", "GITEA_RUNNER_EPHEMERAL=1",
                    "-e", "GITEA_RUNNER_LABELS=ubuntu-latest:host",
                    RUNNER_IMAGE
            );
            waitUntilRunnerReady(runnerName);
            return new RunnerContainer(runnerName);
        }

        private String containerName() throws Exception {
            return run("docker", "inspect", "-f", "{{.Name}}", id).trim().replaceFirst("^/", "");
        }

        String createAdminToken() throws Exception {
            run("docker", "exec", id, "gitea", "admin", "user", "create",
                    "--username", "test",
                    "--password", "test-password",
                    "--email", "test@example.com",
                    "--admin",
                    "--must-change-password=false"
            );
            final String output = run("docker", "exec", id, "gitea", "admin", "user", "generate-access-token",
                    "--username", "test",
                    "--token-name", "plugin-test",
                    "--scopes", "all"
            );
            return Pattern.compile("created:\\s*(\\S+)")
                    .matcher(output)
                    .results()
                    .map(result -> result.group(1))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Gitea token was not printed"));
        }

        void createRepository(final String token, final String name) throws Exception {
            final JsonObject body = new JsonObject();
            body.addProperty("name", name);
            body.addProperty("auto_init", true);
            post(token, "/user/repos", body);
        }

        void createFile(final String token, final String repo, final String path, final String content) throws Exception {
            final JsonObject body = new JsonObject();
            body.addProperty("branch", "main");
            body.addProperty("message", "add " + path);
            body.addProperty("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
            post(token, "/repos/test/" + encode(repo) + "/contents/" + path, body);
        }

        private void post(final String token, final String path, final JsonObject body) throws Exception {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gitea API failed with HTTP " + response.statusCode() + ": " + response.body());
            }
        }

        private void waitUntilReady() throws Exception {
            for (int attempt = 0; attempt < 30; attempt++) {
                try {
                    final HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl() + "/version"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();
                    if (CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                        return;
                    }
                } catch (final IOException ignored) {
                    // Gitea is still waking up.
                }
                Thread.sleep(1_000);
            }
            throw new IllegalStateException("Gitea did not become ready");
        }

        private static void waitUntilRunnerReady(final String runnerName) throws Exception {
            final Pattern ready = Pattern.compile("(?i)(runner registered successfully|declare successfully)");
            for (int attempt = 0; attempt < 45; attempt++) {
                final String logs = runCombined("docker", "logs", runnerName);
                if (ready.matcher(logs).find()) {
                    return;
                }
                Thread.sleep(1_000);
            }
            throw new IllegalStateException("Gitea runner did not become ready: " + runCombined("docker", "logs", runnerName));
        }

        @Override
        public void close() throws Exception {
            final String network = run("docker", "inspect", "-f", "{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}", id).trim();
            try {
                run("docker", "stop", id);
            } finally {
                if (!network.isBlank()) {
                    run("docker", "network", "rm", network);
                }
            }
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String run(final String... command) throws IOException, InterruptedException {
        final Path errorLog = Files.createTempFile("gitea-docker-", ".err");
        try {
            final Process process = new ProcessBuilder(command)
                    .redirectError(errorLog.toFile())
                    .start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out [" + String.join(" ", command) + "]: " + commandOutput(output, errorLog));
            }
            if (process.exitValue() != 0) {
                throw new IOException("Command failed [" + String.join(" ", command) + "]: " + commandOutput(output, errorLog));
            }
            return output;
        } finally {
            Files.deleteIfExists(errorLog);
        }
    }

    private static String runCombined(final String... command) throws IOException, InterruptedException {
        final Path errorLog = Files.createTempFile("gitea-docker-", ".err");
        try {
            final Process process = new ProcessBuilder(command)
                    .redirectError(errorLog.toFile())
                    .start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out [" + String.join(" ", command) + "]: " + commandOutput(output, errorLog));
            }
            if (process.exitValue() != 0) {
                throw new IOException("Command failed [" + String.join(" ", command) + "]: " + commandOutput(output, errorLog));
            }
            return commandOutput(output, errorLog);
        } finally {
            Files.deleteIfExists(errorLog);
        }
    }

    private static String commandOutput(final String output, final Path errorLog) throws IOException {
        final String error = Files.readString(errorLog, StandardCharsets.UTF_8);
        return (output + System.lineSeparator() + error).trim();
    }
}
