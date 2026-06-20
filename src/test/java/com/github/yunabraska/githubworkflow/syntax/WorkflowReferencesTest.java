package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.test.FakeRemoteServer;

import com.github.yunabraska.githubworkflow.test.EditorFeatureTestCase;

import com.github.yunabraska.githubworkflow.syntax.WorkflowReferences;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.openapi.paths.WebReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowReferences.ACTION_KEY;
import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;

public class WorkflowReferencesTest extends EditorFeatureTestCase {

    public void testLocalActionReferenceResolvesToActionFile() {
        final PsiFile actionFile = myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                inputs:
                  known-input:
                    description: Known input
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./.github/actions/<caret>local
                """);
        seedLocalAction("./.github/actions/local", actionFile);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getContainingFile().getName()).isEqualTo("action.yml");
    }

    public void testLocalActionYamlReferenceResolvesToActionFile() {
        final PsiFile actionFile = myFixture.addFileToProject(".github/actions/local/action.yaml", """
                name: Local Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./.github/actions/<caret>local
                """);
        seedLocalAction("./.github/actions/local", actionFile);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getContainingFile().getName()).isEqualTo("action.yaml");
    }

    public void testRootLocalActionReferenceResolvesToActionFile() {
        final PsiFile actionFile = myFixture.addFileToProject("action.yml", """
                name: Root Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: <caret>./
                """);
        seedLocalAction("./", actionFile);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getContainingFile().getName()).isEqualTo("action.yml");
    }

    public void testLocalReusableWorkflowReferenceResolvesToWorkflowFile() {
        final PsiFile workflowFile = myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Reusable
                on:
                  workflow_call:
                    inputs:
                      config-path:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/<caret>reusable.yml
                """);
        seedLocalAction("./.github/workflows/reusable.yml", workflowFile);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getContainingFile().getName()).isEqualTo("reusable.yml");
    }

    public void testRemoteActionReferenceKeepsGithubUrl() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of());
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/<caret>tool@v1
                """);

        final PsiReference reference = referenceAtCaret();

        assertThat(reference).isInstanceOf(WebReference.class);
        assertThat(reference.getCanonicalText()).isEqualTo("owner/tool@v1");
        assertThat(reference.getElement().getUserData(ACTION_KEY))
                .extracting(GitHubAction::githubUrl)
                .isEqualTo("https://github.com/owner/tool/tree/v1#readme");
    }

    public void testRemoteReusableWorkflowReferenceKeepsGithubUrl() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of());
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/<caret>build.yml@v1
                """);

        final PsiReference reference = referenceAtCaret();

        assertThat(reference).isInstanceOf(WebReference.class);
        assertThat(reference.getCanonicalText()).isEqualTo("owner/repo/.github/workflows/build.yml@v1");
        assertThat(reference.getElement().getUserData(ACTION_KEY))
                .extracting(GitHubAction::githubUrl)
                .isEqualTo("https://github.com/owner/repo/blob/v1/.github/workflows/build.yml");
    }

    public void testConfiguredGithubEnterpriseRemoteActionReferenceKeepsServerUrl() throws Exception {
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
                    name: References
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: %s/acme/<caret>tool@main
                    """.formatted(server.webUrl()));

            final PsiReference reference = referenceAtCaret();

            assertThat(reference).isInstanceOf(WebReference.class);
            assertThat(reference.getElement().getUserData(ACTION_KEY))
                    .extracting(GitHubAction::githubUrl)
                    .isEqualTo(server.webUrl() + "/acme/tool/tree/main#readme");
        }
    }

    public void testNeedsScalarReferenceResolvesToPreviousJob() {
        configureWorkflowProjectFile("""
                name: References
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

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("build");
    }

    public void testNeedsQuotedScalarReferenceResolvesToPreviousJob() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: "<caret>build"
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("build");
    }

    public void testNeedsArrayReferenceResolvesToPreviousJob() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: [ <caret>build ]
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("build");
    }

    public void testWorkflowInputExpressionReferenceResolvesToInputKey() {
        configureWorkflowProjectFile("""
                name: References
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

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("known-input");
    }

    public void testMultipleExpressionsResolveReferenceAtCaretOnly() {
        configureWorkflowProjectFile("""
                name: References
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ inputs.known-input }} ${{ steps.<caret>package.outputs.artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("id");
        assertThat(((YAMLKeyValue) resolved).getValueText()).isEqualTo("package");
    }

    public void testBracketWorkflowInputExpressionReferenceResolvesToInputKey() {
        configureWorkflowProjectFile("""
                name: References
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs['<caret>known-input'] }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("known-input");
    }

    public void testWorkflowCallInputDefaultExpressionReferenceResolvesToInputKey() {
        configureWorkflowProjectFile("""
                name: References
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

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("known-input");
    }

    public void testStepExpressionReferenceResolvesToStepId() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.<caret>package.outputs.artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("id");
        assertThat(((YAMLKeyValue) resolved).getValueText()).isEqualTo("package");
    }

    public void testBracketStepExpressionReferenceResolvesToStepId() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps['<caret>package'].outputs.artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("id");
        assertThat(((YAMLKeyValue) resolved).getValueText()).isEqualTo("package");
    }

    public void testStepRunOutputExpressionReferenceResolvesToRunKey() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.<caret>artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("run");
    }

    public void testStepActionOutputExpressionReferenceResolvesToUsesKey() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of("artifact", "Artifact"));
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        uses: owner/tool@v1
                      - run: echo "${{ steps.package.outputs.<caret>artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("uses");
    }

    public void testNeedsExpressionReferenceResolvesToNeedsScalar() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                  test:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.<caret>build.outputs.artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getText()).isEqualTo("build");
    }

    public void testBracketNeedsExpressionReferenceResolvesToNeedsScalar() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                  test:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs['<caret>build'].outputs.artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getText()).isEqualTo("build");
    }

    public void testNeedsOutputExpressionReferenceResolvesToJobOutputKey() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                  test:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.build.outputs.<caret>artifact }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("artifact");
    }

    public void testJobsWorkflowOutputExpressionReferenceResolvesToJob() {
        configureWorkflowProjectFile("""
                name: References
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.<caret>build.outputs.artifact }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("build");
    }

    public void testJobsOutputExpressionReferenceResolvesToJobOutputKey() {
        configureWorkflowProjectFile("""
                name: References
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.build.outputs.<caret>artifact }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("artifact");
    }

    public void testWorkflowEnvExpressionReferenceResolvesToEnvKey() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                env:
                  TOP_LEVEL: top
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ env.<caret>TOP_LEVEL }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("TOP_LEVEL");
    }

    public void testStepEnvExpressionReferencePrefersStepEnvKey() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                env:
                  SHARED: root
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    env:
                      SHARED: job
                    steps:
                      - env:
                          SHARED: step
                        run: echo "${{ env.<caret>SHARED }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("SHARED");
        assertThat(((YAMLKeyValue) resolved).getValueText()).isEqualTo("step");
    }

    public void testJobEnvMapAliasExpressionReferenceResolvesToAnchoredEnvKey() {
        configureWorkflowProjectFile("""
                name: References
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

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("NODE_ENV");
    }

    public void testMatrixExpressionReferenceResolvesToMatrixKey() {
        configureWorkflowProjectFile("""
                name: References
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

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("os");
    }

    public void testMatrixIncludeExpressionReferenceResolvesToIncludeKey() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        include:
                          - os: ubuntu-latest
                            node: 25
                    steps:
                      - run: echo "${{ matrix.<caret>node }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("node");
    }

    public void testWorkflowCallSecretExpressionReferenceResolvesToSecretKey() {
        configureWorkflowProjectFile("""
                name: References
                on:
                  workflow_call:
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.<caret>SECRET_TOKEN }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("SECRET_TOKEN");
    }

    public void testJobServiceExpressionReferenceResolvesToServiceKey() {
        configureWorkflowProjectFile("""
                name: References
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

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("postgres");
    }

    public void testJobServicePortExpressionReferenceResolvesToPortsKey() {
        configureWorkflowProjectFile("""
                name: References
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                        ports:
                          - 5432/tcp
                    steps:
                      - run: echo "${{ job.services.postgres.ports[<caret>5432] }}"
                """);

        final PsiElement resolved = referenceAtCaret().resolve();

        assertThat(resolved).isInstanceOf(YAMLKeyValue.class);
        assertThat(((YAMLKeyValue) resolved).getKeyText()).isEqualTo("ports");
    }
}
