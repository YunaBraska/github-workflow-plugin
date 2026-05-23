package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.psi.PsiElement;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowDocumentationTest extends EditorFeatureTestCase {

    public void testUsesDocumentationShowsResolvedActionMetadata() {
        final GitHubAction action = seedRemoteAction(
                "actions/setup-java@v4",
                Map.of("distribution", "Description: Java distribution\nRequired: true\nDefault: temurin"),
                Map.of("cache-hit", "Description: Whether cache was restored")
        );
        action.displayName("Setup Java")
                .description("Set up a specific version of Java and add it to PATH.");

        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/<caret>setup-java@v4
                """);

        assertThat(documentationHintAtCaret())
                .contains("Setup Java")
                .contains("actions/setup-java@v4");
    }

    public void testInputVariableDocumentationShowsMetadata() {
        configureWorkflowProjectFile("""
                name: Docs
                on:
                  workflow_dispatch:
                    inputs:
                      tag:
                        description: Release tag
                        required: true
                        type: string
                        default: v1
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.<caret>tag }}"
                """);

        assertThat(documentationHintAtCaret())
                .contains("Input tag")
                .contains("Description: Release tag")
                .contains("Type: string")
                .contains("Required: true")
                .contains("Default: v1");
    }

    public void testActionInputDocumentationShowsResolvedActionParameter() {
        seedRemoteAction(
                "actions/setup-java@v4",
                Map.of("distribution", "Description: Java distribution\nRequired: true\nDefault: temurin"),
                Map.of()
        );

        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/setup-java@v4
                        with:
                          <caret>distribution: temurin
                """);

        assertThat(documentationHintAtCaret())
                .contains("Input distribution")
                .contains("Java distribution")
                .contains("Required: true")
                .contains("Default: temurin");
    }

    public void testActionInputHoverDocumentationShowsResolvedActionParameter() {
        seedRemoteAction(
                "actions/checkout@v4",
                Map.of("fetch-depth", "Description: Number of commits to fetch\nDefault: 1"),
                Map.of()
        );

        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          <caret>fetch-depth: 500
                """);

        assertThat(documentationHtmlAtCaret())
                .contains("Input")
                .contains("fetch-depth")
                .contains("Number of commits to fetch")
                .contains("Default");
    }

    public void testStepOutputDocumentationShowsOutputName() {
        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: java_info
                        run: echo "is_gradle=true" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.java_info.outputs.<caret>is_gradle }}"
                """);

        assertThat(documentationHintAtCaret()).contains("Step output is_gradle");
    }

    public void testActionOutputDocumentationShowsResolvedDescription() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of("artifact", "Description: Artifact path"));

        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        uses: owner/tool@v1
                      - run: echo "${{ steps.package.outputs.<caret>artifact }}"
                """);

        assertThat(documentationHintAtCaret())
                .contains("Step output artifact")
                .contains("Description: Artifact path");
    }

    public void testActionOutputDocumentationShowsSourceStepAndActionLink() {
        final GitHubAction action = seedRemoteAction(
                "YunaBraska/java-info-action@main",
                Map.of(),
                Map.of("project_version", "Description: Project version\nType: string")
        );
        action.displayName("Java Info").description("Reads Java metadata.");

        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  tag:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Read Java Info
                        id: java_info
                        uses: YunaBraska/java-info-action@main
                      - run: echo "${{ steps.java_info.outputs.<caret>project_version }}"
                """);

        assertThat(documentationHintAtCaret())
                .contains("Step output project_version")
                .contains("Description: Project version")
                .contains("Step: Read Java Info (java_info)")
                .contains("Uses: YunaBraska/java-info-action@main")
                .contains("External action: Java Info - Reads Java metadata.");
        assertThat(documentationHtmlAtCaret())
                .contains("href=\"https://github.com/YunaBraska/java-info-action/tree/main#readme\"")
                .contains(">YunaBraska/java-info-action@main</a>");
    }

    public void testJobOutputDocumentationShowsMappedStepActionOutput() {
        final GitHubAction action = seedRemoteAction(
                "YunaBraska/java-info-action@main",
                Map.of(),
                Map.of("java_version", "Description: Java version\nType: string")
        );
        action.displayName("Java Info").description("Reads Java metadata.");

        configureWorkflowProjectFile("""
                name: Docs
                on:
                  workflow_call:
                    outputs:
                      java_version:
                        description: "[String] java version from pom file"
                        value: ${{ jobs.tag.outputs.<caret>java_version }}
                jobs:
                  tag:
                    runs-on: ubuntu-latest
                    outputs:
                      java_version: ${{ steps.java_info.outputs.java_version }}
                    steps:
                      - name: Read Java Info
                        id: java_info
                        uses: YunaBraska/java-info-action@main
                """);

        assertThat(documentationHintAtCaret())
                .contains("Reusable workflow job output java_version")
                .contains("Java Info")
                .contains("Reads Java metadata");
    }

    public void testStepDocumentationShowsResolvedActionNameAndDescription() {
        final GitHubAction action = seedRemoteAction("YunaBraska/java-info-action@main", Map.of(), Map.of());
        action.displayName("Java Info").description("Reads Java metadata.");

        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  tag:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Read Java Info
                        id: java_info
                        uses: YunaBraska/java-info-action@main
                      - run: echo "${{ steps.<caret>java_info.outputs.java_version }}"
                """);

        assertThat(documentationHtmlAtCaret())
                .contains("Step")
                .contains("Read Java Info")
                .contains("Java Info")
                .contains("Reads Java metadata");
    }

    public void testExpressionContextDocumentationShowsCollectionMeaning() {
        configureWorkflowProjectFile("""
                name: Docs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: java_info
                        run: echo "is_gradle=true" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.java_info.<caret>outputs.is_gradle }}"
                """);

        assertThat(documentationHintAtCaret()).contains("Output values exposed");
    }

    private String documentationHintAtCaret() {
        final WorkflowDocumentationProvider provider = new WorkflowDocumentationProvider();
        final PsiElement context = elementAtCaret();
        final PsiElement target = provider.getCustomDocumentationElement(
                myFixture.getEditor(),
                myFixture.getFile(),
                context,
                myFixture.getCaretOffset()
        );
        assertThat(target).isNotNull();
        return provider.getQuickNavigateInfo(target, context);
    }

    private String documentationHtmlAtCaret() {
        final WorkflowDocumentationProvider provider = new WorkflowDocumentationProvider();
        final PsiElement context = elementAtCaret();
        final PsiElement target = provider.getCustomDocumentationElement(
                myFixture.getEditor(),
                myFixture.getFile(),
                context,
                myFixture.getCaretOffset()
        );
        assertThat(target).isNotNull();
        return provider.generateHoverDoc(target, context);
    }

    private PsiElement elementAtCaret() {
        final PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        if (element != null) {
            return element;
        }
        return myFixture.getFile().findElementAt(Math.max(0, myFixture.getCaretOffset() - 1));
    }
}
