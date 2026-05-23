package com.github.yunabraska.githubworkflow.services;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Stores workflow run downloads under the IDE system directory and reveals them to the user.
 */
final class WorkflowRunDownloads {

    private WorkflowRunDownloads() {
    }

    static Path writeJobLog(
            final WorkflowRunRequest request,
            final long runId,
            final long jobId,
            final String jobName,
            final String log
    ) throws IOException {
        final Path file = runDirectory(request, runId).resolve(safeName(jobName) + "-" + jobId + ".log");
        Files.writeString(file, Optional.ofNullable(log).orElse(""), StandardCharsets.UTF_8);
        return file;
    }

    static Path writeArtifact(
            final WorkflowRunRequest request,
            final long runId,
            final WorkflowRunClient.ArtifactStatus artifact,
            final byte[] zip
    ) throws IOException {
        final Path file = runDirectory(request, runId).resolve(safeName(artifact.name()) + "-" + artifact.id() + ".zip");
        Files.write(file, Optional.ofNullable(zip).orElseGet(() -> new byte[0]));
        return file;
    }

    static void reveal(final Path path) {
        if (path == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> RevealFileAction.openFile(path.toFile()));
    }

    private static Path runDirectory(final WorkflowRunRequest request, final long runId) throws IOException {
        final Path directory = Path.of(
                PathManager.getSystemPath(),
                "github-workflow-plugin",
                "downloads",
                safeName(request.repositorySlug()),
                "run-" + runId
        );
        Files.createDirectories(directory);
        return directory;
    }

    private static String safeName(final String value) {
        final String normalized = Optional.ofNullable(value)
                .filter(text -> !text.isBlank())
                .orElse("download")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "download" : normalized;
    }
}
