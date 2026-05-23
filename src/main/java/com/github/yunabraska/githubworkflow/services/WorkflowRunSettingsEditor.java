package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.Objects;

/**
 * Plain Swing settings editor for GitHub Workflow run configurations.
 */
public final class WorkflowRunSettingsEditor extends SettingsEditor<WorkflowRunConfiguration> {

    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private final JTextField apiUrl = new JTextField();
    private final JTextField owner = new JTextField();
    private final JTextField repo = new JTextField();
    private final JTextField workflowPath = new JTextField();
    private final JTextField ref = new JTextField();
    private final JTextField tokenEnvVar = new JTextField();
    private final JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
    private final DefaultTableModel inputsModel = new DefaultTableModel(new Object[][]{}, new Object[]{
            GitHubWorkflowBundle.message("documentation.name.label"),
            GitHubWorkflowBundle.message("documentation.value.label")
    }) {
        @Override
        public boolean isCellEditable(final int row, final int column) {
            return true;
        }
    };
    private final JBTable inputsTable = new JBTable(inputsModel);

    public WorkflowRunSettingsEditor() {
        final JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        addRow(fields, 0, GitHubWorkflowBundle.message("workflow.run.field.apiUrl"), apiUrl);
        addRow(fields, 1, GitHubWorkflowBundle.message("workflow.run.field.owner"), owner);
        addRow(fields, 2, GitHubWorkflowBundle.message("workflow.run.field.repo"), repo);
        addRow(fields, 3, GitHubWorkflowBundle.message("workflow.run.field.workflow"), workflowPath);
        addRow(fields, 4, GitHubWorkflowBundle.message("workflow.run.field.ref"), ref);
        addRow(fields, 5, GitHubWorkflowBundle.message("workflow.run.field.tokenEnv"), tokenEnvVar);
        panel.add(fields, BorderLayout.NORTH);

        inputsTable.setFillsViewportHeight(true);
        inputPanel.setBorder(BorderFactory.createTitledBorder(GitHubWorkflowBundle.message("workflow.run.inputs.title")));
        inputPanel.add(ToolbarDecorator.createDecorator(inputsTable)
                .setAddAction(button -> addInputRow("", ""))
                .setRemoveAction(button -> removeSelectedInputRows())
                .disableUpDownActions()
                .createPanel(), BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.CENTER);
    }

    @Override
    protected void resetEditorFrom(@NotNull final WorkflowRunConfiguration configuration) {
        apiUrl.setText(configuration.apiUrl());
        owner.setText(configuration.owner());
        repo.setText(configuration.repo());
        workflowPath.setText(configuration.workflowPath());
        ref.setText(configuration.ref());
        tokenEnvVar.setText(configuration.tokenEnvVar());
        resetInputs(configuration);
    }

    @Override
    protected void applyEditorTo(@NotNull final WorkflowRunConfiguration configuration) throws ConfigurationException {
        configuration.apiUrl(apiUrl.getText())
                .owner(owner.getText())
                .repo(repo.getText())
                .workflowPath(workflowPath.getText())
                .ref(ref.getText())
                .tokenEnvVar(tokenEnvVar.getText())
                .inputsText(inputsText());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return panel;
    }

    private static void addRow(final JPanel panel, final int row, final String label, final JTextField field) {
        final GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(2, 0, 2, 8);
        panel.add(new JLabel(label), labelConstraints);

        final GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(2, 0, 2, 0);
        panel.add(field, fieldConstraints);
    }

    private void resetInputs(final WorkflowRunConfiguration configuration) {
        inputsModel.setRowCount(0);
        for (final Map.Entry<String, String> entry : WorkflowDispatchInputs.parseKeyValueText(configuration.inputsText()).entrySet()) {
            addInputRow(entry.getKey(), entry.getValue());
        }
    }

    private void addInputRow(final String key, final String value) {
        inputsModel.addRow(new Object[]{key, value});
    }

    private void removeSelectedInputRows() {
        final int[] selectedRows = inputsTable.getSelectedRows();
        for (int index = selectedRows.length - 1; index >= 0; index--) {
            inputsModel.removeRow(inputsTable.convertRowIndexToModel(selectedRows[index]));
        }
    }

    private String inputsText() {
        if (inputsTable.isEditing() && inputsTable.getCellEditor() != null) {
            inputsTable.getCellEditor().stopCellEditing();
        }
        final StringBuilder result = new StringBuilder();
        for (int row = 0; row < inputsModel.getRowCount(); row++) {
            final String key = Objects.toString(inputsModel.getValueAt(row, 0), "").trim();
            if (!key.isBlank()) {
                final String value = Objects.toString(inputsModel.getValueAt(row, 1), "");
                result.append(key).append("=").append(value).append("\n");
            }
        }
        return result.toString();
    }
}
