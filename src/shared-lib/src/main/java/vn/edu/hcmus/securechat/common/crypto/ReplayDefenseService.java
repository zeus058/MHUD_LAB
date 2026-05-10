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
     * Validate authenticator: kiểm tra timestamp skew và nonce uniqueness.
     *
     * @throws ReplayAttackException nếu timestamp vượt quá MAX_TIME_SKEW_SECONDS
     *                               hoặc nonce đã được sử dụng
     */
    public void validateAuthenticator(AuthenticatorJson auth) throws ReplayAttackException {
        long now = Instant.now().getEpochSecond();
        long skew = Math.abs(now - auth.getTimestamp());

        // Kiểm tra timestamp skew
        if (skew > maxTimeSkewSeconds) {
            auditLog.warn("REPLAY_REJECTED clientId={} reason=TIMESTAMP_SKEW skew={}s limit={}s",
                    auth.getClientId(), skew, maxTimeSkewSeconds);
            throw new ReplayAttackException(
                    "Timestamp skew " + skew + "s exceeds limit of "
                    + maxTimeSkewSeconds + "s");
        }

        // Kiểm tra nonce uniqueness
        if (nonceCache.contains(auth.getNonce())) {
            auditLog.warn("REPLAY_REJECTED clientId={} reason=NONCE_REUSE nonce={}",
                    auth.getClientId(), auth.getNonce());
            throw new ReplayAttackException("Nonce already used: " + auth.getNonce());
        }

        // Nonce hợp lệ → lưu vào cache
        nonceCache.put(auth.getNonce());
        log.debug("Authenticator validated: clientId={} timestamp={}", auth.getClientId(), auth.getTimestamp());
    }

    /**
     * Shutdown nonce cache cleaner. Gọi khi server shutdown.
     */
    public void shutdown() {
        nonceCache.shutdown();
    }
}
