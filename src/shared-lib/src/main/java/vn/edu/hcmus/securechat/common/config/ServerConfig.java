package vn.edu.hcmus.securechat.common.config;

public final class ServerConfig {
    public static final String CA_HOST = "100.113.166.65"; // đổi thành IP máy server khi chạy 2 máy LAN
    public static final String AS_HOST = "100.113.166.65";
    public static final String TGS_HOST = "100.113.166.65";
    public static final String CHAT_HOST = "100.113.166.65";

    public static final int CA_PORT = 8443; // CA Server (PKI + OCSP)
    public static final int AS_PORT = 8881; // Authentication Server
    public static final int TGS_PORT = 8882; // Ticket Granting Server
    public static final int CHAT_PORT = 8883; // Chat Server
    public static final int OCSP_PORT = 8884; // OCSP Responder endpoint

    public static final int CONNECT_TIMEOUT_MS = 10_000; // 10 giây
    public static final int READ_TIMEOUT_MS = 30_000; // 30 giây
    public static final int NTP_TIMEOUT_MS = 5_000; // 5 giây
    public static final int NTP_RETRY_COUNT = 3;

    private ServerConfig() {
    }
}
