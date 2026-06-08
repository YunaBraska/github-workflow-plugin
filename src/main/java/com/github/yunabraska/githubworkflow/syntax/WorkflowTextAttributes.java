package com.github.yunabraska.githubworkflow.syntax;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public class WorkflowTextAttributes {

    public static final TextAttributesKey VARIABLE_REFERENCE = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_VARIABLE_REFERENCE",
            DefaultLanguageHighlighterColors.CONSTANT
    );

    public static final TextAttributesKey DECLARATION = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_DECLARATION",
            DefaultLanguageHighlighterColors.STATIC_FIELD
    );

    public static final TextAttributesKey RUNNER_VARIABLE = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_RUNNER_VARIABLE",
            DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
    );

    public static final TextAttributesKey SCALAR_LITERAL = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_SCALAR_LITERAL",
            DefaultLanguageHighlighterColors.NUMBER
    );

    private WorkflowTextAttributes() {
        // constants
    }
}
