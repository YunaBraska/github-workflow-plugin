package com.github.yunabraska.githubworkflow.services;

import com.intellij.codeInsight.intention.IntentionAction;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.yaml.YAMLFileType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;

abstract class EditorFeatureTestCase extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getActionCache().getState().actions.clear();
        RemoteActionProviders.Settings.getInstance().setCustomServers(List.of());
        ((CodeInsightTestFixtureImpl) myFixture).canChangeDocumentDuringHighlighting(true);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            getActionCache().getState().actions.clear();
            RemoteActionProviders.Settings.getInstance().setCustomServers(List.of());
        } finally {
            super.tearDown();
        }
    }

    protected static GitHubAction seedRemoteAction(final String usesValue, final Map<String, String> inputs, final Map<String, String> outputs) {
        return seedRemoteAction(usesValue, inputs, outputs, Map.of());
    }

    protected static GitHubAction seedRemoteAction(final String usesValue, final Map<String, String> inputs, final Map<String, String> outputs, final Map<String, String> secrets) {
        final GitHubAction action = GitHubAction.createGithubAction(false, usesValue, usesValue)
                .isResolved(true)
                .setInputs(inputs)
                .setOutputs(outputs)
                .setSecrets(secrets);
        getActionCache().getState().actions.put(usesValue, action);
        return action;
    }

    protected static GitHubAction seedLocalAction(final String usesValue, final PsiFile actionFile) {
        final GitHubAction action = GitHubAction.createGithubAction(true, usesValue, actionFile.getVirtualFile().getPath())
                .isResolved(true);
        getActionCache().getState().actions.put(usesValue, action);
        getActionCache().getState().actions.put(actionFile.getVirtualFile().getPath(), action);
        return action;
    }

    protected final void assertWorkflowHighlights(final String text) {
        configureWorkflow(text);
        myFixture.checkHighlighting(true, false, true);
    }

    protected final void configureWorkflow(final String text) {
        myFixture.configureByText(YAMLFileType.YML, text);
    }

    protected final void configureWorkflowProjectFile(final String text) {
        final int caretOffset = text.indexOf("<caret>");
        final String fileText = text.replace("<caret>", "");
        myFixture.addFileToProject(".github/workflows/workflow.yml", fileText);
        myFixture.configureFromTempProjectFile(".github/workflows/workflow.yml");
        if (caretOffset >= 0) {
            myFixture.getEditor().getCaretModel().moveToOffset(caretOffset);
        }
    }

    protected final PsiReference referenceAtCaret() {
        return myFixture.getReferenceAtCaretPositionWithAssertion();
    }

    protected final List<String> completeWorkflow(final String text) {
        configureWorkflow(text);
        return completeBasicLookupStrings();
    }

    protected final List<String> completeBasicLookupStrings() {
        final LookupElement[] elements = myFixture.completeBasic();
        return elements == null ? List.of() : Arrays.stream(elements)
                .map(LookupElement::getLookupString)
                .toList();
    }

    protected final void assertHighlightedReferenceAtCurrentCaret() {
        assertTextAttributeAtCurrentCaret(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE);
    }

    protected final void assertTextAttributeAtCurrentCaret(final TextAttributesKey key) {
        final int offset = myFixture.getCaretOffset();
        final List<HighlightInfo> highlights = myFixture.doHighlighting();
        final boolean found = highlights.stream()
                .anyMatch(info -> containsOffset(info, offset)
                        && key.equals(info.forcedTextAttributesKey));
        if (!found) {
            final String nearby = highlights.stream()
                    .filter(info -> info.forcedTextAttributesKey != null)
                    .filter(info -> Math.abs(info.startOffset - offset) < 80 || Math.abs(info.endOffset - offset) < 80)
                    .map(info -> info.forcedTextAttributesKey + "@" + info.startOffset + ".." + info.endOffset)
                    .distinct()
                    .toList()
                    .toString();
            throw new AssertionError("Missing text attribute [" + key + "] at caret offset [" + offset + "], nearby " + nearby);
        }
    }

    protected final void assertNoTextAttributeAtCurrentCaret(final TextAttributesKey key) {
        final int offset = myFixture.getCaretOffset();
        final List<HighlightInfo> highlights = myFixture.doHighlighting();
        final List<String> found = highlights.stream()
                .filter(info -> containsOffset(info, offset))
                .filter(info -> key.equals(info.forcedTextAttributesKey))
                .map(info -> info.forcedTextAttributesKey + "@" + info.startOffset + ".." + info.endOffset)
                .toList();
        if (!found.isEmpty()) {
            throw new AssertionError("Unexpected text attribute [" + key + "] at caret offset [" + offset + "], found " + found);
        }
    }

    protected final void clickGutterActionContaining(final String text) {
        final List<String> tooltips = myFixture.doHighlighting().stream()
                .map(HighlightInfo::getGutterIconRenderer)
                .filter(GutterIconRenderer.class::isInstance)
                .map(GutterIconRenderer.class::cast)
                .map(GutterIconRenderer::getTooltipText)
                .filter(tooltip -> tooltip != null)
                .toList();
        final GutterIconRenderer renderer = myFixture.doHighlighting().stream()
                .map(HighlightInfo::getGutterIconRenderer)
                .filter(GutterIconRenderer.class::isInstance)
                .map(GutterIconRenderer.class::cast)
                .filter(gutter -> gutter.getTooltipText() != null)
                .filter(gutter -> gutter.getTooltipText().contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing gutter action containing [" + text + "], found " + tooltips));
        final AnAction action = renderer.getClickAction();
        final AnAction resolvedAction = action == null ? popupActionContaining(renderer, text) : action;
        if (resolvedAction == null) {
            throw new AssertionError("Gutter action has no click action [" + text + "]");
        }
        resolvedAction.actionPerformed(AnActionEvent.createEvent(
                resolvedAction,
                DataContext.EMPTY_CONTEXT,
                new Presentation(),
                "GithubWorkflowPluginTest",
                ActionUiKind.NONE,
                null
        ));
    }

    protected final List<GutterIconRenderer> gutterIcons() {
        return myFixture.doHighlighting().stream()
                .map(HighlightInfo::getGutterIconRenderer)
                .filter(GutterIconRenderer.class::isInstance)
                .map(GutterIconRenderer.class::cast)
                .toList();
    }

    private static AnAction popupActionContaining(final GutterIconRenderer renderer, final String text) {
        final ActionGroup group = renderer.getPopupMenuActions();
        if (group == null) {
            return null;
        }
        return Arrays.stream(group.getChildren(null))
                .filter(action -> action.getTemplatePresentation().getText() != null)
                .filter(action -> action.getTemplatePresentation().getText().contains(text))
                .findFirst()
                .orElse(null);
    }

    protected final void invokeHighlightFixContaining(final String text) {
        final IntentionAction action = myFixture.doHighlighting().stream()
                .map(info -> info.findRegisteredQuickFix((descriptor, range) -> descriptor.getAction().getText().contains(text) ? descriptor.getAction() : null))
                .filter(fix -> fix != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing highlight fix containing [" + text + "]"));
        try {
            action.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
        } catch (final Exception exception) {
            throw new AssertionError("Highlight fix failed [" + text + "]", exception);
        }
    }

    protected final List<String> quickFixTexts(final String text) {
        configureWorkflow(text);
        myFixture.doHighlighting();
        return allIntentions().stream()
                .map(IntentionAction::getText)
                .distinct()
                .toList();
    }

    protected final String applyQuickFix(final String text, final String actionText) {
        myFixture.addFileToProject(".github/workflows/quickfix.yml", text);
        myFixture.configureFromTempProjectFile(".github/workflows/quickfix.yml");
        myFixture.doHighlighting();
        final IntentionAction action = allIntentions().stream()
                .filter(quickFix -> actionText.equals(quickFix.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing quick fix [" + actionText + "]"));
        try {
            action.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
        } catch (final Exception exception) {
            throw new AssertionError("Quick fix failed [" + actionText + "]", exception);
        }
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        return myFixture.getEditor().getDocument().getText();
    }

    protected final List<IntentionAction> allIntentions() {
        return Stream.concat(myFixture.getAllQuickFixes().stream(), myFixture.getAvailableIntentions().stream())
                .toList();
    }

    private static boolean containsOffset(final HighlightInfo info, final int offset) {
        return info.startOffset <= offset && offset < info.endOffset;
    }
}
