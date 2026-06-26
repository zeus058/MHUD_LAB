package vn.edu.hcmus.securechat.client.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.Argon2idKeyDerivation;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.crypto.DoubleRatchetSession;
import vn.edu.hcmus.securechat.common.crypto.EcdheService;
import vn.edu.hcmus.securechat.common.crypto.HkdfKeyDerivation;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatHandshakeResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.E2eeInitMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
import vn.edu.hcmus.securechat.common.protocol.dto.OneTimePreKey;
import vn.edu.hcmus.securechat.common.protocol.dto.PreKeyBundle;
import vn.edu.hcmus.securechat.common.protocol.dto.PreKeyRequest;
import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;

/**
 * Client-side secure session service.
 *
 * The Kerberos ST authenticates access to Chat Server. Message content is then
 * protected by client-to-client Pre-Key + ECDHE + Double Ratchet.
 */
public class E2eeCryptoService {
    private static final Logger log = LoggerFactory.getLogger(E2eeCryptoService.class);

    private static final int OPK_POOL_SIZE = 12;
    private static final long PREKEY_TIMEOUT_SECONDS = 8;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final byte[] PREKEY_STORE_AAD =
            "SecureChat-client-prekey-store-v1".getBytes(StandardCharsets.UTF_8);

    public enum ActivityTone {
        INFO, SUCCESS, ERROR
    }

    @FunctionalInterface
    public interface ActivitySink {
        void onActivity(String title, String body, ActivityTone tone);
    }

    private final SecureRandom secureRandom = new SecureRandom();
    private final Object writeLock = new Object();
    private final AtomicInteger preKeyIdSequence = new AtomicInteger(secureRandom.nextInt(10_000) + 1);
    private final Map<String, DoubleRatchetSession> ratchetSessions = new ConcurrentHashMap<>();
    private final Map<String, DoubleRatchetSession> sessionsByConversation = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<PreKeyBundle>> pendingPreKeyRequests = new ConcurrentHashMap<>();
    private final Map<Integer, PreKeyMaterial> oneTimePreKeyPrivate = new ConcurrentHashMap<>();

    private volatile ActivitySink activitySink = (title, body, tone) -> { };

    private byte[] masterSessionKey;
    private Socket chatSocket;
    private String currentUsername;
    private String identityCertBase64;
    private int signedPreKeyId;
    private KeyPair signedPreKeyEcdh;
    private String signedPreKeyEcdhPub;
    private PreKeyBundle localPreKeyBundle;
    private Path preKeyStoreFile;
    private byte[] preKeyStoreKey;

    public void setActivitySink(ActivitySink activitySink) {
        this.activitySink = activitySink == null ? (title, body, tone) -> { } : activitySink;
    }

    /**
     * Connects to Chat Server with ST authentication, then uploads a Pre-Key Bundle
     * so peers can establish E2EE v2 without exposing content keys to the server.
     */
    public void performHandshake(String username, char[] password) throws Exception {
        fire("Connect to Chat Server", "Opening socket to " + ServerConfig.CHAT_HOST + ":" + ServerConfig.CHAT_PORT
                + " and preparing the ST authenticator.", ActivityTone.INFO);
        log.info("Connecting to Chat Server {}:{}", ServerConfig.CHAT_HOST, ServerConfig.CHAT_PORT);

        byte[] stCacheData = null;
        byte[] sessionKey = null;
        byte[] authBytes = null;
        byte[] encryptedAuth = null;
        try {
            stCacheData = vn.edu.hcmus.securechat.client.kerberos.TicketCache
                    .getTicket(username, "ST_" + ServerConfig.CHAT_HOST, password);
            if (stCacheData == null) {
                throw new Exception("ST was not found in cache. Please sign in again.");
            }

            String cacheStr = new String(stCacheData, StandardCharsets.UTF_8);
            String[] parts = cacheStr.split("\\|\\|\\|");
            if (parts.length < 2) {
                throw new Exception("Invalid ST cache data format.");
            }
            String stBase64 = parts[0];
            sessionKey = Base64.getDecoder().decode(parts[1]);
            String stId = parts.length >= 3 ? parts[2] : "";

            String nonce = randomNonceBase64();
            long timestamp = Instant.now().getEpochSecond();
            AuthenticatorJson auth = new AuthenticatorJson(
                    username,
                    timestamp,
                    nonce,
                    stId,
                    ServerConfig.CHAT_HOST,
                    1L,
                    "");
            authBytes = JsonSerializer.toBytes(auth);
            encryptedAuth = AesGcmCipher.encrypt(sessionKey, authBytes);
            String authenticatorB64 = Base64.getEncoder().encodeToString(encryptedAuth);

            this.currentUsername = username;
            this.masterSessionKey = Arrays.copyOf(sessionKey, sessionKey.length);
            ChatHandshakeRequest payload = new ChatHandshakeRequest(
                    stBase64, authenticatorB64, null, null);

            chatSocket = new Socket(ServerConfig.CHAT_HOST, ServerConfig.CHAT_PORT);
            chatSocket.setSoTimeout(ServerConfig.READ_TIMEOUT_MS);

            PacketFrame.write(chatSocket.getOutputStream(),
                    PacketFrame.TYPE_CHAT_HANDSHAKE,
                    JsonSerializer.toBytes(payload));

            PacketFrame response = PacketFrame.read(chatSocket.getInputStream());
            if (response.getType() == PacketFrame.TYPE_ERROR) {
                String err = new String(response.getPayload(), StandardCharsets.UTF_8);
                chatSocket.close();
                throw new Exception("Chat Server rejected the handshake: " + err);
            }
            if (response.getType() != PacketFrame.TYPE_CHAT_HANDSHAKE) {
                chatSocket.close();
                throw new Exception("Chat Server returned an invalid response.");
            }

            ChatHandshakeResponse handshake = JsonSerializer.fromBytes(
                    response.getPayload(), ChatHandshakeResponse.class);
            if (!"OK".equalsIgnoreCase(handshake.getStatus())) {
                throw new Exception("Chat Server did not confirm the access session.");
            }
            fire("ST accepted", "Chat Server verified the service ticket, Control Vector, and authenticator.",
                    ActivityTone.SUCCESS);

            chatSocket.setSoTimeout(0);
            uploadLocalPreKeyBundle(username, password);
            log.info("Chat Server handshake succeeded for user={}", username);
        } catch (Exception e) {
            fire("Handshake failed", e.getMessage(), ActivityTone.ERROR);
            log.error("E2EE handshake failed", e);
            throw new Exception("Chat Server handshake failed: " + e.getMessage(), e);
        } finally {
            zero(stCacheData);
            zero(sessionKey);
            zero(authBytes);
            zero(encryptedAuth);
        }
    }

