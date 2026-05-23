package com.github.yunabraska.githubworkflow.services;

import junit.framework.TestCase;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowCurrentBranchResolverTest extends TestCase {

    public void testBranchNameReadsRefsHeadsBranch() {
        assertThat(WorkflowCurrentBranchResolver.branchName("ref: refs/heads/feature/logs\n"))
                .contains("feature/logs");
    }

    public void testBranchNameIgnoresDetachedHead() {
        assertThat(WorkflowCurrentBranchResolver.branchName("e1a9e573f4d0838b3a7c1b07401aeb29ed3635a9"))
                .isEmpty();
    }

    public void testResolveReadsCurrentBranchFromGitHead() throws Exception {
        final Path dir = Files.createTempDirectory("workflow-branch");
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("HEAD"), "ref: refs/heads/feature/current\n");

        assertThat(new WorkflowCurrentBranchResolver().resolve(dir))
                .contains("feature/current");
    }

    public void testResolveReadsCurrentBranchFromWorktreeGitFile() throws Exception {
        final Path dir = Files.createTempDirectory("workflow-worktree");
        final Path gitDir = Files.createDirectories(dir.resolve("real-git-dir"));
        Files.writeString(dir.resolve(".git"), "gitdir: real-git-dir\n");
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/worktree/current\n");

        assertThat(new WorkflowCurrentBranchResolver().resolve(dir))
                .contains("worktree/current");
    }
}
