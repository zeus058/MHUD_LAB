package vn.edu.hcmus.securechat.kdc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.exception.CertificateRevokedException;
import vn.edu.hcmus.securechat.common.exception.ChainValidationException;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.exception.InvalidTicketException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponse;
import vn.edu.hcmus.securechat.kdc.crypto.KdcKeyManager;
import vn.edu.hcmus.securechat.kdc.service.AuthenticationService;
import vn.edu.hcmus.securechat.kdc.service.OcspClient;
import vn.edu.hcmus.securechat.kdc.service.TicketGrantingService;

/**
 * KDC Server — Authentication Server (AS) + Ticket Granting Server (TGS).
 * Owner: Gia Hiển | Reviewer: Phú Thọ
 *
 * Kiến trúc: 2 ServerSocket chạy song song
 * - AS trên port AS_PORT (8881) — xử lý TGT_REQUEST
 * - TGS trên port TGS_PORT (8882) — xử lý ST_REQUEST
 *
 * Theo Contrains.md mục 5:
 * - Port cố định từ ServerConfig
 * - ReplayDefenseService kiểm tra Timestamp + Nonce
 * - OCSP check trước khi cấp TGT
 * - Hybrid Encryption cho vé (RSA bọc AES)
 * - Audit log cho mọi sự kiện bảo mật
 */
public class KdcServerMain {

