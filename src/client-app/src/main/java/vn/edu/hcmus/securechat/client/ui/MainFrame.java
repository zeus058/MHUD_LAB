package vn.edu.hcmus.securechat.client.ui;

import vn.edu.hcmus.securechat.client.ui.components.*;
import vn.edu.hcmus.securechat.client.ui.components.ChatPanel;
import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MainFrame — Cửa sổ chính của SecureChat.
 * Layout: Sidebar (danh sách liên hệ) | ChatPanel | [optional: AuditLogTable tab]
 *
 * Kết nối UIController:
 * frame.setContactSelectListener((contactId) -> controller.loadChat(contactId));
 * frame.setChatPanel(chatPanel);
 */
public class MainFrame extends JFrame {

    // === UI ===
    private JSplitPane         mainSplit;
    private JPanel             sidebar;
    private ChatPanel          chatPanel;
    private AuditLogTable      auditTable;
    private JPanel             contactListPanel;
    private JTextField         tfSearch;
    private JLabel             lblCurrentUser;
    private AvatarLabel        selfAvatar;

    // === State ===
    private String currentUserId   = "";
    private String currentUserName = "";
    private int    selfColorIndex  = 0;

    // === Callbacks ===
    private ContactSelectListener contactSelectListener;
    private Runnable onLogout;

    public interface ContactSelectListener {
        void onSelect(String contactId, String contactName, int colorIndex);
    }

    public record ContactItem(String id, String displayName, int colorIndex,
                              String preview, String time, int unread, boolean online) {}

    // === Constructor mới nhận tham số username truyền vào từ ClientApp ===
    public MainFrame(String username) {
        super("SecureChat E2EE");
        
        // Thiết lập thông tin người dùng hiện tại
        this.currentUserName = username;
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setPreferredSize(new Dimension(1100, 680));
        buildUI();
        pack();
        setLocationRelativeTo(null);
        
        // Cập nhật tên hiển thị lên giao diện sau khi UI được dựng xong
        setCurrentUser(username, username, 0);
    }

    private void buildUI() {
        setContentPane(buildRoot());
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.SURFACE);

        // Sidebar
        sidebar = buildSidebar();

        // Chat area (default: empty state)
        chatPanel = new ChatPanel();

