package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;

class ReplayDefenseServiceTest {

    private ReplayDefenseService service;

    @BeforeEach
    void setUp() {
        service = new ReplayDefenseService();
    }

    @Test
    void testValidateAuthenticator_valid() {
        AuthenticatorJson auth = new AuthenticatorJson("client1", Instant.now().getEpochSecond(), "nonce123");
        assertDoesNotThrow(() -> service.validateAuthenticator(auth));
    }

    @Test
    void testValidateAuthenticator_timeSkewExceeded() {
        // 6 minutes ago (exceeds 5 minutes limit)
        long oldTimestamp = Instant.now().getEpochSecond() - 360;
        AuthenticatorJson auth = new AuthenticatorJson("client1", oldTimestamp, "nonce123");
        
        assertThrows(ReplayAttackException.class, () -> service.validateAuthenticator(auth));
    }

    @Test
    void testValidateAuthenticator_replayNonce() throws Exception {
        AuthenticatorJson auth1 = new AuthenticatorJson("client1", Instant.now().getEpochSecond(), "nonce123");
        service.validateAuthenticator(auth1);
        
        // Second time with same nonce
        AuthenticatorJson auth2 = new AuthenticatorJson("client1", Instant.now().getEpochSecond(), "nonce123");
        assertThrows(ReplayAttackException.class, () -> service.validateAuthenticator(auth2));
    }
}
