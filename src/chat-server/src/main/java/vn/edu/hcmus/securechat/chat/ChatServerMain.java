package vn.edu.hcmus.securechat.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import vn.edu.hcmus.securechat.chat.crypto.ChatKeyManager;
import vn.edu.hcmus.securechat.chat.service.OcspStaplingManager;
import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.crypto.EcdheService;
import vn.edu.hcmus.securechat.common.crypto.HkdfKeyDerivation;
import vn.edu.hcmus.securechat.common.crypto.HybridEncryption;
import vn.edu.hcmus.securechat.common.crypto.KyberKemService;
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
import vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
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

    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<Socket, String> clientIdsBySocket = new ConcurrentHashMap<>();
    private final Map<Socket, Object> socketWriteLocks = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    
    private ChatKeyManager keyManager;
    private OcspStaplingManager ocspManager;
    private KeyPair serverKyberPair;

    public ChatServerMain() {
        this.port = ServerConfig.CHAT_PORT;
        this.replayDefense = new ReplayDefenseService();
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "chat-handler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        log.info("========================================");
        log.info("  Chat Server starting on port {}...", port);
        log.info("========================================");

        try {
            // 1. Initialize Key Manager
            keyManager = new ChatKeyManager();
            
            // 2. Initialize OCSP Stapling
            String serialHex = keyManager.getChatCertificate().getSerialNumber().toString(16);
            String issuerDn = keyManager.getChatCertificate().getIssuerX500Principal().getName();
            ocspManager = new OcspStaplingManager(serialHex, issuerDn);
            ocspManager.start();

            // 3. Initialize ML-KEM-768 KeyPair
            serverKyberPair = KyberKemService.generateKeyPair();
            log.info("ML-KEM-768 key pair generated for Chat Server");
            
        } catch (Exception e) {
            log.error("Failed to initialize Chat Server dependencies", e);
            return;
        }

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
            if (ocspManager != null) ocspManager.shutdown();
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

                if (ocspManager != null && !ocspManager.isCertValid()) {
                    log.warn("Chat Server certificate is not valid according to OCSP. Rejecting request.");
                    sendError(socket, ErrorResponse.ERR_CERT_INVALID, "Server certificate is invalid");
                    break;
                }

                switch (type) {
                    case CHAT_HANDSHAKE -> handleHandshake(frame, socket, clientAddr);
                    case CHAT_MESSAGE -> handleChatMessage(frame, socket, clientAddr);
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
        }
    }

    private void handleHandshake(PacketFrame frame, Socket socket, String clientAddr) {
        String clientId = null;
        byte[] ticketSessionKey = null;
        byte[] masterSessionKey = null;
        KeyPair ecdhePair = null;

        try {
            ChatHandshakeRequest request = JsonSerializer.fromBytes(frame.getPayload(), ChatHandshakeRequest.class);
            StInner serviceTicket = parseServiceTicket(request);
            validateServiceTicket(serviceTicket);

            clientId = serviceTicket.getClientId();
            ticketSessionKey = decodeFixedKey(serviceTicket.getSessionKey(), "ST sessionKey");

            AuthenticatorJson auth = parseAuthenticator(request, ticketSessionKey);
            validateAuthenticator(serviceTicket, auth);

            // ECDHE + Kyber Handshake
            ecdhePair = EcdheService.generateKeyPair();
            masterSessionKey = deriveMasterKeyIfPresent(request, ticketSessionKey, ecdhePair);
            
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

            String ecdhePubKeyBase64 = EcdheService.encodePublicKey(ecdhePair.getPublic());
            
            ChatHandshakeResponse response = new ChatHandshakeResponse(
                    "OK",
                    clientId,
                    ecdhePubKeyBase64,
                    serviceTicket.getExpiresAt(),
                    masterSessionKey != ticketSessionKey);
                    
            writeFrame(socket, PacketFrame.TYPE_CHAT_HANDSHAKE, JsonSerializer.toBytes(response));

            auditLog.info("SESSION_ESTABLISHED clientId={} ip={} expiresAt={}",
                    clientId, clientAddr, serviceTicket.getExpiresAt());
            log.info("Client {} authenticated from {}", clientId, clientAddr);
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
            throws ProtocolException, InvalidTicketException, CryptoException {
        if (request == null || isBlank(request.getSt())) {
            throw new InvalidTicketException("Missing service ticket");
        }

        byte[] ticketBytes = decodeRequired(request.getSt(), "st");

        try {
            byte[] decryptedStBytes = HybridEncryption.decrypt(keyManager.getChatPrivateKey(), ticketBytes);
            return JsonSerializer.fromBytes(decryptedStBytes, StInner.class);
        } catch (CryptoException e) {
            throw new InvalidTicketException("Failed to decrypt ST", e);
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

    private byte[] deriveMasterKeyIfPresent(ChatHandshakeRequest request, byte[] ticketSessionKey, KeyPair ecdhePair)
            throws CryptoException, ProtocolException {
        if (isBlank(request.getEcdhePubKey()) || isBlank(request.getKyberCiphertext()) || isBlank(request.getSessionNonce())) {
            return ticketSessionKey;
        }

        byte[] ssEcdhe = null;
        byte[] ssKyber = null;
        byte[] sessionNonce = null;
        try {
            // 1. Calculate ECDHE shared secret
            PublicKey clientEcdhePubKey = EcdheService.decodePublicKey(request.getEcdhePubKey());
            ssEcdhe = EcdheService.computeSharedSecret(ecdhePair.getPrivate(), clientEcdhePubKey);
            
            // 2. Decapsulate Kyber shared secret
            byte[] kyberCiphertext = decodeRequired(request.getKyberCiphertext(), "kyberCiphertext");
            ssKyber = KyberKemService.decapsulate(serverKyberPair.getPrivate(), kyberCiphertext);
            
            // 3. Derive master key
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
        if (ocspManager != null) {
            ocspManager.shutdown();
        }
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
}
