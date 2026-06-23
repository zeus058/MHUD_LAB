package vn.edu.hcmus.securechat.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.ui.ActivityFlowPanel;
import vn.edu.hcmus.securechat.client.ui.ChatPanel;
import vn.edu.hcmus.securechat.client.ui.LoginPanel;
import vn.edu.hcmus.securechat.client.ui.RegisterPanel;
import vn.edu.hcmus.securechat.client.ui.UIConstants;
import vn.edu.hcmus.securechat.client.ui.UiStyles;

import vn.edu.hcmus.securechat.client.network.NtpTimeClient;
import vn.edu.hcmus.securechat.client.kerberos.KerberosClient;
import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.client.db.LocalDatabase;
import vn.edu.hcmus.securechat.common.config.ServerConfig;

/**
 * Client Application — Ứng dụng Desktop chat bảo mật E2EE.
 * Owner: Trúc Ngọc | Reviewer: Chị Bee
 */
public class ClientApp extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ClientApp.class);

    private static final String CARD_LOGIN = "login";
    private static final String CARD_REGISTER = "register";
    private static final String CARD_CHAT = "chat";

    private final CardLayout authCards = new CardLayout();
    private final JPanel authContainer = new JPanel(authCards);
    private final CardLayout rootCards = new CardLayout();
    private final JPanel root = new JPanel(rootCards);

    private LoginPanel loginPanel;
    private RegisterPanel registerPanel;
    private ChatPanel chatPanel;

    public ClientApp() {
        setTitle("SecureChat E2EE v2.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setSize(1100, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UIConstants.DEEP_CARBON);

        buildAuthScreens();
        root.setBackground(UIConstants.DEEP_CARBON);
        root.add(authContainer, CARD_LOGIN);
        setContentPane(root);

        showLogin();
        log.info("ClientApp initialized");
    }

    private void buildAuthScreens() {
        loginPanel = new LoginPanel(new LoginPanel.AuthListener() {
            @Override
            public void onLoginSuccess(String username, char[] password) {
                openChatSession(username, password);
            }

            @Override
            public void onNavigateRegister() {
                showRegister();
            }

            @Override
            public void onAuthError(String message) {
                loginPanel.showAuthError(message);
            }
        });

        registerPanel = new RegisterPanel(new RegisterPanel.RegisterListener() {
            @Override
            public void onRegisterSuccess(String username) {
                JOptionPane.showMessageDialog(ClientApp.this,
                        "Tài khoản @" + username + " đã được tạo thành công.\nBạn có thể đăng nhập ngay.",
                        "Đăng ký thành công",
                        JOptionPane.INFORMATION_MESSAGE);
                showLogin();
            }

            @Override
            public void onNavigateLogin() {
                showLogin();
            }

            @Override
            public void onAuthError(String message) {
                showError(message);
            }
        });

        authContainer.setLayout(authCards);
        authContainer.setBackground(UIConstants.DEEP_CARBON);
        authContainer.add(loginPanel, CARD_LOGIN);
        authContainer.add(registerPanel, CARD_REGISTER);
    }

    private void openChatSession(String username, char[] password) {
        loginPanel.setConnecting(true);
        connectInBackground(username, password);
    }

    /**
     * Kết nối Kerberos + E2EE handshake trước khi vào màn chat.
     */
    private void connectInBackground(String username, char[] password) {
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                try {
                    log.info("Establishing secure session for user={}", username);

                    // 1. Đồng bộ thời gian
                    loginPanel.trace("Đồng bộ thời gian", "Lấy mốc thời gian mạng để chống replay trong cửa sổ 300 giây.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    NtpTimeClient.syncTime();
                    loginPanel.trace("Thời gian hợp lệ", "Timestamp cho authenticator sẽ dùng đồng hồ đã đồng bộ.",
                            ActivityFlowPanel.Tone.SUCCESS);

                    // 2. Lấy vé Kerberos
                    KerberosClient kerberosClient = new KerberosClient();
                    loginPanel.trace("Xin TGT", "Client ký TGT request bằng chứng chỉ X.509 và gửi tới AS.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    kerberosClient.requestTgt(username, password);
                    loginPanel.trace("TGT đã cấp", "AS đã trả TGT và K_A_TGS; cache cục bộ được mã hóa bằng Argon2id.",
                            ActivityFlowPanel.Tone.SUCCESS);
                    loginPanel.trace("Xin ST", "TGS kiểm tra TGT, authenticator và Proof-of-Possession để cấp vé chat.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    kerberosClient.requestSt(username, password, ServerConfig.CHAT_HOST);
                    loginPanel.trace("ST đã cấp", "Service Ticket chứa Control Vector CHAT_SERVICE và khóa K_A_ChatAuth.",
                            ActivityFlowPanel.Tone.SUCCESS);

                    // 3. Handshake E2EE thực sự với Chat Server (dùng ST)
                    E2eeCryptoService e2eeService = new E2eeCryptoService();
                    e2eeService.setActivitySink((title, body, tone) ->
                            loginPanel.trace(title, body, ActivityFlowPanel.Tone.valueOf(tone.name())));
                    e2eeService.performHandshake(username, password);

                    // 4. Mở khóa CSDL nội bộ bằng Argon2id-derived key
                    loginPanel.trace("Mở CSDL cục bộ", "Lịch sử chat được giải khóa bằng key dẫn xuất Argon2id.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    LocalDatabase localDb = new LocalDatabase(username);
                    localDb.unlockDatabase(password);
                    if (!localDb.isUnlocked()) {
                        throw new vn.edu.hcmus.securechat.common.exception.KeyDerivationException(
                                "Không mở khóa được lịch sử chat cục bộ");
                    }
                    loginPanel.trace("Phiên sẵn sàng", "Đăng nhập hoàn tất; nội dung tin nhắn sẽ dùng Double Ratchet.",
                            ActivityFlowPanel.Tone.SUCCESS);

                    return new Object[]{e2eeService, localDb};
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                loginPanel.setConnecting(false);
                try {
                    Object[] result = get();
                    if (result != null && result[0] != null) {
                        showChat(username, (E2eeCryptoService) result[0], (vn.edu.hcmus.securechat.client.db.LocalDatabase) result[1]);
                    } else {
                        loginPanel.showAuthError("Kết nối thất bại. Vui lòng kiểm tra máy chủ và thử lại.");
                    }
                } catch (Exception e) {
                    log.error("Connection failed", e);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage();
                    
                    String friendlyMessage = "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin tài khoản hoặc kết nối mạng.";
                    if (message != null) {
                        String msgLower = message.toLowerCase();
                        if (msgLower.contains("incorrect password") || msgLower.contains("mật khẩu không đúng") || msgLower.contains("invalid key") || msgLower.contains("decrypt") || msgLower.contains("argon2") || msgLower.contains("badpadding")) {
                            friendlyMessage = "Mật khẩu không đúng. Vui lòng thử lại.";
                        } else if (msgLower.contains("user not found") || msgLower.contains("không tìm thấy") || msgLower.contains("principal") || msgLower.contains("does not exist")) {
                            friendlyMessage = "Tài khoản không tồn tại. Vui lòng kiểm tra lại.";
                        } else if (msgLower.contains("refused") || msgLower.contains("connect") || msgLower.contains("timeout") || msgLower.contains("máy chủ")) {
                            friendlyMessage = "Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng của bạn.";
                        } else if (msgLower.contains("expired") || msgLower.contains("thời gian") || msgLower.contains("clock skew")) {
                            friendlyMessage = "Thời gian trên máy tính không khớp hoặc yêu cầu hết hạn. Vui lòng thử lại.";
                        } else if (msgLower.contains("lịch sử chat") || msgLower.contains("database")) {
                            friendlyMessage = "Không thể mở lịch sử trò chuyện. Vui lòng kiểm tra mật khẩu.";
                        }
                    }
                    loginPanel.showAuthError(friendlyMessage);
                }
            }
        }.execute();
    }

    private void showChat(String username, E2eeCryptoService e2ee, vn.edu.hcmus.securechat.client.db.LocalDatabase localDb) {
        if (chatPanel != null) {
            root.remove(chatPanel);
        }
        chatPanel = new ChatPanel(username, e2ee, localDb, () -> {
            e2ee.disconnect();
            root.remove(chatPanel);
            chatPanel = null;
            rootCards.show(root, CARD_LOGIN);
            authCards.show(authContainer, CARD_LOGIN);
            revalidate();
            repaint();
        });
        root.add(chatPanel, CARD_CHAT);
        rootCards.show(root, CARD_CHAT);
        revalidate();
        repaint();
        log.info("Chat session opened for user={}", username);
    }

    private void showLogin() {
        rootCards.show(root, CARD_LOGIN);
        authCards.show(authContainer, CARD_LOGIN);
    }

    private void showRegister() {
        rootCards.show(root, CARD_LOGIN);
        authCards.show(authContainer, CARD_REGISTER);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "SecureChat — Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        UiStyles.applyGlobalTheme();
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            log.warn("Could not set look and feel", e);
        }

        SwingUtilities.invokeLater(() -> {
            ClientApp app = new ClientApp();
            app.setVisible(true);
            log.info("SecureChat Client started");
        });
    }
}
