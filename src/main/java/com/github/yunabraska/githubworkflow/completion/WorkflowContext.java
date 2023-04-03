package com.github.yunabraska.githubworkflow.completion;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.lastIndexOf;

public class WorkflowContext {

    //TODO: if:
    public static Optional<String[]> getCaretBracketItem(final String text, final int caretOffset) {
        final String partString = text.substring(0, caretOffset);
        final int bracketStart = partString.lastIndexOf("${{");
        if (bracketStart != -1 && partString.lastIndexOf("}}") <= bracketStart) {
            return Optional.of(partString.substring(lastIndexOf(partString, " ", "{", "|", "&") + 1).split("\\."));
        }
        return Optional.empty();
    }


}
