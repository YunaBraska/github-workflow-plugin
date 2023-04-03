package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class GitHubWorkflowUtils {

    public static int lastIndexOf(final String input, final String... indexOf) {
        return Arrays.stream(indexOf).mapToInt(input::lastIndexOf).max().orElse(-1);
    }

    private static String orEmpty(final String text) {
        return ofNullable(text).orElse("");
    }

    public static String getDescription(final YamlNode n) {
        return n.getChild("required").map(YamlNode::value).map(required -> "req[" + required + "] ").orElse("")
                + n.getChild("default").map(YamlNode::value).map(def -> "def[" + def + "] ").orElse("")
                + n.getChild("description").map(YamlNode::value).orElse("");
    }

    public static List<LookupElement> mapToLookupElements(final Map<String, String> map, final int prio, final boolean bold) {
        return mapToLookupElements(map, prio, bold, Character.MIN_VALUE);
    }

    public static List<LookupElement> mapToLookupElements(final Map<String, String> map, final int prio, final boolean bold, final char suffix) {
        return map.entrySet().stream().map(item -> PrioritizedLookupElement.withPriority(mapToLookupElementBuilder(item, bold, suffix), prio)).collect(Collectors.toList());
    }

    private static LookupElementBuilder mapToLookupElementBuilder(final Map.Entry<String, String> item, final boolean bold, final char suffix) {
        final LookupElementBuilder lookupElementBuilder = LookupElementBuilder
                .create(item.getKey())
                .withIcon(AllIcons.General.Add)
                .withBoldness(bold)
                .withTypeText(item.getValue());
        return suffix != Character.MIN_VALUE ? lookupElementBuilder.withInsertHandler((ctx, i) -> addDoubleDots(ctx, item.getKey(), suffix)) : lookupElementBuilder;
    }

    private static void addDoubleDots(final InsertionContext ctx, final String value, final char suffix) {
        final int startOffset = ctx.getStartOffset();
        final int tailOffset = ctx.getTailOffset();
        int newOffset = startOffset + value.length();
        final Document document = ctx.getDocument();

        // Find the start of the identifier
        final CharSequence documentChars = document.getCharsSequence();
        // Find the end of the previous value
        int valueEnd = tailOffset;
        if (ctx.getCompletionChar() == '\t') {
            while (valueEnd < documentChars.length() &&
                    documentChars.charAt(valueEnd) != suffix &&
                    documentChars.charAt(valueEnd) != '\n' &&
                    documentChars.charAt(valueEnd) != '\r') {
                valueEnd++;
            }
            valueEnd++;
        }
        // Remove the previous value
        document.deleteString(startOffset, valueEnd);
        // Insert the new value
        document.insertString(startOffset, value);
        // Add ': ' after the inserted value
        final String toInsert = suffix + "";
        document.insertString(startOffset + value.length(), toInsert);
        newOffset += toInsert.length();
        // Update caret position
        ctx.getEditor().getCaretModel().moveToOffset(newOffset);
    }

    public static String downloadContent(final String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        //TODO: set user agent

        final int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                final StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            }
        }
        return null;
    }

    public static Optional<Path> getWorkflowFile(final PsiElement psiElement) {
        return Optional.ofNullable(psiElement)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getOriginalFile)
                .map(PsiFile::getViewProvider)
                .map(FileViewProvider::getVirtualFile)
                .map(VirtualFile::getPath)
                .map(Paths::get)
                .filter(isWorkflowPath());
    }

    public static void yamlOf(final Path path) {
        try (final FileReader fileReader = new FileReader(path.toFile())) {
            processYamlElement(new Yaml().load(fileReader));
        } catch (IOException ignored) {
        }
    }

    public static void processYamlElement(final Object yamlElement) {
        if (yamlElement instanceof Map) {
            final Map<String, Object> yamlMap = (Map<String, Object>) yamlElement;
            for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
                System.out.println("Key: " + entry.getKey());
                processYamlElement(entry.getValue());
            }
        } else if (yamlElement instanceof List) {
            final List<Object> yamlList = (List<Object>) yamlElement;
            for (Object element : yamlList) {
                processYamlElement(element);
            }
        } else {
            System.out.println("Value: " + yamlElement);
        }
    }

    @NotNull
    private static Predicate<Path> isWorkflowPath() {
        return path -> path.getNameCount() > 2
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflows")
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github")
                && (path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yml") || path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yaml"));
    }

    @NotNull
    private static Optional<Path> getFilePath(@NotNull final PsiElement psiElement) {
        return Optional.of(psiElement)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getOriginalFile)
                .map(PsiFile::getViewProvider)
                .map(FileViewProvider::getVirtualFile)
                .map(VirtualFile::getPath)
                .map(Paths::get);
    }

    private static boolean isYamlFile(final Language language) {
        return language.isKindOf("yaml") || language.isKindOf("yml");
    }

    private GitHubWorkflowUtils() {
    }
}
