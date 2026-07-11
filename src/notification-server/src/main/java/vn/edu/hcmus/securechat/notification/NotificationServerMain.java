package vn.edu.hcmus.securechat.notification;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.HybridEncryption;
import vn.edu.hcmus.securechat.common.crypto.KeyStoreManager;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;

import vn.edu.hcmus.securechat.common.protocol.dto.StInner;

public class NotificationServerMain {
    private static final Logger log = LoggerFactory.getLogger(NotificationServerMain.class);
    
    private final int port;
    private final ExecutorService threadPool;
    private java.security.PrivateKey privateKey;

    public NotificationServerMain(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void start() {
        try {
            loadKey();
            log.info("Notification Server initialized. Port = {}", port);
            
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("Notification Server listening on port {}", port);
                while (true) {
                    Socket socket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(socket));
                }
            }
        } catch (Exception e) {
            log.error("Notification Server startup failed", e);
            System.exit(1);
        }
    }
    
    private void loadKey() throws Exception {
        String alias = "securechat-notification";
        try {
            KeyStoreManager.KeyPairEntry keys = KeyStoreManager.loadKeyPair(alias);
            privateKey = keys.privateKey();
        } catch (Exception e) {
            log.warn("Could not load Notification Server private key from: {}. Trying fallback chat key...", alias);
            KeyStoreManager.KeyPairEntry chatKeys = KeyStoreManager.loadKeyPair("securechat-chat");
            privateKey = chatKeys.privateKey();
        }
        if (privateKey == null) {
            throw new Exception("Could not load Notification Server private key");
        }
    }

    private void handleClient(Socket socket) {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        log.info("New connection from {}", clientAddr);
        byte[] sessionKey = null;
        try {
            socket.setSoTimeout(ServerConfig.READ_TIMEOUT_MS);
            
            PacketFrame frame = PacketFrame.read(socket.getInputStream());
            if (frame.getType() != MessageType.CHAT_HANDSHAKE.getCode()) {
                throw new Exception("Expected CHAT_HANDSHAKE, got type: " + frame.getType());
            }
            
            vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest request = JsonSerializer.fromBytes(frame.getPayload(), vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest.class);
            byte[] encryptedSt = Base64.getDecoder().decode(request.getSt());
            byte[] stBytes = HybridEncryption.decrypt(privateKey, encryptedSt);
            StInner stInner = JsonSerializer.fromBytes(stBytes, StInner.class);
            
            if (!ServerConfig.NOTIFICATION_SERVICE_ID.equals(stInner.getTargetServer())) {
                throw new Exception("Invalid target server in ST: " + stInner.getTargetServer());
            }
            
            long now = Instant.now().getEpochSecond();
            if (now > stInner.getExpiresAt()) {
                throw new Exception("Service Ticket is expired");
            }
            
            sessionKey = Base64.getDecoder().decode(stInner.getSessionKey());
            byte[] encryptedAuth = Base64.getDecoder().decode(request.getAuthenticator());
            byte[] authBytes = AesGcmCipher.decrypt(sessionKey, encryptedAuth);
            AuthenticatorJson auth = JsonSerializer.fromBytes(authBytes, AuthenticatorJson.class);
            
            if (!stInner.getClientId().equals(auth.getClientId())) {
                throw new Exception("ClientId mismatch in authenticator");
            }
            
            log.info("Notification client authenticated: {}", stInner.getClientId());
            
            PacketFrame.write(socket.getOutputStream(), vn.edu.hcmus.securechat.common.protocol.PacketFrame.TYPE_CHAT_MESSAGE, "SUCCESS".getBytes(StandardCharsets.UTF_8));
            
            // Wait briefly then push a notification message
            Thread.sleep(1000);
            
            vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope env = 
                new vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope("SYSTEM", 
                    Base64.getEncoder().encodeToString(
                        AesGcmCipher.encrypt(sessionKey, "Welcome to SecureChat Notification Server! You will receive system alerts here.".getBytes(StandardCharsets.UTF_8))
                    ));
            
            PacketFrame.write(socket.getOutputStream(), PacketFrame.TYPE_CHAT_MESSAGE, JsonSerializer.toBytes(env));
            
            // Keep connection alive for future notifications if needed
            while (true) {
                Thread.sleep(10000);
            }
            
        } catch (Exception e) {
            log.error("Notification connection error from {}: {}", clientAddr, e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
            if (sessionKey != null) {
                Arrays.fill(sessionKey, (byte) 0);
            }
        }
    }

    public static void main(String[] args) {
        new NotificationServerMain(ServerConfig.NOTIFICATION_PORT).start();
    }
}
