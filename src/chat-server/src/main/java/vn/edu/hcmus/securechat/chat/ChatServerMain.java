package vn.edu.hcmus.securechat.chat;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.crypto.HkdfKeyDerivation;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.exception.ControlVectorException;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.exception.InvalidTicketException;
import vn.edu.hcmus.securechat.common.exception.MacVerificationException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.protocol.ControlVector;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StInner;

/**
 * Chat Server - routes encrypted E2EE messages between authenticated clients.
 * Owner: Phu Tho | Reviewer: Gia Hien
 */
public class ChatServerMain {

    private static final Logger log = LoggerFactory.getLogger(ChatServerMain.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final int port;
    private final ExecutorService threadPool;
    private final ReplayDefenseService replayDefense;
    private PrivateKey chatPrivateKey;
    private PublicKey chatPublicKey;

    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<Socket, String> clientIdsBySocket = new ConcurrentHashMap<>();
    private final Map<Socket, Object> socketWriteLocks = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    public ChatServerMain() {
        this.port = ServerConfig.CHAT_PORT;
        this.replayDefense = new ReplayDefenseService();
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "chat-handler");
            t.setDaemon(true);
            return t;
        });
        loadChatKeyPair();
    }

    /**
     * Load chatKeyPair từ file data/chat_server_key.bin do KDC ghi ra.
     * Format: [4 bytes privLen][privDer][4 bytes pubLen][pubDer]
     */
    private void loadChatKeyPair() {
        File keyFile = new File("data", "chat_server_key.bin");
        if (!keyFile.exists()) {
            log.warn("chat_server_key.bin not found — ST decryption will be unavailable. Start KDC first!");
            return;
        }
        try {
            byte[] data = Files.readAllBytes(keyFile.toPath());
            int pos = 0;
            int privLen = ((data[pos]&0xFF)<<24)|((data[pos+1]&0xFF)<<16)|((data[pos+2]&0xFF)<<8)|(data[pos+3]&0xFF);
            pos += 4;
            byte[] privDer = Arrays.copyOfRange(data, pos, pos + privLen);
            pos += privLen;
            int pubLen = ((data[pos]&0xFF)<<24)|((data[pos+1]&0xFF)<<16)|((data[pos+2]&0xFF)<<8)|(data[pos+3]&0xFF);
            pos += 4;
            byte[] pubDer = Arrays.copyOfRange(data, pos, pos + pubLen);

            KeyFactory kf = KeyFactory.getInstance("RSA");
            chatPrivateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privDer));
            chatPublicKey  = kf.generatePublic(new X509EncodedKeySpec(pubDer));
            log.info("Chat Server KeyPair loaded from {}", keyFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to load chat server key pair", e);
        }
    }

    public void start() {
        running = true;
        log.info("========================================");
        log.info("  Chat Server starting on port {}...", port);
        log.info("========================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Chat Server is READY - listening on port {}", port);
            auditLog.info("CHAT_SERVER_STARTED port={}", port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                log.info("New connection from {}", clientSocket.getRemoteSocketAddress());
                threadPool.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            log.error("Chat Server error", e);
        } finally {
            threadPool.shutdown();
            replayDefense.shutdown();
            log.info("Chat Server stopped.");
        }
    }

    private void handleClient(Socket socket) {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        socketWriteLocks.put(socket, new Object());
        try {
            while (running && !socket.isClosed()) {
                PacketFrame frame = PacketFrame.read(socket.getInputStream());
                MessageType type = MessageType.fromByte(frame.getType());

                log.info("Received {} from {}", type, clientAddr);

                switch (type) {
                    case CHAT_HANDSHAKE -> handleHandshake(frame, socket, clientAddr);
                    case CHAT_MESSAGE -> handleChatMessage(frame, socket, clientAddr);
                    case USER_LIST -> sendUserList(socket);
                    default -> {
                        log.warn("Unexpected message type {} from {}", type, clientAddr);
                        sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Unexpected message type");
                    }
                }
            }
        } catch (FramingException e) {
            log.error("Framing error from {}", clientAddr, e);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown message type from {}", clientAddr);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Unknown message type");
        } catch (IOException e) {
            log.debug("Client disconnected: {}", clientAddr);
        } finally {
            removeSession(socket);
            closeQuietly(socket);
            socketWriteLocks.remove(socket);
            broadcastUserList(); // Cập nhật danh sách khi ai đó ngắt kết nối
        }
    }

    private void handleHandshake(PacketFrame frame, Socket socket, String clientAddr) {
        String clientId = null;
        byte[] ticketSessionKey = null;
        byte[] masterSessionKey = null;

        try {
            ChatHandshakeRequest request = JsonSerializer.fromBytes(frame.getPayload(), ChatHandshakeRequest.class);
            StInner serviceTicket = parseServiceTicket(request);
            validateServiceTicket(serviceTicket);

            clientId = serviceTicket.getClientId();
            ticketSessionKey = decodeFixedKey(serviceTicket.getSessionKey(), "ST sessionKey");

            AuthenticatorJson auth = parseAuthenticator(request, ticketSessionKey);
            validateAuthenticator(serviceTicket, auth);

            masterSessionKey = deriveMasterKeyIfPresent(request, ticketSessionKey);
            ClientSession session = new ClientSession(
                    clientId,
                    socket,
                    Arrays.copyOf(masterSessionKey, masterSessionKey.length),
                    serviceTicket.getExpiresAt(),
                    Instant.now().getEpochSecond());

            ClientSession previous = activeSessions.put(clientId, session);
            if (previous != null && previous.socket() != socket) {
                closeQuietly(previous.socket());
            }
            clientIdsBySocket.put(socket, clientId);

            ChatHandshakeResponse response = new ChatHandshakeResponse(
                    "OK",
                    clientId,
                    serviceTicket.getExpiresAt(),
                    masterSessionKey != ticketSessionKey);
            writeFrame(socket, PacketFrame.TYPE_CHAT_HANDSHAKE, JsonSerializer.toBytes(response));

            auditLog.info("SESSION_ESTABLISHED clientId={} ip={} expiresAt={}",
                    clientId, clientAddr, serviceTicket.getExpiresAt());
            log.info("Client {} authenticated from {}", clientId, clientAddr);

            // Push danh sách user online cho tất cả client
            broadcastUserList();
        } catch (ReplayAttackException e) {
            auditLog.warn("SESSION_REJECTED clientId={} ip={} reason=REPLAY", clientId, clientAddr);
            sendError(socket, ErrorResponse.ERR_REPLAY_DETECTED, "Authentication rejected");
        } catch (ControlVectorException | InvalidTicketException e) {
            auditLog.warn("SESSION_REJECTED clientId={} ip={} reason=INVALID_TICKET", clientId, clientAddr);
            sendError(socket, ErrorResponse.ERR_AUTH_FAILED, "Authentication rejected");
        } catch (MacVerificationException e) {
            auditLog.error("MAC_FAIL phase=HANDSHAKE clientId={} ip={}", clientId, clientAddr);
            closeQuietly(socket);
        } catch (CryptoException | ProtocolException | IOException | IllegalArgumentException e) {
            log.warn("Handshake failed from {}", clientAddr, e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid handshake request");
        } finally {
            zeroAll(ticketSessionKey, masterSessionKey);
        }
    }

    private void handleChatMessage(PacketFrame frame, Socket socket, String clientAddr) {
        ClientSession sender = sessionFor(socket);
        if (sender == null) {
            sendError(socket, ErrorResponse.ERR_AUTH_FAILED, "Handshake required");
            return;
        }

        try {
            EncryptedChatEnvelope envelope = JsonSerializer.fromBytes(frame.getPayload(), EncryptedChatEnvelope.class);
            validateMessageEnvelope(envelope);

            byte[] plaintext = null;
            try {
                byte[] cipherPayload = decodeRequired(envelope.getPayload(), "payload");
                plaintext = AesGcmCipher.decrypt(sender.sessionKey(), cipherPayload);
                JsonNode message = JsonSerializer.getMapper().readTree(plaintext);
                validateSender(message, sender.clientId());
            } finally {
                zero(plaintext);
            }

            ClientSession recipient = activeSessions.get(envelope.getRecipientId());
            if (recipient == null || recipient.socket().isClosed()) {
                sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Recipient is not connected");
                return;
            }

            writeFrame(recipient.socket(), PacketFrame.TYPE_CHAT_MESSAGE, JsonSerializer.toBytes(envelope));

            auditLog.info("MESSAGE_ROUTED from={} to={} ip={}",
                    sender.clientId(), envelope.getRecipientId(), clientAddr);
            log.info("Routed encrypted message from {} to {}", sender.clientId(), envelope.getRecipientId());
        } catch (MacVerificationException e) {
            auditLog.error("MAC_FAIL phase=CHAT_MESSAGE clientId={} ip={}", sender.clientId(), clientAddr);
            closeQuietly(socket);
        } catch (CryptoException | ProtocolException | IOException | IllegalArgumentException e) {
            log.warn("Could not route message from {}", clientAddr, e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid chat message");
        }
    }

    private StInner parseServiceTicket(ChatHandshakeRequest request)
            throws ProtocolException, InvalidTicketException {
        if (request == null || isBlank(request.getSt())) {
            throw new InvalidTicketException("Missing service ticket");
        }

        String st = request.getSt().trim();
        byte[] ticketBytes;
        if (st.startsWith("{")) {
            // Plain JSON (fallback khi không có mã hóa)
            ticketBytes = st.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            // Base64 → decrypt bằng chatPrivateKey
            byte[] encryptedSt = decodeRequired(st, "st");
            if (chatPrivateKey == null) {
                throw new InvalidTicketException("Chat Server private key not loaded. Start KDC first!");
            }
            try {
                ticketBytes = vn.edu.hcmus.securechat.common.crypto.HybridCrypto
                        .decrypt(chatPrivateKey, encryptedSt);
            } catch (Exception e) {
                throw new InvalidTicketException("Failed to decrypt service ticket: " + e.getMessage(), e);
            }
        }

        try {
            return JsonSerializer.fromBytes(ticketBytes, StInner.class);
        } catch (ProtocolException e) {
            throw new InvalidTicketException("Failed to parse service ticket", e);
        }
    }

    private void validateServiceTicket(StInner st)
            throws ControlVectorException, InvalidTicketException {
        if (isBlank(st.getClientId())) {
            throw new InvalidTicketException("ST missing clientId");
        }
        ControlVector.validateForChatService(st.getCv());

        long now = Instant.now().getEpochSecond();
        if (st.getExpiresAt() <= now) {
            throw new InvalidTicketException("Service ticket expired");
        }
        if (!isBlank(st.getTargetServer())
                && !ServerConfig.CHAT_HOST.equalsIgnoreCase(st.getTargetServer())
                && !"localhost".equalsIgnoreCase(st.getTargetServer())) {
            throw new InvalidTicketException("ST target server mismatch");
        }
    }

    private AuthenticatorJson parseAuthenticator(ChatHandshakeRequest request, byte[] ticketSessionKey)
            throws CryptoException, ProtocolException {
        if (isBlank(request.getAuthenticator())) {
            throw new ProtocolException("Missing authenticator");
        }

        String authenticator = request.getAuthenticator().trim();
        byte[] plaintext;
        if (authenticator.startsWith("{")) {
            plaintext = authenticator.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            plaintext = AesGcmCipher.decrypt(ticketSessionKey, decodeRequired(authenticator, "authenticator"));
        }
        return JsonSerializer.fromBytes(plaintext, AuthenticatorJson.class);
    }

    private void validateAuthenticator(StInner st, AuthenticatorJson auth)
            throws ReplayAttackException, InvalidTicketException {
        if (auth == null || isBlank(auth.getClientId()) || isBlank(auth.getNonce())) {
            throw new InvalidTicketException("Invalid authenticator");
        }
        if (!Objects.equals(st.getClientId(), auth.getClientId())) {
            throw new InvalidTicketException("Authenticator client mismatch");
        }
        replayDefense.validateAuthenticator(auth);
    }

    private byte[] deriveMasterKeyIfPresent(ChatHandshakeRequest request, byte[] ticketSessionKey)
            throws CryptoException, ProtocolException {
        if (isBlank(request.getSsEcdhe()) || isBlank(request.getSsKyber()) || isBlank(request.getSessionNonce())) {
            return ticketSessionKey;
        }

        byte[] ssEcdhe = null;
        byte[] ssKyber = null;
        byte[] sessionNonce = null;
        try {
            ssEcdhe = decodeRequired(request.getSsEcdhe(), "ssEcdhe");
            ssKyber = decodeRequired(request.getSsKyber(), "ssKyber");
            sessionNonce = decodeRequired(request.getSessionNonce(), "sessionNonce");
            return HkdfKeyDerivation.deriveSessionKey(ssEcdhe, ssKyber, sessionNonce);
        } finally {
            zero(ssEcdhe);
            zero(ssKyber);
            zero(sessionNonce);
        }
    }

    private ClientSession sessionFor(Socket socket) {
        String clientId = clientIdsBySocket.get(socket);
        if (clientId == null) {
            return null;
        }
        ClientSession session = activeSessions.get(clientId);
        long now = Instant.now().getEpochSecond();
        if (session != null && session.expiresAt() <= now) {
            removeSession(socket);
            auditLog.info("SESSION_EXPIRED clientId={} sessionId={}", session.clientId(), session.sessionId());
            return null;
        }
        return session;
    }

    private void validateMessageEnvelope(EncryptedChatEnvelope envelope) {
        if (envelope == null || isBlank(envelope.getRecipientId()) || isBlank(envelope.getPayload())) {
            throw new IllegalArgumentException("Message envelope must include recipientId and payload");
        }
    }

    private void validateSender(JsonNode message, String expectedSender) {
        JsonNode senderNode = message.get("senderId");
        if (senderNode == null || !expectedSender.equals(senderNode.asText())) {
            throw new IllegalArgumentException("senderId does not match authenticated session");
        }
    }

    private byte[] decodeFixedKey(String base64, String fieldName) throws ProtocolException {
        byte[] key = decodeRequired(base64, fieldName);
        if (key.length != CryptoConstants.AES_KEY_SIZE_BYTES) {
            zero(key);
            throw new ProtocolException(fieldName + " must be 32 bytes");
        }
        return key;
    }

    private byte[] decodeRequired(String base64, String fieldName) throws ProtocolException {
        if (isBlank(base64)) {
            throw new ProtocolException("Missing field: " + fieldName);
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Invalid base64 field: " + fieldName, e);
        }
    }

    /** Gửi danh sách user online cho 1 client cụ thể. */
    private void sendUserList(Socket socket) {
        try {
            List<String> users = new ArrayList<>(activeSessions.keySet());
            String json = JsonSerializer.getMapper().writeValueAsString(users);
            writeFrame(socket, PacketFrame.TYPE_USER_LIST, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("Failed to send user list", e);
        }
    }

    /** Broadcast danh sách user online cho tất cả client đang kết nối. */
    private void broadcastUserList() {
        for (Map.Entry<Socket, String> entry : clientIdsBySocket.entrySet()) {
            sendUserList(entry.getKey());
        }
    }

    private void sendError(Socket socket, String code, String message) {
        if (socket == null || socket.isClosed()) {
            return;
        }

        try {
            writeFrame(socket, PacketFrame.TYPE_ERROR, JsonSerializer.toBytes(ErrorResponse.of(code, message)));
        } catch (IOException | ProtocolException e) {
            log.debug("Failed to send error response", e);
        }
    }

    private void writeFrame(Socket socket, byte type, byte[] payload) throws IOException {
        Object lock = socketWriteLocks.computeIfAbsent(socket, ignored -> new Object());
        synchronized (lock) {
            PacketFrame.write(socket.getOutputStream(), type, payload);
        }
    }

    private void removeSession(Socket socket) {
        String clientId = clientIdsBySocket.remove(socket);
        if (clientId == null) {
            return;
        }

        ClientSession removed = activeSessions.remove(clientId);
        if (removed != null) {
            zero(removed.sessionKey());
            long duration = Instant.now().getEpochSecond() - removed.establishedAt();
            auditLog.info("SESSION_ENDED sessionId={} clientId={} duration_seconds={}",
                    removed.sessionId(), clientId, duration);
        }
    }

    public void stop() {
        running = false;
        replayDefense.shutdown();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing to do during cleanup.
        }
    }

    private static void zeroAll(byte[] first, byte[] second) {
        zero(first);
        if (first != second) {
            zero(second);
        }
    }

    private static void zero(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    public static void main(String[] args) {
        new ChatServerMain().start();
    }

    private record ClientSession(
            String clientId,
            Socket socket,
            byte[] sessionKey,
            long expiresAt,
            long establishedAt) {

        String sessionId() {
            return Integer.toHexString(System.identityHashCode(socket));
        }
    }

    private static final class ChatHandshakeRequest {
        @JsonProperty("st")
        private String st;

        @JsonProperty("authenticator")
        private String authenticator;

        @JsonProperty("ssEcdhe")
        private String ssEcdhe;

        @JsonProperty("ssKyber")
        private String ssKyber;

        @JsonProperty("sessionNonce")
        private String sessionNonce;

        String getSt() {
            return st;
        }

        String getAuthenticator() {
            return authenticator;
        }

        String getSsEcdhe() {
            return ssEcdhe;
        }

        String getSsKyber() {
            return ssKyber;
        }

        String getSessionNonce() {
            return sessionNonce;
        }
    }

    private static final class ChatHandshakeResponse {
        @JsonProperty("status")
        private final String status;

        @JsonProperty("clientId")
        private final String clientId;

        @JsonProperty("expiresAt")
        private final long expiresAt;

        @JsonProperty("masterKeyDerived")
        private final boolean masterKeyDerived;

        ChatHandshakeResponse(String status, String clientId, long expiresAt, boolean masterKeyDerived) {
            this.status = status;
            this.clientId = clientId;
            this.expiresAt = expiresAt;
            this.masterKeyDerived = masterKeyDerived;
        }
    }

    private static final class EncryptedChatEnvelope {
        @JsonProperty("recipientId")
        private String recipientId;

        @JsonProperty("payload")
        private String payload;

        String getRecipientId() {
            return recipientId;
        }

        String getPayload() {
            return payload;
        }
    }
}
