package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Settings UI for locale override and GitHub Action cache maintenance.
 */
public class GitHubWorkflowSettingsConfigurable implements SearchableConfigurable {

    private static final String SUPPORT_URL = "https://github.com/sponsors/YunaBraska";
    private static final String STORED_TOKEN = "********";
    private static final int GITEA_ENABLED = 0;
    private static final int GITEA_NAME = 1;
    private static final int GITEA_WEB_URL = 2;
    private static final int GITEA_API_URL = 3;
    private static final int GITEA_TOKEN_ENV = 4;
    private static final int GITEA_TOKEN = 5;
    private static final int GITEA_ROW_ID = 6;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
    private static final List<LocaleOption> LOCALES = List.of(
            new LocaleOption(GitHubWorkflowBundle.Settings.SYSTEM_LANGUAGE, "settings.language.system", true),
            new LocaleOption("ar", "العربية"),
            new LocaleOption("cs", "Čeština"),
            new LocaleOption("de", "Deutsch"),
            new LocaleOption("es", "Español"),
            new LocaleOption("fr", "Français"),
            new LocaleOption("hi", "हिन्दी"),
            new LocaleOption("id", "Bahasa Indonesia"),
            new LocaleOption("it", "Italiano"),
            new LocaleOption("ja", "日本語"),
            new LocaleOption("ko", "한국어"),
            new LocaleOption("nl", "Nederlands"),
            new LocaleOption("pl", "Polski"),
            new LocaleOption("pt-BR", "Português (Brasil)"),
            new LocaleOption("ru", "Русский"),
            new LocaleOption("sv", "Svenska"),
            new LocaleOption("th", "ไทย"),
            new LocaleOption("tr", "Türkçe"),
            new LocaleOption("uk", "Українська"),
            new LocaleOption("vi", "Tiếng Việt"),
            new LocaleOption("zh-CN", "简体中文")
    );

    private final GitHubWorkflowBundle.Settings settings = GitHubWorkflowBundle.Settings.getInstance();
    private final RemoteActionProviders.Settings remoteSettings = RemoteActionProviders.Settings.getInstance();
    private final GitHubActionCache cache = GitHubActionCache.getActionCache();
    private final JComboBox<LocaleOption> language = new JComboBox<>(LOCALES.toArray(LocaleOption[]::new));
    private final DefaultTableModel giteaModel = new DefaultTableModel() {
        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            return columnIndex == GITEA_ENABLED ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(final int row, final int column) {
            return column != GITEA_TOKEN && column != GITEA_ROW_ID;
        }
    };
    private final JTable giteaTable = new JBTable(giteaModel);
    private final Map<Integer, String> pendingGiteaTokens = new HashMap<>();
    private final Set<Integer> clearedGiteaTokens = new HashSet<>();
    private int nextGiteaRowSequence = 1;
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable table = new JBTable(tableModel);
    private final JLabel summary = new JLabel();
    private @Nullable JPanel panel;

