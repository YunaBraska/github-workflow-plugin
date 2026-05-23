package com.github.yunabraska.githubworkflow.helper;

import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.AutoPopupInsertHandler.addSuffix;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_IF;

public class GitHubWorkflowHelper {
    private static final String COMPLETION_DUMMY = "IntellijIdeaRulezzz";

    private GitHubWorkflowHelper() {
        // static helper
    }

    public static Optional<String[]> getCaretBracketItem(final PsiElement position, final int offset, final String[] prefix) {
        final PsiElement context = completionContextElement(position, offset);
        final String rawText = context.getText();
        if (rawText == null) {
            return Optional.empty();
        }
        final int rawCursorRel = offset - context.getTextRange().getStartOffset();
        final int dummyIndex = rawText.indexOf(COMPLETION_DUMMY);
        final String wholeText = rawText.replace(COMPLETION_DUMMY, "");
        final int adjustedCursorRel = dummyIndex >= 0 && dummyIndex < rawCursorRel
                ? rawCursorRel - COMPLETION_DUMMY.length()
                : rawCursorRel;
        final int cursorRel = Math.max(0, Math.min(adjustedCursorRel, wholeText.length()));
        final String offsetText = wholeText.substring(0, cursorRel);
        final int bracketStart = offsetText.lastIndexOf("${{");
        if (cursorRel > 2 && isInBrackets(offsetText, bracketStart) || PsiElementHelper.getParent(context, FIELD_IF).isPresent()) {
            return getCaretBracketItem(prefix, wholeText, cursorRel);
        }
        return Optional.empty();
    }

