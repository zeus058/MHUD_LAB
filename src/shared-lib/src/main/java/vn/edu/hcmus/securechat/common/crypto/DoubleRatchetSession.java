package vn.edu.hcmus.securechat.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;

/**
 * Symmetric-key Double Ratchet core used after X3DH + ML-KEM derives a 64-byte conversation key.
 */
public class DoubleRatchetSession {
    private final String conversationId;
    private final String localId;
    private final String remoteId;
    private byte[] sendChainKey;
    private byte[] receiveChainKey;
    private long nextSendMsgId = 1;
    private long nextReceiveMsgId = 1;
    private final Map<Long, SkippedKey> skippedMessageKeys = new LinkedHashMap<>();

    public DoubleRatchetSession(String conversationId, String localId, String remoteId,
                                byte[] conversationKey, boolean initiator)
            throws CryptoException {
        if (conversationKey == null
                || conversationKey.length != CryptoConstants.E2EE_CONVERSATION_KEY_SIZE_BYTES) {
            throw new CryptoException("Conversation key must be exactly 64 bytes");
        }
        this.conversationId = conversationId;
        this.localId = localId;
        this.remoteId = remoteId;

        byte[] first = Arrays.copyOfRange(conversationKey, 0, CryptoConstants.AES_KEY_SIZE_BYTES);
        byte[] second = Arrays.copyOfRange(conversationKey, CryptoConstants.AES_KEY_SIZE_BYTES,
                CryptoConstants.E2EE_CONVERSATION_KEY_SIZE_BYTES);
        if (initiator) {
            this.sendChainKey = first;
            this.receiveChainKey = second;
        } else {
            this.sendChainKey = second;
            this.receiveChainKey = first;
        }
    }

    public synchronized EncryptedChatEnvelope encrypt(String content)
            throws CryptoException, ProtocolException {
        long msgId = nextSendMsgId++;
        long timestamp = Instant.now().getEpochSecond();
        HkdfKeyDerivation.RatchetStep step = HkdfKeyDerivation.deriveRatchetStep(sendChainKey, msgId);
        byte[] oldChain = sendChainKey;
        sendChainKey = Arrays.copyOf(step.nextChainKey(), step.nextChainKey().length);
        Arrays.fill(oldChain, (byte) 0);

        byte[] plaintext = null;
        try {
            ChatMessage message = new ChatMessage(conversationId, msgId, localId, remoteId, content, timestamp);
            plaintext = JsonSerializer.toBytes(message);
            byte[] aad = aad(conversationId, msgId, localId, remoteId, timestamp);
            byte[] encrypted = AesGcmCipher.encryptWithNonce(step.messageKey(), plaintext, aad, step.nonce());
            return new EncryptedChatEnvelope(
                    conversationId,
                    msgId,
                    localId,
                    remoteId,
                    timestamp,
                    Base64.getEncoder().encodeToString(sha256(aad)),
                    Base64.getEncoder().encodeToString(encrypted));
        } finally {
            zero(plaintext);
            zero(step.messageKey());
            zero(step.nextChainKey());
            zero(step.nonce());
        }
    }

    public synchronized ChatMessage decrypt(EncryptedChatEnvelope envelope)
            throws CryptoException, ProtocolException {
        validateEnvelope(envelope);
        byte[] messageKey = null;
        boolean cached = false;
        try {
            SkippedKey skipped = skippedMessageKeys.remove(envelope.getMsgId());
            if (skipped != null) {
                messageKey = skipped.key();
                cached = true;
            } else {
                messageKey = deriveUntil(envelope.getMsgId());
            }

            byte[] aad = aad(envelope.getConversationId(), envelope.getMsgId(),
                    envelope.getSenderId(), envelope.getRecipientId(), envelope.getTimestamp());
            byte[] expectedAadHash = sha256(aad);
            byte[] actualAadHash = Base64.getDecoder().decode(envelope.getAadHash());
            if (!MessageDigest.isEqual(expectedAadHash, actualAadHash)) {
                throw new ProtocolException("AAD hash mismatch");
            }

            byte[] ciphertext = Base64.getDecoder().decode(envelope.getPayload());
            byte[] plaintext = AesGcmCipher.decrypt(messageKey, ciphertext, aad);
            try {
                return JsonSerializer.fromBytes(plaintext, ChatMessage.class);
            } finally {
                zero(plaintext);
                zero(ciphertext);
            }
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Invalid encrypted envelope encoding", e);
        } finally {
            if (!cached) {
                zero(messageKey);
            }
            evictExpiredSkippedKeys();
        }
    }

    public synchronized void destroy() {
        zero(sendChainKey);
        zero(receiveChainKey);
        for (SkippedKey skippedKey : skippedMessageKeys.values()) {
            zero(skippedKey.key());
        }
        skippedMessageKeys.clear();
    }

