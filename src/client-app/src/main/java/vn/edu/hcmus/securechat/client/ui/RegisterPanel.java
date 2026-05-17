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

import vn.edu.hcmus.securechat.client.crypto.PkiManager;
import vn.edu.hcmus.securechat.client.network.SocketClient;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateSigningRequest;

/**
 * Form đăng ký — tạo CSR gửi CA, nhận Certificate.
 */
public class RegisterPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(RegisterPanel.class);

    public interface RegisterListener {
        void onRegisterSuccess(String username);

        void onNavigateLogin();

        void onAuthError(String message);
    }

    private final RegisterListener listener;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmField;
    private final JLabel statusLabel;
    private final JButton registerButton;

    public RegisterPanel(RegisterListener listener) {
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

        UiStyles.RoundedPanel card = UiStyles.cardPanel();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 4, 0);

        JLabel badge = UiStyles.mutedLabel("PKI REGISTRATION");
        badge.setForeground(UIConstants.SECURE_TEAL);
        card.add(badge, c);

        c.gridy++;
        card.add(UiStyles.titleLabel("Đăng ký chứng chỉ"), c);

        c.gridy++;
        card.add(UiStyles.bodyLabel("Tạo CSR và nhận X.509 từ CA Server"), c);

        c.gridy++;
        c.insets = new Insets(16, 0, 6, 0);
        card.add(UiStyles.mutedLabel("Tên đăng nhập"), c);
        c.gridy++;
        usernameField = UiStyles.styledTextField(24);
        card.add(usernameField, c);


        c.gridy++;
        c.insets = new Insets(8, 0, 6, 0);
        card.add(UiStyles.mutedLabel("Mật khẩu"), c);
        c.gridy++;
        passwordField = UiStyles.styledPasswordField(24);
        card.add(passwordField, c);

        c.gridy++;
        c.insets = new Insets(8, 0, 6, 0);
        card.add(UiStyles.mutedLabel("Xác nhận mật khẩu"), c);
        c.gridy++;
        confirmField = UiStyles.styledPasswordField(24);
        card.add(confirmField, c);

        c.gridy++;
        c.insets = new Insets(16, 0, 8, 0);
        registerButton = UiStyles.primaryButton("Gửi CSR & nhận chứng chỉ");
        registerButton.addActionListener(e -> performRegister());
        card.add(registerButton, c);

        c.gridy++;
        statusLabel = UiStyles.mutedLabel(" ");
        statusLabel.setForeground(UIConstants.SIGNAL_RED);
        card.add(statusLabel, c);

        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        JPanel loginRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 6, 0));
        loginRow.setOpaque(false);
        JButton loginLink = UiStyles.linkButton("Đăng nhập");
        loginLink.addActionListener(e -> listener.onNavigateLogin());
        loginRow.add(loginLink);
        card.add(loginRow, c);

        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(24, 48, 24, 48);
        center.add(card, gbc);

        add(center, BorderLayout.CENTER);
        setBorder(new EmptyBorder(UIConstants.PADDING, UIConstants.PADDING,
                UIConstants.PADDING, UIConstants.PADDING));
    }

    private void performRegister() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();

        statusLabel.setText(" ");
        try {
            if (username.isEmpty() || password.length == 0) {
                statusLabel.setText("Vui lòng điền đầy đủ thông tin.");
                Arrays.fill(password, '\0');
                return;
            }
            if (!Arrays.equals(password, confirm)) {
                statusLabel.setText("Mật khẩu xác nhận không khớp.");
                Arrays.fill(password, '\0');
                return;
            }
            // Kiểm tra nếu user đã đăng ký rồi
            java.io.File ksFile = new java.io.File("data/client", "keystore_" + username + ".p12");
            if (ksFile.exists()) {
                statusLabel.setForeground(UIConstants.SECURE_TEAL);
                statusLabel.setText("“" + username + "” đã đăng ký chứng chỉ rồi. Vui lòng đăng nhập!");
                Arrays.fill(password, '\0');
                return;
            }
        } finally {
            Arrays.fill(confirm, '\0');
        }

        setFormEnabled(false);
        statusLabel.setForeground(UIConstants.SECURE_TEAL);
        statusLabel.setText("Đang tạo CSR và gửi tới CA...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    // 1. Khởi tạo KeyPair
                    PkiManager.generateKeyPair(username);
                    
                    // 2. Tạo Request gửi CA
                    CertificateSigningRequest req = PkiManager.createCsrPayload("CN=" + username + ", O=Mock");
                    
                    PacketFrame frame = new PacketFrame(PacketFrame.TYPE_CSR_REQUEST, (byte)1, (short)0, JsonSerializer.toBytes(req));
                    
                    // 3. Gửi SocketClient
                    PacketFrame response = SocketClient.sendRequest(vn.edu.hcmus.securechat.common.config.ServerConfig.CA_HOST, vn.edu.hcmus.securechat.common.config.ServerConfig.CA_PORT, frame);
                    
                    if (response.getType() == PacketFrame.TYPE_CERT_RESPONSE) {
                        CertificateResponse certResp = JsonSerializer.fromBytes(response.getPayload(), CertificateResponse.class);
                        // 4. Lưu KeyStore với mật khẩu của người dùng
                        PkiManager.saveKeyStore(username, password, certResp.getCertificate(), certResp.getCaChain());
                        return true;
                    }
                } catch (Exception e) {
                    log.error("Lỗi đăng ký PKI", e);
                }
                return false;
            }

            @Override
            protected void done() {
                try {
                    if (Boolean.TRUE.equals(get())) {
                        listener.onRegisterSuccess(username);
                    } else {
                        statusLabel.setForeground(UIConstants.SIGNAL_RED);
                        statusLabel.setText("Đăng ký thất bại. Vui lòng thử lại.");
                        listener.onAuthError("Đăng ký thất bại. Vui lòng thử lại.");
                    }
                } catch (Exception e) {
                    log.error("Register failed", e);
                    statusLabel.setForeground(UIConstants.SIGNAL_RED);
                    statusLabel.setText("Đăng ký thất bại. Vui lòng thử lại.");
                    listener.onAuthError("Đăng ký thất bại. Vui lòng thử lại.");
                } finally {
                    Arrays.fill(password, '\0');
                    passwordField.setText("");
                    confirmField.setText("");
                    setFormEnabled(true);
                }
            }
        }.execute();
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmField.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }
}
