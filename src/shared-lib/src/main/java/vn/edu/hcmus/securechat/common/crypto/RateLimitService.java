package vn.edu.hcmus.securechat.common.crypto;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import vn.edu.hcmus.securechat.common.exception.ProtocolException;

/**
 * Lightweight in-process rate limiter for AS/TGS/ChatServer request gates.
 */
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String scope, String subject, int maxRequests, Duration window)
            throws ProtocolException {
        if (subject == null || subject.isBlank()) {
            subject = "UNKNOWN";
        }
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        String key = scope + ":" + subject;
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        synchronized (bucket) {
            if (bucket.cooldownUntilMillis > now) {
                throw new ProtocolException("Rate limit cooldown active for " + scope);
            }
            if (now - bucket.windowStartMillis >= windowMillis) {
                bucket.windowStartMillis = now;
                bucket.count = 0;
            }
            bucket.count++;
            if (bucket.count > maxRequests) {
                throw new ProtocolException("Rate limit exceeded for " + scope);
            }
        }
    }

    public void checkFailureCooldown(String scope, String subject) throws ProtocolException {
        if (subject == null || subject.isBlank()) {
            subject = "UNKNOWN";
        }
        Bucket bucket = buckets.get(scope + ":" + subject);
        long now = System.currentTimeMillis();
        if (bucket != null && bucket.cooldownUntilMillis > now) {
            throw new ProtocolException("Failure cooldown active for " + scope);
        }
    }

    public void recordFailure(String scope, String subject, int maxFailures, Duration cooldown) {
        if (subject == null || subject.isBlank()) {
            subject = "UNKNOWN";
        }
        long now = System.currentTimeMillis();
        String key = scope + ":" + subject;
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        synchronized (bucket) {
            bucket.failures++;
            if (bucket.failures >= maxFailures) {
                bucket.cooldownUntilMillis = now + cooldown.toMillis();
                bucket.failures = 0;
            }
        }
    }

    public void recordSuccess(String scope, String subject) {
        if (subject == null || subject.isBlank()) {
            subject = "UNKNOWN";
        }
        Bucket bucket = buckets.get(scope + ":" + subject);
        if (bucket != null) {
            synchronized (bucket) {
                bucket.failures = 0;
            }
        }
    }

    private static final class Bucket {
        private long windowStartMillis;
        private int count;
        private int failures;
        private long cooldownUntilMillis;

        private Bucket(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
