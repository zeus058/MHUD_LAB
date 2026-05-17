package vn.edu.hcmus.securechat.chat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.protocol.dto.OcspResponse;

class OcspStaplingManagerTest {

    private OcspStaplingManager manager;

    @BeforeEach
    void setUp() {
        manager = new OcspStaplingManager("serial", "issuer");
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void testIsCertValid_noResponseCached_gracefulDegradation() {
        assertTrue(manager.isCertValid(), "Should allow if no response is cached");
    }

    @Test
    void testIsCertValid_goodStatus() throws Exception {
        setCachedResponse("GOOD");
        assertTrue(manager.isCertValid(), "Should be valid if status is GOOD");
    }

    @Test
    void testIsCertValid_revokedStatus() throws Exception {
        setCachedResponse("REVOKED");
        assertFalse(manager.isCertValid(), "Should be invalid if status is REVOKED");
    }

    @SuppressWarnings("unchecked")
    private void setCachedResponse(String status) throws Exception {
        OcspResponse response = new OcspResponse();
        response.setCertStatus(status);

        Field field = OcspStaplingManager.class.getDeclaredField("cachedResponse");
        field.setAccessible(true);
        AtomicReference<OcspResponse> cachedResponse = (AtomicReference<OcspResponse>) field.get(manager);
        cachedResponse.set(response);
    }
}
