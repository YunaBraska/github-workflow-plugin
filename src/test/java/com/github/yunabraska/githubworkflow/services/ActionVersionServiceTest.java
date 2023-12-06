package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Before;
import org.junit.Test;

public class ActionVersionServiceTest extends BasePlatformTestCase {
    ActionVersionService actionVersionService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        var token = System.getenv("GITHUB_TOKEN");
        assertNotNull(token);

        Project project = getProject();
        actionVersionService = new ActionVersionService(project, token);
    }

    @Test
    public void testGetLatestActionVersion() {
        final String latestVersion = actionVersionService.getLatestActionVersion("actions/setup-java");
        assertEquals("v4.0.0", latestVersion);
    }

    @Test
    public void testIsVersionOutdated() {
        assertTrue(actionVersionService.isActionOutdated("actions/setup-java", "v3.0.0"));
        assertFalse(actionVersionService.isActionOutdated("actions/setup-java", "v4.5.2"));
    }
}
