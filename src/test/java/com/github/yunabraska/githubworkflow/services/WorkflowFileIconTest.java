package com.github.yunabraska.githubworkflow.services;

import com.intellij.icons.AllIcons;

import javax.swing.Icon;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowFileIconTest extends EditorFeatureTestCase {

    public void testGithubWorkflowUsesGithubIcon() {
        configureProjectFile(".github/workflows/build.yml", """
                name: CI
                on: push
                jobs: {}
                """);

        final Icon icon = new WorkflowSyntax.FileIcon().getIcon(myFixture.getFile(), 0);

        assertThat(icon).isSameAs(AllIcons.Vcs.Vendors.Github);
    }

    public void testGiteaWorkflowUsesGiteaIcon() {
        configureProjectFile(".gitea/workflows/build.yml", """
                name: CI
                on: push
                jobs: {}
                """);

        final Icon icon = new WorkflowSyntax.FileIcon().getIcon(myFixture.getFile(), 0);

        assertThat(icon)
                .isNotNull()
                .isNotSameAs(AllIcons.Vcs.Vendors.Github);
    }

    public void testGiteaIconVariantsArePackaged() {
        assertThat(getClass().getClassLoader().getResource("icons/gitea.svg")).isNotNull();
        assertThat(getClass().getClassLoader().getResource("icons/gitea_dark.svg")).isNotNull();
    }

    public void testLightGiteaIconAvoidsWhiteDetails() throws Exception {
        final InputStream stream = getClass().getClassLoader().getResourceAsStream("icons/gitea.svg");
        assertThat(stream).isNotNull();
        final String icon = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(icon.toUpperCase())
                .doesNotContain("#FFFFFF")
                .doesNotContain("#E8F5E2");
    }

    private void configureProjectFile(final String path, final String text) {
        myFixture.addFileToProject(path, text);
        myFixture.configureFromTempProjectFile(path);
    }
}