    private static PsiElement completionContextElement(final PsiElement position, final int offset) {
        PsiElement current = position;
        PsiElement fallback = position;
        while (current != null && current.getParent() != current) {
            final boolean containsOffset = current.getTextRange() != null
                    && current.getTextRange().getStartOffset() <= offset
                    && offset <= current.getTextRange().getEndOffset();
            if (containsOffset && current.getText() != null && current.getText().contains(COMPLETION_DUMMY)) {
                fallback = current;
                if (PsiElementHelper.isTextElement(current) || current instanceof YAMLScalar) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return fallback;
    }

    public static Optional<String[]> getCaretBracketItem(final String[] prefix, final String wholeText, final int cursorRel) {
        final Optional<String[]> pathItems = getPathCompletionItems(prefix, wholeText, cursorRel);
        if (pathItems.isPresent()) {
            return pathItems;
        }
        final char previousChar = cursorRel == 0 ? ' ' : wholeText.charAt(cursorRel - 1);
        if (cursorRel > 1 && previousChar == '.') {
            //NEXT ELEMENT
            final int indexStart = getStartIndex(wholeText, cursorRel - 1);
            final int indexEnd = getEndIndex(wholeText, cursorRel - 1, wholeText.length());
            return Optional.of(wholeText.substring(indexStart, indexEnd + 1).split("\\."));
        } else if (isNonValidNodeChar(previousChar)) {
            //START ELEMENT
            return Optional.of(prefix);
        } else {
            //MIDDLE ELEMENT
            final int indexStart = cursorRel == 0 ? 0 : getStartIndex(wholeText, cursorRel - 1);
            final String[] prefArray = wholeText.substring(indexStart, cursorRel).split("\\.", -1);
            prefix[0] = prefArray[prefArray.length - 1];
            return Optional.of(wholeText.substring(indexStart, cursorRel - prefix[0].length()).split("\\."));
        }
    }

    private static Optional<String[]> getPathCompletionItems(final String[] prefix, final String wholeText, final int cursorRel) {
        final int pathStart = getPathStartIndex(wholeText, cursorRel);
        if (pathStart >= cursorRel) {
            return Optional.empty();
        }
        final String path = wholeText.substring(pathStart, cursorRel);
        if (!path.contains(".") && !path.contains("[")) {
            return Optional.empty();
        }
        final CaretPath caretPath = parseCaretPath(path);
        if (caretPath.items().length == 0) {
            return Optional.empty();
        }
        prefix[0] = caretPath.prefix();
        return Optional.of(caretPath.items());
    }

    private static int getPathStartIndex(final String text, final int cursorRel) {
        int result = Math.min(cursorRel, text.length());
        while (result > 0 && isPathChar(text.charAt(result - 1))) {
            result--;
        }
        return result;
    }

    private static CaretPath parseCaretPath(final String text) {
        final List<String> items = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inBracket = false;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            final char character = text.charAt(index);
            if (inBracket) {
                if (quote != 0 && character == quote) {
                    quote = 0;
                } else if (quote == 0 && (character == '\'' || character == '"')) {
                    quote = character;
                } else if (quote == 0 && character == ']') {
                    addPathItem(items, current);
                    inBracket = false;
                } else {
                    current.append(character);
                }
            } else if (character == '.') {
                addPathItem(items, current);
            } else if (character == '[') {
                addPathItem(items, current);
                inBracket = true;
            } else if (character != '\'' && character != '"') {
                current.append(character);
            }
        }
        return new CaretPath(items.toArray(String[]::new), current.toString());
    }

    private static void addPathItem(final List<String> items, final StringBuilder current) {
        if (!current.isEmpty()) {
            items.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isPathChar(final char character) {
        return Character.isLetterOrDigit(character)
                || character == '_'
                || character == '-'
                || character == '.'
                || character == '['
                || character == ']'
                || character == '\''
                || character == '"';
    }

    private record CaretPath(String[] items, String prefix) {
    }

    public static int getStartIndex(final CharSequence currentText, final int fromIndex) {
        int result = fromIndex;
        while (result > 0) {
            final char c = currentText.charAt(result);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') {
                result = result != fromIndex ? result + 1 : result;
                break;
            }
            result--;
        }
        return Math.min(result, fromIndex);
    }

    private static boolean isNonValidNodeChar(final char c) {
        return !Character.isLetterOrDigit(c) && c != '_' && c != '-';
    }

    private static int getEndIndex(final CharSequence currentText, final int fromIndex, final int toIndex) {
        int result = fromIndex;
        final int endIndex = currentText.length();
        while (result < endIndex && result < toIndex) {
            if (isNonValidNodeChar(currentText.charAt(result))) {
                break;
            }
            result++;
        }
        return result;
    }

    private static boolean isInBrackets(final String partString, final int bracketStart) {
        return bracketStart != -1 && partString.lastIndexOf("}}") <= bracketStart;
    }

    public static LookupElement toLookupElement(final NodeIcon icon, final char suffix, final String key, final String text) {
        final LookupElementBuilder result = LookupElementBuilder
                .create(key)
                .withIcon(icon.icon())
                .withBoldness(icon != NodeIcon.ICON_ENV)
                .withTypeText(text)
                .withCaseSensitivity(false)
                .withInsertHandler((ctx, item) -> addSuffix(ctx, item, suffix));
        return PrioritizedLookupElement.withPriority(result, icon.ordinal() + 5d);
    }

    public static Optional<Path> getWorkflowFile(final PsiElement psiElement) {
        return Optional.ofNullable(psiElement)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getOriginalFile)
                .map(PsiFile::getViewProvider)
                .map(FileViewProvider::getVirtualFile)
                .flatMap(PsiElementHelper::toPath)
                .filter(GitHubWorkflowHelper::isWorkflowPath);
    }

    public static boolean isWorkflowPath(final Path path) {
        return path != null && (isActionFile(path) || isWorkflowFile(path) || ApplicationManager.getApplication().isUnitTestMode());
    }

    public static boolean isYamlFile(final Path path) {
        return path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yml") || path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yaml");
    }

    public static boolean isActionFile(final Path path) {
        return path.getNameCount() > 1
                && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("action.yml")
                || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("action.yaml"));
    }

    public static boolean isWorkflowFile(final Path path) {
        return path != null && path.getNameCount() > 2
                && isYamlFile(path)
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflows")
                && (path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                || path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".gitea"));
    }

    public static boolean isWorkflowTemplatePropertiesFile(final Path path) {
        return path.getNameCount() > 2
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflow-templates")
                && isYamlFile(path);
    }

    public static boolean isIssueForms(final Path path) {
        return path.getNameCount() > 2
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("ISSUE_TEMPLATE")
                && isYamlFile(path);
    }

    public static boolean isIssueConfigFile(final Path path) {
        return path.getNameCount() > 2
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("ISSUE_TEMPLATE")
                && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("config.yml")
                || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("config.yaml"));
    }

    public static boolean isFoundingFile(final Path path) {
        return path.getNameCount() > 1
                && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("FUNDING.yml")
                || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("FUNDING.yaml"));
    }

    public static boolean isDependabotFile(final Path path) {
        return path.getNameCount() > 1
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase(".github")
                && (path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("dependabot.yml")
                || path.getName(path.getNameCount() - 1).toString().equalsIgnoreCase("dependabot.yaml"));
    }

    public static boolean isDiscussionFile(final Path path) {
        return path.getNameCount() > 2
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("DISCUSSION_TEMPLATE")
                && isYamlFile(path);
    }

}
