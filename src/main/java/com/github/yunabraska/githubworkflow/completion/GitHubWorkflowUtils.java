package com.github.yunabraska.githubworkflow.completion;

import com.github.yunabraska.githubworkflow.model.DownloadException;
import com.intellij.codeInsight.completion.CompletionParameters;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.PATTERN_GITHUB_ENV;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.PATTERN_GITHUB_OUTPUT;
import static java.util.Optional.ofNullable;

public class GitHubWorkflowUtils {

    public static final Path TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "ide_github_workflow_plugin");
    private static final Logger LOG = Logger.getInstance(GitHubWorkflowUtils.class);

    public static Optional<String[]> getCaretBracketItem(final CompletionParameters parameters, final Supplier<WorkflowFile> part, final String[] prefix) {
        final String wholeText = parameters.getOriginalFile().getText();
        final int caretOffset = parameters.getOffset();
        final String offsetText = wholeText.substring(0, caretOffset);
        final int bracketStart = offsetText.lastIndexOf("${{");
        if (caretOffset > 2 && isInBrackets(offsetText, bracketStart) || "if".equals(part.get().getCurrentNode().name())) {
            final char previousChar = wholeText.charAt(caretOffset - 1);
            if (caretOffset > 1 && previousChar == '.') {
                //NEXT ELEMENT
                final int indexStart = getStartIndex(wholeText, caretOffset - 1);
                final int indexEnd = getEndIndex(wholeText, caretOffset - 1, wholeText.length());
                return Optional.of(wholeText.substring(indexStart, indexEnd + 1).split("\\."));
            } else if (caretOffset > 1 && isNonValidNodeChar(previousChar)) {
                //START ELEMENT
                return Optional.of(prefix);
            } else {
                //MIDDLE ELEMENT
                final int indexStart = getStartIndex(wholeText, caretOffset - 1);
                final String[] prefArray = wholeText.substring(indexStart, caretOffset).split("\\.", -1);
                prefix[0] = prefArray[prefArray.length - 1];
                return Optional.of(wholeText.substring(indexStart, caretOffset - prefix[0].length()).split("\\."));
            }
        }
        return Optional.empty();
    }

    private static boolean isNonValidNodeChar(final char c) {
        return !Character.isLetterOrDigit(c) && c != '_' && c != '-';
    }

    private static int getStartIndex(final CharSequence currentText, final int fromIndex) {
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


    public static boolean isInBrackets(final String partString, final int bracketStart) {
        return bracketStart != -1 && partString.lastIndexOf("}}") <= bracketStart;
    }

    public static String orEmpty(final String text) {
        return ofNullable(text).orElse("");
    }

    public static String getDescription(final YamlNode n) {
        return n.getChild("required").map(YamlNode::value).map(required -> "req[" + required + "] ").orElse("")
                + n.getChild("default").map(YamlNode::value).map(def -> "def[" + def + "] ").orElse("")
                + n.getChild("description").map(YamlNode::value).orElse("");
    }

    public static Map<String, String> toGithubOutputs(final String text) {
        final Map<String, String> variables = new HashMap<>();
        if (text.contains("$GITHUB_OUTPUT") || text.contains("${GITHUB_OUTPUT}")) {
            final Matcher matcher = PATTERN_GITHUB_OUTPUT.matcher(text);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    variables.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return variables;
    }

    public static Map<String, String> toGithubEnvs(final String text) {
        final Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_ENV") || text.contains("${GITHUB_ENV}")) {
            final Matcher matcher = PATTERN_GITHUB_ENV.matcher(text);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    variables.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return variables;
    }

    public static void addLookupElements(final CompletionResultSet resultSet, final Map<String, String> map, final NodeIcon icon, final char suffix) {
        if (!map.isEmpty()) {
            resultSet.addAllElements(toLookupElements(map, icon, suffix));
        }
    }

    public static List<LookupElement> toLookupElements(final Map<String, String> map, final NodeIcon icon, final char suffix) {
        return map.entrySet().stream().map(item -> toLookupElement(icon, suffix, item.getKey(), item.getValue())).collect(Collectors.toList());
    }

    public static LookupElement toLookupElement(final NodeIcon icon, final char suffix, final String key, final String text) {
        LookupElementBuilder result = LookupElementBuilder
                .create(key)
                .withIcon(icon.icon())
                .withBoldness(icon != NodeIcon.ICON_ENV)
                .withTypeText(text)
                .withCaseSensitivity(false);
        result = suffix != Character.MIN_VALUE ? result.withInsertHandler((ctx, i) -> addSuffix(ctx, key, suffix)) : result;
        return PrioritizedLookupElement.withPriority(suffix != Character.MIN_VALUE ? result.withAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) : result, icon.ordinal() + 5d);
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
        final String toInsert = String.valueOf(suffix);
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
            return downloadContentAsync(url, path, expirationTime).get();
        } catch (Exception e) {
            throw new DownloadException(e);
        }
    }

    private static CompletableFuture<String> downloadContentAsync(final String url, final Path path, final long expirationTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.exists(path) && (expirationTime < 1 || Files.getLastModifiedTime(path).toMillis() > System.currentTimeMillis() - expirationTime)) {
                    LOG.info("Cache load [" + path + "] expires in [" + (System.currentTimeMillis() - expirationTime) + "ms]");
                    return readFileAsync(path).get();
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
        });
    }


    public static CompletableFuture<String> readFileAsync(final Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try (final BufferedReader reader = Files.newBufferedReader(path, Charset.defaultCharset())) {
                final StringBuilder contentBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    contentBuilder.append(line).append(System.lineSeparator());
                }
                return contentBuilder.toString();
            } catch (IOException e) {
                LOG.error("Failed to read file [" + path + "] message [" + e.getMessage() + "]");
                return "";
            }
        });
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

    @SuppressWarnings("unused")
    public static void yamlOf(final Path path) {
        try (final FileReader fileReader = new FileReader(path.toFile())) {
            processYamlElement(new Yaml().load(fileReader));
        } catch (IOException ignored) {
            //ignored
        }
    }

    @SuppressWarnings("unchecked")
    public static void processYamlElement(final Object yamlElement) {
        if (yamlElement instanceof Map) {
            final Map<String, Object> yamlMap = (Map<String, Object>) yamlElement;
            for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
                processYamlElement(entry.getValue());
            }
        } else if (yamlElement instanceof List) {
            final List<Object> yamlList = (List<Object>) yamlElement;
            for (Object element : yamlList) {
                processYamlElement(element);
            }
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
