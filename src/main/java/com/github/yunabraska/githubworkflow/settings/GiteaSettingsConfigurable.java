package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Account settings for GitHub and Gitea workflow providers. */
public class GiteaSettingsConfigurable implements SearchableConfigurable {
    private final boolean github;
    private final RemoteActionProviders.Settings settings = RemoteActionProviders.Settings.getInstance();
    private final DefaultListModel<AccountRow> model = new DefaultListModel<>();
    private final JBList<AccountRow> accounts = new JBList<>(model);
    private final Map<Integer, String> pendingTokens = new HashMap<>();
    private final Set<Integer> clearedTokens = new HashSet<>();
    private final Map<Integer, RemoteActionProviders.Server> originalRows = new HashMap<>();
    private List<RemoteActionProviders.Server> original = List.of();
    private int nextId;
    private @Nullable JPanel panel;

    public GiteaSettingsConfigurable() {
        this(false);
    }

    protected GiteaSettingsConfigurable(final boolean github) {
        this.github = github;
    }

    @Override
    public @NotNull String getId() {
        return github ? "github.workflow.github.settings" : "github.workflow.gitea.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return GitHubWorkflowBundle.message(github ? "settings.github.displayName" : "settings.gitea.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        accounts.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                                                           final boolean selected, final boolean focus) {
                final AccountRow row = (AccountRow) value;
                final RemoteActionProviders.Server server = row.server();
                final String text = server.name + "  (" + server.webUrl + ")"
                        + (server.enabled ? "" : "  " + GitHubWorkflowBundle.message("settings.account.disabled"));
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        panel = new JPanel(new BorderLayout());
        panel.add(ToolbarDecorator.createDecorator(accounts)
                .setAddAction(button -> editAccount(-1))
                .setEditAction(button -> editAccount(accounts.getSelectedIndex()))
                .setRemoveAction(button -> {
                    final int index = accounts.getSelectedIndex();
                    if (index >= 0) {
                        final int id = model.get(index).id();
                        pendingTokens.remove(id);
                        clearedTokens.remove(id);
                        model.remove(index);
                    }
                })
                .setMoveUpAction(button -> moveAccount(-1))
                .setMoveDownAction(button -> moveAccount(1))
                .createPanel(), BorderLayout.CENTER);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        final List<RemoteActionProviders.Server> current = new ArrayList<>();
        for (int index = 0; index < model.size(); index++) {
            current.add(model.get(index).server());
        }
        return !current.equals(original) || !pendingTokens.isEmpty() || !clearedTokens.isEmpty();
    }

    @Override
    public void apply() throws ConfigurationException {
        final List<RemoteActionProviders.Server> previous = settings.customServers();
        if (!previous.stream().filter(server -> server.isGitea() != github).toList().equals(original)) {
            throw new ConfigurationException(GitHubWorkflowBundle.message("settings.account.changed"), getDisplayName());
        }
        final List<RemoteActionProviders.Server> current = new ArrayList<>();
        for (int index = 0; index < model.size(); index++) {
            current.add(model.get(index).server());
        }
        try {
            for (int index = 0; index < model.size(); index++) {
                final AccountRow row = model.get(index);
                if (pendingTokens.containsKey(row.id())) {
                    if (github) settings.setGithubToken(row.server(), pendingTokens.get(row.id()));
                    else settings.setGiteaToken(row.server(), pendingTokens.get(row.id()));
                } else if (clearedTokens.contains(row.id())) {
                    if (github) settings.clearGithubToken(row.server());
                    else settings.clearGiteaToken(row.server());
                } else {
                    final RemoteActionProviders.Server old = originalRows.get(row.id());
                    if (old != null && old.tokenStored && !sameCredential(old, row.server())) {
                        final String token = (github ? settings.githubToken(old) : settings.giteaToken(old))
                                .orElseThrow(() -> new IllegalStateException("Stored token is unavailable"));
                        if (github) settings.setGithubToken(row.server(), token);
                        else settings.setGiteaToken(row.server(), token);
                    }
                }
            }
            for (final RemoteActionProviders.Server old : original) {
                final boolean retained = current.stream().anyMatch(server -> sameCredential(old, server));
                if (!retained) {
                    if (github) settings.clearGithubToken(old);
                    else settings.clearGiteaToken(old);
                }
            }
            final List<RemoteActionProviders.Server> persisted = new ArrayList<>(previous.stream()
                    .filter(server -> server.isGitea() == github).toList());
            persisted.addAll(current);
            settings.setCustomServers(persisted);
            reset();
            GitHubActionCache.triggerSyntaxHighlightingForActiveFiles();
        } catch (final RuntimeException exception) {
            throw new ConfigurationException(GitHubWorkflowBundle.message("settings.account.storageError"), getDisplayName());
        }
    }

    @Override
    public void reset() {
        pendingTokens.clear();
        clearedTokens.clear();
        originalRows.clear();
        model.clear();
        original = settings.customServers().stream().filter(server -> server.isGitea() != github).toList();
        original.forEach(server -> {
            final int id = nextId++;
            originalRows.put(id, server);
            model.addElement(new AccountRow(id, server));
        });
    }

    @Override
    public void disposeUIResources() {
        pendingTokens.clear();
        clearedTokens.clear();
        originalRows.clear();
        panel = null;
    }

    private void editAccount(final int index) {
        if (index < 0 && !accounts.isSelectionEmpty()) {
            accounts.clearSelection();
        }
        final AccountRow existing = index < 0 ? null : model.get(index);
        final AccountDialog dialog = new AccountDialog(panel, github, existing == null ? null : existing.server());
        if (!dialog.showAndGet()) return;
        final RemoteActionProviders.Server candidate = dialog.result();
        for (int row = 0; row < model.size(); row++) {
            if (row != index && sameCredential(model.get(row).server(), candidate)) {
                Messages.showErrorDialog(panel, GitHubWorkflowBundle.message("settings.account.duplicate"), getDisplayName());
                return;
            }
        }
        final int id = existing == null ? nextId++ : existing.id();
        if (originalRows.entrySet().stream().anyMatch(entry -> entry.getKey() != id
                && sameCredential(entry.getValue(), candidate))) {
            Messages.showErrorDialog(panel, GitHubWorkflowBundle.message("settings.account.duplicate"), getDisplayName());
            return;
        }
        if (dialog.resultToken() != null) {
            pendingTokens.put(id, dialog.resultToken());
            clearedTokens.remove(id);
        } else if (dialog.tokenCleared()) {
            pendingTokens.remove(id);
            clearedTokens.add(id);
        }
        final AccountRow row = new AccountRow(id, candidate);
        if (index < 0) {
            model.addElement(row);
            accounts.setSelectedIndex(model.size() - 1);
        } else {
            model.set(index, row);
            accounts.setSelectedIndex(index);
        }
    }

    private void moveAccount(final int direction) {
        final int index = accounts.getSelectedIndex();
        final int target = index + direction;
        if (index < 0 || target < 0 || target >= model.size()) return;
        final AccountRow row = model.remove(index);
        model.add(target, row);
        accounts.setSelectedIndex(target);
    }

    private boolean sameCredential(final RemoteActionProviders.Server left, final RemoteActionProviders.Server right) {
        return Objects.equals(left.apiUrl, right.apiUrl) && (!github || Objects.equals(left.name, right.name));
    }

    private record AccountRow(int id, RemoteActionProviders.Server server) {
    }
}
