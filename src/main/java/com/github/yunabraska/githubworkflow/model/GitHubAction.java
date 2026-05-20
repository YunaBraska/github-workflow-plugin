package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.services.RemoteActionProviders;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.hasText;
import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * Serialized class for JetBrains Cache
 * Name can be miss leading as it's used for [GitHub Actions, GitHub Workflows]
 * Try to use the metaData and don't use non-serializable fields
 */
@SuppressWarnings("unused")
public class GitHubAction implements Serializable {

    // SERIALIZABLE
    private final Map<String, String> metaData = new ConcurrentHashMap<>();
    private final Map<String, String> inputs = new ConcurrentHashMap<>();
    private final Map<String, String> outputs = new ConcurrentHashMap<>();
    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    // NON SERIALIZABLE
    private final Set<String> ignoredInputs = ConcurrentHashMap.newKeySet();
    private final Set<String> ignoredOutputs = ConcurrentHashMap.newKeySet();

    // STATICS
    @Serial
    private static final long serialVersionUID = 135457798745235490L;
    private static final Logger LOG = Logger.getInstance(GitHubAction.class);

    public static GitHubAction createSchemaAction(final String url, final String content) {
        return new GitHubAction()
                .isResolved(true)
                .isLocal(true)
                .isSuppressed(false)
                .name(content)
                .downloadUrl(url)
                .expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 30));
    }

    @SuppressWarnings("java:S3776")
    public static GitHubAction createGithubAction(final boolean isLocal, final String usesValue, final String absolutePath) {
        final int tagIndex = usesValue.indexOf("@");
        final int userNameIndex = usesValue.indexOf("/");
        final int repoNameIndex = usesValue.indexOf("/", userNameIndex + 1);
        final String ref = tagIndex != -1 ? usesValue.substring(tagIndex + 1) : null;
        final String tmpName;
        String slug = null;
        String tmpSub = null;

        final boolean isAction = isLocal ? !isWorkflowFile(usesValue) : (!absolutePath.contains(".yaml") && !absolutePath.contains(".yml") && !absolutePath.contains(".action.y"));

        // START [EXTRACT PARTS]
        if (tagIndex != -1 && userNameIndex < tagIndex) {
            slug = usesValue.substring(0, repoNameIndex > 0 ? repoNameIndex : tagIndex);
            if (!isAction) {
                final int beginIndex = usesValue.lastIndexOf("/") + 1;
                tmpName = beginIndex >= tagIndex ? "InvalidAction" : usesValue.substring(beginIndex, tagIndex);
            } else {
                tmpSub = repoNameIndex < tagIndex && repoNameIndex > 0 ? "/" + usesValue.substring(repoNameIndex + 1, tagIndex) : "";
                tmpName = usesValue.substring(userNameIndex + 1, tagIndex);
            }
        } else {
            tmpName = usesValue;
        }
        // END [EXTRACT PARTS]

        return new GitHubAction()
                .name(slug != null ? slug + ofNullable(tmpSub).orElse("") : tmpName)
                .usesValue(usesValue)
                .downloadUrl(isLocal ? absolutePath : toRemoteDownloadUrl(isAction, ref, slug, tmpSub, tmpName))
                .githubUrl(isAction ? toGitHubActionUrl(ref, slug, tmpSub) : toGitHubWorkflowUrl(ref, slug, tmpName))
                .expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 14))
                .isLocal(isLocal)
                .setAction(isAction)
                .isSuppressed(false)
                ;
    }

    public Optional<String> getLocalPath(final Project project) {
        return getLocalVirtualFile(project)
                .map(VirtualFile::getPath)
                .or(() -> isLocal() ? ofNullable(downloadUrl()).filter(PsiElementHelper::hasText) : Optional.empty());
    }

    public Optional<VirtualFile> getLocalVirtualFile(final Project project) {
        return isLocal() ? ofNullable(project)
                .map(ProjectUtil::guessProjectDir)
                .map(dir -> findActionYaml(usesValue(), dir))
                .or(() -> ofNullable(downloadUrl()).map(path -> LocalFileSystem.getInstance().refreshAndFindFileByPath(path))) : Optional.empty();
    }

    // !!! Performs Network and File Operations !!!
    //TODO: get Tags for autocompletion
    public synchronized GitHubAction resolve() {
        if ((!isResolved() || System.currentTimeMillis() >= expiryTime()) && !isSuppressed()) {
            extractParameters();
        }
        return this;
    }

    public Map<String, String> freshInputs() {
        if (isLocal()) {
            extractLocalParameters();
        }
        return concatMap(inputs, ignoredInputs.stream().filter(PsiElementHelper::hasText).collect(Collectors.toMap(key -> key, value -> "*** manual added input ***")));
    }

    public Map<String, String> freshOutputs() {
        return freshOutputs(true);
    }

    public Map<String, String> freshOutputs(final boolean withIgnoredItems) {
        if (isLocal()) {
            extractLocalParameters();
        }
        return withIgnoredItems ? concatMap(outputs, ignoredOutputs.stream().filter(PsiElementHelper::hasText).collect(Collectors.toMap(key -> key, value -> "*** manual added output ***"))) : unmodifiableMap(outputs);
    }

    public Map<String, String> freshSecrets() {
        if (isLocal()) {
            extractLocalParameters();
        }
        return unmodifiableMap(secrets);
    }

    public String name() {
        return metaData.getOrDefault("name", "");
    }

    public GitHubAction name(final String name) {
        ofNullable(name).ifPresent(s -> metaData.put("name", s));
        return this;
    }

    public String displayName() {
        return metaData.getOrDefault("displayName", name());
    }

    public GitHubAction displayName(final String displayName) {
        ofNullable(displayName).filter(PsiElementHelper::hasText).ifPresent(s -> metaData.put("displayName", s));
        return this;
    }

    public String description() {
        return metaData.getOrDefault("description", "");
    }

    public GitHubAction description(final String description) {
        ofNullable(description).filter(PsiElementHelper::hasText).ifPresent(s -> metaData.put("description", s));
        return this;
    }

    public String downloadUrl() {
        return metaData.getOrDefault("downloadUrl", "");
    }

    public GitHubAction downloadUrl(final String downloadUrl) {
        ofNullable(downloadUrl).ifPresent(s -> metaData.put("downloadUrl", s));
        return this;
    }

    public String githubUrl() {
        return metaData.getOrDefault("githubUrl", "");
    }

    public GitHubAction githubUrl(final String githubUrl) {
        ofNullable(githubUrl).ifPresent(s -> metaData.put("githubUrl", s));
        return this;
    }

    public String usesValue() {
        return metaData.getOrDefault("usesValue", "");
    }

    public GitHubAction usesValue(final String usesValue) {
        ofNullable(usesValue).ifPresent(s -> metaData.put("usesValue", s));
        return this;
    }

    public long expiryTime() {
        return Long.parseLong(metaData.getOrDefault("expiryTime", "-1"));
    }

    public GitHubAction expiryTime(final long expiryTime) {
        metaData.put("expiryTime", Long.toString(expiryTime));
        return this;
    }

    public boolean isLocal() {
        return parseBoolean(metaData.getOrDefault("isLocal", "false"));
    }

    public GitHubAction isLocal(final boolean local) {
        metaData.put("isLocal", Boolean.toString(local));
        return this;
    }

    public boolean isAction() {
        return parseBoolean(metaData.getOrDefault("isAction", "false"));
    }

    public GitHubAction setAction(final boolean action) {
        metaData.put("isAction", Boolean.toString(action));
        return this;
    }

    public boolean isResolved() {
        return parseBoolean(metaData.getOrDefault("isResolved", "false"));
    }

    public GitHubAction isResolved(final boolean isResolved) {
        metaData.put("isResolved", Boolean.toString(isResolved));
        return this;
    }

    public boolean isSuppressed() {
        return parseBoolean(metaData.getOrDefault("isSuppressed", "false"));
    }

    public GitHubAction isSuppressed(final boolean isSuppressed) {
        metaData.put("isSuppressed", Boolean.toString(isSuppressed));
        return this;
    }

    public GitHubAction suppressInput(final String id, final boolean supress) {
        if (supress) {
            ignoredInputs.add(id);
        } else {
            ignoredInputs.remove(id);
        }
        metaData.put("ignoredInputs", ignoredInputs.stream().filter(PsiElementHelper::hasText).collect(Collectors.joining(";")));
        return this;
    }

    public GitHubAction suppressOutput(final String id, final boolean supress) {
        if (supress) {
            ignoredOutputs.add(id);
        } else {
            ignoredOutputs.remove(id);
        }
        metaData.put("ignoredOutputs", ignoredOutputs.stream().filter(PsiElementHelper::hasText).collect(Collectors.joining(";")));
        return this;
    }

    public Set<String> ignoredInputs() {
        return unmodifiableSet(ignoredInputs);
    }

    public Set<String> ignoredOutputs() {
        return unmodifiableSet(ignoredOutputs);
    }

    /**
     * Checks whether this action has any suppressed validation warnings.
     *
     * @return {@code true} when the whole action, at least one input, or at least one output is suppressed
     */
    public boolean hasSuppressedWarnings() {
        return isSuppressed() || !ignoredInputs.isEmpty() || !ignoredOutputs.isEmpty();
    }

    /**
     * Restores all validation warnings for this action metadata entry.
     *
     * @return this action instance after suppressions were removed
     */
    public GitHubAction restoreWarnings() {
        isSuppressed(false);
        ignoredInputs.clear();
        ignoredOutputs.clear();
        metaData.put("ignoredInputs", "");
        metaData.put("ignoredOutputs", "");
        return this;
    }

    public Map<String, String> getInputs() {
        return unmodifiableMap(inputs);
    }

    public GitHubAction setInputs(final Map<String, String> inputs) {
        ofNullable(inputs).ifPresent(this.inputs::putAll);
        return this;
    }

    public Map<String, String> getOutputs() {
        return unmodifiableMap(outputs);
    }

    public GitHubAction setOutputs(final Map<String, String> outputs) {
        ofNullable(outputs).ifPresent(this.outputs::putAll);
        return this;
    }

    public Map<String, String> getSecrets() {
        return unmodifiableMap(secrets);
    }

    public GitHubAction setSecrets(final Map<String, String> secrets) {
        ofNullable(secrets).ifPresent(this.secrets::putAll);
        return this;
    }

    public Map<String, String> getMetaData() {
        return unmodifiableMap(metaData);
    }

    public GitHubAction setMetaData(final Map<String, String> metaData) {
        ofNullable(metaData).ifPresent(values -> {
            this.metaData.putAll(values);
            this.ignoredInputs.addAll(Arrays.stream(values.getOrDefault("ignoredInputs", "").split(";")).toList());
            this.ignoredOutputs.addAll(Arrays.stream(values.getOrDefault("ignoredOutputs", "").split(";")).toList());
        });
        return this;
    }

    public static VirtualFile findActionYaml(final String subPath, final VirtualFile projectDir) {
        final String localPath = normalizeLocalPath(subPath);
        return ofNullable(projectDir.findFileByRelativePath(localPath)).filter(p -> !p.isDirectory())
                .or(() -> ofNullable(projectDir.findFileByRelativePath(actionYamlPath(localPath, "action.yml"))).filter(VirtualFile::isValid).filter(p -> !p.isDirectory()))
                .or(() -> ofNullable(projectDir.findFileByRelativePath(actionYamlPath(localPath, "action.yaml"))).filter(VirtualFile::isValid).filter(p -> !p.isDirectory()))
                .orElse(null);
    }

    private static String normalizeLocalPath(final String subPath) {
        if (subPath == null || subPath.isBlank() || ".".equals(subPath) || "./".equals(subPath)) {
            return "";
        }
        return subPath.startsWith("./") ? subPath.substring(2) : subPath;
    }

    private static String actionYamlPath(final String localPath, final String fileName) {
        return localPath.isBlank() ? fileName : localPath + "/" + fileName;
    }

    private static boolean isWorkflowFile(final String usesValue) {
        return ofNullable(usesValue)
                .map(value -> value.replace('\\', '/'))
                .filter(value -> value.contains(".github/workflows/") || value.contains(".gitea/workflows/"))
                .filter(value -> value.endsWith(".yml") || value.endsWith(".yaml"))
                .isPresent();
    }

    private void extractParameters() {
        final boolean wasResolved = isResolved();
        try {
            if (isLocal()) {
                extractLocalParameters();
            } else {
                extractRemoteParameters();
            }
        } catch (final Exception e) {
            LOG.warn("Failed to set parameters [" + this.name() + "]", e);
            if (wasResolved) {
                expiryTime(System.currentTimeMillis() + CACHE_ONE_DAY);
            } else {
                isResolved(false);
            }
        }
    }

    private void extractRemoteParameters() {
        final boolean wasResolved = isResolved();
        RemoteActionProviders.resolve(usesValue())
                .ifPresentOrElse(resolution -> {
                    name(resolution.name());
                    downloadUrl(resolution.downloadUrl());
                    githubUrl(resolution.githubUrl());
                    setAction(resolution.action());
                    remoteRefs(resolution.refs());
                    setParameters(resolution.content());
                }, () -> {
                    if (wasResolved) {
                        expiryTime(System.currentTimeMillis() + CACHE_ONE_DAY);
                    } else {
                        isResolved(false);
                    }
                });
    }

    private void extractLocalParameters() {
        of(downloadUrl()).flatMap(PsiElementHelper::toPath).filter(Files::isRegularFile).map(file -> {
            try {
                return Files.readString(file);
            } catch (final IOException ignored) {
                return null;
            }
        }).or(this::readLocalVirtualFileContent).ifPresent(this::setParameters);
    }

    private Optional<String> readLocalVirtualFileContent() {
        return Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .map(this::getLocalVirtualFile)
                .flatMap(Optional::stream)
                .filter(VirtualFile::isValid)
                .filter(virtualFile -> !virtualFile.isDirectory())
                .findFirst()
                .flatMap(GitHubAction::readVirtualFileContent);
    }

    private static Optional<String> readVirtualFileContent(final VirtualFile virtualFile) {
        try {
            return Optional.of(new String(virtualFile.contentsToByteArray(), StandardCharsets.UTF_8));
        } catch (final IOException ignored) {
            return Optional.empty();
        }
    }

    private void setParameters(final String content) {
        isResolved(hasText(content));
        readPsiElement(ProjectManager.getInstance().getDefaultProject(), downloadUrl(), content, psiFile -> {
            displayName(PsiElementHelper.getText(psiFile.getContainingFile(), "name").orElse(name()));
            description(PsiElementHelper.getText(psiFile.getContainingFile(), "description").orElse(""));
            inputs.clear();
            inputs.putAll(getActionParameters(psiFile, FIELD_INPUTS, isAction()));
            outputs.clear();
            outputs.putAll(getActionParameters(psiFile, FIELD_OUTPUTS, isAction()));
            secrets.clear();
            secrets.putAll(getActionParameters(psiFile, FIELD_SECRETS, isAction()));
        });
    }

    @Nullable
    private static String toRemoteDownloadUrl(final boolean isAction, final String ref, final String slug, final String sub, final String name) {
        return isAction ? toActionDownloadUrl(ref, slug, sub) : toWorkflowDownloadUrl(ref, slug, name);
    }

    @Nullable
    private static String toWorkflowDownloadUrl(final String ref, final String slug, final String name) {
        return (ref != null && slug != null) ? "https://raw.githubusercontent.com/" + slug + "/" + ref + "/.github/workflows/" + name : null;
    }

    @Nullable
    private static String toActionDownloadUrl(final String ref, final String slug, final String sub) {
        return (ref != null && slug != null && sub != null) ? "https://raw.githubusercontent.com/" + slug + "/" + ref + sub + "/action.yml" : null;
    }

    @Nullable
    private static String toGitHubWorkflowUrl(final String ref, final String slug, final String name) {
        return (ref != null && slug != null) ? "https://github.com/" + slug + "/blob/" + ref + "/.github/workflows/" + name : null;
    }

    @Nullable
    private static String toGitHubActionUrl(final String ref, final String slug, final String sub) {
        //        return (ref != null && slug != null && sub != null) ? "https://github.com/" + slug + "/blob/" + ref + sub + "/action.yml" : null;
        //        https://github.com/actions/checkout/tree/Update-description#readme
        return (ref != null && slug != null && sub != null) ? "https://github.com/" + slug + "/tree/" + ref + sub + "#readme" : null;
    }

    public List<String> remoteRefs() {
        return Arrays.stream(metaData.getOrDefault("remoteRefs", "").split(";"))
                .filter(PsiElementHelper::hasText)
                .toList();
    }

    public GitHubAction remoteRefs(final List<String> refs) {
        metaData.put("remoteRefs", ofNullable(refs).orElseGet(List::of).stream()
                .filter(PsiElementHelper::hasText)
                .distinct()
                .collect(Collectors.joining(";")));
        return this;
    }

    @NotNull
    private static Map<String, String> getActionParameters(final PsiElement psiElement, final String fieldName, final boolean action) {
        if (action) {
            return readActionParameters(psiElement, fieldName);
        } else {
            return readWorkflowParameters(psiElement, fieldName);
        }
    }

    @NotNull
    private static Map<String, String> readActionParameters(final PsiElement psiElement, final String fieldName) {
        return getChild(psiElement.getContainingFile(), fieldName)
                .map(PsiElementHelper::getChildren)
                .map(children -> children.stream().collect(Collectors.toMap(YAMLKeyValue::getKeyText, field -> PsiElementHelper.getDescription(field, FIELD_INPUTS.equals(fieldName)))))
                .orElseGet(Collections::emptyMap);
    }

    @NotNull
    private static Map<String, String> readWorkflowParameters(final PsiElement psiElement, final String fieldName) {
        return getChild(psiElement.getContainingFile(), FIELD_ON)
                .flatMap(on -> getChild(on, "workflow_call"))
                .flatMap(workflowCall -> getChild(workflowCall, fieldName))
                .map(PsiElementHelper::getChildren)
                .map(children -> children.stream().collect(Collectors.toMap(YAMLKeyValue::getKeyText, field -> PsiElementHelper.getDescription(field, FIELD_INPUTS.equals(fieldName)))))
                .orElseGet(Collections::emptyMap);
    }

    private static void readPsiElement(final Project project, final String fileName, final String fileContent, final Consumer<PsiFile> action) {
        ReadAction.nonBlocking(() -> {
            try {
                ofNullable(action).ifPresent(consumer -> consumer.accept(PsiFileFactory.getInstance(project).createFileFromText(fileName, YAMLFileType.YML, fileContent.replaceAll("\r?\\n|\\r", "\n"))));
            } catch (final Exception ignored) {
                // ignored
            }
            return null;
        }).executeSynchronously();
    }

    private static <K, V> Map<K, V> concatMap(final Map<K, V> map1, final Map<K, V> map2) {
        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GitHubAction that = (GitHubAction) o;
        return Objects.equals(downloadUrl(), that.downloadUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(downloadUrl());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", GitHubAction.class.getSimpleName() + "[", "]")
                .add("metaData=" + metaData)
                .add("inputs=" + (inputs.size() + ignoredInputs.size()))
                .add("outputs=" + (outputs.size() + ignoredOutputs.size()))
                .add("secrets=" + secrets.size())
                .toString();
    }
}
