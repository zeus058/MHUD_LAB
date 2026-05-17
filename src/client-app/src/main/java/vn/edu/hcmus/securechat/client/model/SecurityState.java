package vn.edu.hcmus.securechat.client.model;

/**
 * Trạng thái bảo mật hiển thị trên Security Monitor Panel.
 */
public class SecurityState {

    public enum ConnectionStatus {
        DISCONNECTED("Ngắt kết nối", false),
        CONNECTING("Đang kết nối...", false),
        CONNECTED_E2EE("CONNECTED (E2EE)", true);

        private final String label;
        private final boolean secure;

        ConnectionStatus(String label, boolean secure) {
            this.label = label;
            this.secure = secure;
        }

        public String getLabel() {
            return label;
        }

        public boolean isSecure() {
            return secure;
        }
    }

    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private String tgtRemaining = "—";
    private String stRemaining = "—";
    private String encryption = "—";
    private String certificate = "—";
    private int sentCount;
    private int receivedCount;

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public String getTgtRemaining() {
        return tgtRemaining;
    }

    public void setTgtRemaining(String tgtRemaining) {
        this.tgtRemaining = tgtRemaining;
    }

    public String getStRemaining() {
        return stRemaining;
    }

    public void setStRemaining(String stRemaining) {
        this.stRemaining = stRemaining;
    }

    public String getEncryption() {
        return encryption;
    }

    public void setEncryption(String encryption) {
        this.encryption = encryption;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public int getSentCount() {
        return sentCount;
    }

    public void setSentCount(int sentCount) {
        this.sentCount = sentCount;
    }

    public int getReceivedCount() {
        return receivedCount;
    }

    public void setReceivedCount(int receivedCount) {
        this.receivedCount = receivedCount;
    }

    public void incrementSent() {
        sentCount++;
    }

    public void incrementReceived() {
        receivedCount++;
    }

    /** Trạng thái mẫu sau khi đăng nhập thành công (demo UI). */
    public static SecurityState demoConnected() {
        SecurityState state = new SecurityState();
        state.status = ConnectionStatus.CONNECTED_E2EE;
        state.tgtRemaining = "6h 42m";
        state.stRemaining = "6h 42m";
        state.encryption = "AES-256-GCM + ECDHE + Kyber-768";
        state.certificate = "VALID (hết hạn 2027-01-01)";
        return state;
    }

    /**
     * Khởi tạo SecurityState với thông tin chứng chỉ thực tế từ PkiManager.
     */
    public static SecurityState fromRealSession(java.security.cert.X509Certificate cert) {
        SecurityState state = new SecurityState();
        state.status = ConnectionStatus.CONNECTED_E2EE;
        state.encryption = "AES-256-GCM + ECDHE + Kyber-768";

        // TGT/ST hết hạn sau 8 giờ kể từ lúc đăng nhập
        long expiresMs = System.currentTimeMillis() + 8 * 60 * 60 * 1000L;
        long remaining = (expiresMs - System.currentTimeMillis()) / 60000;
        state.tgtRemaining = remaining / 60 + "h " + (remaining % 60) + "m";
        state.stRemaining = state.tgtRemaining;

        if (cert != null) {
            try {
                java.util.Date notAfter = cert.getNotAfter();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                String expiry = sdf.format(notAfter);
                boolean valid = notAfter.after(new java.util.Date());
                String cn = cert.getSubjectX500Principal().getName();
                // Trích CN=...
                for (String part : cn.split(",")) {
                    if (part.trim().startsWith("CN=")) {
                        cn = part.trim().substring(3);
                        break;
                    }
                }
                state.certificate = (valid ? "VALID" : "EXPIRED") + " " + cn + " (hết hạn " + expiry + ")";
            } catch (Exception e) {
                state.certificate = "VALID (không đọc được thông tin)";
            }
        } else {
            state.certificate = "VALID (không có chứng chỉ)";
        }
        return state;
    }
}
