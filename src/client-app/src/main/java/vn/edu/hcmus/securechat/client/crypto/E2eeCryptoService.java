package vn.edu.hcmus.securechat.client.crypto;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest;

/**
 * E2EE Crypto Service — kết nối Chat Server thực sự, thực hiện handshake Kerberos ST.
 * Sau handshake thành công, giữ socket mở để gửi/nhận tin nhắn.
 */
public class E2eeCryptoService {
    private static final Logger log = LoggerFactory.getLogger(E2eeCryptoService.class);

    private byte[] masterSessionKey;
    private Socket chatSocket;

    /**
     * Kết nối đến Chat Server và xác thực bằng ST (Service Ticket).
     * @param username tên đăng nhập
     * @param password mật khẩu (để đọc ST từ cache)
     * @return true nếu handshake thành công
     */
    public void performHandshake(String username, char[] password) throws Exception {
        log.info("Kết nối đến Chat Server {}:{}", ServerConfig.CHAT_HOST, ServerConfig.CHAT_PORT);
        byte[] stCacheData = null;
        byte[] sessionKey = null;
        byte[] authBytes = null;
        byte[] encryptedAuth = null;
        try {
            // 1. Đọc ST từ TicketCache
            stCacheData = vn.edu.hcmus.securechat.client.kerberos.TicketCache
                    .getTicket(username, "ST_" + ServerConfig.CHAT_HOST, password);
            if (stCacheData == null) {
                throw new Exception("Không tìm thấy ST trong cache. Vui lòng đảm bảo bạn đã đăng nhập và không bị timeout.");
            }
            // Format: stBase64|||sessionKeyBase64
            String cacheStr = new String(stCacheData, StandardCharsets.UTF_8);
            String[] parts = cacheStr.split("\\|\\|\\|");
            if (parts.length != 2) {
                throw new Exception("Định dạng dữ liệu ST cache không hợp lệ.");
            }
            String stBase64 = parts[0];
            sessionKey = Base64.getDecoder().decode(parts[1]);

            // 2. Tạo Authenticator mã hóa bằng K_A_Chat
            String nonce = randomNonceBase64();
            long timestamp = Instant.now().getEpochSecond();
            AuthenticatorJson auth = new AuthenticatorJson(username, timestamp, nonce);
            authBytes = JsonSerializer.toBytes(auth);
            encryptedAuth = AesGcmCipher.encrypt(sessionKey, authBytes);
            String authenticatorB64 = Base64.getEncoder().encodeToString(encryptedAuth);

            // ChatServer hiện hỗ trợ fallback: nếu ECDHE/Kyber chưa được cấp public key
            // ở bước server-hello, K_A_Chat từ ST được dùng làm session key chung.
            this.masterSessionKey = Arrays.copyOf(sessionKey, sessionKey.length);
            ChatHandshakeRequest payload = new ChatHandshakeRequest(
                    stBase64, authenticatorB64, null, null, null);

            chatSocket = new Socket(ServerConfig.CHAT_HOST, ServerConfig.CHAT_PORT);
            chatSocket.setSoTimeout(ServerConfig.READ_TIMEOUT_MS);

            byte[] payloadBytes = JsonSerializer.toBytes(payload);
            PacketFrame.write(chatSocket.getOutputStream(), PacketFrame.TYPE_CHAT_HANDSHAKE, payloadBytes);

            // 6. Đọc response
            PacketFrame response = PacketFrame.read(chatSocket.getInputStream());
            if (response.getType() == PacketFrame.TYPE_ERROR) {
                String err = new String(response.getPayload(), StandardCharsets.UTF_8);
                chatSocket.close();
                throw new Exception("Chat Server từ chối handshake: " + err);
            }
            if (response.getType() != PacketFrame.TYPE_CHAT_HANDSHAKE) {
                chatSocket.close();
                throw new Exception("Chat Server trả về response không hợp lệ.");
            }

            // Tắt timeout cho long-lived socket
            chatSocket.setSoTimeout(0);
            log.info("Chat Server handshake thành công cho user={}", username);

        } catch (Exception e) {
            log.error("E2EE Handshake thất bại", e);
            throw new Exception("Handshake đến Chat Server thất bại: " + e.getMessage(), e);
        } finally {
            if (stCacheData != null) Arrays.fill(stCacheData, (byte) 0);
            if (sessionKey != null) Arrays.fill(sessionKey, (byte) 0);
            if (authBytes != null) Arrays.fill(authBytes, (byte) 0);
            if (encryptedAuth != null) Arrays.fill(encryptedAuth, (byte) 0);
        }
    }

    public byte[] getMasterSessionKey() {
        return masterSessionKey;
    }

    /** Socket đến Chat Server — giữ mở để gửi/nhận tin nhắn sau handshake. */
    public Socket getChatSocket() {
        return chatSocket;
    }

    /** Đóng kết nối đến Chat Server. */
    public void disconnect() {
        if (chatSocket != null && !chatSocket.isClosed()) {
            try { chatSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static String randomNonceBase64() {
        byte[] nonce = new byte[CryptoConstants.NONCE_SIZE_BYTES];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }
}
