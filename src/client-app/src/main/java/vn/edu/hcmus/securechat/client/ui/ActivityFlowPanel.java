package vn.edu.hcmus.securechat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Timeline mô tả luồng xử lý bảo mật bằng ngôn ngữ tự nhiên.
 */
public class ActivityFlowPanel extends JPanel {

    public enum Tone {
        INFO, ACTIVE, SUCCESS, ERROR
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_ROWS = 40;

    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JPanel rows;
    private final JPanel healthCard;
    private final Deque<Component> rowRefs = new ArrayDeque<>();
    private boolean importantOnly;

    public ActivityFlowPanel(String title, String subtitle) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 0));

        UiStyles.RoundedPanel card = UiStyles.surfacePanel(UIConstants.LOG_BG);
        card.setLayout(new BorderLayout(0, 18));
        card.setBorder(BorderFactory.createCompoundBorder(
                UiStyles.focusBorder(false),
                new EmptyBorder(22, 22, 22, 22)));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        titleLabel = UiStyles.appTitleLabel(title);
        header.add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            subtitleLabel = UiStyles.mutedLabel(subtitle);
            subtitleLabel.setForeground(UIConstants.TEXT_MUTED);
            header.add(Box.createVerticalStrut(4));
            header.add(subtitleLabel);
        } else {
            subtitleLabel = new JLabel();
        }
        card.add(header, BorderLayout.NORTH);

        rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JScrollPane scroll = UiStyles.styledScrollPane(rows);
        scroll.getViewport().setBackground(UIConstants.LOG_BG);
        scroll.setBackground(UIConstants.LOG_BG);
        card.add(scroll, BorderLayout.CENTER);
        healthCard = buildHealthCard();
        healthCard.setVisible(false);
        card.add(healthCard, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    private JPanel buildHealthCard() {
        JPanel health = new JPanel(new BorderLayout(0, 10));
        health.setOpaque(false);
        health.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, UIConstants.SECURE_TEAL),
                new EmptyBorder(14, 0, 0, 0)));

        JLabel title = UiStyles.sectionLabel("System Health");
        title.setForeground(UIConstants.SECURE_TEAL);
        health.add(title, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(2, 1, 0, 8));
        metrics.setOpaque(false);
        metrics.add(metricRow("Signal Integrity", "100%"));
        metrics.add(metricRow("Entropy Level", "94.2 bits"));
        health.add(metrics, BorderLayout.CENTER);
        return health;
    }

    private JPanel metricRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel left = UiStyles.mutedLabel(label);
        left.setFont(UIConstants.FONT_MONO.deriveFont(10f));
        JLabel right = UiStyles.mutedLabel(value);
        right.setFont(UIConstants.FONT_MONO.deriveFont(10f));
        right.setForeground(UIConstants.SECURE_TEAL);
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    public void setImportantOnly(boolean importantOnly) {
        this.importantOnly = importantOnly;
    }

    public void setHealthVisible(boolean visible) {
        healthCard.setVisible(visible);
    }

    public void setHeader(String title, String subtitle) {
        runOnEdt(() -> {
            titleLabel.setText(title);
            subtitleLabel.setText(subtitle);
        });
    }

    public void clear() {
        runOnEdt(() -> {
            rows.removeAll();
            rowRefs.clear();
            refresh();
        });
    }

    public void seed(String[][] entries) {
        clear();
        for (String[] entry : entries) {
            Tone tone = entry.length > 2 ? Tone.valueOf(entry[2]) : Tone.INFO;
            addEvent(entry[0], entry[1], tone);
        }
    }

    public void addEvent(String title, String body, Tone tone) {
        Tone effectiveTone = tone == null ? Tone.INFO : tone;
        if (importantOnly && !isImportantKeyEvent(title, body, effectiveTone)) {
            return;
        }
        runOnEdt(() -> {
            JPanel row = new TimelineRow(title, body, effectiveTone);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            Component gap = Box.createVerticalStrut(8);
            rows.add(row, 0);
            rows.add(gap, Math.min(1, rows.getComponentCount()));
            rowRefs.addFirst(gap);
            rowRefs.addFirst(row);
            while (rowRefs.size() > MAX_ROWS * 2) {
                Component last = rowRefs.removeLast();
                rows.remove(last);
            }
            refresh();
        });
    }

    private static boolean isImportantKeyEvent(String title, String body, Tone tone) {
        if (tone == Tone.ERROR) {
            return true;
        }
        String text = (safe(title) + " " + safe(body)).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("message key")
                || text.contains("giải mã")
                || text.contains("đã route")
                || text.contains("chuẩn bị gửi")
                || text.contains("mở hội thoại")
                || text.contains("tin mới")) {
            return false;
        }
        return text.contains("tgt")
                || text.contains("st ")
                || text.contains("service ticket")
                || text.contains("kerberos")
                || text.contains("handshake")
                || text.contains("pre-key")
                || text.contains("e2ee init")
                || text.contains("ratchet")
                || text.contains("opk")
                || text.contains("ml-kem")
                || text.contains("ecdhe")
                || text.contains("csr")
                || text.contains("chứng chỉ")
                || text.contains("certificate")
                || text.contains("identity keypair")
                || text.contains("proof-of-possession")
                || text.contains("keystore")
                || text.contains("phiên sẵn sàng");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void refresh() {
        rows.revalidate();
        rows.repaint();
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static final class TimelineRow extends JPanel {
        private final Tone tone;

        TimelineRow(String title, String body, Tone tone) {
            this.tone = tone;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(0, 0, 0, 2));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));

            JPanel marker = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.OUTLINE);
                    g2.drawLine(9, 18, 9, getHeight());
                    g2.setColor(colorFor(tone));
                    g2.fillOval(4, 7, 11, 11);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            marker.setOpaque(false);
            marker.setPreferredSize(new Dimension(22, 34));
            add(marker, BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel heading = UiStyles.bodyLabel("<html><b>" + escape(title) + "</b></html>");
            heading.setForeground(UIConstants.TEXT_WHITE);
            JLabel detail = UiStyles.mutedLabel("<html><body style='width:245px;'>"
                    + escape(body) + "</body></html>");
            detail.setForeground(tone == Tone.ERROR ? UIConstants.SIGNAL_RED : UIConstants.TEXT_SILVER);
            JLabel time = UiStyles.mutedLabel(LocalTime.now().format(TIME_FMT));
            time.setForeground(UIConstants.TEXT_MUTED);

            text.add(heading);
            text.add(Box.createVerticalStrut(3));
            text.add(detail);
            text.add(Box.createVerticalStrut(3));
            text.add(time);
            add(text, BorderLayout.CENTER);
        }

        private static Color colorFor(Tone tone) {
            return switch (tone) {
                case SUCCESS -> UIConstants.SECURE_TEAL;
                case ERROR -> UIConstants.SIGNAL_RED;
                case ACTIVE -> UIConstants.TEXT_WHITE;
                case INFO -> UIConstants.OUTLINE;
            };
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
