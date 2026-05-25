package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DilithiumSignatureServiceTest {

    @Test
    void signsAndVerifiesTranscript() throws Exception {
        DilithiumSignatureService.DilithiumKeyPair pair =
                DilithiumSignatureService.generateKeyPair();
        byte[] transcript = "SecureChat transcript binding".getBytes(StandardCharsets.UTF_8);
        byte[] signature = DilithiumSignatureService.sign(pair.privateKey(), pair.publicKey(), transcript);

        assertTrue(DilithiumSignatureService.verify(pair.publicKey(), transcript, signature));
        transcript[0] ^= 0x01;
        assertFalse(DilithiumSignatureService.verify(pair.publicKey(), transcript, signature));
        pair.destroyPrivateKey();
    }
}
