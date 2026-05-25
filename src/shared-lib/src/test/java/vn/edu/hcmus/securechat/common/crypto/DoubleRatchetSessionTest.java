package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;

class DoubleRatchetSessionTest {

    @Test
    void decryptsOutOfOrderWithSkippedKeyCache() throws Exception {
        byte[] conversationKey = new byte[CryptoConstants.E2EE_CONVERSATION_KEY_SIZE_BYTES];
        Arrays.fill(conversationKey, (byte) 9);

        DoubleRatchetSession alice = new DoubleRatchetSession(
                "conv-1", "alice", "bob", conversationKey, true);
        DoubleRatchetSession bob = new DoubleRatchetSession(
                "conv-1", "bob", "alice", conversationKey, false);

        EncryptedChatEnvelope first = alice.encrypt("one");
        EncryptedChatEnvelope second = alice.encrypt("two");

        ChatMessage two = bob.decrypt(second);
        ChatMessage one = bob.decrypt(first);

        assertEquals("two", two.getContent());
        assertEquals("one", one.getContent());

        alice.destroy();
        bob.destroy();
    }
}
