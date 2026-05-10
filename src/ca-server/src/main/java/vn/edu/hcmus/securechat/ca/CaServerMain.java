package vn.edu.hcmus.securechat.ca;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;

/**
 * CA Server — Máy chủ PKI cấp phát và thu hồi chứng chỉ X.509 v3.
 * Owner: Chị Bee | Reviewer: Trúc Ngọc
 *
 * Chức năng cần implement:
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

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("CA Server is READY — listening on port {}", port);
            auditLog.info("CA_SERVER_STARTED port={}", port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                log.info("New connection from {}", clientSocket.getRemoteSocketAddress());
                threadPool.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            log.error("CA Server error", e);
        } finally {
            threadPool.shutdown();
            log.info("CA Server stopped.");
        }
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
                    // TODO: Gửi ErrorResponse
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
    // TODO: Chị Bee — Implement các hàm bên dưới
    // ====================================================================

    private void handleCsrRequest(PacketFrame frame, Socket socket) {
        // TODO: 1. Deserialize CSR từ frame.getPayload() bằng JsonSerializer
        //       2. Validate CSR
        //       3. Cấp X.509 v3 Certificate (dùng BouncyCastle X509v3CertificateBuilder)
        //       4. Ghi audit log: auditLog.info("CERT_ISSUED ...")
        //       5. Gửi response TYPE_CERT_RESPONSE qua PacketFrame.write()
        log.warn("handleCsrRequest — NOT YET IMPLEMENTED");
    }

    private void handleOcspRequest(PacketFrame frame, Socket socket) {
        // TODO: 1. Deserialize OCSP request
        //       2. Kiểm tra trạng thái cert (valid/revoked/unknown)
        //       3. Ký OCSP response bằng CA key
        //       4. Gửi response TYPE_OCSP_RESPONSE
        log.warn("handleOcspRequest — NOT YET IMPLEMENTED");
    }

    public void stop() {
        running = false;
    }

    public static void main(String[] args) {
        new CaServerMain().start();
    }
}
