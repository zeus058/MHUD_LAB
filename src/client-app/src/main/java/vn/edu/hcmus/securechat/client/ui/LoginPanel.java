package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Form đăng nhập — username + password (char[]).
 */
public class LoginPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(LoginPanel.class);

    public interface AuthListener {
        void onLoginSuccess(String username, String password);

        void onNavigateRegister();

        void onAuthError(String message);
    }

    private final AuthListener listener;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel statusLabel;
    private final JButton loginButton;

    public LoginPanel(AuthListener listener) {
        this.listener = listener;
        setOpaque(true);
        setBackground(UIConstants.DEEP_CARBON);
        setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);

        UiStyles.RoundedPanel card = UiStyles.cardPanel();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 4, 0);

        JLabel badge = UiStyles.mutedLabel("ZERO-TRUST MESSAGING");
        badge.setForeground(UIConstants.SECURE_TEAL);
        card.add(badge, c);

        c.gridy++;
        card.add(UiStyles.titleLabel("SecureChat E2EE"), c);

        c.gridy++;
        card.add(UiStyles.bodyLabel("Đăng nhập để tiếp tục phiên bảo mật"), c);

        c.gridy++;
        c.insets = new Insets(20, 0, 6, 0);
        card.add(UiStyles.mutedLabel("Tên đăng nhập"), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        usernameField = UiStyles.styledTextField(24);
        card.add(usernameField, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(UiStyles.mutedLabel("Mật khẩu"), c);

        c.gridy++;
        passwordField = UiStyles.styledPasswordField(24);
        card.add(passwordField, c);

        c.gridy++;
        c.insets = new Insets(16, 0, 8, 0);
        loginButton = UiStyles.primaryButton("Đăng nhập");
        loginButton.addActionListener(e -> performLogin());
        card.add(loginButton, c);

        c.gridy++;
        statusLabel = UiStyles.mutedLabel(" ");
        statusLabel.setForeground(UIConstants.SIGNAL_RED);
        card.add(statusLabel, c);

        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        JPanel registerRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 6, 0));
        registerRow.setOpaque(false);
        registerRow.add(UiStyles.mutedLabel("Chưa có tài khoản?"));
        JButton registerLink = UiStyles.linkButton("Đăng ký chứng chỉ");
        registerLink.addActionListener(e -> listener.onNavigateRegister());
        registerRow.add(registerLink);
        card.add(registerRow, c);

        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(40, 48, 40, 48);
        center.add(card, gbc);

        add(center, BorderLayout.CENTER);
        setBorder(new EmptyBorder(UIConstants.PADDING, UIConstants.PADDING,
                UIConstants.PADDING, UIConstants.PADDING));
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        statusLabel.setText(" ");
        if (username.isEmpty() || password.length == 0) {
            statusLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            Arrays.fill(password, '\0');
            return;
        }

        setFormEnabled(false);
        statusLabel.setForeground(UIConstants.SECURE_TEAL);
        statusLabel.setText("Đang xác thực...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                // LoginPanel chỉ lo UI, việc connect sẽ ở ClientApp
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return true;
            }

            @Override
            protected void done() {
                try {
                    if (Boolean.TRUE.equals(get())) {
                        listener.onLoginSuccess(username, new String(password));
                    } else {
                        listener.onAuthError("Đăng nhập thất bại. Vui lòng thử lại.");
                        statusLabel.setForeground(UIConstants.SIGNAL_RED);
                        statusLabel.setText("Đăng nhập thất bại. Vui lòng thử lại.");
                    }
                } catch (Exception e) {
                    log.error("Login failed", e);
                    listener.onAuthError("Đăng nhập thất bại. Vui lòng thử lại.");
                    statusLabel.setForeground(UIConstants.SIGNAL_RED);
                    statusLabel.setText("Đăng nhập thất bại. Vui lòng thử lại.");
                } finally {
                    Arrays.fill(password, '\0');
                    passwordField.setText("");
                    setFormEnabled(true);
                }
            }
        }.execute();
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }
}
