package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubAction;

import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;

public class WorkflowStylingTest extends EditorFeatureTestCase {

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
            RemoteServerSettings.getInstance().setCustomServers(List.of(new RemoteServerSettings.Server("Fake Enterprise",
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.VARIABLE_REFERENCE);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.VARIABLE_REFERENCE);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.VARIABLE_REFERENCE);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.VARIABLE_REFERENCE);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.RUNNER_VARIABLE);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.SCALAR_LITERAL);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.SCALAR_LITERAL);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.DECLARATION);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.DECLARATION);
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

        assertNoTextAttributeAtCurrentCaret(WorkflowTextAttributes.VARIABLE_REFERENCE);
        assertNoTextAttributeAtCurrentCaret(WorkflowTextAttributes.DECLARATION);
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

        assertTextAttributeAtCurrentCaret(WorkflowTextAttributes.VARIABLE_REFERENCE);
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
}
