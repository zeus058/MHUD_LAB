package vn.edu.hcmus.securechat.client.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.client.network.FileTransferManager;
import vn.edu.hcmus.securechat.client.network.GroupManager;
import vn.edu.hcmus.securechat.client.network.WebRtcManager;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.CallSignalDto;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.E2eeInitMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.FileChunkDto;
import vn.edu.hcmus.securechat.common.protocol.dto.FileMetadataDto;
import vn.edu.hcmus.securechat.common.protocol.dto.GroupMessageDto;
import vn.edu.hcmus.securechat.common.protocol.dto.PreKeyBundle;
import vn.edu.hcmus.securechat.common.protocol.dto.UserListEntry;

/**
 * Màn hình chat chính: hội thoại, nội dung tin nhắn và luồng hoạt động bảo mật.
 */
public class ChatPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ChatPanel.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LAST_SEEN_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CHAT_LIST_WIDTH = 260;
    private static final int RIGHT_RAIL_WIDTH = 320;

    public interface ChatListener {
        void onLogout();
    }

    private final ChatListener listener;
    private final vn.edu.hcmus.securechat.client.db.LocalDatabase localDb;
    private final String username;
    private final E2eeCryptoService e2ee;
    private final ActivityFlowPanel activityPanel;

    private JPanel messageContainer;
    private JScrollPane messageScroll;
    private JTextField messageInput;
    private JButton attachBtn;
    private JButton sendBtn;
    private JTextField contactField;
    private AvatarBadge peerAvatar;
    private JLabel peerTitle;
    private JLabel peerStatus;
    private JPanel hintWrap;
    private String selectedPeer;
    private DefaultListModel<ConversationItem> userListModel;
    private JList<ConversationItem> userList;
    private boolean updatingUserList;
    private List<ConversationItem> lastUserList = new java.util.ArrayList<>();
    private int messageCount = 0;

    private boolean rightRailOpen = false;
    private JPanel rightRail;
    private JButton connHeaderBtn;

    // === Fields cuộc gọi và nút header ===
    private javax.swing.JDialog activeCallDialog;
    private JLabel activeCallStateLbl;
    private JButton phoneBtn;
    private JButton videoBtn;
    private JButton deleteBtn;

    // === Managers cho tính năng mới ===
    private final GroupManager groupManager;
    private final FileTransferManager fileTransferManager;
    private final WebRtcManager webRtcManager;
    /** Thanh điều khiển cuộc gọi hiển thị trực tiếp trên giao diện (không phải dialog popup). */
    private JPanel callControlBar;
    /** Tín hiệu cuộc gọi đến chưa trả lời (pending inbound). */
    private CallSignalDto pendingInboundCall;

    public ChatPanel(String username, E2eeCryptoService e2ee,
            vn.edu.hcmus.securechat.client.db.LocalDatabase localDb, ChatListener listener) {
        this.username = username;
        this.e2ee = e2ee;
        this.localDb = localDb;
        this.listener = listener;

        // 1. Khởi tạo các panels log hoạt động bảo mật
        this.activityPanel = new ActivityFlowPanel("Recent activity", "");
        this.activityPanel.setImportantOnly(true);
        this.activityPanel.setHealthVisible(false);

        // 2. Liên kết luồng log mã hóa
        this.e2ee.setActivitySink((title, body, tone) -> {
            ActivityFlowPanel.Tone t = ActivityFlowPanel.Tone.valueOf(tone.name());
            activityPanel.addEvent(title, body, t);
        });

        // 3. Khởi tạo 3 Managers
        this.groupManager = new GroupManager(username, e2ee, localDb);
        this.fileTransferManager = new FileTransferManager(username, e2ee);
        this.fileTransferManager.setGroupManager(this.groupManager);
        this.webRtcManager = new WebRtcManager(username, e2ee);

        // Wire FileTransfer callbacks (chạy trên background thread)
        this.fileTransferManager.setOnFileReceived((file, fileName, senderId, groupId) ->
            SwingUtilities.invokeLater(() -> showFileReceivedNotification(file, fileName, senderId, groupId)));
        this.fileTransferManager.setOnProgress((transferId, pct) ->
            SwingUtilities.invokeLater(() -> flow("File transfer", transferId + " — " + pct + "%",
                    pct == 100 ? ActivityFlowPanel.Tone.SUCCESS : ActivityFlowPanel.Tone.INFO)));

        // Wire WebRTC callbacks
        this.webRtcManager.setOnStateChanged(state ->
            SwingUtilities.invokeLater(() -> onCallStateChanged(state)));
        this.webRtcManager.setOnCallEnded(peerId ->
            SwingUtilities.invokeLater(() -> onCallEndedByPeer(peerId)));
        this.webRtcManager.setOnSignalReceived(signal ->
            SwingUtilities.invokeLater(() -> handleIncomingCallSignalUI(signal)));

        setLayout(new BorderLayout());
        setBackground(UIConstants.DEEP_CARBON);

        // 3. Thiết lập Sidebar Panel dọc trái và Workspace chính
        add(new SidebarPanel(), BorderLayout.WEST);

        // Chat Workspace
        JPanel chatWorkspace = new JPanel(new BorderLayout());
        chatWorkspace.setOpaque(false);
        chatWorkspace.add(buildSidebar(), BorderLayout.WEST);
        chatWorkspace.add(buildChatArea(), BorderLayout.CENTER);
        
        rightRail = buildRightRail();
        chatWorkspace.add(rightRail, BorderLayout.EAST);

        add(chatWorkspace, BorderLayout.CENTER);

        activityPanel.seed(new String[][] {
                {"Pre-Key đã upload", "Signed pre-key và one-time pre-key đã sẵn sàng cho peer offline.", "SUCCESS"},
                {"Phiên truy cập đã mở", "ST, authenticator và Chat handshake đã được xác thực.", "SUCCESS"}
        });

        startReceiverThread();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(UIConstants.DEEP_CARBON);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setOpaque(true);
        header.setBackground(UIConstants.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.BORDER_SUBTLE),
                new EmptyBorder(14, UIConstants.PADDING, 14, UIConstants.PADDING)));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel appName = UiStyles.appTitleLabel("SecureChat");
        JLabel userLine = UiStyles.mutedLabel("@" + username);
        userLine.setForeground(UIConstants.TEXT_SILVER);
        titles.add(appName);
        titles.add(Box.createVerticalStrut(2));
        titles.add(userLine);
        header.add(titles, BorderLayout.WEST);

        JButton logout = UiStyles.ghostButton("Đăng xuất");
        logout.setPreferredSize(new Dimension(112, 38));
        logout.addActionListener(e -> listener.onLogout());
        header.add(logout, BorderLayout.EAST);
        return header;
    }

    private JPanel buildRightRail() {
        return new ConnectionInfoPanel();
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UIConstants.GLASS_SIDEBAR);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        sidebar.setOpaque(false);
        sidebar.setBackground(UIConstants.GLASS_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(CHAT_LIST_WIDTH, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIConstants.GLASS_BORDER));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(20, 16, 12, 16));

        JLabel startLbl = UiStyles.headingLabel("Messages");
        startLbl.setFont(UIConstants.FONT_TITLE.deriveFont(Font.BOLD, 22f));
        startLbl.setAlignmentX(LEFT_ALIGNMENT);
        top.add(startLbl);
        top.add(Box.createVerticalStrut(16));

        // Beautiful figma-style search bar panel
        JPanel openRow = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.INPUT_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_LG, UIConstants.CORNER_RADIUS_LG);
                g2.setColor(UIConstants.GLASS_BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, UIConstants.CORNER_RADIUS_LG, UIConstants.CORNER_RADIUS_LG);
                g2.dispose();
            }
        };
        openRow.setOpaque(false);
        openRow.setAlignmentX(LEFT_ALIGNMENT);
        openRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        openRow.setPreferredSize(new Dimension(0, 46));
        openRow.setBorder(new EmptyBorder(5, 12, 5, 12));

        // Mini magnifying glass icon on left
        JPanel searchIconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.8f));
                int size = 12;
                int ix = (getWidth() - size) / 2;
                int iy = (getHeight() - size) / 2;
                g2.drawOval(ix, iy, 8, 8);
                g2.drawLine(ix + 7, iy + 7, ix + 11, iy + 11);
                g2.dispose();
            }
        };
        searchIconPanel.setOpaque(false);
        searchIconPanel.setPreferredSize(new Dimension(20, 0));
        openRow.add(searchIconPanel, BorderLayout.WEST);

        contactField = new EmbeddedPlaceholderField("Search conversations");
        contactField.setFont(UIConstants.FONT_BODY.deriveFont(14f));
        contactField.setForeground(UIConstants.TEXT_WHITE);
        contactField.setCaretColor(UIConstants.SECURE_TEAL);
        contactField.setOpaque(false);
        contactField.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
        contactField.setToolTipText("Type a username and press Enter to open a conversation");
        contactField.addActionListener(e -> openManualConversation());
        openRow.add(contactField, BorderLayout.CENTER);

        JButton open = new JButton("+") {
            private boolean hover = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? UIConstants.SECURE_TEAL.brighter() : UIConstants.SECURE_TEAL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(UIConstants.FONT_HEADING.deriveFont(Font.BOLD, 14f));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth("+")) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent() - 1;
                g2.drawString("+", x, y);
                g2.dispose();
            }
        };
        open.setPreferredSize(new Dimension(0, 0));
        open.setContentAreaFilled(false);
        open.setBorderPainted(false);
        open.setFocusPainted(false);
        open.setVisible(false);
        open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        open.addActionListener(e -> openManualConversation());
        
        JPanel eastWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 1));
        eastWrapper.setOpaque(false);
        eastWrapper.add(open);
        openRow.add(eastWrapper, BorderLayout.EAST);
        
        top.add(openRow);
        sidebar.add(top, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userListModel.addElement(ConversationItem.placeholder("Đang tải danh sách..."));

        userList = new JList<>(userListModel);
        userList.setBackground(new Color(0, 0, 0, 0));
        userList.setOpaque(false);
        userList.setForeground(UIConstants.TEXT_SILVER);
        userList.setFont(UIConstants.FONT_BODY);
        userList.setSelectionBackground(UIConstants.ACCENT_DIM);
        userList.setSelectionForeground(UIConstants.TEXT_WHITE);
        userList.setFixedCellHeight(72);
        userList.setBorder(new EmptyBorder(0, 8, 10, 8));
        userList.setCellRenderer(new UserCellRenderer());
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (updatingUserList || e.getValueIsAdjusting()) {
                return;
            }
            ConversationItem sel = userList.getSelectedValue();
            if (sel != null && sel.isSelectable(username)) {
                selectPeer(sel);
            }
        });

        userList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int index = userList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        userList.setSelectedIndex(index);
                        ConversationItem sel = userList.getSelectedValue();
                        if (sel != null && sel.isGroup && sel.isSelectable(username)) {
                            javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                            javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Xóa nhóm");
                            deleteItem.addActionListener(ae -> {
                                int confirm = javax.swing.JOptionPane.showConfirmDialog(
                                    ChatPanel.this,
                                    "Bạn có chắc chắn muốn xóa nhóm \"" + sel.displayName() + "\" không?\nLịch sử trò chuyện của nhóm cũng sẽ bị xóa.",
                                    "Xác nhận xóa nhóm",
                                    javax.swing.JOptionPane.YES_NO_OPTION,
                                    javax.swing.JOptionPane.WARNING_MESSAGE
                                );
                                if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                                    groupManager.removeGroup(sel.userId);
                                    if (sel.userId.equals(selectedPeer)) {
                                        clearActiveChat();
                                    }
                                    applyUserList(lastUserList);
                                }
                            });
                            popup.add(deleteItem);
                            popup.show(userList, e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane userScroll = UiStyles.styledScrollPane(userList);
        userScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        userScroll.getViewport().setOpaque(false);
        userScroll.setBackground(new Color(0, 0, 0, 0));
        userScroll.setOpaque(false);
        sidebar.add(userScroll, BorderLayout.CENTER);

        // === Nút Tạo nhóm ở cuối sidebar ===
        JPanel groupBtnPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UIConstants.GLASS_BORDER);
                g2.drawLine(16, 0, getWidth() - 16, 0);
                g2.dispose();
            }
        };
        groupBtnPanel.setOpaque(false);
        groupBtnPanel.setBorder(new EmptyBorder(2, 12, 12, 12));

        JButton createGroupBtn = new JButton("✜ Tạo nhóm") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 161, 156, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(UIConstants.SECURE_TEAL);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        createGroupBtn.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 13f));
        createGroupBtn.setForeground(UIConstants.SECURE_TEAL);
        createGroupBtn.setContentAreaFilled(false);
        createGroupBtn.setBorderPainted(false);
        createGroupBtn.setFocusPainted(false);
        createGroupBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createGroupBtn.setPreferredSize(new Dimension(CHAT_LIST_WIDTH - 24, 38));
        createGroupBtn.setToolTipText("Tạo nhóm chat mới");
        createGroupBtn.addActionListener(e -> showCreateGroupDialog());
        groupBtnPanel.add(createGroupBtn);
        sidebar.add(groupBtnPanel, BorderLayout.SOUTH);

        return sidebar;
    }

    private JPanel buildChatArea() {
        JPanel chat = new JPanel(new BorderLayout());
        chat.setOpaque(true);
        chat.setBackground(UIConstants.DEEP_CARBON);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(true);
        topBar.setBackground(UIConstants.SURFACE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.GLASS_BORDER),
                new EmptyBorder(12, 20, 12, 20)));

        JPanel peerInfo = new JPanel(new BorderLayout(12, 0));
        peerInfo.setOpaque(false);
        peerAvatar = new AvatarBadge("", Color.decode("#5B54F6"), UIConstants.SECURE_TEAL);
        peerAvatar.setPreferredSize(new Dimension(38, 38));
        peerInfo.add(peerAvatar, BorderLayout.WEST);

        JPanel peerBlock = new JPanel();
        peerBlock.setOpaque(false);
        peerBlock.setLayout(new BoxLayout(peerBlock, BoxLayout.Y_AXIS));
        peerTitle = UiStyles.appTitleLabel("Select a conversation");
        peerTitle.setFont(UIConstants.FONT_HEADING.deriveFont(Font.BOLD, 17f));
        peerStatus = UiStyles.mutedLabel("Messages unlock after you choose a contact.");
        peerStatus.setForeground(UIConstants.TEXT_SILVER);
        peerBlock.add(peerTitle);
        peerBlock.add(Box.createVerticalStrut(3));
        peerBlock.add(peerStatus);
        peerInfo.add(peerBlock, BorderLayout.CENTER);
        topBar.add(peerInfo, BorderLayout.CENTER);

        // Header controls (Phone, Video, More, Connection)
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionsRow.setOpaque(false);

        phoneBtn = createHeaderButton("phone");
        phoneBtn.setToolTipText("Gọi thoại");
        phoneBtn.setEnabled(false);
        phoneBtn.addActionListener(e -> startCallWithPeer(WebRtcManager.MediaType.AUDIO));
        actionsRow.add(phoneBtn);

        videoBtn = createHeaderButton("video");
        videoBtn.setToolTipText("Gọi video");
        videoBtn.setEnabled(false);
        videoBtn.addActionListener(e -> startCallWithPeer(WebRtcManager.MediaType.VIDEO));
        actionsRow.add(videoBtn);

        deleteBtn = createHeaderButton("delete");
        deleteBtn.setToolTipText("Xóa nhóm");
        deleteBtn.setEnabled(false);
        deleteBtn.setVisible(false);
        deleteBtn.addActionListener(e -> deleteActiveGroup());
        actionsRow.add(deleteBtn);



        connHeaderBtn = new JButton("Connection") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean open = rightRailOpen;
                
                g2.setColor(open ? UIConstants.ACCENT_DIM : new Color(0, 0, 0, 0));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                g2.dispose();

                // Call super paint component to draw the text/foreground
                super.paintComponent(g);

                // Draw custom border and icon over it
                g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.SECURE_TEAL);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);

                // Shield icon on left
                g2.setColor(UIConstants.SECURE_TEAL);
                g2.setStroke(new BasicStroke(1.5f));
                int ix = 12;
                int iy = (getHeight() - 12) / 2;
                Path2D.Double shield = new Path2D.Double();
                int wSize = 12;
                shield.moveTo(ix + wSize / 2.0, iy);
                shield.lineTo(ix + wSize - 1, iy + 1.5);
                shield.lineTo(ix + wSize - 1, iy + wSize / 2.0);
                shield.quadTo(ix + wSize - 1, iy + wSize * 0.75, ix + wSize / 2.0, iy + wSize - 0.5);
                shield.quadTo(ix + 1, iy + wSize * 0.75, ix + 1, iy + wSize / 2.0);
                shield.lineTo(ix + 1, iy + 1.5);
                shield.closePath();
                g2.draw(shield);
                g2.drawLine(ix + wSize / 2 - 2, iy + wSize / 2, ix + wSize / 2, iy + wSize / 2 + 2);
                g2.drawLine(ix + wSize / 2, iy + wSize / 2 + 2, ix + wSize / 2 + 3, iy + wSize / 2 - 2);

                g2.dispose();
            }
        };
        connHeaderBtn.setFont(UIConstants.FONT_HEADING.deriveFont(12f));
        connHeaderBtn.setForeground(UIConstants.SECURE_TEAL);
        connHeaderBtn.setBorder(new EmptyBorder(8, 28, 8, 14));
        connHeaderBtn.setContentAreaFilled(false);
        connHeaderBtn.setFocusPainted(false);
        connHeaderBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        connHeaderBtn.addActionListener(e -> {
            rightRailOpen = !rightRailOpen;
            rightRail.setVisible(rightRailOpen);
            connHeaderBtn.repaint();
            revalidate();
            repaint();
        });
        // actionsRow.add(connHeaderBtn);

        topBar.add(actionsRow, BorderLayout.EAST);

        hintWrap = new JPanel();
        hintWrap.setVisible(false);

        // Container panel bọc topBar và callControlBar theo trục dọc
        JPanel northWrapper = new JPanel();
        northWrapper.setLayout(new BoxLayout(northWrapper, BoxLayout.Y_AXIS));
        northWrapper.setOpaque(false);
        northWrapper.add(topBar);

        callControlBar = new JPanel(new BorderLayout());
        callControlBar.setVisible(false);
        northWrapper.add(callControlBar);

        chat.add(northWrapper, BorderLayout.NORTH);

        messageContainer = new JPanel();
        messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.Y_AXIS));
        messageContainer.setBackground(UIConstants.DEEP_CARBON);
        messageContainer.setBorder(new EmptyBorder(20, 20, 16, 20));

        loadChatHistory();

        messageScroll = UiStyles.styledScrollPane(messageContainer);
        messageScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chat.add(messageScroll, BorderLayout.CENTER);
        chat.add(buildComposer(), BorderLayout.SOUTH);
        return chat;
    }

    private JButton createHeaderButton(String iconName) {
        JButton btn = new JButton() {
            private boolean hover = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (hover) {
                    g2.setColor(new Color(255, 255, 255, 12));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                }
                
                g2.setColor(hover ? UIConstants.TEXT_WHITE : UIConstants.TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.8f));
                int w = getWidth();
                int h = getHeight();
                int size = 16;
                int ix = (w - size) / 2;
                int iy = (h - size) / 2;
                if ("phone".equals(iconName)) {
                    Path2D.Double path = new Path2D.Double();
                    path.moveTo(ix + 3, iy + 1);
                    path.lineTo(ix + 6, iy + 1);
                    path.quadTo(ix + 7, iy + 4, ix + 5, iy + 6);
                    path.lineTo(ix + 9, iy + 10);
                    path.quadTo(ix + 11, iy + 8, ix + 14, iy + 9);
                    path.lineTo(ix + 14, iy + 12);
                    path.quadTo(ix + 10, iy + 15, ix + 5, iy + 10);
                    path.quadTo(ix + 0, iy + 5, ix + 3, iy + 1);
                    path.closePath();
                    g2.draw(path);
                } else if ("video".equals(iconName)) {
                    g2.drawRoundRect(ix, iy + 2, size - 6, size - 4, 3, 3);
                    int[] px = {ix + size - 4, ix + size, ix + size, ix + size - 4};
                    int[] py = {iy + 6, iy + 3, iy + size - 3, iy + size - 6};
                    g2.drawPolygon(px, py, 4);
                } else if ("more".equals(iconName)) {
                    g2.fillOval(ix + size/2 - 2, iy + 2, 4, 4);
                    g2.fillOval(ix + size/2 - 2, iy + size/2 - 2, 4, 4);
                    g2.fillOval(ix + size/2 - 2, iy + size - 6, 4, 4);
                } else if ("close".equals(iconName)) {
                    g2.drawLine(ix + 4, iy + 4, ix + size - 4, iy + size - 4);
                    g2.drawLine(ix + size - 4, iy + 4, ix + 4, iy + size - 4);
                } else if ("delete".equals(iconName)) {
                    // Draw a vector trash can icon
                    g2.drawRoundRect(ix + 6, iy + 1, 4, 2, 1, 1);
                    g2.drawLine(ix + 2, iy + 3, ix + size - 2, iy + 3);
                    g2.drawRoundRect(ix + 4, iy + 4, size - 8, size - 5, 2, 2);
                    g2.drawLine(ix + 7, iy + 7, ix + 7, iy + 11);
                    g2.drawLine(ix + 9, iy + 7, ix + 9, iy + 11);
                }
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel buildComposer() {
        JPanel composerWrapper = new JPanel(new BorderLayout());
        composerWrapper.setOpaque(false);
        composerWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.GLASS_BORDER),
                new EmptyBorder(14, 20, 14, 20)));

        JPanel pill = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.INPUT_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_LG, UIConstants.CORNER_RADIUS_LG);
                g2.setColor(messageInput.hasFocus() ? UIConstants.SECURE_TEAL : UIConstants.GLASS_BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, UIConstants.CORNER_RADIUS_LG, UIConstants.CORNER_RADIUS_LG);
                g2.dispose();
            }
        };
        pill.setOpaque(false);
        pill.setPreferredSize(new Dimension(0, 46));
        pill.setBorder(new EmptyBorder(5, 12, 5, 12));

        this.attachBtn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? UIConstants.TEXT_MUTED : new Color(148, 163, 184, 80));
                g2.setStroke(new BasicStroke(1.8f));
                
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.translate(cx, cy);
                g2.rotate(Math.toRadians(45));

                g2.drawLine(-4, -4, -4, 4);
                g2.drawArc(-4, 0, 8, 8, 180, 180);
                g2.drawLine(4, -4, 4, 4);
                g2.drawArc(-2, -7, 6, 6, 0, 180);
                g2.drawLine(-2, -4, -2, 1);
                g2.drawArc(-2, -1, 4, 4, 180, 180);
                g2.drawLine(2, 1, 2, -1);

                g2.dispose();
            }
        };
        this.attachBtn.setPreferredSize(new Dimension(36, 36));
        this.attachBtn.setContentAreaFilled(false);
        this.attachBtn.setBorderPainted(false);
        this.attachBtn.setFocusPainted(false);
        this.attachBtn.setEnabled(false);
        this.attachBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Wire attach button: mở hộp thoại chọn file
        this.attachBtn.addActionListener(e -> openFileChooser());
        pill.add(this.attachBtn, BorderLayout.WEST);

        messageInput = new EmbeddedPlaceholderField("Type a message");
        messageInput.setFont(UIConstants.FONT_BODY.deriveFont(15f));
        messageInput.setForeground(UIConstants.TEXT_WHITE);
        messageInput.setCaretColor(UIConstants.SECURE_TEAL);
        messageInput.setOpaque(false);
        messageInput.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 8));
        messageInput.setEnabled(false);
        pill.add(messageInput, BorderLayout.CENTER);

        this.sendBtn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hasText = !messageInput.getText().trim().isEmpty();
                boolean active = hasText && isEnabled();
                g2.setColor(active ? UIConstants.SECURE_TEAL : new Color(0, 161, 156, 60));
                g2.fillOval(0, 0, getWidth(), getHeight());
                
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.8f));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int[] px = {cx - 6, cx + 8, cx - 6, cx - 3};
                int[] py = {cy - 6, cy, cy + 6, cy};
                g2.drawPolygon(px, py, 4);
                g2.dispose();
            }
        };
        this.sendBtn.setPreferredSize(new Dimension(36, 36));
        this.sendBtn.setContentAreaFilled(false);
        this.sendBtn.setBorderPainted(false);
        this.sendBtn.setFocusPainted(false);
        this.sendBtn.setEnabled(false);
        this.sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.sendBtn.addActionListener(e -> sendMessage());
        messageInput.addActionListener(e -> sendMessage());
        
        // Repaint send button on typing
        messageInput.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                sendBtn.repaint();
            }
        });
        pill.add(this.sendBtn, BorderLayout.EAST);

        composerWrapper.add(pill, BorderLayout.CENTER);
        return composerWrapper;
    }

    private void openManualConversation() {
        String peer = contactField.getText().trim();
        if (peer.isEmpty()) {
            contactField.requestFocusInWindow();
            return;
        }
        if (peer.equals(username)) {
            contactField.selectAll();
            return;
        }
        ConversationItem item = findConversation(peer);
        if (item == null) {
            item = ConversationItem.manual(peer);
            addConversation(item);
        }
        contactField.setText("");
        selectPeer(item);
        updatingUserList = true;
        try {
            selectListItem(peer);
        } finally {
            updatingUserList = false;
        }
    }

    private void clearActiveChat() {
        selectedPeer = null;
        updatePeerHeader(null);
        messageInput.setText("");
        messageInput.setEnabled(false);
        if (attachBtn != null) attachBtn.setEnabled(false);
        if (sendBtn != null) sendBtn.setEnabled(false);
        if (phoneBtn != null) {
            phoneBtn.setEnabled(false);
            phoneBtn.setVisible(true);
        }
        if (videoBtn != null) {
            videoBtn.setEnabled(false);
            videoBtn.setVisible(true);
        }
        if (deleteBtn != null) {
            deleteBtn.setEnabled(false);
            deleteBtn.setVisible(false);
        }
        messageContainer.removeAll();
        messageContainer.add(Box.createVerticalGlue());
        messageContainer.revalidate();
        messageContainer.repaint();
        userList.clearSelection();
    }

    private void deleteActiveGroup() {
        if (selectedPeer == null || !selectedPeer.startsWith("group-")) {
            return;
        }
        String groupName = peerTitle.getText();
        int confirm = javax.swing.JOptionPane.showConfirmDialog(
            ChatPanel.this,
            "Bạn có chắc chắn muốn xóa nhóm \"" + groupName + "\" không?\nLịch sử trò chuyện của nhóm cũng sẽ bị xóa.",
            "Xác nhận xóa nhóm",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE
        );
        if (confirm == javax.swing.JOptionPane.YES_OPTION) {
            groupManager.removeGroup(selectedPeer);
            clearActiveChat();
            applyUserList(lastUserList);
        }
    }

    private void selectPeer(ConversationItem item) {
        selectedPeer = item.userId;
        item.unreadCount = 0;
        updatePeerHeader(item);
        messageInput.setEnabled(true);
        if (attachBtn != null) {
            attachBtn.setEnabled(true);
            attachBtn.repaint();
        }
        if (sendBtn != null) {
            sendBtn.setEnabled(true);
            sendBtn.repaint();
        }
        
        // Show/hide call and delete buttons based on conversation type
        boolean isGroup = item.isGroup;
        if (phoneBtn != null) {
            phoneBtn.setEnabled(!isGroup);
            phoneBtn.setVisible(!isGroup);
        }
        if (videoBtn != null) {
            videoBtn.setEnabled(!isGroup);
            videoBtn.setVisible(!isGroup);
        }
        if (deleteBtn != null) {
            deleteBtn.setEnabled(isGroup);
            deleteBtn.setVisible(isGroup);
        }

        loadChatHistory();
        userList.repaint();
        messageInput.requestFocusInWindow();
    }

    private void updatePeerHeader(ConversationItem item) {
        if (selectedPeer == null) {
            peerTitle.setText("Select a conversation");
            peerStatus.setText("Messages unlock after you choose a contact.");
            peerStatus.setForeground(UIConstants.TEXT_SILVER);
            if (peerAvatar != null) {
                peerAvatar.setValues("", Color.decode("#5B54F6"), UIConstants.OUTLINE);
            }
            if (hintWrap != null) {
                hintWrap.setVisible(false);
            }
            return;
        }
        if (hintWrap != null) {
            hintWrap.setVisible(true);
        }
        ConversationItem display = item == null ? ConversationItem.manual(selectedPeer) : item;
        peerTitle.setText(display.displayName());
        peerStatus.setText(display.statusText());
        if (display.online) {
            peerStatus.setForeground(UIConstants.SECURE_TEAL);
        } else {
            peerStatus.setForeground(UIConstants.TEXT_MUTED);
        }
        if (peerAvatar != null) {
            peerAvatar.setValues(display.userId, Color.decode("#5B54F6"),
                    display.online ? UIConstants.SECURE_TEAL : UIConstants.OUTLINE);
        }
    }

    private void loadChatHistory() {
        messageContainer.removeAll();
        this.messageCount = 0;
        messageContainer.add(Box.createVerticalGlue());
        if (selectedPeer != null && localDb != null) {
            java.util.List<ChatMessage> msgs = localDb.loadMessages(username, selectedPeer);
            for (ChatMessage msg : msgs) {
                boolean outgoing = msg.getSenderId().equals(username);
                String time = Instant.ofEpochSecond(msg.getSentAt())
                        .atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT);
                addMessageBubbleInternal(msg.getContent(), outgoing, time, selectedPeer.startsWith("group-") ? msg.getSenderId() : null);
                this.messageCount++;
            }
        }
        if (this.messageCount == 0) {
            messageContainer.removeAll();
            showEmptyState(selectedPeer == null
                    ? "Choose a conversation on the left to begin."
                    : "No messages yet. Send the first one below.");
        }
        scrollMessagesToEnd();
    }


    private void showEmptyState(String hint) {
        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 28));
        hintRow.setOpaque(false);
        hintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = UiStyles.mutedLabel(hint);
        lbl.setForeground(UIConstants.TEXT_MUTED);
        hintRow.add(lbl);
        messageContainer.add(hintRow);
    }

    private void scrollMessagesToEnd() {
        messageContainer.revalidate();
        messageContainer.repaint();
        if (messageScroll != null) {
            SwingUtilities.invokeLater(() -> {
                JScrollPane sp = messageScroll;
                sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
            });
        }
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (selectedPeer == null) {
            return;
        }

        messageInput.setEnabled(false);
        if (attachBtn != null) attachBtn.setEnabled(false);
        if (sendBtn != null) sendBtn.setEnabled(false);
        String time = LocalTime.now().format(TIME_FMT);
        String textToSend = text;
        String peerToSend = selectedPeer;
        messageInput.setText("");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    if (peerToSend.startsWith("group-")) {
                        groupManager.sendGroupMessage(peerToSend, textToSend);
                    } else {
                        EncryptedChatEnvelope envelope = e2ee.encryptForPeer(peerToSend, textToSend);
                        e2ee.sendFrame(PacketFrame.TYPE_CHAT_MESSAGE, JsonSerializer.toBytes(envelope));
                    }
                    return true;
                } catch (Exception ex) {
                    log.error("Gửi tin nhắn thất bại", ex);
                    flow("Gửi tin thất bại", ex.getMessage(), ActivityFlowPanel.Tone.ERROR);
                    return false;
                }
            }

            @Override
            protected void done() {
                boolean sent = false;
                try {
                    sent = Boolean.TRUE.equals(get());
                    if (sent) {
                        addMessageBubble(textToSend, true, time);
                        localDb.saveMessage(username, peerToSend, username, textToSend, Instant.now().getEpochSecond());
                    } else {
                        messageInput.setText(textToSend);
                    }
                } catch (Exception e) {
                    log.error("send done error", e);
                    messageInput.setText(textToSend);
                }
                boolean hasPeer = (selectedPeer != null);
                messageInput.setEnabled(hasPeer);
                if (attachBtn != null) attachBtn.setEnabled(hasPeer);
                if (sendBtn != null) sendBtn.setEnabled(hasPeer);
                messageInput.requestFocusInWindow();
            }
        }.execute();
    }

    private void startReceiverThread() {
        Thread t = new Thread(() -> {
            try {
                Socket sock = e2ee.getChatSocket();
                if (sock == null) {
                    return;
                }
                while (!sock.isClosed()) {
                    PacketFrame frame = PacketFrame.read(sock.getInputStream());
                    handleIncomingFrame(frame);
                }
            } catch (Exception ex) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.info("Receiver thread ended: {}", ex.getMessage());
                }
            }
        }, "chat-receiver");
        t.setDaemon(true);
        t.start();
    }

    private void handleIncomingFrame(PacketFrame frame) {
        try {
            if (frame.getType() == PacketFrame.TYPE_USER_LIST) {
                List<ConversationItem> users = parseUserList(frame.getPayload());
                this.lastUserList = users;
                SwingUtilities.invokeLater(() -> applyUserList(users));
            } else if (frame.getType() == PacketFrame.TYPE_CHAT_MESSAGE) {
                EncryptedChatEnvelope envelope = JsonSerializer.fromBytes(
                        frame.getPayload(), EncryptedChatEnvelope.class);
                ChatMessage msg = e2ee.decryptIncoming(envelope);
                String sender = msg.getSenderId();
                String text = msg.getContent();
                String time = Instant.ofEpochSecond(msg.getSentAt())
                        .atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT);
                SwingUtilities.invokeLater(() -> {
                    ConversationItem item = ensureConversation(sender, true, true);
                    if (sender.equals(selectedPeer)) {
                        addMessageBubble(text, false, time);
                        item.unreadCount = 0;
                    } else {
                        item.unreadCount++;
                    }
                    localDb.saveMessage(username, sender, sender, text, msg.getSentAt());
                    userList.repaint();
                });
            } else if (frame.getType() == PacketFrame.TYPE_PREKEY_RESPONSE) {
                PreKeyBundle bundle = JsonSerializer.fromBytes(frame.getPayload(), PreKeyBundle.class);
                e2ee.acceptPreKeyBundle(bundle);
                SwingUtilities.invokeLater(() -> markPreKeyAvailable(bundle.getOwnerId()));
            } else if (frame.getType() == PacketFrame.TYPE_E2EE_INIT) {
                E2eeInitMessage init = JsonSerializer.fromBytes(frame.getPayload(), E2eeInitMessage.class);
                e2ee.acceptE2eeInit(init);
            } else if (frame.getType() == PacketFrame.TYPE_ERROR) {
                String message = decodeError(frame);
                e2ee.handleServerError(message);
            // === Tính năng mới ===
            } else if (frame.getType() == PacketFrame.TYPE_GROUP_MESSAGE) {
                GroupMessageDto groupMsg = JsonSerializer.fromBytes(
                        frame.getPayload(), GroupMessageDto.class);
                if (groupMsg != null) {
                    int myIndex = groupMsg.getRecipientIds().indexOf(username);
                    if (myIndex >= 0 && myIndex < groupMsg.getEncryptedPayloads().size()) {
                        String payloadJson = groupMsg.getEncryptedPayloads().get(myIndex);
                        EncryptedChatEnvelope envelope = JsonSerializer.fromString(
                                payloadJson, EncryptedChatEnvelope.class);
                        ChatMessage msg = e2ee.decryptIncoming(envelope);
                        String sender = msg.getSenderId();
                        String text = msg.getContent();
                        String groupId = groupMsg.getGroupId();
                        String groupName = groupMsg.getGroupName();
                        String time = Instant.ofEpochSecond(msg.getSentAt())
                                .atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT);
                        
                        SwingUtilities.invokeLater(() -> {
                            List<String> allMembers = new ArrayList<>();
                            allMembers.add(sender);
                            if (groupMsg.getRecipientIds() != null) {
                                allMembers.addAll(groupMsg.getRecipientIds());
                            }
                            groupManager.registerGroup(groupId, groupName, allMembers);
                            ConversationItem item = findConversation(groupId);
                            if (item == null) {
                                item = ConversationItem.manual(groupId);
                                item.displayName = groupName;
                                item.online = true;
                                item.isGroup = true;
                                addConversation(item);
                            }
                            
                            if (groupId.equals(selectedPeer)) {
                                addMessageBubble(text, false, time, sender);
                                item.unreadCount = 0;
                            } else {
                                item.unreadCount++;
                            }
                            localDb.saveMessage(username, groupId, sender, text, msg.getSentAt());
                            userList.repaint();
                        });
                    }
                }
            } else if (frame.getType() == PacketFrame.TYPE_FILE_INIT) {
                FileMetadataDto meta = JsonSerializer.fromBytes(frame.getPayload(), FileMetadataDto.class);
                fileTransferManager.handleFileInit(meta);
                SwingUtilities.invokeLater(() -> flow("File đến",
                        meta.getSenderId() + " gửi \"" + meta.getFileName() + "\" ("
                        + (meta.getFileSize() / 1024) + " KB)",
                        ActivityFlowPanel.Tone.INFO));
            } else if (frame.getType() == PacketFrame.TYPE_FILE_CHUNK) {
                FileChunkDto chunk = JsonSerializer.fromBytes(frame.getPayload(), FileChunkDto.class);
                fileTransferManager.handleFileChunk(chunk);
            } else if (frame.getType() == PacketFrame.TYPE_CALL_SDP_OFFER
                    || frame.getType() == PacketFrame.TYPE_CALL_SDP_ANSWER
                    || frame.getType() == PacketFrame.TYPE_CALL_ICE_CANDIDATE) {
                // Giải mã E2EE rồi chuyển cho WebRtcManager (chỉ với các gói sdp/ice điều khiển, không với audio frame)
                CallSignalDto signal = JsonSerializer.fromBytes(frame.getPayload(), CallSignalDto.class);
                if (signal != null) {
                    if ("AUDIO_FRAME".equals(signal.getSignalType())) {
                        // Chạy trực tiếp trên receiver thread ngoài EDT để tránh blocking UI và bỏ qua giải mã Double Ratchet
                        webRtcManager.handleIncomingSignal(signal);
                    } else {
                        try {
                            // Giải mã encryptedSignal bằng Double Ratchet
                            EncryptedChatEnvelope env = JsonSerializer.fromString(
                                    signal.getEncryptedSignal(), EncryptedChatEnvelope.class);
                            ChatMessage plainSignal = e2ee.decryptIncoming(env);
                            signal.setEncryptedSignal(plainSignal.getContent());
                        } catch (Exception ex) {
                            log.warn("Failed to decrypt call signal payload", ex);
                        }
                        CallSignalDto finalSignal = signal;
                        SwingUtilities.invokeLater(() -> webRtcManager.handleIncomingSignal(finalSignal));
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi xử lý frame từ server", ex);
            flow("Không xử lý được frame", ex.getMessage(), ActivityFlowPanel.Tone.ERROR);
        }
    }

    private List<ConversationItem> parseUserList(byte[] payload) throws Exception {
        JsonNode root = JsonSerializer.getMapper().readTree(payload);
        List<ConversationItem> items = new ArrayList<>();
        if (!root.isArray()) {
            return items;
        }
        for (JsonNode node : root) {
            ConversationItem item;
            if (node.isTextual()) {
                item = ConversationItem.legacy(node.asText());
            } else {
                UserListEntry entry = JsonSerializer.getMapper().treeToValue(node, UserListEntry.class);
                item = ConversationItem.from(entry);
            }
            if (item != null && item.isSelectable(username)) {
                item.unreadCount = unreadCountFor(item.userId);
                items.add(item);
            }
        }
        items.sort(Comparator
                .comparing(ConversationItem::isOnline, Comparator.reverseOrder())
                .thenComparing(ConversationItem::isPreKeyAvailable, Comparator.reverseOrder())
                .thenComparing(ConversationItem::displayName, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private void applyUserList(List<ConversationItem> users) {
        updatingUserList = true;
        try {
            userListModel.clear();
            if (groupManager != null) {
                for (GroupManager.GroupInfo g : groupManager.listGroups()) {
                    ConversationItem item = ConversationItem.manual(g.groupId());
                    item.displayName = g.groupName();
                    item.online = true;
                    item.isGroup = true;
                    userListModel.addElement(item);
                }
            }
            for (ConversationItem item : users) {
                userListModel.addElement(item);
            }
            if (selectedPeer != null && findIn(users, selectedPeer) == null && !selectedPeer.startsWith("group-")) {
                userListModel.addElement(ConversationItem.manual(selectedPeer));
            }
            if (userListModel.isEmpty()) {
                userListModel.addElement(ConversationItem.placeholder("Chưa có hội thoại khả dụng"));
            }
            selectListItem(selectedPeer);
        } finally {
            updatingUserList = false;
        }
        if (selectedPeer != null) {
            updatePeerHeader(findConversation(selectedPeer));
        }
    }

    private ConversationItem ensureConversation(String peer, boolean online, boolean preKeyAvailable) {
        ConversationItem existing = findConversation(peer);
        if (existing != null) {
            existing.online = existing.online || online;
            existing.preKeyAvailable = existing.preKeyAvailable || preKeyAvailable;
            return existing;
        }
        boolean isGrp = peer.startsWith("group-");
        ConversationItem item = new ConversationItem(peer, online, preKeyAvailable,
                Instant.now().getEpochSecond(), false, isGrp);
        addConversation(item);
        return item;
    }

    private void addConversation(ConversationItem item) {
        if (userListModel.size() == 1 && userListModel.get(0).placeholder) {
            userListModel.clear();
        }
        userListModel.addElement(item);
    }

    // =========================================================================
    // === TÍNH NĂNG MỚI: File Transfer ========================================
    // =========================================================================

    /** Mở hộp thoại chọn file để gửi E2EE cho peer đang chat. */
    private void openFileChooser() {
        if (selectedPeer == null) return;
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Chọn file để gửi cho " + selectedPeer);
        chooser.setMultiSelectionEnabled(false);
        int result = chooser.showOpenDialog(this);
        if (result != javax.swing.JFileChooser.APPROVE_OPTION) return;

        java.io.File selectedFile = chooser.getSelectedFile();
        String peer = selectedPeer;

        // Hiển thị tin nhắn tạm thời
        String time = java.time.LocalTime.now().format(TIME_FMT);
        addMessageBubble("📎 " + selectedFile.getName() + " (đang gửi...)", true, time);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                fileTransferManager.sendFile(selectedFile, peer);
                return true;
            }

            @Override
            protected void done() {
                try {
                    get();
                    flow("File đã gửi",
                            "\"" + selectedFile.getName() + "\" → " + peer,
                            ActivityFlowPanel.Tone.SUCCESS);
                    
                    if (localDb != null) {
                        localDb.saveMessage(username, peer, username, "📎 " + selectedFile.getName() + " (đã gửi)", Instant.now().getEpochSecond());
                    }
                    if (peer.equals(selectedPeer)) {
                        loadChatHistory();
                    }
                } catch (Exception ex) {
                    log.error("File send failed", ex);
                    flow("Gửi file thất bại", ex.getMessage(), ActivityFlowPanel.Tone.ERROR);
                }
            }
        }.execute();
    }

    /**
     * Hiển thị thông báo nhận file xong (chạy trên EDT).
     */
    private void showFileReceivedNotification(java.io.File file, String fileName, String senderId, String groupId) {
        String peer = (groupId != null && !groupId.isEmpty()) ? groupId : senderId;

        flow("File nhận xong ✓", "\"" + fileName + "\" đã lưu tại " + file.getParent(),
                ActivityFlowPanel.Tone.SUCCESS);

        // Thêm bubble tin nhắn hệ thống
        String time = java.time.LocalTime.now().format(TIME_FMT);
        
        // Save to SQLite DB for persistence
        if (localDb != null) {
            localDb.saveMessage(username, peer, senderId, "📥 " + fileName + " (đã nhận — SHA-256 ✓)", Instant.now().getEpochSecond());
        }

        if (peer.equals(selectedPeer)) {
            addMessageBubble("📥 " + fileName + " (đã nhận — SHA-256 ✓)", false, time, senderId);
        } else {
            ConversationItem item = findConversation(peer);
            if (item == null) {
                if (groupId != null && !groupId.isEmpty() && groupManager != null) {
                    GroupManager.GroupInfo group = groupManager.getGroup(groupId);
                    String groupName = group != null ? group.groupName() : "Nhóm mới";
                    item = ConversationItem.manual(groupId);
                    item.displayName = groupName;
                    item.online = true;
                    item.isGroup = true;
                    addConversation(item);
                } else {
                    item = ConversationItem.manual(senderId);
                    addConversation(item);
                }
            }
            item.unreadCount++;
            userList.repaint();
        }

        // Hỏi mở file không
        int opt = javax.swing.JOptionPane.showConfirmDialog(this,
                "Đã nhận xong file \"" + fileName + "\".\nBạn có muốn mở thư mục lưu không?",
                "File đã tải xong", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        if (opt == javax.swing.JOptionPane.YES_OPTION) {
            try {
                java.awt.Desktop.getDesktop().open(file.getParentFile());
            } catch (Exception ex) {
                log.warn("Cannot open directory: {}", ex.getMessage());
            }
        }
    }

    // =========================================================================
    // === TÍNH NĂNG MỚI: Group Chat ===========================================
    // =========================================================================

    /**
     * Hiển thị dialog tạo nhóm — Glassmorphism UI.
     */
    private void showCreateGroupDialog() {
        javax.swing.JFrame topFrame = (javax.swing.JFrame)
                javax.swing.SwingUtilities.getWindowAncestor(this);

        javax.swing.JDialog dialog = new javax.swing.JDialog(topFrame, "Tạo nhóm chat mới", true);
        dialog.setMinimumSize(new Dimension(460, 420));
        dialog.setUndecorated(false);
        dialog.getContentPane().setBackground(UIConstants.DEEP_CARBON);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIConstants.DEEP_CARBON);
        content.setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel title = UiStyles.headingLabel("Tạo nhóm chat");
        title.setForeground(UIConstants.TEXT_WHITE);
        title.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));

        JLabel nameLabel = UiStyles.mutedLabel("Tên nhóm");
        nameLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        content.add(nameLabel);
        content.add(Box.createVerticalStrut(6));

        JTextField groupNameField = UiStyles.styledTextField(20);
        UiStyles.setPlaceholder(groupNameField, "VD: Nhóm học tập, Dev Team...");
        groupNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        groupNameField.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        content.add(groupNameField);
        content.add(Box.createVerticalStrut(18));

        JLabel membersLabel = UiStyles.mutedLabel("Chọn thành viên vào nhóm");
        membersLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        content.add(membersLabel);
        content.add(Box.createVerticalStrut(6));

        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBackground(UIConstants.INPUT_BG);
        membersPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        List<javax.swing.JCheckBox> checkBoxes = new ArrayList<>();
        if (lastUserList != null) {
            for (ConversationItem user : lastUserList) {
                if (user != null && !user.placeholder && !user.isGroup && !user.userId.equals(username)) {
                    javax.swing.JCheckBox cb = new javax.swing.JCheckBox(user.displayName() + " (" + user.userId + ")");
                    cb.setActionCommand(user.userId);
                    cb.setFont(UIConstants.FONT_BODY);
                    cb.setForeground(UIConstants.TEXT_SILVER);
                    cb.setOpaque(false);
                    cb.setFocusPainted(false);
                    if (user.userId.equals(selectedPeer)) {
                        cb.setSelected(true);
                    }
                    membersPanel.add(cb);
                    membersPanel.add(Box.createVerticalStrut(6));
                    checkBoxes.add(cb);
                }
            }
        }

        if (checkBoxes.isEmpty()) {
            JLabel noUsersLabel = new JLabel("Không có thành viên trực tuyến nào khác");
            noUsersLabel.setFont(UIConstants.FONT_BODY);
            noUsersLabel.setForeground(UIConstants.TEXT_MUTED);
            noUsersLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            membersPanel.add(noUsersLabel);
        }

        JScrollPane scrollPane = new JScrollPane(membersPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIConstants.GLASS_BORDER, 1));
        scrollPane.setBackground(UIConstants.INPUT_BG);
        scrollPane.getViewport().setBackground(UIConstants.INPUT_BG);
        scrollPane.setPreferredSize(new Dimension(380, 150));
        scrollPane.setMinimumSize(new Dimension(380, 150));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        scrollPane.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        content.add(scrollPane);
        content.add(Box.createVerticalStrut(24));

        JPanel btnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton cancelBtn = UiStyles.ghostButton("Hủy");
        cancelBtn.addActionListener(e -> dialog.dispose());
        btnRow.add(cancelBtn);

        JButton createBtn = UiStyles.primaryButton("Tạo nhóm");
        createBtn.addActionListener(e -> {
            String groupName = groupNameField.getText().trim();
            if (groupName.isEmpty()) {
                groupNameField.requestFocus();
                return;
            }
            List<String> memberIds = new ArrayList<>();
            for (javax.swing.JCheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    memberIds.add(cb.getActionCommand());
                }
            }
            if (memberIds.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(dialog,
                        "Vui lòng chọn ít nhất 1 thành viên.", "Lỗi",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            GroupManager.GroupInfo group = groupManager.createGroup(groupName, memberIds);
            flow("Nhóm đã tạo", "\"" + groupName + "\" với " + memberIds.size() + " thành viên",
                    ActivityFlowPanel.Tone.SUCCESS);
            dialog.dispose();

            // Mở conversation với nhóm vừa tạo
            ConversationItem groupItem = ConversationItem.manual(group.groupId());
            groupItem.displayName = group.groupName();
            groupItem.online = true;
            groupItem.isGroup = true;
            SwingUtilities.invokeLater(() -> {
                addConversation(groupItem);
                selectPeer(groupItem);
            });
        });
        btnRow.add(createBtn);
        content.add(btnRow);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(topFrame);
        dialog.setVisible(true);
    }

    // =========================================================================
    // === TÍNH NĂNG MỚI: Voice/Video Call =====================================
    // =========================================================================

    private static class VideoMeshPanel extends JPanel {
        private float waveOffset = 0f;
        boolean micMuted = false;
        boolean camOff = false;
        private final String peerId;
        private final String localId;
        private final javax.swing.Timer timer;

        VideoMeshPanel(String peerId, String localId) {
            this.peerId = peerId;
            this.localId = localId;
            setLayout(null);
            
            timer = new javax.swing.Timer(50, e -> {
                waveOffset += 0.05f;
                repaint();
            });
            timer.start();
        }

        void cleanup() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth();
            int h = getHeight();

            if (camOff) {
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(0, 0, w, h);
                g2.setColor(UIConstants.TEXT_MUTED);
                g2.setFont(UIConstants.FONT_BODY.deriveFont(16f));
                String s = "Camera is off";
                int sw = g2.getFontMetrics().stringWidth(s);
                g2.drawString(s, (w - sw) / 2, h / 2 + 80);
            } else {
                float x1 = (float) (w * (0.5 + 0.3 * Math.sin(waveOffset)));
                float y1 = (float) (h * (0.3 + 0.2 * Math.cos(waveOffset * 0.7)));
                float x2 = (float) (w * (0.2 + 0.4 * Math.cos(waveOffset * 1.3)));
                float y2 = (float) (h * (0.7 + 0.15 * Math.sin(waveOffset * 0.9)));

                Point2D center = new Point2D.Float(x1, y1);
                float radius = Math.max(w, h) * 0.8f;
                float[] dist = {0.0f, 0.5f, 1.0f};
                Color[] colors = {
                    new Color(13, 148, 136, 120),
                    new Color(79, 70, 229, 90),
                    new Color(15, 23, 42, 255)
                };

                java.awt.RadialGradientPaint radial = new java.awt.RadialGradientPaint(
                    center, radius, dist, colors
                );
                g2.setPaint(radial);
                g2.fillRect(0, 0, w, h);

                Point2D center2 = new Point2D.Float(x2, y2);
                Color[] colors2 = {
                    new Color(124, 58, 237, 70),
                    new Color(13, 148, 136, 50),
                    new Color(0, 0, 0, 0)
                };
                java.awt.RadialGradientPaint radial2 = new java.awt.RadialGradientPaint(
                    center2, radius * 0.7f, dist, colors2
                );
                g2.setPaint(radial2);
                g2.fillRect(0, 0, w, h);
            }
            g2.dispose();
        }
    }

    /**
     * Bắt đầu cuộc gọi tới peer đang chọn.
     */
    private void startCallWithPeer(WebRtcManager.MediaType mediaType) {
        if (selectedPeer == null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một người dùng để gọi.", "Chưa chọn người dùng",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (webRtcManager.isInCall()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Đang trong một cuộc gọi khác.", "Bận",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        String peer = selectedPeer;
        String typeLabel = mediaType == WebRtcManager.MediaType.VIDEO ? "Video" : "Thoại";

        showActiveCallDialog(peer, mediaType, true);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                webRtcManager.startCall(peer, mediaType);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    flow("Gọi " + typeLabel, "Đang chờ " + peer + " bắt máy...",
                            ActivityFlowPanel.Tone.INFO);
                } catch (Exception ex) {
                    log.error("startCall failed", ex);
                    flow("Gọi thất bại", ex.getMessage(), ActivityFlowPanel.Tone.ERROR);
                    if (activeCallDialog != null) activeCallDialog.dispose();
                }
            }
        }.execute();
    }

    /**
     * Hiển thị Dialog cuộc gọi đang hoạt động.
     */
    private void showActiveCallDialog(String peer, WebRtcManager.MediaType mediaType, boolean isCaller) {
        if (activeCallDialog != null) activeCallDialog.dispose();

        javax.swing.JFrame topFrame = (javax.swing.JFrame)
                javax.swing.SwingUtilities.getWindowAncestor(this);

        String callTypeIcon = mediaType == WebRtcManager.MediaType.VIDEO ? "📹" : "📞";
        activeCallDialog = new javax.swing.JDialog(topFrame,
                "Cuộc gọi — " + peer, false);

        if (mediaType == WebRtcManager.MediaType.AUDIO) {
            activeCallDialog.setSize(360, 340);
            activeCallDialog.setResizable(false);
            activeCallDialog.setLocationRelativeTo(this);

            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SURFACE);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    java.awt.GradientPaint glow = new java.awt.GradientPaint(
                            0, 0, new Color(0, 161, 156, 30),
                            getWidth(), getHeight(), new Color(0, 0, 0, 0));
                    g2.setPaint(glow);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(new EmptyBorder(30, 30, 30, 30));

            AvatarBadge callAvatar = new AvatarBadge(peer, Color.decode("#5B54F6"), UIConstants.SECURE_TEAL);
            callAvatar.setPreferredSize(new Dimension(72, 72));
            callAvatar.setMaximumSize(new Dimension(72, 72));
            callAvatar.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            panel.add(callAvatar);
            panel.add(Box.createVerticalStrut(14));

            JLabel peerLabel = UiStyles.headingLabel(peer);
            peerLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            peerLabel.setForeground(UIConstants.TEXT_WHITE);
            panel.add(peerLabel);
            panel.add(Box.createVerticalStrut(6));

            JLabel statusLabel = UiStyles.mutedLabel(isCaller ? "Đang gọi..." : "Cuộc gọi thoại");
            statusLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            statusLabel.setForeground(UIConstants.SECURE_TEAL);
            panel.add(statusLabel);
            panel.add(Box.createVerticalStrut(4));

            JLabel e2eeLabel = UiStyles.mutedLabel("🔒 Cuộc gọi được bảo mật");
            e2eeLabel.setFont(UIConstants.FONT_SMALL.deriveFont(10f));
            e2eeLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            e2eeLabel.setForeground(new Color(0, 161, 156, 180));
            panel.add(e2eeLabel);
            panel.add(Box.createVerticalGlue());

            JPanel btnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 20, 0));
            btnRow.setOpaque(false);

            JButton endBtn = new JButton("      Kết thúc") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SIGNAL_RED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                    
                    // Draw hangup icon
                    g2.setColor(Color.WHITE);
                    int size = 16;
                    int ix = 16;
                    int iy = (getHeight() - size) / 2;
                    Path2D.Double path = new Path2D.Double();
                    path.moveTo(ix + 1, iy + 9);
                    path.quadTo(ix + 8, iy + 3, ix + 15, iy + 9);
                    path.lineTo(ix + 13, iy + 12);
                    path.quadTo(ix + 8, iy + 7, ix + 3, iy + 12);
                    path.closePath();
                    g2.fill(path);
                    g2.dispose();
                    
                    super.paintComponent(g);
                }
            };
            endBtn.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
            endBtn.setForeground(Color.WHITE);
            endBtn.setFocusPainted(false);
            endBtn.setBorderPainted(false);
            endBtn.setContentAreaFilled(false);
            endBtn.setOpaque(false);
            endBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            endBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
            endBtn.setPreferredSize(new Dimension(140, 42));
            endBtn.addActionListener(e -> endCallAction());
            btnRow.add(endBtn);
            panel.add(btnRow);

            activeCallDialog.setContentPane(panel);
        } else {
            activeCallDialog.setSize(640, 520);
            activeCallDialog.setLocationRelativeTo(this);
            
            VideoMeshPanel meshPanel = new VideoMeshPanel(peer, username);
            meshPanel.setSize(640, 520);

            JPanel centerInfo = new JPanel();
            centerInfo.setOpaque(false);
            centerInfo.setLayout(new BoxLayout(centerInfo, BoxLayout.Y_AXIS));
            
            AvatarBadge bigAvatar = new AvatarBadge(peer, Color.decode("#5B54F6"), UIConstants.SECURE_TEAL);
            bigAvatar.setPreferredSize(new Dimension(100, 100));
            bigAvatar.setMaximumSize(new Dimension(100, 100));
            bigAvatar.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            
            JLabel peerLabel = new JLabel("@" + peer);
            peerLabel.setFont(UIConstants.FONT_HEADING.deriveFont(Font.BOLD, 20f));
            peerLabel.setForeground(UIConstants.TEXT_WHITE);
            peerLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

            activeCallStateLbl = new JLabel(isCaller ? "Đang kết nối..." : "Đang kết nối video...");
            activeCallStateLbl.setFont(UIConstants.FONT_BODY.deriveFont(13f));
            activeCallStateLbl.setForeground(UIConstants.SECURE_TEAL);
            activeCallStateLbl.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

            centerInfo.add(bigAvatar);
            centerInfo.add(Box.createVerticalStrut(12));
            centerInfo.add(peerLabel);
            centerInfo.add(Box.createVerticalStrut(6));
            centerInfo.add(activeCallStateLbl);
            centerInfo.setBounds(170, 100, 300, 180);
            meshPanel.add(centerInfo);

            JPanel pipPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 200));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(new Color(255, 255, 255, 20));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                    g2.dispose();
                }
            };
            pipPanel.setLayout(new BorderLayout());
            pipPanel.setOpaque(false);
            pipPanel.setBounds(470, 20, 140, 180);
            pipPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            AvatarBadge pipAvatar = new AvatarBadge(username, Color.decode("#10B981"), UIConstants.SECURE_TEAL);
            pipAvatar.setPreferredSize(new Dimension(64, 64));
            
            JLabel pipTitle = new JLabel("Bạn (Webcam)");
            pipTitle.setFont(UIConstants.FONT_SMALL.deriveFont(Font.BOLD, 10f));
            pipTitle.setForeground(UIConstants.TEXT_MUTED);
            pipTitle.setHorizontalAlignment(JLabel.CENTER);

            JPanel pipInner = new JPanel(new BorderLayout());
            pipInner.setOpaque(false);
            pipInner.add(pipAvatar, BorderLayout.CENTER);
            pipInner.add(pipTitle, BorderLayout.SOUTH);
            pipPanel.add(pipInner, BorderLayout.CENTER);
            meshPanel.add(pipPanel);

            JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 12)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 220));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                    g2.setColor(new Color(255, 255, 255, 15));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                    g2.dispose();
                }
            };
            controlBar.setOpaque(false);
            controlBar.setBounds(140, 390, 360, 64);

            JButton micBtn = new JButton("") {
                private boolean active = true;
                {
                    setFont(UIConstants.FONT_BODY.deriveFont(16f));
                    setPreferredSize(new Dimension(40, 40));
                    setMargin(new java.awt.Insets(0, 0, 0, 0));
                    setBorder(BorderFactory.createEmptyBorder());
                    setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    setVerticalAlignment(javax.swing.SwingConstants.CENTER);
                    setFocusPainted(false);
                    setBorderPainted(false);
                    setContentAreaFilled(false);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setToolTipText("Tắt Mic");
                    addActionListener(e -> {
                        active = !active;
                        webRtcManager.setMicMuted(!active);
                        setToolTipText(active ? "Tắt Mic" : "Bật Mic");
                        repaint();
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(active ? new Color(255, 255, 255, 20) : UIConstants.SIGNAL_RED);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    
                    // Draw modern microphone vector icon
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.8f));
                    int size = 16;
                    int ix = (getWidth() - size) / 2;
                    int iy = (getHeight() - size) / 2;
                    
                    // Mic capsule
                    g2.drawRoundRect(ix + 5, iy + 1, 6, 9, 6, 6);
                    if (active) {
                        g2.fillRoundRect(ix + 5, iy + 1, 6, 9, 6, 6);
                    }
                    // Mic stand (U-shape)
                    g2.drawArc(ix + 2, iy + 4, 12, 8, 180, 180);
                    // Stand stem & base
                    g2.drawLine(ix + 8, iy + 12, ix + 8, iy + 15);
                    g2.drawLine(ix + 5, iy + 15, ix + 11, iy + 15);
                    
                    // Slash if muted
                    if (!active) {
                        g2.setStroke(new BasicStroke(2.0f));
                        g2.drawLine(ix + 1, iy + 1, ix + 15, iy + 15);
                    }
                    g2.dispose();
                }
            };
            micBtn.setForeground(Color.WHITE);

            JButton camBtn = new JButton("") {
                private boolean active = true;
                {
                    setFont(UIConstants.FONT_BODY.deriveFont(16f));
                    setPreferredSize(new Dimension(40, 40));
                    setMargin(new java.awt.Insets(0, 0, 0, 0));
                    setBorder(BorderFactory.createEmptyBorder());
                    setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    setVerticalAlignment(javax.swing.SwingConstants.CENTER);
                    setFocusPainted(false);
                    setBorderPainted(false);
                    setContentAreaFilled(false);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setToolTipText("Tắt Camera");
                    addActionListener(e -> {
                        active = !active;
                        setToolTipText(active ? "Bật Camera" : "Tắt Camera");
                        meshPanel.repaint();
                        pipPanel.setVisible(active);
                        repaint();
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(active ? new Color(255, 255, 255, 20) : UIConstants.SIGNAL_RED);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    
                    // Draw modern camera vector icon
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.8f));
                    int size = 16;
                    int ix = (getWidth() - size) / 2;
                    int iy = (getHeight() - size) / 2;
                    
                    // Camera body
                    g2.drawRoundRect(ix, iy + 3, 10, 8, 2, 2);
                    if (active) {
                        g2.fillRoundRect(ix, iy + 3, 10, 8, 2, 2);
                    }
                    // Camera lens
                    int[] px = {ix + 10, ix + 15, ix + 15, ix + 10};
                    int[] py = {iy + 6, iy + 3, iy + 11, iy + 8};
                    g2.drawPolygon(px, py, 4);
                    if (active) {
                        g2.fillPolygon(px, py, 4);
                    }
                    
                    // Slash if camera is off
                    if (!active) {
                        g2.setStroke(new BasicStroke(2.0f));
                        g2.drawLine(ix - 1, iy + 1, ix + 16, iy + 14);
                    }
                    g2.dispose();
                }
            };
            camBtn.setForeground(Color.WHITE);

            JButton hangupBtn = new JButton("") {
                {
                    setFont(UIConstants.FONT_BODY.deriveFont(18f));
                    setPreferredSize(new Dimension(44, 44));
                    setMargin(new java.awt.Insets(0, 0, 0, 0));
                    setBorder(BorderFactory.createEmptyBorder());
                    setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    setVerticalAlignment(javax.swing.SwingConstants.CENTER);
                    setFocusPainted(false);
                    setBorderPainted(false);
                    setContentAreaFilled(false);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setToolTipText("Kết thúc cuộc gọi");
                    addActionListener(e -> endCallAction());
                }
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SIGNAL_RED);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    
                    // Draw modern hangup phone receiver icon
                    g2.setColor(Color.WHITE);
                    int size = 16;
                    int ix = (getWidth() - size) / 2;
                    int iy = (getHeight() - size) / 2;
                    Path2D.Double path = new Path2D.Double();
                    path.moveTo(ix + 1, iy + 9);
                    path.quadTo(ix + 8, iy + 3, ix + 15, iy + 9);
                    path.lineTo(ix + 13, iy + 12);
                    path.quadTo(ix + 8, iy + 7, ix + 3, iy + 12);
                    path.closePath();
                    g2.fill(path);
                    g2.dispose();
                }
            };
            hangupBtn.setForeground(Color.WHITE);

            controlBar.add(micBtn);
            controlBar.add(camBtn);
            controlBar.add(hangupBtn);
            meshPanel.add(controlBar);

            JPanel e2eeBanner = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0, 161, 156, 35));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(0, 161, 156, 100));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                }
            };
            e2eeBanner.setOpaque(false);
            e2eeBanner.setBounds(20, 20, 240, 32);
            e2eeBanner.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));
            JLabel bannerLabel = new JLabel("🔒 CUỘC GỌI VIDEO");
            bannerLabel.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 10f));
            bannerLabel.setForeground(UIConstants.SECURE_TEAL);
            e2eeBanner.add(bannerLabel);
            meshPanel.add(e2eeBanner);

            activeCallDialog.setContentPane(meshPanel);
            
            activeCallDialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    meshPanel.cleanup();
                }
            });
        }

        activeCallDialog.getContentPane().setBackground(UIConstants.SURFACE);
        activeCallDialog.setVisible(true);
    }

    /**
     * Hiển thị dialog cuộc gọi đến.
     */
    private void showIncomingCallDialog(CallSignalDto signal) {
        if (activeCallDialog != null) activeCallDialog.dispose();

        pendingInboundCall = signal;
        WebRtcManager.MediaType mediaType = "VIDEO".equalsIgnoreCase(signal.getMediaType())
                ? WebRtcManager.MediaType.VIDEO : WebRtcManager.MediaType.AUDIO;
        String caller = signal.getCallerId();
        String icon = mediaType == WebRtcManager.MediaType.VIDEO ? "📹" : "📞";

        javax.swing.JFrame topFrame = (javax.swing.JFrame)
                javax.swing.SwingUtilities.getWindowAncestor(this);

        activeCallDialog = new javax.swing.JDialog(topFrame,
                "Cuộc gọi đến từ " + caller, false);
        activeCallDialog.setSize(380, 330);
        activeCallDialog.setResizable(false);
        activeCallDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UIConstants.SURFACE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                java.awt.GradientPaint glow = new java.awt.GradientPaint(
                        0, 0, new Color(0, 161, 156, 25),
                        getWidth(), getHeight(), new Color(0, 0, 0, 0));
                g2.setPaint(glow);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(28, 28, 28, 28));

        AvatarBadge avatar = new AvatarBadge(caller, Color.decode("#5B54F6"), UIConstants.SECURE_TEAL);
        avatar.setPreferredSize(new Dimension(60, 60));
        avatar.setMaximumSize(new Dimension(60, 60));
        avatar.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        panel.add(avatar);
        panel.add(Box.createVerticalStrut(12));

        JLabel callerLabel = UiStyles.headingLabel(caller);
        callerLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        panel.add(callerLabel);
        panel.add(Box.createVerticalStrut(4));

        JLabel typeLabel = UiStyles.mutedLabel(icon + " Cuộc gọi " +
                (mediaType == WebRtcManager.MediaType.VIDEO ? "video" : "thoại") + " đến");
        typeLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        typeLabel.setForeground(UIConstants.SECURE_TEAL);
        panel.add(typeLabel);
        panel.add(Box.createVerticalGlue());

        JPanel btnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 16, 0));
        btnRow.setOpaque(false);

        JButton rejectBtn = new JButton("      Từ chối") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.SIGNAL_RED);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                
                // Draw hangup icon
                g2.setColor(Color.WHITE);
                int size = 16;
                int ix = 16;
                int iy = (getHeight() - size) / 2;
                Path2D.Double path = new Path2D.Double();
                path.moveTo(ix + 1, iy + 9);
                path.quadTo(ix + 8, iy + 3, ix + 15, iy + 9);
                path.lineTo(ix + 13, iy + 12);
                path.quadTo(ix + 8, iy + 7, ix + 3, iy + 12);
                path.closePath();
                g2.fill(path);
                g2.dispose();
                
                super.paintComponent(g);
            }
        };
        rejectBtn.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
        rejectBtn.setForeground(Color.WHITE);
        rejectBtn.setFocusPainted(false);
        rejectBtn.setBorderPainted(false);
        rejectBtn.setContentAreaFilled(false);
        rejectBtn.setOpaque(false);
        rejectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rejectBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
        rejectBtn.setPreferredSize(new Dimension(140, 42));
        rejectBtn.addActionListener(e -> rejectCallAction());
        btnRow.add(rejectBtn);

        JButton acceptBtn = new JButton("      Bắt máy") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.SECURE_TEAL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                
                // Draw accept icon
                g2.setColor(Color.WHITE);
                int size = 16;
                int ix = 16;
                int iy = (getHeight() - size) / 2;
                Path2D.Double path = new Path2D.Double();
                path.moveTo(ix + 3, iy + 1);
                path.lineTo(ix + 6, iy + 1);
                path.quadTo(ix + 7, iy + 4, ix + 5, iy + 6);
                path.lineTo(ix + 9, iy + 10);
                path.quadTo(ix + 11, iy + 8, ix + 14, iy + 9);
                path.lineTo(ix + 14, iy + 12);
                path.quadTo(ix + 10, iy + 15, ix + 5, iy + 10);
                path.quadTo(ix + 0, iy + 5, ix + 3, iy + 1);
                path.closePath();
                g2.fill(path);
                g2.dispose();
                
                super.paintComponent(g);
            }
        };
        acceptBtn.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
        acceptBtn.setForeground(Color.WHITE);
        acceptBtn.setFocusPainted(false);
        acceptBtn.setBorderPainted(false);
        acceptBtn.setContentAreaFilled(false);
        acceptBtn.setOpaque(false);
        acceptBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        acceptBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
        acceptBtn.setPreferredSize(new Dimension(140, 42));
        acceptBtn.addActionListener(e -> {
            activeCallDialog.dispose();
            acceptCallAction();
        });
        btnRow.add(acceptBtn);
        panel.add(btnRow);

        activeCallDialog.setContentPane(panel);
        activeCallDialog.setVisible(true);
    }

    /** Thanh điều khiển cuộc gọi */
    private void updateCallControlBar(WebRtcManager.CallState state, String peer, WebRtcManager.MediaType mediaType) {
        if (state == WebRtcManager.CallState.IDLE || state == WebRtcManager.CallState.ENDED) {
            callControlBar.setVisible(false);
            callControlBar.removeAll();
            revalidate();
            repaint();
            return;
        }

        callControlBar.removeAll();
        callControlBar.setVisible(true);
        callControlBar.setOpaque(true);
        callControlBar.setBackground(new Color(15, 23, 42));
        callControlBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.GLASS_BORDER),
                new EmptyBorder(10, 20, 10, 20)));

        String callTypeStr = mediaType == WebRtcManager.MediaType.VIDEO ? "gọi video" : "gọi thoại";
        String statusText = "";
        boolean showAccept = false;
        boolean showReject = false;
        boolean showHangup = false;

        boolean isCaller = false;
        WebRtcManager.ActiveCall active = webRtcManager.getActiveCall();
        if (active != null) {
            isCaller = active.isCaller();
        } else if (pendingInboundCall != null) {
            isCaller = false;
        }

        switch (state) {
            case RINGING -> {
                if (isCaller) {
                    statusText = "Đang gọi " + callTypeStr + " tới @" + peer + "... Đang đổ chuông";
                    showHangup = true;
                } else {
                    statusText = "Cuộc " + callTypeStr + " đến từ @" + peer + "...";
                    showAccept = true;
                    showReject = true;
                }
            }
            case CONNECTING -> {
                statusText = "Đang kết nối cuộc " + callTypeStr + " với @" + peer + "...";
                showHangup = true;
            }
            case ACTIVE -> {
                statusText = "Cuộc " + callTypeStr + " đang hoạt động với @" + peer;
                showHangup = true;
            }
        }

        JLabel label = new JLabel(statusText);
        label.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 13f));
        label.setForeground(UIConstants.TEXT_WHITE);
        
        JPanel dotPanel = new JPanel() {
            private float alpha = 1.0f;
            private javax.swing.Timer blinkTimer = new javax.swing.Timer(300, e -> {
                alpha = alpha == 1.0f ? 0.2f : 1.0f;
                repaint();
            });
            {
                setOpaque(false);
                setPreferredSize(new java.awt.Dimension(16, 16));
                blinkTimer.start();
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(state == WebRtcManager.CallState.ACTIVE ? UIConstants.SECURE_TEAL : UIConstants.SIGNAL_RED);
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
                g2.fillOval(4, 4, 8, 8);
                g2.dispose();
            }
        };
        
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(dotPanel);
        left.add(label);
        callControlBar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        if (showAccept) {
            JButton accept = new JButton("      Trả lời") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SECURE_TEAL);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                    
                    // Draw accept icon
                    g2.setColor(Color.WHITE);
                    int size = 16;
                    int ix = 12;
                    int iy = (getHeight() - size) / 2;
                    Path2D.Double path = new Path2D.Double();
                    path.moveTo(ix + 3, iy + 1);
                    path.lineTo(ix + 6, iy + 1);
                    path.quadTo(ix + 7, iy + 4, ix + 5, iy + 6);
                    path.lineTo(ix + 9, iy + 10);
                    path.quadTo(ix + 11, iy + 8, ix + 14, iy + 9);
                    path.lineTo(ix + 14, iy + 12);
                    path.quadTo(ix + 10, iy + 15, ix + 5, iy + 10);
                    path.quadTo(ix + 0, iy + 5, ix + 3, iy + 1);
                    path.closePath();
                    g2.fill(path);
                    g2.dispose();
                    
                    super.paintComponent(g);
                }
            };
            accept.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
            accept.setForeground(Color.WHITE);
            accept.setFocusPainted(false);
            accept.setBorderPainted(false);
            accept.setContentAreaFilled(false);
            accept.setOpaque(false);
            accept.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            accept.setBorder(new EmptyBorder(8, 16, 8, 16));
            Dimension pref = accept.getPreferredSize();
            accept.setPreferredSize(new Dimension(Math.max(120, pref.width), 34));
            accept.addActionListener(e -> acceptCallAction());
            right.add(accept);
        }
        if (showReject) {
            JButton reject = new JButton("      Từ chối") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SIGNAL_RED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                    
                    // Draw hangup icon
                    g2.setColor(Color.WHITE);
                    int size = 16;
                    int ix = 12;
                    int iy = (getHeight() - size) / 2;
                    Path2D.Double path = new Path2D.Double();
                    path.moveTo(ix + 1, iy + 9);
                    path.quadTo(ix + 8, iy + 3, ix + 15, iy + 9);
                    path.lineTo(ix + 13, iy + 12);
                    path.quadTo(ix + 8, iy + 7, ix + 3, iy + 12);
                    path.closePath();
                    g2.fill(path);
                    g2.dispose();
                    
                    super.paintComponent(g);
                }
            };
            reject.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
            reject.setForeground(Color.WHITE);
            reject.setFocusPainted(false);
            reject.setBorderPainted(false);
            reject.setContentAreaFilled(false);
            reject.setOpaque(false);
            reject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reject.setBorder(new EmptyBorder(8, 16, 8, 16));
            Dimension pref = reject.getPreferredSize();
            reject.setPreferredSize(new Dimension(Math.max(120, pref.width), 34));
            reject.addActionListener(e -> rejectCallAction());
            right.add(reject);
        }
        if (showHangup) {
            JButton hangup = new JButton("      Cúp máy") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SIGNAL_RED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                    
                    // Draw hangup icon
                    g2.setColor(Color.WHITE);
                    int size = 16;
                    int ix = 12;
                    int iy = (getHeight() - size) / 2;
                    Path2D.Double path = new Path2D.Double();
                    path.moveTo(ix + 1, iy + 9);
                    path.quadTo(ix + 8, iy + 3, ix + 15, iy + 9);
                    path.lineTo(ix + 13, iy + 12);
                    path.quadTo(ix + 8, iy + 7, ix + 3, iy + 12);
                    path.closePath();
                    g2.fill(path);
                    g2.dispose();
                    
                    super.paintComponent(g);
                }
            };
            hangup.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
            hangup.setForeground(Color.WHITE);
            hangup.setFocusPainted(false);
            hangup.setBorderPainted(false);
            hangup.setContentAreaFilled(false);
            hangup.setOpaque(false);
            hangup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            hangup.setBorder(new EmptyBorder(8, 16, 8, 16));
            Dimension pref = hangup.getPreferredSize();
            hangup.setPreferredSize(new Dimension(Math.max(120, pref.width), 34));
            hangup.addActionListener(e -> endCallAction());
            right.add(hangup);
        }

        callControlBar.add(right, BorderLayout.EAST);
        revalidate();
        repaint();
    }

    private void acceptCallAction() {
        CallSignalDto s = pendingInboundCall;
        if (s == null) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                webRtcManager.acceptCall(s);
                return null;
            }
            @Override protected void done() {
                pendingInboundCall = null;
                WebRtcManager.MediaType mediaType = "VIDEO".equalsIgnoreCase(s.getMediaType())
                        ? WebRtcManager.MediaType.VIDEO : WebRtcManager.MediaType.AUDIO;
                if (mediaType == WebRtcManager.MediaType.VIDEO) {
                    showActiveCallDialog(s.getCallerId(), mediaType, false);
                }
            }
        }.execute();
    }

    private void rejectCallAction() {
        CallSignalDto s = pendingInboundCall;
        if (s == null) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                webRtcManager.rejectCall(s);
                return null;
            }
            @Override protected void done() {
                pendingInboundCall = null;
                updateCallControlBar(WebRtcManager.CallState.IDLE, null, null);
            }
        }.execute();
    }

    private void endCallAction() {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                webRtcManager.endCall();
                return null;
            }
            @Override protected void done() {
                updateCallControlBar(WebRtcManager.CallState.IDLE, null, null);
            }
        }.execute();
    }

    /** Callback khi trạng thái cuộc gọi thay đổi. */
    private void onCallStateChanged(WebRtcManager.CallState state) {
        String peer = "";
        WebRtcManager.MediaType mediaType = WebRtcManager.MediaType.AUDIO;
        
        WebRtcManager.ActiveCall active = webRtcManager.getActiveCall();
        if (active != null) {
            peer = active.peerId();
            mediaType = active.mediaType();
        } else if (pendingInboundCall != null) {
            peer = pendingInboundCall.getCallerId();
            mediaType = "VIDEO".equalsIgnoreCase(pendingInboundCall.getMediaType())
                    ? WebRtcManager.MediaType.VIDEO : WebRtcManager.MediaType.AUDIO;
        }

        updateCallControlBar(state, peer, mediaType);

        if (activeCallDialog != null && activeCallDialog.isVisible() && mediaType == WebRtcManager.MediaType.VIDEO) {
            String stateStr = switch (state) {
                case RINGING -> "Đang đổ chuông...";
                case CONNECTING -> "Đang kết nối...";
                case ACTIVE -> "Cuộc gọi đang hoạt động 🔒";
                case ENDED -> "Cuộc gọi đã kết thúc";
                default -> state.name();
            };
            if (activeCallStateLbl != null) {
                activeCallStateLbl.setText(stateStr);
            }
        }

        String msg = switch (state) {
            case RINGING -> "Đang đổ chuông...";
            case CONNECTING -> "Đang kết nối...";
            case ACTIVE -> "Cuộc gọi đang hoạt động 🔒";
            case ENDED -> "Cuộc gọi đã kết thúc";
            default -> state.name();
        };
        flow("Cuộc gọi", msg,
                state == WebRtcManager.CallState.ACTIVE ? ActivityFlowPanel.Tone.SUCCESS
                : state == WebRtcManager.CallState.ENDED ? ActivityFlowPanel.Tone.INFO
                : ActivityFlowPanel.Tone.INFO);

        if (state == WebRtcManager.CallState.ENDED) {
            pendingInboundCall = null;
            activeCallStateLbl = null;
            if (activeCallDialog != null) {
                activeCallDialog.dispose();
                activeCallDialog = null;
            }
        }
    }

    /** Callback khi peer kết thúc cuộc gọi. */
    private void onCallEndedByPeer(String peerId) {
        flow("Cuộc gọi kết thúc", peerId + " đã cúp máy.", ActivityFlowPanel.Tone.INFO);
        updateCallControlBar(WebRtcManager.CallState.ENDED, peerId, WebRtcManager.MediaType.AUDIO);
        if (activeCallDialog != null) {
            activeCallDialog.dispose();
            activeCallDialog = null;
        }
    }

    /** Xử lý tín hiệu cuộc gọi đến trên UI (chạy trên EDT). */
    private void handleIncomingCallSignalUI(CallSignalDto signal) {
        if ("SDP_OFFER".equals(signal.getSignalType())) {
            pendingInboundCall = signal;
            WebRtcManager.MediaType mediaType = "VIDEO".equalsIgnoreCase(signal.getMediaType())
                    ? WebRtcManager.MediaType.VIDEO : WebRtcManager.MediaType.AUDIO;
            updateCallControlBar(WebRtcManager.CallState.RINGING, signal.getCallerId(), mediaType);
            showIncomingCallDialog(signal);
        }
    }



    private void markPreKeyAvailable(String peer) {
        if (peer == null || peer.equals(username)) {
            return;
        }
        ConversationItem item = ensureConversation(peer, false, true);
        item.preKeyAvailable = true;
        if (peer.equals(selectedPeer)) {
            updatePeerHeader(item);
        }
        userList.repaint();
    }

    private ConversationItem findConversation(String peer) {
        if (peer == null || userListModel == null) {
            return null;
        }
        for (int i = 0; i < userListModel.size(); i++) {
            ConversationItem item = userListModel.get(i);
            if (!item.placeholder && peer.equals(item.userId)) {
                return item;
            }
        }
        return null;
    }

    private ConversationItem findIn(List<ConversationItem> items, String peer) {
        if (peer == null) {
            return null;
        }
        for (ConversationItem item : items) {
            if (peer.equals(item.userId)) {
                return item;
            }
        }
        return null;
    }

    private int unreadCountFor(String peer) {
        ConversationItem item = findConversation(peer);
        return item == null ? 0 : item.unreadCount;
    }

    private void selectListItem(String peer) {
        if (peer == null || userList == null) {
            return;
        }
        for (int i = 0; i < userListModel.size(); i++) {
            ConversationItem item = userListModel.get(i);
            if (!item.placeholder && peer.equals(item.userId)) {
                userList.setSelectedIndex(i);
                return;
            }
        }
        userList.clearSelection();
    }

    private static String decodeError(PacketFrame frame) {
        try {
            ErrorResponse error = JsonSerializer.fromBytes(frame.getPayload(), ErrorResponse.class);
            return error.getMessage();
        } catch (Exception ignored) {
            return new String(frame.getPayload(), StandardCharsets.UTF_8);
        }
    }

    private void flow(String title, String body, ActivityFlowPanel.Tone tone) {
        activityPanel.addEvent(title, body, tone);
    }

    private void addMessageBubble(String text, boolean outgoing, String time) {
        addMessageBubble(text, outgoing, time, null);
    }

    private void addMessageBubble(String text, boolean outgoing, String time, String senderId) {
        if (this.messageCount == 0) {
            messageContainer.removeAll();
            messageContainer.add(Box.createVerticalGlue());
        }
        addMessageBubbleInternal(text, outgoing, time, senderId);
        this.messageCount++;
        scrollMessagesToEnd();
    }

    private void addMessageBubbleInternal(String text, boolean outgoing, String time) {
        addMessageBubbleInternal(text, outgoing, time, null);
    }

    private void addMessageBubbleInternal(String text, boolean outgoing, String time, String senderId) {
        JPanel row = new JPanel(new FlowLayout(
                outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if (!outgoing) {
            AvatarBadge avatar = new AvatarBadge(senderId != null ? senderId : selectedPeer, Color.decode("#5B54F6"), UIConstants.SECURE_TEAL);
            avatar.setPreferredSize(new Dimension(26, 26));
            row.add(avatar);
            row.add(Box.createHorizontalStrut(8));
        }

        JPanel bubbleAndMeta = new JPanel();
        bubbleAndMeta.setLayout(new BoxLayout(bubbleAndMeta, BoxLayout.Y_AXIS));
        bubbleAndMeta.setOpaque(false);

        if (!outgoing && senderId != null && selectedPeer != null && selectedPeer.startsWith("group-")) {
            JLabel senderLabel = new JLabel("@" + senderId);
            senderLabel.setFont(UIConstants.FONT_SMALL.deriveFont(Font.BOLD, 11f));
            senderLabel.setForeground(UIConstants.SECURE_TEAL);
            senderLabel.setBorder(new EmptyBorder(0, 6, 2, 0));
            bubbleAndMeta.add(senderLabel);
        }

        MessageBubble bubble = new MessageBubble(text, outgoing);
        bubbleAndMeta.add(bubble);

        JPanel metaRow = new JPanel(new FlowLayout(outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 8, 2));
        metaRow.setOpaque(false);
        JLabel timeLabel = UiStyles.mutedLabel(time);
        timeLabel.setFont(UIConstants.FONT_MONO.deriveFont(9f));
        timeLabel.setForeground(UIConstants.TEXT_MUTED);
        metaRow.add(timeLabel);
        bubbleAndMeta.add(metaRow);

        row.add(bubbleAndMeta);
        messageContainer.add(row);
        messageContainer.add(Box.createVerticalStrut(8));
    }

    private static final class ConversationItem {
        private final String userId;
        private String displayName;
        private boolean online;
        private boolean preKeyAvailable;
        private long lastSeenAt;
        private final boolean placeholder;
        private int unreadCount;
        private boolean isGroup;

        private ConversationItem(String userId, boolean online, boolean preKeyAvailable,
                long lastSeenAt, boolean placeholder, boolean isGroup) {
            this.userId = userId;
            this.online = online;
            this.preKeyAvailable = preKeyAvailable;
            this.lastSeenAt = lastSeenAt;
            this.placeholder = placeholder;
            this.isGroup = isGroup;
        }

        static ConversationItem placeholder(String text) {
            return new ConversationItem(text, false, false, 0L, true, false);
        }

        static ConversationItem manual(String userId) {
            boolean isGrp = userId.startsWith("group-");
            return new ConversationItem(userId, isGrp, false, 0L, false, isGrp);
        }

        static ConversationItem legacy(String userId) {
            boolean isGrp = userId.startsWith("group-");
            return new ConversationItem(userId, !isGrp, false, Instant.now().getEpochSecond(), false, isGrp);
        }

        static ConversationItem from(UserListEntry entry) {
            if (entry == null || entry.getUserId() == null || entry.getUserId().isBlank()) {
                return null;
            }
            boolean isGrp = entry.getUserId().startsWith("group-");
            return new ConversationItem(entry.getUserId(), entry.isOnline(),
                    entry.isPreKeyAvailable(), entry.getLastSeenAt(), false, isGrp);
        }

        boolean isSelectable(String self) {
            return !placeholder && userId != null && !userId.isBlank() && !userId.equals(self);
        }

        boolean isOnline() {
            return online;
        }

        boolean isPreKeyAvailable() {
            return preKeyAvailable;
        }

        String displayName() {
            if (placeholder) return userId;
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
            return userId;
        }

        String statusText() {
            if (placeholder) {
                return "Waiting for server...";
            }
            if (isGroup) {
                return "Trò chuyện nhóm";
            }
            return online ? "Active now" : "Offline";
        }

        String badgeText() {
            return "";
        }

        String timeText() {
            if (placeholder || lastSeenAt <= 0) {
                return "";
            }
            return Instant.ofEpochSecond(lastSeenAt)
                    .atZone(ZoneId.systemDefault()).toLocalTime().format(LAST_SEEN_FMT);
        }
    }

    private static final class UserCellRenderer extends JPanel implements ListCellRenderer<ConversationItem> {
        private final AvatarBadge avatar = new AvatarBadge("", Color.decode("#5B54F6"), UIConstants.OUTLINE);
        private final JLabel name = new JLabel();
        private final JLabel time = new JLabel();
        private final JLabel badge = new CountBadge();
        private final JLabel status = new JLabel();
        private boolean isSelected = false;

        UserCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 8, 8, 8));

            avatar.setPreferredSize(new Dimension(38, 38));
            add(avatar, BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setOpaque(false);
            name.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
            time.setFont(UIConstants.FONT_MONO.deriveFont(10f));
            time.setForeground(UIConstants.TEXT_MUTED);
            top.add(name, BorderLayout.CENTER);
            top.add(time, BorderLayout.EAST);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            status.setFont(UIConstants.FONT_SMALL);
            status.setForeground(UIConstants.TEXT_MUTED);
            
            badge.setFont(UIConstants.FONT_MONO.deriveFont(Font.BOLD, 10f));
            badge.setBorder(new EmptyBorder(1, 6, 1, 6));
            
            bottom.add(status, BorderLayout.CENTER);
            bottom.add(badge, BorderLayout.EAST);

            text.add(top);
            text.add(Box.createVerticalStrut(4));
            text.add(bottom);
            add(text, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                LinearGradientPaint bgGrad = new LinearGradientPaint(
                    new Point2D.Float(0, 0), new Point2D.Float(getWidth(), 0),
                    new float[]{0f, 1f},
                    new Color[]{UIConstants.ACCENT_DIM, new Color(0, 161, 156, 15)}
                );
                g2.setPaint(bgGrad);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                
                g2.setColor(UIConstants.ACCENT_GLOW);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                g2.dispose();
            }
            super.paintComponent(g);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ConversationItem> list,
                ConversationItem value, int index, boolean isSelected, boolean cellHasFocus) {
            this.isSelected = isSelected;
            ConversationItem item = value == null ? ConversationItem.placeholder("") : value;

            avatar.setValues(item.placeholder ? "" : item.userId,
                    Color.decode("#5B54F6"), item.online ? UIConstants.SECURE_TEAL : UIConstants.OUTLINE);

            name.setText(item.displayName());
            name.setForeground(item.placeholder ? UIConstants.TEXT_MUTED : UIConstants.TEXT_WHITE);
            time.setText(item.timeText());

            status.setText(item.statusText());
            status.setForeground(UIConstants.TEXT_MUTED);

            if (item.unreadCount > 0) {
                badge.setText(" " + item.unreadCount + " ");
                badge.setForeground(UIConstants.TEXT_WHITE);
                badge.setBackground(UIConstants.SIGNAL_RED);
                badge.setOpaque(false);
            } else {
                badge.setText("");
                badge.setOpaque(false);
            }
            return this;
        }
    }

    private static final class CountBadge extends JLabel {
        CountBadge() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (getText() != null && !getText().isBlank()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.SIGNAL_RED);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 999, 999);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private static final class EmbeddedPlaceholderField extends JTextField {
        private final String placeholder;

        EmbeddedPlaceholderField(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty() || placeholder == null || placeholder.isBlank() || hasFocus()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setClip(null);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(UIConstants.TEXT_MUTED);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            Insets insets = getInsets();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, insets.left + 4, y);
            g2.dispose();
        }
    }

    private static final class AvatarBadge extends JPanel {
        private String text;
        private Color fill;
        private Color stroke;

        AvatarBadge(String text, Color fill, Color stroke) {
            this.text = text == null ? "" : text;
            this.fill = fill;
            this.stroke = stroke;
            setOpaque(false);
            setPreferredSize(new Dimension(40, 40));
        }

        void setValues(String text, Color fill, Color stroke) {
            this.text = text == null ? "" : text;
            this.fill = fill;
            this.stroke = stroke;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean online = UIConstants.SECURE_TEAL.equals(stroke);
            boolean isGroup = text != null && text.startsWith("group-");
            g2.setColor(isGroup ? new Color(0, 150, 136) : fill);
            int avatarSize = Math.min(getWidth(), getHeight()) - (online ? 5 : 1);
            g2.fillOval(0, 0, avatarSize, avatarSize);
            String initials = initials(text);
            g2.setFont(UIConstants.FONT_MONO.deriveFont(Font.BOLD, Math.max(11f, getWidth() / 3f)));
            g2.setColor(UIConstants.TEXT_WHITE);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int x = (avatarSize - fm.stringWidth(initials)) / 2;
            int y = (avatarSize - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(initials, x, y);
            if (online && !isGroup) {
                int dotSize = Math.max(7, getWidth() / 5);
                g2.setColor(UIConstants.SECURE_TEAL);
                g2.fillOval(getWidth() - dotSize - 1, getHeight() - dotSize - 1, dotSize, dotSize);
                g2.setColor(UIConstants.SURFACE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(getWidth() - dotSize - 1, getHeight() - dotSize - 1, dotSize, dotSize);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        private static String initials(String value) {
            if (value == null || value.isBlank()) {
                return "SC";
            }
            if (value.startsWith("group-")) {
                return "GP";
            }
            String clean = value.replace("@", "").trim();
            return clean.length() <= 2 ? clean.toUpperCase(java.util.Locale.ROOT)
                    : clean.substring(0, 2).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static final class MessageBubble extends JPanel {
        private final boolean outgoing;
        private final Color bubbleColor;
        private final Color borderColor;

        MessageBubble(String text, boolean outgoing) {
            this.outgoing = outgoing;
            this.bubbleColor = outgoing ? new Color(0, 161, 156, 38) : UIConstants.GLASS_CARD;
            this.borderColor = outgoing ? new Color(0, 161, 156, 102) : UIConstants.GLASS_BORDER;
            setLayout(new BorderLayout(0, 0));
            setOpaque(false);

            int maxW = 400; // Fits about 8-9 words per line
            JTextArea body = new JTextArea(text);
            body.setFont(UIConstants.FONT_BODY);
            body.setForeground(UIConstants.TEXT_SILVER);
            body.setBackground(bubbleColor);
            body.setWrapStyleWord(true);
            body.setEditable(false);
            body.setBorder(new EmptyBorder(10, 14, 10, 14));
            body.setOpaque(false);
            body.setLineWrap(false);

            Dimension pref = body.getPreferredSize();
            body.setLineWrap(true);
            int width;
            if (pref.width + 16 <= maxW) {
                width = Math.max(70, pref.width + 16); // Added 16px buffer to avoid premature wrapping
            } else {
                width = maxW;
            }
            body.setSize(new Dimension(width, 9999));
            int height = body.getPreferredSize().height;
            
            Dimension size = new Dimension(width, height);
            body.setPreferredSize(size);
            body.setMinimumSize(size);
            body.setMaximumSize(size);

            add(body, BorderLayout.CENTER);
            
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bubbleColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
        }
    }

    private class ConnectionInfoPanel extends JPanel {
        ConnectionInfoPanel() {
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(RIGHT_RAIL_WIDTH, 0));
            setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UIConstants.GLASS_BORDER));

            add(buildConnectionHeader(), BorderLayout.NORTH);

            JPanel topContainer = new JPanel();
            topContainer.setOpaque(false);
            topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
            topContainer.setBorder(new EmptyBorder(12, 12, 8, 12));



            // Card 2: Security parameters table
            JPanel securityCard = new JPanel(new GridLayout(3, 1, 0, 4)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.GLASS_CARD);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(UIConstants.GLASS_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                    g2.dispose();
                }
            };
            securityCard.setOpaque(false);
            securityCard.setBorder(new EmptyBorder(10, 14, 10, 14));

            securityCard.add(paramRow(ActivityFlowPanel.IconType.KEY, "Encryption", "Active"));
            securityCard.add(paramRow(ActivityFlowPanel.IconType.SERVER, "Channel status", "Online"));
            securityCard.add(paramRow(ActivityFlowPanel.IconType.SHIELD, "Last key refresh", "Just now"));

            topContainer.add(securityCard);
            topContainer.add(Box.createVerticalStrut(14));

            JPanel content = new JPanel(new BorderLayout());
            content.setOpaque(false);
            content.add(topContainer, BorderLayout.NORTH);
            content.add(activityPanel, BorderLayout.CENTER);
            add(content, BorderLayout.CENTER);
        }

        private JPanel buildConnectionHeader() {
            JPanel header = new JPanel(new BorderLayout(10, 0));
            header.setOpaque(false);
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.GLASS_BORDER),
                    new EmptyBorder(14, 16, 14, 16)));

            JLabel title = UiStyles.headingLabel("Connection Info");
            title.setFont(UIConstants.FONT_HEADING.deriveFont(Font.BOLD, 16f));
            header.add(title, BorderLayout.CENTER);

            JButton close = createHeaderButton("close");
            close.setToolTipText("Hide connection info");
            close.addActionListener(e -> connHeaderBtn.doClick());
            header.add(close, BorderLayout.EAST);
            return header;
        }

        private JPanel paramRow(ActivityFlowPanel.IconType iconType, String label, String value) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);

            JPanel iconPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.SECURE_TEAL);
                    g2.setStroke(new BasicStroke(1.5f));
                    int size = 14;
                    int ix = (getWidth() - size) / 2;
                    int iy = (getHeight() - size) / 2;
                    switch (iconType) {
                        case KEY -> {
                            int r = size / 2;
                            g2.drawOval(ix, iy + (size - r) / 2, r, r);
                            int shankY = iy + size / 2;
                            g2.drawLine(ix + r - 1, shankY, ix + size, shankY);
                            g2.drawLine(ix + size - 2, shankY, ix + size - 2, shankY + 3);
                            g2.drawLine(ix + size - 5, shankY, ix + size - 5, shankY + 3);
                        }
                        case SERVER -> {
                            g2.drawOval(ix + size / 2 - 2, iy + 2, 4, 4);
                            g2.drawOval(ix + 2, iy + size - 5, 4, 4);
                            g2.drawOval(ix + size - 6, iy + size - 5, 4, 4);
                            g2.drawLine(ix + size / 2, iy + 6, ix + 4, iy + size - 5);
                            g2.drawLine(ix + size / 2, iy + 6, ix + size - 4, iy + size - 5);
                        }
                        case SHIELD -> {
                            Path2D.Double shield = new Path2D.Double();
                            shield.moveTo(ix + size / 2.0, iy);
                            shield.lineTo(ix + size - 1, iy + 1.5);
                            shield.lineTo(ix + size - 1, iy + size / 2.0);
                            shield.quadTo(ix + size - 1, iy + size * 0.75, ix + size / 2.0, iy + size - 0.5);
                            shield.quadTo(ix + 1, iy + size * 0.75, ix + 1, iy + size / 2.0);
                            shield.lineTo(ix + 1, iy + 1.5);
                            shield.closePath();
                            g2.draw(shield);
                            g2.drawLine(ix + size / 2 - 2, iy + size / 2, ix + size / 2, iy + size / 2 + 2);
                            g2.drawLine(ix + size / 2, iy + size / 2 + 2, ix + size / 2 + 3, iy + size / 2 - 2);
                        }
                    }
                    g2.dispose();
                }
            };
            iconPanel.setOpaque(false);
            iconPanel.setPreferredSize(new Dimension(20, 20));

            JLabel lbl = UiStyles.mutedLabel(label);
            lbl.setFont(UIConstants.FONT_BODY.deriveFont(13f));
            JLabel val = new JLabel(value);
            val.setFont(UIConstants.FONT_HEADING.deriveFont(Font.BOLD, 13f));
            val.setForeground(UIConstants.TEXT_WHITE);

            row.add(iconPanel, BorderLayout.WEST);
            row.add(lbl, BorderLayout.CENTER);
            row.add(val, BorderLayout.EAST);
            return row;
        }
    }

    private final class SidebarPanel extends JPanel {
        SidebarPanel() {
            setPreferredSize(new Dimension(72, 0));
            setOpaque(true);
            setBackground(UIConstants.SURFACE);
            setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIConstants.GLASS_BORDER));
            setLayout(new BorderLayout());

            JPanel topPanel = new JPanel() {
                private float ledAlpha = 1.0f;
                private javax.swing.Timer pulseTimer;
                {
                    setOpaque(false);
                    setPreferredSize(new Dimension(72, 80));
                    pulseTimer = new javax.swing.Timer(100, e -> {
                        ledAlpha = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 200.0));
                        repaint();
                    });
                    pulseTimer.start();
                }

                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    g2d.setColor(new Color(36, 47, 56, 180));
                    g2d.fillOval(14, 20, 44, 44);
                    g2d.setColor(UIConstants.GLASS_BORDER);
                    g2d.drawOval(14, 20, 44, 44);

                    g2d.setColor(UIConstants.TEXT_WHITE);
                    g2d.setFont(UIConstants.FONT_HEADING);
                    java.awt.FontMetrics fm = g2d.getFontMetrics();
                    String initials = username.length() >= 2 ? username.substring(0, 2).toUpperCase() : username.toUpperCase();
                    int textX = 14 + (44 - fm.stringWidth(initials)) / 2;
                    int textY = 20 + (44 - fm.getHeight()) / 2 + fm.getAscent();
                    g2d.drawString(initials, textX, textY);

                    g2d.setColor(new Color(0, 161, 156, (int)(ledAlpha * 255)));
                    g2d.fillOval(48, 52, 10, 10);
                    
                    g2d.setColor(new Color(0, 161, 156, (int)(ledAlpha * 100)));
                    g2d.setStroke(new java.awt.BasicStroke(3f));
                    g2d.drawOval(47, 51, 12, 12);

                    g2d.dispose();
                }
            };

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 20));
            bottomPanel.setOpaque(false);
            SidebarButton logoutBtn = new SidebarButton("logout", "Logout");
            logoutBtn.addActionListener(e -> listener.onLogout());
            bottomPanel.add(logoutBtn);

            add(topPanel, BorderLayout.NORTH);
            add(bottomPanel, BorderLayout.SOUTH);
        }

        private class SidebarButton extends JButton {
            private final String viewId;
            private boolean hover = false;

            SidebarButton(String viewId, String tooltip) {
                this.viewId = viewId;
                setToolTipText(tooltip);
                setPreferredSize(new Dimension(72, 44)); // Span full width of sidebar (72px)
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int cardSize = 44;
                int cx = (getWidth() - cardSize) / 2;
                int cy = (getHeight() - cardSize) / 2;
                
                if (hover) {
                    g2d.setColor(new Color(255, 255, 255, 12));
                    g2d.fillRoundRect(cx, cy, cardSize, cardSize, 12, 12);
                }

                int size = 20;
                int ix = (getWidth() - size) / 2;
                int iy = (getHeight() - size) / 2;
                
                g2d.setColor(hover ? UIConstants.TEXT_WHITE : UIConstants.TEXT_MUTED);
                if ("logout".equals(viewId) && hover) {
                    g2d.setColor(UIConstants.SIGNAL_RED);
                }
                g2d.setStroke(new BasicStroke(1.8f));

                if ("logout".equals(viewId)) {
                    g2d.drawLine(ix + 7, iy, ix + 2, iy);
                    g2d.drawLine(ix + 2, iy, ix + 2, iy + size);
                    g2d.drawLine(ix + 2, iy + size, ix + 7, iy + size);
                    g2d.drawLine(ix + 7, iy + size / 2, ix + size - 1, iy + size / 2);
                    g2d.drawLine(ix + size - 5, iy + size / 2 - 4, ix + size - 1, iy + size / 2);
                    g2d.drawLine(ix + size - 5, iy + size / 2 + 4, ix + size - 1, iy + size / 2);
                }

                g2d.dispose();
            }
        }
    }
}
