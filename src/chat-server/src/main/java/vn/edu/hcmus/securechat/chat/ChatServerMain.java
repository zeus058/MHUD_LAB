package vn.edu.hcmus.securechat.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
import vn.edu.hcmus.securechat.common.protocol.dto.E2eeInitMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.OneTimePreKey;
import vn.edu.hcmus.securechat.common.protocol.dto.PreKeyBundle;
import vn.edu.hcmus.securechat.common.protocol.dto.PreKeyRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.SessionTicket;
import vn.edu.hcmus.securechat.common.protocol.dto.StInner;
import vn.edu.hcmus.securechat.common.protocol.dto.UserListEntry;

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
    private final Map<String, PreKeyBundle> preKeyBundles = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenAtByClient = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    
    // Offline message queue
    private record OfflineMessage(String senderId, String recipientId, byte type, byte[] payload, boolean e2eeEnvelope) {}
    private final Map<String, java.util.List<OfflineMessage>> offlineMessages = new ConcurrentHashMap<>();

    private ChatKeyManager keyManager;
    private OcspStaplingManager ocspManager;
    private KeyPair serverKyberPair;
    private byte[] ticketServerKey;

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
            // 0. Register BouncyCastle
            java.security.Security.addProvider(new BouncyCastleProvider());

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

            ticketServerKey = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(ticketServerKey);
            log.info("Chat access-ticket sealing key initialized");
            
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
            zero(ticketServerKey);
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
                    case PREKEY_UPLOAD -> handlePreKeyUpload(frame, socket);
                    case PREKEY_REQUEST -> handlePreKeyRequest(frame, socket);
                    case E2EE_INIT -> handleE2eeInit(frame, socket, clientAddr);
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
            lastSeenAtByClient.put(clientId, Instant.now().getEpochSecond());

            String ecdhePubKeyBase64 = EcdheService.encodePublicKey(ecdhePair.getPublic());
            String accessTicket = createAccessTicket(serviceTicket, auth);
            
            ChatHandshakeResponse response = new ChatHandshakeResponse(
                    "OK",
                    clientId,
                    ecdhePubKeyBase64,
                    serviceTicket.getExpiresAt(),
                    masterSessionKey != ticketSessionKey,
                    accessTicket);
                    
            writeFrame(socket, PacketFrame.TYPE_CHAT_HANDSHAKE, JsonSerializer.toBytes(response));

            auditLog.info("SESSION_ESTABLISHED clientId={} ip={} expiresAt={}",
                    clientId, clientAddr, serviceTicket.getExpiresAt());
            log.info("Client {} authenticated from {}", clientId, clientAddr);

            try {
                byte[] stHash = java.security.MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(request.getSt()));
                String ticketId = Base64.getEncoder().encodeToString(stHash);
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        clientId,
                        "N/A",
                        ticketId,
                        ServerConfig.CHAT_HOST,
                        "CHAT_HANDSHAKE",
                        "SUCCESS",
                        "Chat session established"
                );
            } catch (Exception ex) {
                log.warn("Failed to write to secure log chain", ex);
            }

            broadcastUserList();

            // Gửi tin nhắn offline nếu có
            java.util.List<OfflineMessage> pending = offlineMessages.remove(clientId);
            if (pending != null) {
                for (OfflineMessage offMsg : pending) {
                    try {
                        if (offMsg.e2eeEnvelope) {
                            writeFrame(socket, offMsg.type, offMsg.payload);
                        } else {
                            byte[] reEncrypted = AesGcmCipher.encrypt(masterSessionKey, offMsg.payload);
                            EncryptedChatEnvelope env = new EncryptedChatEnvelope(clientId, Base64.getEncoder().encodeToString(reEncrypted));
                            writeFrame(socket, offMsg.type, JsonSerializer.toBytes(env));
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to send offline message", ex);
                    }
                }
            }
        } catch (ReplayAttackException e) {
            auditLog.warn("SESSION_REJECTED clientId={} ip={} reason=REPLAY", clientId, clientAddr);
            sendError(socket, ErrorResponse.ERR_REPLAY_DETECTED, "Authentication rejected");
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        clientId != null ? clientId : "UNKNOWN",
                        "N/A",
                        "N/A",
                        ServerConfig.CHAT_HOST,
                        "CHAT_HANDSHAKE",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
        } catch (ControlVectorException | InvalidTicketException e) {
            auditLog.warn("SESSION_REJECTED clientId={} ip={} reason=INVALID_TICKET", clientId, clientAddr);
            sendError(socket, ErrorResponse.ERR_AUTH_FAILED, "Authentication rejected");
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        clientId != null ? clientId : "UNKNOWN",
                        "N/A",
                        "N/A",
                        ServerConfig.CHAT_HOST,
                        "CHAT_HANDSHAKE",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
        } catch (MacVerificationException e) {
            auditLog.error("MAC_FAIL phase=HANDSHAKE clientId={} ip={}", clientId, clientAddr);
            closeQuietly(socket);
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        clientId != null ? clientId : "UNKNOWN",
                        "N/A",
                        "N/A",
                        ServerConfig.CHAT_HOST,
                        "CHAT_HANDSHAKE",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
        } catch (CryptoException | ProtocolException | IOException | IllegalArgumentException e) {
            log.warn("Handshake failed from {}", clientAddr, e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid handshake request");
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        clientId != null ? clientId : "UNKNOWN",
                        "N/A",
                        "N/A",
                        ServerConfig.CHAT_HOST,
                        "CHAT_HANDSHAKE",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
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

            if (isE2eeV2Envelope(envelope)) {
                validateEnvelopeSender(envelope, sender.clientId());
                ClientSession recipient = activeSessions.get(envelope.getRecipientId());
                if (recipient == null || recipient.socket().isClosed()) {
                    offlineMessages.computeIfAbsent(envelope.getRecipientId(),
                            k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                            .add(new OfflineMessage(sender.clientId(), envelope.getRecipientId(),
                                    PacketFrame.TYPE_CHAT_MESSAGE,
                                    Arrays.copyOf(frame.getPayload(), frame.getPayload().length), true));
                    log.info("Stored offline E2EE envelope from {} to {}",
                            sender.clientId(), envelope.getRecipientId());
                    broadcastUserList();
                } else {
                    writeFrame(recipient.socket(), PacketFrame.TYPE_CHAT_MESSAGE, frame.getPayload());
                    log.info("Routed E2EE envelope from {} to {} without decryption",
                            sender.clientId(), envelope.getRecipientId());
                }
                auditLog.info("MESSAGE_ROUTED_E2EE from={} to={} ip={}",
                        sender.clientId(), envelope.getRecipientId(), clientAddr);
                return;
            }

            byte[] plaintext = null;
            try {
                byte[] cipherPayload = decodeRequired(envelope.getPayload(), "payload");
                plaintext = AesGcmCipher.decrypt(sender.sessionKey(), cipherPayload);
                JsonNode message = JsonSerializer.getMapper().readTree(plaintext);
                validateSender(message, sender.clientId());

                ClientSession recipient = activeSessions.get(envelope.getRecipientId());
                if (recipient == null || recipient.socket().isClosed()) {
                    OfflineMessage offMsg = new OfflineMessage(sender.clientId(), envelope.getRecipientId(),
                            PacketFrame.TYPE_CHAT_MESSAGE, Arrays.copyOf(plaintext, plaintext.length), false);
                    offlineMessages.computeIfAbsent(envelope.getRecipientId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(offMsg);
                    log.info("Stored offline message from {} to {}", sender.clientId(), envelope.getRecipientId());
                    broadcastUserList();
                } else {
                    byte[] reEncrypted = AesGcmCipher.encrypt(recipient.sessionKey(), plaintext);
                    envelope.setPayload(Base64.getEncoder().encodeToString(reEncrypted));
                    writeFrame(recipient.socket(), PacketFrame.TYPE_CHAT_MESSAGE, JsonSerializer.toBytes(envelope));
                    log.info("Routed encrypted message from {} to {}", sender.clientId(), envelope.getRecipientId());
                }

                auditLog.info("MESSAGE_ROUTED from={} to={} ip={}",
                        sender.clientId(), envelope.getRecipientId(), clientAddr);
            } finally {
                zero(plaintext);
            }
        } catch (MacVerificationException e) {
            auditLog.error("MAC_FAIL phase=CHAT_MESSAGE clientId={} ip={}", sender.clientId(), clientAddr);
            closeQuietly(socket);
        } catch (CryptoException | ProtocolException | IOException | IllegalArgumentException e) {
            log.warn("Could not route message from {}", clientAddr, e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid chat message");
        }
    }

    private void handlePreKeyUpload(PacketFrame frame, Socket socket) {
        ClientSession session = sessionFor(socket);
        if (session == null) {
            sendError(socket, ErrorResponse.ERR_AUTH_FAILED, "Handshake required");
            return;
        }
        try {
            PreKeyBundle bundle = JsonSerializer.fromBytes(frame.getPayload(), PreKeyBundle.class);
            if (bundle == null || isBlank(bundle.getOwnerId())) {
                throw new ProtocolException("Pre-Key bundle missing ownerId");
            }
            if (!Objects.equals(session.clientId(), bundle.getOwnerId())) {
                throw new ProtocolException("Pre-Key bundle owner mismatch");
            }
            preKeyBundles.put(session.clientId(), bundle);
            lastSeenAtByClient.put(session.clientId(), Instant.now().getEpochSecond());
            int opkCount = bundle.getOneTimePreKeys() == null ? 0 : bundle.getOneTimePreKeys().size();
            if (opkCount < 10) {
                auditLog.warn("PREKEY_POOL_LOW clientId={} opkCount={}", session.clientId(), opkCount);
            }
            auditLog.info("PREKEY_UPLOADED clientId={} opkCount={}", session.clientId(), opkCount);
            broadcastUserList();
        } catch (Exception e) {
            log.warn("Pre-Key upload failed", e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid Pre-Key bundle");
        }
    }

    private void handlePreKeyRequest(PacketFrame frame, Socket socket) {
        ClientSession requester = sessionFor(socket);
        if (requester == null) {
            sendError(socket, ErrorResponse.ERR_AUTH_FAILED, "Handshake required");
            return;
        }
        try {
            PreKeyRequest request = JsonSerializer.fromBytes(frame.getPayload(), PreKeyRequest.class);
            if (request == null || isBlank(request.getRecipientId())) {
                throw new ProtocolException("Pre-Key request missing recipientId");
            }
            PreKeyBundle stored = preKeyBundles.get(request.getRecipientId());
            if (stored == null) {
                sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Pre-Key bundle unavailable");
                return;
            }

            PreKeyBundle response = JsonSerializer.fromBytes(JsonSerializer.toBytes(stored), PreKeyBundle.class);
            if (stored.getOneTimePreKeys() != null && !stored.getOneTimePreKeys().isEmpty()) {
                OneTimePreKey consumed = stored.getOneTimePreKeys().remove(0);
                response.setOneTimePreKeys(java.util.List.of(consumed));
                response.setLastResort(false);
            } else {
                response.setOneTimePreKeys(java.util.List.of());
                response.setLastResort(true);
                auditLog.warn("PREKEY_LAST_RESORT requester={} recipient={}",
                        requester.clientId(), request.getRecipientId());
            }
            writeFrame(socket, PacketFrame.TYPE_PREKEY_RESPONSE, JsonSerializer.toBytes(response));
            auditLog.info("PREKEY_ISSUED requester={} recipient={}",
                    requester.clientId(), request.getRecipientId());
        } catch (Exception e) {
            log.warn("Pre-Key request failed", e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid Pre-Key request");
        }
    }

    private void handleE2eeInit(PacketFrame frame, Socket socket, String clientAddr) {
        ClientSession sender = sessionFor(socket);
        if (sender == null) {
            sendError(socket, ErrorResponse.ERR_AUTH_FAILED, "Handshake required");
            return;
        }
        try {
            E2eeInitMessage init = JsonSerializer.fromBytes(frame.getPayload(), E2eeInitMessage.class);
            if (init == null || isBlank(init.getConversationId())
                    || isBlank(init.getSenderId()) || isBlank(init.getRecipientId())) {
                throw new ProtocolException("E2EE init missing required fields");
            }
            if (!Objects.equals(sender.clientId(), init.getSenderId())) {
                throw new ProtocolException("E2EE init sender mismatch");
            }
            ClientSession recipient = activeSessions.get(init.getRecipientId());
            if (recipient == null || recipient.socket().isClosed()) {
                offlineMessages.computeIfAbsent(init.getRecipientId(),
                        k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                        .add(new OfflineMessage(sender.clientId(), init.getRecipientId(),
                                PacketFrame.TYPE_E2EE_INIT,
                                Arrays.copyOf(frame.getPayload(), frame.getPayload().length), true));
                auditLog.info("E2EE_INIT_STORED from={} to={} conversationId={} ip={}",
                        sender.clientId(), init.getRecipientId(), init.getConversationId(), clientAddr);
                broadcastUserList();
            } else {
                writeFrame(recipient.socket(), PacketFrame.TYPE_E2EE_INIT, frame.getPayload());
                auditLog.info("E2EE_INIT_FORWARDED from={} to={} conversationId={} ip={}",
                        sender.clientId(), init.getRecipientId(), init.getConversationId(), clientAddr);
            }
        } catch (Exception e) {
            log.warn("E2EE init failed", e);
            sendError(socket, ErrorResponse.ERR_BAD_REQUEST, "Invalid E2EE init");
        }
    }

    private String createAccessTicket(StInner st, AuthenticatorJson auth)
            throws CryptoException, ProtocolException {
        if (ticketServerKey == null) {
            throw new ProtocolException("Access ticket key is not initialized");
        }
        long now = Instant.now().getEpochSecond();
        long expiresAt = Math.min(st.getExpiresAt(), now + CryptoConstants.ST_LIFETIME_SECONDS);
        String stId = isBlank(st.getStId()) ? "legacy-st" : st.getStId();
        String deviceBinding = isBlank(auth.getChannelBinding())
                ? "unbound:" + st.getClientId()
                : auth.getChannelBinding();
        SessionTicket ticket = new SessionTicket(
                st.getClientId(),
                deviceBinding,
                stId,
                now,
                expiresAt,
                java.util.List.of("chat.send", "chat.recv", "prekey.upload"));
        byte[] plaintext = null;
        byte[] encrypted = null;
        try {
            plaintext = JsonSerializer.toBytes(ticket);
            encrypted = AesGcmCipher.encrypt(ticketServerKey, plaintext,
                    "SecureChat-access-ticket-v2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } finally {
            zero(plaintext);
            zero(encrypted);
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

    private boolean isE2eeV2Envelope(EncryptedChatEnvelope envelope) {
        return !isBlank(envelope.getConversationId())
                && !isBlank(envelope.getSenderId())
                && !isBlank(envelope.getAadHash())
                && envelope.getMsgId() > 0;
    }

    private void validateEnvelopeSender(EncryptedChatEnvelope envelope, String expectedSender) {
        if (!Objects.equals(expectedSender, envelope.getSenderId())) {
            throw new IllegalArgumentException("envelope senderId does not match authenticated session");
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

        ClientSession current = activeSessions.get(clientId);
        ClientSession removed = null;
        if (current != null && current.socket() == socket) {
            removed = current;
            activeSessions.remove(clientId, current);
        }
        if (removed != null) {
            zero(removed.sessionKey());
            long endedAt = Instant.now().getEpochSecond();
            long duration = endedAt - removed.establishedAt();
            lastSeenAtByClient.put(clientId, endedAt);
            auditLog.info("SESSION_ENDED sessionId={} clientId={} duration_seconds={}",
                    removed.sessionId(), clientId, duration);
            broadcastUserList();
        }
    }

    private void broadcastUserList() {
        java.util.Set<String> userIds = new java.util.TreeSet<>();
        userIds.addAll(activeSessions.keySet());
        userIds.addAll(preKeyBundles.keySet());
        userIds.addAll(offlineMessages.keySet());
        userIds.addAll(lastSeenAtByClient.keySet());

        java.util.List<UserListEntry> users = new java.util.ArrayList<>();
        for (String userId : userIds) {
            if (isBlank(userId)) {
                continue;
            }
            ClientSession session = activeSessions.get(userId);
            boolean online = session != null && !session.socket().isClosed();
            boolean preKeyAvailable = preKeyBundles.containsKey(userId);
            long lastSeenAt = online && session != null
                    ? session.establishedAt()
                    : lastSeenAtByClient.getOrDefault(userId, 0L);
            users.add(new UserListEntry(userId, online, preKeyAvailable, lastSeenAt));
        }
        try {
            byte[] payload = JsonSerializer.toBytes(users);
            for (ClientSession session : activeSessions.values()) {
                if (!session.socket().isClosed()) {
                    writeFrame(session.socket(), PacketFrame.TYPE_USER_LIST, payload);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast user list", e);
        }
    }

    public void stop() {
        running = false;
        replayDefense.shutdown();
        if (ocspManager != null) {
            ocspManager.shutdown();
        }
        zero(ticketServerKey);
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
