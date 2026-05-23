package com.github.yunabraska.githubworkflow.services;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Iconable;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowQuickFixTest extends EditorFeatureTestCase {

    public void testUnknownActionInputProvidesDeleteQuickFix() {
        seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());

        assertThat(quickFixTexts("""
                name: Quick Fixes
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          wrong-input: no
                """)).contains("Delete invalid input [wrong-input]");
    }

    public void testInspectionQuickFixTextsUseConfiguredPluginLanguage() {
        final PluginSettings settings = PluginSettings.getInstance();
        final String previousLanguage = settings.languageTag();
        try {
            settings.languageTag("de");
            seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());

            assertThat(quickFixTexts("""
                    name: Quick Fixes
                    on: workflow_dispatch
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: owner/tool@v1
                            with:
                              wrong-input: no
                    """))
                    .anyMatch(text -> text.contains("Neu laden [owner/tool]"))
                    .anyMatch(text -> text.contains("Warnungen umschalten [aus] für [owner/tool]"))
                    .anyMatch(text -> text.contains("Ungültig: Eingabe [wrong-input] löschen"));
        } finally {
            settings.languageTag(previousLanguage);
        }
    }

    public void testPluginQuickFixesExposeIcons() {
        seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());
        configureWorkflow("""
                name: Quick Fixes
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          wrong-input: no
                """);
        myFixture.doHighlighting();

        assertQuickFixHasIcon("Reload [owner/tool]");
        assertQuickFixHasIcon("Toggle warnings [off] for [owner/tool]");
        assertQuickFixHasIcon("Toggle warnings [off] for [wrong-input]");
        assertQuickFixHasIcon("Delete invalid input [wrong-input]");
    }

    public void testReferenceQuickFixesExposeIcons() {
        configureWorkflow("""
                name: Quick Fixes
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - if: secrets.SECRET_TOKEN != ''
                        run: echo "${{ inputs.missing }} ${{ runner.os.extra }}"
                """);
        myFixture.doHighlighting();

        assertQuickFixHasIcon("Replace with [known-input]");
        assertQuickFixHasIcon("Remove [secrets.SECRET_TOKEN] - Secrets are not valid in `if` statements");
        assertQuickFixHasIcon("Remove invalid suffix [extra]");
    }

    public void testUnknownWorkflowInputProvidesReplaceQuickFix() {
        assertThat(quickFixTexts("""
                name: Quick Fixes
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.missing }}"
                """)).contains("Replace with [known-input]");
    }

    public void testPlainGithubUrlInsideRunBlockDoesNotOfferContextReplacement() {
        assertThat(quickFixTexts("""
                name: Quick Fixes
                on: workflow_dispatch
                jobs:
                  release:
                    runs-on: ubuntu-latest
                    steps:
                      - run: |
                          notes_file="$(mktemp)"
                          printf -- '- https://github.com/%s/compare/%s...%s\\n' "$REPOSITORY" "$previous_tag" "$TAG_NAME"
                          echo "ref [${{ github.ref }}]" >> "$notes_file"
                """))
                .noneMatch(text -> text.contains("Replace with [action]"))
                .noneMatch(text -> text.contains("github.com"));
    }

    public void testSecretInIfProvidesDeleteQuickFix() {
        assertThat(quickFixTexts("""
                name: Quick Fixes
                on:
                  workflow_call:
                    secrets:
                      SECRET_TOKEN:
                        required: false
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - if: secrets.SECRET_TOKEN != ''
                        run: echo ok
                """)).contains("Remove [secrets.SECRET_TOKEN] - Secrets are not valid in `if` statements");
    }

    public void testUnusedJobOutputProvidesDeleteQuickFix() {
        assertThat(quickFixTexts("""
                name: Quick Fixes
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    outputs:
                      artifact: ${{ steps.package.outputs.artifact }}
                    steps:
                      - id: package
                        run: echo "artifact=dist" >> "$GITHUB_OUTPUT"
                """)).contains("Unused [artifact]");
    }

    public void testReplaceQuickFixUpdatesWorkflowInputReference() {
        final String fixedText = applyQuickFix("""
                name: Quick Fixes
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ inputs.<caret>missing }}"
                """, "Replace with [known-input]");

        assertThat(fixedText).contains("inputs.known-input");
        assertThat(fixedText).doesNotContain("inputs.missing");
    }

    public void testDeleteQuickFixRemovesInvalidActionInput() {
        seedRemoteAction("owner/tool@v1", Map.of("known-input", "Known input"), Map.of());

        final String fixedText = applyQuickFix("""
                name: Quick Fixes
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: owner/tool@v1
                        with:
                          <caret>wrong-input: no
                """, "Delete invalid input [wrong-input]");

        assertThat(fixedText).doesNotContain("wrong-input");
    }

    public void testDeleteQuickFixRemovesInvalidReusableWorkflowSecret() {
        seedRemoteAction("owner/repo/.github/workflows/build.yml@v1", Map.of(), Map.of(), Map.of("access-token", "Access token"));

        final String fixedText = applyQuickFix("""
                name: Quick Fixes
                on: workflow_dispatch
                jobs:
                  call:
                    uses: owner/repo/.github/workflows/build.yml@v1
                    secrets:
                      <caret>wrong-token: ${{ secrets.PERSONAL_ACCESS_TOKEN }}
                """, "Delete invalid secret [wrong-token]");

        assertThat(fixedText).doesNotContain("wrong-token");
    }

    public void testInvalidSuffixQuickFixRemovesTrailingMember() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "${{ runner.os.extra }}"
                """, "Remove invalid suffix [extra]");

        assertThat(fixedText).contains("${{ runner.os }}");
        assertThat(fixedText).doesNotContain("runner.os.");
    }

    public void testInvalidNeedsMemberQuickFixRemovesMember() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
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
                      - run: echo "${{ needs.build.bad }}"
                """, "Remove invalid [bad]");

        assertThat(fixedText).contains("${{ needs.build }}");
        assertThat(fixedText).doesNotContain("needs.build.");
    }

    private void assertQuickFixHasIcon(final String text) {
        final IntentionAction action = allIntentions().stream()
                .filter(quickFix -> text.equals(quickFix.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing quick fix [" + text + "]"));
        assertThat(action)
                .as("Quick fix [%s] exposes an IDE icon", text)
                .isInstanceOf(Iconable.class);
        assertThat(((Iconable) action).getIcon(0))
                .as("Quick fix [%s] icon", text)
                .isNotNull();
    }

    public void testReplaceQuickFixUpdatesWorkflowCallInputDefaultReference() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                      target:
                        type: string
                        default: ${{ inputs.missing }}
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """, "Replace with [known-input]");

        assertThat(fixedText).contains("default: ${{ inputs.known-input }}");
        assertThat(fixedText).doesNotContain("inputs.missing");
    }

    public void testReplaceQuickFixUpdatesJobContainerScalarReference() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
                on:
                  workflow_call:
                    inputs:
                      known-input:
                        type: string
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container: ${{ inputs.missing }}
                    steps:
                      - run: echo ok
                """, "Replace with [known-input]");

        assertThat(fixedText).contains("container: ${{ inputs.known-input }}");
        assertThat(fixedText).doesNotContain("inputs.missing");
    }

    public void testInvalidJobContainerMemberQuickFixRemovesMember() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    container: postgres:16
                    steps:
                      - run: echo "${{ job.container.bad }}"
                """, "Remove invalid [bad]");

        assertThat(fixedText).contains("${{ job.container }}");
        assertThat(fixedText).doesNotContain("job.container.");
    }

    public void testInvalidJobServiceMemberQuickFixRemovesMember() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.postgres.bad }}"
                """, "Remove invalid [bad]");

        assertThat(fixedText).contains("${{ job.services.postgres }}");
        assertThat(fixedText).doesNotContain("job.services.postgres.");
    }

    public void testReplaceQuickFixUpdatesJobServiceIdReference() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:16
                    steps:
                      - run: echo "${{ job.services.database.network }}"
                """, "Replace with [postgres]");

        assertThat(fixedText).contains("job.services.postgres.network");
        assertThat(fixedText).doesNotContain("job.services.database.network");
    }

    public void testReplaceQuickFixUpdatesJobServicePortReference() {
        final String fixedText = applyQuickFix("""
                name: QuickFix
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
                      - run: echo "${{ job.services.postgres.ports[1234] }}"
                """, "Replace with [5432]");

        assertThat(fixedText).contains("job.services.postgres.ports[5432]");
        assertThat(fixedText).doesNotContain("ports[1234]");
    }

    public void testOutdatedMajorActionProvidesUpdateQuickFixForIssue47() {
        seedRemoteAction("actions/checkout@v3", Map.of(), Map.of()).remoteRefs(List.of("v4", "v3", "main"));

        assertThat(quickFixTexts("""
                name: QuickFix
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v3
                """)).contains("Update action [actions/checkout@v3] to [actions/checkout@v4]");
    }

    public void testUpdateActionQuickFixRewritesUsesRefForIssue47() {
        seedRemoteAction("actions/checkout@v3", Map.of(), Map.of()).remoteRefs(List.of("v4", "v3"));

        final String fixedText = applyQuickFix("""
                name: QuickFix
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v3
                """, "Update action [actions/checkout@v3] to [actions/checkout@v4]");

        assertThat(fixedText).contains("uses: actions/checkout@v4");
        assertThat(fixedText).doesNotContain("actions/checkout@v3");
    }
}
