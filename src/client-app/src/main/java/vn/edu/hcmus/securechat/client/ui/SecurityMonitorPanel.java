package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import vn.edu.hcmus.securechat.client.model.SecurityState;
import vn.edu.hcmus.securechat.client.model.SecurityState.ConnectionStatus;

/**
 * Panel hiển thị trạng thái bảo mật real-time — theo Contrains.md mục 8.2.
 */
public class SecurityMonitorPanel extends JPanel {

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

        UiStyles.RoundedPanel card = UiStyles.cardPanel();
        card.setLayout(new BorderLayout(0, 12));

        JPanel titleRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        statusDot = new JPanel();
        statusDot.setOpaque(true);
        statusDot.setBackground(UIConstants.SIGNAL_RED);
        statusDot.setPreferredSize(new java.awt.Dimension(10, 10));
        titleRow.add(statusDot);
        titleRow.add(UiStyles.headingLabel("Security Monitor"));
        card.add(titleRow, BorderLayout.NORTH);

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        statusValue = addRow(rows, gbc, "🔒 Trạng thái", "DISCONNECTED");
        tgtValue = addRow(rows, gbc, "🎟  TGT còn lại", "—");
        stValue = addRow(rows, gbc, "🎟  ST còn lại", "—");
        encryptionValue = addRow(rows, gbc, "🔐  Mã hóa", "—");
        certificateValue = addRow(rows, gbc, "🪪  Chứng chỉ", "—");
        messagesValue = addRow(rows, gbc, "💬  Tin nhắn", "Đã gửi 0 · Đã nhận 0");

        card.add(rows, BorderLayout.CENTER);
        add(card, BorderLayout.NORTH);
        add(new JPanel(), BorderLayout.CENTER);
        setBorder(new EmptyBorder(0, UIConstants.PADDING, UIConstants.PADDING, UIConstants.PADDING));
    }

    private JLabel addRow(JPanel parent, GridBagConstraints gbc, String label, String initial) {
        gbc.gridy++;
        JLabel key = UiStyles.mutedLabel(label);
        parent.add(key, gbc);

        gbc.gridy++;
        JLabel value = UiStyles.bodyLabel(initial);
        value.setForeground(UIConstants.TEXT_WHITE);
        parent.add(value, gbc);
        return value;
    }

    public void updateState(SecurityState state) {
        ConnectionStatus status = state.getStatus();
        statusValue.setText(status.getLabel());
        statusValue.setForeground(status.isSecure() ? UIConstants.SECURE_TEAL : UIConstants.TEXT_SILVER);
        statusDot.setBackground(status.isSecure() ? UIConstants.SECURE_TEAL : UIConstants.SIGNAL_RED);

        tgtValue.setText(state.getTgtRemaining());
        stValue.setText(state.getStRemaining());
        encryptionValue.setText(state.getEncryption());
        certificateValue.setText(state.getCertificate());
        messagesValue.setText("Đã gửi " + state.getSentCount() + " · Đã nhận " + state.getReceivedCount());

        Color certColor = state.getCertificate().startsWith("VALID")
                ? UIConstants.SECURE_TEAL
                : UIConstants.SIGNAL_RED;
        certificateValue.setForeground(certColor);
    }
}
