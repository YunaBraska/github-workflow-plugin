package com.github.yunabraska.githubworkflow.services;

import com.intellij.psi.PsiFile;

import java.util.Map;

public class WorkflowShowcaseTest extends EditorFeatureTestCase {

    public void testLargeShowcaseWorkflowHighlightsWithoutErrors() {
        final PsiFile localAction = myFixture.addFileToProject(".github/actions/local/action.yml", """
                name: Local Action
                inputs:
                  target:
                    description: Target
                outputs:
                  local-artifact:
                    description: Local artifact
                runs:
                  using: composite
                  steps:
                    - id: package
                      run: echo "local-artifact=dist/local" >> "$GITHUB_OUTPUT"
                      shell: sh
                """);
        final PsiFile reusableWorkflow = myFixture.addFileToProject(".github/workflows/reusable.yml", """
                name: Local Reusable
                on:
                  workflow_call:
                    inputs:
                      config:
                        type: string
                    secrets:
                      access-token:
                        required: true
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
                        run: echo "artifact=dist/reusable" >> "$GITHUB_OUTPUT"
                """);
        seedLocalAction("./.github/actions/local", localAction);
        seedLocalAction("./.github/workflows/reusable.yml", reusableWorkflow);
        seedRemoteAction(
                "actions/checkout@v4",
                Map.of("fetch-depth", "Number of commits to fetch"),
                Map.of()
        );
        seedRemoteAction(
                "owner/tool@v1",
                Map.of("target", "Target", "path", "Path"),
                Map.of("artifact", "Artifact", "status", "Status")
        );
        seedRemoteAction(
                "owner/repo/.github/workflows/deploy.yml@v1",
                Map.of("config", "Config"),
                Map.of("remote-artifact", "Remote artifact"),
                Map.of("access-token", "Access token")
        );

        configureWorkflowProjectFile(showcaseWorkflow());
        myFixture.checkHighlighting(true, false, true);
    }

    private static String showcaseWorkflow() {
        return """
                name: Showcase
                on:
                  push:
                    branches: [main]
                    tags: ["v*"]
                  workflow_dispatch:
                    inputs:
                      deploy-target:
                        type: choice
                        options: [staging, production]
                        default: staging
                  workflow_call:
                    inputs:
                      deploy-target:
                        type: string
                        default: staging
                    secrets:
                      access-token:
                        required: true
                permissions:
                  contents: read
                  actions: read
                concurrency:
                  group: showcase-${{ github.ref_name }}
                  cancel-in-progress: true
                env: &workflow_env
                  TOP_LEVEL: production
                  CACHE_SCOPE: ${{ github.ref_name }}
                jobs:
                  build:
                    name: Build ${{ matrix.os }} / ${{ matrix.node }}
                    runs-on: ${{ matrix.os }}
                    env:
                      <<: *workflow_env
                      JOB_LEVEL: build
                    strategy:
                      fail-fast: false
                      matrix:
                        os: [ubuntu-latest, windows-latest]
                        node: [20, 22]
                        include:
                          - os: ubuntu-latest
                            node: 24
                            experimental: true
                    services:
                      postgres:
                        image: postgres:16
                        ports:
                          - 5432:5432
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                      tee-version: ${{ steps.version.outputs.dev_version }}
                      remote-artifact: ${{ steps.remote.outputs.artifact }}
                    steps:
                      - uses: actions/checkout@v4
                      - id: package
                        run: |
                          {
                            echo "artifact<<EOF"
                            echo "dist/${{ matrix.os }}"
                            echo "EOF"
                          } >> "$GITHUB_OUTPUT"
                      - id: version
                        run: echo "dev_version=$GITHUB_REF_NAME" | tee -a $GITHUB_OUTPUT $GITHUB_STEP_SUMMARY
                      - id: remote
                        uses: owner/tool@v1
                        with:
                          target: ${{ inputs.deploy-target }}
                          path: ${{ steps.package.outputs.artifact }}
                      - uses: ./.github/actions/local
                        with:
                          target: ${{ env.TOP_LEVEL }}
                      - run: echo "${{ job.services.postgres.ports[5432] }} ${{ runner.environment }} ${{ strategy.fail-fast }}"
                  local-call:
                    needs: build
                    uses: ./.github/workflows/reusable.yml
                    with:
                      config: ${{ needs.build.outputs.artifact }}
                    secrets:
                      access-token: ${{ secrets.access-token }}
                  remote-call:
                    needs: build
                    uses: owner/repo/.github/workflows/deploy.yml@v1
                    with:
                      config: ${{ needs.build.outputs.tee-version }}
                    secrets:
                      access-token: ${{ secrets.access-token }}
                  publish:
                    needs: [build, local-call, remote-call]
                    if: always() && startsWith(github.ref, 'refs/tags/') && needs.build.result == 'success'
                    runs-on: ubuntu-latest
                    steps:
                      - run: |
                          echo "${{ needs.build.outputs.artifact }}"
                          echo "${{ needs.build.outputs.remote-artifact }}"
                          echo "${{ needs.local-call.outputs.artifact }}"
                          echo "${{ needs.remote-call.outputs.remote-artifact }}"
                          echo "${{ vars.DEPLOY_TARGET }} ${{ gitea.ref_name }}"
                """;
    }
}
