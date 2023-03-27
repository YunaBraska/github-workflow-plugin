package com.github.yunabraska.githubworkflowplugin.completion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class GitHubAction {

    private final Map<String, String> inputs = new ConcurrentHashMap<>();
    private final Map<String, String> outputs = new ConcurrentHashMap<>();
    private final AtomicLong expiration = new AtomicLong(0);
    private final List<String> tags = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> ref = new AtomicReference<>(null);
    private final AtomicReference<String> slug = new AtomicReference<>(null);
    private final AtomicReference<String> actionName = new AtomicReference<>(null);

    public static GitHubAction gitHubActionOf(final String uses) {
        return new GitHubAction(uses);
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

    public AtomicReference<String> actionName() {
        return actionName;
    }

    @SuppressWarnings("java:S2259")
    private void setRemoteSettings(final String action) {
        final int tagIndex = action == null ? -1 : action.indexOf("@");
        final int nameIndex = action == null ? -1 : action.indexOf("/");
        if (action != null && tagIndex != -1 && nameIndex < tagIndex) {
            ref.set(action.substring(tagIndex + 1));
            slug.set(action.substring(0, tagIndex));
            actionName.set(action.substring(nameIndex + 1, tagIndex));
        }
    }

    private String toActionYamlUrl() {
        return (ref.get() != null && slug.get() != null) ? "https://raw.githubusercontent.com/" + slug.get() + "/" + ref.get() + "/action.yml" : null;
    }

    private String toMarketplaceUrl() {
        return (actionName.get() != null && ref.get() != null) ? "https://github.com/marketplace/actions/" + actionName.get() + "/" + ref.get() + "/action.yml" : null;
    }

    private String toGitHubUrl() {
        return (slug.get() != null && ref.get() != null) ? "https://github.com/" + slug.get() + "/tree" + ref.get() : null;
    }

    private void updateActionsYml() {
        //TODO: store inputs and outputs per each ref (lazy loaded)
        //if reachable
        //update expiration date
    }

    private GitHubAction(final String uses) {
        //TODO: validate URLS else null
        setRemoteSettings(uses);
    }
}
