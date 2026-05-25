package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
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
    private JTextField contactField;
    private JLabel peerTitle;
    private JLabel peerStatus;
    private JPanel hintWrap;
    private String selectedPeer;
    private DefaultListModel<ConversationItem> userListModel;
    private JList<ConversationItem> userList;
    private boolean updatingUserList;


    public ChatPanel(String username, E2eeCryptoService e2ee,
            vn.edu.hcmus.securechat.client.db.LocalDatabase localDb, ChatListener listener) {
        this.username = username;
        this.e2ee = e2ee;
        this.localDb = localDb;
        this.listener = listener;
        this.activityPanel = new ActivityFlowPanel("Luồng hoạt động", "");
        this.activityPanel.setImportantOnly(true);
        this.activityPanel.setHealthVisible(false);
        this.e2ee.setActivitySink((title, body, tone) ->
                activityPanel.addEvent(title, body, ActivityFlowPanel.Tone.valueOf(tone.name())));

        setLayout(new BorderLayout());
        setBackground(UIConstants.DEEP_CARBON);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        activityPanel.seed(new String[][] {
                {"Pre-Key đã upload", "Signed pre-key và one-time pre-key đã sẵn sàng cho peer offline.", "SUCCESS"},
                {"Phiên truy cập đã mở", "ST, authenticator và Chat handshake đã được xác thực.", "SUCCESS"}
        });

        startReceiverThread();
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

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setOpaque(false);
        body.add(buildSidebar(), BorderLayout.WEST);
        body.add(buildChatArea(), BorderLayout.CENTER);
        body.add(buildRightRail(), BorderLayout.EAST);
        return body;
    }

    private JPanel buildRightRail() {
        JPanel rightRail = new JPanel(new BorderLayout());
        rightRail.setOpaque(false);
        rightRail.setPreferredSize(new Dimension(320, 0));
        rightRail.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UIConstants.OUTLINE));
        rightRail.add(activityPanel, BorderLayout.CENTER);
        return rightRail;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setOpaque(true);
        sidebar.setBackground(UIConstants.DARK_SILVER);
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIConstants.OUTLINE));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.OUTLINE),
                new EmptyBorder(16, 14, 16, 14)));

        top.add(UiStyles.sectionLabel("Bắt đầu trò chuyện"));
        top.add(Box.createVerticalStrut(10));

        JPanel openRow = new JPanel(new BorderLayout(8, 0));
        openRow.setOpaque(false);
        contactField = UiStyles.styledTextField(14);
        contactField.setToolTipText("Nhập username người nhận rồi bấm Mở");
        contactField.addActionListener(e -> openManualConversation());
        JButton open = UiStyles.ghostButton("Mở");
        open.setPreferredSize(new Dimension(66, 40));
        open.addActionListener(e -> openManualConversation());
        openRow.add(contactField, BorderLayout.CENTER);
        openRow.add(open, BorderLayout.EAST);
        top.add(openRow);
        top.add(Box.createVerticalStrut(20));
        top.add(UiStyles.sectionLabel("Hội thoại gần đây"));
        sidebar.add(top, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userListModel.addElement(ConversationItem.placeholder("Đang tải danh sách..."));

        userList = new JList<>(userListModel);
        userList.setBackground(UIConstants.DARK_SILVER);
        userList.setForeground(UIConstants.TEXT_SILVER);
        userList.setFont(UIConstants.FONT_BODY);
        userList.setSelectionBackground(UIConstants.SECURE_TEAL);
        userList.setSelectionForeground(UIConstants.TEXT_WHITE);
        userList.setFixedCellHeight(76);
        userList.setBorder(new EmptyBorder(4, 8, 10, 8));
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
        userScroll.getViewport().setBackground(UIConstants.DARK_SILVER);
        userScroll.setBackground(UIConstants.DARK_SILVER);
        sidebar.add(userScroll, BorderLayout.CENTER);
        return sidebar;
    }

    private JButton navButton(String text, boolean active) {
        JButton button = UiStyles.linkButton(text);
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setForeground(active ? UIConstants.SECURE_TEAL : UIConstants.TEXT_SILVER);
        button.setFont(UIConstants.FONT_MONO.deriveFont(Font.BOLD, 12f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, active ? 2 : 0, 0, 0,
                        active ? UIConstants.SECURE_TEAL : UIConstants.DARK_SILVER),
                new EmptyBorder(9, active ? 12 : 14, 9, 12)));
        button.setOpaque(active);
        button.setBackground(active ? UIConstants.SURFACE_HIGH : UIConstants.DARK_SILVER);
        return button;
    }

    private JPanel buildChatArea() {
        JPanel chat = new JPanel(new BorderLayout());
        chat.setOpaque(true);
        chat.setBackground(UIConstants.DEEP_CARBON);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(true);
        topBar.setBackground(UIConstants.DEEP_CARBON);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.OUTLINE),
                new EmptyBorder(12, 22, 12, 22)));

        JPanel peerBlock = new JPanel();
        peerBlock.setOpaque(false);
        peerBlock.setLayout(new BoxLayout(peerBlock, BoxLayout.Y_AXIS));
        peerTitle = UiStyles.appTitleLabel("Chưa chọn hội thoại");
        peerStatus = UiStyles.mutedLabel("Tin nhắn sẽ chỉ mở sau khi bạn chọn một hội thoại.");
        peerStatus.setForeground(UIConstants.TEXT_SILVER);
        peerBlock.add(peerTitle);
        peerBlock.add(Box.createVerticalStrut(3));
        peerBlock.add(peerStatus);
        topBar.add(peerBlock, BorderLayout.WEST);

        hintWrap = new JPanel();
        hintWrap.setVisible(false);
        chat.add(topBar, BorderLayout.NORTH);

        messageContainer = new JPanel();
        messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.Y_AXIS));
        messageContainer.setBackground(UIConstants.DEEP_CARBON);
        messageContainer.setBorder(new EmptyBorder(16, 22, 16, 22));

        loadChatHistory();

        messageScroll = UiStyles.styledScrollPane(messageContainer);
        messageScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chat.add(messageScroll, BorderLayout.CENTER);
        chat.add(buildComposer(), BorderLayout.SOUTH);
        return chat;
    }

    private JPanel buildComposer() {
        JPanel composer = new JPanel(new BorderLayout(10, 0));
        composer.setOpaque(true);
        composer.setBackground(UIConstants.DARK_SILVER);
        composer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIConstants.OUTLINE),
                new EmptyBorder(12, 18, 12, 18)));

        messageInput = UiStyles.styledTextField(1);
        messageInput.setToolTipText("Nhập tin nhắn (Enter để gửi)");
        messageInput.setEnabled(false);
        composer.add(messageInput, BorderLayout.CENTER);

        JButton send = UiStyles.primaryButton("Gửi");
        send.setPreferredSize(new Dimension(92, 42));
        send.addActionListener(e -> sendMessage());
        messageInput.addActionListener(e -> sendMessage());
        composer.add(send, BorderLayout.EAST);
        return composer;
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
        loadChatHistory();
        userList.repaint();
        messageInput.requestFocusInWindow();
    }

    private void updatePeerHeader(ConversationItem item) {
        if (selectedPeer == null) {
            peerTitle.setText("Chưa chọn hội thoại");
            peerStatus.setText("Tin nhắn sẽ chỉ mở sau khi bạn chọn một hội thoại.");
            if (hintWrap != null) {
                hintWrap.setVisible(false);
            }
            return;
        }
        if (hintWrap != null) {
            hintWrap.setVisible(true);
        }
        peerTitle.setText("@" + selectedPeer);
        ConversationItem display = item == null ? ConversationItem.manual(selectedPeer) : item;
        peerStatus.setText(display.statusText());
    }

    private void loadChatHistory() {
        messageContainer.removeAll();
        if (selectedPeer != null && localDb != null) {
            java.util.List<ChatMessage> msgs = localDb.loadMessages(username, selectedPeer);
            for (ChatMessage msg : msgs) {
                boolean outgoing = msg.getSenderId().equals(username);
                String time = Instant.ofEpochSecond(msg.getSentAt())
                        .atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT);
                addMessageBubble(msg.getContent(), outgoing, time);
            }
        }
        if (messageContainer.getComponentCount() == 0) {
            showEmptyState(selectedPeer == null
                    ? "Chọn một hội thoại bên trái để bắt đầu."
                    : "Chưa có tin nhắn. Gửi tin đầu tiên bên dưới.");
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
                messageInput.setEnabled(selectedPeer != null);
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
        JPanel row = new JPanel(new FlowLayout(
                outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        MessageBubble bubble = new MessageBubble(text, time, outgoing);
        row.add(bubble);
        messageContainer.add(row);
        messageContainer.add(Box.createVerticalStrut(4));
        scrollMessagesToEnd();
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
            return placeholder ? userId : "@" + userId;
        }

        String statusText() {
            if (placeholder) {
                return "Đang chờ máy chủ...";
            }
            return online ? "Đang hoạt động" : "Ngoại tuyến";
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
        private final AvatarBadge avatar = new AvatarBadge("", UIConstants.SURFACE, UIConstants.OUTLINE);
        private final JLabel name = new JLabel();
        private final JLabel time = new JLabel();
        private final JLabel badge = new JLabel();
        private final JLabel status = new JLabel();

        UserCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setOpaque(true);
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
        public Component getListCellRendererComponent(JList<? extends ConversationItem> list,
                ConversationItem value, int index, boolean isSelected, boolean cellHasFocus) {
            ConversationItem item = value == null ? ConversationItem.placeholder("") : value;
            setBackground(isSelected ? UIConstants.SURFACE_HIGH : UIConstants.DARK_SILVER);

            avatar.setValues(item.placeholder ? "" : item.userId,
                    UIConstants.SURFACE, item.online ? UIConstants.SECURE_TEAL : UIConstants.OUTLINE);

            name.setText(item.displayName());
            name.setForeground(item.placeholder ? UIConstants.TEXT_MUTED : UIConstants.TEXT_WHITE);
            time.setText(item.timeText());

            status.setText(item.statusText());
            status.setForeground(UIConstants.TEXT_MUTED);

            if (item.unreadCount > 0) {
                badge.setText(" " + item.unreadCount + " ");
                badge.setForeground(UIConstants.TEXT_WHITE);
                badge.setBackground(UIConstants.SIGNAL_RED);
                badge.setOpaque(true);
            } else {
                badge.setText("");
                badge.setOpaque(false);
            }
            return this;
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
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
            g2.setColor(stroke);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
            String initials = initials(text);
            g2.setFont(UIConstants.FONT_MONO.deriveFont(Font.BOLD, Math.max(11f, getWidth() / 3f)));
            g2.setColor(UIConstants.TEXT_SILVER);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(initials)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(initials, x, y);
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

        MessageBubble(String text, String time, boolean outgoing) {
            this.outgoing = outgoing;
            this.bubbleColor = outgoing ? new Color(0, 161, 156, 32) : UIConstants.DARK_SILVER;
            this.borderColor = outgoing ? new Color(0, 161, 156, 96) : UIConstants.OUTLINE;
            setLayout(new BorderLayout(0, 0));
            setOpaque(false);

            int maxW = 350;
            JTextArea body = new JTextArea(text);
            body.setFont(UIConstants.FONT_BODY);
            body.setForeground(UIConstants.TEXT_SILVER);
            body.setBackground(bubbleColor);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setEditable(false);
            body.setBorder(new EmptyBorder(8, 12, 4, 12));
            body.setOpaque(false);
            body.setSize(new Dimension(maxW, Short.MAX_VALUE));
            Dimension pref = body.getPreferredSize();
            int width = Math.min(maxW, Math.max(80, pref.width + 10));
            body.setPreferredSize(new Dimension(width, pref.height));

            JPanel meta = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            meta.setOpaque(false);
            meta.setBorder(new EmptyBorder(0, 12, 6, 12));
            JLabel timeLabel = UiStyles.mutedLabel(time);
            timeLabel.setFont(UIConstants.FONT_MONO.deriveFont(9f));
            timeLabel.setForeground(UIConstants.TEXT_MUTED);
            meta.add(timeLabel);

            add(body, BorderLayout.CENTER);
            add(meta, BorderLayout.SOUTH);
            setMaximumSize(new Dimension(maxW + 20, Integer.MAX_VALUE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bubbleColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
