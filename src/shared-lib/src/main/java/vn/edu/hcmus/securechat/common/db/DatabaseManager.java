package vn.edu.hcmus.securechat.common.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quản lý kết nối SQLite cho từng module.
 * Mỗi module dùng file DB riêng biệt.
 *
 * Sử dụng:
 *   DatabaseManager db = new DatabaseManager("data/ca-server.db");
 *   db.connect();
 *   db.executeUpdate("CREATE TABLE IF NOT EXISTS certs (...)", new Object[]{});
 *   ResultSet rs = db.executeQuery("SELECT * FROM certs WHERE serial = ?", serialNo);
 *   db.close();
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final String dbPath;
    private Connection connection;

    /**
     * @param dbPath Đường dẫn tới file SQLite (ví dụ: "data/ca-server.db")
     */
    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Mở kết nối tới database. Tự động tạo thư mục cha nếu chưa tồn tại.
     */
    public synchronized void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return; // đã kết nối
        }

        // Tạo thư mục cha nếu cần
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                log.info("Created database directory: {}", parentDir.getAbsolutePath());
            }
        }

        String jdbcUrl = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(jdbcUrl);

        // Bật WAL mode cho hiệu suất tốt hơn khi concurrent read
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        log.info("Connected to SQLite database: {}", dbPath);
    }

    /**
     * Lấy connection hiện tại. Tự động kết nối nếu chưa mở.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    /**
     * Thực thi câu lệnh INSERT/UPDATE/DELETE.
     *
     * @return số dòng bị ảnh hưởng
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            bindParams(pstmt, params);
            int affected = pstmt.executeUpdate();
            log.debug("SQL UPDATE: {} — affected rows: {}", sql, affected);
            return affected;
        }
    }

    /**
     * Thực thi câu lệnh SELECT.
     * CẢNH BÁO: Caller chịu trách nhiệm đóng ResultSet và PreparedStatement.
     *
     * Khuyến nghị sử dụng executeQueryAndProcess() để tự động quản lý resource.
     */
    public ResultSet executeQuery(String sql, Object... params) throws SQLException {
        PreparedStatement pstmt = getConnection().prepareStatement(sql);
        bindParams(pstmt, params);
        return pstmt.executeQuery();
    }

    /**
     * Thực thi SELECT và xử lý kết quả qua callback — tự động đóng resource.
     */
    public <T> T executeQueryAndProcess(String sql, ResultSetProcessor<T> processor,
                                         Object... params) throws SQLException {
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            bindParams(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                return processor.process(rs);
            }
        }
    }

    /**
     * Thực thi SQL statement (CREATE TABLE, etc.) không có tham số.
     */
    public void execute(String sql) throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            log.debug("SQL EXECUTE: {}", sql);
        }
    }

    /**
     * Đóng kết nối database.
     */
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Closed SQLite database: {}", dbPath);
            } catch (SQLException e) {
                log.error("Error closing database: {}", dbPath, e);
            } finally {
                connection = null;
            }
        }
    }

    private void bindParams(PreparedStatement pstmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            pstmt.setObject(i + 1, params[i]);
        }
    }

    /**
     * Functional interface cho xử lý ResultSet.
     */
    @FunctionalInterface
    public interface ResultSetProcessor<T> {
        T process(ResultSet rs) throws SQLException;
    }
}
