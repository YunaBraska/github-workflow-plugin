package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalarText;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_ENV;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_ENV_MULTILINE;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT_MULTILINE;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT_TEE;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

public final class WorkflowPsi {

    private WorkflowPsi() {
        // static helper class
    }

    public static Optional<YAMLKeyValue> getParentJob(final PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_JOBS, YAMLKeyValue.class);
    }

    public static List<SimpleElement> parseEnvVariables(final LeafPsiElement element) {
        return element == null ? Collections.emptyList() : parseVariables(element, WorkflowPsi::toGithubEnvs);
    }

    public static List<SimpleElement> parseOutputVariables(final LeafPsiElement element) {
        return element == null ? Collections.emptyList() : parseVariables(element, WorkflowPsi::toGithubOutputs);
    }

    public static List<SimpleElement> parseEnvVariables(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, WorkflowPsi::toGithubEnvs);
    }

    public static List<SimpleElement> parseOutputVariables(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, WorkflowPsi::toGithubOutputs);
    }

    public static <T> Optional<T> getChild(final PsiElement psiElement, final Class<T> clazz) {
        return getFirstElement(getChildren(psiElement, clazz));
    }

    public static <T> Optional<T> getFirstElement(final List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public static List<YAMLKeyValue> getAllJobs(final PsiElement psiElement) {
        final List<YAMLKeyValue> result = new ArrayList<>();
        getAllJobs(result, ofNullable(psiElement).map(PsiElement::getContainingFile).orElse(null));
        return unmodifiableList(result);
    }

    public static void getAllJobs(final List<YAMLKeyValue> result, final PsiElement element) {
        if (result == null || element == null) {
            return;
        }
        if (element instanceof final YAMLKeyValue keyValue && FIELD_JOBS.equals(keyValue.getKeyText())) {
            result.addAll(getChildren(keyValue));
        } else {
            Arrays.stream(element.getChildren()).forEach(child -> getAllJobs(result, child));
        }
    }

    public static List<YAMLKeyValue> getChildren(final PsiElement psiElement) {
        return getChildren(psiElement, YAMLKeyValue.class);
    }

    public static Optional<String> getText(final PsiElement psiElement) {
        return getTextElements(psiElement).stream().map(PsiElement::getText).map(WorkflowPsi::removeQuotes).filter(WorkflowPsi::hasText).findFirst();
    }

    public static Optional<String> getText(final PsiElement psiElement, final String key) {
        return getChild(psiElement, key).flatMap(WorkflowPsi::getText);
    }


    public static Optional<PsiElement> getTextElement(final PsiElement psiElement) {
        final List<PsiElement> textValues = getTextElements(psiElement);
        return textValues.isEmpty() ? Optional.empty() : Optional.of(textValues.get(0));
    }

    public static List<PsiElement> getTextElements(final PsiElement psiElement) {
        final ArrayList<PsiElement> result = new ArrayList<>();
        getTextElements(result, psiElement);
        return unmodifiableList(result);
    }

    public static void getTextElements(final List<PsiElement> result, final PsiElement psiElement) {
        ofNullable(psiElement).ifPresent(element -> {
            if (isTextElement(element)) {
                if (hasText(element.getText())) {
                    result.add(element);
                }
            } else {
                Arrays.stream(element.getChildren()).forEach(child -> getTextElements(result, child));
            }
        });
    }

    public static boolean isTextElement(final PsiElement element) {
        return element instanceof YAMLScalarText || element instanceof YAMLPlainTextImpl || element instanceof YAMLQuotedText;
    }

    public static List<YAMLKeyValue> getAllElements(final PsiElement psiElement, final String keyName) {
        return psiElement == null || keyName == null ? Collections.emptyList() : unmodifiableList(getAllElements(new ArrayList<>(), psiElement, keyName));
    }

    public static List<YAMLKeyValue> getAllElements(final List<YAMLKeyValue> result, final PsiElement psiElement, final String keyName) {
        if (psiElement instanceof final YAMLKeyValue keyValue && keyName.equals(keyValue.getKeyText())) {
            result.add(keyValue);
        }

        for (final PsiElement child : psiElement.getChildren()) {
            getAllElements(result, child, keyName);
        }

        return result;
    }

    public static Optional<PsiElement> getParentStepOrJob(final PsiElement psiElement) {
        return getParentStep(psiElement).map(PsiElement.class::cast).or(() -> getParentJob(psiElement));
    }

    public static Optional<YAMLSequenceItem> getParentStep(final PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_STEPS, YAMLSequenceItem.class);
    }

    public static List<YAMLSequenceItem> getChildSteps(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .map(element -> element instanceof final YAMLKeyValue keyValue && FIELD_STEPS.equals(keyValue.getKeyText()) ? List.of(keyValue) : getAllElements(element, FIELD_STEPS))
                .map(yamlKeyValues -> yamlKeyValues.stream().flatMap(steps -> getChildren(steps, YAMLSequenceItem.class).stream().filter(Objects::nonNull)).toList())
                .orElseGet(Collections::emptyList);
    }

    public static <T> List<T> getChildren(final PsiElement psiElement, final Class<T> clazz) {
        return ofNullable(psiElement)
                .map(PsiElement::getChildren)
                .map(psiElements -> Arrays.stream(psiElements).filter(clazz::isInstance).map(clazz::cast).toList())
                .filter(children -> !children.isEmpty())
                .or(() -> getAliasedChildren(psiElement, clazz))
                .or(() -> ofNullable(psiElement)
                        .map(PsiElement::getChildren)
                        .flatMap(psiElements -> Arrays.stream(psiElements).map(child -> getChildren(child, clazz)).filter(children -> !children.isEmpty()).findFirst())
                )
                .orElseGet(Collections::emptyList);
    }

    private static <T> Optional<List<T>> getAliasedChildren(final PsiElement psiElement, final Class<T> clazz) {
        return findDirectAlias(psiElement)
                .flatMap(alias -> findAnchor(alias.getContainingFile(), alias.getAliasName()))
                .map(YAMLAnchor::getMarkedValue)
                .map(value -> getChildren(value, clazz))
                .filter(children -> !children.isEmpty());
    }

    private static Optional<YAMLAlias> findDirectAlias(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .map(PsiElement::getChildren)
                .flatMap(children -> Arrays.stream(children)
                        .filter(YAMLAlias.class::isInstance)
                        .map(YAMLAlias.class::cast)
                        .findFirst());
    }

    private static Optional<YAMLAnchor> findAnchor(final PsiElement psiElement, final String name) {
        final String normalizedName = normalizeAnchorName(name);
        return ofNullable(psiElement)
                .map(element -> {
                    if (element instanceof final YAMLAnchor anchor && isAnchorName(anchor, normalizedName)) {
                        return Optional.of(anchor);
                    }
                    return Arrays.stream(element.getChildren())
                            .map(child -> findAnchor(child, normalizedName))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findFirst();
                })
                .orElseGet(Optional::empty);
    }

    private static boolean isAnchorName(final YAMLAnchor anchor, final String name) {
        return Objects.equals(name, normalizeAnchorName(anchor.getName()))
                || Objects.equals(name, normalizeAnchorName(anchor.getText()));
    }

    private static String normalizeAnchorName(final String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.startsWith("&") || name.startsWith("*") ? name.substring(1) : name;
    }

    public static Optional<YAMLKeyValue> getChild(final PsiElement psiElement, final String childKey) {
        return psiElement == null || childKey == null ? Optional.empty() : Optional.of(psiElement)
                .map(WorkflowPsi::getChildren)
                .flatMap(children -> children.stream()
                        .filter(Objects::nonNull)
                        .filter(child -> childKey.equals(child.getKeyText()))
                        .findFirst()
                        .or(() -> children.stream()
                                .filter(Objects::nonNull)
                                .map(child -> getChild(child, childKey))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .findFirst())
                );
    }

    public static <T extends PsiElement> Optional<T> getElementUnderParent(final PsiElement psiElement, final String keyName, final Class<T> clazz) {
        return psiElement == null || keyName == null ? Optional.empty() : getParent(psiElement, yamlKeyValue -> keyName.equals(yamlKeyValue.getKeyText()))
                .flatMap(yamlKeyValue -> getClosestChild(psiElement, yamlKeyValue, clazz));
    }

    public static Optional<YAMLKeyValue> getParent(final PsiElement psiElement, final String fieldKey) {
        return psiElement == null || fieldKey == null ? Optional.empty() : getParent(psiElement, parent -> fieldKey.equals(parent.getKeyText()));
    }

    public static Optional<YAMLKeyValue> getParent(final PsiElement psiElement, final Predicate<YAMLKeyValue> filter) {
        return psiElement == null || filter == null ? Optional.empty() : Optional.of(psiElement)
                .flatMap(WorkflowPsi::toYAMLKeyValue)
                .filter(filter)
                .or(() -> Optional.of(psiElement)
                        .map(PsiElement::getParent)
                        .flatMap(parent -> getParent(parent, filter))
                );
    }

    public static Optional<YAMLKeyValue> toYAMLKeyValue(final PsiElement psiElement) {
        if (psiElement instanceof final YAMLKeyValue element) {
            return Optional.of(element);
        }
        return Optional.empty();
    }

    public static String getDescription(final PsiElement psiElement, final boolean requiredField) {
        if (psiElement == null) {
            return "";
        }
        final List<String> details = new ArrayList<>();
        getText(psiElement, "description").or(() -> getText(psiElement, "desc"))
                .ifPresent(description -> details.add(GitHubWorkflowBundle.message("documentation.description", description)));
        getText(psiElement, "type")
                .ifPresent(type -> details.add(GitHubWorkflowBundle.message("documentation.type", type)));
        if (requiredField) {
            details.add(GitHubWorkflowBundle.message("documentation.required", getText(psiElement, "required").map(Boolean::parseBoolean).orElse(false)));
        }
        getText(psiElement, "default")
                .ifPresent(defaultValue -> details.add(GitHubWorkflowBundle.message("documentation.default", defaultValue)));
        getText(psiElement, "deprecationMessage")
                .ifPresent(message -> details.add(GitHubWorkflowBundle.message("documentation.deprecated", message)));
        return String.join("\n", details);
    }

    public static Optional<Path> toPath(final VirtualFile virtualFile) {
        return ofNullable(virtualFile).map(VirtualFile::getPath).flatMap(WorkflowPsi::toPath);
    }

    public static Optional<Path> toPath(final String path) {
        try {
            return ofNullable(path)
                    .map(String::trim)
                    .filter(WorkflowPsi::looksLikePathText)
                    .map(Paths::get)
                    .filter(p -> Files.exists(p) || ApplicationManager.getApplication().isUnitTestMode());
        } catch (final Exception ignored) {
            //e.g. java.nio.file.InvalidPathException: Illegal char <<> at index 0: <36ba1c43-b8f1-4f54-ace0-cef443d1e8f0>/etc/php/8.1/apache2/php.ini
            return Optional.empty();
        }
    }

    private static boolean looksLikePathText(final String path) {
        return hasText(path) && !path.startsWith("{") && !path.matches("^<[0-9a-fA-F-]{36}>.*");
    }

    public static Project getProject(final PsiElement psiElement) {
        return psiElement != null && psiElement.isValid() ? psiElement.getProject() : null;
    }

    public static String removeQuotes(final String result) {
        return removeBrackets(result, '"', '\'');
    }

    public static boolean hasText(final String str) {
        return (str != null && !str.isEmpty() && containsText(str));
    }

    public static String goToDeclarationString() {
        return GitHubWorkflowBundle.message("documentation.open.declaration", Arrays.stream(KeymapUtil.getActiveKeymapShortcuts("GotoDeclaration").getShortcuts())
                .limit(2)
                .map(KeymapUtil::getShortcutText)
                .collect(Collectors.joining(", "))
        );
    }

    private static Map<String, String> toGithubOutputs(final String text) {
        final Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_OUTPUT")) {
            putMatches(variables, PATTERN_GITHUB_OUTPUT.matcher(text), false);
            putMatches(variables, PATTERN_GITHUB_OUTPUT_TEE.matcher(text), false);
            putMatches(variables, PATTERN_GITHUB_OUTPUT_MULTILINE.matcher(text), true);
        }
        return variables;
    }

    private static Map<String, String> toGithubEnvs(final String text) {
        final Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_ENV")) {
            putMatches(variables, PATTERN_GITHUB_ENV.matcher(text), false);
            putMatches(variables, PATTERN_GITHUB_ENV_MULTILINE.matcher(text), true);
        }
        return variables;
    }

    private static void putMatches(final Map<String, String> variables, final Matcher matcher, final boolean multiline) {
        while (matcher.find()) {
            if (matcher.groupCount() >= 1) {
                variables.putIfAbsent(matcher.group(1), multiline ? "<multiline>" : matcher.group(2));
            }
        }
    }

    private static String removeBrackets(final String text, final char... chars) {
        if (text != null && text.length() > 1) {
            for (final char c : chars) {
                if (text.charAt(0) == c && text.charAt(text.length() - 1) == (c == '[' ? ']' : validateRoundBracket(c))) {
                    return text.substring(1, text.length() - 1);
                }
            }
        }
        return text;
    }

    private static <T extends PsiElement> Optional<T> getClosestChild(final PsiElement from, final YAMLKeyValue to, final Class<T> clazz) {
        return listAllParents(from, to).stream()
                .filter(Objects::nonNull)
                .filter(parent -> !(parent instanceof YAMLBlockSequenceImpl))
                .filter(clazz::isInstance)
                .findFirst()
                .map(clazz::cast);
    }

    private static List<PsiElement> listAllParents(final PsiElement from, final PsiElement to) {
        final List<PsiElement> result = new ArrayList<>();
        listAllParents(result, from.getParent(), to);
        Collections.reverse(result);
        return result;
    }

    private static void listAllParents(final List<PsiElement> result, final PsiElement from, final PsiElement to) {
        if (from != null && from != to) {
            result.add(from);
            listAllParents(result, from.getParent(), to);
        }
    }

    private static List<SimpleElement> parseVariables(final LeafPsiElement element, final Function<String, Map<String, String>> method) {
        return ofNullable(element)
                .filter(leafPsiElement -> method != null)
                .map(line -> method.apply(line.getText()).entrySet().stream()
                        .map(env -> new SimpleElement(env.getKey(), env.getValue(), line.getTextRange()))
                        .toList()
                )
                .orElseGet(Collections::emptyList);
    }

    private static List<SimpleElement> parseVariables(final PsiElement psiElement, final Function<String, Map<String, String>> method) {
        final List<SimpleElement> lineElements = getLineElements(psiElement);
        final Map<String, SimpleElement> result = new LinkedHashMap<>();
        lineElements.forEach(line -> method.apply(line.text()).forEach((key, value) -> result.putIfAbsent(key, new SimpleElement(key, value, line.range()))));
        method.apply(psiElement.getText()).forEach((key, value) -> result.putIfAbsent(key, new SimpleElement(key, value, psiElement.getTextRange())));
        return new ArrayList<>(result.values());
    }

    private static List<SimpleElement> getLineElements(final PsiElement psiElement) {
        return getChild(psiElement, YAMLBlockScalarImpl.class).map(psi -> {
            final TextRange parentRange = psi.getTextRange();
            return psi.getContentRanges().stream().map(textRange -> new SimpleElement(
                    null,
                    removeQuotes(psi.getText().substring(textRange.getStartOffset(), textRange.getEndOffset())),
                    new TextRange(parentRange.getStartOffset() + textRange.getStartOffset(), parentRange.getStartOffset() + textRange.getEndOffset())
            )).filter(element -> element.startIndexOffset() < element.endIndexOffset()).filter(element -> hasText(element.text())).toList();
        }).orElseGet(Collections::emptyList);
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

    private static char validateRoundBracket(final char c) {
        return c == '(' ? ')' : c;
    }
}
