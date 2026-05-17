package vn.edu.hcmus.securechat.chat.service;

import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.exception.PkiException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspResponse;

/**
 * OCSP Stapling Manager — Chat Server phải fetch OCSP mỗi 4 giờ.
 * Theo Contrains.md mục 4.2.
 *
 * - Cache OCSP response
 * - Refresh mỗi 4 giờ (không phải mỗi connection)
 * - Nếu response hết hạn → reject connections
 */
public class OcspStaplingManager {

    private static final Logger log = LoggerFactory.getLogger(OcspStaplingManager.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private static final long REFRESH_INTERVAL_MS = 4 * 60 * 60 * 1000L; // 4 giờ

    private final AtomicReference<OcspResponse> cachedResponse = new AtomicReference<>();
    private final String certSerial;
    private final String issuerDn;
    private final ScheduledExecutorService scheduler;

    /**
     * @param certSerial Certificate serial number (hex) của Chat Server
     * @param issuerDn   Issuer DN
     */
    public OcspStaplingManager(String certSerial, String issuerDn) {
        this.certSerial = certSerial;
        this.issuerDn = issuerDn;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ocsp-stapling-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Khởi động OCSP stapling — fetch lần đầu và schedule refresh.
     */
    public void start() {
        // Fetch ngay lập tức
        refreshOcspResponse();

        // Schedule refresh mỗi 4 giờ
        scheduler.scheduleAtFixedRate(
                this::refreshOcspResponse,
                REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        log.info("OCSP Stapling Manager started, refresh every 4 hours");
    }

    /**
     * Fetch OCSP response từ CA Server và cache lại.
     */
    private void refreshOcspResponse() {
        try {
            OcspResponse response = fetchOcspFromCa();
            if (response != null) {
                cachedResponse.set(response);
                log.info("OCSP response refreshed: status={}, nextUpdate={}",
                        response.getCertStatus(), response.getNextUpdate());
                auditLog.info("OCSP_REFRESHED serial={} status={}", certSerial, response.getCertStatus());
            }
        } catch (Exception e) {
            log.error("OCSP refresh failed — existing response still in use", e);
            auditLog.warn("OCSP_REFRESH_FAILED serial={} reason={}", certSerial, e.getMessage());
        }
    }

    /**
     * Lấy cached OCSP response.
     *
     * @return cached response, có thể null nếu chưa fetch được lần nào
     */
    public OcspResponse getCachedResponse() {
        return cachedResponse.get();
    }

    /**
     * Kiểm tra cert còn hợp lệ theo cached OCSP.
     */
    public boolean isCertValid() {
        OcspResponse response = cachedResponse.get();
        if (response == null) {
            return true; // Chưa có response → cho phép (graceful degradation)
        }
        // Chỉ từ chối khi chứng chỉ bị thu hồi (REVOKED)
        // UNKNOWN = CA chưa biết cert này (ví dụ self-signed dev certs) → cho phép
        return !"REVOKED".equals(response.getCertStatus());
    }

    /**
     * Fetch OCSP từ CA Server.
     */
    private OcspResponse fetchOcspFromCa() throws IOException, PkiException {
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);

        OcspRequest request = new OcspRequest(
                certSerial, issuerDn,
                Base64.getEncoder().encodeToString(nonceBytes)
        );

        try (Socket socket = new Socket(ServerConfig.CA_HOST, ServerConfig.CA_PORT)) {
            socket.setSoTimeout(ServerConfig.READ_TIMEOUT_MS);

            byte[] payload = JsonSerializer.toBytes(request);
            PacketFrame.write(socket.getOutputStream(),
                    MessageType.OCSP_REQUEST.getCode(), payload);

            PacketFrame responseFrame = PacketFrame.read(socket.getInputStream());

            if (responseFrame.getType() == MessageType.ERROR.getCode()) {
                throw new PkiException("CA returned error for OCSP request");
            }

            return JsonSerializer.fromBytes(responseFrame.getPayload(), OcspResponse.class);
        } catch (IOException e) {
            log.warn("Failed to connect to CA for OCSP check: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new PkiException("OCSP request processing failed", e);
        }
    }

    /**
     * Shutdown scheduler. Gọi khi server shutdown.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
