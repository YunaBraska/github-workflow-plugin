package com.github.yunabraska.githubworkflow.services;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

@TestDataPath("\\$CONTENT_ROOT/src/test/resources/testdata")
public class HighlightAnnotatorTest extends BasePlatformTestCase {
    @Override
    protected String getBasePath() {
        return "testdata";
    }

    public void testHighlighting() {
        myFixture.testHighlighting(true, false, true, "show_case.yml");
//        myFixture.testHighlighting(true, false, true, "local_references.yml");
        myFixture.testHighlighting(true, false, true, "issue_10.yml");
        myFixture.testHighlighting(true, false, true, "issue_24.yml");
        myFixture.testHighlighting(true, false, true, "issue_25.yml");
    }
}