    @Override
    public @NotNull String getId() {
        return "github.workflow.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return GitHubWorkflowBundle.message("settings.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(topPanel(), BorderLayout.NORTH);
        final JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(giteaPanel(), BorderLayout.NORTH);
        center.add(cachePanel(), BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        final LocaleOption option = (LocaleOption) language.getSelectedItem();
        return option != null && !Objects.equals(option.tag(), settings.languageTag())
                || !Objects.equals(giteaServersFromTable(), remoteSettings.customServers().stream()
                .filter(RemoteActionProviders.Server::isGitea)
                .toList())
                || giteaTokenModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        final LocaleOption option = (LocaleOption) language.getSelectedItem();
        settings.languageTag(option == null ? GitHubWorkflowBundle.Settings.SYSTEM_LANGUAGE : option.tag());
        saveGiteaRows();
        reloadTable();
        GitHubActionCache.triggerSyntaxHighlightingForActiveFiles();
    }

    @Override
    public void reset() {
        selectLanguage(settings.languageTag());
        reloadGiteaTable();
        reloadTable();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }

    private JPanel topPanel() {
        final JPanel result = new JPanel(new GridBagLayout());
        final GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = 0;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(0, 0, 0, 8);
        result.add(new JLabel(GitHubWorkflowBundle.message("settings.language.label")), label);

        final GridBagConstraints combo = new GridBagConstraints();
        combo.gridx = 1;
        combo.gridy = 0;
        combo.weightx = 1;
        combo.fill = GridBagConstraints.HORIZONTAL;
        result.add(language, combo);

        final JButton support = new JButton(randomSupportLine());
        support.setToolTipText(GitHubWorkflowBundle.message("settings.support.tooltip"));
        support.addActionListener(event -> BrowserUtil.browse(SUPPORT_URL));
        final GridBagConstraints supportConstraints = new GridBagConstraints();
        supportConstraints.gridx = 2;
        supportConstraints.gridy = 0;
        supportConstraints.insets = new Insets(0, 8, 0, 0);
        result.add(support, supportConstraints);
        return result;
    }

    private JPanel giteaPanel() {
        giteaModel.setColumnIdentifiers(new Object[]{
                GitHubWorkflowBundle.message("settings.gitea.column.enabled"),
                GitHubWorkflowBundle.message("settings.gitea.column.name"),
                GitHubWorkflowBundle.message("settings.gitea.column.webUrl"),
                GitHubWorkflowBundle.message("settings.gitea.column.apiUrl"),
                GitHubWorkflowBundle.message("settings.gitea.column.tokenEnv"),
                GitHubWorkflowBundle.message("settings.gitea.column.token"),
                ""
        });
        if (giteaTable.getColumnModel().getColumnCount() > GITEA_ROW_ID) {
            giteaTable.removeColumn(giteaTable.getColumnModel().getColumn(GITEA_ROW_ID));
        }
        giteaTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final JPanel result = new JPanel(new BorderLayout(4, 4));
        result.setBorder(BorderFactory.createTitledBorder(GitHubWorkflowBundle.message("settings.gitea.title")));
        result.add(new JScrollPane(giteaTable), BorderLayout.CENTER);
        result.add(giteaButtons(), BorderLayout.SOUTH);
        return result;
    }

    private JPanel giteaButtons() {
        final JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addButton(result, "settings.gitea.add", this::addGiteaRow);
        addButton(result, "settings.gitea.remove", this::removeSelectedGiteaRows);
        addButton(result, "settings.gitea.setToken", this::setSelectedGiteaToken);
        addButton(result, "settings.gitea.clearToken", this::clearSelectedGiteaToken);
        return result;
    }

    private JPanel cachePanel() {
        tableModel.setColumnIdentifiers(new Object[]{
                GitHubWorkflowBundle.message("settings.cache.column.key"),
                GitHubWorkflowBundle.message("settings.cache.column.name"),
                GitHubWorkflowBundle.message("settings.cache.column.kind"),
                GitHubWorkflowBundle.message("settings.cache.column.state"),
                GitHubWorkflowBundle.message("settings.cache.column.expires")
        });
        table.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final JPanel result = new JPanel(new BorderLayout(4, 4));
        result.setBorder(BorderFactory.createTitledBorder(GitHubWorkflowBundle.message("settings.cache.title")));
        result.add(summary, BorderLayout.NORTH);
        result.add(new JScrollPane(table), BorderLayout.CENTER);
        result.add(cacheButtons(), BorderLayout.SOUTH);
        return result;
    }

    private JPanel cacheButtons() {
        final JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addButton(result, "settings.cache.refresh", this::reloadTable);
        addButton(result, "settings.cache.deleteSelected", this::deleteSelected);
        addButton(result, "settings.cache.deleteAll", this::deleteAll);
        addButton(result, "settings.cache.export", this::exportCache);
        addButton(result, "settings.cache.import", this::importCache);
        return result;
    }

    private void addButton(final JPanel panel, final String key, final Runnable action) {
        final JButton button = new JButton(GitHubWorkflowBundle.message(key));
        button.addActionListener(event -> action.run());
        panel.add(button);
    }

    private void reloadGiteaTable() {
        pendingGiteaTokens.clear();
        clearedGiteaTokens.clear();
        giteaModel.setRowCount(0);
        remoteSettings.customServers().stream()
                .filter(RemoteActionProviders.Server::isGitea)
                .forEach(server -> giteaModel.addRow(new Object[]{
                        server.enabled,
                        server.name,
                        server.webUrl,
                        server.apiUrl,
                        server.tokenEnvVar,
                        remoteSettings.hasGiteaToken(server) ? STORED_TOKEN : "",
                        nextGiteaRowId()
                }));
    }

    private void addGiteaRow() {
        giteaModel.addRow(new Object[]{
                true,
                "Gitea",
                "https://gitea.com",
                "https://gitea.com/api/v1",
                "GITEA_TOKEN",
                "",
                nextGiteaRowId()
        });
    }

    private void removeSelectedGiteaRows() {
        final int[] rows = giteaTable.getSelectedRows();
        for (int index = rows.length - 1; index >= 0; index--) {
            giteaModel.removeRow(giteaTable.convertRowIndexToModel(rows[index]));
        }
    }

    private void setSelectedGiteaToken() {
        final int row = selectedGiteaRow();
        if (row < 0) {
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.gitea.noneSelected"), getDisplayName());
            return;
        }
        final String token = Messages.showPasswordDialog(GitHubWorkflowBundle.message("settings.gitea.token.prompt"), getDisplayName());
        if (token != null && !token.isBlank()) {
            final int rowId = rowId(row);
            pendingGiteaTokens.put(rowId, token);
            clearedGiteaTokens.remove(rowId);
            giteaModel.setValueAt(STORED_TOKEN, row, GITEA_TOKEN);
        }
    }

    private void clearSelectedGiteaToken() {
        final int row = selectedGiteaRow();
        if (row < 0) {
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.gitea.noneSelected"), getDisplayName());
            return;
        }
        final int rowId = rowId(row);
        pendingGiteaTokens.remove(rowId);
        clearedGiteaTokens.add(rowId);
        giteaModel.setValueAt("", row, GITEA_TOKEN);
    }

    private int selectedGiteaRow() {
        final int viewRow = giteaTable.getSelectedRow();
        return viewRow < 0 ? -1 : giteaTable.convertRowIndexToModel(viewRow);
    }

    private void saveGiteaRows() {
        final List<RemoteActionProviders.Server> previous = remoteSettings.customServers();
        final List<RemoteActionProviders.Server> servers = giteaServersFromTable();
        final List<String> nextKeys = servers.stream().map(server -> server.apiUrl).toList();
        final List<RemoteActionProviders.Server> persisted = new ArrayList<>(previous.stream()
                .filter(server -> !server.isGitea())
                .toList());
        persisted.addAll(servers);

        previous.stream()
                .filter(RemoteActionProviders.Server::isGitea)
                .filter(server -> !nextKeys.contains(server.apiUrl))
                .forEach(remoteSettings::clearGiteaToken);

        for (int row = 0; row < giteaModel.getRowCount(); row++) {
            final RemoteActionProviders.Server server = giteaServerFromRow(row);
            if (!server.isValid()) {
                continue;
            }
            final int rowId = rowId(row);
            if (pendingGiteaTokens.containsKey(rowId)) {
                remoteSettings.setGiteaToken(server, pendingGiteaTokens.get(rowId));
            } else if (clearedGiteaTokens.contains(rowId)) {
                remoteSettings.clearGiteaToken(server);
            }
        }
        remoteSettings.setCustomServers(persisted);
        reloadGiteaTable();
    }

    private boolean giteaTokenModified() {
        if (!pendingGiteaTokens.isEmpty()) {
            return true;
        }
        for (int row = 0; row < giteaModel.getRowCount(); row++) {
            final RemoteActionProviders.Server server = giteaServerFromRow(row);
            if (clearedGiteaTokens.contains(rowId(row)) && remoteSettings.hasGiteaToken(server)) {
                return true;
            }
        }
        return false;
    }

    private List<RemoteActionProviders.Server> giteaServersFromTable() {
        final List<RemoteActionProviders.Server> result = new ArrayList<>();
        for (int row = 0; row < giteaModel.getRowCount(); row++) {
            final RemoteActionProviders.Server server = giteaServerFromRow(row);
            if (server.isValid()) {
                result.add(server);
            }
        }
        return result;
    }

    private RemoteActionProviders.Server giteaServerFromRow(final int row) {
        return RemoteActionProviders.Server.gitea(
                text(row, GITEA_NAME),
                text(row, GITEA_WEB_URL),
                text(row, GITEA_API_URL),
                text(row, GITEA_TOKEN_ENV),
                Boolean.TRUE.equals(giteaModel.getValueAt(row, GITEA_ENABLED))
        ).normalized();
    }

    private String text(final int row, final int column) {
        return Objects.toString(giteaModel.getValueAt(row, column), "").trim();
    }

    private int rowId(final int row) {
        final Object value = giteaModel.getValueAt(row, GITEA_ROW_ID);
        return value instanceof Integer id ? id : -1;
    }

    private int nextGiteaRowId() {
        final int next = nextGiteaRowSequence;
        nextGiteaRowSequence++;
        return next;
    }

    private void reloadTable() {
        tableModel.setRowCount(0);
        cache.entries().forEach(entry -> tableModel.addRow(new Object[]{
                entry.key(),
                entry.name(),
                entry.local() ? GitHubWorkflowBundle.message("settings.cache.kind.local") : GitHubWorkflowBundle.message("settings.cache.kind.remote"),
                stateText(entry),
                entry.expiryTime() <= 0 ? "" : DATE_TIME.format(Instant.ofEpochMilli(entry.expiryTime()))
        }));
        final GitHubActionCache.CacheSummary cacheSummary = cache.summary();
        final long cacheKb = Math.max(0, (cache.estimatedSizeBytes() + 1023) / 1024);
        summary.setText(GitHubWorkflowBundle.message(
                "settings.cache.summary",
                cacheSummary.total(),
                cacheSummary.resolved(),
                cacheSummary.remote(),
                cacheSummary.expired(),
                cacheSummary.suppressed(),
                cacheKb
        ));
        summary.setToolTipText(null);
    }

    private String stateText(final GitHubActionCache.CacheEntry entry) {
        if (entry.suppressed()) {
            return GitHubWorkflowBundle.message("settings.cache.state.suppressed");
        }
        if (entry.expired()) {
            return GitHubWorkflowBundle.message("settings.cache.state.expired");
        }
        return entry.resolved()
                ? GitHubWorkflowBundle.message("settings.cache.state.resolved")
                : GitHubWorkflowBundle.message("settings.cache.state.pending");
    }

    private void deleteSelected() {
        final int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.cache.noneSelected"), getDisplayName());
            return;
        }
        final List<String> keys = Arrays.stream(rows)
                .map(table::convertRowIndexToModel)
                .mapToObj(row -> String.valueOf(tableModel.getValueAt(row, 0)))
                .toList();
        cache.removeAll(keys);
        reloadTable();
        Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.cache.deleteSelected.done", keys.size()), getDisplayName());
    }

