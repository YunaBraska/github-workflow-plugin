package com.github.yunabraska.githubworkflow.helper;

import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.AutoPopupInsertHandler.addSuffix;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_IF;
import static com.github.yunabraska.githubworkflow.model.schema.GitHubActionSchemaProvider.isActionYaml;
import static com.github.yunabraska.githubworkflow.model.schema.GitHubWorkflowSchemaProvider.isWorkflowYaml;

public class GitHubWorkflowHelper {

    private GitHubWorkflowHelper() {
        // static helper
    }

    public static Optional<String[]> getCaretBracketItem(final PsiElement position, final int offset, final String[] prefix) {
        final String wholeText = position.getText();
        if (wholeText == null) {
            return Optional.empty();
        }
        final int cursorRel = offset - position.getTextRange().getStartOffset();
        final String offsetText = wholeText.substring(0, cursorRel);
        final int bracketStart = offsetText.lastIndexOf("${{");
        if (cursorRel > 2 && isInBrackets(offsetText, bracketStart) || PsiElementHelper.getParent(position, FIELD_IF).isPresent()) {
            return getCaretBracketItem(prefix, wholeText, cursorRel);
        }
        return Optional.empty();
    }

    public static Optional<String[]> getCaretBracketItem(final String[] prefix, final String wholeText, final int cursorRel) {
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
                .map(VirtualFile::getPath)
                .map(Paths::get)
                .filter(GitHubWorkflowHelper::isWorkflowPath);
    }

    public static boolean isWorkflowPath(final Path path) {
        return path != null && (isActionYaml(path) || isWorkflowYaml(path));
    }

    public static boolean isYamlFile(final Path path) {
        return path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yml") || path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yaml");
    }
}
