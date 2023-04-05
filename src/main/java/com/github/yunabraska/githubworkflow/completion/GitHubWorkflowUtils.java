package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.io.HttpRequests;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.PATTERN_GITHUB_OUTPUT;
import static java.util.Optional.ofNullable;

public class GitHubWorkflowUtils {

    public static int lastIndexOf(final String input, final String... indexOf) {
        return Arrays.stream(indexOf).mapToInt(input::lastIndexOf).max().orElse(-1);
    }

    public static String orEmpty(final String text) {
        return ofNullable(text).orElse("");
    }

    public static String getDescription(final YamlNode n) {
        return n.getChild("required").map(YamlNode::value).map(required -> "req[" + required + "] ").orElse("")
                + n.getChild("default").map(YamlNode::value).map(def -> "def[" + def + "] ").orElse("")
                + n.getChild("description").map(YamlNode::value).orElse("");
    }

    public static Map<String, String> toGithubOutputs(final YamlNode step) {
        final Map<String, String> result = new HashMap<>();
        step.children().stream().map(YamlNode::value).filter(Objects::nonNull).forEach(s -> result.putAll(toGithubOutputs(s)));
        return result;
    }

    public static Map<String, String> toGithubOutputs(final String s) {
        final Map<String, String> variables = new HashMap<>();
        if (s.contains("$GITHUB_OUTPUT") || s.contains("${GITHUB_OUTPUT}")) {
            final Matcher matcher = PATTERN_GITHUB_OUTPUT.matcher(s);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    variables.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return variables;
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

    public static VirtualFile downloadSchema(final String url, final String name) {
        try {
            final VirtualFile newVirtualFile = new LightVirtualFile("github-workflow-plugin-" + name + "-schema.json", JsonFileType.INSTANCE, "");
            VfsUtil.saveText(newVirtualFile, downloadContent(url));
            return newVirtualFile;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String downloadContent(final String urlString) {
        try {
            final ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
            return ApplicationManager.getApplication().executeOnPooledThread(() -> HttpRequests
                    .request(urlString)
                    .gzip(true)
                    .readTimeout(5000)
                    .connectTimeout(5000)
                    .userAgent(applicationInfo.getBuild().getProductCode() + "/" + applicationInfo.getFullVersion()).tuner(request -> request.setRequestProperty("Client-Name", "GitHub Workflow Plugin")).readString()).get();
        } catch (Exception ignored) {
            System.out.println(ignored.getMessage());
            //ignored
        }
        return "";
    }

    public static Optional<Path> getWorkflowFile(final PsiElement psiElement) {
        return Optional.ofNullable(psiElement)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getOriginalFile)
                .map(PsiFile::getViewProvider)
                .map(FileViewProvider::getVirtualFile)
                .map(VirtualFile::getPath)
                .map(Paths::get)
                .filter(GitHubWorkflowUtils::isWorkflowPath);
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

    public static boolean isWorkflowPath(final Path path) {
        return path.getNameCount() > 2
                && isYamlFile(path)
                && path.getName(path.getNameCount() - 2).toString().equalsIgnoreCase("workflows")
                && path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(".github");
    }

    public static boolean isYamlFile(final Path path) {
        return path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yml") || path.getName(path.getNameCount() - 1).toString().toLowerCase().endsWith(".yaml");
    }

    private GitHubWorkflowUtils() {
    }
}
