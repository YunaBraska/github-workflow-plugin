package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ConfigurationException;
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
    private final Map<Integer, RemoteActionProviders.Server> originalRows = new HashMap<>();
    private final Set<Integer> clearedTokens = new HashSet<>();
    private final Set<Integer> storedTokenRows = new HashSet<>();
    private final List<LocalizedButton> buttons = new ArrayList<>();
    private int nextRowSequence = 1;
    private @Nullable JPanel panel;
    private @Nullable TitledBorder border;
    private final boolean github;

    public GiteaSettingsConfigurable() {
        this(false);
    }

    protected GiteaSettingsConfigurable(final boolean github) {
        this.github = github;
    }

    /**
     * Returns the stable settings identifier used by IntelliJ Settings search.
     *
     * @return the Gitea settings configurable id
     */
    @Override
    public @NotNull String getId() {
        return github ? "github.workflow.github.settings" : "github.workflow.gitea.settings";
    }

    /**
     * Returns the localized Gitea settings page title.
     *
     * @return localized display name
     */
    @Override
    public @Nls String getDisplayName() {
        return GitHubWorkflowBundle.message(github ? "settings.github.displayName" : "settings.gitea.displayName");
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
                        .filter(server -> server.isGitea() != github)
                        .toList())
                || tokenModified();
    }

    /**
     * Stores Gitea account rows and tokens in IDE settings and Password Safe.
     */
    @Override
    public void apply() throws ConfigurationException {
        if (invalidRowCount() > 0) {
            throw new ConfigurationException(GitHubWorkflowBundle.message(github ? "settings.github.invalidRows" : "settings.gitea.invalidRows"), getDisplayName());
        }
        if (github && !new HashSet<>(settings.customServers().stream().filter(server -> !server.isGitea()).toList())
                .equals(new HashSet<>(originalRows.values()))) {
            throw new ConfigurationException(GitHubWorkflowBundle.message("settings.github.changed"), getDisplayName());
        }
        try {
            saveRows();
        } catch (final RuntimeException exception) {
            throw new ConfigurationException(GitHubWorkflowBundle.message("settings.github.storageError"), getDisplayName());
        }
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
        pendingTokens.clear();
        panel = null;
        border = null;
    }

    private JPanel accountsPanel() {
        setColumnHeaders();
        table.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final JPanel result = new JPanel(new BorderLayout(4, 4));
        border = BorderFactory.createTitledBorder(GitHubWorkflowBundle.message(github ? "settings.github.title" : "settings.gitea.title"));
        result.setBorder(border);
        result.add(new JScrollPane(table), BorderLayout.CENTER);
        result.add(buttonsPanel(), BorderLayout.SOUTH);
        return result;
    }

    private JPanel buttonsPanel() {
        final JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addButton(result, github ? "settings.github.add" : "settings.gitea.add", this::addRow);
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
        originalRows.clear();
        clearedTokens.clear();
        storedTokenRows.clear();
        model.setRowCount(0);
        settings.customServers().stream()
                .filter(server -> server.isGitea() != github)
                .forEach(this::addPersistedRow);
    }

    private void addPersistedRow(final RemoteActionProviders.Server server) {
        final int rowId = nextRowId();
        originalRows.put(rowId, server);
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
                github ? "GitHub" : "Gitea",
                github ? "https://github.com" : "https://gitea.com",
                github ? "https://api.github.com" : "https://gitea.com/api/v1",
                github ? "" : "GITEA_TOKEN",
                "",
                nextRowId()
        });
    }

    private void removeSelectedRows() {
        final int[] rows = table.getSelectedRows();
        for (int index = rows.length - 1; index >= 0; index--) {
            final int row = table.convertRowIndexToModel(rows[index]);
            pendingTokens.remove(rowId(row));
            model.removeRow(row);
        }
    }

    private void setSelectedToken() {
        final int row = selectedRow();
        if (row < 0) {
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message(github ? "settings.github.noneSelected" : "settings.gitea.noneSelected"), getDisplayName());
            return;
        }
        final String token = Messages.showPasswordDialog(GitHubWorkflowBundle.message(github ? "settings.github.token.prompt" : "settings.gitea.token.prompt"), getDisplayName());
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
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message(github ? "settings.github.noneSelected" : "settings.gitea.noneSelected"), getDisplayName());
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
        if (github) {
            saveGithubRows();
            return;
        }
        final List<RemoteActionProviders.Server> previous = settings.customServers();
        final List<RemoteActionProviders.Server> servers = serversFromTable();
        final List<String> nextKeys = servers.stream().map(server -> server.apiUrl).toList();
        final List<RemoteActionProviders.Server> persisted = new ArrayList<>(previous.stream()
                .filter(server -> server.isGitea() == github)
                .toList());
        persisted.addAll(servers);

        previous.stream()
                .filter(server -> server.isGitea() != github)
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

    private void saveGithubRows() {
        final List<RemoteActionProviders.Server> previous = settings.customServers();
        final List<RemoteActionProviders.Server> servers = serversFromTable();
        for (int row = 0; row < model.getRowCount(); row++) {
            final int rowId = rowId(row);
            final RemoteActionProviders.Server server = serverFromRow(row);
            final RemoteActionProviders.Server original = originalRows.get(rowId);
            final boolean moved = original != null && (!original.apiUrl.equals(server.apiUrl) || !original.name.equals(server.name));
            if (pendingTokens.containsKey(rowId)) {
                settings.setGithubToken(server, pendingTokens.get(rowId));
            } else if (clearedTokens.contains(rowId)) {
                settings.clearGithubToken(server);
            } else if (moved && original.tokenStored) {
                final String token = settings.githubToken(original).orElseThrow(() -> new IllegalStateException("Stored token is unavailable"));
                settings.setGithubToken(server, token);
            }
        }
        previous.stream().filter(server -> !server.isGitea())
                .filter(server -> servers.stream().noneMatch(candidate -> candidate.apiUrl.equals(server.apiUrl) && candidate.name.equals(server.name)))
                .forEach(settings::clearGithubToken);
        final List<RemoteActionProviders.Server> persisted = new ArrayList<>(previous.stream().filter(RemoteActionProviders.Server::isGitea).toList());
        persisted.addAll(servers);
        settings.setCustomServers(persisted);
        reloadTable();
    }

    private boolean tokenModified() {
        return !pendingTokens.isEmpty() || !clearedTokens.isEmpty();
    }

    private int invalidRowCount() {
        int result = 0;
        final Set<String> identities = new HashSet<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            final RemoteActionProviders.Server server = serverFromRow(row);
            final int currentRowId = rowId(row);
            if (!server.isValid() || (github && (text(row, NAME).isBlank()
                    || !validUrl(server.webUrl) || !validUrl(server.apiUrl)
                    || !identities.add(server.apiUrl + "\n" + server.name)
                    || originalRows.entrySet().stream().anyMatch(entry -> entry.getKey() != currentRowId
                            && entry.getValue().apiUrl.equals(server.apiUrl) && entry.getValue().name.equals(server.name))))) {
                result++;
            }
        }
        return result;
    }

    private boolean validUrl(final String value) {
        try {
            final java.net.URI uri = java.net.URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
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
        return new RemoteActionProviders.Server(
                github ? RemoteActionProviders.Settings.TYPE_GITHUB : RemoteActionProviders.Settings.TYPE_GITEA,
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
            border.setTitle(GitHubWorkflowBundle.message(github ? "settings.github.title" : "settings.gitea.title"));
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
