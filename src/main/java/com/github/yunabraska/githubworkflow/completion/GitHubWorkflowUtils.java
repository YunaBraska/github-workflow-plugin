package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.io.HttpRequests;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.PATTERN_GITHUB_OUTPUT;
import static java.util.Optional.ofNullable;

public class GitHubWorkflowUtils {

    public static final Path TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "ide_github_workflow_plugin");
    private static final Logger LOG = Logger.getInstance(GitHubWorkflowUtils.class);

    public static Optional<String[]> getCaretBracketItem(final String text, final int caretOffset, final Supplier<YamlNode> currentNode) {
        final String partString = text.substring(0, caretOffset);
        final int bracketStart = partString.lastIndexOf("${{");
        if (isInBrackets(partString, bracketStart) || "if".equals(currentNode.get().name())) {
            return Optional.of(partString.substring(lastIndexOf(partString, " ", "{", "|", "&", "(", ")") + 1).split("\\."));
        }
        return Optional.empty();
    }


    public static boolean isInBrackets(final String partString, final int bracketStart) {
        return bracketStart != -1 && partString.lastIndexOf("}}") <= bracketStart;
    }

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

    public static void addLookupElements(final CompletionResultSet resultSet, final Map<String, String> map, final NodeIcon icon) {
        if (!map.isEmpty()) {
            resultSet.addAllElements(toLookupElements(map, icon, Character.MIN_VALUE));
        }
    }

    public static void addLookupElements(final CompletionResultSet resultSet, final Map<String, String> map, final NodeIcon icon, final char suffix) {
        if (!map.isEmpty()) {
            resultSet.addAllElements(toLookupElements(map, icon, suffix));
        }
    }

    public static List<LookupElement> toLookupElements(final Map<String, String> map, final NodeIcon icon, final char suffix) {
        return map.entrySet().stream().map(item -> {
            LookupElementBuilder result = LookupElementBuilder
                    .create(item.getKey())
                    .withIcon(icon.icon())
                    .withBoldness(icon != NodeIcon.ICON_ENV)
                    .withTypeText(item.getValue());
            result = suffix != Character.MIN_VALUE ? result.withInsertHandler((ctx, i) -> addSuffix(ctx, item.getKey(), suffix)) : result;
            return PrioritizedLookupElement.withPriority(suffix != Character.MIN_VALUE ? result.withAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) : result, icon.ordinal() + 5);
        }).collect(Collectors.toList());
    }

    private static void addSuffix(final InsertionContext ctx, final String value, final char suffix) {
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
            final Path path = TMP_DIR.resolve(name + "_schema.json");
            final VirtualFile newVirtualFile = new LightVirtualFile("github_workflow_plugin_" + path.getFileName().toString(), JsonFileType.INSTANCE, "");
            //FIXME: how to use the intellij idea cache?
            VfsUtil.saveText(newVirtualFile, downloadContent(url, path, CACHE_ONE_DAY * 30));
            return newVirtualFile;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String downloadAction(final String url, final GitHubAction gitHubAction) {
        return downloadContent(url, TMP_DIR.resolve(gitHubAction.actionName() + "_" + gitHubAction.ref() + "_schema.json"), CACHE_ONE_DAY * 14);
    }

    public static String downloadContent(final String url, final Path path, final long expirationTime) {
        try {
            if (Files.exists(path) && (expirationTime < 1 || Files.getLastModifiedTime(path).toMillis() > System.currentTimeMillis() - expirationTime)) {
                LOG.info("Cache load [" + path + "] expires in [" + (System.currentTimeMillis() - expirationTime) + "ms]");
                return readFile(path);
            } else {
                if (!Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                final String content = downloadContent(url);
                Files.write(path, content.getBytes());
                return content;
            }
        } catch (Exception e) {
            LOG.error("Cache failed for [" + path + "] message [" + (e instanceof NullPointerException ? null : e.getMessage()) + "]");
            return "";
        }
    }

    private static String readFile(final Path path) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(path, Charset.defaultCharset());) {
            final StringBuilder contentBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append(System.lineSeparator());
            }
            return contentBuilder.toString();
        }
    }

    private static String downloadContent(final String urlString) {
        LOG.info("Download [" + urlString + "]");
        try {
            final ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
            return ApplicationManager.getApplication().executeOnPooledThread(() -> HttpRequests
                    .request(urlString)
                    .gzip(true)
                    .readTimeout(5000)
                    .connectTimeout(5000)
                    .userAgent(applicationInfo.getBuild().getProductCode() + "/" + applicationInfo.getFullVersion()).tuner(request -> request.setRequestProperty("Client-Name", "GitHub Workflow Plugin")).readString()).get();
        } catch (Exception e) {
            LOG.error("Download failed for [" + urlString + "] message [" + (e instanceof NullPointerException ? null : e.getMessage()) + "]");
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
