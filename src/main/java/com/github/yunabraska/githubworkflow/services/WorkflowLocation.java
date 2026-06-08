package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.services.WorkflowYaml;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorkflowLocation {

    private final YAMLKeyValue keyValue;
    private final List<String> path;
    private final boolean workflowFile;

    private WorkflowLocation(
            final YAMLKeyValue keyValue,
            final List<String> path,
            final boolean workflowFile
    ) {
        this.keyValue = keyValue;
        this.path = List.copyOf(path);
        this.workflowFile = workflowFile;
    }

    static Optional<WorkflowLocation> from(final PsiElement element) {
        return Optional.ofNullable(element)
                .filter(PsiElement::isValid)
                .flatMap(WorkflowLocation::keyValueOf)
                .map(keyValue -> new WorkflowLocation(keyValue, pathOf(keyValue), isWorkflowFile(keyValue)));
    }

    YAMLKeyValue keyValue() {
        return keyValue;
    }

    List<String> path() {
        return path;
    }

    boolean workflowFile() {
        return workflowFile;
    }

    static boolean pathEndsWith(final List<String> path, final String... expected) {
        if (path.size() < expected.length) {
            return false;
        }
        final int offset = path.size() - expected.length;
        for (int index = 0; index < expected.length; index++) {
            if (!expected[index].equals(path.get(offset + index))) {
                return false;
            }
        }
        return true;
    }

    static boolean isChildOf(final List<String> path, final String... expectedParent) {
        return path.size() == expectedParent.length + 1 && pathEndsWith(path.subList(0, path.size() - 1), expectedParent);
    }

    static boolean pathMatches(final List<String> path, final String... pattern) {
        if (path.size() != pattern.length) {
            return false;
        }
        for (int index = 0; index < pattern.length; index++) {
            if (!"*".equals(pattern[index]) && !pattern[index].equals(path.get(index))) {
                return false;
            }
        }
        return true;
    }

    static Optional<KeyContext> keyContextAt(final PsiElement position, final int rawOffset) {
        return Optional.ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> keyContextFromText(file.getText(), rawOffset));
    }

    static KeyContext keyContextFromText(final String wholeText, final int rawOffset) {
        final int offset = boundedOffset(wholeText, rawOffset);
        final int lineStart = currentLineStart(wholeText, offset);
        final String currentLine = lineBeforeCaret(wholeText, offset);
        final int currentIndent = leadingSpaces(currentLine);
        final List<YamlAncestor> stack = new ArrayList<>();
        wholeText.substring(0, Math.min(lineStart, wholeText.length())).lines().forEach(raw -> {
            final String content = raw.trim();
            if (!content.isBlank() && !content.startsWith("#")) {
                currentLineKey(content).ifPresent(key -> {
                    final int indent = leadingSpaces(raw);
                    while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                        stack.remove(stack.size() - 1);
                    }
                    stack.add(new YamlAncestor(indent, key));
                });
            }
        });
        while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= currentIndent) {
            stack.remove(stack.size() - 1);
        }
        return new KeyContext(stack.stream().map(YamlAncestor::key).toList(), currentLine);
    }

    static List<String> pathOf(final YAMLKeyValue element) {
        final List<String> result = new ArrayList<>();
        PsiElement current = element.getParent();
        while (current != null && current != element.getContainingFile()) {
            if (current instanceof YAMLKeyValue keyValue) {
                result.add(0, keyValue.getKeyText());
            }
            current = current.getParent();
        }
        return result;
    }

    static Optional<String> currentLineKey(final String currentLine) {
        return yamlKey(currentLine.trim());
    }

    static boolean isValueCompletion(final String currentLine) {
        return currentLine.replace("IntellijIdeaRulezzz", "").matches("\\s*[^:#]+:\\s*.*");
    }

    static String lineBeforeCaret(final String wholeText, final int rawOffset) {
        final int offset = boundedOffset(wholeText, rawOffset);
        final int lineStart = currentLineStart(wholeText, offset);
        if (lineStart > offset) {
            return "";
        }
        return wholeText.substring(lineStart, offset).replace("IntellijIdeaRulezzz", "");
    }

    static int currentLineStart(final String wholeText, final int offset) {
        if (offset < 1) {
            return 0;
        }
        return wholeText.lastIndexOf('\n', offset - 1) + 1;
    }

    static int boundedOffset(final String wholeText, final int rawOffset) {
        return Math.max(0, Math.min(rawOffset, wholeText.length()));
    }

    private static Optional<String> yamlKey(final String content) {
        final String normalized = content.startsWith("- ") ? content.substring(2).trim() : content;
        final int separator = normalized.indexOf(':');
        if (separator <= 0) {
            return Optional.empty();
        }
        return Optional.of(stripYamlKeyQuotes(normalized.substring(0, separator).trim()))
                .filter(key -> !key.isBlank());
    }

    private static int leadingSpaces(final String value) {
        int result = 0;
        while (result < value.length() && value.charAt(result) == ' ') {
            result++;
        }
        return result;
    }

    private static String stripYamlKeyQuotes(final String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Optional<YAMLKeyValue> keyValueOf(final PsiElement element) {
        PsiElement current = element;
        while (current != null && current != element.getContainingFile()) {
            if (current instanceof YAMLKeyValue keyValue) {
                return Optional.of(keyValue);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static boolean isWorkflowFile(final PsiElement element) {
        return WorkflowYaml.getWorkflowFile(element)
                .filter(WorkflowYaml::isWorkflowFile)
                .isPresent();
    }

    public static final class RepositoryResolver {

        private static final Pattern HTTPS_REMOTE = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+?)(?:[.]git)?/?$");
        private static final Pattern SSH_REMOTE = Pattern.compile("(?:git@|ssh://git@)([^:/]+)[:/]([^/]+)/([^/]+?)(?:[.]git)?/?$");

        public Optional<Repository> resolve(final Project project) {
            return Optional.ofNullable(project)
                    .map(ProjectUtil::guessProjectDir)
                    .map(VirtualFile::getPath)
                    .map(Path::of)
                    .flatMap(this::resolve);
        }

        public Optional<Repository> resolve(final Project project, final VirtualFile file) {
            return repositoryRoot(file)
                    .flatMap(this::resolve)
                    .or(() -> resolve(project));
        }

        Optional<Repository> resolve(final Path projectDir) {
            return readGitConfig(projectDir)
                    .flatMap(RepositoryResolver::firstOriginUrl)
                    .flatMap(RepositoryResolver::fromRemoteUrl);
        }

        Optional<String> branch(final Project project) {
            return Optional.ofNullable(project)
                    .map(ProjectUtil::guessProjectDir)
                    .map(VirtualFile::getPath)
                    .map(Path::of)
                    .flatMap(this::branch);
        }

        Optional<String> branch(final Project project, final VirtualFile file) {
            return repositoryRoot(file)
                    .flatMap(this::branch)
                    .or(() -> branch(project));
        }

        Optional<String> branch(final Path projectDir) {
            return gitDir(projectDir)
                    .map(dir -> dir.resolve("HEAD"))
                    .flatMap(RepositoryResolver::readString)
                    .flatMap(RepositoryResolver::branchName);
        }

        static Optional<Repository> fromRemoteUrl(final String remoteUrl) {
            return match(HTTPS_REMOTE, remoteUrl).or(() -> match(SSH_REMOTE, remoteUrl));
        }

        static Optional<String> branchName(final String head) {
            final String prefix = "ref: refs/heads/";
            return Optional.ofNullable(head)
                    .map(String::trim)
                    .filter(value -> value.startsWith(prefix))
                    .map(value -> value.substring(prefix.length()))
                    .filter(value -> !value.isBlank());
        }

        private static Optional<Repository> match(final Pattern pattern, final String remoteUrl) {
            final Matcher matcher = pattern.matcher(Optional.ofNullable(remoteUrl).orElse("").trim());
            if (!matcher.matches()) {
                return Optional.empty();
            }
            final String host = matcher.group(1);
            final String owner = matcher.group(2);
            final String repo = matcher.group(3);
            final String webUrl = "https://" + host;
            final String apiUrl = "github.com".equalsIgnoreCase(host)
                    ? "https://api.github.com"
                    : webUrl + "/api/v3";
            return Optional.of(new Repository(webUrl, apiUrl, owner, repo));
        }

        private static Optional<String> readGitConfig(final Path projectDir) {
            final Path config = projectDir.resolve(".git").resolve("config");
            if (!Files.isRegularFile(config)) {
                return Optional.empty();
            }
            return readString(config);
        }

        static Optional<Path> gitDir(final Path projectDir) {
            final Path dotGit = projectDir.resolve(".git");
            if (Files.isDirectory(dotGit)) {
                return Optional.of(dotGit);
            }
            if (!Files.isRegularFile(dotGit)) {
                return Optional.empty();
            }
            return readString(dotGit)
                    .map(String::trim)
                    .filter(value -> value.startsWith("gitdir:"))
                    .map(value -> value.substring("gitdir:".length()).trim())
                    .filter(value -> !value.isBlank())
                    .map(Path::of)
                    .map(path -> path.isAbsolute() ? path : projectDir.resolve(path).normalize());
        }

        private static Optional<String> readString(final Path path) {
            try {
                return Optional.of(Files.readString(path));
            } catch (final IOException ignored) {
                return Optional.empty();
            }
        }

        private static Optional<Path> repositoryRoot(final VirtualFile file) {
            Path current = Optional.ofNullable(file)
                    .map(VirtualFile::getPath)
                    .map(Path::of)
                    .map(Path::getParent)
                    .orElse(null);
            while (current != null) {
                if (Files.isRegularFile(current.resolve(".git").resolve("config")) || Files.isRegularFile(current.resolve(".git"))) {
                    return Optional.of(current);
                }
                current = current.getParent();
            }
            return Optional.empty();
        }

        private static Optional<String> firstOriginUrl(final String config) {
            boolean inOrigin = false;
            for (final String line : config.split("\\R")) {
                final String trimmed = line.trim();
                if (trimmed.startsWith("[remote ")) {
                    inOrigin = trimmed.equals("[remote \"origin\"]");
                    continue;
                }
                if (inOrigin && trimmed.startsWith("url =")) {
                    return Optional.of(trimmed.substring("url =".length()).trim()).filter(value -> !value.isBlank());
                }
            }
            return Optional.empty();
        }
    }

    record KeyContext(List<String> path, String currentLine) {
    }

    record Repository(String webUrl, String apiUrl, String owner, String repo) {
    }

    private record YamlAncestor(int indent, String key) {
    }
}
