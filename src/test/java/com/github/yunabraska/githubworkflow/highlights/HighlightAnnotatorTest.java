package com.github.yunabraska.githubworkflow.highlights;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

@TestDataPath("\\$CONTENT_ROOT/src/test/data/editor/highlighting")
public class HighlightAnnotatorTest extends BasePlatformTestCase {
    @Override
    protected String getBasePath() {
        return "editor/highlighting";
    }

    public void testHighlighting() {
        myFixture.testHighlighting(true, false, true, "show_case.yml");
    }
}
