package com.github.yunabraska.githubworkflow.settings;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sun.net.httpserver.HttpServer;

import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountDialogTest extends BasePlatformTestCase {
    public void testLoginDialogVerifiesTokenAndUsesServerAccountName() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/user", exchange -> {
            final byte[] body = "{\"login\":\"actual-user\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        final AccountDialog dialog = new AccountDialog(new JPanel(), true, null);
        final Container content = dialog.createCenterPanel();
        try {
            field(content, "Server:", JTextField.class)
                    .setText("http://127.0.0.1:" + server.getAddress().getPort());
            component(content, JPasswordField.class).setText("test-token");
            dialog.performOKAction();
            PlatformTestUtil.waitWithEventsDispatching("Account login", dialog::isOK, 5);
            assertThat(dialog.result().name).isEqualTo("actual-user");
            assertThat(dialog.resultToken()).isEqualTo("test-token");
        } finally {
            if (!dialog.isOK()) dialog.doCancelAction();
            server.stop(0);
        }
    }

    public void testLoginDialogKeepsInvalidTokenOutOfSettings() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/user", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        final AccountDialog dialog = new AccountDialog(new JPanel(), false, null);
        final Container content = dialog.createCenterPanel();
        try {
            field(content, "Server:", JTextField.class)
                    .setText("http://127.0.0.1:" + server.getAddress().getPort());
            component(content, JPasswordField.class).setText("bad-token");
            dialog.performOKAction();
            PlatformTestUtil.waitWithEventsDispatching("Account login failure", dialog::isOKActionEnabled, 15);
            assertThat(dialog.isOK()).isFalse();
            assertThat(dialog.result()).isNull();
            assertThat(dialog.resultToken()).isNull();
        } finally {
            dialog.doCancelAction();
            server.stop(0);
        }
    }

    public void testRenamingAnAccountKeepsItsStoredToken() {
        final var account = new RemoteActionProviders.Server(
                RemoteActionProviders.Settings.TYPE_GITHUB, "old-name", "https://github.com",
                "https://api.github.com", "", true, true);
        final AccountDialog dialog = new AccountDialog(new JPanel(), true, account);
        final Container content = dialog.createCenterPanel();
        try {
            field(content, "Account name:", JTextField.class).setText("new-name");
            dialog.performOKAction();
            assertThat(dialog.result()).isNotNull();
            assertThat(dialog.result().name).isEqualTo("new-name");
            assertThat(dialog.result().tokenStored).isTrue();
        } finally {
            dialog.doCancelAction();
        }
    }

    private static <T extends Component> T field(final Container parent, final String text, final Class<T> type) {
        for (final Component component : parent.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText()) && type.isInstance(label.getLabelFor())) {
                return type.cast(label.getLabelFor());
            }
            if (component instanceof Container child) {
                final T value = fieldOrNull(child, text, type);
                if (value != null) return value;
            }
        }
        throw new AssertionError("Field not found: " + text);
    }

    private static <T extends Component> T fieldOrNull(final Container parent, final String text, final Class<T> type) {
        for (final Component component : parent.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText()) && type.isInstance(label.getLabelFor())) return type.cast(label.getLabelFor());
            if (component instanceof Container child) {
                final T value = fieldOrNull(child, text, type);
                if (value != null) return value;
            }
        }
        return null;
    }

    private static <T extends Component> T component(final Container parent, final Class<T> type) {
        if (type.isInstance(parent)) return type.cast(parent);
        for (final Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                final T value = componentOrNull(container, type);
                if (value != null) return value;
            }
        }
        throw new AssertionError("Component not found: " + type);
    }

    private static <T extends Component> T componentOrNull(final Container parent, final Class<T> type) {
        for (final Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                final T value = componentOrNull(container, type);
                if (value != null) return value;
            }
        }
        return null;
    }
}
