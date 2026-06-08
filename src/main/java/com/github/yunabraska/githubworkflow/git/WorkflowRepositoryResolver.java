package com.github.yunabraska.githubworkflow.git;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the GitHub repository for the current project from `.git/config`.
 */
public class WorkflowRepositoryResolver {

    private static final Pattern HTTPS_REMOTE = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+?)(?:[.]git)?/?$");
    private static final Pattern SSH_REMOTE = Pattern.compile("(?:git@|ssh://git@)([^:/]+)[:/]([^/]+)/([^/]+?)(?:[.]git)?/?$");

    public Optional<WorkflowRepository> resolve(final Project project) {
        return Optional.ofNullable(project)
                .map(ProjectUtil::guessProjectDir)
                .map(VirtualFile::getPath)
                .map(Path::of)
                .flatMap(this::resolve);
    }

    public Optional<WorkflowRepository> resolve(final Project project, final VirtualFile file) {
        return repositoryRoot(file)
                .flatMap(this::resolve)
                .or(() -> resolve(project));
    }

    public Optional<WorkflowRepository> resolve(final Path projectDir) {
        return readGitConfig(projectDir)
                .flatMap(WorkflowRepositoryResolver::firstOriginUrl)
                .flatMap(WorkflowRepositoryResolver::fromRemoteUrl);
    }

    public static Optional<WorkflowRepository> fromRemoteUrl(final String remoteUrl) {
        return match(HTTPS_REMOTE, remoteUrl).or(() -> match(SSH_REMOTE, remoteUrl));
    }

    private static Optional<WorkflowRepository> match(final Pattern pattern, final String remoteUrl) {
        final Matcher matcher = pattern.matcher(Optional.ofNullable(remoteUrl).orElse("").trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final String host = matcher.group(1);
        final String owner = matcher.group(2);
        final String repo = matcher.group(3);
        final String webUrl = "https://" + host;
        final String apiUrl = "github.com".equalsIgnoreCase(host)
                ? "https://api.github.com"
                : webUrl + "/api/v3";
        return Optional.of(new WorkflowRepository(webUrl, apiUrl, owner, repo));
    }

    private static Optional<String> readGitConfig(final Path projectDir) {
        final Path config = projectDir.resolve(".git").resolve("config");
        if (!Files.isRegularFile(config)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(config));
        } catch (final IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Path> repositoryRoot(final VirtualFile file) {
        Path current = Optional.ofNullable(file)
                .map(VirtualFile::getPath)
                .map(Path::of)
                .map(Path::getParent)
                .orElse(null);
        while (current != null) {
            if (Files.isRegularFile(current.resolve(".git").resolve("config"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static Optional<String> firstOriginUrl(final String config) {
        boolean inOrigin = false;
        for (final String line : config.split("\\R")) {
            final String trimmed = line.trim();
            if (trimmed.startsWith("[remote ")) {
                inOrigin = trimmed.equals("[remote \"origin\"]");
                continue;
            }
            if (inOrigin && trimmed.startsWith("url =")) {
                return Optional.of(trimmed.substring("url =".length()).trim()).filter(value -> !value.isBlank());
            }
        }
        return Optional.empty();
    }
}
