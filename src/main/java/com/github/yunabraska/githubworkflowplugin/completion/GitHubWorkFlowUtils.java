package com.github.yunabraska.githubworkflowplugin.completion;

import com.intellij.lang.Language;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class GitHubWorkFlowUtils {

    public static String downloadContent(final String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

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
                .map(Path::of)
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
                .map(Path::of);
    }

    private static boolean isYamlFile(final Language language) {
        return language.isKindOf("yaml") || language.isKindOf("yml");
    }

    private GitHubWorkFlowUtils() {
    }
}