    private byte[] deriveUntil(long targetMsgId) throws CryptoException, ProtocolException {
        if (targetMsgId < nextReceiveMsgId) {
            throw new ProtocolException("Message key no longer available");
        }
        if (targetMsgId - nextReceiveMsgId > CryptoConstants.E2EE_SKIPPED_KEY_MAX_COUNT) {
            throw new ProtocolException("Too many skipped message keys");
        }
        while (nextReceiveMsgId < targetMsgId) {
            HkdfKeyDerivation.RatchetStep skippedStep =
                    HkdfKeyDerivation.deriveRatchetStep(receiveChainKey, nextReceiveMsgId);
            byte[] oldChain = receiveChainKey;
            receiveChainKey = Arrays.copyOf(skippedStep.nextChainKey(), skippedStep.nextChainKey().length);
            Arrays.fill(oldChain, (byte) 0);
            skippedMessageKeys.put(nextReceiveMsgId,
                    new SkippedKey(Arrays.copyOf(skippedStep.messageKey(), skippedStep.messageKey().length),
                            System.currentTimeMillis()));
            zero(skippedStep.messageKey());
            zero(skippedStep.nextChainKey());
            zero(skippedStep.nonce());
            nextReceiveMsgId++;
        }

        HkdfKeyDerivation.RatchetStep step =
                HkdfKeyDerivation.deriveRatchetStep(receiveChainKey, targetMsgId);
        byte[] oldChain = receiveChainKey;
        receiveChainKey = Arrays.copyOf(step.nextChainKey(), step.nextChainKey().length);
        Arrays.fill(oldChain, (byte) 0);
        nextReceiveMsgId = targetMsgId + 1;
        zero(step.nextChainKey());
        zero(step.nonce());
        return Arrays.copyOf(step.messageKey(), step.messageKey().length);
    }

    private void validateEnvelope(EncryptedChatEnvelope envelope) throws ProtocolException {
        if (envelope == null
                || isBlank(envelope.getConversationId())
                || isBlank(envelope.getSenderId())
                || isBlank(envelope.getRecipientId())
                || isBlank(envelope.getPayload())
                || isBlank(envelope.getAadHash())) {
            throw new ProtocolException("Encrypted envelope missing required E2EE metadata");
        }
        if (!conversationId.equals(envelope.getConversationId())
                || !remoteId.equals(envelope.getSenderId())
                || !localId.equals(envelope.getRecipientId())) {
            throw new ProtocolException("Encrypted envelope metadata mismatch");
        }
    }

    private void evictExpiredSkippedKeys() {
        long cutoff = System.currentTimeMillis() - CryptoConstants.E2EE_SKIPPED_KEY_TTL_MILLIS;
        Iterator<Map.Entry<Long, SkippedKey>> iterator = skippedMessageKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, SkippedKey> entry = iterator.next();
            if (entry.getValue().createdAtMillis() < cutoff
                    || skippedMessageKeys.size() > CryptoConstants.E2EE_SKIPPED_KEY_MAX_COUNT) {
                zero(entry.getValue().key());
                iterator.remove();
            }
        }
    }

    public static byte[] aad(String conversationId, long msgId, String senderId,
                             String recipientId, long timestamp) {
        return (conversationId + "|" + msgId + "|" + senderId + "|" + recipientId + "|" + timestamp)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) throws CryptoException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new CryptoException("SHA-256 failed", e);
        }
    }

    public DoubleRatchetSession(String conversationId, String localId, String remoteId,
                                byte[] sendChainKey, byte[] receiveChainKey,
                                long nextSendMsgId, long nextReceiveMsgId,
                                Map<Long, SkippedKey> skippedMessageKeys) {
        this.conversationId = conversationId;
        this.localId = localId;
        this.remoteId = remoteId;
        this.sendChainKey = sendChainKey != null ? Arrays.copyOf(sendChainKey, sendChainKey.length) : null;
        this.receiveChainKey = receiveChainKey != null ? Arrays.copyOf(receiveChainKey, receiveChainKey.length) : null;
        this.nextSendMsgId = nextSendMsgId;
        this.nextReceiveMsgId = nextReceiveMsgId;
        if (skippedMessageKeys != null) {
            this.skippedMessageKeys.putAll(skippedMessageKeys);
        }
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getLocalId() {
        return localId;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public byte[] getSendChainKey() {
        return sendChainKey;
    }

    public byte[] getReceiveChainKey() {
        return receiveChainKey;
    }

    public long getNextSendMsgId() {
        return nextSendMsgId;
    }

    public long getNextReceiveMsgId() {
        return nextReceiveMsgId;
    }

    public Map<Long, SkippedKey> getSkippedMessageKeys() {
        return skippedMessageKeys;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void zero(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    public record SkippedKey(byte[] key, long createdAtMillis) {}
}
