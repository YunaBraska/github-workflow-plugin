package com.github.yunabraska.githubworkflow;

import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;

import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("unused")
public class PluginExecutionPolicy extends IdeaTestExecutionPolicy {
    @Override
    protected String getName() {
        return "GitHub Workflow";
    }

    @Override
    public String getHomePath() {
        var homePath = System.getProperty("PLUGIN_HOME_PATH");
        assert Files.isDirectory(Path.of(homePath));
        return homePath;
    }
}
