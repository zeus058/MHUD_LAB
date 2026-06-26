package vn.edu.hcmus.securechat.client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NTP Time Client - simulates clock synchronization.
 * The client synchronizes time to reduce replay-attack risk.
 */
public class NtpTimeClient {
    private static final Logger log = LoggerFactory.getLogger(NtpTimeClient.class);
    
    // Simulated local clock offset from NTP.
    private static final long MOCK_TIME_OFFSET = 1200; // 1.2s

    public static void syncTime() {
        log.info("Synchronizing time with the NTP server...");
        try {
            Thread.sleep(200); // Simulated network latency.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Time synchronization succeeded. Offset = {} ms", MOCK_TIME_OFFSET);
    }

    public static long getCurrentNetworkTime() {
        return System.currentTimeMillis() + MOCK_TIME_OFFSET;
    }
}
