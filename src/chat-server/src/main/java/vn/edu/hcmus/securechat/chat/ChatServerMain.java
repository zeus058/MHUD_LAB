package vn.edu.hcmus.securechat.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;

/**
 * Chat Server — Máy chủ định tuyến tin nhắn đã mã hóa E2EE.
 * Owner: Phú Thọ | Reviewer: Gia Hiển
 *
 * Chức năng cần implement:
 * - CHAT_HANDSHAKE: Verify ST, thực hiện ECDHE + Kyber handshake
 * - CHAT_MESSAGE: Định tuyến tin nhắn đã mã hóa giữa 2 client
 * - Session management: Quản lý các phiên chat active
 * - OCSP stapling: Fetch OCSP response từ CA mỗi 4 giờ
 */
public class ChatServerMain {

    private static final Logger log = LoggerFactory.getLogger(ChatServerMain.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final int port;
    private final ExecutorService threadPool;

    // Quản lý client sessions: clientId → Socket
    private final Map<String, Socket> activeSessions = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    public ChatServerMain() {
        this.port = ServerConfig.CHAT_PORT;
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "chat-handler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        log.info("========================================");
        log.info("  Chat Server starting on port {}...", port);
        log.info("========================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Chat Server is READY — listening on port {}", port);
            auditLog.info("CHAT_SERVER_STARTED port={}", port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                log.info("New connection from {}", clientSocket.getRemoteSocketAddress());
                threadPool.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            log.error("Chat Server error", e);
        } finally {
            threadPool.shutdown();
            log.info("Chat Server stopped.");
        }
    }

    private void handleClient(Socket socket) {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        try {
            // Đọc liên tục từ socket (long-lived connection)
            while (running && !socket.isClosed()) {
                PacketFrame frame = PacketFrame.read(socket.getInputStream());
                MessageType type = MessageType.fromByte(frame.getType());

                log.info("Received {} from {}", type, clientAddr);

                switch (type) {
                    case CHAT_HANDSHAKE -> handleHandshake(frame, socket, clientAddr);
                    case CHAT_MESSAGE   -> handleChatMessage(frame, socket, clientAddr);
                    default -> {
                        log.warn("Unexpected message type {} from {}", type, clientAddr);
                    }
                }
            }
        } catch (FramingException e) {
            log.error("Framing error from {}", clientAddr, e);
        } catch (IOException e) {
            log.debug("Client disconnected: {}", clientAddr);
        } finally {
            // Dọn dẹp session
            removeSession(socket);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ====================================================================
    // TODO: Phú Thọ — Implement các hàm bên dưới
    // ====================================================================

    private void handleHandshake(PacketFrame frame, Socket socket, String clientAddr) {
        // TODO: 1. Decrypt ST bằng Chat Server private key
        //       2. Validate Control Vector: ControlVector.validateForChatService(cv)
        //       3. Validate Authenticator: replayDefense.validateAuthenticator(auth)
        //       4. Thực hiện ECDHE key exchange
        //       5. Thực hiện Kyber ML-KEM-768 encapsulation
        //       6. Derive Master Session Key: HkdfKeyDerivation.deriveSessionKey(ssEcdhe, ssKyber, nonce)
        //       7. Lưu session vào activeSessions
        //       8. Audit log: auditLog.info("SESSION_ESTABLISHED clientId={} ip={}", ...)
        log.warn("handleHandshake — NOT YET IMPLEMENTED");
    }

    private void handleChatMessage(PacketFrame frame, Socket socket, String clientAddr) {
        // TODO: 1. Xác định session từ socket
        //       2. Decrypt message bằng session key
        //       3. Xác định recipient từ message content
        //       4. Re-encrypt hoặc forward tới recipient socket
        //       5. Log message routing (KHÔNG log nội dung)
        log.warn("handleChatMessage — NOT YET IMPLEMENTED");
    }

    private void removeSession(Socket socket) {
        activeSessions.values().remove(socket);
    }

    public void stop() {
        running = false;
    }

    public static void main(String[] args) {
        new ChatServerMain().start();
    }
}
