package com.github.yunabraska.githubworkflow.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service(Service.Level.PROJECT)
final public class ActionVersionService {
    private static final Logger LOG = Logger.getInstance(ActionVersionService.class);
    private static final Map<String, String> CACHE = new HashMap<>();
    private final String serverPath;
    private final GithubApiRequestExecutor requestExecutor;

    public ActionVersionService(final Project project, final String token) {
        this.requestExecutor = createRequestExecutor(project, token);
        this.serverPath = determineServerPath(project);
    }

    public ActionVersionService(final Project project) {
        this(project, getGitHubToken(project));
    }

    private String determineServerPath(final Project project) {
        var repoManager = project.getService(GHHostedRepositoriesManager.class);
        final Set<GHGitRepositoryMapping> mappings = repoManager.getKnownRepositoriesState().getValue();
        if (mappings.isEmpty()) {
            LOG.info("No repository mappings, using default graphql url");
            return "https://api.github.com/graphql";
        } else {
            final GHGitRepositoryMapping mapping = mappings.iterator().next();
            return mapping.getRepository().getServerPath().toGraphQLUrl();
        }
    }

    private GithubApiRequestExecutor createRequestExecutor(final Project project, final String token) {
        return GithubApiRequestExecutor.Factory.getInstance().create(token);
    }

    private static String getGitHubToken(final Project project) {
        var gitHubAccounts = GHAccountsUtil.getAccounts();
        for (final GithubAccount account : gitHubAccounts) {
            var token = GHCompatibilityUtil.getOrRequestToken(account, project);
            if (token != null) {
                return token;
            }
        }
        LOG.warn("No GitHub account found");
        throw new RuntimeException("No GitHub account found");
    }

    /**
     * Gets latest version of action from GitHub
     *
     * @param fullActionName action name in format org/name
     * @return latest version of action, null if not found or exception occurred
     */
    String getLatestActionVersion(final String fullActionName) {
        if (fullActionName == null || fullActionName.startsWith("./")) {
            return null; // Local actions don't have versions
        }
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
        try {
            final JsonNode response = requestExecutor.execute(request);
            final String version = response.toString().replace("\"", "");
            CACHE.put(actionName, version);
            return version;
        } catch (IOException e) {
            LOG.warn("Failed to get latest version of action " + fullActionName, e);
        }
        return null;
    }

    /**
     * Checks if action current version is outdated
     *
     * @param actionName     action name
     * @param currentVersion current version
     * @return true if action is outdated
     */
    Boolean isActionOutdated(String actionName, String currentVersion) {
        String latestVersion = getLatestActionVersion(actionName);
        if (latestVersion == null) {
            return false;
        }
        if (latestVersion.startsWith("v")) {
            latestVersion = latestVersion.substring(1);
        }
        if (currentVersion.startsWith("v")) {
            currentVersion = currentVersion.substring(1);
        }
        // Comparing only major versions
        LOG.info(String.format("Comparing %s versions: %s and %s", actionName, latestVersion, currentVersion));
        final String majorLatest = latestVersion.split("\\.")[0];
        final String majorCurrent = currentVersion.split("\\.")[0];
        return !majorLatest.equals(majorCurrent);
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
