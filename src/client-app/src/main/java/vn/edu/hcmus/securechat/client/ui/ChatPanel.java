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
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.E2eeInitMessage;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
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
    private int messageCount = 0;

    private boolean rightRailOpen = true;
    private JPanel rightRail;
    private JButton connHeaderBtn;

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

        JScrollPane userScroll = UiStyles.styledScrollPane(userList);
        userScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        userScroll.getViewport().setOpaque(false);
        userScroll.setBackground(new Color(0, 0, 0, 0));
        userScroll.setOpaque(false);
        sidebar.add(userScroll, BorderLayout.CENTER);
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

        actionsRow.add(createHeaderButton("phone"));
        actionsRow.add(createHeaderButton("video"));
        actionsRow.add(createHeaderButton("more"));

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
        actionsRow.add(connHeaderBtn);

        topBar.add(actionsRow, BorderLayout.EAST);

        hintWrap = new JPanel();
        hintWrap.setVisible(false);
        chat.add(topBar, BorderLayout.NORTH);

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
                addMessageBubbleInternal(msg.getContent(), outgoing, time);
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
                    Socket sock = e2ee.getChatSocket();
                    if (sock == null || sock.isClosed()) {
                        return false;
                    }

                    EncryptedChatEnvelope envelope = e2ee.encryptForPeer(peerToSend, textToSend);
                    e2ee.sendFrame(PacketFrame.TYPE_CHAT_MESSAGE, JsonSerializer.toBytes(envelope));
                    return true;
                } catch (Exception ex) {
                    log.error("Gửi tin nhắn thất bại", ex);
                    flow("Trao đổi khóa hoặc gửi tin thất bại", ex.getMessage(), ActivityFlowPanel.Tone.ERROR);
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
            for (ConversationItem item : users) {
                userListModel.addElement(item);
            }
            if (selectedPeer != null && findIn(users, selectedPeer) == null) {
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
        ConversationItem item = new ConversationItem(peer, online, preKeyAvailable,
                Instant.now().getEpochSecond(), false);
        addConversation(item);
        return item;
    }

    private void addConversation(ConversationItem item) {
        if (userListModel.size() == 1 && userListModel.get(0).placeholder) {
            userListModel.clear();
        }
        userListModel.addElement(item);
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
        if (this.messageCount == 0) {
            messageContainer.removeAll();
            messageContainer.add(Box.createVerticalGlue());
        }
        addMessageBubbleInternal(text, outgoing, time);
        this.messageCount++;
        scrollMessagesToEnd();
    }

    private void addMessageBubbleInternal(String text, boolean outgoing, String time) {
        JPanel row = new JPanel(new FlowLayout(
                outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if (!outgoing) {
            AvatarBadge avatar = new AvatarBadge(selectedPeer, Color.decode("#5B54F6"), UIConstants.SECURE_TEAL);
            avatar.setPreferredSize(new Dimension(26, 26));
            row.add(avatar);
            row.add(Box.createHorizontalStrut(8));
        }

        JPanel bubbleAndMeta = new JPanel();
        bubbleAndMeta.setLayout(new BoxLayout(bubbleAndMeta, BoxLayout.Y_AXIS));
        bubbleAndMeta.setOpaque(false);

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
        private boolean online;
        private boolean preKeyAvailable;
        private long lastSeenAt;
        private final boolean placeholder;
        private int unreadCount;

        private ConversationItem(String userId, boolean online, boolean preKeyAvailable,
                long lastSeenAt, boolean placeholder) {
            this.userId = userId;
            this.online = online;
            this.preKeyAvailable = preKeyAvailable;
            this.lastSeenAt = lastSeenAt;
            this.placeholder = placeholder;
        }

        static ConversationItem placeholder(String text) {
            return new ConversationItem(text, false, false, 0L, true);
        }

        static ConversationItem manual(String userId) {
            return new ConversationItem(userId, false, false, 0L, false);
        }

        static ConversationItem legacy(String userId) {
            return new ConversationItem(userId, true, false, Instant.now().getEpochSecond(), false);
        }

        static ConversationItem from(UserListEntry entry) {
            if (entry == null || entry.getUserId() == null || entry.getUserId().isBlank()) {
                return null;
            }
            return new ConversationItem(entry.getUserId(), entry.isOnline(),
                    entry.isPreKeyAvailable(), entry.getLastSeenAt(), false);
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
            return placeholder ? userId : userId;
        }

        String statusText() {
            if (placeholder) {
                return "Waiting for server...";
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
            g2.setColor(fill);
            int avatarSize = Math.min(getWidth(), getHeight()) - (online ? 5 : 1);
            g2.fillOval(0, 0, avatarSize, avatarSize);
            String initials = initials(text);
            g2.setFont(UIConstants.FONT_MONO.deriveFont(Font.BOLD, Math.max(11f, getWidth() / 3f)));
            g2.setColor(UIConstants.TEXT_WHITE);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int x = (avatarSize - fm.stringWidth(initials)) / 2;
            int y = (avatarSize - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(initials, x, y);
            if (online) {
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

            int maxW = 300;
            JTextArea body = new JTextArea(text);
            body.setFont(UIConstants.FONT_BODY);
            body.setForeground(UIConstants.TEXT_SILVER);
            body.setBackground(bubbleColor);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setEditable(false);
            body.setBorder(new EmptyBorder(10, 14, 10, 14));
            body.setOpaque(false);

            Dimension pref = body.getPreferredSize();
            int width;
            if (pref.width <= maxW) {
                width = Math.max(50, pref.width);
            } else {
                width = maxW;
            }
            body.setSize(new Dimension(width, 1));
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
