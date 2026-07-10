package vn.edu.hcmus.securechat.common.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tiện ích xử lý đường dẫn — Giúp ứng dụng tìm đúng thư mục dữ liệu (data/)
 * bất kể vị trí chạy (IDE, CMD, hoặc thư mục con).
 */
public final class PathUtil {
    private static final Logger log = LoggerFactory.getLogger(PathUtil.class);
    
    private static Path cachedRoot = null;

    private PathUtil() {
    }

    /**
     * Lấy đường dẫn tuyệt đối đến thư mục gốc của dự án (nơi chứa data/).
     */
    public static Path getProjectRoot() {
        if (cachedRoot != null) {
            return cachedRoot;
        }

        // 1. Kiểm tra System Property
        String override = System.getProperty("app.root");
        if (override != null) {
            cachedRoot = Paths.get(override).toAbsolutePath();
            return cachedRoot;
        }

        // 2. Tự động tìm kiếm ngược lên trên từ thư mục hiện tại
        Path current = Paths.get("").toAbsolutePath();
        Path candidate = current;

        while (candidate != null) {
            // Kiểm tra xem thư mục này có phải gốc không (có data/ hoặc pom.xml)
            if (Files.isDirectory(candidate.resolve("data")) || Files.exists(candidate.resolve("pom.xml"))) {
                cachedRoot = candidate;
                log.info("Detected project root at: {}", cachedRoot);
                return cachedRoot;
            }
            candidate = candidate.getParent();
        }

        // 3. Fallback về thư mục hiện tại
        cachedRoot = current;
        log.warn("Could not detect project root. Falling back to: {}", cachedRoot);
        return cachedRoot;
    }

    /**
     * Giải quyết đường dẫn tương đối từ gốc dự án.
     */
    public static Path resolve(String path) {
        return getProjectRoot().resolve(path);
    }
}
