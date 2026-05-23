package com.github.yunabraska.githubworkflow.helper;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public class AutoPopupInsertHandler<T extends LookupElement> implements InsertHandler<T> {
    public static final AutoPopupInsertHandler<LookupElement> INSTANCE = new AutoPopupInsertHandler<>();

    @Override
    public void handleInsert(@NotNull final InsertionContext context, @NotNull final T item) {
        AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }

    public static void addSuffix(final InsertionContext ctx, final LookupElement item, final char suffix) {
        if (suffix != Character.MIN_VALUE) {
            final String key = item.getLookupString();
            final int startOffset = ctx.getStartOffset();
            final Document document = ctx.getDocument();
            final CharSequence documentChars = document.getCharsSequence();
            final int tailOffset = ctx.getTailOffset();
            final String toInsert = toInsertString(suffix, documentChars, tailOffset);

            document.replaceString(startOffset, getEndIndex(ctx, suffix, documentChars, tailOffset), key + toInsert);
            ctx.getEditor().getCaretModel().moveToOffset(startOffset + (key + toInsert).length());

            if (suffix == '.') {
                AutoPopupInsertHandler.INSTANCE.handleInsert(ctx, item);
            }
        }
    }

    private static int getEndIndex(final InsertionContext ctx, final char suffix, final CharSequence documentChars, final int tailOffset) {
        int result = tailOffset;
        if (ctx.getCompletionChar() == '\t') {
            while (result < documentChars.length()
                    && documentChars.charAt(result) != suffix
                    && !isLineBreak(documentChars.charAt(result))
            ) {
                result++;
            }
        }
        return result;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isLineBreak(final char c) {
        return c == '\n' || c == '\r';
    }

    @NotNull
    private static String toInsertString(final char suffix, final CharSequence documentChars, final int tailOffset) {
        final StringBuilder sb = new StringBuilder();
        sb.append(suffix);
        final boolean isNextChatSpace = tailOffset < documentChars.length() && documentChars.charAt(tailOffset + 1) == ' ';
        if (suffix != '.' && !isNextChatSpace) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
