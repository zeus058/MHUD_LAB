package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Form đăng nhập — username + password (char[]).
 */
public class LoginPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(LoginPanel.class);

    public interface AuthListener {
        void onLoginSuccess(String username, char[] password);

        void onNavigateRegister();

        void onAuthError(String message);
    }

    private final AuthListener listener;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel statusLabel;
    private final JButton loginButton;
    private final ActivityFlowPanel flowPanel;

    public LoginPanel(AuthListener listener) {
        this.listener = listener;
        setOpaque(true);
        setBackground(UIConstants.DEEP_CARBON);
        setLayout(new BorderLayout(20, 0));

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);

        UiStyles.RoundedPanel card = UiStyles.cardPanel();
        card.setPreferredSize(new Dimension(430, 430));
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 4, 0);

        JLabel badge = UiStyles.statusBadge("SECURE ACCESS · KERBEROS",
                UIConstants.SECURE_TEAL, UIConstants.BORDER_SUBTLE);
        c.insets = new Insets(0, 0, 12, 0);
        card.add(badge, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 4, 0);
        card.add(UiStyles.titleLabel("Đăng nhập SecureChat"), c);

        c.gridy++;
        card.add(UiStyles.bodyLabel("Mở phiên Kerberos, xác thực PKI và chuẩn bị E2EE v2"), c);

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
        gbc.insets = new Insets(32, 52, 32, 52);
        center.add(card, gbc);

        add(center, BorderLayout.CENTER);
        flowPanel = new ActivityFlowPanel("Luồng đăng nhập",
                "Theo dõi xác thực, cấp vé và thiết lập phiên bảo mật");
        flowPanel.setImportantOnly(true);
        flowPanel.setPreferredSize(new Dimension(390, 0));
        flowPanel.seed(new String[][] {
                {"Sẵn sàng xác thực", "Nhập tài khoản để mở khóa keystore PKCS#12 cục bộ.", "INFO"},
                {"Kiểm tra chứng chỉ", "Client sẽ dùng X.509 để ký Proof-of-Possession.", "INFO"},
                {"Tách khóa nội dung", "KDC chỉ cấp khóa truy cập; nội dung chat sẽ dùng Pre-Key và ratchet.", "INFO"}
        });
        add(flowPanel, BorderLayout.EAST);
        setBorder(new EmptyBorder(24, 24, 24, 24));
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        statusLabel.setText(" ");
        if (username.isEmpty() || password.length == 0) {
            statusLabel.setForeground(UIConstants.SIGNAL_RED);
            statusLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            Arrays.fill(password, '\0');
            return;
        }

        trace("Bắt đầu Kerberos", "Đang mở identity keystore của @" + username
                + " và chuẩn bị ký request TGT/ST.", ActivityFlowPanel.Tone.ACTIVE);
        listener.onLoginSuccess(username, password.clone());
        Arrays.fill(password, '\0');
    }

    /** ClientApp gọi khi đang thiết lập phiên Kerberos + E2EE. */
    public void setConnecting(boolean connecting) {
        setFormEnabled(!connecting);
        if (connecting) {
            statusLabel.setForeground(UIConstants.SECURE_TEAL);
            statusLabel.setText("Đang kết nối máy chủ và thiết lập E2EE...");
            loginButton.setText("Đang kết nối...");
            trace("Đang xử lý phiên", "NTP, TGT, ST, Chat handshake và Pre-Key upload sẽ chạy tuần tự.",
                    ActivityFlowPanel.Tone.ACTIVE);
        } else {
            loginButton.setText("Đăng nhập");
            passwordField.setText("");
        }
    }

    public void showAuthError(String message) {
        statusLabel.setForeground(UIConstants.SIGNAL_RED);
        statusLabel.setText(message);
        trace("Đăng nhập bị từ chối", message, ActivityFlowPanel.Tone.ERROR);
        setFormEnabled(true);
        loginButton.setText("Đăng nhập");
    }

    public void trace(String title, String body, ActivityFlowPanel.Tone tone) {
        flowPanel.addEvent(title, body, tone);
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }
}