    private static final Logger log = LoggerFactory.getLogger(KdcServerMain.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final ExecutorService threadPool;
    private final ReplayDefenseService replayDefense;
    private volatile boolean running = false;

    // Core services — khởi tạo trong start()
    private KdcKeyManager keyManager;
    private AuthenticationService authService;
    private TicketGrantingService tgsService;

    public KdcServerMain() {
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "kdc-handler");
            t.setDaemon(true);
            return t;
        });
        this.replayDefense = new ReplayDefenseService();
    }

    public void start() {
        running = true;
        log.info("========================================");
        log.info("  KDC Server starting...");
        log.info("  AS  port: {}", ServerConfig.AS_PORT);
        log.info("  TGS port: {}", ServerConfig.TGS_PORT);
        log.info("========================================");

        try {
            // Đăng ký BouncyCastle provider
            java.security.Security.addProvider(new BouncyCastleProvider());

            // Khởi tạo key manager (load keys từ Windows DPAPI)
            keyManager = new KdcKeyManager();

            // Khởi tạo Database và Storage
            vn.edu.hcmus.securechat.common.db.DatabaseManager db = new vn.edu.hcmus.securechat.common.db.DatabaseManager(
                    "data/kdc-server.db");
            vn.edu.hcmus.securechat.kdc.storage.KdcStorage storage = new vn.edu.hcmus.securechat.kdc.storage.KdcStorage(
                    db);
            storage.initializeTables();

            // Khởi tạo services
            OcspClient ocspClient = new OcspClient();
            authService = new AuthenticationService(keyManager, ocspClient, storage);
            tgsService = new TicketGrantingService(keyManager, replayDefense, storage);

            log.info("KDC services initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize KDC services", e);
            auditLog.error("KDC_INIT_FAILED error={}", e.getMessage());
            stop();
            return;
        }

        // Chạy AS và TGS trên 2 thread riêng
        Thread asThread = new Thread(() -> runServer("AS", ServerConfig.AS_PORT), "as-acceptor");
        Thread tgsThread = new Thread(() -> runServer("TGS", ServerConfig.TGS_PORT), "tgs-acceptor");

        asThread.start();
        tgsThread.start();

        auditLog.info("KDC_SERVER_STARTED as_port={} tgs_port={}", ServerConfig.AS_PORT, ServerConfig.TGS_PORT);

        try {
            asThread.join();
            tgsThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("KDC Server interrupted");
        }
    }

    private void runServer(String name, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("{} is READY — listening on port {}", name, port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                log.info("[{}] New connection from {}", name, clientSocket.getRemoteSocketAddress());
                threadPool.submit(() -> handleClient(name, clientSocket));
            }
        } catch (IOException e) {
            if (running) {
                log.error("{} server error", name, e);
            }
        }
    }

    private void handleClient(String serverName, Socket socket) {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        try {
            socket.setSoTimeout(ServerConfig.READ_TIMEOUT_MS);

            PacketFrame frame = PacketFrame.read(socket.getInputStream());
            MessageType type = MessageType.fromByte(frame.getType());

            log.info("[{}] Received {} from {}", serverName, type, clientAddr);

            switch (type) {
                case TGT_REQUEST -> handleTgtRequest(frame, socket, clientAddr);
                case ST_REQUEST -> handleStRequest(frame, socket, clientAddr);
                default -> {
                    log.warn("[{}] Unexpected message type {} from {}", serverName, type, clientAddr);
                    sendErrorSafe(socket, "INVALID_MESSAGE_TYPE",
                            "Unexpected message type: " + type);
                }
            }
        } catch (FramingException e) {
            log.error("[{}] Framing error from {}", serverName, clientAddr, e);
        } catch (IOException e) {
            log.error("[{}] IO error from {}", serverName, clientAddr, e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ====================================================================
    // TGT Request Handler — Authentication Server (AS)
    // ====================================================================

    private void handleTgtRequest(PacketFrame frame, Socket socket, String clientAddr) {
        try {
            // 1. Deserialize TgtRequest
            TgtRequest request = JsonSerializer.fromBytes(
                    frame.getPayload(), TgtRequest.class);

            log.info("Processing TGT request: clientId={}, targetTgs={}",
                    request.getClientId(), request.getTargetTgs());

            // 2. Issue TGT via AuthenticationService
            TgtResponse response = authService.issueTgt(request, clientAddr);

            // 3. Serialize và gửi response
            byte[] responsePayload = JsonSerializer.toBytes(response);
            PacketFrame.write(socket.getOutputStream(),
                    MessageType.TGT_RESPONSE.getCode(), responsePayload);

            log.info("TGT response sent to client={}", request.getClientId());

        } catch (CertificateRevokedException e) {
            log.error("TGT rejected — certificate revoked: {}", clientAddr, e);
            auditLog.error("TGT_REJECTED ip={} reason=CERT_REVOKED detail={}",
                    clientAddr, e.getMessage());
            sendErrorSafe(socket, "CERT_REVOKED", "Certificate has been revoked");

        } catch (ChainValidationException e) {
            log.error("TGT rejected — chain validation failed: {}", clientAddr, e);
            auditLog.error("TGT_REJECTED ip={} reason=CHAIN_INVALID detail={}",
                    clientAddr, e.getMessage());
            sendErrorSafe(socket, "CHAIN_VALIDATION_FAILED", "Certificate chain invalid");

        } catch (CryptoException e) {
            log.error("TGT rejected — crypto error: {}", clientAddr, e);
            auditLog.error("TGT_REJECTED ip={} reason=CRYPTO_ERROR", clientAddr);
            sendErrorSafe(socket, "CRYPTO_ERROR", "Encryption/decryption error");

        } catch (ProtocolException e) {
            log.error("TGT rejected — protocol error: {}", clientAddr, e);
            auditLog.warn("TGT_REJECTED ip={} reason=PROTOCOL_ERROR detail={}",
                    clientAddr, e.getMessage());
            sendErrorSafe(socket, "PROTOCOL_ERROR", e.getMessage());

        } catch (Exception e) {
            log.error("TGT rejected — unexpected error: {}", clientAddr, e);
            auditLog.error("TGT_REJECTED ip={} reason=INTERNAL_ERROR", clientAddr);
            sendErrorSafe(socket, "INTERNAL_ERROR", "Internal server error");
        }
    }

    // ====================================================================
    // ST Request Handler — Ticket Granting Server (TGS)
    // ====================================================================

    private void handleStRequest(PacketFrame frame, Socket socket, String clientAddr) {
        try {
            // 1. Deserialize StRequest
            StRequest request = JsonSerializer.fromBytes(
                    frame.getPayload(), StRequest.class);

            log.info("Processing ST request: targetServer={}", request.getTargetServer());

            // 2. Issue ST via TicketGrantingService
            StResponse response = tgsService.issueServiceTicket(request, clientAddr);

            // 3. Serialize và gửi response
            byte[] responsePayload = JsonSerializer.toBytes(response);
            PacketFrame.write(socket.getOutputStream(),
                    MessageType.ST_RESPONSE.getCode(), responsePayload);

            log.info("ST response sent for targetServer={}", request.getTargetServer());

        } catch (ReplayAttackException e) {
            log.error("ST rejected — replay attack detected: {}", clientAddr, e);
            auditLog.warn("ST_REJECTED ip={} reason=REPLAY_ATTACK detail={}",
                    clientAddr, e.getMessage());
            sendErrorSafe(socket, "REPLAY_ATTACK", "Replay attack detected");

        } catch (InvalidTicketException e) {
            log.error("ST rejected — invalid ticket: {}", clientAddr, e);
            auditLog.warn("ST_REJECTED ip={} reason=INVALID_TICKET detail={}",
                    clientAddr, e.getMessage());
            sendErrorSafe(socket, "INVALID_TICKET", "TGT is invalid or expired");

        } catch (CryptoException e) {
            log.error("ST rejected — crypto error: {}", clientAddr, e);
            auditLog.error("ST_REJECTED ip={} reason=CRYPTO_ERROR", clientAddr);
            sendErrorSafe(socket, "CRYPTO_ERROR", "Encryption/decryption error");

        } catch (ProtocolException e) {
            log.error("ST rejected — protocol error: {}", clientAddr, e);
            auditLog.warn("ST_REJECTED ip={} reason=PROTOCOL_ERROR detail={}",
                    clientAddr, e.getMessage());
            sendErrorSafe(socket, "PROTOCOL_ERROR", e.getMessage());

        } catch (Exception e) {
            log.error("ST rejected — unexpected error: {}", clientAddr, e);
            auditLog.error("ST_REJECTED ip={} reason=INTERNAL_ERROR", clientAddr);
            sendErrorSafe(socket, "INTERNAL_ERROR", "Internal server error");
        }
    }

    // ====================================================================
    // Helper methods
    // ====================================================================

    /**
     * Gửi error response — không throw exception nếu gửi fail.
     */
    private void sendErrorSafe(Socket socket, String errorCode, String message) {
        try {
            sendErrorResponse(socket, errorCode, message);
        } catch (Exception e) {
            log.error("Failed to send error response", e);
        }
    }

    /**
     * Gửi error response về client — generic message, KHÔNG leak chi tiết.
     */
    private void sendErrorResponse(Socket socket, String errorCode, String errorMessage)
            throws IOException, ProtocolException {
        ErrorResponse error = ErrorResponse.of(errorCode, errorMessage);
        byte[] payload = JsonSerializer.toBytes(error);
        PacketFrame.write(socket.getOutputStream(), MessageType.ERROR.getCode(), payload);
    }

    public void stop() {
        running = false;
        replayDefense.shutdown();
        threadPool.shutdown();
    }

    public static void main(String[] args) {
        new KdcServerMain().start();
    }
}