    private void deleteAll() {
        final int choice = Messages.showYesNoDialog(panel, GitHubWorkflowBundle.message("settings.cache.deleteAll.confirm"), getDisplayName(), null);
        if (choice != Messages.YES) {
            return;
        }
        cache.clear();
        reloadTable();
        Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.cache.deleteAll.done"), getDisplayName());
    }

    private void exportCache() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("github-workflow-cache.txt"));
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            final GitHubActionCache.CacheSummary exported = cache.exportCache(chooser.getSelectedFile().toPath());
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.cache.export.done", exported.total()), getDisplayName());
        } catch (final Exception exception) {
            Messages.showErrorDialog(panel, exception.getMessage(), getDisplayName());
        }
    }

    private void importCache() {
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            cache.importCache(Path.of(chooser.getSelectedFile().getPath()));
            reloadTable();
            Messages.showInfoMessage(panel, GitHubWorkflowBundle.message("settings.cache.import.done"), getDisplayName());
        } catch (final Exception exception) {
            Messages.showErrorDialog(panel, exception.getMessage(), getDisplayName());
        }
    }

    private void selectLanguage(final String tag) {
        for (final LocaleOption option : LOCALES) {
            if (Objects.equals(option.tag(), tag)) {
                language.setSelectedItem(option);
                return;
            }
        }
        language.setSelectedIndex(0);
    }

    private static String randomSupportLine() {
        final int index = ThreadLocalRandom.current().nextInt(3);
        return GitHubWorkflowBundle.message("settings.support.line." + index);
    }

    private record LocaleOption(String tag, String label, boolean bundleKey) {
        private LocaleOption(final String tag, final String label) {
            this(tag, label, false);
        }

        @Override
        public String toString() {
            return bundleKey ? GitHubWorkflowBundle.message(label) : label;
        }
    }
}