    public EncryptedChatEnvelope encryptForPeer(String peerId, String content) throws Exception {
        DoubleRatchetSession session = ratchetSessions.get(peerId);
        if (session == null) {
            synchronized (ratchetSessions) {
                session = ratchetSessions.get(peerId);
                if (session == null) {
                    session = establishOutboundSession(peerId);
                }
            }
        }

        EncryptedChatEnvelope env = session.encrypt(content);
        saveSessions();
        return env;
    }

    public ChatMessage decryptIncoming(EncryptedChatEnvelope envelope) throws Exception {
        log.info("decryptIncoming: conversationId='{}', senderId='{}', msgId={}, aadHash='{}', payload={}",
                envelope.getConversationId(), envelope.getSenderId(), envelope.getMsgId(), envelope.getAadHash(),
                envelope.getPayload() != null && envelope.getPayload().length() > 30 ? envelope.getPayload().substring(0, 30) + "..." : envelope.getPayload());
        if (isE2eeV2Envelope(envelope)) {
            DoubleRatchetSession session = sessionsByConversation.get(envelope.getConversationId());
            if (session == null) {
                session = ratchetSessions.get(envelope.getSenderId());
            }
            if (session == null) {
                throw new ProtocolException("No Double Ratchet session with " + envelope.getSenderId());
            }
            ChatMessage msg = session.decrypt(envelope);
            saveSessions();
            return msg;
        }

        byte[] encrypted = Base64.getDecoder().decode(envelope.getPayload());
        byte[] plain = AesGcmCipher.decrypt(masterSessionKey, encrypted);
        try {
            return JsonSerializer.fromBytes(plain, ChatMessage.class);
        } finally {
            zero(encrypted);
            zero(plain);
        }
    }

    public void acceptPreKeyBundle(PreKeyBundle bundle) {
        if (bundle == null || isBlank(bundle.getOwnerId())) {
            return;
        }
        CompletableFuture<PreKeyBundle> future = pendingPreKeyRequests.remove(bundle.getOwnerId());
        if (future != null) {
            future.complete(bundle);
        }
        int opk = bundle.getOneTimePreKeys() == null ? 0 : bundle.getOneTimePreKeys().size();
        fire("Pre-Key Bundle received", "Received bundle for @" + bundle.getOwnerId()
                + " with " + opk + " one-time pre-key.", ActivityTone.INFO);
    }

