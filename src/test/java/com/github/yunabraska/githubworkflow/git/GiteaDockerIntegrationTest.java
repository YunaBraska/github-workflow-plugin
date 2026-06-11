package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
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
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class GiteaDockerIntegrationTest extends BasePlatformTestCase {

    private static final String TEST_SWITCH = "GITEA_DOCKER_TEST";
    private static final String IMAGE = Optional.ofNullable(System.getenv("GITEA_IMAGE"))
            .filter(value -> !value.isBlank())
            .orElse("docker.gitea.com/gitea:1.26.2-rootless");
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

    private record GiteaContainer(String id, String webUrl) implements AutoCloseable {

        static GiteaContainer start() throws Exception {
            final String id = run("docker", "run", "--rm", "-d",
                    "-p", "127.0.0.1::3000",
                    "-e", "GITEA__database__DB_TYPE=sqlite3",
                    "-e", "GITEA__database__PATH=/var/lib/gitea/gitea.db",
                    "-e", "GITEA__security__INSTALL_LOCK=true",
                    "-e", "GITEA__service__DISABLE_REGISTRATION=true",
                    "-e", "GITEA__server__HTTP_PORT=3000",
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

        @Override
        public void close() throws Exception {
            run("docker", "stop", id);
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
            if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
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

    private static String commandOutput(final String output, final Path errorLog) throws IOException {
        final String error = Files.readString(errorLog, StandardCharsets.UTF_8);
        return (output + System.lineSeparator() + error).trim();
    }
}
