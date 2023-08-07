package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Optional.ofNullable;

public class YamlElementHelper {

    private YamlElementHelper() {
    }

    public static PsiElement getYamlRoot(final PsiElement element) {
        return element != null && element.getParent() != null && !(element instanceof YAMLDocument) ? getYamlRoot(element.getParent()) : element;
    }

    public static String getPath(final PsiElement element) {
        return ofNullable(getVirtualFile(element)).map(VirtualFile::getPath).orElse(null);
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
        return yamlOf(null, getYamlRoot(element));
    }

    public static YamlElement yamlOf(final YamlElement parent, final PsiElement element) {
        if (element instanceof YAMLFile || element instanceof YAMLDocument) {
            return element.getFirstChild() == null ? null : yamlOf(null, element.getFirstChild());
        } else if (parent == null) {
            return createRootElement(element);
        } else if (element instanceof YAMLMapping) {
            return addChildren(parent, element.getChildren());
        } else if (element instanceof final YAMLKeyValue yamlKeyValue) {
            return createElement(parent, yamlKeyValue);
        } else if (element instanceof final YAMLSequenceItem yamlSequenceItem) {
            return addChildren(parent, yamlSequenceItem);
        } else if (element instanceof final YAMLSequence yamlSequence) {
            return addChildren(parent, yamlSequence);
        } else if (element instanceof YAMLQuotedText || element instanceof YAMLPlainTextImpl) {
            return createElement(parent, (YAMLScalar) element);
        } else if (element instanceof final YAMLBlockScalarImpl yamlBlockScalarImpl) {
            return addChildren(parent, yamlBlockScalarImpl);
        } else if (element instanceof YAMLCompoundValue || element instanceof PsiErrorElementImpl) {
            //IGNORE
            return parent;
        } else {
            throw new RuntimeException("Not Implemented Element " + element.getClass().getSimpleName());
        }
    }

    private static YamlElement addChildren(final YamlElement parent, final YAMLSequence element) {
        Arrays.stream(element.getChildren()).map(child -> yamlOf(parent, child)).forEach(addToParent(parent));
        return parent;
    }

    private static YamlElement addChildren(final YamlElement parent, final YAMLSequenceItem element) {
        final YamlElement listItem = new YamlElement(
                element.getTextRange().getStartOffset(),
                element.getTextRange().getEndOffset(),
                null,
                null,
                element,
                parent,
                new ArrayList<>()
        );
        Arrays.stream(element.getChildren()).map(child -> yamlOf(listItem, child)).forEach(addToParent(listItem));
        addToParent(parent).accept(listItem);
        return parent;
    }

    private static YamlElement createRootElement(final PsiElement element) {
        return addChildren(new YamlElement(
                -1,
                -1,
                null,
                element.getText(),
                element,
                null,
                new ArrayList<>()
        ), element.getChildren());
    }

    private static YamlElement createElement(final YamlElement parent, final YAMLKeyValue element) {
        return addChildren(new YamlElement(
                element.getTextOffset(),
                element.getTextOffset() + element.getKeyText().length(),
                element.getKeyText(),
                null,
                element,
                parent,
                new ArrayList<>()
        ), element.getChildren());
    }

    //ATTENTION: "YAMLScalar" is nearly everything. Use with care
    private static YamlElement createElement(final YamlElement parent, final YAMLScalar element) {
        return addChildren(new YamlElement(
                element.getTextRange().getStartOffset(),
                element.getTextRange().getEndOffset(),
                null,
                element.getText(),
                element,
                parent,
                new ArrayList<>()
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

    private static YamlElement addChildren(final YamlElement parent, final YAMLBlockScalarImpl element) {
        if (parent != null) {
            element.getContentRanges().stream().map(textRange -> new YamlElement(
                    element.getTextRange().getStartOffset() + textRange.getStartOffset(),
                    element.getTextRange().getStartOffset() + textRange.getEndOffset(),
                    null,
                    element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset()),
                    element,
                    parent,
                    null
            )).forEach(addToParent(parent));
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

    private static Consumer<YamlElement> addToParent(final YamlElement parent) {
        return child -> ofNullable(parent)
                .filter(p -> child != null)
                .filter(p -> child != p)
                .filter(p -> !p.children().contains(child))
                .ifPresent(p -> p.children().add(child));
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
