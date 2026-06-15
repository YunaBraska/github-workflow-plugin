package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Settings UI for Gitea accounts used by workflow metadata and run requests.
 */
public class GiteaSettingsConfigurable implements SearchableConfigurable {

    private static final String STORED_TOKEN = "********";
    private static final int ENABLED = 0;
    private static final int NAME = 1;
    private static final int WEB_URL = 2;
    private static final int API_URL = 3;
    private static final int TOKEN_ENV = 4;
    private static final int TOKEN = 5;
    private static final int ROW_ID = 6;

    private final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance();
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            return columnIndex == ENABLED ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(final int row, final int column) {
            return column != TOKEN && column != ROW_ID;
        }
    };
    private final JTable table = new JBTable(model);
    private final Map<Integer, String> pendingTokens = new HashMap<>();
    private final Set<Integer> clearedTokens = new HashSet<>();
    private final Set<Integer> storedTokenRows = new HashSet<>();
    private final List<LocalizedButton> buttons = new ArrayList<>();
    private int nextRowSequence = 1;
    private @Nullable JPanel panel;
    private @Nullable TitledBorder border;

    /**
     * Returns the stable settings identifier used by IntelliJ Settings search.
     *
     * @return the Gitea settings configurable id
     */
    @Override
    public @NotNull String getId() {
        return "github.workflow.gitea.settings";
    }

    /**
     * Returns the localized Gitea settings page title.
     *
     * @return localized display name
     */
    @Override
    public @Nls String getDisplayName() {
        return GitHubWorkflowBundle.message("settings.gitea.displayName");
    }

    /**
     * Builds the Gitea account editor.
     *
     * @return settings component
     */
    @Override
    public @Nullable JComponent createComponent() {
        buttons.clear();
        panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(accountsPanel(), BorderLayout.CENTER);
        reset();
        return panel;
    }

    /**
     * Reports whether table rows or pending token edits differ from persisted settings.
     *
     * @return {@code true} when user edits are pending
     */
    @Override
    public boolean isModified() {
        return invalidRowCount() > 0
                || !Objects.equals(serversFromTable(), settings.customServers().stream()
                        .filter(RemoteActionProviders.Server::isGitea)
                        .toList())
                || tokenModified();
    }

    /**
     * Stores Gitea account rows and tokens in IDE settings and Password Safe.
     */
    @Override
    public void apply() {
        if (invalidRowCount() > 0) {
            Messages.showErrorDialog(panel, GitHubWorkflowBundle.message("settings.gitea.invalidRows"), getDisplayName());
            return;
        }
        saveRows();
        refreshTexts();
        GitHubActionCache.triggerSyntaxHighlightingForActiveFiles();
    }

    /**
     * Reloads persisted Gitea account rows and refreshes localized labels.
     */
    @Override
    public void reset() {
        reloadTable();
        refreshTexts();
    }

    /**
     * Releases Swing component references.
     */
    @Override
    public void disposeUIResources() {
        panel = null;
        border = null;
    }

    private JPanel accountsPanel() {
        setColumnHeaders();
        table.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final JPanel result = new JPanel(new BorderLayout(4, 4));
        border = BorderFactory.createTitledBorder(GitHubWorkflowBundle.message("settings.gitea.title"));
        result.setBorder(border);
        result.add(new JScrollPane(table), BorderLayout.CENTER);
        result.add(buttonsPanel(), BorderLayout.SOUTH);
        return result;
    }

    private JPanel buttonsPanel() {
        final JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addButton(result, "settings.gitea.add", this::addRow);
        addButton(result, "settings.gitea.remove", this::removeSelectedRows);
        addButton(result, "settings.gitea.setToken", this::setSelectedToken);
        addButton(result, "settings.gitea.clearToken", this::clearSelectedToken);
        return result;
    }

    private void addButton(final JPanel panel, final String key, final Runnable action) {
        final JButton button = new JButton(GitHubWorkflowBundle.message(key));
        button.addActionListener(event -> action.run());
        buttons.add(new LocalizedButton(button, key));
        panel.add(button);
    }

    private void reloadTable() {
        pendingTokens.clear();
        clearedTokens.clear();
        storedTokenRows.clear();
        model.setRowCount(0);
        settings.customServers().stream()
                .filter(RemoteActionProviders.Server::isGitea)
                .forEach(this::addPersistedRow);
    }

    private void addPersistedRow(final RemoteActionProviders.Server server) {
        final int rowId = nextRowId();
        if (server.tokenStored) {
            storedTokenRows.add(rowId);
        }
        model.addRow(new Object[]{
                server.enabled,
                server.name,
                server.webUrl,
                server.apiUrl,
                server.tokenEnvVar,
                server.tokenStored ? STORED_TOKEN : "",
                rowId
        });
    }

    private void addRow() {
        model.addRow(new Object[]{
                true,
                "Gitea",
                "https://gitea.com",
                "https://gitea.com/api/v1",
                "GITEA_TOKEN",
                "",
                nextRowId()
        });
    }

    private void removeSelectedRows() {
        final int[] rows = table.getSelectedRows();
        for (int index = rows.length - 1; index >= 0; index--) {
            model.removeRow(table.convertRowIndexToModel(rows[index]));
        }
    }

    private void setSelectedToken() {
        final int row = selectedRow();
        if (row < 0) {
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.gitea.noneSelected"), getDisplayName());
            return;
        }
        final String token = Messages.showPasswordDialog(GitHubWorkflowBundle.message("settings.gitea.token.prompt"), getDisplayName());
        if (token != null && !token.isBlank()) {
            final int rowId = rowId(row);
            pendingTokens.put(rowId, token);
            clearedTokens.remove(rowId);
            storedTokenRows.add(rowId);
            model.setValueAt(STORED_TOKEN, row, TOKEN);
        }
    }

    private void clearSelectedToken() {
        final int row = selectedRow();
        if (row < 0) {
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.gitea.noneSelected"), getDisplayName());
            return;
        }
        final int rowId = rowId(row);
        pendingTokens.remove(rowId);
        clearedTokens.add(rowId);
        storedTokenRows.remove(rowId);
        model.setValueAt("", row, TOKEN);
    }

    private int selectedRow() {
        final int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
    }

    private void saveRows() {
        final List<RemoteActionProviders.Server> previous = settings.customServers();
        final List<RemoteActionProviders.Server> servers = serversFromTable();
        final List<String> nextKeys = servers.stream().map(server -> server.apiUrl).toList();
        final List<RemoteActionProviders.Server> persisted = new ArrayList<>(previous.stream()
                .filter(server -> !server.isGitea())
                .toList());
        persisted.addAll(servers);

        previous.stream()
                .filter(RemoteActionProviders.Server::isGitea)
                .filter(server -> !nextKeys.contains(server.apiUrl))
                .forEach(settings::clearGiteaToken);

        for (int row = 0; row < model.getRowCount(); row++) {
            final RemoteActionProviders.Server server = serverFromRow(row);
            if (!server.isValid()) {
                continue;
            }
            final int rowId = rowId(row);
            if (pendingTokens.containsKey(rowId)) {
                settings.setGiteaToken(server, pendingTokens.get(rowId));
            } else if (clearedTokens.contains(rowId)) {
                settings.clearGiteaToken(server);
            }
        }
        settings.setCustomServers(persisted);
        reloadTable();
    }

    private boolean tokenModified() {
        return !pendingTokens.isEmpty() || !clearedTokens.isEmpty();
    }

    private int invalidRowCount() {
        int result = 0;
        for (int row = 0; row < model.getRowCount(); row++) {
            if (!serverFromRow(row).isValid()) {
                result++;
            }
        }
        return result;
    }

    private List<RemoteActionProviders.Server> serversFromTable() {
        final List<RemoteActionProviders.Server> result = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            final RemoteActionProviders.Server server = serverFromRow(row);
            if (server.isValid()) {
                result.add(server);
            }
        }
        return result;
    }

    private RemoteActionProviders.Server serverFromRow(final int row) {
        return RemoteActionProviders.Server.gitea(
                text(row, NAME),
                text(row, WEB_URL),
                text(row, API_URL),
                text(row, TOKEN_ENV),
                Boolean.TRUE.equals(model.getValueAt(row, ENABLED)),
                storedTokenRows.contains(rowId(row))
        ).normalized();
    }

    private String text(final int row, final int column) {
        return Objects.toString(model.getValueAt(row, column), "").trim();
    }

    private int rowId(final int row) {
        final Object value = model.getValueAt(row, ROW_ID);
        return value instanceof Integer id ? id : -1;
    }

    private int nextRowId() {
        final int next = nextRowSequence;
        nextRowSequence++;
        return next;
    }

    private void refreshTexts() {
        buttons.forEach(button -> button.component().setText(GitHubWorkflowBundle.message(button.key())));
        setColumnHeaders();
        if (border != null) {
            border.setTitle(GitHubWorkflowBundle.message("settings.gitea.title"));
        }
        if (panel != null) {
            panel.revalidate();
            panel.repaint();
        }
    }

    private void setColumnHeaders() {
        model.setColumnIdentifiers(new Object[]{
                GitHubWorkflowBundle.message("settings.gitea.column.enabled"),
                GitHubWorkflowBundle.message("settings.gitea.column.name"),
                GitHubWorkflowBundle.message("settings.gitea.column.webUrl"),
                GitHubWorkflowBundle.message("settings.gitea.column.apiUrl"),
                GitHubWorkflowBundle.message("settings.gitea.column.tokenEnv"),
                GitHubWorkflowBundle.message("settings.gitea.column.token"),
                ""
        });
        if (table.getColumnModel().getColumnCount() > ROW_ID) {
            table.removeColumn(table.getColumnModel().getColumn(ROW_ID));
        }
    }

    private record LocalizedButton(JButton component, String key) {
    }
}
