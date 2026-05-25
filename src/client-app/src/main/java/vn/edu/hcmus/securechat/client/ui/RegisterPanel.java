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
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.crypto.PkiManager;
import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;
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
    private final ActivityFlowPanel flowPanel;


    public RegisterPanel(RegisterListener listener) {
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

        UiStyles.RoundedPanel card = UiStyles.cardPanel();
        card.setPreferredSize(new Dimension(430, 580));
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 4, 0);

        JLabel badge = UiStyles.statusBadge("CERTIFICATE SETUP · PKI",
                UIConstants.SECURE_TEAL, UIConstants.BORDER_SUBTLE);
        c.insets = new Insets(0, 0, 12, 0);
        card.add(badge, c);

        c.gridy++;
        card.add(UiStyles.titleLabel("Đăng ký danh tính"), c);

        c.gridy++;
        card.add(UiStyles.bodyLabel("Sinh keypair, ký CSR và nhận chứng chỉ X.509"), c);

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
        loginRow.add(UiStyles.mutedLabel("Đã có tài khoản?"));
        JButton loginLink = UiStyles.linkButton("Đăng nhập");
        loginLink.addActionListener(e -> listener.onNavigateLogin());
        loginRow.add(loginLink);
        card.add(loginRow, c);

        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(28, 52, 28, 52);
        center.add(card, gbc);

        add(center, BorderLayout.CENTER);
        flowPanel = new ActivityFlowPanel("Luồng đăng ký",
                "CSR, Proof-of-Possession và phát hành chứng chỉ");
        flowPanel.setImportantOnly(true);
        flowPanel.setPreferredSize(new Dimension(390, 0));
        flowPanel.seed(new String[][] {
                {"Chuẩn bị danh tính", "Client sẽ sinh RSA-2048 identity keypair ngay trên máy người dùng.", "INFO"},
                {"Tạo CSR", "CSR chứa public key, nonce và chữ ký chứng minh sở hữu private key.", "INFO"},
                {"Nhận chứng chỉ", "CA trả X.509 và chuỗi CA để lưu vào keystore PKCS#12.", "INFO"}
        });
        add(flowPanel, BorderLayout.EAST);
        setBorder(new EmptyBorder(24, 24, 24, 24));
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
            if (ClientStoragePaths.keystoreExists(username)
                    || java.nio.file.Files.isRegularFile(
                            java.nio.file.Path.of("data/client", "keystore_" + username + ".p12"))) {
                statusLabel.setForeground(UIConstants.SECURE_TEAL);
                statusLabel.setText("“" + username + "” đã đăng ký chứng chỉ rồi. Vui lòng đăng nhập!");
                trace("Chứng chỉ đã tồn tại", "@" + username
                        + " đã có keystore cục bộ nên có thể chuyển sang đăng nhập.", ActivityFlowPanel.Tone.INFO);
                Arrays.fill(password, '\0');
                return;
            }
        } finally {
            Arrays.fill(confirm, '\0');
        }

        setFormEnabled(false);
        statusLabel.setForeground(UIConstants.SECURE_TEAL);
        statusLabel.setText("Đang tạo CSR và gửi tới CA...");
        trace("Bắt đầu đăng ký", "Đang sinh khóa, tạo CSR và gửi tới CA Server.", ActivityFlowPanel.Tone.ACTIVE);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    // 1. Khởi tạo KeyPair
                    trace("Sinh identity keypair", "Private key được tạo cục bộ và không gửi ra mạng.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    PkiManager.generateKeyPair(username);
                    
                    // 2. Tạo Request gửi CA
                    trace("Ký CSR", "CSR được ký bằng private key để CA kiểm tra Proof-of-Possession.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    CertificateSigningRequest req = PkiManager.createCsrPayload("CN=" + username + ", O=Mock");
                    
                    PacketFrame frame = new PacketFrame(PacketFrame.TYPE_CSR_REQUEST, (byte)1, (short)0, JsonSerializer.toBytes(req));
                    
                    // 3. Gửi SocketClient
                    trace("Gửi tới CA", "PacketFrame TYPE_CSR_REQUEST được gửi qua socket length-prefix.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    PacketFrame response = SocketClient.sendRequest(vn.edu.hcmus.securechat.common.config.ServerConfig.CA_HOST, vn.edu.hcmus.securechat.common.config.ServerConfig.CA_PORT, frame);
                    
                    if (response.getType() == PacketFrame.TYPE_CERT_RESPONSE) {
                        CertificateResponse certResp = JsonSerializer.fromBytes(response.getPayload(), CertificateResponse.class);
                        // 4. Lưu KeyStore với mật khẩu của người dùng
                        trace("Lưu keystore", "Chứng chỉ X.509 và chain CA được ghi vào PKCS#12 của @"
                                + username + ".", ActivityFlowPanel.Tone.ACTIVE);
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
                        trace("Đăng ký hoàn tất", "@" + username
                                + " đã có chứng chỉ để xin TGT/ST trong bước đăng nhập.", ActivityFlowPanel.Tone.SUCCESS);
                        listener.onRegisterSuccess(username);
                    } else {
                        statusLabel.setForeground(UIConstants.SIGNAL_RED);
                        statusLabel.setText("Đăng ký thất bại. Vui lòng thử lại.");
                        trace("Đăng ký thất bại", "CA không trả về certificate response hợp lệ.",
                                ActivityFlowPanel.Tone.ERROR);
                        listener.onAuthError("Đăng ký thất bại. Vui lòng thử lại.");
                    }
                } catch (Exception e) {
                    log.error("Register failed", e);
                    statusLabel.setForeground(UIConstants.SIGNAL_RED);
                    statusLabel.setText("Đăng ký thất bại. Vui lòng thử lại.");
                    trace("Đăng ký thất bại", e.getMessage(), ActivityFlowPanel.Tone.ERROR);
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

    public void trace(String title, String body, ActivityFlowPanel.Tone tone) {
        flowPanel.addEvent(title, body, tone);
    }
}
