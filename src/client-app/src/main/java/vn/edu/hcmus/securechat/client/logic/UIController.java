package vn.edu.hcmus.securechat.client.logic;

import vn.edu.hcmus.securechat.client.ui.*;
import vn.edu.hcmus.securechat.client.ui.components.*;
import vn.edu.hcmus.securechat.client.ui.components.ChatPanel;
import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * UIController — Lớp điều phối trung tâm giữa UI và logic backend.
 *
 * ┌──────────────────────────────────────────────────────┐
 * │                    UIController                      │
 * │  LoginDialog ─┐                                      │
 * │  RegisterPanel─┼── handleLogin/handleRegister ──────►│ KDC / CA
 * │  MainFrame ───┘    sendMessage ────────────────────►│ ChatServer
 * │               ◄── onMessageReceived ────────────────│
 * └──────────────────────────────────────────────────────┘
 *
 * CÁCH SỬ DỤNG:
 *   UIController controller = new UIController();
 *   controller.start();
 *
 * Để kết nối với backend thực:
 *   - Override / inject các phương thức handle*
 *   - Hoặc dùng setter injection cho các service
 */
public class UIController {

    // === UI References ===
    private LoginDialog   loginDialog;
    private RegisterPanel registerPanel;
    private JDialog       registerDialog;
    private MainFrame     mainFrame;

    // === State ===
    private String currentUsername = "";
    private String currentContactId = "";

    /**
     * Khởi động ứng dụng — gọi từ ClientApp.main()
     */
    public void start() {
        SwingUtilities.invokeLater(() -> {
            AppTheme.apply();
            showLogin();
        });
    }

    // ─── Login flow ───

    private void showLogin() {
        if (mainFrame == null) {
            mainFrame = buildMainFrame();
        }
        loginDialog = new LoginDialog(mainFrame);
        loginDialog.setLoginListener(this::handleLogin);
        loginDialog.setOnRegisterClick(this::showRegister);
        loginDialog.setVisible(true);
    }

