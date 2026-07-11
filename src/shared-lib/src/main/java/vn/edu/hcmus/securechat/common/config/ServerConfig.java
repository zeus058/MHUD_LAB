package vn.edu.hcmus.securechat.common.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.util.PathUtil;

/**
 * Cấu hình kết nối cho hệ thống SecureChat.
 * Hỗ trợ nạp động từ file config.properties ngoài để dễ dàng thay đổi địa chỉ IP máy chủ Azure.
 */
public final class ServerConfig {
    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    private static final Properties props = new Properties();

    static {
        // Thử nạp config.properties từ root dự án/ứng dụng hoặc thư mục chạy hiện tại
        Path configFile = PathUtil.resolve("config.properties");
        if (!Files.exists(configFile)) {
            configFile = Path.of("config.properties").toAbsolutePath();
        }

        if (Files.exists(configFile) && Files.isRegularFile(configFile)) {
            log.info("Loading configuration from external file: {}", configFile.toAbsolutePath());
            try (InputStream is = Files.newInputStream(configFile)) {
                props.load(is);
            } catch (Exception e) {
                log.error("Failed to load config.properties, falling back to defaults", e);
            }
        } else {
            log.info("config.properties not found at: {}, using default configurations", configFile.toAbsolutePath());
        }
    }

    private static String getProperty(String key, String defaultValue) {
        // Ưu tiên System property trước, sau đó là properties từ file, cuối cùng là mặc định
        return System.getProperty(key, props.getProperty(key, defaultValue));
    }

    private static int getIntProperty(String key, int defaultValue) {
        String val = getProperty(key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid integer config for key {}: '{}', using default: {}", key, val, defaultValue);
            }
        }
        return defaultValue;
    }

    public static final String CA_HOST = getProperty("ca.host", "70.153.139.17");
    public static final String AS_HOST = getProperty("as.host", "70.153.139.17");
    public static final String TGS_HOST = getProperty("tgs.host", "70.153.139.17");
    public static final String CHAT_HOST = getProperty("chat.host", "70.153.139.17");

    public static final int CA_PORT = getIntProperty("ca.port", 8443);
    public static final int AS_PORT = getIntProperty("as.port", 8881);
    public static final int TGS_PORT = getIntProperty("tgs.port", 8882);
    public static final int CHAT_PORT = getIntProperty("chat.port", 8883);
    public static final int OCSP_PORT = getIntProperty("ocsp.port", 8884);

    public static final String NOTIFICATION_HOST = getProperty("notification.host", "70.153.139.17");
    public static final int NOTIFICATION_PORT = getIntProperty("notification.port", 8885);
    public static final String NOTIFICATION_SERVICE_ID = getProperty("notification.service.id", "notification-server");

    public static final int CONNECT_TIMEOUT_MS = getIntProperty("connect.timeout.ms", 10_000);
    public static final int READ_TIMEOUT_MS = getIntProperty("read.timeout.ms", 30_000);
    public static final int NTP_TIMEOUT_MS = getIntProperty("ntp.timeout.ms", 5_000);
    public static final int NTP_RETRY_COUNT = getIntProperty("ntp.retry.count", 3);

    private ServerConfig() {
    }
}