    public void acceptE2eeInit(E2eeInitMessage init) throws Exception {
        validateIncomingInit(init);
        if (sessionsByConversation.containsKey(init.getConversationId())) {
            return;
        }

        PreKeyMaterial material = selectLocalPreKey(init.getUsedOneTimePreKeyId());
        byte[] ssEcdhe = null;
        byte[] conversationKey = null;
        byte[] salt = null;
        try {
            String transcript = buildInitTranscript(init, material.ecdhPubKey());
            verifyTranscriptHash(init, transcript);
            verifySignatureFromCertificate(init.getSenderCertEcdsa(), transcript, init.getSignatureEcdsa());

            PublicKey senderEcdhPub = EcdheService.decodePublicKey(init.getEphemeralEcdhPubKey());
            ssEcdhe = EcdheService.computeSharedSecret(material.ecdhPair().getPrivate(), senderEcdhPub);
            salt = conversationSalt(init.getConversationId(), init.getNonce());
            conversationKey = HkdfKeyDerivation.deriveConversationKey(ssEcdhe, salt);

            DoubleRatchetSession session = new DoubleRatchetSession(
                    init.getConversationId(),
                    currentUsername,
                    init.getSenderId(),
                    conversationKey,
                    false);
            sessionsByConversation.put(init.getConversationId(), session);
            ratchetSessions.putIfAbsent(init.getSenderId(), session);
            saveSessions();

            if (init.getUsedOneTimePreKeyId() != null
                    && oneTimePreKeyPrivate.remove(init.getUsedOneTimePreKeyId()) != null) {
                fire("OPK consumed", "One-time pre-key #" + init.getUsedOneTimePreKeyId()
                        + " was used exactly once for @" + init.getSenderId() + ".", ActivityTone.SUCCESS);
                savePreKeyStoreQuietly();
            }
            fire("E2EE init validated", "Transcript verified and Double Ratchet opened with @"
                    + init.getSenderId() + ".", ActivityTone.SUCCESS);
        } finally {
            zero(ssEcdhe);
            zero(conversationKey);
            zero(salt);
        }
    }

    public void handleServerError(String message) {
        ProtocolException error = new ProtocolException(message == null ? "Chat Server error" : message);
        for (Map.Entry<String, CompletableFuture<PreKeyBundle>> entry : pendingPreKeyRequests.entrySet()) {
            entry.getValue().completeExceptionally(error);
        }
        pendingPreKeyRequests.clear();
        fire("Server error", message == null ? "The current request was rejected." : message, ActivityTone.ERROR);
    }

    public byte[] getMasterSessionKey() {
        return masterSessionKey;
    }

    public Socket getChatSocket() {
        return chatSocket;
    }

    public void sendFrame(byte type, byte[] payload) throws IOException {
        Socket chatSocketRef = chatSocket;
        if (chatSocketRef == null || chatSocketRef.isClosed()) {
            throw new IOException("Chat socket is not connected");
        }
        synchronized (writeLock) {
            PacketFrame.write(chatSocketRef.getOutputStream(), type, payload);
        }
    }

    public void disconnect() {
        java.util.Set<DoubleRatchetSession> sessions = new java.util.HashSet<>(ratchetSessions.values());
        sessions.addAll(sessionsByConversation.values());
        for (DoubleRatchetSession session : sessions) {
            session.destroy();
        }
        ratchetSessions.clear();
        sessionsByConversation.clear();
        oneTimePreKeyPrivate.clear();
        localPreKeyBundle = null;
        zero(masterSessionKey);
        zero(preKeyStoreKey);
        preKeyStoreKey = null;
        Socket chatSocketRef = chatSocket;
        if (chatSocketRef != null && !chatSocketRef.isClosed()) {
            try {
                chatSocketRef.close();
            } catch (IOException ignored) {
                // Nothing to do during UI logout.
            }
        }
    }

