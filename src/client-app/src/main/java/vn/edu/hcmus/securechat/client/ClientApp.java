package vn.edu.hcmus.securechat.client;

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
 * SecureChat desktop client with E2EE messaging.
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
        setTitle("SecureChat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setSize(1100, 720);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
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
                        "Account @" + username + " was created successfully.\nYou can sign in now.",
                        "Registration successful",
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
     * Runs Kerberos and E2EE handshake before entering the chat screen.
     */
    private void connectInBackground(String username, char[] password) {
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                try {
                    log.info("Establishing secure session for user={}", username);
                    loginPanel.trace("Synchronize time", "Fetching network time to prevent replay within the 300-second window.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    NtpTimeClient.syncTime();
                    loginPanel.trace("Time validated", "The authenticator timestamp will use the synchronized clock.",
                            ActivityFlowPanel.Tone.SUCCESS);
                    KerberosClient kerberosClient = new KerberosClient();
                    loginPanel.trace("Request TGT", "The client signs the TGT request with the X.509 certificate and sends it to AS.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    kerberosClient.requestTgt(username, password);
                    loginPanel.trace("TGT issued", "AS returned the TGT and K_A_TGS; the local cache is encrypted with Argon2id.",
                            ActivityFlowPanel.Tone.SUCCESS);
                    loginPanel.trace("Request ST", "TGS checks the TGT, authenticator, and Proof-of-Possession before issuing the chat ticket.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    kerberosClient.requestSt(username, password, ServerConfig.CHAT_HOST);
                    loginPanel.trace("ST issued", "The Service Ticket contains Control Vector CHAT_SERVICE and key K_A_ChatAuth.",
                            ActivityFlowPanel.Tone.SUCCESS);

                    // Request ST for Notification Server (M\u1ee5c 3: Multi-service SSO)
                    loginPanel.trace("Request Notif ST", "Requesting ST for Notification Server to demonstrate Multi-service SSO.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    kerberosClient.requestSt(username, password, ServerConfig.NOTIFICATION_SERVICE_ID);
                    loginPanel.trace("Notif ST issued", "Successfully received Service Ticket for Notification Server.",
                            ActivityFlowPanel.Tone.SUCCESS);
                    E2eeCryptoService e2eeService = new E2eeCryptoService();
                    e2eeService.setActivitySink((title, body, tone) ->
                            loginPanel.trace(title, body, ActivityFlowPanel.Tone.valueOf(tone.name())));
                    e2eeService.performHandshake(username, password);
                    loginPanel.trace("Unlock local database", "Chat history is unlocked with an Argon2id-derived key.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    LocalDatabase localDb = new LocalDatabase(username);
                    localDb.unlockDatabase(password);
                    if (!localDb.isUnlocked()) {
                        throw new vn.edu.hcmus.securechat.common.exception.KeyDerivationException(
                                "Unable to unlock local chat history");
                    }
                    loginPanel.trace("Session ready", "Sign-in is complete; message content will use Double Ratchet.",
                            ActivityFlowPanel.Tone.SUCCESS);

                    Thread renewThread = new Thread(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            try {
                                Thread.sleep(60000); // Check every 60s
                                kerberosClient.renewTgtIfNeeded(username);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            } catch (Exception ex) {
                                log.warn("Failed to auto-renew TGT in background", ex);
                            }
                        }
                    });
                    renewThread.setDaemon(true);
                    renewThread.setName("tgt-renewer");
                    renewThread.start();

                    startNotificationClient(username, password);

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
                        loginPanel.showAuthError("Connection failed. Please check the server and try again.");
                    }
                } catch (Exception e) {
                    log.error("Connection failed", e);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage();
                    
                    String friendlyMessage = "Sign-in failed. Please check your account information or network connection.";
                    if (message != null) {
                        String msgLower = message.toLowerCase();
                        if (msgLower.contains("incorrect password") || msgLower.contains("invalid key") || msgLower.contains("decrypt") || msgLower.contains("argon2") || msgLower.contains("badpadding")) {
                            friendlyMessage = "Incorrect password. Please try again.";
                        } else if (msgLower.contains("user not found") || msgLower.contains("principal") || msgLower.contains("does not exist")) {
                            friendlyMessage = "Account does not exist. Please check again.";
                        } else if (msgLower.contains("refused") || msgLower.contains("connect") || msgLower.contains("timeout") || msgLower.contains("server")) {
                            friendlyMessage = "Unable to connect to the server. Please check your network connection.";
                        } else if (msgLower.contains("expired") || msgLower.contains("time") || msgLower.contains("clock skew")) {
                            friendlyMessage = "Your computer time does not match or the request expired. Please try again.";
                        } else if (msgLower.contains("chat history") || msgLower.contains("database")) {
                            friendlyMessage = "Unable to open chat history. Please check your password.";
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
            if (loginPanel != null) {
                loginPanel.reset();
            }
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
        JOptionPane.showMessageDialog(this, message, "SecureChat - Error", JOptionPane.ERROR_MESSAGE);
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

    private void startNotificationClient(String username, char[] password) {
        // L\u1ea5y ST notification c\u00f3 tr\u01b0\u1edbc khi x\u00f3a password
        byte[] stData = vn.edu.hcmus.securechat.client.kerberos.TicketCache.getTicket(username, "ST_" + ServerConfig.NOTIFICATION_SERVICE_ID, password);
        if (stData == null) {
            log.warn("Notification ST not found, notification client won't start");
            return;
        }
        
        Thread t = new Thread(() -> {
            try {
                String cacheStr = new String(stData, java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = cacheStr.split("\\|\\|\\|");
                String stBase64 = parts[0];
                byte[] sessionKey = java.util.Base64.getDecoder().decode(parts[1]);

                long timestamp = NtpTimeClient.getCurrentNetworkTime() / 1000L;
                byte[] nonceBytes = new byte[vn.edu.hcmus.securechat.common.crypto.CryptoConstants.NONCE_SIZE_BYTES];
                new java.security.SecureRandom().nextBytes(nonceBytes);
                String authNonce = java.util.Base64.getEncoder().encodeToString(nonceBytes);

                vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson auth = 
                        new vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson(
                                username, timestamp, authNonce, "", "NOTIFICATION", 1L, "");
                
                byte[] authBytes = vn.edu.hcmus.securechat.common.protocol.JsonSerializer.toBytes(auth);
                byte[] encryptedAuth = vn.edu.hcmus.securechat.common.crypto.AesGcmCipher.encrypt(sessionKey, authBytes);
                String authBase64 = java.util.Base64.getEncoder().encodeToString(encryptedAuth);

                vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest req = 
                        new vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest(stBase64, authBase64, "", "", "");
                byte[] reqBytes = vn.edu.hcmus.securechat.common.protocol.JsonSerializer.toBytes(req);
                vn.edu.hcmus.securechat.common.protocol.PacketFrame frame = 
                        new vn.edu.hcmus.securechat.common.protocol.PacketFrame(vn.edu.hcmus.securechat.common.protocol.PacketFrame.TYPE_CHAT_HANDSHAKE, (byte) 1, (short) 0, reqBytes);

                try (java.net.Socket socket = new java.net.Socket(ServerConfig.NOTIFICATION_HOST, ServerConfig.NOTIFICATION_PORT)) {
                    socket.setSoTimeout(0); // keep alive
                    
                    vn.edu.hcmus.securechat.common.protocol.PacketFrame.write(socket.getOutputStream(), frame.getType(), frame.getPayload());
                    vn.edu.hcmus.securechat.common.protocol.PacketFrame response = vn.edu.hcmus.securechat.common.protocol.PacketFrame.read(socket.getInputStream());
                    
                    if (response.getType() == vn.edu.hcmus.securechat.common.protocol.PacketFrame.TYPE_CHAT_MESSAGE) {
                        log.info("Notification client successfully connected");
                        
                        while (true) {
                            vn.edu.hcmus.securechat.common.protocol.PacketFrame msgFrame = vn.edu.hcmus.securechat.common.protocol.PacketFrame.read(socket.getInputStream());
                            if (msgFrame.getType() == vn.edu.hcmus.securechat.common.protocol.PacketFrame.TYPE_CHAT_MESSAGE) {
                                vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope env = 
                                        vn.edu.hcmus.securechat.common.protocol.JsonSerializer.fromBytes(msgFrame.getPayload(), vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope.class);
                                
                                if ("SYSTEM".equals(env.getSenderId())) {
                                    byte[] encData = java.util.Base64.getDecoder().decode(env.getPayload());
                                    byte[] plainBytes = vn.edu.hcmus.securechat.common.crypto.AesGcmCipher.decrypt(sessionKey, encData);
                                    String message = new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
                                    
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        javax.swing.JOptionPane.showMessageDialog(null, "System Notification: " + message, "Notification", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                                    });
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (!(e instanceof java.io.EOFException) && !(e instanceof java.net.SocketException)) {
                    log.error("Notification client error", e);
                }
            }
        });
        t.setDaemon(true);
        t.setName("notification-client");
        t.start();
    }
}
