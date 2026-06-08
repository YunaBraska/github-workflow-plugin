package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.services.WorkflowYaml;
import com.github.yunabraska.githubworkflow.services.WorkflowPsi;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Run configuration that dispatches a workflow_dispatch event and follows the resulting run.
 */
public final class WorkflowRunConfiguration extends RunConfigurationBase<Object> {

    private String apiUrl = "https://api.github.com";
    private String owner = "";
    private String repo = "";
    private String workflowPath = "";
    private String ref = "main";
    private String tokenEnvVar = "";
    private String inputsText = "";

    WorkflowRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new Editor();
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment environment) {
        return new CommandLineState(environment) {
            @Override
            protected @NotNull ProcessHandler startProcess() throws ExecutionException {
                return new WorkflowRunProcessHandler(getProject(), toRequest(), new WorkflowRun(getProject()), environment.getExecutor());
            }
        };
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (isBlank(apiUrl)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.apiUrl"));
        }
        if (isBlank(owner) || isBlank(repo)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.repository"));
        }
        if (isBlank(workflowPath)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.workflow"));
        }
        if (isBlank(ref)) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.ref"));
        }
        if (WorkflowRun.DispatchInputs.parseKeyValueText(inputsText).size() > 25) {
            throw new RuntimeConfigurationError(GitHubWorkflowBundle.message("workflow.run.error.inputs"));
        }
    }

    @Override
    public void readExternal(@NotNull final Element element) throws InvalidDataException {
        super.readExternal(element);
        apiUrl = value(element, "apiUrl", apiUrl);
        owner = value(element, "owner", owner);
        repo = value(element, "repo", repo);
        workflowPath = value(element, "workflowPath", workflowPath);
        ref = value(element, "ref", ref);
        tokenEnvVar = value(element, "tokenEnvVar", tokenEnvVar);
        inputsText = value(element, "inputsText", inputsText);
    }

    @Override
    public void writeExternal(@NotNull final Element element) {
        super.writeExternal(element);
        element.setAttribute("apiUrl", apiUrl);
        element.setAttribute("owner", owner);
        element.setAttribute("repo", repo);
        element.setAttribute("workflowPath", workflowPath);
        element.setAttribute("ref", ref);
        element.setAttribute("tokenEnvVar", tokenEnvVar);
        element.setAttribute("inputsText", inputsText);
    }

    WorkflowRun.Request toRequest() {
        final Map<String, String> inputs = WorkflowRun.DispatchInputs.parseKeyValueText(inputsText);
        return new WorkflowRun.Request(apiUrl, owner, repo, workflowPath, ref, inputs, tokenEnvVar);
    }

    public String apiUrl() {
        return apiUrl;
    }

    public WorkflowRunConfiguration apiUrl(final String apiUrl) {
        this.apiUrl = clean(apiUrl);
        return this;
    }

    public String owner() {
        return owner;
    }

    public WorkflowRunConfiguration owner(final String owner) {
        this.owner = clean(owner);
        return this;
    }

    public String repo() {
        return repo;
    }

    public WorkflowRunConfiguration repo(final String repo) {
        this.repo = clean(repo);
        return this;
    }

    public String workflowPath() {
        return workflowPath;
    }

    public WorkflowRunConfiguration workflowPath(final String workflowPath) {
        this.workflowPath = clean(workflowPath);
        return this;
    }

    public String ref() {
        return ref;
    }

    public WorkflowRunConfiguration ref(final String ref) {
        this.ref = clean(ref);
        return this;
    }

    public String tokenEnvVar() {
        return tokenEnvVar;
    }

    public WorkflowRunConfiguration tokenEnvVar(final String tokenEnvVar) {
        this.tokenEnvVar = clean(tokenEnvVar);
        return this;
    }

    public String inputsText() {
        return inputsText;
    }

    public WorkflowRunConfiguration inputsText(final String inputsText) {
        this.inputsText = inputsText == null ? "" : inputsText;
        return this;
    }

    private static String value(final Element element, final String name, final String fallback) {
        final String value = element.getAttributeValue(name);
        return value == null ? fallback : value;
    }

    private static String clean(final String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    public static final class Editor extends SettingsEditor<WorkflowRunConfiguration> {

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

        public Editor() {
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
            for (final Map.Entry<String, String> entry : WorkflowRun.DispatchInputs.parseKeyValueText(configuration.inputsText()).entrySet()) {
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

    public static final class Producer extends LazyRunConfigurationProducer<WorkflowRunConfiguration> {

        private static final WorkflowRun.DispatchInputs DISPATCH_INPUTS = new WorkflowRun.DispatchInputs();

        @Override
        public @NotNull ConfigurationFactory getConfigurationFactory() {
            return Type.getInstance().factory();
        }

        @Override
        protected boolean setupConfigurationFromContext(
                @NotNull final WorkflowRunConfiguration configuration,
                @NotNull final ConfigurationContext context,
                @NotNull final Ref<PsiElement> sourceElement
        ) {
            final PsiFile file = workflowFile(context.getPsiLocation()).orElse(null);
            if (file == null) {
                return false;
            }
            final Project project = context.getProject();
            final WorkflowLocation.RepositoryResolver repositoryResolver = new WorkflowLocation.RepositoryResolver();
            final WorkflowLocation.Repository repository = repositoryResolver.resolve(project, file.getVirtualFile()).orElse(null);
            if (repository == null) {
                return false;
            }
            final String path = workflowPath(project, file.getVirtualFile()).orElse(file.getName());
            configuration.setName(GitHubWorkflowBundle.message("workflow.run.configuration.name", file.getName()));
            configuration.apiUrl(repository.apiUrl())
                    .owner(repository.owner())
                    .repo(repository.repo())
                    .workflowPath(path)
                    .ref(repositoryResolver.branch(project, file.getVirtualFile()).orElse("main"))
                    .tokenEnvVar("")
                    .inputsText(DISPATCH_INPUTS.defaultsText(file.getText()));
            sourceElement.set(file);
            return true;
        }

        @Override
        public boolean isConfigurationFromContext(
                @NotNull final WorkflowRunConfiguration configuration,
                @NotNull final ConfigurationContext context
        ) {
            return workflowFile(context.getPsiLocation())
                    .flatMap(file -> workflowPath(context.getProject(), file.getVirtualFile()))
                    .filter(path -> path.equals(configuration.workflowPath()))
                    .filter(path -> new WorkflowLocation.RepositoryResolver().branch(context.getProject())
                            .map(branch -> branch.equals(configuration.ref()))
                            .orElse(true))
                    .isPresent();
        }

        private static Optional<PsiFile> workflowFile(final PsiElement element) {
            return Optional.ofNullable(element)
                    .map(PsiElement::getContainingFile)
                    .filter(file -> Optional.ofNullable(file.getVirtualFile())
                            .flatMap(WorkflowPsi::toPath)
                            .filter(WorkflowYaml::isWorkflowPath)
                            .isPresent());
        }

        static Optional<String> workflowPath(final Project project, final VirtualFile file) {
            return Optional.ofNullable(project)
                    .flatMap(p -> Optional.ofNullable(com.intellij.openapi.project.ProjectUtil.guessProjectDir(p)))
                    .map(VirtualFile::getPath)
                    .map(Path::of)
                    .flatMap(root -> Optional.ofNullable(file)
                            .map(VirtualFile::getPath)
                            .map(Path::of)
                            .map(root::relativize)
                            .map(Path::toString)
                            .map(path -> path.replace('\\', '/')));
        }
    }

    public static final class Type implements ConfigurationType {

        public static final String ID = "GitHubWorkflow.RunConfiguration";

        private final ConfigurationFactory factory = new Factory(this);

        public static Type getInstance() {
            return ConfigurationTypeUtil.findConfigurationType(Type.class);
        }

        @Override
        public String getDisplayName() {
            return GitHubWorkflowBundle.message("workflow.run.configuration.display");
        }

        @Override
        public String getConfigurationTypeDescription() {
            return GitHubWorkflowBundle.message("workflow.run.configuration.description");
        }

        @Override
        public Icon getIcon() {
            return AllIcons.Actions.Execute;
        }

        @Override
        public @NotNull String getId() {
            return ID;
        }

        @Override
        public ConfigurationFactory[] getConfigurationFactories() {
            return new ConfigurationFactory[]{factory};
        }

        public ConfigurationFactory factory() {
            return factory;
        }

        private static final class Factory extends ConfigurationFactory {
            private Factory(final ConfigurationType type) {
                super(type);
            }

            @Override
            public @NotNull String getId() {
                return ID + ".Factory";
            }

            @Override
            public RunConfiguration createTemplateConfiguration(@NotNull final Project project) {
                return new WorkflowRunConfiguration(project, this, GitHubWorkflowBundle.message("workflow.run.configuration.display"));
            }
        }
    }
}
