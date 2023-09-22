package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadContent;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadFileFromGitHub;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.model.PsiElementHelper.getChildWithKey;
import static com.github.yunabraska.githubworkflow.model.PsiElementHelper.hasText;
import static com.github.yunabraska.githubworkflow.model.PsiElementHelper.readPsiElement;
import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * Serialized class for JetBrains Cache
 * Name can be miss leading as it's used for [GitHub Actions, GitHub Workflows, GitHub Schema]
 * Try to use the metaData and don't use non-serializable fields
 */
@SuppressWarnings("unused")
public class GitHubAction implements Serializable {

    private final Map<String, String> metaData = new ConcurrentHashMap<>();
    private final Map<String, String> inputs = new ConcurrentHashMap<>();
    private final Map<String, String> outputs = new ConcurrentHashMap<>();
    @Serial
    private static final long serialVersionUID = 135457798745235490L;
    private static final Logger LOG = Logger.getInstance(GitHubAction.class);

    public static GitHubAction createSchemaAction(final String url, final String content) {
        return new GitHubAction()
                .isResolved(true)
                .isSchema(true)
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

        final boolean isAction = isLocal || (!absolutePath.contains(".yaml") && !absolutePath.contains(".yml") && !absolutePath.contains(".action.y"));

        // START [EXTRACT PARTS]
        if (tagIndex != -1 && userNameIndex < tagIndex) {
            slug = usesValue.substring(0, repoNameIndex > 0 ? repoNameIndex : tagIndex);
            if (!isAction) {
                tmpName = usesValue.substring(usesValue.lastIndexOf("/") + 1, tagIndex);
            } else {
                tmpSub = repoNameIndex < tagIndex && repoNameIndex > 0 ? "/" + usesValue.substring(repoNameIndex + 1, tagIndex) : "";
                tmpName = usesValue.substring(userNameIndex + 1, tagIndex);
            }
        } else {
            tmpName = usesValue;
        }
        // END [EXTRACT PARTS]

        return new GitHubAction()
                .name(slug != null ? slug : tmpName)
                .usesValue(usesValue)
                .downloadUrl(isLocal ? absolutePath : toRemoteDownloadUrl(isAction, ref, slug, tmpSub, tmpName))
                .githubUrl(isAction ? toGitHubActionUrl(ref, slug, tmpSub) : toGitHubWorkflowUrl(ref, slug, tmpName))
                .expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 14))
                .isLocal(isLocal)
                .setAction(isAction)
                .isSchema(false)
                .isSuppressed(false)
                ;
    }

    public Optional<String> getLocalPath(final Project project) {
        return isLocal() ? ofNullable(project)
                .map(ProjectUtil::guessProjectDir)
                .map(dir -> findActionYaml(usesValue(), dir))
                .map(VirtualFile::getPath) : Optional.empty();
    }

    // !!! Performs Network and File Operations !!!
    //TODO: get Tags for autocompletion
    public synchronized GitHubAction resolve() {
        if (!isResolved() && !isLocal()) {
            extractParameters();
        }
        return this;
    }

    public Map<String, String> freshInputs() {
        if (isLocal()) {
            extractLocalParameters();
        }
        return unmodifiableMap(inputs);
    }

    public Map<String, String> freshOutputs() {
        if (isLocal()) {
            extractLocalParameters();
        }
        return unmodifiableMap(outputs);
    }

    public String name() {
        return metaData.getOrDefault("name", "");
    }

    public GitHubAction name(final String name) {
        ofNullable(name).ifPresent(s -> metaData.put("name", s));
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

    public boolean isSchema() {
        return parseBoolean(metaData.getOrDefault("isSchema", "false"));
    }

    public GitHubAction isSchema(final boolean isSchema) {
        metaData.put("isSchema", Boolean.toString(isSchema));
        return this;
    }

    public boolean isSuppressed() {
        return parseBoolean(metaData.getOrDefault("isSuppressed", "false"));
    }

    public GitHubAction isSuppressed(final boolean isSuppressed) {
        metaData.put("isSuppressed", Boolean.toString(isSuppressed));
        return this;
    }

    public Map<String, String> getInputs() {
        return unmodifiableMap(inputs);
    }

    public GitHubAction setInputs(final Map<String, String> inputs) {
        this.inputs.putAll(inputs);
        return this;
    }

    public Map<String, String> getOutputs() {
        return unmodifiableMap(outputs);
    }

    public GitHubAction setOutputs(final Map<String, String> outputs) {
        this.outputs.putAll(outputs);
        return this;
    }

    public Map<String, String> getMetaData() {
        return unmodifiableMap(metaData);
    }

    public GitHubAction setMetaData(final Map<String, String> metaData) {
        this.metaData.putAll(metaData);
        return this;
    }


    public static VirtualFile findActionYaml(final String subPath, final VirtualFile projectDir) {
        return ofNullable(projectDir.findFileByRelativePath(subPath)).filter(p -> !p.isDirectory())
                .or(() -> ofNullable(projectDir.findFileByRelativePath(subPath + "/action.yml")).filter(p -> !p.isDirectory()))
                .or(() -> ofNullable(projectDir.findFileByRelativePath(subPath + "/action.yaml")).filter(p -> !p.isDirectory()))
                .orElse(null);
    }

    private void extractParameters() {
        try {
            if (isLocal()) {
                extractLocalParameters();
            } else {
                extractRemoteParameters();
            }
        } catch (final Exception e) {
            LOG.warn("Failed to set parameters [" + this.name() + "]", e);
            isResolved(false);
        }
    }

    private void extractRemoteParameters() {
        ofNullable(downloadFileFromGitHub(downloadUrl())).or(() -> ofNullable(downloadContent(downloadUrl()))).ifPresent(this::setParameters);
    }

    private void extractLocalParameters() {
        of(downloadUrl()).map(Paths::get).filter(Files::exists).filter(Files::isRegularFile).map(file -> {
            try {
                return Files.readString(file);
            } catch (final IOException ignored) {
                return null;
            }
        }).ifPresent(this::setParameters);
    }

    private void setParameters(final String content) {
        isResolved(hasText(content));
        readPsiElement(ProjectManager.getInstance().getDefaultProject(), downloadUrl(), content, psiFile -> {
            inputs.clear();
            inputs.putAll(getActionParameters(psiFile, FIELD_INPUTS, isAction()));
            outputs.clear();
            outputs.putAll(getActionParameters(psiFile, FIELD_OUTPUTS, isAction()));
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
        return (ref != null && slug != null && sub != null) ? "https://github.com/" + slug + "/blob/" + ref + sub + "/action.yml" : null;
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
        return getChildWithKey(psiElement.getContainingFile(), fieldName)
                .map(PsiElementHelper::getKvChildren)
                .map(children -> children.stream().collect(Collectors.toMap(YAMLKeyValue::getKeyText, PsiElementHelper::getDescription)))
                .orElseGet(Collections::emptyMap);
    }

    @NotNull
    private static Map<String, String> readWorkflowParameters(final PsiElement psiElement, final String fieldName) {
        return getChildWithKey(psiElement.getContainingFile(), FIELD_ON)
                .flatMap(keyValue -> getChildWithKey(psiElement.getContainingFile(), fieldName))
                .map(PsiElementHelper::getKvChildren)
                .map(children -> children.stream().collect(Collectors.toMap(YAMLKeyValue::getKeyText, PsiElementHelper::getDescription)))
                .orElseGet(Collections::emptyMap);
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
}
