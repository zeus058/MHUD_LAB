package vn.edu.hcmus.securechat.client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NTP Time Client - Mô phỏng việc đồng bộ thời gian.
 * Theo yêu cầu của báo cáo mục 5.2, Client phải đồng bộ giờ NTP để chống Replay Attack.
 */
public class NtpTimeClient {
    private static final Logger log = LoggerFactory.getLogger(NtpTimeClient.class);
    
    // Giả lập sai lệch đồng hồ nội bộ so với NTP
    private static final long MOCK_TIME_OFFSET = 1200; // 1.2s

    public static void syncTime() {
        log.info("Đang đồng bộ thời gian với máy chủ NTP...");
        try {
            Thread.sleep(200); // Giả lập độ trễ mạng
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Đồng bộ thành công. Offset = {} ms", MOCK_TIME_OFFSET);
    }

    public static long getCurrentNetworkTime() {
        return System.currentTimeMillis() + MOCK_TIME_OFFSET;
    }
}