    private void uploadLocalPreKeyBundle(String username, char[] password) throws Exception {
        X509Certificate cert = PkiManager.getCertificate();
        if (cert == null || PkiManager.getPrivateKey() == null) {
            throw new ProtocolException("Identity certificate has not been loaded for signing the Pre-Key Bundle.");
        }

        this.identityCertBase64 = Base64.getEncoder().encodeToString(cert.getEncoded());
        initializePreKeyStore(username, password);
        boolean restoredStore = tryLoadPreKeyStore();
        loadSessions();
        if (restoredStore) {
            sendFrame(PacketFrame.TYPE_PREKEY_UPLOAD, JsonSerializer.toBytes(localPreKeyBundle));
            int opkCount = localPreKeyBundle.getOneTimePreKeys() == null
                    ? 0 : localPreKeyBundle.getOneTimePreKeys().size();
            fire("Local Pre-Key loaded", "Restored signed pre-key and " + opkCount
                    + " one-time pre-key for receiving offline messages.", ActivityTone.SUCCESS);
            return;
        }

        oneTimePreKeyPrivate.clear();
        this.signedPreKeyId = preKeyIdSequence.getAndIncrement();
        this.signedPreKeyEcdh = EcdheService.generateKeyPair();
        this.signedPreKeyEcdhPub = EcdheService.encodePublicKey(signedPreKeyEcdh.getPublic());

        List<OneTimePreKey> opks = new ArrayList<>();
        for (int i = 0; i < OPK_POOL_SIZE; i++) {
            int id = preKeyIdSequence.getAndIncrement();
            KeyPair opkEcdh = EcdheService.generateKeyPair();
            String ecdhPub = EcdheService.encodePublicKey(opkEcdh.getPublic());
            oneTimePreKeyPrivate.put(id, new PreKeyMaterial(id, opkEcdh, ecdhPub));
            opks.add(new OneTimePreKey(id, ecdhPub));
        }

        PreKeyBundle bundle = new PreKeyBundle();
        bundle.setOwnerId(username);
        bundle.setIdentityCertEcdsa(identityCertBase64);
        bundle.setSignedPreKeyId(signedPreKeyId);
        bundle.setSignedPreKeyEcdh(signedPreKeyEcdhPub);
        bundle.setOneTimePreKeys(opks);
        bundle.setBundleTimestamp(Instant.now().getEpochSecond());
        bundle.setSignedPreKeySignatureEcdsa(signText(signedPreKeyTranscript(bundle)));
        bundle.setBundleSignatureEcdsa(signText(bundleTranscript(bundle)));
        bundle.setLastResort(false);
        localPreKeyBundle = bundle;
        savePreKeyStore();

        sendFrame(PacketFrame.TYPE_PREKEY_UPLOAD, JsonSerializer.toBytes(bundle));
        fire("Pre-Key Bundle uploaded", "Published signed pre-key and " + OPK_POOL_SIZE
                + " one-time pre-key with RSA signature.", ActivityTone.SUCCESS);
    }

    private void initializePreKeyStore(String username, char[] password) throws Exception {
        ClientStoragePaths.ensureUserDir(username);
        preKeyStoreFile = ClientStoragePaths.preKeyStoreFile(username);
        byte[] salt = loadOrCreatePreKeyStoreSalt(username);
        try {
            zero(preKeyStoreKey);
            preKeyStoreKey = Argon2idKeyDerivation.deriveDbKey(password, salt);
        } finally {
            zero(salt);
        }
    }

    private byte[] loadOrCreatePreKeyStoreSalt(String username) throws IOException {
        Path saltFile = ClientStoragePaths.preKeyStoreSaltFile(username);
        if (Files.exists(saltFile) && Files.size(saltFile) >= CryptoConstants.ARGON2ID_SALT_SIZE) {
            byte[] fileBytes = Files.readAllBytes(saltFile);
            if (fileBytes.length >= CryptoConstants.ARGON2ID_SALT_SIZE) {
                return Arrays.copyOf(fileBytes, CryptoConstants.ARGON2ID_SALT_SIZE);
            }
        }

        byte[] salt = new byte[CryptoConstants.ARGON2ID_SALT_SIZE];
        secureRandom.nextBytes(salt);
        Files.write(saltFile, salt);
        return salt;
    }

    private boolean tryLoadPreKeyStore() {
        if (preKeyStoreFile == null || preKeyStoreKey == null || !Files.isRegularFile(preKeyStoreFile)) {
            return false;
        }

        byte[] encrypted = null;
        byte[] json = null;
        try {
            encrypted = Files.readAllBytes(preKeyStoreFile);
            if (encrypted.length == 0) {
                return false;
            }
            json = AesGcmCipher.decrypt(preKeyStoreKey, encrypted, PREKEY_STORE_AAD);
            PersistedPreKeyStore store = JsonSerializer.fromBytes(json, PersistedPreKeyStore.class);
            if (!Objects.equals(identityCertBase64, store.identityCertBase64)) {
                fire("Pre-Key rotation needed", "Identity certificate changed, so the client will generate a new bundle.",
                        ActivityTone.INFO);
                return false;
            }
            applyPreKeyStore(store);
            return localPreKeyBundle != null && signedPreKeyEcdh != null;
        } catch (Exception e) {
            log.warn("Could not load persisted pre-key store", e);
            fire("Old Pre-Key could not be read", "The client will generate a new bundle; old offline messages may not open.",
                    ActivityTone.ERROR);
            return false;
        } finally {
            zero(encrypted);
            zero(json);
        }
    }

    private void applyPreKeyStore(PersistedPreKeyStore store) throws Exception {
        if (store == null || store.bundle == null) {
            throw new ProtocolException("Pre-Key store is empty.");
        }
        signedPreKeyId = store.signedPreKeyId;
        signedPreKeyEcdhPub = store.signedPreKeyEcdhPub;
        signedPreKeyEcdh = decodeEcdhKeyPair(store.signedPreKeyEcdhPub, store.signedPreKeyEcdhPrivate);

        oneTimePreKeyPrivate.clear();
        int maxId = signedPreKeyId;
        if (store.oneTimePreKeys != null) {
            for (PersistedOneTimePreKey item : store.oneTimePreKeys) {
                if (item == null || item.id <= 0) {
                    continue;
                }
                KeyPair ecdh = decodeEcdhKeyPair(item.ecdhPubKey, item.ecdhPrivate);
                oneTimePreKeyPrivate.put(item.id,
                        new PreKeyMaterial(item.id, ecdh, item.ecdhPubKey));
                maxId = Math.max(maxId, item.id);
            }
        }
        localPreKeyBundle = store.bundle;
        syncLocalPreKeyBundle();
        preKeyIdSequence.set(Math.max(store.nextPreKeyId, maxId + 1));
    }

