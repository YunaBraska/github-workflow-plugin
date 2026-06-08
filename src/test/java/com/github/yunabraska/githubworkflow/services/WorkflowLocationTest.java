package com.github.yunabraska.githubworkflow.services;

import junit.framework.TestCase;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowLocationTest extends TestCase {

    public void testGithubHttpsRemoteUsesPublicApi() {
        assertThat(WorkflowLocation.RepositoryResolver.fromRemoteUrl("https://github.com/YunaBraska/github-workflow-plugin.git"))
                .contains(new WorkflowLocation.Repository(
                        "https://github.com",
                        "https://api.github.com",
                        "YunaBraska",
                        "github-workflow-plugin"
                ));
    }

    public void testEnterpriseHttpsRemoteUsesApiV3() {
        assertThat(WorkflowLocation.RepositoryResolver.fromRemoteUrl("https://github.acme.test/tools/workflows.git"))
                .contains(new WorkflowLocation.Repository(
                        "https://github.acme.test",
                        "https://github.acme.test/api/v3",
                        "tools",
                        "workflows"
                ));
    }

    public void testSshRemoteUsesPublicApi() {
        assertThat(WorkflowLocation.RepositoryResolver.fromRemoteUrl("git@github.com:YunaBraska/github-workflow-plugin.git"))
                .contains(new WorkflowLocation.Repository(
                        "https://github.com",
                        "https://api.github.com",
                        "YunaBraska",
                        "github-workflow-plugin"
                ));
    }

    public void testResolveReadsOriginFromGitConfig() throws Exception {
        final Path dir = Files.createTempDirectory("workflow-repo");
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("config"), """
                [remote "origin"]
                    url = https://github.com/YunaBraska/github-workflow-plugin.git
                """);

        assertThat(new WorkflowLocation.RepositoryResolver().resolve(dir))
                .contains(new WorkflowLocation.Repository(
                        "https://github.com",
                        "https://api.github.com",
                        "YunaBraska",
                        "github-workflow-plugin"
                ));
    }

    public void testBranchNameReadsRefsHeadsBranch() {
        assertThat(WorkflowLocation.RepositoryResolver.branchName("ref: refs/heads/feature/logs\n"))
                .contains("feature/logs");
    }

    public void testBranchNameIgnoresDetachedHead() {
        assertThat(WorkflowLocation.RepositoryResolver.branchName("e1a9e573f4d0838b3a7c1b07401aeb29ed3635a9"))
                .isEmpty();
    }

    public void testResolveReadsCurrentBranchFromGitHead() throws Exception {
        final Path dir = Files.createTempDirectory("workflow-branch");
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("HEAD"), "ref: refs/heads/feature/current\n");

        assertThat(new WorkflowLocation.RepositoryResolver().branch(dir))
                .contains("feature/current");
    }

    public void testResolveReadsCurrentBranchFromWorktreeGitFile() throws Exception {
        final Path dir = Files.createTempDirectory("workflow-worktree");
        final Path gitDir = Files.createDirectories(dir.resolve("real-git-dir"));
        Files.writeString(dir.resolve(".git"), "gitdir: real-git-dir\n");
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/worktree/current\n");

        assertThat(new WorkflowLocation.RepositoryResolver().branch(dir))
                .contains("worktree/current");
    }
}
