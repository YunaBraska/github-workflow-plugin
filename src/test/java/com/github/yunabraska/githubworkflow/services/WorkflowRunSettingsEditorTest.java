package com.github.yunabraska.githubworkflow.services;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunSettingsEditorTest extends EditorFeatureTestCase {

    public void testResetUsesOnlyTheSelectedConfigurationInputs() throws Exception {
        final WorkflowRunSettingsEditor editor = new WorkflowRunSettingsEditor();
        final WorkflowRunConfiguration first = configuration("first").inputsText("old_key=old-value\n");
        final WorkflowRunConfiguration second = configuration("second").inputsText("new_key=new-value\n");

        editor.resetFrom(first);
        editor.applyTo(first);
        editor.resetFrom(second);
        editor.applyTo(second);

        assertThat(second.toRequest().inputs())
                .containsEntry("new_key", "new-value")
                .doesNotContainKey("old_key");
    }

    private WorkflowRunConfiguration configuration(final String name) {
        return new WorkflowRunConfiguration(getProject(), WorkflowRunConfigurationType.getInstance().factory(), name);
    }
}
