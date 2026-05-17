package vn.edu.hcmus.securechat.kdc.storage;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.db.DatabaseManager;

/**
 * KDC Storage — Quản lý lưu trữ phiên, vé và audit trail trong SQLite.
 *
 * Tables:
 *   - issued_tgts:  Lưu trữ TGT đã cấp (cho audit trail + revocation)
 *   - issued_sts:   Lưu trữ ST đã cấp (cho audit trail)
 *   - kdc_audit_log: Audit log chi tiết cho mọi thao tác bảo mật
 */
public class KdcStorage {

    private static final Logger log = LoggerFactory.getLogger(KdcStorage.class);

    private final DatabaseManager db;

    public KdcStorage(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Khởi tạo database schema.
     * Gọi 1 lần khi KDC server startup.
     */
    public void initializeTables() throws SQLException {
        // Bảng TGT đã cấp
        db.execute(
            "CREATE TABLE IF NOT EXISTS issued_tgts (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  client_id TEXT NOT NULL," +
            "  target_tgs TEXT NOT NULL," +
            "  issued_at INTEGER NOT NULL," +
            "  expires_at INTEGER NOT NULL," +
            "  client_ip TEXT," +
            "  cv TEXT NOT NULL," +
            "  status TEXT DEFAULT 'ACTIVE'" +
            ")"
        );

        // Bảng ST đã cấp
        db.execute(
            "CREATE TABLE IF NOT EXISTS issued_sts (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  client_id TEXT NOT NULL," +
            "  target_server TEXT NOT NULL," +
            "  issued_at INTEGER NOT NULL," +
            "  expires_at INTEGER NOT NULL," +
            "  client_ip TEXT," +
            "  cv TEXT NOT NULL," +
            "  status TEXT DEFAULT 'ACTIVE'" +
            ")"
        );

        // Bảng audit log
        db.execute(
            "CREATE TABLE IF NOT EXISTS kdc_audit_log (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  timestamp INTEGER NOT NULL," +
            "  event_type TEXT NOT NULL," +
            "  client_id TEXT," +
            "  client_ip TEXT," +
            "  detail TEXT," +
            "  success INTEGER NOT NULL DEFAULT 1" +
            ")"
        );

        // Indexes
        db.execute("CREATE INDEX IF NOT EXISTS idx_tgt_client ON issued_tgts(client_id)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_tgt_expires ON issued_tgts(expires_at)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_st_client ON issued_sts(client_id)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_st_expires ON issued_sts(expires_at)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_audit_ts ON kdc_audit_log(timestamp)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_audit_type ON kdc_audit_log(event_type)");

        log.info("KDC database schema initialized successfully.");
    }

    /**
     * Ghi lại TGT đã cấp.
     */
    public void recordTgtIssued(String clientId, String targetTgs,
                                long issuedAt, long expiresAt,
                                String clientIp, String cv) throws SQLException {
        db.executeUpdate(
            "INSERT INTO issued_tgts (client_id, target_tgs, issued_at, expires_at, client_ip, cv) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            clientId, targetTgs, issuedAt, expiresAt, clientIp, cv
        );
    }

    /**
     * Ghi lại ST đã cấp.
     */
    public void recordStIssued(String clientId, String targetServer,
                               long issuedAt, long expiresAt,
                               String clientIp, String cv) throws SQLException {
        db.executeUpdate(
            "INSERT INTO issued_sts (client_id, target_server, issued_at, expires_at, client_ip, cv) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            clientId, targetServer, issuedAt, expiresAt, clientIp, cv
        );
    }

    /**
     * Ghi audit log entry.
     *
     * @param eventType ví dụ: TGT_ISSUED, ST_ISSUED, TGT_REJECTED, REPLAY_ATTACK
     * @param clientId  ID của client (nullable)
     * @param clientIp  IP của client (nullable)
     * @param detail    Chi tiết (nullable)
     * @param success   true nếu thao tác thành công
     */
    public void logAuditEvent(String eventType, String clientId,
                              String clientIp, String detail, boolean success)
            throws SQLException {
        db.executeUpdate(
            "INSERT INTO kdc_audit_log (timestamp, event_type, client_id, client_ip, detail, success) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            System.currentTimeMillis() / 1000, eventType, clientId, clientIp, detail,
            success ? 1 : 0
        );
    }

    /**
     * Đếm số TGT active (chưa hết hạn) cho một client.
     */
    public int countActiveTgts(String clientId) throws SQLException {
        long now = System.currentTimeMillis() / 1000;
        return db.executeQueryAndProcess(
            "SELECT COUNT(*) FROM issued_tgts WHERE client_id = ? AND expires_at > ? AND status = 'ACTIVE'",
            rs -> {
                rs.next();
                return rs.getInt(1);
            },
            clientId, now
        );
    }

    /**
     * Xóa các vé đã hết hạn (chạy định kỳ để giữ DB gọn).
     *
     * @return số vé đã xóa
     */
    public int cleanExpiredTickets() throws SQLException {
        long now = System.currentTimeMillis() / 1000;
        int tgtCleaned = db.executeUpdate(
            "DELETE FROM issued_tgts WHERE expires_at < ?", now);
        int stCleaned = db.executeUpdate(
            "DELETE FROM issued_sts WHERE expires_at < ?", now);
        log.info("Cleaned {} expired TGTs and {} expired STs", tgtCleaned, stCleaned);
        return tgtCleaned + stCleaned;
    }
}
