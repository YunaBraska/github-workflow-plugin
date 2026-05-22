package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubAction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowCompletionTest extends EditorFeatureTestCase {

    public void testRootCompletionSuggestsAvailableContexts() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                    secrets:
                      SECRET_TOKEN:
                        required: false
                env:
                  TOP_LEVEL: top
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - id: package
                        run: echo ok
                      - run: echo "${{ <caret> }}"
                """)).contains("github", "job", "runner", "strategy", "matrix", "env", "inputs", "secrets", "steps");
    }

    public void testGithubCompletionSuggestsRefName() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.<caret> }}"
                """)).contains("ref_name");
    }

    public void testGithubCompletionSuggestsCurrentDocumentedIds() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.<caret> }}"
                """)).contains("actor_id", "ref_protected", "repository_owner_id");
    }

    public void testIfExpressionCompletionAfterDotWithoutBraces() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    if: github.<caret>
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """)).contains("ref_name");
    }

    public void testRunnerCompletionSuggestsEnvironment() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.<caret> }}"
                """)).contains("environment");
    }

    public void testRunnerCompletionSuggestsDebug() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.<caret> }}"
                """)).contains("debug");
    }

    public void testJobCompletionSuggestsStatus() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ job.<caret> }}"
                """)).contains("status");
    }

    public void testJobContainerCompletionSuggestsContainerMembers() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container: postgres:16
                    steps:
                      - run: echo "${{ job.container.<caret> }}"
                """)).contains("id", "network");
    }

    public void testJobServicesCompletionSuggestsServiceIds() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.<caret> }}"
                """)).contains("postgres");
    }

    public void testJobServiceCompletionSuggestsServiceMembers() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.postgres.<caret> }}"
                """)).contains("id", "network", "ports");
    }

    public void testJobServicePortsCompletionSuggestsMappedPorts() {
        assertThat(completeWorkflow("""
                name: Completion
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
                      - run: echo "${{ job.services.postgres.ports[<caret>] }}"
                """)).contains("5432");
    }

    public void testStrategyCompletionSuggestsJobIndex() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - run: echo "${{ strategy.<caret> }}"
                """)).contains("job-index", "job-total", "max-parallel");
    }

    public void testMatrixCompletionUsesCurrentJobMatrixKeys() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest]
                        node: [24, 25]
                    steps:
                      - run: echo "${{ matrix.<caret> }}"
                """)).contains("os", "node");
    }

    public void testMatrixCompletionUsesIncludeKeys() {
        assertThat(completeWorkflow("""
                name: Completion
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
                      - run: echo "${{ matrix.<caret> }}"
                """)).contains("os", "node");
    }

    public void testEnvCompletionIncludesWorkflowJobStepAndRunEnvironmentValues() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                env:
                  WORKFLOW_LEVEL: workflow
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    env:
                      JOB_LEVEL: job
                    steps:
                      - run: echo "RUN_LEVEL=run" >> "$GITHUB_ENV"
                      - env:
                          STEP_LEVEL: step
                        run: echo "${{ env.<caret> }}"
                """)).contains("WORKFLOW_LEVEL", "JOB_LEVEL", "STEP_LEVEL", "RUN_LEVEL");
    }

    public void testEnvCompletionIncludesJobEnvMapAliasValues() {
        assertThat(completeWorkflow("""
                name: Completion
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
                      - run: echo "${{ env.<caret> }}"
                """)).contains("NODE_ENV");
    }

    public void testInputsCompletionUsesWorkflowCallInputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.<caret> }}"
                """)).contains("deploy-target");
    }

    public void testWorkflowCallInputDefaultCompletionSuggestsInputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                      target:
                        type: string
                        default: ${{ inputs.<caret> }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """)).contains("deploy-target");
    }

    public void testJobContainerCredentialsCompletionSuggestsInputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      registry-user:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container:
                      image: ghcr.io/owner/image
                      credentials:
                        username: ${{ inputs.<caret> }}
                    steps:
                      - run: echo ok
                """)).contains("registry-user");
    }

    public void testJobStrategyFailFastCompletionSuggestsInputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      fail-fast:
                        type: boolean
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    strategy:
                      fail-fast: ${{ inputs.<caret> }}
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - run: echo ok
                """)).contains("fail-fast");
    }

    public void testBracketInputCompletionUsesWorkflowCallInputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs['<caret>'] }}"
                """)).contains("deploy-target");
    }

    public void testBracketInputCompletionHonorsPrefix() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                      package-name:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs['dep<caret>'] }}"
                """)).contains("deploy-target").doesNotContain("package-name");
    }

    public void testSecretsCompletionUsesWorkflowCallSecrets() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.<caret> }}"
                """)).contains("SECRET_TOKEN");
    }

    public void testSecretsCompletionIncludesAutomaticGithubToken() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.<caret> }}"
                """)).contains("GITHUB_TOKEN");
    }

    public void testBracketGithubCompletionSuggestsRefName() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github['<caret>'] }}"
                """)).contains("ref_name");
    }

    public void testNeedsFieldCompletionSuggestsPreviousJobs() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  lint:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: <caret>
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """)).contains("build");
    }

    public void testNeedsContextCompletionSuggestsDirectNeeds() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.<caret> }}"
                """)).contains("build");
    }

    public void testBracketNeedsCompletionSuggestsDirectNeeds() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs['<caret>'] }}"
                """)).contains("build");
    }

    public void testNeedsContextMemberCompletionSuggestsOutputsAndResult() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.build.<caret> }}"
                """)).contains("outputs", "result");
    }

    public void testNeedsOutputCompletionSuggestsJobOutputs() {
        assertThat(completeWorkflow("""
                name: Completion
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
                      - run: echo "${{ needs.build.outputs.<caret> }}"
                """)).contains("artifact");
    }

    public void testRunScriptExpressionCompletionSuggestsNeedsOutputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  github_release:
                    runs-on: ubuntu-latest
                    outputs:
                      has_pom: ${{ steps.pom.outputs.has_pom }}
                    steps:
                      - id: pom
                        run: echo "has_pom=true" >> "$GITHUB_OUTPUT"
                  show:
                    needs: github_release
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "has_pom [${{ needs.github_release.outputs.<caret> }}]"
                """)).contains("has_pom");
    }

    public void testBracketNeedsOutputCompletionSuggestsJobOutputs() {
        assertThat(completeWorkflow("""
                name: Completion
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
                      - run: echo "${{ needs['build'].outputs['<caret>'] }}"
                """)).contains("artifact");
    }

    public void testJobsWorkflowOutputCompletionSuggestsJobs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.<caret> }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """)).contains("build");
    }

    public void testJobsContextMemberCompletionSuggestsOutputsAndResult() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.build.<caret> }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """)).contains("outputs", "result");
    }

    public void testJobsOutputCompletionSuggestsJobOutputs() {
        assertThat(completeWorkflow("""
                name: Completion
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.build.outputs.<caret> }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """)).contains("artifact");
    }

    public void testStepsCompletionSuggestsPreviousStepIds() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo ok
                      - run: echo "${{ steps.<caret> }}"
                """)).contains("package");
    }

    public void testBracketStepsCompletionSuggestsPreviousStepIds() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo ok
                      - run: echo "${{ steps['<caret>'] }}"
                """)).contains("package");
    }

    public void testStepsMemberCompletionSuggestsOutputsOutcomeConclusion() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo ok
                      - run: echo "${{ steps.package.<caret> }}"
                """)).contains("outputs", "outcome", "conclusion");
    }

    public void testStepsOutputCompletionSuggestsRunOutput() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.<caret> }}"
                """)).contains("artifact");
    }

    public void testBracketStepsOutputCompletionSuggestsRunOutput() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps['package'].outputs['<caret>'] }}"
                """)).contains("artifact");
    }

    public void testStepsOutputCompletionSuggestsActionOutput() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of("artifact", "Artifact path"));

        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        uses: owner/tool@v1
                      - run: echo "${{ steps.package.outputs.<caret> }}"
                """)).contains("artifact");
    }

    public void testCompositeActionOutputStepCompletionSuggestsRunStep() {
        assertThat(completeWorkflow("""
                name: Composite Action
                outputs:
                  artifact:
                    value: ${{ steps.<caret> }}
                runs:
                  using: composite
                  steps:
                    - id: package
                      run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      shell: sh
                """)).contains("package");
    }

    public void testActionInputCompletionUsesResolvedActionMetadata() {
        seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input", "other-input", "Other input"), Map.of());

        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          <caret>
                """)).contains("known-input");
    }

    public void testReusableWorkflowInputCompletionUsesResolvedWorkflowMetadata() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of("config-path", "Config path"), Map.of());

        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    with:
                      <caret>
                """)).contains("config-path");
    }

    public void testReusableWorkflowOutputCompletionSuggestsWorkflowOutputs() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of("artifact", "Artifact path"));

        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                  consume:
                    needs: call
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.call.outputs.<caret> }}"
                """)).contains("artifact");
    }

    public void testReusableWorkflowSecretCompletionSuggestsWorkflowSecrets() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of(), Map.of("access-token", "Access token"));

        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    secrets:
                      <caret>
                """)).contains("access-token");
    }

    public void testLocalActionInputCompletionUsesActionYamlMetadata() throws Exception {
        final Path actionYaml = Files.createTempFile("github-workflow-local-action", ".yaml");
        actionYaml.toFile().deleteOnExit();
        Files.writeString(actionYaml, """
                name: Local Action
                inputs:
                  local-input:
                    description: Local input
                  other-local-input:
                    description: Other local input
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        final GitHubAction action = GitHubAction.createGithubAction(true, "./.github/actions/local", actionYaml.toString())
                .isResolved(true);
        getActionCache().getState().actions.put("./.github/actions/local", action);
        assertThat(action.freshInputs()).containsKey("local-input");

        configureWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./.github/actions/local
                        with:
                          <caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("local-input");
    }

    public void testProjectLocalActionInputCompletionUsesActionYamlMetadata() {
        final GitHubAction action = seedLocalAction("./.github/actions/local", myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                inputs:
                  local-input:
                    description: Local input
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """));
        assertThat(action.freshInputs()).containsKey("local-input");

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./.github/actions/local
                        with:
                          <caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("local-input");
    }

    public void testLocalReusableWorkflowInputCompletionUsesWorkflowCallMetadata() {
        final GitHubAction action = seedLocalAction("./.github/workflows/reusable.yml", myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Local Reusable
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
                """));
        assertThat(action.freshInputs()).containsKey("config-path");

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/reusable.yml
                    with:
                      <caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("config-path");
    }

    public void testLocalReusableWorkflowOutputCompletionUsesWorkflowCallMetadata() {
        final GitHubAction action = seedLocalAction("./.github/workflows/reusable.yml", myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Local Reusable
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.build.outputs.artifact }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """));
        assertThat(action.freshOutputs()).containsKey("artifact");

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/reusable.yml
                  consume:
                    needs: call
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.call.outputs.<caret> }}"
                """);

        assertThat(completeBasicLookupStrings()).contains("artifact");
    }

    public void testLocalReusableWorkflowSecretCompletionUsesWorkflowCallMetadata() {
        final GitHubAction action = seedLocalAction("./.github/workflows/reusable.yml", myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Local Reusable
                on:
                  workflow_call:
                    secrets:
                      access-token:
                        required: true
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """));
        assertThat(action.freshSecrets()).containsKey("access-token");

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/reusable.yml
                    secrets:
                      <caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("access-token");
    }

    public void testStepUsesCompletionSuggestsLocalActionDirectories() {
        myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: <caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("./.github/actions/local");
    }

    public void testUsesCompletionSuggestsKnownRemoteCallableTargets() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of());

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/<caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("owner/tool");
    }

    public void testUsesCompletionDiscoversRemoteCallableTargetsBeforeResolution() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.setRepositories("actions", Map.of(
                    "checkout", "Checkout repository",
                    "setup-java", "Set up Java"
            ));
            RemoteServerSettings.getInstance().setCustomServers(List.of(new RemoteServerSettings.Server(
                    "Fake Enterprise",
                    server.webUrl(),
                    server.apiUrl("/api/v3"),
                    "",
                    true
            )));

            configureWorkflowProjectFile("""
                    name: Completion
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/<caret>
                    """);

            assertThat(completeBasicLookupStrings()).contains("actions/checkout", "actions/setup-java");
        }
    }

    public void testUsesRefCompletionSuggestsKnownRemoteRefsFromCache() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of());
        seedRemoteAction("owner/tool@main", Map.of(), Map.of());

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@<caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("v1", "main");
    }

    public void testUsesRefCompletionDiscoversLatestRemoteRefsBeforeActionIsResolved() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.setTags("acme", "tool", List.of("v10", "v9", "v8", "v7", "v6", "v5", "v4", "v3", "v2", "v1", "v0"));
            RemoteServerSettings.getInstance().setCustomServers(List.of(new RemoteServerSettings.Server(
                    "Fake Enterprise",
                    server.webUrl(),
                    server.apiUrl("/api/v3"),
                    "",
                    true
            )));

            configureWorkflowProjectFile("""
                    name: Completion
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: acme/tool@<caret>
                    """);

            assertThat(completeBasicLookupStrings())
                    .contains("v10", "v9", "v8", "v7", "v6", "v5", "v4", "v3", "v2", "v1")
                    .doesNotContain("v0");
        }
    }

    public void testEmptyWithBlockCompletionSuggestsResolvedActionInputs() {
        seedRemoteAction("actions/checkout@v4", Map.of(
                "fetch-depth", "Description: Number of commits to fetch\nDefault: 1",
                "ref", "Description: Branch, tag, or SHA to checkout"
        ), Map.of());

        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          <caret>
                """)).contains("fetch-depth", "ref");
    }

    public void testUsesRefCompletionSuggestsRefsAlreadyPresentInWorkflow() {
        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                      - uses: owner/tool@<caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("v1");
    }

    public void testUsesRefCompletionSuggestsRefsResolvedFromConfiguredServer() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "tool", "action.yml", "v1", """
                    name: Enterprise Tool
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            server.setBranches("acme", "tool", List.of("main"));
            server.setTags("acme", "tool", List.of("v1"));
            RemoteServerSettings.getInstance().setCustomServers(List.of(new RemoteServerSettings.Server("Fake",
                    server.webUrl(),
                    server.apiUrl("/api/v3"),
                    "",
                    true
            )));
            configureWorkflowProjectFile("""
                    name: Completion
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: acme/tool@v1
                          - uses: acme/tool@<caret>
                    """);
            GitHubActionCache.getActionCache().get(getProject(), "acme/tool@v1").resolve();

            assertThat(completeBasicLookupStrings()).contains("main", "v1");
        }
    }

    public void testAbsoluteGithubServerUrlCompletionSuggestsRefsResolvedFromConfiguredServer() throws Exception {
        try (FakeRemoteServer server = new FakeRemoteServer()) {
            server.addContent("acme", "tool", "action.yml", "main", """
                    name: Enterprise Tool
                    runs:
                      using: composite
                      steps:
                        - run: echo ok
                          shell: sh
                    """);
            server.setBranches("acme", "tool", List.of("main"));
            server.setTags("acme", "tool", List.of("v2"));
            RemoteServerSettings.getInstance().setCustomServers(List.of(new RemoteServerSettings.Server("Fake Enterprise",
                    server.webUrl(),
                    server.apiUrl("/api/v3"),
                    "",
                    true
            )));
            final String usesValue = server.webUrl() + "/acme/tool@main";
            configureWorkflowProjectFile("""
                    name: Completion
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: %s
                          - uses: %s/acme/tool@<caret>
                    """.formatted(usesValue, server.webUrl()));
            GitHubActionCache.getActionCache().get(getProject(), usesValue).resolve();

            assertThat(completeBasicLookupStrings()).contains("main", "v2");
        }
    }

    public void testStepUsesCompletionDoesNotSuggestReusableWorkflows() {
        myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Reusable
                on: workflow_call
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: <caret>
                """);

        assertThat(completeBasicLookupStrings())
                .contains("./.github/actions/local")
                .doesNotContain("./.github/workflows/reusable.yml");
    }

    public void testJobUsesCompletionSuggestsLocalReusableWorkflowFiles() {
        myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);
        myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Reusable
                on: workflow_call
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  call:
                    uses: <caret>
                """);

        assertThat(completeBasicLookupStrings())
                .contains("./.github/workflows/reusable.yml")
                .doesNotContain("./.github/actions/local");
    }

    public void testRootActionUsesCompletionSuggestsRepositoryAction() {
        myFixture.addFileToProject("action.yml", """
                name: Root Action
                runs:
                  using: composite
                  steps:
                    - run: echo ok
                      shell: sh
                """);

        configureWorkflowProjectFile("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: <caret>
                """);

        assertThat(completeBasicLookupStrings()).contains("./");
    }

    public void testRunCompletionSuggestsGithubEnvironmentFiles() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "x=y" >> "$<caret>"
                """)).contains("GITHUB_ENV", "GITHUB_OUTPUT");
    }

    public void testRunCompletionSuggestsDefaultEnvironmentVariables() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "$<caret>"
                """)).contains("GITHUB_TRIGGERING_ACTOR", "GITHUB_REPOSITORY_OWNER_ID", "RUNNER_ENVIRONMENT");
    }

    public void testShellCompletionSuggestsSupportedGithubShells() {
        assertThat(completeWorkflow("""
                name: Completion
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - shell: <caret>
                        run: echo ok
                """)).contains("bash", "sh", "pwsh", "powershell", "cmd", "python");
    }
}
