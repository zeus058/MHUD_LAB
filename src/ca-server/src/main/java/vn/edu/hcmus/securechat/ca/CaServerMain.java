package vn.edu.hcmus.securechat.ca;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.ca.service.CertificateAuthority;
import vn.edu.hcmus.securechat.ca.service.OcspResponder;
import vn.edu.hcmus.securechat.ca.storage.CertificateStorage;
import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.db.DatabaseManager;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateSigningRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspResponse;

/**
 * CA Server — Máy chủ PKI cấp phát và thu hồi chứng chỉ X.509 v3.
 * Owner: Chị Bee | Reviewer: Trúc Ngọc
 *
 * Chức năng:
 * - Xử lý CSR Request (TYPE_CSR_REQUEST) → cấp Certificate
 * - OCSP Responder (TYPE_OCSP_REQUEST) → trả OCSP status
 * - Certificate chain validation
 * - Certificate revocation
 */
public class CaServerMain {

    private static final Logger log = LoggerFactory.getLogger(CaServerMain.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final int port;
    private final ExecutorService threadPool;
    private volatile boolean running = false;

    // Core services
    private DatabaseManager databaseManager;
    private CertificateStorage certStorage;
    private CertificateAuthority ca;
    private OcspResponder ocspResponder;
    private com.sun.net.httpserver.HttpServer adminHttpServer;

    public CaServerMain() {
        this.port = ServerConfig.CA_PORT;
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ca-handler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        log.info("========================================");
        log.info("  CA Server starting on port {}...", port);
        log.info("========================================");

        try {
            // Khởi tạo database
            databaseManager = new DatabaseManager("data/ca-server.db");
            databaseManager.connect();

            // Khởi tạo storage
            certStorage = new CertificateStorage(databaseManager);
            certStorage.initializeTables();

            // Khởi tạo Certificate Authority
            ca = new CertificateAuthority();

            // Khởi tạo OCSP Responder
            // Dùng CA's private key và certificate làm OCSP signer
            ocspResponder = new OcspResponder(
                certStorage,
                ca.getCaPrivateKey(),
                ca.getCaCertificate()
            );

            // Khởi tạo Admin HTTP API
            startAdminApi();

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("CA Server is READY — listening on port {}", port);
                auditLog.info("CA_SERVER_STARTED port={}", port);

                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    log.info("New connection from {}", clientSocket.getRemoteSocketAddress());
                    threadPool.submit(() -> handleClient(clientSocket));
                }
            }
        } catch (Exception e) {
            log.error("CA Server error during startup", e);
            auditLog.error("CA_SERVER_ERROR error={}", e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void startAdminApi() throws IOException {
        adminHttpServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(8080), 0);
        adminHttpServer.createContext("/admin/revoke", (com.sun.net.httpserver.HttpExchange exchange) -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String query = exchange.getRequestURI().getQuery();
                    if (query == null) throw new IllegalArgumentException("Missing query parameters");
                    
                    String serial = null;
                    String reason = "unspecified";
                    
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1) {
                            if ("serial".equals(pair[0])) serial = pair[1];
                            if ("reason".equals(pair[0])) reason = pair[1];
                        }
                    }

                    if (serial == null || serial.isEmpty()) {
                        sendHttpError(exchange, 400, "Missing serial parameter");
                        return;
                    }

                    certStorage.revokeCertificate(serial, reason);
                    log.info("Admin revoked certificate: serial={}, reason={}", serial, reason);
                    String response = "Certificate revoked successfully";
                    exchange.sendResponseHeaders(200, response.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    log.error("Error revoking certificate via API", e);
                    sendHttpError(exchange, 500, e.getMessage());
                }
            } else {
                sendHttpError(exchange, 405, "Method Not Allowed");
            }
        });
        adminHttpServer.setExecutor(threadPool);
        adminHttpServer.start();
        log.info("Admin API started on port 8080");
    }

    private void sendHttpError(com.sun.net.httpserver.HttpExchange exchange, int code, String message) throws IOException {
        exchange.sendResponseHeaders(code, message.length());
        java.io.OutputStream os = exchange.getResponseBody();
        os.write(message.getBytes());
        os.close();
    }

    private void handleClient(Socket socket) {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        try {
            PacketFrame frame = PacketFrame.read(socket.getInputStream());
            MessageType type = MessageType.fromByte(frame.getType());

            log.info("Received {} from {}", type, clientAddr);

            switch (type) {
                case CSR_REQUEST -> handleCsrRequest(frame, socket);
                case OCSP_REQUEST -> handleOcspRequest(frame, socket);
                default -> {
                    log.warn("Unexpected message type {} from {}", type, clientAddr);
                    try {
                        sendErrorResponse(socket, "INVALID_MESSAGE_TYPE", "Unexpected message type: " + type);
                    } catch (IOException | ProtocolException ignored) {}
                }
            }
        } catch (FramingException e) {
            log.error("Framing error from {}", clientAddr, e);
        } catch (IOException e) {
            log.error("IO error handling client {}", clientAddr, e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ====================================================================
    // CSR Request Handler — Cấp chứng chỉ X.509 v3
    // ====================================================================

    private void handleCsrRequest(PacketFrame frame, Socket socket) {
        try {
            log.info("Processing CSR Request");

            // 1. Deserialize CSR từ frame payload
            CertificateSigningRequest csr = JsonSerializer.fromBytes(
                frame.getPayload(),
                CertificateSigningRequest.class
            );

            // 2. Validate CSR
            validateCsr(csr);

            // 3. Decode public key từ base64
            byte[] pubKeyDer = Base64.getDecoder().decode(csr.getPublicKey());
            PublicKey subjectPublicKey = decodePublicKey(pubKeyDer);

            // 4. Cấp chứng chỉ
            byte[] certificateDer = ca.issueCertificate(csr.getSubjectDn(), subjectPublicKey);
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) 
                java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(
                    new java.io.ByteArrayInputStream(certificateDer));

            // 5. Tính SHA-256 fingerprint
            String fingerprint = calculateFingerprint(certificateDer);

            // 6. Lưu vào database
            String serial = cert.getSerialNumber().toString(16);
            long now = System.currentTimeMillis();
            long notAfter = cert.getNotAfter().getTime();

            certStorage.saveCertificate(
                serial,
                csr.getSubjectDn(),
                ca.getCaCertificate().getSubjectX500Principal().getName(),
                certificateDer,
                cert.getNotBefore().getTime(),
                notAfter,
                fingerprint
            );

            // 7. Tạo response
            byte[] caChainDer = encodeCertificateChain(ca.getCertificateChain());
            CertificateResponse response = new CertificateResponse(
                Base64.getEncoder().encodeToString(certificateDer),
                Base64.getEncoder().encodeToString(caChainDer),
                serial,
                now,
                notAfter,
                String.format("http://%s:%d/ocsp", ServerConfig.CA_HOST, ServerConfig.OCSP_PORT)
            );

            // 8. Gửi response
            byte[] payload = JsonSerializer.toBytes(response);
            PacketFrame.write(socket.getOutputStream(),
                MessageType.CERT_RESPONSE.getCode(), payload);

            log.info("CSR Request processed successfully: subject={}, serial={}", 
                csr.getSubjectDn(), serial);
            auditLog.info("CSR_REQUEST_PROCESSED subject={} serial={}", 
                csr.getSubjectDn(), serial);

        } catch (IOException | ProtocolException e) {
            log.error("Error handling CSR Request", e);
            auditLog.error("CSR_REQUEST_FAILED error={}", e.getMessage());
            try {
                sendErrorResponse(socket, "CSR_PROCESSING_FAILED", e.getMessage());
            } catch (IOException | ProtocolException sendError) {
                log.error("Failed to send error response", sendError);
            }
        } catch (Exception e) {
            log.error("Error handling CSR Request", e);
            auditLog.error("CSR_REQUEST_FAILED error={}", e.getMessage());
            try {
                sendErrorResponse(socket, "CSR_PROCESSING_FAILED", e.getMessage());
            } catch (IOException | ProtocolException sendError) {
                log.error("Failed to send error response", sendError);
            }
        }
    }

    // ====================================================================
    // OCSP Request Handler — Kiểm tra trạng thái chứng chỉ
    // ====================================================================

    private void handleOcspRequest(PacketFrame frame, Socket socket) {
        try {
            log.info("Processing OCSP Request");

            // 1. Deserialize OCSP request
            OcspRequest ocspReq = JsonSerializer.fromBytes(
                frame.getPayload(),
                OcspRequest.class
            );

            // 2. Tạo OCSP response (bao gồm ký số)
            OcspResponse ocspResp = ocspResponder.respondToOcspRequest(ocspReq);

            // 3. Gửi response
            byte[] payload = JsonSerializer.toBytes(ocspResp);
            PacketFrame.write(socket.getOutputStream(),
                MessageType.OCSP_RESPONSE.getCode(), payload);

            log.info("OCSP Request processed: serial={}, status={}",
                ocspReq.getCertSerial(), ocspResp.getCertStatus());

        } catch (IOException | ProtocolException e) {
            log.error("Error handling OCSP Request", e);
            auditLog.error("OCSP_REQUEST_FAILED error={}", e.getMessage());
            try {
                sendErrorResponse(socket, "OCSP_PROCESSING_FAILED", e.getMessage());
            } catch (IOException | ProtocolException sendError) {
                log.error("Failed to send error response", sendError);
            }
        } catch (Exception e) {
            log.error("Error handling OCSP Request", e);
            auditLog.error("OCSP_REQUEST_FAILED error={}", e.getMessage());
            try {
                sendErrorResponse(socket, "OCSP_PROCESSING_FAILED", e.getMessage());
            } catch (IOException | ProtocolException sendError) {
                log.error("Failed to send error response", sendError);
            }
        }
    }

    // ====================================================================
    // Helper Methods
    // ====================================================================

    /**
     * Validate CSR (chứng chỉ phải có các trường bắt buộc).
     */
    private void validateCsr(CertificateSigningRequest csr) throws ProtocolException {
        if (csr.getSubjectDn() == null || csr.getSubjectDn().isEmpty()) {
            throw new ProtocolException("CSR missing subjectDn");
        }
        if (csr.getPublicKey() == null || csr.getPublicKey().isEmpty()) {
            throw new ProtocolException("CSR missing publicKey");
        }
        if (csr.getNonce() == null || csr.getNonce().isEmpty()) {
            throw new ProtocolException("CSR missing nonce");
        }
        if (csr.getSignature() == null || csr.getSignature().isEmpty()) {
            throw new ProtocolException("CSR missing signature");
        }

        // Verify signature trên CSR (RSA-PSS or SHA256withRSA)
        try {
            byte[] pubKeyDer = Base64.getDecoder().decode(csr.getPublicKey());
            PublicKey publicKey = decodePublicKey(pubKeyDer);

            String dataToVerify = csr.getSubjectDn() + "|" + csr.getPublicKey() + "|" + csr.getNonce();
            byte[] signatureBytes = Base64.getDecoder().decode(csr.getSignature());

            // Dùng SHA256withRSA cho đơn giản và tương thích rộng
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(dataToVerify.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            if (!sig.verify(signatureBytes)) {
                throw new ProtocolException("CSR signature verification failed");
            }
        } catch (Exception e) {
            throw new ProtocolException("CSR validation error: " + e.getMessage());
        }
    }

    /**
     * Decode public key từ DER bytes.
     */
    private PublicKey decodePublicKey(byte[] pubKeyDer) throws Exception {
        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(pubKeyDer);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    /**
     * Tính SHA-256 fingerprint của certificate.
     */
    private String calculateFingerprint(byte[] certificateDer) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(certificateDer);
        return bytesToHex(digest);
    }

    /**
     * Encode certificate chain thành DER bytes.
     */
    private byte[] encodeCertificateChain(java.security.cert.X509Certificate[] chain)
            throws java.security.cert.CertificateEncodingException, IOException {
        // Concatenate tất cả certificates
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        for (java.security.cert.X509Certificate cert : chain) {
            baos.write(cert.getEncoded());
        }
        return baos.toByteArray();
    }

    /**
     * Gửi error response về client.
     */
    private void sendErrorResponse(Socket socket, String errorCode, String errorMessage)
            throws IOException, ProtocolException {
        ErrorResponse error = new ErrorResponse(errorCode, errorMessage, System.currentTimeMillis());
        byte[] payload = JsonSerializer.toBytes(error);
        PacketFrame.write(socket.getOutputStream(),
            MessageType.ERROR.getCode(), payload);
    }

    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void stop() {
        running = false;
    }

    private void cleanup() {
        if (adminHttpServer != null) {
            adminHttpServer.stop(0);
        }
        threadPool.shutdown();
        if (databaseManager != null) {
            databaseManager.close();
        }
        log.info("CA Server stopped.");
    }

    public static void main(String[] args) {
        new CaServerMain().start();
    }
}
