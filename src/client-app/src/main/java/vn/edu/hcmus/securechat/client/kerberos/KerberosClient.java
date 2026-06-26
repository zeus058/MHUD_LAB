package vn.edu.hcmus.securechat.client.kerberos;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.client.network.NtpTimeClient;
import vn.edu.hcmus.securechat.client.network.SocketClient;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.StRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.HybridEncryption;
import vn.edu.hcmus.securechat.client.crypto.PkiManager;
import vn.edu.hcmus.securechat.common.config.ServerConfig;
import java.nio.charset.StandardCharsets;

/**
 * Client executes the Kerberos V5 flow.
 */
public class KerberosClient {
    private static final Logger log = LoggerFactory.getLogger(KerberosClient.class);

    private String freshTgtUsername;
    private String freshTgtBase64;
    private String freshTgtId;
    private byte[] freshKaTgs;

    /**
     * Requests a TGT from the Authentication Server using PKINIT (X.509 + RSA).
     */
    public void requestTgt(String username, char[] password) throws Exception {
        log.info("Requesting TGT from Authentication Server for user={}", username);
        try {
            // 1. Load KeyStore
            PkiManager.loadKeyStore(username, password);
        } catch (Exception e) {
            throw new Exception(
                    "User certificate was not found or the password is incorrect. Please register first or check your credentials.",
                    e);
        }

        try {
            // 2. Create TgtRequest
            TgtRequest req = new TgtRequest();
            req.setClientId(username);
            req.setTargetTgs(ServerConfig.TGS_HOST);
            String nonce = randomNonceBase64();
            req.setNonce(nonce);

            byte[] certDer = PkiManager.getCertificate().getEncoded();
            req.setCert(Base64.getEncoder().encodeToString(certDer));

            long timestamp = NtpTimeClient.getCurrentNetworkTime() / 1000L;
            req.setTimestamp(timestamp);

            // Sign Proof-of-Possession (PoP) for TGT
            String dataToSign = username + "|" + ServerConfig.TGS_HOST + "|" + nonce + "|" + timestamp;
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(PkiManager.getPrivateKey());
            sig.update(dataToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            req.setSignature(Base64.getEncoder().encodeToString(sig.sign()));

            // 3. Send to AS Server
            PacketFrame frame = new PacketFrame(PacketFrame.TYPE_TGT_REQUEST, (byte) 1, (short) 0,
                    JsonSerializer.toBytes(req));
            PacketFrame response = SocketClient.sendRequest(ServerConfig.AS_HOST, ServerConfig.AS_PORT, frame);

            if (response.getType() == PacketFrame.TYPE_TGT_RESPONSE) {
                log.info("Received TGT_RESPONSE. Decrypting Hybrid payload...");
                TgtResponse tgtResp = JsonSerializer.fromBytes(response.getPayload(), TgtResponse.class);

                // 4. Decrypt inner response with the client private key
                byte[] cipherBytes = Base64.getDecoder().decode(tgtResp.getResponse());
                byte[] innerBytes = HybridEncryption.decrypt(PkiManager.getPrivateKey(), cipherBytes);
                TgtResponseInner inner = JsonSerializer.fromBytes(innerBytes, TgtResponseInner.class);

                if (!nonce.equals(inner.getNonce())) {
                    throw new Exception("Nonce in TGT_RESPONSE does not match. Please try again.");
                }

                byte[] sessionKeyBytes = Base64.getDecoder().decode(inner.getSessionKey());
                try {
                    rememberFreshTgt(username, tgtResp.getTgt(), sessionKeyBytes, inner.getTgtId());
                } finally {
                    Arrays.fill(sessionKeyBytes, (byte) 0);
                }

                // 5. Save the ticket and K_A_TGS to disk
                String cacheData = tgtResp.getTgt() + "|||" + inner.getSessionKey()
                        + "|||" + nullToEmpty(inner.getTgtId());
                byte[] cacheBytes = cacheData.getBytes(StandardCharsets.UTF_8);
                try {
                    TicketCache.saveTicket(username, "TGT", cacheBytes, password);
                } finally {
                    Arrays.fill(cacheBytes, (byte) 0);
                }

                log.info("Kerberos AS authentication succeeded. Saved TGT and session key K_A_TGS.");
                return;
            } else if (response.getType() == PacketFrame.TYPE_ERROR) {
                ErrorResponse errorResp = JsonSerializer.fromBytes(response.getPayload(), ErrorResponse.class);
                log.error("Server error: code={}, message={}", errorResp.getErrorCode(), errorResp.getMessage());
                throw new Exception("Sign-in failed: " + errorResp.getMessage());
            } else {
                throw new Exception("Server returned an invalid packet instead of TGT_RESPONSE.");
            }
        } catch (Exception e) {
            log.error("Failed to request TGT", e);
            throw new Exception("Sign-in failed while requesting TGT: " + e.getMessage(), e);
        }
    }

    /**
     * Requests a Service Ticket (ST) from the Ticket Granting Server.
     */
    public void requestSt(String username, char[] password, String targetService) throws Exception {
        log.info("Requesting ST for service={}", targetService);
        byte[] cachedData = null;
        byte[] kaTgsBytes = null;
        byte[] authBytes = null;
        byte[] encryptedAuth = null;
        byte[] reqBytes = null;
        byte[] cipherBytes = null;
        byte[] innerBytes = null;
        try {
            // 1. Load TGT and K_A_TGS from cache
            String tgtBase64;
            String tgtId;
            if (hasFreshTgt(username)) {
                tgtBase64 = freshTgtBase64;
                tgtId = freshTgtId;
                kaTgsBytes = Arrays.copyOf(freshKaTgs, freshKaTgs.length);
                log.info("Using the fresh in-memory TGT to request ST and avoid stale cache data.");
            } else {
                cachedData = TicketCache.getTicket(username, "TGT", password);
                if (cachedData == null) {
                    TicketCache.clearCache(username);
                    throw new Exception("Unable to read TGT from cache. Please sign in again.");
                }
                String cacheString = new String(cachedData, StandardCharsets.UTF_8);
                String[] parts = cacheString.split("\\|\\|\\|");
                if (parts.length < 2) {
                    TicketCache.clearCache(username);
                    throw new Exception("TGT cache data is corrupted. Please sign in again.");
                }
                tgtBase64 = parts[0];
                String kaTgsBase64 = parts[1];
                tgtId = parts.length >= 3 ? parts[2] : "";
                kaTgsBytes = Base64.getDecoder().decode(kaTgsBase64);
            }

            // 2. Create Authenticator (timestamp uses seconds, matching
            // ReplayDefenseService)
            long timestamp = NtpTimeClient.getCurrentNetworkTime() / 1000L;
            String authNonce = randomNonceBase64();
            AuthenticatorJson auth = new AuthenticatorJson(
                    username,
                    timestamp,
                    authNonce,
                    tgtId,
                    targetService,
                    1L,
                    "");
            authBytes = JsonSerializer.toBytes(auth);
            encryptedAuth = AesGcmCipher.encrypt(kaTgsBytes, authBytes);
            String authBase64 = Base64.getEncoder().encodeToString(encryptedAuth);

            // 3. Create ST_REQUEST
            StRequest req = new StRequest(tgtBase64, authBase64, targetService);

            // Sign Proof-of-Possession (PoP) for ST
            String dataToSignSt = tgtBase64 + "|" + authBase64 + "|" + targetService;
            java.security.Signature sigSt = java.security.Signature.getInstance("SHA256withRSA");
            sigSt.initSign(PkiManager.getPrivateKey());
            sigSt.update(dataToSignSt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            req.setSignature(Base64.getEncoder().encodeToString(sigSt.sign()));

            reqBytes = JsonSerializer.toBytes(req);
            PacketFrame frame = new PacketFrame(PacketFrame.TYPE_ST_REQUEST, (byte) 1, (short) 0, reqBytes);

            log.info("Sending ST_REQUEST to TGS...");
            PacketFrame response = SocketClient.sendRequest(ServerConfig.TGS_HOST, ServerConfig.TGS_PORT, frame);

            if (response.getType() == PacketFrame.TYPE_ST_RESPONSE) {
                log.info("Received ST_RESPONSE. Decrypting...");
                StResponse stResp = JsonSerializer.fromBytes(response.getPayload(), StResponse.class);

                // Decrypt inner response
                cipherBytes = Base64.getDecoder().decode(stResp.getResponse());
                innerBytes = AesGcmCipher.decrypt(kaTgsBytes, cipherBytes);
                StResponseInner inner = JsonSerializer.fromBytes(innerBytes, StResponseInner.class);

                if (!authNonce.equals(inner.getNonce())) {
                    throw new Exception("Nonce in ST_RESPONSE does not match.");
                }

                // Save ST and K_A_Chat
                String stCacheData = stResp.getSt() + "|||" + inner.getSessionKey()
                        + "|||" + nullToEmpty(inner.getStId());
                byte[] stCacheBytes = stCacheData.getBytes(StandardCharsets.UTF_8);
                try {
                    TicketCache.saveTicket(username, "ST_" + targetService, stCacheBytes, password);
                } finally {
                    Arrays.fill(stCacheBytes, (byte) 0);
                }
                log.info("Kerberos TGS authentication succeeded. Saved ST and session key K_A_Chat.");
                clearFreshTgt();
                return;
            } else if (response.getType() == PacketFrame.TYPE_ERROR) {
                ErrorResponse errorResp = JsonSerializer.fromBytes(response.getPayload(), ErrorResponse.class);
                log.error("Server error: code={}, message={}", errorResp.getErrorCode(), errorResp.getMessage());
                if ("INVALID_TICKET".equals(errorResp.getErrorCode()) || "CRYPTO_ERROR".equals(errorResp.getErrorCode())) {
                    TicketCache.clearCache(username);
                }
                throw new Exception("Sign-in failed: " + errorResp.getMessage());
            } else {
                throw new Exception("Server returned an invalid packet instead of ST_RESPONSE.");
            }
        } catch (Exception e) {
            log.error("Failed to request ST", e);
            throw new Exception("Sign-in failed while requesting ST: " + e.getMessage(), e);
        } finally {
            if (cachedData != null) Arrays.fill(cachedData, (byte) 0);
            if (kaTgsBytes != null) Arrays.fill(kaTgsBytes, (byte) 0);
            if (authBytes != null) Arrays.fill(authBytes, (byte) 0);
            if (encryptedAuth != null) Arrays.fill(encryptedAuth, (byte) 0);
            if (reqBytes != null) Arrays.fill(reqBytes, (byte) 0);
            if (cipherBytes != null) Arrays.fill(cipherBytes, (byte) 0);
            if (innerBytes != null) Arrays.fill(innerBytes, (byte) 0);
        }
    }

    private static String randomNonceBase64() {
        byte[] nonce = new byte[CryptoConstants.NONCE_SIZE_BYTES];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void rememberFreshTgt(String username, String tgtBase64, byte[] kaTgsBytes, String tgtId) {
        clearFreshTgt();
        freshTgtUsername = username;
        freshTgtBase64 = tgtBase64;
        freshTgtId = nullToEmpty(tgtId);
        freshKaTgs = Arrays.copyOf(kaTgsBytes, kaTgsBytes.length);
    }

    private boolean hasFreshTgt(String username) {
        return username != null
                && username.equals(freshTgtUsername)
                && freshTgtBase64 != null
                && freshKaTgs != null;
    }

    private void clearFreshTgt() {
        if (freshKaTgs != null) {
            Arrays.fill(freshKaTgs, (byte) 0);
        }
        freshTgtUsername = null;
        freshTgtBase64 = null;
        freshTgtId = null;
        freshKaTgs = null;
    }
}
