package vn.edu.hcmus.securechat.ca.storage;

import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.db.DatabaseManager;

/**
 * CertificateStorage — Quản lý lưu trữ chứng chỉ trong SQLite.
 * 
 * Tables:
 *   - issued_certificates: Lưu trữ tất cả chứng chỉ đã cấp
 *   - revoked_certificates: Lưu trữ danh sách chứng chỉ bị thu hồi
 */
public class CertificateStorage {

    private static final Logger log = LoggerFactory.getLogger(CertificateStorage.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final DatabaseManager db;

    public CertificateStorage(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Khởi tạo database schema.
     * Gọi 1 lần khi CA server startup.
     */
    public void initializeTables() throws SQLException {
        // Bảng chứng chỉ đã cấp
        db.execute(
            "CREATE TABLE IF NOT EXISTS issued_certificates (" +
            "  serial TEXT PRIMARY KEY," +
            "  subject_dn TEXT NOT NULL," +
            "  issuer_dn TEXT NOT NULL," +
            "  certificate BLOB NOT NULL," +
            "  not_before INTEGER NOT NULL," +
            "  not_after INTEGER NOT NULL," +
            "  created_at INTEGER NOT NULL," +
            "  fingerprint TEXT UNIQUE NOT NULL," +
            "  status TEXT DEFAULT 'ACTIVE'" +
            ")"
        );

        // Bảng chứng chỉ bị thu hồi
        db.execute(
            "CREATE TABLE IF NOT EXISTS revoked_certificates (" +
            "  serial TEXT PRIMARY KEY," +
            "  revocation_time INTEGER NOT NULL," +
            "  revocation_reason TEXT," +
            "  FOREIGN KEY(serial) REFERENCES issued_certificates(serial)" +
            ")"
        );

        // Index để tối ưu query
        db.execute("CREATE INDEX IF NOT EXISTS idx_subject_dn ON issued_certificates(subject_dn)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_fingerprint ON issued_certificates(fingerprint)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_created_at ON issued_certificates(created_at)");

        log.info("Database schema initialized successfully.");
    }

    /**
     * Lưu chứng chỉ mới vào database.
     *
     * @param serial Certificate serial number (hex string)
     * @param subjectDn X.500 DN của subject
     * @param issuerDn X.500 DN của issuer (CA)
     * @param certificateDer DER-encoded certificate bytes
     * @param notBefore Unix timestamp (ms)
     * @param notAfter Unix timestamp (ms)
     * @param fingerprint SHA-256 fingerprint (hex string)
     */
    public void saveCertificate(String serial, String subjectDn, String issuerDn,
                                byte[] certificateDer, long notBefore, long notAfter,
                                String fingerprint) throws SQLException {
        String sql = "INSERT INTO issued_certificates" +
                     "(serial, subject_dn, issuer_dn, certificate, not_before, not_after," +
                     " created_at, fingerprint, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')";

        db.executeUpdate(sql,
            serial, subjectDn, issuerDn, certificateDer,
            notBefore, notAfter, System.currentTimeMillis(),
            fingerprint
        );

        log.info("Certificate stored: serial={}, subject={}", serial, subjectDn);
        auditLog.info("CERT_ISSUED serial={} subject_dn={}", serial, subjectDn);
    }

    /**
     * Lấy chứng chỉ từ database theo serial number.
     *
     * @return Optional chứa DER-encoded certificate, hoặc empty nếu không tìm thấy
     */
    public Optional<byte[]> getCertificateBySerial(String serial) throws SQLException {
        return db.executeQueryAndProcess(
            "SELECT certificate FROM issued_certificates WHERE serial = ?",
            rs -> {
                if (rs.next()) {
                    return Optional.of(rs.getBytes("certificate"));
                }
                return Optional.empty();
            },
            serial
        );
    }

    /**
     * Kiểm tra trạng thái chứng chỉ: GOOD, REVOKED, hoặc UNKNOWN.
     *
     * @return CertStatus enum
     */
    public CertStatus getCertificateStatus(String serial) throws SQLException {
        return db.executeQueryAndProcess(
            "SELECT ic.serial, rc.serial as revoked_serial " +
            "FROM issued_certificates ic " +
            "LEFT JOIN revoked_certificates rc ON ic.serial = rc.serial " +
            "WHERE ic.serial = ?",
            rs -> {
                if (rs.next()) {
                    String revokedSerial = rs.getString("revoked_serial");
                    if (revokedSerial != null) {
                        return CertStatus.REVOKED;
                    }
                    return CertStatus.GOOD;
                }
                return CertStatus.UNKNOWN;
            },
            serial
        );
    }

    /**
     * Lấy thông tin chi tiết của chứng chỉ (bao gồm thời gian revocation nếu có).
     */
    public Optional<CertificateInfo> getCertificateInfo(String serial) throws SQLException {
        return db.executeQueryAndProcess(
            "SELECT ic.serial, ic.subject_dn, ic.issuer_dn, ic.not_before, ic.not_after," +
            "       ic.created_at, rc.revocation_time, rc.revocation_reason " +
            "FROM issued_certificates ic " +
            "LEFT JOIN revoked_certificates rc ON ic.serial = rc.serial " +
            "WHERE ic.serial = ?",
            rs -> {
                if (rs.next()) {
                    CertificateInfo info = new CertificateInfo();
                    info.serial = rs.getString("serial");
                    info.subjectDn = rs.getString("subject_dn");
                    info.issuerDn = rs.getString("issuer_dn");
                    info.notBefore = rs.getLong("not_before");
                    info.notAfter = rs.getLong("not_after");
                    info.createdAt = rs.getLong("created_at");
                    info.revocationTime = rs.getLong("revocation_time"); // 0 if not revoked
                    info.revocationReason = rs.getString("revocation_reason");
                    return Optional.of(info);
                }
                return Optional.empty();
            },
            serial
        );
    }

    /**
     * Thu hồi chứng chỉ.
     *
     * @param serial Certificate serial number
     * @param reason Lý do thu hồi (unspecified, keyCompromise, caCompromise, etc.)
     */
    public void revokeCertificate(String serial, String reason) throws SQLException {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO revoked_certificates (serial, revocation_time, revocation_reason) " +
                     "VALUES (?, ?, ?)";
        db.executeUpdate(sql, serial, now, reason);

        log.warn("Certificate revoked: serial={}, reason={}", serial, reason);
        auditLog.warn("CERT_REVOKED serial={} reason={}", serial, reason);
    }

    /**
     * Kiểm tra xem chứng chỉ có tồn tại không.
     */
    public boolean certificateExists(String serial) throws SQLException {
        return db.executeQueryAndProcess(
            "SELECT 1 FROM issued_certificates WHERE serial = ? LIMIT 1",
            rs -> rs.next(),
            serial
        );
    }

    /**
     * DTO chứa thông tin chi tiết chứng chỉ.
     */
    public static class CertificateInfo {
        public String serial;
        public String subjectDn;
        public String issuerDn;
        public long notBefore;
        public long notAfter;
        public long createdAt;
        public long revocationTime;
        public String revocationReason;

        public boolean isRevoked() {
            return revocationTime > 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > notAfter;
        }
    }

    /**
     * Enum trạng thái chứng chỉ cho OCSP response.
     */
    public enum CertStatus {
        GOOD,
        REVOKED,
        UNKNOWN
    }
}
