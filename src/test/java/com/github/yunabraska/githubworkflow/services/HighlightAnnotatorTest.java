package com.github.yunabraska.githubworkflow.services;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

@TestDataPath("\\$CONTENT_ROOT/src/test/resources/testdata/.github")
public class HighlightAnnotatorTest extends BasePlatformTestCase {
    @Override
    protected String getBasePath() {
        return "testdata/.github";
    }

    public void testHighlighting() {
        // Not testable as it relies on valid project files
        // myFixture.configureByText("action.yml", "my_action/action.yml");
        // myFixture.configureByFile("local_references.yml");
        // System.out.println("!!!! TestDwataPath" + LocalFileSystem.getInstance().findFileByIoFile(new File(myFixture.getTestDataPath() + "/local_references.yml")));
        // myFixture.testHighlighting(true, false, true, "local_references.yml");
        // final List<IntentionAction> availableIntentions = myFixture.getAvailableIntentions();
        // System.out.println("!!!! AvailableIntentions" + availableIntentions.size());

        myFixture.testHighlighting(true, false, true, "show_case.yml");
        myFixture.testHighlighting(true, false, true, "issue_10.yml");
        myFixture.testHighlighting(true, false, true, "issue_24.yml");
        myFixture.testHighlighting(true, false, true, "issue_25.yml");
    }
}
