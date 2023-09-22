package com.github.yunabraska.githubworkflow.highlights;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;

public class HighlightAnnotatorTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.copyDirectoryToProject("src/test/resources/GHA_WORKFLOW_TEST", ".");
        myFixture.setTestDataPath(getTestDataPath());
    }

    public void testHighlighting() {
        final String pathToTestFile = ".github/workflows/show_case.yml";
        myFixture.configureFromTempProjectFile(pathToTestFile);
        myFixture.checkHighlighting(true, false, true);
    }

    @Override
    protected String getTestDataPath() {
        return Path.of(System.getProperty("user.dir"), "src/test/resources/GHA_WORKFLOW_TEST").toString();
    }
}
