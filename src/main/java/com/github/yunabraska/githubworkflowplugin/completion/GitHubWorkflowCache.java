package com.github.yunabraska.githubworkflowplugin.completion;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.yunabraska.githubworkflowplugin.completion.GitHubWorkFlowUtils.downloadContent;

public class GitHubWorkflowCache {
    private static final Map<String, CacheEntry> inputCache = new ConcurrentHashMap<>();
    private static final Map<String, GitHubAction> ACTION_CACHE = new ConcurrentHashMap<>();

    private static final long CACHE_ONE_DAY = 24L * 60 * 60 * 1000;

    //TODO:
    // trigger read file on any autocompletion
    // trigger update action XY:
    //      ref
    //      steps.id_to_action
    // steps.id triggers read all actions

    private static class CacheEntry {
        Map<String, String> inputs;
        LocalDateTime expiration;

        CacheEntry(final Map<String, String> inputs, final LocalDateTime expiration) {
            this.inputs = inputs;
            this.expiration = expiration;
        }
    }

    public static Map<String, String> getActionInputs(final String actionUrl) {
        CacheEntry cacheEntry = inputCache.get(actionUrl);
        if (cacheEntry == null || cacheEntry.expiration.isBefore(LocalDateTime.now())) {
            try {
                final String content = downloadContent(actionUrl);
                final Map<String, String> inputs = parseActionYaml(content);
                cacheEntry = new CacheEntry(inputs, LocalDateTime.now().plusDays(1));
                inputCache.put(actionUrl, cacheEntry);
            } catch (IOException e) {
                // Handle download or parsing exception
                e.printStackTrace();
            }
        }
        return cacheEntry.inputs;
    }

    private static Map<String, String> parseActionYaml(final String content) {
        final Yaml yaml = new Yaml();
        final Map<String, Object> yamlMap = yaml.load(content);
        final Map<String, Map<String, Object>> inputs = (Map<String, Map<String, Object>>) yamlMap.get("inputs");

        if (inputs == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> inputDescriptions = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : inputs.entrySet()) {
            final String inputName = entry.getKey();
            final Map<String, Object> inputProperties = entry.getValue();
            final String inputDescription = (String) inputProperties.get("description");
            inputDescriptions.put(inputName, inputDescription);
        }

        return inputDescriptions;
    }
}
