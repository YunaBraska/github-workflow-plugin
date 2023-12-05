package com.github.yunabraska.githubworkflow.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class ActionVersionHandler {
    private static final Map<String, String> CACHE = new HashMap<>();
    private final Project project;
    private final String serverPath;
    private final GithubApiRequestExecutor requestExecutor =
        GithubApiRequestExecutor.Factory.getInstance().create("token");// todo token

    public ActionVersionHandler(final Project project) {
        this.project = project;
        var repoManager = project.getService(GHHostedRepositoriesManager.class);
        final Set<GHGitRepositoryMapping> mappings = repoManager.getKnownRepositoriesState().getValue();
        if (mappings.isEmpty()) {
            this.serverPath = "https://api.github.com/graphql";
        } else {
            final GHGitRepositoryMapping mapping = mappings.iterator().next();
            this.serverPath = mapping.getRepository().getServerPath().toGraphQLUrl();
        }
    }

    /**
     * Gets latest version of action from GitHub
     *
     * @param fullActionName action name in format org/name
     * @return latest version of action
     * @throws IOException if request to GitHub fails
     */
    String getLatestActionVersion(final String fullActionName) throws IOException {
        if (CACHE.containsKey(fullActionName)) {
            return CACHE.get(fullActionName);
        }
        final String actionOrg = fullActionName.split("/")[0];
        final String actionName = fullActionName.split("/")[1];
        final GithubApiRequest.Post.GQLQuery<JsonNode> request = new GithubApiRequest.Post.GQLQuery.TraversedParsed<>(
            serverPath, "getLatestRelease.graphql",
            Map.of("owner", actionOrg, "name", actionName),
            JsonNode.class,
            "repository", "latestRelease", "tag", "name");
        final JsonNode response = requestExecutor.execute(request);
        final String version = response.toString().replace("\"", "");
        CACHE.put(actionName, version);
        return version;
    }

    /**
     * Checks if action current version is outdated
     *
     * @param actionName     action name
     * @param currentVersion current version
     * @return true if action is outdated
     */
    Boolean isActionOutdated(String actionName, String currentVersion) throws IOException {
        final String latestVersion = getLatestActionVersion(actionName);
        //todo compare versions
        return !latestVersion.equals(currentVersion);
    }

    /**
     * Extracts all actions from workflow yaml
     *
     * @param workflowYaml yaml text
     * @return map of action name and version used
     */
    Map<String, String> getAllActionsWithCurrentVersions(String workflowYaml) {
        // TODO
        return new HashMap<>();
    }

    /**
     * Extracts all outdated actions from workflow yaml
     *
     * @param workflowYaml yaml text
     * @return map of action name and latest version
     */
    Map<String, String> getOutdatedActions(final String workflowYaml) {
        final Map<String, String> allActions = getAllActionsWithCurrentVersions(workflowYaml);
        //TODO
        return null;
    }
}
