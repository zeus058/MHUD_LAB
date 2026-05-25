package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import vn.edu.hcmus.securechat.client.model.SecurityState;
import vn.edu.hcmus.securechat.client.model.SecurityState.ConnectionStatus;

/**
 * Panel hiển thị trạng thái bảo mật real-time (Contrains.md §8.2).
 */
public class SecurityMonitorPanel extends JPanel {

    private static final int VALUE_WRAP_PX = 260;

    private final JLabel statusValue;
    private final JLabel tgtValue;
    private final JLabel stValue;
    private final JLabel encryptionValue;
    private final JLabel certificateValue;
    private final JLabel messagesValue;
    private final JPanel statusDot;

    public SecurityMonitorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBackground(UIConstants.DEEP_CARBON);
        setPreferredSize(new Dimension(320, 0));
        setMinimumSize(new Dimension(300, 0));

        UiStyles.RoundedPanel card = UiStyles.cardPanel();
        card.setLayout(new BorderLayout(0, 12));

        JPanel titleRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        statusDot = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        statusDot.setOpaque(false);
        statusDot.setBackground(UIConstants.SIGNAL_RED);
        statusDot.setPreferredSize(new Dimension(12, 12));
        titleRow.add(statusDot);
        titleRow.add(UiStyles.headingLabel("Giám sát bảo mật"));
        card.add(titleRow, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        statusValue = createValueLabel();
        tgtValue = createValueLabel();
        stValue = createValueLabel();
        encryptionValue = createValueLabel();
        certificateValue = createValueLabel();
        messagesValue = createValueLabel();

        rows.add(metricBlock("Trạng thái kết nối", statusValue));
        rows.add(gap());
        rows.add(metricBlock("Vé TGT (đăng nhập Kerberos)", tgtValue));
        rows.add(gap());
        rows.add(metricBlock("Vé ST (dịch vụ chat)", stValue));
        rows.add(gap());
        rows.add(metricBlock("Lớp mã hóa tin nhắn", encryptionValue));
        rows.add(gap());
        rows.add(metricBlock("Chứng chỉ X.509", certificateValue));
        rows.add(gap());
        rows.add(metricBlock("Thống kê tin nhắn", messagesValue));

        card.add(rows, BorderLayout.CENTER);
        add(card, BorderLayout.NORTH);
        setBorder(new EmptyBorder(12, UIConstants.PADDING, UIConstants.PADDING, UIConstants.PADDING));
    }

    private static JPanel metricBlock(String title, JLabel value) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JLabel key = UiStyles.mutedLabel(title);
        key.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(key);
        block.add(Box.createVerticalStrut(4));
        block.add(value);
        return block;
    }

    private static Component gap() {
        return Box.createVerticalStrut(10);
    }

    private static JLabel createValueLabel() {
        JLabel label = UiStyles.bodyLabel("—");
        label.setForeground(UIConstants.TEXT_WHITE);
        return label;
    }

    public void updateState(SecurityState state) {
        ConnectionStatus status = state.getStatus();
        setWrappedText(statusValue, status.getLabel(), status.isSecure()
                ? UIConstants.SECURE_TEAL : UIConstants.TEXT_SILVER);
        statusDot.setBackground(status.isSecure() ? UIConstants.SECURE_TEAL : UIConstants.SIGNAL_RED);

        setWrappedText(tgtValue, "Còn " + state.getTgtRemaining(), UIConstants.TEXT_WHITE);
        setWrappedText(stValue, "Còn " + state.getStRemaining(), UIConstants.TEXT_WHITE);

        setWrappedText(encryptionValue, formatEncryption(state.getEncryption()), UIConstants.SECURE_TEAL);

        String cert = state.getCertificate();
        Color certColor = cert.startsWith("Hợp lệ") ? UIConstants.SECURE_TEAL : UIConstants.SIGNAL_RED;
        setWrappedText(certificateValue, formatCertificate(cert), certColor);

        setWrappedText(messagesValue,
                "Đã gửi " + state.getSentCount() + " · Đã nhận " + state.getReceivedCount(),
                UIConstants.TEXT_SILVER);
    }

    private static String formatEncryption(String encryption) {
        if (encryption == null || encryption.isBlank() || "—".equals(encryption)) {
            return "—";
        }
        return encryption
                .replace(" + ", "\n")
                .replace("AES-256-GCM", "AES-256-GCM (tin nhắn)")
                .replace("ML-KEM-768", "ML-KEM-768 (PQC)")
                .replace("Double Ratchet", "Double Ratchet (message key)");
    }

    private static String formatCertificate(String cert) {
        if (cert == null || cert.isBlank()) {
            return "—";
        }
        if (cert.startsWith("Không có")) {
            return cert;
        }
        String[] parts = cert.split(" · ");
        if (parts.length >= 3) {
            String status = parts[0];
            String cn = parts[1];
            String expiry = parts[2].replace("đến ", "Hết hạn: ");
            return status + "\n@" + cn + "\n" + expiry;
        }
        return cert;
    }

    private static void setWrappedText(JLabel label, String text, Color color) {
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        String body = escapeHtml(text).replace("\n", "<br/>");
        label.setText("<html><body style='width:" + VALUE_WRAP_PX + "px;margin:0;"
                + "font-family:Segoe UI;font-size:14px;color:" + hex + ";'>" + body + "</body></html>");
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
