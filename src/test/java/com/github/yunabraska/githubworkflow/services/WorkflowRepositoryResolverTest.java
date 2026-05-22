package com.github.yunabraska.githubworkflow.services;

import junit.framework.TestCase;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRepositoryResolverTest extends TestCase {

    public void testGithubHttpsRemoteUsesPublicApi() {
        assertThat(WorkflowRepositoryResolver.fromRemoteUrl("https://github.com/YunaBraska/github-workflow-plugin.git"))
                .contains(new WorkflowRepository(
                        "https://github.com",
                        "https://api.github.com",
                        "YunaBraska",
                        "github-workflow-plugin"
                ));
    }

    public void testEnterpriseHttpsRemoteUsesApiV3() {
        assertThat(WorkflowRepositoryResolver.fromRemoteUrl("https://github.acme.test/tools/workflows.git"))
                .contains(new WorkflowRepository(
                        "https://github.acme.test",
                        "https://github.acme.test/api/v3",
                        "tools",
                        "workflows"
                ));
    }

    public void testSshRemoteUsesPublicApi() {
        assertThat(WorkflowRepositoryResolver.fromRemoteUrl("git@github.com:YunaBraska/github-workflow-plugin.git"))
                .contains(new WorkflowRepository(
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

        assertThat(new WorkflowRepositoryResolver().resolve(dir))
                .contains(new WorkflowRepository(
                        "https://github.com",
                        "https://api.github.com",
                        "YunaBraska",
                        "github-workflow-plugin"
                ));
    }
}
