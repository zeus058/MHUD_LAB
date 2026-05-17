package vn.edu.hcmus.securechat.kdc.service;

import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.exception.CertificateRevokedException;
import vn.edu.hcmus.securechat.common.exception.PkiException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspResponse;

/**
 * OCSP Client — KDC gửi yêu cầu kiểm tra trạng thái chứng chỉ tới CA Server.
 *
 * Theo Contrains.md mục 4.2:
 * - Chat Server và KDC phải tự fetch OCSP response từ CA
 * - Nếu OCSP response hết hạn hoặc verify fail → ngắt kết nối
 */
public class OcspClient {

    private static final Logger log = LoggerFactory.getLogger(OcspClient.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final java.util.concurrent.ConcurrentHashMap<String, CachedOcspResponse> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedOcspResponse(OcspResponse response, long expiresAt) {}

    /**
     * Kiểm tra trạng thái chứng chỉ qua OCSP.
     *
     * @param certSerial Certificate serial number (hex string)
     * @param issuerDn   Issuer DN
     * @throws CertificateRevokedException nếu cert đã bị thu hồi
     * @throws PkiException                nếu OCSP request fail hoặc status UNKNOWN
     */
    public void verifyCertificateStatus(String certSerial, String issuerDn)
            throws CertificateRevokedException, PkiException {
        try {
            long now = System.currentTimeMillis();
            CachedOcspResponse cached = cache.get(certSerial);
            OcspResponse response;

            if (cached != null && cached.expiresAt() > now) {
                response = cached.response();
                log.debug("Using cached OCSP response for serial={}", certSerial);
            } else {
                // Sinh nonce chống replay
                byte[] nonceBytes = new byte[16];
                new SecureRandom().nextBytes(nonceBytes);
                String nonce = Base64.getEncoder().encodeToString(nonceBytes);

                // Tạo OCSP request
                OcspRequest request = new OcspRequest(certSerial, issuerDn, nonce);

                // Gửi tới CA Server (OCSP port)
                response = sendOcspRequest(request);

                // Lưu vào cache
                if (response != null) {
                    long expiresAt = response.getNextUpdate() > 0 ? response.getNextUpdate() : now + 4 * 60 * 60 * 1000L;
                    cache.put(certSerial, new CachedOcspResponse(response, expiresAt));
                }
            }

            // Kiểm tra trạng thái
            if (response == null) {
                log.warn("OCSP response is null for serial={}", certSerial);
                throw new PkiException("OCSP response is null");
            }

            String status = response.getCertStatus();
            log.info("OCSP check: serial={}, status={}", certSerial, status);

            switch (status) {
                case "GOOD" -> {
                    auditLog.info("OCSP_VERIFIED serial={} status=GOOD", certSerial);
                }
                case "REVOKED" -> {
                    auditLog.error("OCSP_REVOKED serial={} revokedAt={} reason={}",
                            certSerial, response.getRevokedAt(), response.getRevocationReason());
                    throw new CertificateRevokedException(
                            "Certificate " + certSerial + " has been revoked: "
                            + response.getRevocationReason());
                }
                default -> {
                    auditLog.warn("OCSP_UNKNOWN serial={}", certSerial);
                    throw new PkiException("Certificate status unknown: " + certSerial);
                }
            }

        } catch (PkiException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCSP verification failed for serial={}", certSerial, e);
            throw new PkiException("OCSP verification error: " + e.getMessage(), e);
        }
    }

    /**
     * Gửi OCSP request tới CA Server và nhận response.
     */
    private OcspResponse sendOcspRequest(OcspRequest request)
            throws IOException, PkiException {
        try (Socket socket = new Socket(ServerConfig.CA_HOST, ServerConfig.CA_PORT)) {
            socket.setSoTimeout(ServerConfig.READ_TIMEOUT_MS);

            // Gửi request
            byte[] payload = JsonSerializer.toBytes(request);
            PacketFrame.write(socket.getOutputStream(),
                    MessageType.OCSP_REQUEST.getCode(), payload);

            // Nhận response
            PacketFrame responseFrame = PacketFrame.read(socket.getInputStream());

            if (responseFrame.getType() == MessageType.ERROR.getCode()) {
                throw new PkiException("CA returned error for OCSP request");
            }

            return JsonSerializer.fromBytes(responseFrame.getPayload(), OcspResponse.class);

        } catch (IOException e) {
            log.error("Failed to connect to CA for OCSP check", e);
            throw e;
        } catch (Exception e) {
            throw new PkiException("OCSP request processing failed", e);
        }
    }
}
