package vn.edu.hcmus.securechat.client.ui.components;

import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatPanel — Vùng hiển thị tin nhắn và nhập liệu.
 *
 * Kết nối UIController:
 *   chatPanel.setSendListener((msg) -> controller.sendMessage(recipientId, msg));
 */
public class ChatPanel extends JPanel {

    public record ChatMessage(String sender, String content, boolean isMe, LocalTime time) {}

    public interface SendListener { void onSend(String message); }

    // === State ===
    private String contactName  = "";
    private int    contactColor = 0;
    private boolean isOnline    = false;
    private final List<ChatMessage> messages = new ArrayList<>();
    private SendListener sendListener;

    // === UI ===
    private JPanel      messagesPanel;
    private JScrollPane scrollPane;
    private JTextField  tfInput;
    private AvatarLabel headerAvatar;
    private JLabel      lblContactName, lblStatus, lblE2EE;

    public ChatPanel() {
        setLayout(new BorderLayout());
        setBackground(AppTheme.SURFACE);
        buildUI();
    }

    private void buildUI() {
        add(buildHeader(),    BorderLayout.NORTH);
        add(buildMessages(),  BorderLayout.CENTER);
        add(buildInputArea(), BorderLayout.SOUTH);
    }

    // ─── Header ───
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(AppTheme.SURFACE);
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Bottom border
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
            new EmptyBorder(12, 16, 12, 16)
        ));

        // Left: avatar + name/status
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        headerAvatar = new AvatarLabel("", 0, 36);
        left.add(headerAvatar);

        JPanel nameBox = new JPanel();
        nameBox.setOpaque(false);
        nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
        lblContactName = new JLabel("Chọn một cuộc trò chuyện");
        lblContactName.setFont(AppTheme.FONT_MEDIUM.deriveFont(14f));
        lblContactName.setForeground(AppTheme.TEXT_PRIMARY);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        statusRow.setOpaque(false);
        lblStatus = new JLabel("—");
        lblStatus.setFont(AppTheme.FONT_SMALL.deriveFont(12f));
        lblStatus.setForeground(AppTheme.TEXT_SECONDARY);

        lblE2EE = new JLabel();
        lblE2EE.setVisible(false);

        statusRow.add(lblStatus);
        statusRow.add(lblE2EE);

        nameBox.add(lblContactName);
        nameBox.add(statusRow);
        left.add(nameBox);
        header.add(left, BorderLayout.CENTER);

        // Right: actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(buildIconBtn("🔑", "Thông tin phiên"));
        actions.add(buildIconBtn("ℹ", "Thông tin liên hệ"));
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JButton buildIconBtn(String icon, String tooltip) {
        JButton btn = new JButton(icon) {
            @Override protected void paintComponent(Graphics g) {
                if (getModel().isRollover()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(AppTheme.SURFACE_2);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(32, 32));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ─── Messages area ───
    private JScrollPane buildMessages() {
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(AppTheme.SURFACE_2);
        messagesPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        scrollPane = new JScrollPane(messagesPanel);
        scrollPane.setBackground(AppTheme.SURFACE_2);
        scrollPane.getViewport().setBackground(AppTheme.SURFACE_2);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    // ─── Input area ───
    private JPanel buildInputArea() {
        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.setBackground(AppTheme.SURFACE);
        input.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
            new EmptyBorder(10, 14, 10, 14)
        ));

        // Attach button
        JButton btnAttach = buildIconBtn("📎", "Đính kèm file");
        input.add(btnAttach, BorderLayout.WEST);

        // Text field với bo góc
        tfInput = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppTheme.SURFACE_2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppTheme.RADIUS_PILL * 2, AppTheme.RADIUS_PILL * 2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tfInput.setFont(AppTheme.FONT_REGULAR.deriveFont(13f));
        tfInput.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn...");
        tfInput.setOpaque(false);
        tfInput.setBorder(new EmptyBorder(8, 14, 8, 14));
        tfInput.addActionListener(e -> doSend());
        input.add(tfInput, BorderLayout.CENTER);

        // Send button
        JButton btnSend = new JButton("➤") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? AppTheme.PRIMARY_HOVER : AppTheme.PRIMARY;
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("➤", (getWidth() - fm.stringWidth("➤")) / 2,
                               (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        btnSend.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        btnSend.setPreferredSize(new Dimension(36, 36));
        btnSend.setContentAreaFilled(false);
        btnSend.setBorderPainted(false);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> doSend());
        input.add(btnSend, BorderLayout.EAST);

        return input;
    }

    // ─── Send logic ───
    private void doSend() {
        String text = tfInput.getText().trim();
        if (text.isEmpty()) return;
        tfInput.setText("");

        ChatMessage msg = new ChatMessage("Tôi", text, true, LocalTime.now());
        addMessage(msg);

        if (sendListener != null) sendListener.onSend(text);
    }

    // ─── Public API ───

    public void setContact(String name, int colorIndex, boolean online) {
        this.contactName  = name;
        this.contactColor = colorIndex;
        this.isOnline     = online;

        headerAvatar.update(name, colorIndex);
        lblContactName.setText(name);
        lblStatus.setText(online ? "● Online" : "○ Offline");
        lblStatus.setForeground(online ? AppTheme.SUCCESS : AppTheme.TEXT_SECONDARY);
        lblE2EE.setVisible(true);

        // E2EE badge
        RoundedPanel badge = new RoundedPanel(AppTheme.RADIUS_PILL);
        badge.setBackground(AppTheme.SUCCESS_BG);
        badge.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel bl = new JLabel("🔒 E2EE");
        bl.setFont(AppTheme.FONT_SMALL.deriveFont(10f));
        bl.setForeground(new Color(0x085041));
        badge.add(bl);

        repaint();
    }

    public void addSystemMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
            wrap.setOpaque(false);
            wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

            RoundedPanel bubble = new RoundedPanel(AppTheme.RADIUS_PILL, AppTheme.BORDER);
            bubble.setBackground(AppTheme.SURFACE);
            bubble.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 4));
            JLabel lbl = new JLabel("🔒 " + text);
            lbl.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
            lbl.setForeground(AppTheme.TEXT_SECONDARY);
            bubble.add(lbl);
            wrap.add(bubble);

            messagesPanel.add(wrap);
            messagesPanel.add(Box.createVerticalStrut(8));
            refreshScroll();
        });
    }

    public void addMessage(ChatMessage msg) {
        SwingUtilities.invokeLater(() -> {
            messagesPanel.add(buildMessageRow(msg));
            messagesPanel.add(Box.createVerticalStrut(10));
            refreshScroll();
        });
    }

    public void clearMessages() {
        SwingUtilities.invokeLater(() -> {
            messagesPanel.removeAll();
            messages.clear();
            repaint();
        });
    }

    private JPanel buildMessageRow(ChatMessage msg) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (msg.isMe()) {
            row.add(Box.createHorizontalGlue());
            row.add(buildBubble(msg));
        } else {
            row.add(new AvatarLabel(msg.sender(), contactColor, 28));
            row.add(Box.createHorizontalStrut(6));
            row.add(buildBubble(msg));
            row.add(Box.createHorizontalGlue());
        }
        return row;
    }

    private JPanel buildBubble(ChatMessage msg) {
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));

        // Bubble chính
        boolean isMe = msg.isMe();
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isMe ? AppTheme.BUBBLE_ME : AppTheme.BUBBLE_OTHER;
                g2.setColor(bg);
                // Bo góc không đều: góc gần avatar vuông hơn
                int r = AppTheme.RADIUS_LG;
                int w = getWidth(), h = getHeight();
                if (isMe) {
                    // Top-right gần vuông
                    g2.fillRoundRect(0, 0, w, h, r * 2, r * 2);
                    g2.fillRect(w - r, 0, r, r);
                } else {
                    // Top-left gần vuông
                    g2.fillRoundRect(0, 0, w, h, r * 2, r * 2);
                    g2.fillRect(0, 0, r, r);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));

        // Wrap text
        JLabel lblText = new JLabel("<html><body style='width:220px'>" +
                                    msg.content().replace("\n","<br>") + "</body></html>");
        lblText.setFont(AppTheme.FONT_REGULAR.deriveFont(13f));
        lblText.setForeground(isMe ? AppTheme.BUBBLE_ME_TEXT : AppTheme.BUBBLE_OTHER_TEXT);
        bubble.add(lblText);

        // Time + status row
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        JPanel timeRow = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 0));
        timeRow.setOpaque(false);
        JLabel lblTime = new JLabel(msg.time().format(fmt));
        lblTime.setFont(AppTheme.FONT_SMALL.deriveFont(10f));
        lblTime.setForeground(isMe ? new Color(0xCCCCFF) : AppTheme.TEXT_HINT);
        timeRow.add(lblTime);
        if (isMe) {
            JLabel lblCheck = new JLabel("✓✓");
            lblCheck.setFont(AppTheme.FONT_SMALL.deriveFont(10f));
            lblCheck.setForeground(new Color(0x9FE1CB));
            timeRow.add(lblCheck);
        }

        outer.add(bubble);
        outer.add(timeRow);
        outer.setMaximumSize(new Dimension(300, Integer.MAX_VALUE));
        return outer;
    }

    private void refreshScroll() {
        messagesPanel.revalidate();
        messagesPanel.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    public void setSendListener(SendListener l) { this.sendListener = l; }
    public void requestInputFocus()             { tfInput.requestFocus(); }
}