    private void savePreKeyStore() throws Exception {
        if (preKeyStoreFile == null || preKeyStoreKey == null || localPreKeyBundle == null) {
            return;
        }
        syncLocalPreKeyBundle();

        PersistedPreKeyStore store = new PersistedPreKeyStore();
        store.nextPreKeyId = preKeyIdSequence.get();
        store.identityCertBase64 = identityCertBase64;
        store.signedPreKeyId = signedPreKeyId;
        store.signedPreKeyEcdhPub = signedPreKeyEcdhPub;
        store.signedPreKeyEcdhPrivate = encodePrivateKey(signedPreKeyEcdh.getPrivate());
        store.bundle = localPreKeyBundle;
        store.oneTimePreKeys = oneTimePreKeyPrivate.values().stream()
                .sorted(Comparator.comparingInt(PreKeyMaterial::id))
                .map(material -> {
                    PersistedOneTimePreKey item = new PersistedOneTimePreKey();
                    item.id = material.id();
                    item.ecdhPubKey = material.ecdhPubKey();
                    item.ecdhPrivate = encodePrivateKey(material.ecdhPair().getPrivate());
                    return item;
                })
                .toList();

        byte[] json = null;
        byte[] encrypted = null;
        try {
            json = JsonSerializer.toBytes(store);
            encrypted = AesGcmCipher.encrypt(preKeyStoreKey, json, PREKEY_STORE_AAD);
            Files.write(preKeyStoreFile, encrypted);
        } finally {
            zero(json);
            zero(encrypted);
        }
    }

    private void savePreKeyStoreQuietly() {
        try {
            savePreKeyStore();
        } catch (Exception e) {
            log.warn("Could not persist pre-key store", e);
            fire("Pre-Key could not be saved", "The used OPK was not written back to local storage.",
                    ActivityTone.ERROR);
        }
    }

    private void syncLocalPreKeyBundle() {
        if (localPreKeyBundle == null) {
            return;
        }
        List<OneTimePreKey> opks = oneTimePreKeyPrivate.values().stream()
                .sorted(Comparator.comparingInt(PreKeyMaterial::id))
                .map(material -> new OneTimePreKey(material.id(), material.ecdhPubKey()))
                .toList();
        localPreKeyBundle.setOneTimePreKeys(opks);
    }

    private DoubleRatchetSession establishOutboundSession(String peerId) throws Exception {
        fire("Request Pre-Key Bundle", "Asking Chat Server for @" + peerId
                + "'s bundle; the server only forwards public keys.", ActivityTone.INFO);
        PreKeyBundle bundle = requestPeerPreKeyBundle(peerId);
        verifyBundle(bundle);

        SelectedPreKey selected = selectRemotePreKey(bundle);
        KeyPair localEcdh = EcdheService.generateKeyPair();
        byte[] ssEcdhe = null;
        byte[] conversationKey = null;
        byte[] salt = null;
        try {
            PublicKey remoteEcdh = EcdheService.decodePublicKey(selected.ecdhPubKey());
            ssEcdhe = EcdheService.computeSharedSecret(localEcdh.getPrivate(), remoteEcdh);

            String nonce = randomNonceBase64();
            String conversationId = conversationId(currentUsername, peerId, nonce);
            salt = conversationSalt(conversationId, nonce);
            conversationKey = HkdfKeyDerivation.deriveConversationKey(ssEcdhe, salt);

            E2eeInitMessage init = new E2eeInitMessage();
            init.setConversationId(conversationId);
            init.setSenderId(currentUsername);
            init.setRecipientId(peerId);
            init.setEphemeralEcdhPubKey(EcdheService.encodePublicKey(localEcdh.getPublic()));
            init.setNonce(nonce);
            init.setTimestamp(Instant.now().getEpochSecond());
            init.setUsedOneTimePreKeyId(selected.id());
            init.setSenderCertEcdsa(identityCertBase64);

            String transcript = buildInitTranscript(init, selected.ecdhPubKey());
            init.setTranscriptHash(Base64.getEncoder().encodeToString(sha256(transcript)));
            init.setSignatureEcdsa(signText(transcript));

            DoubleRatchetSession session = new DoubleRatchetSession(
                    conversationId,
                    currentUsername,
                    peerId,
                    conversationKey,
                    true);
            ratchetSessions.put(peerId, session);
            sessionsByConversation.put(conversationId, session);
            saveSessions();
            sendFrame(PacketFrame.TYPE_E2EE_INIT, JsonSerializer.toBytes(init));
            fire("E2EE init sent", "Transcript covers ECDHE and the selected pre-key for @"
                    + peerId + ".", ActivityTone.SUCCESS);
            return session;
        } finally {
            zero(ssEcdhe);
            zero(conversationKey);
            zero(salt);
        }
    }

