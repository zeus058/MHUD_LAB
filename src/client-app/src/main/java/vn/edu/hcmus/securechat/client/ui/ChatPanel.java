package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.client.model.SecurityState;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;

/**
 * Giao diện chat chính — danh sách user, khu vực tin nhắn, Security Monitor.
 */
public class ChatPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ChatPanel.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public interface ChatListener {
        void onLogout();
    }

    private final ChatListener listener;
    private final String username;
    private final E2eeCryptoService e2ee;
    private final SecurityState securityState;
    private final SecurityMonitorPanel securityPanel;
    private JPanel messageContainer;
    private JTextField messageInput;
    private JLabel peerTitle;
    private JLabel connectionBadge;
    private String selectedPeer = null;
    private DefaultListModel<String> userListModel;

    public ChatPanel(String username, E2eeCryptoService e2ee, ChatListener listener) {
        this.username = username;
        this.e2ee = e2ee;
        this.listener = listener;
        this.securityState = SecurityState.fromRealSession(
                vn.edu.hcmus.securechat.client.crypto.PkiManager.getCertificate());
        this.securityPanel = new SecurityMonitorPanel();
        securityPanel.updateState(securityState);

        setLayout(new BorderLayout());
        setBackground(UIConstants.DEEP_CARBON);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        // Bắt đầu thread nhận tin nhắn từ Chat Server
        startReceiverThread();
    }

    public SecurityMonitorPanel getSecurityPanel() {
        return securityPanel;
    }

    public SecurityState getSecurityState() {
        return securityState;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(UIConstants.DARK_SILVER);
        header.setBorder(new EmptyBorder(14, UIConstants.PADDING, 14, UIConstants.PADDING));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        JLabel lock = UiStyles.headingLabel("🔒");
        brand.add(lock);
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        JLabel appName = UiStyles.headingLabel("SecureChat E2EE");
        JLabel userLine = UiStyles.mutedLabel("@" + username);
        userLine.setForeground(UIConstants.SECURE_TEAL);
        titles.add(appName);
        titles.add(userLine);
        brand.add(titles);
        header.add(brand, BorderLayout.WEST);

        connectionBadge = UiStyles.mutedLabel("● E2EE");
        connectionBadge.setForeground(UIConstants.SECURE_TEAL);
        connectionBadge.setFont(UIConstants.FONT_SMALL.deriveFont(Font.BOLD));
        header.add(connectionBadge, BorderLayout.CENTER);

        JButton logout = UiStyles.ghostButton("Đăng xuất");
        logout.setPreferredSize(new Dimension(110, 36));
        logout.addActionListener(e -> listener.onLogout());
        header.add(logout, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setOpaque(false);
        body.add(buildSidebar(), BorderLayout.WEST);
        body.add(buildChatArea(), BorderLayout.CENTER);
        body.add(securityPanel, BorderLayout.EAST);
        securityPanel.setPreferredSize(new Dimension(280, 0));
        return body;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setOpaque(true);
        sidebar.setBackground(UIConstants.DARK_SILVER);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIConstants.DEEP_CARBON));

        JLabel title = UiStyles.mutedLabel("  TRỰC TUYẼN");
        title.setBorder(new EmptyBorder(14, 8, 8, 8));
        sidebar.add(title, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userListModel.addElement("(chờ kết nối...)");

        JList<String> userList = new JList<>(userListModel);
        userList.setBackground(UIConstants.DARK_SILVER);
        userList.setForeground(UIConstants.TEXT_SILVER);
        userList.setFont(UIConstants.FONT_BODY);
        userList.setSelectionBackground(UIConstants.SECURE_TEAL);
        userList.setSelectionForeground(UIConstants.TEXT_WHITE);
        userList.setFixedCellHeight(44);
        userList.setBorder(new EmptyBorder(0, 8, 8, 8));
        userList.setCellRenderer(new UserCellRenderer());
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = userList.getSelectedValue();
                if (sel != null && !sel.equals(username)) {
                    selectedPeer = sel;
                    peerTitle.setText(selectedPeer);
                }
            }
        });

        sidebar.add(new JScrollPane(userList) {{
            setBorder(BorderFactory.createEmptyBorder());
            getViewport().setBackground(UIConstants.DARK_SILVER);
            setBackground(UIConstants.DARK_SILVER);
        }}, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel buildChatArea() {
        JPanel chat = new JPanel(new BorderLayout());
        chat.setOpaque(true);
        chat.setBackground(UIConstants.DEEP_CARBON);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(12, UIConstants.PADDING, 8, UIConstants.PADDING));
        peerTitle = UiStyles.headingLabel(selectedPeer);
        topBar.add(peerTitle, BorderLayout.WEST);
        JLabel encryptedHint = UiStyles.mutedLabel("Tin nhắn được mã hóa AES-256-GCM");
        encryptedHint.setForeground(UIConstants.SECURE_TEAL);
        topBar.add(encryptedHint, BorderLayout.EAST);
        chat.add(topBar, BorderLayout.NORTH);

        messageContainer = new JPanel();
        messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.Y_AXIS));
        messageContainer.setBackground(UIConstants.DEEP_CARBON);
        messageContainer.setBorder(new EmptyBorder(8, UIConstants.PADDING, 8, UIConstants.PADDING));

        seedDemoMessages();

        JScrollPane scroll = UiStyles.styledScrollPane(messageContainer);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chat.add(scroll, BorderLayout.CENTER);
        chat.add(buildComposer(), BorderLayout.SOUTH);
        return chat;
    }

    private void seedDemoMessages() {
        // Không còn demo — chờ tin nhắn thực tế từ server
        addMessageBubble("Kết nối E2EE thành công. Chọ tin nhắn...", false, LocalTime.now().format(TIME_FMT));
    }

    private JPanel buildComposer() {
        JPanel composer = new JPanel(new BorderLayout(10, 0));
        composer.setOpaque(true);
        composer.setBackground(UIConstants.DARK_SILVER);
        composer.setBorder(new EmptyBorder(12, UIConstants.PADDING, 12, UIConstants.PADDING));

        messageInput = UiStyles.styledTextField(1);
        messageInput.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn...");
        composer.add(messageInput, BorderLayout.CENTER);

        JButton send = UiStyles.primaryButton("Gửi");
        send.setPreferredSize(new Dimension(96, 42));
        send.addActionListener(e -> sendMessage());
        messageInput.addActionListener(e -> sendMessage());
        composer.add(send, BorderLayout.EAST);
        return composer;
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty() || selectedPeer == null) {
            return;
        }
        messageInput.setEnabled(false);
        String time = LocalTime.now().format(TIME_FMT);
        String textToSend = text;
        messageInput.setText("");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    Socket sock = e2ee.getChatSocket();
                    if (sock == null || sock.isClosed()) return false;

                    // Tạo JSON tin nhắn
                    String msgJson = "{\"senderId\":\"" + username + "\","
                            + "\"text\":\"" + textToSend.replace("\"", "\\\"")
                            + "\"}"; 
                    byte[] plain = msgJson.getBytes(StandardCharsets.UTF_8);
                    byte[] encrypted = AesGcmCipher.encrypt(e2ee.getMasterSessionKey(), plain);

                    // Tạo envelope
                    String envelopeJson = "{\"recipientId\":\"" + selectedPeer + "\","
                            + "\"payload\":\"" + Base64.getEncoder().encodeToString(encrypted) + "\"}"; 
                    PacketFrame.write(sock.getOutputStream(), 
                            PacketFrame.TYPE_CHAT_MESSAGE,
                            envelopeJson.getBytes(StandardCharsets.UTF_8));
                    return true;
                } catch (Exception ex) {
                    log.error("Gửii tin nhắn thất bại", ex);
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (Boolean.TRUE.equals(get())) {
                        addMessageBubble(textToSend, true, time);
                        securityState.incrementSent();
                        securityPanel.updateState(securityState);
                    }
                } catch (Exception e) { log.error("send done error", e); }
                messageInput.setEnabled(true);
                messageInput.requestFocusInWindow();
            }
        }.execute();
    }

    /**
     * Thread nền liên tục đọc packet từ Chat Server socket.
     */
    private void startReceiverThread() {
        Thread t = new Thread(() -> {
            try {
                Socket sock = e2ee.getChatSocket();
                if (sock == null) return;
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
                // Cập nhật danh sách user
                String json = new String(frame.getPayload(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                List<String> users = vn.edu.hcmus.securechat.common.protocol.JsonSerializer
                        .getMapper().readValue(json, List.class);
                SwingUtilities.invokeLater(() -> {
                    userListModel.clear();
                    for (String u : users) {
                        if (!u.equals(username)) userListModel.addElement(u);
                    }
                    if (selectedPeer == null && !userListModel.isEmpty()) {
                        selectedPeer = userListModel.get(0);
                        peerTitle.setText(selectedPeer);
                    }
                });
            } else if (frame.getType() == PacketFrame.TYPE_CHAT_MESSAGE) {
                // Giải mã và hiển thị tin nhắn
                String envelopeJson = new String(frame.getPayload(), StandardCharsets.UTF_8);
                com.fasterxml.jackson.databind.JsonNode node = 
                        vn.edu.hcmus.securechat.common.protocol.JsonSerializer
                        .getMapper().readTree(envelopeJson);
                byte[] encrypted = Base64.getDecoder().decode(node.get("payload").asText());
                byte[] plain = AesGcmCipher.decrypt(e2ee.getMasterSessionKey(), encrypted);
                com.fasterxml.jackson.databind.JsonNode msg =
                        vn.edu.hcmus.securechat.common.protocol.JsonSerializer
                        .getMapper().readTree(plain);
                String sender = msg.get("senderId").asText();
                String text   = msg.get("text").asText();
                String time   = LocalTime.now().format(TIME_FMT);
                SwingUtilities.invokeLater(() -> {
                    addMessageBubble(sender + ": " + text, false, time);
                    securityState.incrementReceived();
                    securityPanel.updateState(securityState);
                });
            }
        } catch (Exception ex) {
            log.warn("Lỗi xử lý frame từ server", ex);
        }
    }

    private void addMessageBubble(String text, boolean outgoing, String time) {
        JPanel row = new JPanel(new FlowLayout(
                outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        row.add(new MessageBubble(text, time, outgoing));
        messageContainer.add(row);
        messageContainer.add(Box.createVerticalStrut(4));
        messageContainer.revalidate();
        messageContainer.repaint();
    }

    private static final class MessageBubble extends JPanel {

        MessageBubble(String text, String time, boolean outgoing) {
            setLayout(new BorderLayout(0, 4));
            setOpaque(false);

            int maxW = 340;
            JTextArea body = new JTextArea(text);
            body.setFont(UIConstants.FONT_BODY);
            body.setForeground(outgoing ? UIConstants.TEXT_WHITE : UIConstants.TEXT_SILVER);
            body.setBackground(outgoing ? UIConstants.SECURE_TEAL : UIConstants.DARK_SILVER);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setEditable(false);
            body.setBorder(new EmptyBorder(10, 14, 6, 14));
            body.setOpaque(true);
            Dimension pref = body.getPreferredSize();
            body.setPreferredSize(new Dimension(Math.min(maxW, pref.width + 20), pref.height));

            JLabel timeLabel = UiStyles.mutedLabel(time);
            timeLabel.setForeground(outgoing ? UIConstants.TEXT_WHITE : UIConstants.TEXT_SILVER);
            timeLabel.setBorder(new EmptyBorder(0, 14, 8, 14));
            timeLabel.setOpaque(true);
            timeLabel.setBackground(body.getBackground());

            add(body, BorderLayout.CENTER);
            add(timeLabel, BorderLayout.SOUTH);
            setMaximumSize(new Dimension(maxW + 20, Integer.MAX_VALUE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Component body = getComponent(0);
            g2.setColor(body.getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
            g2.dispose();
        }
    }

    private static final class UserCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText("  ●  " + value);
            label.setBorder(new EmptyBorder(4, 8, 4, 8));
            if (isSelected) {
                label.setBackground(UIConstants.SECURE_TEAL);
                label.setForeground(UIConstants.TEXT_WHITE);
            } else {
                label.setBackground(UIConstants.DARK_SILVER);
                label.setForeground(UIConstants.TEXT_SILVER);
            }
            label.setFont(UIConstants.FONT_BODY);
            return label;
        }
    }
}
