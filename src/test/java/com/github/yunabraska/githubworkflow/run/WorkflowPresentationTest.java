package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.entry.WorkflowAnnotator;

import com.github.yunabraska.githubworkflow.entry.WorkflowDocumentationProvider;

import com.github.yunabraska.githubworkflow.test.FakeRemoteServer;

import com.github.yunabraska.githubworkflow.test.EditorFeatureTestCase;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowPresentationTest extends EditorFeatureTestCase {

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

    public void testResolvedRemoteActionUseIsStyledAsReference() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of());

        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/<caret>tool@v1
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testConfiguredGithubEnterpriseRemoteActionUseIsStyledAsReference() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "tool", "action.yml", "main", """
                    name: Enterprise Tool
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            RemoteActionProviders.Settings.getInstance().setCustomServers(List.of(new RemoteActionProviders.Server("Fake Enterprise",
                    server.webUrl(),
                    server.apiUrl("/api/v3"),
                    "",
                    true
            )));
            final String usesValue = server.webUrl() + "/acme/tool@main";
            final GitHubAction action = GitHubAction.createGithubAction(false, usesValue, usesValue).resolve();
            getActionCache().getState().actions.put(usesValue, action);

            configureWorkflowProjectFile("""
                    name: Styling
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: %s/acme/<caret>tool@main
                    """.formatted(server.webUrl()));

            assertHighlightedReferenceAtCurrentCaret();
        }
    }

    public void testResolvedLocalWorkflowUseIsStyledAsReference() {
        seedLocalAction("./.github/workflows/reusable.yml", myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Reusable
                on: workflow_call
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """));

        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/<caret>reusable.yml
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testWorkflowInputExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.<caret>known-input }}"
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testWorkflowInputExpressionUsesWorkflowVariableColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.<caret>known-input }}"
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.VARIABLE_REFERENCE);
    }

    public void testUnresolvedExpressionContextSegmentUsesWorkflowVariableColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ <caret>steps.pom.outputs.has_pom }}"
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.VARIABLE_REFERENCE);
    }

    public void testAutomaticGithubTokenSecretUsesWorkflowVariableColorWithoutReferenceRequirement() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.<caret>GITHUB_TOKEN }}"
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.VARIABLE_REFERENCE);
    }

    public void testIfExpressionWithoutBracesUsesWorkflowVariableColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    if: <caret>github.ref == 'refs/heads/main'
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.VARIABLE_REFERENCE);
    }

    public void testRunCommandGithubOutputUsesRunnerVariableColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "java_version=21" >> "$<caret>GITHUB_OUTPUT"
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.RUNNER_VARIABLE);
    }

    public void testBooleanAndNumberScalarsUseLiteralColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          generateReleaseNotes: <caret>true
                          fetch-depth: 500
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.SCALAR_LITERAL);
    }

    public void testNumberScalarsUseLiteralColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          fetch-depth: <caret>500
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.SCALAR_LITERAL);
    }

    public void testJobIdUsesWorkflowDeclarationColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  <caret>build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.DECLARATION);
    }

    public void testStepIdUsesWorkflowDeclarationColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: <caret>package
                        run: echo ok
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.DECLARATION);
    }

    public void testMixedStepNameTextDoesNotUseWorkflowVariableOrDeclarationColor() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: java_info
                        run: echo "project_version=1.0.0" >> "$GITHUB_OUTPUT"
                      - id: semver_info
                        run: echo "clean_semver=1.0.1" >> "$GITHUB_OUTPUT"
                      - name: "Update<caret> Project Version (Maven Only) [${{ steps.java_info.outputs.project_version }} > ${{ steps.semver_info.outputs.clean_semver }}]"
                        run: echo ok
                """);

        assertNoTextAttributeAtCurrentCaret(WorkflowAnnotator.VARIABLE_REFERENCE);
        assertNoTextAttributeAtCurrentCaret(WorkflowAnnotator.DECLARATION);
    }

    public void testMixedStepNameExpressionUsesWorkflowVariableColorOnlyInsideExpression() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: java_info
                        run: echo "project_version=1.0.0" >> "$GITHUB_OUTPUT"
                      - id: semver_info
                        run: echo "clean_semver=1.0.1" >> "$GITHUB_OUTPUT"
                      - name: "Update Project Version (Maven Only) [${{ steps.java_info.outputs.<caret>project_version }} > ${{ steps.semver_info.outputs.clean_semver }}]"
                        run: echo ok
                """);

        assertTextAttributeAtCurrentCaret(WorkflowAnnotator.VARIABLE_REFERENCE);
    }

    public void testWorkflowCallInputDefaultExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                      target:
                        type: string
                        default: ${{ inputs.<caret>known-input }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testEnvExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                env:
                  TOP_LEVEL: top
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ env.<caret>TOP_LEVEL }}"
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testJobEnvMapAliasExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  define:
                    runs-on: ubuntu-latest
                    env: &env_vars
                      NODE_ENV: production
                    steps:
                      - run: echo ok
                  reuse:
                    runs-on: ubuntu-latest
                    env: *env_vars
                    steps:
                      - run: echo "${{ env.<caret>NODE_ENV }}"
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testMatrixExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - run: echo "${{ matrix.<caret>os }}"
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testStepOutputExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.<caret>artifact }}"
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testNeedsScalarIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: <caret>build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testJobServiceExpressionIsStyledAsReference() {
        configureWorkflowProjectFile("""
                name: Styling
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.<caret>postgres.network }}"
                """);

        assertHighlightedReferenceAtCurrentCaret();
    }

    public void testRunBlockInjectsShellScriptLanguageWhenAvailable() {
        assertThat(Language.findLanguageByID("Shell Script")).isNotNull();

        configureWorkflowProjectFile("""
                name: Injection
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - shell: bash
                        run: |
                          echo "hello"
                          <caret>if [ -f pom.xml ]; then
                            echo ok
                          fi
                """);

        final YAMLScalar scalar = scalarAtCaret();
        final List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(scalar);

        assertThat(injected).isNotEmpty();
        assertThat(injected.get(0).first.getLanguage().getID()).isEqualTo("Shell Script");
    }

    private YAMLScalar scalarAtCaret() {
        final int offset = myFixture.getCaretOffset();
        final YAMLScalar scalar = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), YAMLScalar.class)
                .stream()
                .filter(candidate -> candidate.getTextRange().getStartOffset() <= offset)
                .filter(candidate -> offset <= candidate.getTextRange().getEndOffset())
                .findFirst()
                .orElse(null);
        assertThat(scalar).isNotNull();
        return scalar;
    }
}
