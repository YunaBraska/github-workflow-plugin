package com.github.yunabraska.githubworkflow.helper;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import java.io.IOException;

public class ActionVersionHandlerTest extends BasePlatformTestCase {
    @Test
    public void testGetLatestActionVersion() {
        Project project = getProject();
        ActionVersionHandler handler = new ActionVersionHandler(project);
        try {
            handler.getLatestActionVersion("actions/setup-java");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
