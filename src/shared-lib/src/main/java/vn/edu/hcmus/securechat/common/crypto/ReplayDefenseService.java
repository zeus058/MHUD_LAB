package vn.edu.hcmus.securechat.common.crypto;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;

/**
 * Dịch vụ chống Replay Attack — kiểm tra Timestamp + Nonce.
 * Theo Contrains.md mục 5.3.
 *
 * Sử dụng:
 *   ReplayDefenseService defense = new ReplayDefenseService();
 *   defense.validateAuthenticator(authenticator); // throws ReplayAttackException
 */
public class ReplayDefenseService {

    private static final Logger log = LoggerFactory.getLogger(ReplayDefenseService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final NonceCache nonceCache;
    private final int maxTimeSkewSeconds;

    public ReplayDefenseService() {
        this(new NonceCache(), CryptoConstants.MAX_TIME_SKEW_SECONDS);
    }

    public ReplayDefenseService(NonceCache nonceCache, int maxTimeSkewSeconds) {
        this.nonceCache = nonceCache;
        this.maxTimeSkewSeconds = maxTimeSkewSeconds;
    }

    /**
     * Validate request: kiểm tra timestamp skew và sự duy nhất của bộ khóa (client_id, timestamp, nonce/session_id).
     *
     * @throws ReplayAttackException nếu timestamp vượt quá MAX_TIME_SKEW_SECONDS
     *                               hoặc yêu cầu đã được xử lý trước đó
     */
    public void validate(String clientId, long timestamp, String nonceOrSessionId) throws ReplayAttackException {
        long now = Instant.now().getEpochSecond();
        long skew = Math.abs(now - timestamp);

        // Kiểm tra timestamp skew
        if (skew > maxTimeSkewSeconds) {
            auditLog.warn("REPLAY_REJECTED clientId={} reason=TIMESTAMP_SKEW skew={}s limit={}s",
                    clientId, skew, maxTimeSkewSeconds);
            throw new ReplayAttackException(
                    "Timestamp skew " + skew + "s exceeds limit of "
                    + maxTimeSkewSeconds + "s");
        }

        // Tạo compound key: (client_id, authenticator_timestamp, nonce/session_id)
        String cacheKey = clientId + ":" + timestamp + ":" + nonceOrSessionId;

        // Kiểm tra trong cache
        if (nonceCache.contains(cacheKey)) {
            auditLog.warn("REPLAY_REJECTED clientId={} reason=REPLAY_DETECTED key={}",
                    clientId, cacheKey);
            throw new ReplayAttackException("Replay attack detected: " + cacheKey);
        }

        // Thêm compound key vào cache
        nonceCache.put(cacheKey);
        log.debug("Request validated for replay defense: key={}", cacheKey);
    }

    /**
     * Validate authenticator: kiểm tra timestamp skew và nonce uniqueness.
     *
     * @throws ReplayAttackException nếu timestamp vượt quá MAX_TIME_SKEW_SECONDS
     *                               hoặc nonce đã được sử dụng
     */
    public void validateAuthenticator(AuthenticatorJson auth) throws ReplayAttackException {
        validate(auth.getClientId(), auth.getTimestamp(), auth.getNonce());
    }

    /**
     * Shutdown nonce cache cleaner. Gọi khi server shutdown.
     */
    public void shutdown() {
        nonceCache.shutdown();
    }
}