    /**
     * Xử lý đăng nhập:
     * 1. Gọi KDC AS (port 8881) để xin TGT qua Kerberos
     * 2. Nếu OK → lưu TGT, show MainFrame
     * 3. Nếu lỗi → loginDialog.showError(message)
     *
     * TODO: Thay thế phần demo bằng lời gọi KDC thực:
     *   TgtRequest req = new TgtRequest(username, password);
     *   TgtResponse res = kdcClient.requestTgt(req);
     */
    private void handleLogin(String username, char[] password) {
        try {
            // ── DEMO: giả lập network delay ──
            Thread.sleep(1200);

            // ── TODO: Gọi KDC AS thực ──
            // KdcAsClient kdcAs = new KdcAsClient("localhost", 8881);
            // TgtResponse tgt = kdcAs.authenticate(username, password);
            // sessionManager.storeTgt(tgt);

            // Xóa mảng password ngay sau khi dùng (bắt buộc theo Constraints.md)
            java.util.Arrays.fill(password, '\0');

            currentUsername = username;

            SwingUtilities.invokeLater(() -> {
                loginDialog.loginSuccess();
                showMainFrame(username);
            });

        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                loginDialog.showError("Đăng nhập thất bại: " + ex.getMessage()));
        }
    }

    // ─── Register flow ───

    private void showRegister() {
        registerPanel = new RegisterPanel(new RegisterPanel.RegisterListener() {
            @Override public void onRegisterSuccess(String username) {
                registerDialog.dispose();
                showLogin();
            }
            @Override public void onNavigateLogin() {
                registerDialog.dispose();
                showLogin();
            }
            @Override public void onAuthError(String message) {}
        });

        registerDialog = new JDialog(mainFrame, "Đăng ký tài khoản", true);
        registerDialog.setContentPane(registerPanel);
        registerDialog.setSize(460, 580);
        registerDialog.setLocationRelativeTo(mainFrame);
        registerDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        registerDialog.setVisible(true);
    }

    // handleRegister has been removed because it is deprecated

    // ─── Main frame ───

    private MainFrame buildMainFrame() {
        MainFrame frame = new MainFrame("");

        frame.setContactSelectListener((contactId, contactName, colorIndex) -> {
            currentContactId = contactId;
            onContactSelected(contactId, contactName, colorIndex);
        });

        frame.getChatPanel().setSendListener(message -> handleSend(message));
        frame.setOnLogout(this::handleLogout);

        // Demo contacts
        frame.setContacts(List.of(
            new MainFrame.ContactItem("giahien", "Gia Hiển (KDC)",   1, "Session key đã được cấp ✓", "09:41", 2, true),
            new MainFrame.ContactItem("chibee",  "Chị Bee (CA)",     2, "Cert đã verify xong rồi nhé", "08:55", 0, true),
            new MainFrame.ContactItem("phutho",  "Phú Thọ (Chat Srv)", 3, "Port 8883 OK", "Hôm qua", 0, false),
            new MainFrame.ContactItem("trucngoc","Trúc Ngọc",         0, "UI mới trông ổn chưa ạ?", "Hôm qua", 0, false)
        ));

        return frame;
    }

    private void showMainFrame(String username) {
        mainFrame.setCurrentUser(username, username, 0);
        mainFrame.setVisible(true);
    }

    // ─── Chat ───

    /**
     * Gọi khi user chọn contact: load lịch sử chat từ server/DB.
     * TODO: Gọi ChatServer qua ServiceTicket để lấy lịch sử
     */
    private void onContactSelected(String contactId, String contactName, int colorIndex) {
        // Demo: thêm vài tin nhắn mẫu
        ChatPanel cp = mainFrame.getChatPanel();
        if (contactId.equals("giahien")) {
            cp.addMessage(new ChatPanel.ChatMessage(contactName,
                "Mình đã fix xong phần TGS, bạn test thử đi nhé.", false, LocalTime.of(9, 38)));
            cp.addMessage(new ChatPanel.ChatMessage("Tôi",
                "OK mình build lại rồi test ngay.", true, LocalTime.of(9, 39)));
            cp.addMessage(new ChatPanel.ChatMessage(contactName,
                "Session key đã được cấp thành công, check log nhé!", false, LocalTime.of(9, 41)));
        }
    }

    /**
     * Gửi tin nhắn:
     * 1. Lấy Service Ticket từ KDC TGS (nếu chưa có)
     * 2. Mã hoá bằng AES-256-GCM (session key từ Kyber ML-KEM)
     * 3. Gói vào PacketFrame và gửi qua TCP đến ChatServer
     *
     * TODO:
     *   byte[] encrypted = AesGcmCipher.encrypt(sessionKey, message.getBytes());
     *   PacketFrame frame = PacketFrame.of(MessageType.CHAT_MSG, encrypted);
     *   chatServerSocket.send(frame);
     */
    private void handleSend(String message) {
        // Ghi audit entry
        mainFrame.getAuditTable().addEntry(new AuditLogTable.AuditEntry(
            LocalDateTime.now(),
            AuditLogTable.EventType.MESSAGE,
            currentUsername + " → " + currentContactId,
            "AES-256-GCM encrypted",
            true
        ));
    }

    /**
     * Callback khi nhận tin nhắn từ ChatServer (gọi từ network thread).
     * TODO: Gọi từ ChatServerListener khi nhận PacketFrame loại CHAT_MSG
     */
    public void onMessageReceived(String senderId, String senderName, int colorIndex, String plaintext) {
        ChatPanel.ChatMessage msg = new ChatPanel.ChatMessage(
            senderName, plaintext, false, LocalTime.now()
        );
        mainFrame.getChatPanel().addMessage(msg);
        mainFrame.getAuditTable().addEntry(new AuditLogTable.AuditEntry(
            LocalDateTime.now(),
            AuditLogTable.EventType.MESSAGE,
            senderName,
            "Đã giải mã OK",
            true
        ));
    }

    // ─── Logout ───
    private void handleLogout() {
        int confirm = JOptionPane.showConfirmDialog(mainFrame,
            "Bạn có chắc chắn muốn đăng xuất?", "Xác nhận đăng xuất",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            // TODO: Huỷ TGT, đóng kết nối socket
            mainFrame.setVisible(false);
            currentUsername = "";
            showLogin();
        }
    }
}