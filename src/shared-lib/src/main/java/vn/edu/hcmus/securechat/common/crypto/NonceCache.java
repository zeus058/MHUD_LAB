package vn.edu.hcmus.securechat.common.crypto;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache nonce đã sử dụng để phát hiện Replay Attack.
 * Thread-safe, tự động dọn dẹp entry hết hạn.
 *
 * TTL mặc định: CryptoConstants.NONCE_CACHE_TTL_SECONDS (600s = 10 phút)
 *
 * Sử dụng:
 *   NonceCache cache = new NonceCache();
 *   if (cache.contains(nonce)) { throw new ReplayAttackException(...); }
 *   cache.put(nonce);
 */
public class NonceCache {

    private static final Logger log = LoggerFactory.getLogger(NonceCache.class);

    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();
    private final int ttlSeconds;
    private final ScheduledExecutorService cleaner;

    public NonceCache() {
        this(CryptoConstants.NONCE_CACHE_TTL_SECONDS);
    }

    public NonceCache(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nonce-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        // Dọn dẹp mỗi 60 giây
        this.cleaner.scheduleAtFixedRate(this::evictExpired, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Kiểm tra nonce đã tồn tại trong cache hay chưa.
     */
    public boolean contains(String nonce) {
        Long insertedAt = cache.get(nonce);
        if (insertedAt == null) {
            return false;
        }
        // Kiểm tra xem entry có còn trong TTL không
        long now = System.currentTimeMillis() / 1000;
        if (now - insertedAt > ttlSeconds) {
            cache.remove(nonce); // đã hết hạn
            return false;
        }
        return true;
    }

    /**
     * Thêm nonce vào cache với timestamp hiện tại.
     */
    public void put(String nonce) {
        long now = System.currentTimeMillis() / 1000;
        cache.put(nonce, now);
    }

    /**
     * Dọn dẹp các entry đã hết TTL.
     */
    private void evictExpired() {
        long now = System.currentTimeMillis() / 1000;
        int removed = 0;
        Iterator<Map.Entry<String, Long>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > ttlSeconds) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("NonceCache evicted {} expired entries, {} remaining", removed, cache.size());
        }
    }

    /**
     * Số lượng nonce hiện đang trong cache.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Shutdown cleaner thread. Gọi khi server shutdown.
     */
    public void shutdown() {
        cleaner.shutdown();
    }
}