    private PreKeyBundle requestPeerPreKeyBundle(String peerId) throws Exception {
        CompletableFuture<PreKeyBundle> future = new CompletableFuture<>();
        CompletableFuture<PreKeyBundle> previous = pendingPreKeyRequests.put(peerId, future);
        if (previous != null) {
            previous.completeExceptionally(new ProtocolException("Pre-Key request superseded"));
        }

        sendFrame(PacketFrame.TYPE_PREKEY_REQUEST,
                JsonSerializer.toBytes(new PreKeyRequest(peerId)));
        try {
            return future.get(PREKEY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingPreKeyRequests.remove(peerId);
            throw new ProtocolException("Timed out waiting for Pre-Key Bundle from @" + peerId, e);
        }
    }

    private void verifyBundle(PreKeyBundle bundle) throws Exception {
        if (bundle == null || isBlank(bundle.getOwnerId())) {
            throw new ProtocolException("Pre-Key Bundle is empty.");
        }
        if (isBlank(bundle.getIdentityCertEcdsa())
                || isBlank(bundle.getSignedPreKeyEcdh())) {
            throw new ProtocolException("Pre-Key Bundle is missing required keys.");
        }

        if (!isBlank(bundle.getBundleSignatureEcdsa())) {
            verifySignatureFromCertificate(
                    bundle.getIdentityCertEcdsa(),
                    bundleTranscript(bundle),
                    bundle.getBundleSignatureEcdsa());
        }
        if (!isBlank(bundle.getSignedPreKeySignatureEcdsa())) {
            verifySignatureFromCertificate(
                    bundle.getIdentityCertEcdsa(),
                    signedPreKeyTranscript(bundle),
                    bundle.getSignedPreKeySignatureEcdsa());
        }
        fire("Verify Pre-Key", "X.509 signature for bundle @" + bundle.getOwnerId()
                + " is valid; the server is not treated as the final trust source.", ActivityTone.SUCCESS);
    }

    private SelectedPreKey selectRemotePreKey(PreKeyBundle bundle) {
        if (bundle.getOneTimePreKeys() != null && !bundle.getOneTimePreKeys().isEmpty()) {
            OneTimePreKey opk = bundle.getOneTimePreKeys().get(0);
            return new SelectedPreKey(opk.getId(), opk.getEcdhPubKey());
        }
        return new SelectedPreKey(bundle.getSignedPreKeyId(),
                bundle.getSignedPreKeyEcdh());
    }

    private PreKeyMaterial selectLocalPreKey(Integer usedOneTimePreKeyId) throws ProtocolException {
        if (usedOneTimePreKeyId != null) {
            PreKeyMaterial material = oneTimePreKeyPrivate.get(usedOneTimePreKeyId);
            if (material != null) {
                return material;
            }
            if (usedOneTimePreKeyId == signedPreKeyId) {
                return new PreKeyMaterial(signedPreKeyId, signedPreKeyEcdh, signedPreKeyEcdhPub);
            }
            throw new ProtocolException("One-time pre-key #" + usedOneTimePreKeyId + " is no longer available.");
        }
        if (signedPreKeyEcdh != null) {
            return new PreKeyMaterial(signedPreKeyId, signedPreKeyEcdh, signedPreKeyEcdhPub);
        }
        throw new ProtocolException("Private pre-key for opening E2EE init was not found.");
    }

    private void validateIncomingInit(E2eeInitMessage init) throws ProtocolException {
        if (init == null
                || isBlank(init.getConversationId())
                || isBlank(init.getSenderId())
                || isBlank(init.getRecipientId())
                || isBlank(init.getEphemeralEcdhPubKey())
                || isBlank(init.getNonce())
                || isBlank(init.getTranscriptHash())
                || isBlank(init.getSignatureEcdsa())) {
            throw new ProtocolException("E2EE init is missing required fields.");
        }
        if (!Objects.equals(currentUsername, init.getRecipientId())) {
            throw new ProtocolException("E2EE init is not intended for the current client.");
        }
    }

    private void verifyTranscriptHash(E2eeInitMessage init, String transcript) throws Exception {
        byte[] expected = sha256(transcript);
        byte[] actual = Base64.getDecoder().decode(init.getTranscriptHash());
        try {
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new ProtocolException("Transcript hash mismatch.");
            }
        } finally {
            zero(expected);
            zero(actual);
        }
    }

