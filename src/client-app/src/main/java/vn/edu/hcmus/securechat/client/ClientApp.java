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

import vn.edu.hcmus.securechat.client.model.SecurityState;
import vn.edu.hcmus.securechat.client.model.SecurityState.ConnectionStatus;
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
                        "Chứng chỉ đã được cấp cho @" + username + ".\nBạn có thể đăng nhập ngay.",
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
        SecurityState connecting = new SecurityState();
        connecting.setStatus(ConnectionStatus.CONNECTING);

        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                try {
                    log.info("Establishing secure session for user={}", username);

                    // 1. Đồng bộ thời gian
                    NtpTimeClient.syncTime();

                    // 2. Lấy vé Kerberos
                    KerberosClient kerberosClient = new KerberosClient();
                    kerberosClient.requestTgt(username, password);
                    kerberosClient.requestSt(username, password, ServerConfig.CHAT_HOST);

                    // 3. Handshake E2EE thực sự với Chat Server (dùng ST)
                    E2eeCryptoService e2eeService = new E2eeCryptoService();
                    e2eeService.performHandshake(username, password);

                    // 4. Mở khóa CSDL nội bộ bằng PBKDF2 derived key
                    LocalDatabase localDb = new LocalDatabase(username);
                    localDb.unlockDatabase(password);
                    if (!localDb.isUnlocked()) {
                        throw new vn.edu.hcmus.securechat.common.exception.KeyDerivationException(
                                "Không mở khóa được lịch sử chat cục bộ");
                    }

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
                        loginPanel.showAuthError("Kết nối thất bại. Kiểm tra máy chủ và thử lại.");
                    }
                } catch (Exception e) {
                    log.error("Connection failed", e);
                    String message = e.getMessage() != null ? e.getMessage() : "Kết nối thất bại. Vui lòng thử lại.";
                    loginPanel.showAuthError(message);
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