        // Split pane
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, buildRightPane());
        mainSplit.setDividerLocation(270);
        mainSplit.setDividerSize(1);
        mainSplit.setBorder(null);
        mainSplit.setContinuousLayout(true);
        
        // Remove default border & gỡ lỗi BasicSplitPaneDivider
        mainSplit.setUI(new BasicSplitPaneUI() {
            @Override 
            public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider d = super.createDefaultDivider();
                d.setBackground(AppTheme.BORDER);
                return d;
            }
        });

        root.add(mainSplit, BorderLayout.CENTER);
        return root;
    }

    // ─── Sidebar ───
    private JPanel buildSidebar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.SURFACE);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.BORDER));

        p.add(buildSidebarHeader(), BorderLayout.NORTH);
        p.add(buildContactList(),   BorderLayout.CENTER);
        p.add(buildSidebarFooter(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildSidebarHeader() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(AppTheme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
            new EmptyBorder(14, 14, 10, 14)
        ));

        // Title row
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = new JLabel("SecureChat");
        title.setFont(AppTheme.FONT_MEDIUM.deriveFont(15f));
        title.setForeground(AppTheme.TEXT_PRIMARY);
        titleRow.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(buildSmallIconBtn("✏", "Cuộc trò chuyện mới", null));
        actions.add(buildSmallIconBtn("⚙", "Cài đặt",             null));
        titleRow.add(actions, BorderLayout.EAST);

        // Search
        tfSearch = new JTextField();
        tfSearch.setFont(AppTheme.FONT_REGULAR.deriveFont(13f));
        tfSearch.putClientProperty("JTextField.placeholderText", "🔍  Tìm kiếm...");
        tfSearch.setPreferredSize(new Dimension(0, 32));

        p.add(titleRow, BorderLayout.NORTH);
        p.add(tfSearch, BorderLayout.SOUTH);
        return p;
    }

    private JScrollPane buildContactList() {
        contactListPanel = new JPanel();
        contactListPanel.setLayout(new BoxLayout(contactListPanel, BoxLayout.Y_AXIS));
        contactListPanel.setBackground(AppTheme.SURFACE);

        JScrollPane sp = new JScrollPane(contactListPanel);
        sp.setBorder(null);
        sp.setBackground(AppTheme.SURFACE);
        sp.getViewport().setBackground(AppTheme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JPanel buildSidebarFooter() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(AppTheme.SURFACE_2);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
            new EmptyBorder(10, 14, 10, 14)
        ));

        selfAvatar = new AvatarLabel("", selfColorIndex, 32);
        p.add(selfAvatar, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        lblCurrentUser = new JLabel(currentUserName.isEmpty() ? "—" : currentUserName);
        lblCurrentUser.setFont(AppTheme.FONT_MEDIUM.deriveFont(13f));
        lblCurrentUser.setForeground(AppTheme.TEXT_PRIMARY);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        statusRow.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setFont(AppTheme.FONT_SMALL.deriveFont(8f));
        dot.setForeground(AppTheme.SUCCESS);
        JLabel online = new JLabel("Online");
        online.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        online.setForeground(AppTheme.TEXT_SECONDARY);
        statusRow.add(dot); statusRow.add(online);
        info.add(lblCurrentUser);
        info.add(statusRow);
        p.add(info, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setOpaque(false);
        btns.add(buildSmallIconBtn("🛡", "Audit Log", () -> showAuditLog()));
        btns.add(buildSmallIconBtn("🚪", "Đăng xuất", () -> { if (onLogout != null) onLogout.run(); }));
        p.add(btns, BorderLayout.EAST);
        return p;
    }

    // ─── Right pane: tabs (Chat | Audit) ───
    private JTabbedPane buildRightPane() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(AppTheme.FONT_SMALL.deriveFont(13f));
        tabs.addTab("💬  Chat",      chatPanel);

        auditTable = new AuditLogTable();
        tabs.addTab("🛡  Audit Log", auditTable);

        // Seed demo audit entries
        seedDemoAudit();
        return tabs;
    }

    private void showAuditLog() {
        Component right = mainSplit.getRightComponent();
        if (right instanceof JTabbedPane tabs) {
            tabs.setSelectedIndex(1);
        }
    }

    // ─── Contact Item Row ───
    public void setContacts(List<ContactItem> contacts) {
        SwingUtilities.invokeLater(() -> {
            contactListPanel.removeAll();
            for (ContactItem c : contacts) {
                contactListPanel.add(buildContactRow(c));
            }
            contactListPanel.revalidate();
            contactListPanel.repaint();
        });
    }

    public void addContact(ContactItem contact) {
        SwingUtilities.invokeLater(() -> {
            contactListPanel.add(buildContactRow(contact), 0);
            contactListPanel.revalidate();
            contactListPanel.repaint();
        });
    }

    private JPanel buildContactRow(ContactItem contact) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(AppTheme.SURFACE);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
            new EmptyBorder(10, 14, 10, 14)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Avatar
        AvatarLabel av = new AvatarLabel(contact.displayName(), contact.colorIndex(), 38);

        // Info
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        nameRow.setOpaque(false);
        JLabel name = new JLabel(contact.displayName());
        name.setFont(AppTheme.FONT_MEDIUM.deriveFont(13f));
        name.setForeground(AppTheme.TEXT_PRIMARY);
        if (contact.online()) {
            JLabel onlineDot = new JLabel("●");
            onlineDot.setFont(AppTheme.FONT_SMALL.deriveFont(8f));
            onlineDot.setForeground(AppTheme.SUCCESS);
            nameRow.add(onlineDot);
        }
        nameRow.add(name);

        JLabel preview = new JLabel(contact.preview());
        preview.setFont(AppTheme.FONT_SMALL.deriveFont(12f));
        preview.setForeground(AppTheme.TEXT_SECONDARY);
        info.add(nameRow);
        info.add(preview);

        // Meta (time + badge)
        JPanel meta = new JPanel();
        meta.setOpaque(false);
        meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
        JLabel time = new JLabel(contact.time());
        time.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        time.setForeground(AppTheme.TEXT_HINT);
        time.setAlignmentX(Component.RIGHT_ALIGNMENT);
        meta.add(time);
        if (contact.unread() > 0) {
            JLabel badge = new JLabel(String.valueOf(contact.unread())) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppTheme.PRIMARY);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            badge.setFont(AppTheme.FONT_SMALL.deriveFont(Font.BOLD, 10f));
            badge.setForeground(Color.WHITE);
            badge.setOpaque(false);
            badge.setBorder(new EmptyBorder(1, 6, 1, 6));
            badge.setAlignmentX(Component.RIGHT_ALIGNMENT);
            meta.add(Box.createVerticalStrut(3));
            meta.add(badge);
        }

        row.add(av,   BorderLayout.WEST);
        row.add(info, BorderLayout.CENTER);
        row.add(meta, BorderLayout.EAST);

        // Hover + click
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(AppTheme.SURFACE_2); }
            @Override public void mouseExited(MouseEvent e)  { row.setBackground(AppTheme.SURFACE); }
            @Override public void mouseClicked(MouseEvent e) {
                chatPanel.setContact(contact.displayName(), contact.colorIndex(), contact.online());
                chatPanel.clearMessages();
                chatPanel.addSystemMessage("Phiên mã hoá bắt đầu — Kyber ML-KEM + ECDHE");
                chatPanel.requestInputFocus();
                if (contactSelectListener != null)
                    contactSelectListener.onSelect(contact.id(), contact.displayName(), contact.colorIndex());
            }
        });

        return row;
    }

    // ─── User info ───
    public void setCurrentUser(String userId, String displayName, int colorIndex) {
        this.currentUserId   = userId;
        this.currentUserName = displayName;
        this.selfColorIndex  = colorIndex;
        SwingUtilities.invokeLater(() -> {
            if (lblCurrentUser != null) lblCurrentUser.setText(displayName);
            if (selfAvatar != null) selfAvatar.update(displayName, colorIndex);
        });
    }

    // ─── Demo seed ───
    private void seedDemoAudit() {
        auditTable.addEntries(List.of(
            new AuditLogTable.AuditEntry(LocalDateTime.now().minusMinutes(5),
                AuditLogTable.EventType.SESSION_KEY, "GiaHien → TrucNgoc", "Kyber ML-KEM handshake", true),
            new AuditLogTable.AuditEntry(LocalDateTime.now().minusMinutes(8),
                AuditLogTable.EventType.MESSAGE, "GiaHien", "AES-256-GCM OK", true),
            new AuditLogTable.AuditEntry(LocalDateTime.now().minusMinutes(16),
                AuditLogTable.EventType.LOGIN, "TrucNgoc", "TGT cấp OK", true),
            new AuditLogTable.AuditEntry(LocalDateTime.now().minusMinutes(30),
                AuditLogTable.EventType.WARNING, "Unknown@192.168.1.5", "Replay attack detected", false),
            new AuditLogTable.AuditEntry(LocalDateTime.now().minusHours(1),
                AuditLogTable.EventType.LOGIN, "ChiBee", "TGT cấp OK", true),
            new AuditLogTable.AuditEntry(LocalDateTime.now().minusHours(2),
                AuditLogTable.EventType.KEY_EXCHANGE, "TrucNgoc ↔ GiaHien", "ECDHE + Kyber", true)
        ));
    }

    // ─── Helpers ───
    private JButton buildSmallIconBtn(String icon, String tooltip, Runnable onClick) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (onClick != null) btn.addActionListener(e -> onClick.run());
        return btn;
    }

    public ChatPanel     getChatPanel()    { return chatPanel; }
    public AuditLogTable getAuditTable()   { return auditTable; }

    public void setContactSelectListener(ContactSelectListener l) { this.contactSelectListener = l; }
    public void setOnLogout(Runnable r)                           { this.onLogout = r; }
}