    private String bundleTranscript(PreKeyBundle bundle) {
        return String.join("|",
                "SecureChat-PreKeyBundle-v2",
                safe(bundle.getOwnerId()),
                safe(bundle.getIdentityCertEcdsa()),
                String.valueOf(bundle.getSignedPreKeyId()),
                safe(bundle.getSignedPreKeyEcdh()),
                String.valueOf(bundle.getBundleTimestamp()));
    }

    private String signedPreKeyTranscript(PreKeyBundle bundle) {
        return String.join("|",
                "SecureChat-SignedPreKey-v2",
                safe(bundle.getOwnerId()),
                String.valueOf(bundle.getSignedPreKeyId()),
                safe(bundle.getSignedPreKeyEcdh()),
                String.valueOf(bundle.getBundleTimestamp()));
    }

    private String buildInitTranscript(E2eeInitMessage init, String selectedEcdhPubKey) {
        return String.join("|",
                "SecureChat-E2EE-Init-v2",
                safe(init.getConversationId()),
                safe(init.getSenderId()),
                safe(init.getRecipientId()),
                safe(init.getEphemeralEcdhPubKey()),
                safe(init.getNonce()),
                String.valueOf(init.getTimestamp()),
                String.valueOf(init.getUsedOneTimePreKeyId()),
                safe(selectedEcdhPubKey));
    }

    private String signText(String text) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(PkiManager.getPrivateKey());
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private void verifySignatureFromCertificate(String certificateBase64, String text, String signatureBase64)
            throws Exception {
        X509Certificate cert = decodeCertificate(certificateBase64);
        cert.checkValidity();
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(cert.getPublicKey());
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        try {
            if (!signature.verify(signatureBytes)) {
                throw new ProtocolException("Transcript or Pre-Key Bundle signature is invalid.");
            }
        } finally {
            zero(signatureBytes);
        }
    }

    private static KeyPair decodeEcdhKeyPair(String publicKeyBase64, String privateKeyBase64)
            throws CryptoException {
        return new KeyPair(
                EcdheService.decodePublicKey(publicKeyBase64),
                decodePrivateKey("EC", privateKeyBase64));
    }

