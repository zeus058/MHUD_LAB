package vn.edu.hcmus.securechat.common.crypto;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.config.ServerConfig;

/**
 * Dịch vụ đồng bộ thời gian NTP — Contrains.md mục 5.3.
 *
 * - Client phải sync NTP trước khi gửi Authenticator
 * - Timeout: 5 giây, retry 3 lần
 * - Nếu NTP fail sau 3 retry → fallback sang Nonce-only mode, log WARN
 *
 * Sử dụng SNTP (Simple NTP) qua UDP port 123.
 */
public final class NtpSyncService {

    private static final Logger log = LoggerFactory.getLogger(NtpSyncService.class);

    private static final String[] NTP_SERVERS = {
        "pool.ntp.org",
        "time.google.com",
        "time.windows.com"
    };

    private static final int NTP_PORT = 123;
    private static final int NTP_PACKET_SIZE = 48;
    // Offset between NTP epoch (1900-01-01) and Unix epoch (1970-01-01) in seconds
    private static final long NTP_EPOCH_OFFSET = 2208988800L;

    private volatile long offsetSeconds = 0;
    private volatile boolean synced = false;

    public NtpSyncService() {}

    /**
     * Thực hiện NTP sync. Gọi trước khi tạo Authenticator.
     *
     * @return true nếu sync thành công, false nếu fallback Nonce-only
     */
    public boolean synchronize() {
        for (String server : NTP_SERVERS) {
            for (int retry = 0; retry < ServerConfig.NTP_RETRY_COUNT; retry++) {
                try {
                    long offset = queryNtpOffset(server);
                    this.offsetSeconds = offset;
                    this.synced = true;
                    log.info("NTP sync OK: server={}, offset={}s", server, offset);
                    return true;
                } catch (Exception e) {
                    log.debug("NTP attempt {}/{} to {} failed: {}",
                            retry + 1, ServerConfig.NTP_RETRY_COUNT, server, e.getMessage());
                }
            }
        }

        // Tất cả NTP servers fail → Nonce-only mode
        log.warn("NTP sync FAILED after all retries — falling back to Nonce-only mode");
        this.synced = false;
        this.offsetSeconds = 0;
        return false;
    }

    /**
     * Lấy timestamp hiện tại đã corrected bằng NTP offset.
     * Nếu chưa sync → trả về system time.
     */
    public long getCorrectedTimestamp() {
        return Instant.now().getEpochSecond() + offsetSeconds;
    }

    /**
     * Kiểm tra đã sync NTP thành công chưa.
     */
    public boolean isSynced() {
        return synced;
    }

    /**
     * Lấy NTP offset (seconds).
     */
    public long getOffsetSeconds() {
        return offsetSeconds;
    }

    /**
     * Query NTP server và tính offset.
     * Dùng SNTP (simplified) — đủ cho mục đích chống replay (±5 phút tolerance).
     */
    private long queryNtpOffset(String server) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(ServerConfig.NTP_TIMEOUT_MS);

            InetAddress address = InetAddress.getByName(server);

            // Tạo NTP request packet
            byte[] buffer = new byte[NTP_PACKET_SIZE];
            buffer[0] = 0x1B; // LI=0, VN=3, Mode=3 (client)

            DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, NTP_PORT);

            long t1 = System.currentTimeMillis(); // Client send time
            socket.send(request);

            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            long t4 = System.currentTimeMillis(); // Client receive time

            // Parse NTP response — Transmit Timestamp at bytes 40-47
            long ntpSeconds = 0;
            for (int i = 40; i <= 43; i++) {
                ntpSeconds = (ntpSeconds << 8) | (buffer[i] & 0xFF);
            }

            // Convert NTP time to Unix time (seconds)
            long serverTime = ntpSeconds - NTP_EPOCH_OFFSET;

            // Tính offset: server_time - ((t1 + t4) / 2) / 1000
            long localTime = (t1 + t4) / 2 / 1000;
            long offset = serverTime - localTime;

            return offset;
        }
    }
}
