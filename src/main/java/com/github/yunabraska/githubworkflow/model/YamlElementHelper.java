package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.model.WorkflowContext.WORKFLOW_CONTEXT_MAP;
import static com.github.yunabraska.githubworkflow.model.YamlElement.createYamlElement;
import static java.util.Optional.ofNullable;

public class YamlElementHelper {

    private YamlElementHelper() {
    }

    public static PsiElement getYamlRoot(final PsiElement element) {
        if (element == null || element instanceof YAMLDocument || element instanceof YAMLFile || element.getParent() == null) {
            return element;
        } else {
            return getYamlRoot(element.getParent());
        }
    }

    @SuppressWarnings("java:S2637")
    public static String getPath(final PsiElement element) {
        return ofNullable(element).map(YamlElementHelper::getVirtualFile).map(VirtualFile::getPath).orElse(null);
    }

    public static VirtualFile getVirtualFile(final PsiElement element) {
        return ofNullable(getPsiFile(element)).map(yamlFile -> Optional.of(yamlFile.getOriginalFile()).map(PsiFile::getVirtualFile).orElseGet(yamlFile::getVirtualFile)).orElse(null);
    }

    public static YAMLFile getPsiFile(final PsiElement element) {
        if (element == null) {
            return null;
        } else if (element instanceof final YAMLFile yamlFile) {
            return yamlFile;
        } else {
            return getPsiFile(element.getParent());
        }
    }

    public static YamlElement yamlOf(final PsiElement element) {
        final PsiElement psiRoot = getYamlRoot(element);
        final YamlElement elementRoot = ofNullable(psiRoot).map(root -> yamlOf(createRootElement(element), root)).orElse(null);
        ofNullable(elementRoot)
                .map(YamlElement::initContext)
                .map(yamlElement -> psiRoot)
                .map(YamlElementHelper::getPsiFile)
                .map(yamlFile -> Optional.of(yamlFile.getOriginalFile()).map(PsiFile::getVirtualFile).orElseGet(yamlFile::getVirtualFile))
                .map(VirtualFile::getPath)
                .ifPresent(patString -> WORKFLOW_CONTEXT_MAP.put(patString, elementRoot.context()));
        return elementRoot;
    }

    public static YamlElement yamlOf(final YamlElement parent, final PsiElement psiElement) {
        if (psiElement == null) {
            return null;
        }

        //INVOKE ONLY ONE: getTextRange invoke only once as it can be slow in deep trees
        final Optional<TextRange> range = ofNullable(psiElement.getTextRange());

        //PREPARE RESULT
        final YamlElement result = createYamlElement(
                range.map(TextRange::getStartOffset).orElse(-1),
                range.map(TextRange::getEndOffset).orElse(-1),
                (psiElement instanceof final YAMLKeyValue keyValue ? keyValue.getKeyText() : null),
                hasChildren(psiElement) ? null : psiElement.getText()
        );

        //GET ALL CHILDREN
        final List<YamlElement> children = Stream.concat(getChildren(psiElement, result).stream(), result.children().stream())
                .distinct()
                .filter(Objects::nonNull)
                .filter(child -> !result.equals(child))
                .toList();

        //AVOID WRAPPER ELEMENTS - exclude YAMLSequenceItem as it represents list items "- name: something"
        if (result.key() == null && result.text() == null && !(psiElement instanceof YAMLSequenceItem)) {
            if (children.size() == 1) {
                return children.get(0).parent(parent);
            } else {
                return parent.children(children);
            }
        }
        return result.parent(parent).children(children);
    }

    @NotNull
    private static List<YamlElement> getChildren(final PsiElement psiElement, final YamlElement result) {
        return Optional.of(psiElement)
                .filter(YAMLBlockScalarImpl.class::isInstance)
                .map(YAMLBlockScalarImpl.class::cast)
                .map(YamlElementHelper::createChildren).orElseGet(() -> Arrays.stream(psiElement.getChildren()).map(psi -> yamlOf(result, psi)).toList());
    }

    @NotNull
    private static List<YamlElement> createChildren(final YAMLBlockScalarImpl psi) {
        return psi.getContentRanges().stream().map(textRange -> createYamlElement(
                psi.getTextRange().getStartOffset() + textRange.getStartOffset(),
                psi.getTextRange().getStartOffset() + textRange.getEndOffset(),
                null,
                psi.getText().substring(textRange.getStartOffset(), textRange.getEndOffset())
        )).toList();
    }

    public static boolean hasChildren(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .filter(YAMLBlockScalarImpl.class::isInstance)
                .map(YAMLBlockScalarImpl.class::cast)
                .map(YAMLBlockScalarImpl::getContentRanges)
                .map(List::size)
                .or(() -> ofNullable(psiElement).map(PsiElement::getChildren).map(children -> children.length))
                .map(count -> count > 0).orElse(false);
    }


    private static YamlElement createRootElement(final PsiElement element) {
        return addChildren(new YamlElement(
                -1,
                -1,
                null,
                element.getText(),
                true
        ), element.getChildren());
    }

    private static YamlElement addChildren(final YamlElement parent, final PsiElement[] children) {
        if (children != null && parent != null) {
            parent.children().addAll(Arrays.stream(children)
                    .filter(child -> child.getLanguage().isKindOf(YAMLLanguage.INSTANCE))
                    .map(child -> yamlOf(parent, child))
                    .filter(Objects::nonNull)
                    .filter(e -> !e.equals(parent))
                    .toList());
        }
        return parent;
    }


    public static List<YamlElement> filterNodesRecursive(final YamlElement currentNode, final Predicate<YamlElement> filter, final List<YamlElement> resultNodes) {
        if (filter.test(currentNode)) {
            resultNodes.add(currentNode);
        }
        for (final YamlElement child : currentNode.children) {
            filterNodesRecursive(child, filter, resultNodes);
        }
        return resultNodes;
    }

    public static boolean hasText(final String str) {
        return (str != null && !str.isEmpty() && containsText(str));
    }

    private static boolean containsText(final CharSequence str) {
        final int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static String removeQuotes(final String result) {
        return removeBrackets(result, '"', '\'');
    }

    public static String removeBrackets(final String text, final char... chars) {
        if (text != null && text.length() > 1) {
            for (final char c : chars) {
                if (text.charAt(0) == c && text.charAt(text.length() - 1) == (c == '[' ? ']' : validateRoundBracket(c))) {
                    return text.substring(1, text.length() - 1);
                }
            }
        }
        return text;
    }

    private static char validateRoundBracket(final char c) {
        return c == '(' ? ')' : c;
    }
}
