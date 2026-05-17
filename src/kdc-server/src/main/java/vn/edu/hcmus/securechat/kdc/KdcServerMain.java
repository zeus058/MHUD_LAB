package vn.edu.hcmus.securechat.kdc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.exception.FramingException;
import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtInner;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.StRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.StInner;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.crypto.HybridCrypto;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.io.ByteArrayInputStream;

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
    private KeyPair tgsKeyPair;
    private KeyPair chatKeyPair;

    public KdcServerMain() {
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "kdc-handler");
            t.setDaemon(true);
            return t;
        });
        this.replayDefense = new ReplayDefenseService();
        
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, new SecureRandom());
            tgsKeyPair = keyGen.generateKeyPair();
            chatKeyPair = keyGen.generateKeyPair();
            log.info("Volatile TGS RSA-2048 KeyPair generated.");
            log.info("Chat Server RSA-2048 KeyPair generated.");

            // Lưu chatKeyPair ra file để ChatServer load
            saveChatKeyPair(chatKeyPair);
        } catch (Exception e) {
            log.error("Failed to generate KeyPair", e);
        }
    }

    /**
     * Lưu private key và public key của ChatServer ra file data/chat_server_key.bin.
     * Format: [4 bytes privLen][privDer][4 bytes pubLen][pubDer]
     */
    private void saveChatKeyPair(KeyPair kp) {
        try {
            File dir = new File("data");
            dir.mkdirs();
            File keyFile = new File(dir, "chat_server_key.bin");
            byte[] priv = kp.getPrivate().getEncoded();
            byte[] pub  = kp.getPublic().getEncoded();
            try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                // write privLen + priv
                fos.write((priv.length >> 24) & 0xFF);
                fos.write((priv.length >> 16) & 0xFF);
                fos.write((priv.length >> 8)  & 0xFF);
                fos.write( priv.length        & 0xFF);
                fos.write(priv);
                // write pubLen + pub
                fos.write((pub.length >> 24) & 0xFF);
                fos.write((pub.length >> 16) & 0xFF);
                fos.write((pub.length >> 8)  & 0xFF);
                fos.write( pub.length        & 0xFF);
                fos.write(pub);
            }
            log.info("Chat Server KeyPair saved to {}", keyFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save chat server key pair", e);
        }
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
        try {
            // 1. Deserialize TgtRequest từ payload
            TgtRequest req = JsonSerializer.fromBytes(frame.getPayload(), TgtRequest.class);
            
            // 2. Decode client certificate
            byte[] certBytes = Base64.getDecoder().decode(req.getCert());
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate clientCert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            
            // TODO: Verify client certificate (chain validation + OCSP check)
            
            // 3. Sinh session key K_A_TGS (32 bytes, SecureRandom)
            byte[] kaTgsBytes = new byte[32];
            new SecureRandom().nextBytes(kaTgsBytes);
            String kaTgsBase64 = Base64.getEncoder().encodeToString(kaTgsBytes);
            
            // 4. Tạo TgtInner, encrypt bằng PU_TGS (Hybrid Encrypt)
            long now = System.currentTimeMillis();
            long expiresAt = now + 8 * 60 * 60 * 1000; // 8 hours
            TgtInner tgtInner = new TgtInner(req.getClientId(), req.getTargetTgs(), now, expiresAt, kaTgsBase64, true, "ENCRYPT_ONLY|TGS_SERVICE|8H_EXPIRY");
            byte[] tgtInnerBytes = JsonSerializer.toBytes(tgtInner);
            byte[] encryptedTgt = HybridCrypto.encrypt(tgsKeyPair.getPublic(), tgtInnerBytes);
            String tgtBase64 = Base64.getEncoder().encodeToString(encryptedTgt);
            
            // 5. Tạo response inner, encrypt bằng PU_client
            TgtResponseInner respInner = new TgtResponseInner(kaTgsBase64, req.getNonce(), req.getTargetTgs());
            byte[] respInnerBytes = JsonSerializer.toBytes(respInner);
            byte[] encryptedResp = HybridCrypto.encrypt(clientCert.getPublicKey(), respInnerBytes);
            String respBase64 = Base64.getEncoder().encodeToString(encryptedResp);
            
            // 6. Gửi TgtResponse qua PacketFrame
            TgtResponse response = new TgtResponse(tgtBase64, respBase64);
            PacketFrame.write(socket.getOutputStream(), MessageType.TGT_RESPONSE.getCode(), JsonSerializer.toBytes(response));
            
            // 7. Audit log
            auditLog.info("TGT_ISSUED clientId={} ip={} expiresAt={}", req.getClientId(), clientAddr, expiresAt);
            log.info("[AS] Issued TGT for clientId={}", req.getClientId());
            
        } catch (Throwable e) {
            log.error("[AS] Error handling TGT request from {}", clientAddr, e);
        }
    }

    private void handleStRequest(PacketFrame frame, Socket socket, String clientAddr) {
        try {
            // 1. Deserialize StRequest
            StRequest req = JsonSerializer.fromBytes(frame.getPayload(), StRequest.class);
            
            // 2. Decrypt TGT bằng TGS private key
            byte[] encryptedTgt = Base64.getDecoder().decode(req.getTgt());
            byte[] tgtInnerBytes = HybridCrypto.decrypt(tgsKeyPair.getPrivate(), encryptedTgt);
            TgtInner tgtInner = JsonSerializer.fromBytes(tgtInnerBytes, TgtInner.class);
            
            if (System.currentTimeMillis() > tgtInner.getExpiresAt()) {
                log.error("[TGS] TGT has expired for clientId={}", tgtInner.getClientId());
                return;
            }
            
            byte[] kaTgsBytes = Base64.getDecoder().decode(tgtInner.getSessionKey());
            
            // 3. Decrypt Authenticator
            byte[] encryptedAuth = Base64.getDecoder().decode(req.getAuthenticator());
            byte[] authBytes = AesGcmCipher.decrypt(kaTgsBytes, encryptedAuth);
            AuthenticatorJson auth = JsonSerializer.fromBytes(authBytes, AuthenticatorJson.class);
            
            // 4. Validate Authenticator
            if (!auth.getClientId().equals(tgtInner.getClientId())) {
                log.error("[TGS] Authenticator clientId mismatch!");
                return;
            }
            try {
                replayDefense.validateAuthenticator(auth);
            } catch (vn.edu.hcmus.securechat.common.exception.ReplayAttackException e) {
                log.error("[TGS] Authenticator rejected: {}", e.getMessage());
                return;
            }
            
            // 5. Sinh session key K_A_Chat (32 bytes)
            byte[] kaChatBytes = new byte[32];
            new SecureRandom().nextBytes(kaChatBytes);
            String kaChatBase64 = Base64.getEncoder().encodeToString(kaChatBytes);
            
            // 6. Tạo StInner, encrypt bằng PU_ChatServer (Hybrid Encrypt)
            long now = System.currentTimeMillis();
            long expiresAt = now + 8 * 60 * 60 * 1000;
            String targetServer = ServerConfig.CHAT_HOST;
            StInner stInner = new StInner(tgtInner.getClientId(), "", targetServer, now, expiresAt, kaChatBase64, "ENCRYPT_ONLY|CHAT_SERVICE|8H_EXPIRY");
            byte[] stInnerBytes = JsonSerializer.toBytes(stInner);
            byte[] encryptedSt = HybridCrypto.encrypt(chatKeyPair.getPublic(), stInnerBytes);
            String stBase64 = Base64.getEncoder().encodeToString(encryptedSt);
            
            // 7. Tạo response inner, encrypt bằng K_A_TGS
            StResponseInner respInner = new StResponseInner(kaChatBase64, req.getNonce());
            byte[] respInnerBytes = JsonSerializer.toBytes(respInner);
            byte[] encryptedResp = AesGcmCipher.encrypt(kaTgsBytes, respInnerBytes);
            String respBase64 = Base64.getEncoder().encodeToString(encryptedResp);
            
            // 8. Gửi ST_RESPONSE
            StResponse response = new StResponse(stBase64, respBase64);
            PacketFrame.write(socket.getOutputStream(), MessageType.ST_RESPONSE.getCode(), JsonSerializer.toBytes(response));
            
            // 9. Audit log
            auditLog.info("ST_ISSUED clientId={} target={} expiresAt={}", tgtInner.getClientId(), targetServer, expiresAt);
            log.info("[TGS] Issued ST for clientId={} to target={}", tgtInner.getClientId(), targetServer);
            
        } catch (Throwable e) {
            log.error("[TGS] Error handling ST request from {}", clientAddr, e);
        }
    }

    public void stop() {
        running = false;
        replayDefense.shutdown();
    }

    public static void main(String[] args) {
        new KdcServerMain().start();
    }
}
