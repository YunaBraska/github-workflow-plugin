package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.auth.GitHubDeviceAuth;
import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** The add/edit account dialog shared by both providers. */
final class AccountDialog extends DialogWrapper {
    private final boolean github;
    private final RemoteActionProviders.Server original;
    private final JTextField webUrl = new JTextField(30);
    private final JTextField apiUrl = new JTextField(30);
    private final JTextField name = new JTextField(30);
    private final JTextField tokenEnv = new JTextField(30);
    private final JPasswordField token = new JPasswordField(30);
    private final JCheckBox enabled = new JCheckBox(GitHubWorkflowBundle.message("settings.account.enabled"), true);
    private final JCheckBox advanced = new JCheckBox(GitHubWorkflowBundle.message("settings.account.advanced"));
    private final JCheckBox clearToken = new JCheckBox(GitHubWorkflowBundle.message("settings.account.clearToken"));
    private final JCheckBox withoutToken = new JCheckBox(GitHubWorkflowBundle.message("settings.account.withoutToken"));
    private final JPanel advancedPanel = new JPanel(new GridBagLayout());
    private final JLabel status = new JLabel();
    private final JTextField deviceCode = new JTextField(12);
    private SwingWorker<AccountLogin.Result, Void> verification;
    private SwingWorker<BrowserLogin, GitHubDeviceAuth.DeviceCode> browserLogin;
    private boolean updatingApi;
    private boolean apiCustomized;
    private RemoteActionProviders.Server result;
    private String resultToken;
    private boolean tokenCleared;

