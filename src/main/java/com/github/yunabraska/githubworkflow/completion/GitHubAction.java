package com.github.yunabraska.githubworkflow.completion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.ACTION_CACHE;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.CACHE_TEN_MINUTES;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadAction;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.orEmpty;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.hasText;
import static java.util.Optional.ofNullable;

public class GitHubAction {

    private final Map<String, String> inputs = new ConcurrentHashMap<>();
    private final Map<String, String> outputs = new ConcurrentHashMap<>();
    private final AtomicLong expiration = new AtomicLong(0);
    //TODO: get Tags for autocompletion
    private final List<String> tags = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> ref = new AtomicReference<>(null);
    private final AtomicReference<String> slug = new AtomicReference<>(null);
    private final AtomicReference<String> sub = new AtomicReference<>(null);
    private final AtomicReference<String> actionName = new AtomicReference<>(null);
    private final AtomicBoolean isAvailable = new AtomicBoolean(false);

    public static GitHubAction getGitHubAction(final String uses) {
        try {
            GitHubAction gitHubAction = ACTION_CACHE.getOrDefault(uses, null);
            if (gitHubAction == null || gitHubAction.expiration() < System.currentTimeMillis()) {
                gitHubAction = new GitHubAction(uses);
                ACTION_CACHE.put(uses, gitHubAction);
            }
            return gitHubAction;
        } catch (Exception e) {
            return new GitHubAction(null);
        }
    }

    public Map<String, String> inputs() {
        return inputs;
    }

    public Map<String, String> outputs() {
        return outputs;
    }

    public long expiration() {
        return expiration.get();
    }

    public List<String> tags() {
        return tags;
    }

    public String ref() {
        return ref.get();
    }

    public String slug() {
        return slug.get();
    }

    public String actionName() {
        return actionName.get();
    }

    public String sub() {
        return sub.get();
    }


    public boolean isAvailable() {
        return isAvailable.get();
    }

    private String toActionYamlUrl() {
        return (ref.get() != null && slug.get() != null && sub.get() != null) ? "https://raw.githubusercontent.com/" + slug.get() + "/" + ref.get() + sub.get() + "/action.yml" : null;
    }

    private String toWorkflowYamlUrl() {
        return (ref.get() != null && slug.get() != null) ? "https://raw.githubusercontent.com/" + slug.get() + "/" + ref.get() + "/.github/workflows/" + actionName : null;
    }

    private String toMarketplaceUrl() {
        return (actionName.get() != null && ref.get() != null) ? "https://github.com/marketplace/actions/" + actionName.get() + "/" + ref.get() + "/action.yml" : null;
    }

    private String toGitHubUrl() {
        return (slug.get() != null && ref.get() != null) ? "https://github.com/" + slug.get() + "/tree" + ref.get() : null;
    }

    private GitHubAction(final String uses) {
        if (uses != null) {
            final int tagIndex = uses.indexOf("@");
            final int userNameIndex = uses.indexOf("/");
            final int repoNameIndex = uses.indexOf("/", userNameIndex + 1);

            if (tagIndex != -1 && userNameIndex < tagIndex) {
                ref.set(uses.substring(tagIndex + 1));
                slug.set(uses.substring(0, repoNameIndex > 0 ? repoNameIndex : tagIndex));
                if (uses.contains(".yaml") || uses.contains(".yml")) {
                    actionName.set(uses.substring(uses.lastIndexOf("/") + 1, tagIndex));
                    setActionParameters(toWorkflowYamlUrl(), false);
                } else {
                    sub.set(repoNameIndex < tagIndex && repoNameIndex > 0 ? "/" + uses.substring(repoNameIndex + 1, tagIndex) : "");
                    actionName.set(uses.substring(userNameIndex + 1, tagIndex));
                    setActionParameters(toActionYamlUrl(), true);
                }
            }
        }
    }

    private void setActionParameters(final String downloadUrl, final boolean isAction) {
        try {
            extractActionParameters(downloadAction(downloadUrl, this), isAction);
        } catch (Exception e) {
            isAvailable.set(false);
            expiration.set(System.currentTimeMillis() + CACHE_TEN_MINUTES);
        }
    }

    private void extractActionParameters(final String content, final boolean isAction) {
        isAvailable.set(hasText(content));
        expiration.set(System.currentTimeMillis() + (hasText(content) ? CACHE_ONE_DAY : CACHE_TEN_MINUTES));
        final WorkflowFile workflowFile = WorkflowFile.workflowFileOf(actionName() + "_" + ref(), content);
        inputs.putAll(getActionParameters(workflowFile, FIELD_INPUTS, isAction));
        outputs.putAll(getActionParameters(workflowFile, FIELD_OUTPUTS, isAction));
    }


    private Map<String, String> getActionParameters(final WorkflowFile workflowFile, final String node, final boolean action) {
        return workflowFile.nodesToMap(
                node, n -> action || (ofNullable(n.parent()).map(YamlNode::parent).map(YamlNode::parent).filter(parent -> "on".equals(parent.name) || "true".equals(parent.name)).isPresent()),
                n -> orEmpty(n.name()),
                GitHubWorkflowUtils::getDescription
        );
    }
}