    private static PrivateKey decodePrivateKey(String algorithm, String privateKeyBase64)
            throws CryptoException {
        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new CryptoException("Failed to decode private key for " + algorithm, e);
        } finally {
            zero(keyBytes);
        }
    }

    private static String encodePrivateKey(PrivateKey privateKey) {
        byte[] encoded = privateKey == null ? null : privateKey.getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw new IllegalStateException("Private key is not encodable");
        }
        return Base64.getEncoder().encodeToString(encoded);
    }

    private static X509Certificate decodeCertificate(String certificateBase64) throws Exception {
        byte[] certBytes = Base64.getDecoder().decode(certificateBase64);
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
        } finally {
            zero(certBytes);
        }
    }

    private String conversationId(String sender, String recipient, String nonce) throws CryptoException {
        byte[] digest = sha256(sender + "|" + recipient + "|" + nonce + "|" + Instant.now().toEpochMilli());
        try {
            return "conv-" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, 18));
        } finally {
            zero(digest);
        }
    }

    private static byte[] conversationSalt(String conversationId, String nonce) throws CryptoException {
        return sha256(nonce + "|" + conversationId);
    }

    private static boolean isE2eeV2Envelope(EncryptedChatEnvelope envelope) {
        return envelope != null
                && !isBlank(envelope.getConversationId())
                && !isBlank(envelope.getSenderId())
                && !isBlank(envelope.getAadHash())
                && envelope.getMsgId() > 0;
    }

    private static String randomNonceBase64() {
        byte[] nonce = new byte[CryptoConstants.NONCE_SIZE_BYTES];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    private static byte[] sha256(String text) throws CryptoException {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CryptoException("SHA-256 failed", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void fire(String title, String body, ActivityTone tone) {
        activitySink.onActivity(title, body, tone);
    }

    private static void zero(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    public static class PersistedPreKeyStore {
        public int nextPreKeyId;
        public String identityCertBase64;
        public int signedPreKeyId;
        public String signedPreKeyEcdhPub;
        public String signedPreKeyEcdhPrivate;
        public PreKeyBundle bundle;
        public List<PersistedOneTimePreKey> oneTimePreKeys = new ArrayList<>();
    }

    public static class PersistedOneTimePreKey {
        public int id;
        public String ecdhPubKey;
        public String ecdhPrivate;
    }

    private record SelectedPreKey(Integer id, String ecdhPubKey) { }

    private record PreKeyMaterial(
            int id,
            KeyPair ecdhPair,
            String ecdhPubKey) { }

    private void saveSessions() {
        if (currentUsername == null || preKeyStoreKey == null) {
            return;
        }
        try {
            PersistedSessions ps = new PersistedSessions();
            java.util.Set<DoubleRatchetSession> uniqueSessions = new java.util.HashSet<>();
            uniqueSessions.addAll(ratchetSessions.values());
            uniqueSessions.addAll(sessionsByConversation.values());

            for (DoubleRatchetSession session : uniqueSessions) {
                PersistedSession s = new PersistedSession();
                s.conversationId = session.getConversationId();
                s.localId = session.getLocalId();
                s.remoteId = session.getRemoteId();
                s.sendChainKey = session.getSendChainKey() != null ? Base64.getEncoder().encodeToString(session.getSendChainKey()) : null;
                s.receiveChainKey = session.getReceiveChainKey() != null ? Base64.getEncoder().encodeToString(session.getReceiveChainKey()) : null;
                s.nextSendMsgId = session.getNextSendMsgId();
                s.nextReceiveMsgId = session.getNextReceiveMsgId();
                if (session.getSkippedMessageKeys() != null) {
                    for (Map.Entry<Long, DoubleRatchetSession.SkippedKey> entry : session.getSkippedMessageKeys().entrySet()) {
                        PersistedSkippedKey sk = new PersistedSkippedKey();
                        sk.msgId = entry.getKey();
                        sk.key = entry.getValue().key() != null ? Base64.getEncoder().encodeToString(entry.getValue().key()) : null;
                        sk.createdAtMillis = entry.getValue().createdAtMillis();
                        s.skippedKeys.add(sk);
                    }
                }
                ps.sessions.add(s);
            }

            byte[] jsonBytes = JsonSerializer.toBytes(ps);
            byte[] encrypted = AesGcmCipher.encrypt(preKeyStoreKey, jsonBytes, PREKEY_STORE_AAD);
            Path sessionFile = ClientStoragePaths.e2eeSessionsFile(currentUsername);
            Files.write(sessionFile, encrypted);
        } catch (Exception e) {
            log.warn("Could not save E2EE Double Ratchet sessions", e);
        }
    }

    private void loadSessions() {
        if (currentUsername == null || preKeyStoreKey == null) {
            return;
        }
        Path sessionFile = ClientStoragePaths.e2eeSessionsFile(currentUsername);
        if (!Files.isRegularFile(sessionFile)) {
            return;
        }
        byte[] encrypted = null;
        byte[] jsonBytes = null;
        try {
            encrypted = Files.readAllBytes(sessionFile);
            if (encrypted.length == 0) {
                return;
            }
            jsonBytes = AesGcmCipher.decrypt(preKeyStoreKey, encrypted, PREKEY_STORE_AAD);
            PersistedSessions ps = JsonSerializer.fromBytes(jsonBytes, PersistedSessions.class);
            if (ps != null && ps.sessions != null) {
                for (PersistedSession s : ps.sessions) {
                    byte[] sendChainKey = s.sendChainKey != null ? Base64.getDecoder().decode(s.sendChainKey) : null;
                    byte[] receiveChainKey = s.receiveChainKey != null ? Base64.getDecoder().decode(s.receiveChainKey) : null;
                    Map<Long, DoubleRatchetSession.SkippedKey> skippedKeys = new java.util.LinkedHashMap<>();
                    if (s.skippedKeys != null) {
                        for (PersistedSkippedKey sk : s.skippedKeys) {
                            byte[] keyBytes = sk.key != null ? Base64.getDecoder().decode(sk.key) : null;
                            skippedKeys.put(sk.msgId, new DoubleRatchetSession.SkippedKey(keyBytes, sk.createdAtMillis));
                        }
                    }
                    DoubleRatchetSession session = new DoubleRatchetSession(
                            s.conversationId,
                            s.localId,
                            s.remoteId,
                            sendChainKey,
                            receiveChainKey,
                            s.nextSendMsgId,
                            s.nextReceiveMsgId,
                            skippedKeys
                    );
                    sessionsByConversation.put(s.conversationId, session);
                    ratchetSessions.put(s.remoteId, session);
                }
                log.info("Restored {} Double Ratchet sessions for user={}", ps.sessions.size(), currentUsername);
            }
        } catch (Exception e) {
            log.warn("Could not load E2EE Double Ratchet sessions", e);
        } finally {
            zero(encrypted);
            zero(jsonBytes);
        }
    }

    public static class PersistedSessions {
        public List<PersistedSession> sessions = new ArrayList<>();
    }

    public static class PersistedSession {
        public String conversationId;
        public String localId;
        public String remoteId;
        public String sendChainKey;
        public String receiveChainKey;
        public long nextSendMsgId;
        public long nextReceiveMsgId;
        public List<PersistedSkippedKey> skippedKeys = new ArrayList<>();
    }

    public static class PersistedSkippedKey {
        public long msgId;
        public String key;
        public long createdAtMillis;
    }
}