    AccountDialog(final Component parent, final boolean github, final RemoteActionProviders.Server original) {
        super(parent, true);
        this.github = github;
        this.original = original;
        final String initialWeb = original == null ? (github ? "https://github.com" : "https://gitea.com") : original.webUrl;
        final String initialApi = original == null ? apiFor(github, initialWeb) : original.apiUrl;
        webUrl.setText(initialWeb);
        apiUrl.setText(initialApi);
        name.setText(original == null ? (github ? "GitHub" : "Gitea") : original.name);
        tokenEnv.setText(original == null ? (github ? "" : "GITEA_TOKEN") : original.tokenEnvVar);
        enabled.setSelected(original == null || original.enabled);
        clearToken.setVisible(original != null && original.tokenStored);
        apiCustomized = !initialApi.equals(apiFor(github, initialWeb));
        webUrl.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent event) { updateApi(); }
            @Override public void removeUpdate(final DocumentEvent event) { updateApi(); }
            @Override public void changedUpdate(final DocumentEvent event) { updateApi(); }
        });
        apiUrl.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent event) { markCustomized(); }
            @Override public void removeUpdate(final DocumentEvent event) { markCustomized(); }
            @Override public void changedUpdate(final DocumentEvent event) { markCustomized(); }
        });
        setTitle(GitHubWorkflowBundle.message(github ? "settings.account.github.title" : "settings.account.gitea.title"));
        setOKButtonText(GitHubWorkflowBundle.message("settings.account.login"));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        addRow(panel, 0, GitHubWorkflowBundle.message("settings.account.server"), webUrl);
        final JPanel tokenPanel = new JPanel(new java.awt.BorderLayout(6, 0));
        token.getAccessibleContext().setAccessibleName(GitHubWorkflowBundle.message("settings.account.token"));
        final JButton generate = new JButton(GitHubWorkflowBundle.message("settings.account.generate"));
        generate.addActionListener(event -> {
            final String web = webUrl.getText().trim().replaceAll("/+$", "");
            if (!validUrl(web)) {
                setErrorText(GitHubWorkflowBundle.message("settings.account.invalid"));
                return;
            }
            BrowserUtil.browse(web + (github ? "/settings/tokens" : "/user/settings/applications"));
        });
        tokenPanel.add(token, java.awt.BorderLayout.CENTER);
        tokenPanel.add(generate, java.awt.BorderLayout.EAST);
        addRow(panel, 1, GitHubWorkflowBundle.message("settings.account.token"), tokenPanel);
        if (github) {
            final JPanel browserPanel = new JPanel(new java.awt.BorderLayout(6, 0));
            final JButton browser = new JButton(GitHubWorkflowBundle.message("settings.account.browser"));
            browser.addActionListener(event -> startBrowserLogin(browser));
            final JButton install = new JButton(GitHubWorkflowBundle.message("settings.account.install"));
            install.addActionListener(event -> BrowserUtil.browse("https://github.com/apps/workflow-ide-for-github"));
            deviceCode.setEditable(false);
            deviceCode.setVisible(false);
            deviceCode.getAccessibleContext().setAccessibleName(GitHubWorkflowBundle.message("settings.account.deviceCode"));
            browserPanel.add(browser, java.awt.BorderLayout.WEST);
            browserPanel.add(deviceCode, java.awt.BorderLayout.CENTER);
            browserPanel.add(install, java.awt.BorderLayout.EAST);
            addRow(panel, 2, "", browserPanel);
        }
        addRow(advancedPanel, 0, GitHubWorkflowBundle.message("settings.account.name"), name);
        addRow(advancedPanel, 1, GitHubWorkflowBundle.message("settings.account.api"), apiUrl);
        addRow(advancedPanel, 2, GitHubWorkflowBundle.message("settings.account.env"), tokenEnv);
        final GridBagConstraints check = new GridBagConstraints();
        check.gridx = 1;
        check.gridy = 3;
        check.anchor = GridBagConstraints.WEST;
        advancedPanel.add(enabled, check);
        check.gridy = 4;
        advancedPanel.add(clearToken, check);
        check.gridy = 5;
        withoutToken.setVisible(original == null);
        withoutToken.addActionListener(event -> setOKButtonText(GitHubWorkflowBundle.message(
                withoutToken.isSelected() ? "settings.account.save" : "settings.account.login")));
        advancedPanel.add(withoutToken, check);
        advancedPanel.setVisible(false);
        advanced.addActionListener(event -> {
            advancedPanel.setVisible(advanced.isSelected());
            pack();
        });
        final GridBagConstraints full = new GridBagConstraints();
        full.gridx = 0;
        full.gridy = 3;
        full.gridwidth = 2;
        full.anchor = GridBagConstraints.WEST;
        panel.add(advanced, full);
        full.gridy = 4;
        full.fill = GridBagConstraints.HORIZONTAL;
        panel.add(advancedPanel, full);
        full.gridy = 5;
        panel.add(status, full);
        return panel;
    }

    @Override
    protected void doOKAction() {
        final String web = webUrl.getText().trim().replaceAll("/+$", "");
        final String api = apiUrl.getText().trim().replaceAll("/+$", "");
        final String displayName = name.getText().trim();
        if (!validUrl(web) || !validUrl(api) || displayName.isBlank()) {
            setErrorText(GitHubWorkflowBundle.message("settings.account.invalid"));
            return;
        }
        final char[] chars = token.getPassword();
        final String secret = new String(chars).trim();
        Arrays.fill(chars, '\0');
        if (clearToken.isSelected() && !secret.isEmpty()) {
            setErrorText(GitHubWorkflowBundle.message("settings.account.tokenConflict"));
            return;
        }
        if (original == null && secret.isEmpty() && !(advanced.isSelected() && withoutToken.isSelected())) {
            setErrorText(GitHubWorkflowBundle.message("settings.account.tokenRequired"));
            return;
        }
        final RemoteActionProviders.Server candidate = new RemoteActionProviders.Server(
                github ? RemoteActionProviders.Settings.TYPE_GITHUB : RemoteActionProviders.Settings.TYPE_GITEA,
                displayName, web, api, tokenEnv.getText().trim(), enabled.isSelected(),
                !secret.isEmpty() || (original != null && original.tokenStored && !clearToken.isSelected())).normalized();
        if (secret.isEmpty()) {
            result = candidate;
            tokenCleared = clearToken.isSelected();
            super.doOKAction();
            return;
        }
        getOKAction().setEnabled(false);
        setErrorText(null);
        status.setText(GitHubWorkflowBundle.message("settings.account.verifying"));
        verification = new SwingWorker<>() {
            @Override
            protected AccountLogin.Result doInBackground() throws Exception {
                return AccountLogin.verify(candidate, secret);
            }
            @Override
            protected void done() {
                try {
                    final AccountLogin.Result verified = get();
                    status.setText("");
                    if (verified.status() != AccountLogin.Status.SUCCESS) {
                        setErrorText(GitHubWorkflowBundle.message("settings.account.error." + verified.status().name().toLowerCase(Locale.ROOT)));
                        getOKAction().setEnabled(true);
                        return;
                    }
                    result = new RemoteActionProviders.Server(candidate.type, verified.login().orElseThrow(),
                            candidate.webUrl, candidate.apiUrl, candidate.tokenEnvVar, candidate.enabled, true).normalized();
                    resultToken = secret;
                    token.setText("");
                    getOKAction().setEnabled(true);
                    AccountDialog.super.doOKAction();
                } catch (final CancellationException ignored) {
                    // The dialog was cancelled.
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    status.setText("");
                    getOKAction().setEnabled(true);
                } catch (final ExecutionException exception) {
                    status.setText("");
                    setErrorText(GitHubWorkflowBundle.message("settings.account.error.network_error"));
                    getOKAction().setEnabled(true);
                }
            }
        };
        verification.execute();
    }

    @Override
    public void doCancelAction() {
        if (verification != null) {
            verification.cancel(true);
        }
        if (browserLogin != null) browserLogin.cancel(true);
        token.setText("");
        super.doCancelAction();
    }

    RemoteActionProviders.Server result() { return result; }
    String resultToken() { return resultToken; }
    boolean tokenCleared() { return tokenCleared; }

    private void startBrowserLogin(final JButton button) {
        if (!"https://github.com".equalsIgnoreCase(webUrl.getText().trim().replaceAll("/+$", ""))
                || !"https://api.github.com".equalsIgnoreCase(apiUrl.getText().trim().replaceAll("/+$", ""))) {
            setErrorText(GitHubWorkflowBundle.message("settings.account.browserServer"));
            return;
        }
        setErrorText(null);
        final String environment = tokenEnv.getText().trim();
        final boolean accountEnabled = enabled.isSelected();
        getOKAction().setEnabled(false);
        button.setEnabled(false);
        webUrl.setEnabled(false);
        apiUrl.setEnabled(false);
        deviceCode.setVisible(false);
        status.setText(GitHubWorkflowBundle.message("settings.account.browserStarting"));
        browserLogin = new SwingWorker<>() {
            @Override protected BrowserLogin doInBackground() throws Exception {
                final GitHubDeviceAuth.Client client = GitHubDeviceAuth.github();
                final GitHubDeviceAuth.DeviceCode code = client.start();
                publish(code);
                final GitHubDeviceAuth.Token credential = client.poll(code);
                final RemoteActionProviders.Server candidate = new RemoteActionProviders.Server(
                        RemoteActionProviders.Settings.TYPE_GITHUB, "GitHub", "https://github.com",
                        "https://api.github.com", environment, accountEnabled, true);
                return new BrowserLogin(candidate, credential, AccountLogin.verify(candidate, credential.accessToken()));
            }

            @Override protected void process(final List<GitHubDeviceAuth.DeviceCode> codes) {
                if (isCancelled()) return;
                deviceCode.setText(codes.get(codes.size() - 1).userCode());
                deviceCode.setVisible(true);
                status.setText(GitHubWorkflowBundle.message("settings.account.browserCode"));
                pack();
                BrowserUtil.browse("https://github.com/login/device");
            }

            @Override protected void done() {
                try {
                    final BrowserLogin login = get();
                    if (login.verified().status() != AccountLogin.Status.SUCCESS) {
                        setErrorText(GitHubWorkflowBundle.message("settings.account.error." + login.verified().status().name().toLowerCase(Locale.ROOT)));
                        return;
                    }
                    final RemoteActionProviders.Server candidate = login.server();
                    result = new RemoteActionProviders.Server(candidate.type, login.verified().login().orElseThrow(),
                            candidate.webUrl, candidate.apiUrl, candidate.tokenEnvVar, candidate.enabled, true).normalized();
                    resultToken = login.token().encode();
                    token.setText("");
                    getOKAction().setEnabled(true);
                    AccountDialog.super.doOKAction();
                } catch (final CancellationException ignored) {
                    return;
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setErrorText(GitHubWorkflowBundle.message("settings.account.browserFailed"));
                } catch (final ExecutionException exception) {
                    setErrorText(GitHubWorkflowBundle.message("settings.account.browserFailed"));
                } finally {
                    status.setText("");
                    getOKAction().setEnabled(true);
                    button.setEnabled(true);
                    webUrl.setEnabled(true);
                    apiUrl.setEnabled(true);
                }
            }
        };
        browserLogin.execute();
    }

    private record BrowserLogin(RemoteActionProviders.Server server, GitHubDeviceAuth.Token token, AccountLogin.Result verified) { }

    private void updateApi() {
        if (!apiCustomized) {
            updatingApi = true;
            apiUrl.setText(apiFor(github, webUrl.getText().trim()));
            updatingApi = false;
        }
    }

    private void markCustomized() {
        if (!updatingApi) {
            apiCustomized = true;
        }
    }

    private static String apiFor(final boolean github, final String web) {
        final String clean = web.replaceAll("/+$", "");
        try {
            final URI uri = URI.create(clean);
            if (github && "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && (uri.getPath() == null || uri.getPath().isEmpty())) {
                return "https://api.github.com";
            }
        } catch (final IllegalArgumentException ignored) {
            // The server is still being edited.
        }
        return clean + (github ? "/api/v3" : "/api/v1");
    }

    private static boolean validUrl(final String value) {
        try {
            final URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (final IllegalArgumentException exception) {
            return false;
        }
    }

    private static void addRow(final JPanel panel, final int row, final String label, final JComponent component) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 0, 4, 8);
        final JLabel text = new JLabel(label);
        text.setLabelFor(component);
        panel.add(text, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        panel.add(component, constraints);
    }
}
