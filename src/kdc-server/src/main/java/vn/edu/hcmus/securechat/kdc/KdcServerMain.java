package vn.edu.hcmus.securechat.kdc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;

/**
 * KDC Server — Authentication Server (AS) + Ticket Granting Server (TGS).
 * Owner: Gia Hiển | Reviewer: Phú Thọ
 *
 * Kiến trúc: 2 ServerSocket chạy song song
 * - AS trên port AS_PORT (8881) — xử lý TGT_REQUEST
 * - TGS trên port TGS_PORT (8882) — xử lý ST_REQUEST
 */
public class KdcServerMain {

    private static final Logger log = LoggerFactory.getLogger(KdcServerMain.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final ExecutorService threadPool;
    private final ReplayDefenseService replayDefense;
    private volatile boolean running = false;

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
            log.error("{} server error", name, e);
        }
    }

    private void handleClient(String serverName, Socket socket) {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        try {
            PacketFrame frame = PacketFrame.read(socket.getInputStream());
            MessageType type = MessageType.fromByte(frame.getType());

            log.info("[{}] Received {} from {}", serverName, type, clientAddr);

            switch (type) {
                case TGT_REQUEST -> handleTgtRequest(frame, socket, clientAddr);
                case ST_REQUEST  -> handleStRequest(frame, socket, clientAddr);
                default -> {
                    log.warn("[{}] Unexpected message type {} from {}", serverName, type, clientAddr);
                }
            }
        } catch (FramingException e) {
            log.error("[{}] Framing error from {}", serverName, clientAddr, e);
        } catch (IOException e) {
            log.error("[{}] IO error from {}", serverName, clientAddr, e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ====================================================================
    // TODO: Gia Hiển — Implement các hàm bên dưới
    // ====================================================================

    private void handleTgtRequest(PacketFrame frame, Socket socket, String clientAddr) {
        // TODO: 1. Deserialize TgtRequest từ payload bằng JsonSerializer
        //       2. Verify client certificate (chain validation + OCSP check)
        //       3. Sinh session key K_A_TGS (32 bytes, SecureRandom)
        //       4. Tạo TgtInner, encrypt bằng PU_TGS (Hybrid Encrypt)
        //       5. Tạo response inner, encrypt bằng PU_client
        //       6. Gửi TgtResponse qua PacketFrame.write(TYPE_TGT_RESPONSE)
        //       7. Audit log: auditLog.info("TGT_ISSUED clientId={} ip={} ...", ...)
        log.warn("handleTgtRequest — NOT YET IMPLEMENTED");
    }

    private void handleStRequest(PacketFrame frame, Socket socket, String clientAddr) {
        // TODO: 1. Decrypt TGT bằng TGS private key
        //       2. Validate Authenticator (dùng replayDefense.validateAuthenticator())
        //       3. Kiểm tra Control Vector (ControlVector.validateForChatService())
        //       4. Sinh session key K_A_Chat
        //       5. Tạo StInner, encrypt bằng PU_ChatServer
        //       6. Gửi ST_RESPONSE
        //       7. Audit log: auditLog.info("ST_ISSUED clientId={} target={} ...")
        log.warn("handleStRequest — NOT YET IMPLEMENTED");
    }

    public void stop() {
        running = false;
        replayDefense.shutdown();
    }

    public static void main(String[] args) {
        new KdcServerMain().start();
    }
}
