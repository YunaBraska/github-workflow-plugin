package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the checked-out Git branch for workflow_dispatch run configurations.
 */
final class WorkflowCurrentBranchResolver {

    Optional<String> resolve(final Project project) {
        return Optional.ofNullable(project)
                .map(ProjectUtil::guessProjectDir)
                .map(VirtualFile::getPath)
                .map(Path::of)
                .flatMap(this::resolve);
    }

    Optional<String> resolve(final Project project, final VirtualFile file) {
        return repositoryRoot(file)
                .flatMap(this::resolve)
                .or(() -> resolve(project));
    }

    Optional<String> resolve(final Path projectDir) {
        return gitDir(projectDir)
                .map(dir -> dir.resolve("HEAD"))
                .flatMap(WorkflowCurrentBranchResolver::readString)
                .flatMap(WorkflowCurrentBranchResolver::branchName);
    }

    static Optional<String> branchName(final String head) {
        final String prefix = "ref: refs/heads/";
        return Optional.ofNullable(head)
                .map(String::trim)
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length()))
                .filter(value -> !value.isBlank());
    }

    private static Optional<Path> gitDir(final Path projectDir) {
        final Path dotGit = projectDir.resolve(".git");
        if (Files.isDirectory(dotGit)) {
            return Optional.of(dotGit);
        }
        if (!Files.isRegularFile(dotGit)) {
            return Optional.empty();
        }
        return readString(dotGit)
                .map(String::trim)
                .filter(value -> value.startsWith("gitdir:"))
                .map(value -> value.substring("gitdir:".length()).trim())
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .map(path -> path.isAbsolute() ? path : projectDir.resolve(path).normalize());
    }

    private static Optional<String> readString(final Path path) {
        try {
            return Optional.of(Files.readString(path));
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
            if (Files.isDirectory(current.resolve(".git")) || Files.isRegularFile(current.resolve(".git"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }
}
