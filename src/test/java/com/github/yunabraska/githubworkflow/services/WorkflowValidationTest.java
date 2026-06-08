package com.github.yunabraska.githubworkflow.services;

import java.util.Map;

public class WorkflowValidationTest extends EditorFeatureTestCase {

    public void testUnknownTopLevelWorkflowKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                <weak_warning descr="Unknown workflow key [jobz]">jobz</weak_warning>:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownWorkflowEventKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  <weak_warning descr="Unknown workflow event [pull_requestz]">pull_requestz</weak_warning>:
                    branches: [main]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownTriggerFilterKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  push:
                    <weak_warning descr="Unknown trigger filter [branchz]">branchz</weak_warning>: [main]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownTriggerActivityTypeIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_run:
                    types: <weak_warning descr="Unknown trigger value [teleported]">teleported</weak_warning>
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testScheduleRejectsBranchFilters() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  schedule:
                    - cron: '0 0 * * *'
                    - <weak_warning descr="Unknown trigger filter [branches]">branches</weak_warning>: [main]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownJobKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    <weak_warning descr="Unknown job key [stepz]">stepz</weak_warning>:
                      - run: echo ok
                """);
    }

    public void testUnknownStepKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - <weak_warning descr="Unknown step key [shellz]">shellz</weak_warning>: sh
                        run: echo ok
                """);
    }

    public void testUnknownConcurrencyKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                concurrency:
                  group: release
                  <weak_warning descr="Unknown workflow key [cancelled-by-committee]">cancelled-by-committee</weak_warning>: true
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownEnvironmentKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                jobs:
                  deploy:
                    runs-on: ubuntu-latest
                    environment:
                      name: production
                      <weak_warning descr="Unknown workflow key [portal]">portal</weak_warning>: https://example.com
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownContainerCredentialKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container:
                      image: ghcr.io/example/app:latest
                      credentials:
                        username: octo
                        <weak_warning descr="Unknown workflow key [token]">token</weak_warning>: hush
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownServiceCredentialKeyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      database:
                        image: postgres:16
                        credentials:
                          username: octo
                          <weak_warning descr="Unknown workflow key [token]">token</weak_warning>: hush
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownPermissionScopeIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                permissions:
                  <weak_warning descr="Unknown permission [contentz]">contentz</weak_warning>: read
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownWorkflowDispatchInputPropertyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_dispatch:
                    inputs:
                      target:
                        type: choice
                        <weak_warning descr="Unknown trigger key [wizardry]">wizardry</weak_warning>: quiet
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownWorkflowDispatchInputTypeIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_dispatch:
                    inputs:
                      target:
                        type: <weak_warning descr="Unknown trigger value [potato]">potato</weak_warning>
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testWorkflowCallInputRejectsDispatchOnlyTypes() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_call:
                    inputs:
                      target:
                        type: <weak_warning descr="Unknown trigger value [choice]">choice</weak_warning>
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testWorkflowInputRequiredRejectsNonBooleanValue() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_dispatch:
                    inputs:
                      target:
                        required: <weak_warning descr="Unknown trigger value [maybe]">maybe</weak_warning>
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownWorkflowCallOutputPropertyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_call:
                    outputs:
                      image:
                        value: ${{ jobs.build.outputs.image }}
                        <weak_warning descr="Unknown trigger key [artifact]">artifact</weak_warning>: docker
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      image: ${{ steps.meta.outputs.image }}
                    steps:
                      - id: meta
                        run: echo "image=demo" >> "$GITHUB_OUTPUT"
                """);
    }

    public void testUnknownWorkflowCallSecretPropertyIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on:
                  workflow_call:
                    secrets:
                      token:
                        required: true
                        <weak_warning descr="Unknown trigger key [vault]">vault</weak_warning>: no
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnknownPermissionValueIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                permissions:
                  contents: <weak_warning descr="Unknown permission value [writer]">writer</weak_warning>
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testRestrictedPermissionValueIsHighlighted() {
        assertWorkflowHighlights("""
                name: Syntax
                on: workflow_dispatch
                permissions:
                  id-token: <weak_warning descr="Unknown permission value [read]">read</weak_warning>
                  models: <weak_warning descr="Unknown permission value [write]">write</weak_warning>
                  vulnerability-alerts: <weak_warning descr="Unknown permission value [write]">write</weak_warning>
                  artifact-metadata: write
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testResolvedActionInputIsAccepted() {
        seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());

        assertWorkflowHighlights("""
                name: Action Input
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          known-input: ok
                """);
    }

    public void testUnknownActionInputIsHighlighted() {
        seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());

        assertWorkflowHighlights("""
                name: Action Input
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          <error descr="Delete invalid input [wrong-input]">wrong-input: no</error>
                """);
    }

    public void testUnresolvedRemoteActionMessageMentionsCommonFailureModes() {
        assertWorkflowHighlights("""
                name: Remote Action
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - <weak_warning descr="Unresolved [missing/tool@v1] - check GitHub account access, private repository permissions, rate limits, missing refs, or missing action/workflow metadata">uses: missing/tool@v1</weak_warning>
                """);
    }

    public void testReusableWorkflowInputIsAccepted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of("config-path", "Config path"), Map.of());

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    with:
                      config-path: .github/labeler.yml
                """);
    }

    public void testUnknownReusableWorkflowInputIsHighlighted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of("config-path", "Config path"), Map.of());

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    with:
                      <error descr="Delete invalid input [wrong-input]">wrong-input: value</error>
                """);
    }

    public void testReusableWorkflowSecretIsAccepted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of(), Map.of("access-token", "Access token"));

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    secrets:
                      access-token: ${{ secrets.PERSONAL_ACCESS_TOKEN }}
                """);
    }

    public void testUnknownReusableWorkflowSecretIsHighlighted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of(), Map.of("access-token", "Access token"));

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    secrets:
                      <error descr="Delete invalid secret [wrong-token]">wrong-token: ${{ secrets.PERSONAL_ACCESS_TOKEN }}</error>
                """);
    }

    public void testReusableWorkflowInheritSecretsIsAccepted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of(), Map.of("access-token", "Access token"));

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    secrets: inherit
                """);
    }

    public void testLocalReusableWorkflowSecretIsAccepted() {
        seedLocalAction("./.github/workflows/reusable.yml", myFixture.addFileToProject(".github/workflows/reusable.yml", """
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

        configureWorkflowProjectFile("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/reusable.yml
                    secrets:
                      access-token: ${{ secrets.PERSONAL_ACCESS_TOKEN }}
                """);
        myFixture.checkHighlighting(true, false, true);
    }

    public void testReusableWorkflowOutputReferenceIsAccepted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of("artifact", "Artifact path"));

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                  consume:
                    needs: call
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.call.outputs.artifact }}"
                """);
    }

    public void testLocalReusableWorkflowOutputReferenceIsAccepted() {
        seedLocalAction("./.github/workflows/reusable.yml", myFixture.addFileToProject(".github/workflows/reusable.yml", """
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

        configureWorkflowProjectFile("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: ./.github/workflows/reusable.yml
                  consume:
                    needs: call
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.call.outputs.artifact }}"
                """);
        myFixture.checkHighlighting(true, false, true);
    }

    public void testUnknownReusableWorkflowOutputIsHighlighted() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of("artifact", "Artifact path"));

        assertWorkflowHighlights("""
                name: Reusable Workflow
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                  consume:
                    needs: call
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ needs.call.outputs.<error descr="Replace with [artifact]">missing</error> }}"
                """);
    }

    public void testWorkflowCallInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Inputs
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.known-input }}"
                """);
    }

    public void testWorkflowDispatchInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Inputs
                on:
                  workflow_dispatch:
                    inputs:
                      deploy-target:
                        type: choice
                        options:
                          - staging
                          - prod
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.deploy-target }}"
                """);
    }

    public void testUnknownInputReferenceIsHighlighted() {
        assertWorkflowHighlights("""
                name: Inputs
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.<error descr="Replace with [known-input]">missing</error> }}"
                """);
    }

    public void testRunNameInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Inputs
                run-name: Deploy ${{ inputs.environment }}
                on:
                  workflow_dispatch:
                    inputs:
                      environment:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testRunNameUnknownInputReferenceIsHighlighted() {
        assertWorkflowHighlights("""
                name: Inputs
                run-name: Deploy ${{ inputs.<error descr="Replace with [environment]">missing</error> }}
                on:
                  workflow_dispatch:
                    inputs:
                      environment:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testIncompleteInputExpressionIsHighlighted() {
        assertWorkflowHighlights("""
                name: Inputs
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ <error descr="Incomplete statement [inputs]">inputs</error>. }}"
                """);
    }

    public void testFoldedScalarUnknownInputReferenceIsHighlighted() {
        assertWorkflowHighlights("""
                name: Inputs
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: >
                          echo "${{ inputs.<error descr="Replace with [known-input]">missing</error> }}"
                """);
    }

    public void testMultilineExpressionInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Inputs
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: |
                          echo "${{
                            inputs.known-input
                          }}"
                """);
    }

    public void testWorkflowCallSecretReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Secrets
                on:
                  workflow_call:
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.SECRET_TOKEN }}"
                """);
    }

    public void testAutomaticGithubTokenSecretReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Secrets
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.GITHUB_TOKEN }}"
                """);
    }

    public void testUnknownWorkflowCallSecretIsWeakWarning() {
        assertWorkflowHighlights("""
                name: Secrets
                on:
                  workflow_call:
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ secrets.<weak_warning descr="Replace [MISSING_SECRET] with [SECRET_TOKEN] - if it is not provided at runtime">MISSING_SECRET</weak_warning> }}"
                """);
    }

    public void testSecretInIfConditionIsError() {
        assertWorkflowHighlights("""
                name: Secrets
                on:
                  workflow_call:
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - if: <error descr="Remove [secrets.SECRET_TOKEN] - Secrets are not valid in `if` statements">secrets.SECRET_TOKEN</error> != ''
                        run: echo ok
                """);
    }

    public void testWorkflowEnvReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                env:
                  TOP_LEVEL: top
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ env.TOP_LEVEL }}"
                """);
    }

    public void testJobEnvReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    env:
                      JOB_LEVEL: job
                    steps:
                      - run: echo "${{ env.JOB_LEVEL }}"
                """);
    }

    public void testStepEnvReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - env:
                          STEP_LEVEL: step
                        run: echo "${{ env.STEP_LEVEL }}"
                """);
    }

    public void testAliasBackedEnvReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                env:
                  SHARED: &shared shared-value
                  COPIED: *shared
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ env.COPIED }}"
                """);
    }

    public void testJobEnvMapAliasReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Env
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
                      - run: echo "${{ env.NODE_ENV }}"
                """);
    }

    public void testRunEnvFromBashFileCommandIsAcceptedInLaterStep() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "ACTION_STATE=yellow" >> "$GITHUB_ENV"
                      - run: echo "${{ env.ACTION_STATE }}"
                """);
    }

    public void testRunEnvFromPowerShellFileCommandIsAcceptedInLaterStep() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: windows-latest
                    steps:
                      - shell: pwsh
                        run: |
                          "ACTION_STATE=yellow" >> $env:GITHUB_ENV
                      - run: echo "${{ env.ACTION_STATE }}"
                """);
    }

    public void testRunEnvFromMultilineFileCommandIsAcceptedInLaterStep() {
        assertWorkflowHighlights("""
                name: Env
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: |
                          {
                            echo 'JSON_RESPONSE<<EOF'
                            echo '{}'
                            echo EOF
                          } >> "$GITHUB_ENV"
                      - run: echo "${{ env.JSON_RESPONSE }}"
                """);
    }

    public void testGithubContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Github Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.ref_name }}"
                """);
    }

    public void testGithubActorIdContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Github Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.actor_id }}"
                """);
    }

    public void testGithubRefProtectedContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Github Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.ref_protected }}"
                """);
    }

    public void testGithubRepositoryOwnerIdContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Github Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.repository_owner_id }}"
                """);
    }

    public void testRunnerDebugContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Runner Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.debug }}"
                """);
    }

    public void testGiteaContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Gitea Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ gitea.ref_name }}"
                """);
    }

    public void testGithubEventNestedContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Github Context
                on: pull_request
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.event.pull_request.number }}"
                """);
    }

    public void testUnknownGithubContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Github Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github.<error descr="Replace with [action]">missing</error> }}"
                """);
    }

    public void testLiteralGithubUrlInsideRunBlockIsNotTreatedAsExpression() {
        assertWorkflowHighlights("""
                name: Github Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: |
                          echo "https://github.com/YunaBraska/github-workflow-plugin"
                          printf -- '- https://github.com/%s/compare/%s...%s\\n' "$REPOSITORY" "$OLD_TAG" "$TAG_NAME"
                """);
    }

    public void testJobContainerNetworkContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Job Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container: postgres:16
                    steps:
                      - run: echo "${{ job.container.network }}"
                """);
    }

    public void testJobServicePortContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Job Context
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
                      - run: echo "${{ job.services.postgres.ports[5432] }}"
                """);
    }

    public void testUnknownJobContainerMemberIsHighlighted() {
        assertWorkflowHighlights("""
                name: Job Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container: postgres:16
                    steps:
                      - run: echo "${{ job.container<error descr="Remove invalid [bad]">.bad</error> }}"
                """);
    }

    public void testUnknownJobServiceIdIsHighlighted() {
        assertWorkflowHighlights("""
                name: Job Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.<error descr="Replace with [postgres]">redis</error>.network }}"
                """);
    }

    public void testUnknownJobServiceMemberIsHighlighted() {
        assertWorkflowHighlights("""
                name: Job Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.postgres<error descr="Remove invalid [bad]">.bad</error> }}"
                """);
    }

    public void testUnknownJobServicePortIsHighlighted() {
        assertWorkflowHighlights("""
                name: Job Context
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
                      - run: echo "${{ job.services.postgres.ports[<error descr="Replace with [5432]">6379</error>] }}"
                """);
    }

    public void testRunnerContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Runner Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.os }}"
                """);
    }

    public void testRunnerEnvironmentContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Runner Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.environment }}"
                """);
    }

    public void testUnknownRunnerContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Runner Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.<error descr="Replace with [name]">missing</error> }}"
                """);
    }

    public void testRunnerExpressionWithInvalidSuffixIsHighlighted() {
        assertWorkflowHighlights("""
                name: Runner Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.os<error descr="Remove invalid suffix [extra]">.extra</error> }}"
                """);
    }

    public void testStepOutputFromBashFileCommandIsAccepted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.artifact }}"
                """);
    }

    public void testHyphenatedStepOutputFromBashFileCommandIsAccepted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact-path=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.artifact-path }}"
                """);
    }

    public void testStepOutputFromPowerShellFileCommandIsAccepted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: windows-latest
                    steps:
                      - id: package
                        shell: pwsh
                        run: |
                          "artifact=dist" >> $env:GITHUB_OUTPUT
                      - run: echo "${{ steps.package.outputs.artifact }}"
                """);
    }

    public void testStepOutputFromMultilineFileCommandIsAccepted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: |
                          {
                            echo 'artifact-json<<EOF'
                            echo '{}'
                            echo EOF
                          } >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.artifact-json }}"
                """);
    }

    public void testIssue79MultilineStepOutputFromGroupedEchoIsAccepted() {
        seedRemoteAction("azure/login@v2", Map.of("creds", "Azure credentials"), Map.of());

        assertWorkflowHighlights("""
                name: Issue 79
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: creds
                        run: |
                          CREDS=$(cat <<EOF
                          {
                            "clientId" : "$CLIENT_ID",
                            "tenantId" : "$TENANT_ID"
                          }
                          EOF
                          )
                          {
                            echo "CREDS<<EOF"
                            echo "$CREDS"
                            echo "EOF"
                          } >> "$GITHUB_OUTPUT"
                      - uses: azure/login@v2
                        with:
                          creds: ${{ steps.creds.outputs.CREDS }}
                """);
    }

    public void testIssue73StepOutputFromTeePipeIsAccepted() {
        assertWorkflowHighlights("""
                name: Issue 73
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: version
                        run: echo "dev_version=$DEV_VERSION" | tee -a $GITHUB_OUTPUT $GITHUB_STEP_SUMMARY
                      - run: echo "${{ steps.version.outputs.dev_version }}"
                """);
    }

    public void testStepOutputFromActionMetadataIsAccepted() {
        seedRemoteAction("owner/tool@v1", Map.of(), Map.of("artifact", "Artifact path"));

        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        uses: owner/tool@v1
                      - run: echo "${{ steps.package.outputs.artifact }}"
                """);
    }

    public void testIssue76CompositeActionCanReferencePreviousActionOutputs() {
        seedRemoteAction(
                "actions/cache@v4",
                Map.of("path", "Cache paths", "key", "Cache key"),
                Map.of("cache-hit", "Whether the cache was restored")
        );

        assertWorkflowHighlights("""
                name: Build application
                description: Build application
                inputs:
                  working_dir:
                    description: Working directory
                    required: false
                    default: ./
                runs:
                  using: composite
                  steps:
                    - uses: actions/cache@v4
                      id: cache-gradle
                      with:
                        path: ${{ inputs.working_dir }}/.gradle
                        key: gradle-${{ runner.os }}
                    - if: steps.cache-gradle.outputs.cache-hit != 'true'
                      run: echo build
                      shell: sh
                """);
    }

    public void testCompositeActionInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Composite Action
                inputs:
                  local-input:
                    description: Local input
                runs:
                  using: composite
                  steps:
                    - run: echo "${{ inputs.local-input }}"
                      shell: sh
                """);
    }

    public void testCompositeActionOutputStepReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Composite Action
                outputs:
                  artifact:
                    value: ${{ steps.package.outputs.artifact }}
                runs:
                  using: composite
                  steps:
                    - id: package
                      run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      shell: sh
                """);
    }

    public void testCompositeActionUnknownOutputStepReferenceIsHighlighted() {
        assertWorkflowHighlights("""
                name: Composite Action
                outputs:
                  artifact:
                    value: ${{ steps.<error descr="Replace with [package]">missing</error>.outputs.artifact }}
                runs:
                  using: composite
                  steps:
                    - id: package
                      run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      shell: sh
                """);
    }

    public void testUnknownStepIdIsHighlighted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.<error descr="Replace with [package]">missing</error>.outputs.artifact }}"
                """);
    }

    public void testUnknownStepOutputIsHighlighted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package.outputs.<error descr="Replace with [artifact]">missing</error> }}"
                """);
    }

    public void testInvalidStepMemberIsHighlighted() {
        assertWorkflowHighlights("""
                name: Step Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps.package<error descr="Remove invalid [bad]">.bad</error>.artifact }}"
                """);
    }

    public void testStepOutcomeIsAccepted() {
        assertWorkflowHighlights("""
                name: Step State
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo ok
                      - run: echo "${{ steps.package.outcome }}"
                """);
    }

    public void testStepConclusionIsAccepted() {
        assertWorkflowHighlights("""
                name: Step State
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo ok
                      - run: echo "${{ steps.package.conclusion }}"
                """);
    }

    public void testNeedsScalarAcceptsPreviousJob() {
        assertWorkflowHighlights("""
                name: Needs
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
                      - run: echo ok
                """);
    }

    public void testNeedsArrayHighlightsUnknownJob() {
        assertWorkflowHighlights("""
                name: Needs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  test:
                    needs: [ build, <error descr="Remove invalid jobId [missing_job] - this jobId doesn't match any previous job">missing_job</error> ]
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testNeedsOutputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Needs
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
                      - run: echo "${{ needs.build.outputs.artifact }}"
                """);
    }

    public void testNeedsResultReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Needs
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
                      - run: echo "${{ needs.build.result }}"
                """);
    }

    public void testIssue77NeedsResultInsideFunctionConditionIsAccepted() {
        assertWorkflowHighlights("""
                name: Issue 77
                on: push
                jobs:
                  ci-passed:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                  release:
                    needs: ci-passed
                    if: always() && startsWith(github.ref, 'refs/tags/') && needs.ci-passed.result == 'success'
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo release
                """);
    }

    public void testUnknownNeedsOutputIsHighlighted() {
        assertWorkflowHighlights("""
                name: Needs
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
                      - run: echo "${{ needs.build.outputs.artifact }}"
                      - run: echo "${{ needs.build.outputs.<error descr="Replace with [artifact]">missing</error> }}"
                """);
    }

    public void testInvalidNeedsMemberIsHighlighted() {
        assertWorkflowHighlights("""
                name: Needs
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
                      - run: echo "${{ needs.build<error descr="Remove invalid [bad]">.bad</error> }}"
                """);
    }

    public void testJobsWorkflowOutputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Reusable Outputs
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
                """);
    }

    public void testJobsResultReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Reusable Outputs
                on:
                  workflow_call:
                    outputs:
                      build-result:
                        value: ${{ jobs.build.result }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testInvalidJobsMemberIsHighlighted() {
        assertWorkflowHighlights("""
                name: Reusable Outputs
                on:
                  workflow_call:
                    outputs:
                      artifact:
                        value: ${{ jobs.build<error descr="Remove invalid [bad]">.bad</error> }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testUnusedJobOutputIsWeakWarning() {
        assertWorkflowHighlights("""
                name: Job Outputs
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      <weak_warning descr="Unused [artifact]">artifact: ${{ steps.package.outputs.artifact }}</weak_warning>
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """);
    }

    public void testUsedJobOutputIsAccepted() {
        assertWorkflowHighlights("""
                name: Job Outputs
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
                      - run: echo "${{ needs.build.outputs.artifact }}"
                """);
    }

    public void testIssue46JobOutputUsedInsideFromJsonIsAccepted() {
        assertWorkflowHighlights("""
                name: Issue 46
                on: workflow_dispatch
                jobs:
                  list-folders:
                    runs-on: ubuntu-latest
                    outputs:
                      folders: ${{ steps.list.outputs.folders }}
                    steps:
                      - id: list
                        run: echo "folders=[]" >> "$GITHUB_OUTPUT"
                  update-all:
                    runs-on: ubuntu-latest
                    needs: list-folders
                    strategy:
                      matrix:
                        folder: ${{ fromJson(needs.list-folders.outputs.folders) }}
                    steps:
                      - run: echo "${{ matrix.folder }}"
                """);
    }

    public void testJobStatusContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Job Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ job.status }}"
                """);
    }

    public void testStrategyContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Strategy Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                    steps:
                      - run: echo "${{ strategy.job-index }}"
                """);
    }

    public void testMatrixContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Matrix Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                    steps:
                      - run: echo "${{ matrix.os }}"
                """);
    }

    public void testRunsOnMatrixContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Matrix Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                    steps:
                      - run: echo ok
                """);
    }

    public void testRunsOnSequenceMatrixContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Matrix Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: [self-hosted, "${{ matrix.os }}"]
                    strategy:
                      matrix:
                        os: [linux]
                    steps:
                      - run: echo ok
                """);
    }

    public void testRunsOnUnknownMatrixContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Matrix Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.<error descr="Replace with [os]">missing</error> }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                    steps:
                      - run: echo ok
                """);
    }

    public void testMatrixIncludeContextReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Matrix Context
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
                      - run: echo "${{ matrix.node }}"
                """);
    }

    public void testTopLevelConcurrencyInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Concurrency
                on:
                  workflow_dispatch:
                    inputs:
                      environment:
                        type: string
                concurrency: deploy-${{ inputs.environment }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobConcurrencyGroupInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Concurrency
                on:
                  workflow_dispatch:
                    inputs:
                      environment:
                        type: string
                jobs:
                  build:
                    concurrency:
                      group: deploy-${{ inputs.environment }}
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testEnvironmentUrlStepOutputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Environment
                on: workflow_dispatch
                jobs:
                  deploy:
                    runs-on: ubuntu-latest
                    environment:
                      name: production
                      url: ${{ steps.deploy.outputs.url }}
                    steps:
                      - id: deploy
                        run: echo "url=https://example.com" >> "$GITHUB_OUTPUT"
                """);
    }

    public void testStepContinueOnErrorMatrixReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Step Controls
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    strategy:
                      matrix:
                        experimental: [true]
                    steps:
                      - continue-on-error: ${{ matrix.experimental }}
                        run: echo ok
                """);
    }

    public void testStepTimeoutEnvReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Step Controls
                on: workflow_dispatch
                env:
                  TIMEOUT: 10
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - timeout-minutes: ${{ env.TIMEOUT }}
                        run: echo ok
                """);
    }

    public void testUnknownStrategyContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Strategy Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - run: echo "${{ strategy.<error descr="Replace with [fail-fast]">missing</error> }}"
                """);
    }

    public void testUnknownJobContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Job Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ job.<error descr="Replace with [status]">missing</error> }}"
                """);
    }

    public void testUnknownMatrixContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Matrix Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ${{ matrix.os }}
                    strategy:
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                    steps:
                      - run: echo "${{ matrix.<error descr="Replace with [os]">missing</error> }}"
                """);
    }

    public void testBracketNotationInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Brackets
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs['known-input'] }}"
                """);
    }

    public void testDoubleQuotedBracketNotationInputReferenceIsAccepted() {
        assertWorkflowHighlights("""
                name: Brackets
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs[\\"known-input\\"] }}"
                """);
    }

    public void testBracketNotationUnknownInputIsHighlighted() {
        assertWorkflowHighlights("""
                name: Brackets
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs['<error descr="Replace with [known-input]">missing</error>'] }}"
                """);
    }

    public void testBracketNotationGithubContextIsAccepted() {
        assertWorkflowHighlights("""
                name: Brackets
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github['ref_name'] }}"
                """);
    }

    public void testBracketNotationUnknownGithubContextIsHighlighted() {
        assertWorkflowHighlights("""
                name: Brackets
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ github['<error descr="Replace with [action]">missing</error>'] }}"
                """);
    }

    public void testBracketNotationStepOutputIsAccepted() {
        assertWorkflowHighlights("""
                name: Brackets
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps['package'].outputs['artifact'] }}"
                """);
    }

    public void testBracketNotationUnknownStepIdIsHighlighted() {
        assertWorkflowHighlights("""
                name: Brackets
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps['<error descr="Replace with [package]">missing</error>'].outputs['artifact'] }}"
                """);
    }

    public void testBracketNotationUnknownStepOutputIsHighlighted() {
        assertWorkflowHighlights("""
                name: Brackets
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                      - run: echo "${{ steps['package'].outputs['<error descr="Replace with [artifact]">missing</error>'] }}"
                """);
    }

    public void testBracketNotationNeedsResultIsAccepted() {
        assertWorkflowHighlights("""
                name: Brackets
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
                      - run: echo "${{ needs['build'].result }}"
                """);
    }

    public void testVarsContextIsAcceptedWithoutLocalValidation() {
        assertWorkflowHighlights("""
                name: Vars Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ vars.DEPLOY_TARGET }}"
                """);
    }

    public void testBracketVarsContextIsAcceptedWithoutLocalValidation() {
        assertWorkflowHighlights("""
                name: Vars Context
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ vars['DEPLOY_TARGET'] }}"
                """);
    }

    public void testWorkflowCallInputDefaultExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Workflow Call Defaults
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                      target:
                        type: string
                        default: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobContainerScalarExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Container Scalar
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobContainerCredentialsExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Container Credentials
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container:
                      image: ghcr.io/owner/image
                      credentials:
                        username: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobEnvironmentScalarExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Environment Scalar
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    environment: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobStrategyFailFastExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Strategy Fail Fast
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    strategy:
                      fail-fast: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobStrategyMaxParallelExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Strategy Max Parallel
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    strategy:
                      max-parallel: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                      matrix:
                        os: [ubuntu-latest]
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobDefaultsRunShellExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Defaults Run Shell
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    defaults:
                      run:
                        shell: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                    steps:
                      - run: echo ok
                """);
    }

    public void testJobServiceCredentialsExpressionIsValidated() {
        assertWorkflowHighlights("""
                name: Service Credentials
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      registry:
                        image: ghcr.io/owner/image
                        credentials:
                          username: ${{ inputs.<error descr="Replace with [known-input]">missing</error> }}
                    steps:
                      - run: echo ok
                """);
    }
}
