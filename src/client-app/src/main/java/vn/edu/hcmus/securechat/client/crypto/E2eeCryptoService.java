package vn.edu.hcmus.securechat.client.crypto;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.HkdfKeyDerivation;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;

/**
 * E2EE Crypto Service — kết nối Chat Server thực sự, thực hiện handshake Kerberos ST.
 * Sau handshake thành công, giữ socket mở để gửi/nhận tin nhắn.
 */
public class E2eeCryptoService {
    private static final Logger log = LoggerFactory.getLogger(E2eeCryptoService.class);

    private byte[] masterSessionKey;
    private Socket chatSocket;
    private String username;

    /**
     * Kết nối đến Chat Server và xác thực bằng ST (Service Ticket).
     * @param username tên đăng nhập
     * @param password mật khẩu (để đọc ST từ cache)
     * @return true nếu handshake thành công
     */
    public void performHandshake(String username, String password) throws Exception {
        this.username = username;
        log.info("Kết nối đến Chat Server {}:{}", ServerConfig.CHAT_HOST, ServerConfig.CHAT_PORT);
        try {
            // 1. Đọc ST từ TicketCache
            byte[] stCacheData = vn.edu.hcmus.securechat.client.kerberos.TicketCache
                    .getTicket("ST_CHAT_SERVICE", password);
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
            byte[] sessionKey = Base64.getDecoder().decode(parts[1]);

            // 2. Tạo Authenticator mã hóa bằng K_A_Chat
            String nonce = UUID.randomUUID().toString();
            long timestamp = Instant.now().getEpochSecond();
            AuthenticatorJson auth = new AuthenticatorJson(username, timestamp, nonce);
            byte[] authBytes = JsonSerializer.toBytes(auth);
            byte[] encryptedAuth = AesGcmCipher.encrypt(sessionKey, authBytes);
            String authenticatorB64 = Base64.getEncoder().encodeToString(encryptedAuth);

            // 3. Tạo shared secrets (random) cho HKDF — trong thực tế dùng ECDHE + Kyber
            byte[] ssEcdhe = new byte[32];
            byte[] ssKyber = new byte[32];
            byte[] sessionNonce = new byte[32];
            new SecureRandom().nextBytes(ssEcdhe);
            new SecureRandom().nextBytes(ssKyber);
            new SecureRandom().nextBytes(sessionNonce);

            // 4. Tính Master Session Key
            this.masterSessionKey = HkdfKeyDerivation.deriveSessionKey(ssEcdhe, ssKyber, sessionNonce);

            // 5. Gửi CHAT_HANDSHAKE đến Chat Server
            ChatHandshakePayload payload = new ChatHandshakePayload(
                    stBase64,
                    authenticatorB64,
                    Base64.getEncoder().encodeToString(ssEcdhe),
                    Base64.getEncoder().encodeToString(ssKyber),
                    Base64.getEncoder().encodeToString(sessionNonce));

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
        }
    }

    /** @deprecated Dùng performHandshake(username, password) */
    public boolean performHandshake() {
        log.warn("performHandshake() không tham số được gọi — dùng Mock key");
        try {
            byte[] ss1 = new byte[32]; byte[] ss2 = new byte[32]; byte[] nonce = new byte[32];
            new SecureRandom().nextBytes(ss1);
            new SecureRandom().nextBytes(ss2);
            new SecureRandom().nextBytes(nonce);
            this.masterSessionKey = HkdfKeyDerivation.deriveSessionKey(ss1, ss2, nonce);
            return true;
        } catch (Exception e) { return false; }
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

    // ─── Inner DTOs ──────────────────────────────────────────────────────────

    private static final class ChatHandshakePayload {
        @JsonProperty("st")        private final String st;
        @JsonProperty("authenticator") private final String authenticator;
        @JsonProperty("ssEcdhe")   private final String ssEcdhe;
        @JsonProperty("ssKyber")   private final String ssKyber;
        @JsonProperty("sessionNonce") private final String sessionNonce;

        ChatHandshakePayload(String st, String authenticator,
                             String ssEcdhe, String ssKyber, String sessionNonce) {
            this.st = st;
            this.authenticator = authenticator;
            this.ssEcdhe = ssEcdhe;
            this.ssKyber = ssKyber;
            this.sessionNonce = sessionNonce;
        }
    }
}
