package vn.edu.hcmus.securechat.client.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * High-fidelity Showcase Panel matching the Figma mockup.
 * Supports both Checklist Mode (for login/register) and Timeline Mode (for logs and E2EE logs).
 */
public class ActivityFlowPanel extends JPanel {

    public enum Tone {
        INFO, ACTIVE, SUCCESS, ERROR
    }

    public enum StepState {
        PENDING, ACTIVE, SUCCESS, ERROR
    }

    public enum IconType {
        SERVER, SHIELD, KEY, SHARE
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_ROWS = 40;

    private final String mainTitle;
    private final String mainSubtitle;
    private final boolean isRegisterMode;
    private final boolean isChecklistMode;

    // Checklist fields
    private StepRow[] stepRows;

    // Timeline fields
    private JPanel timelineRows;
    private JScrollPane scrollPane;
    private final Deque<Component> rowRefs = new ArrayDeque<>();
    private boolean importantOnly;

    public ActivityFlowPanel(String title, String subtitle) {
        this.mainTitle = title;
        this.mainSubtitle = subtitle;
        this.isRegisterMode = title.toLowerCase().contains("đăng ký") || title.toLowerCase().contains("register");
        this.isChecklistMode = title.toLowerCase().contains("đăng nhập") || title.toLowerCase().contains("đăng ký") 
                || title.toLowerCase().contains("register") || title.toLowerCase().contains("login");

        setOpaque(false);
        setLayout(new BorderLayout());

        if (isChecklistMode) {
            setBorder(new EmptyBorder(0, 0, 0, 0));
            buildChecklistUI();
        } else {
            setBorder(new EmptyBorder(0, 0, 0, 0));
            buildTimelineUI();
        }
    }

    private void buildChecklistUI() {
        JPanel contentContainer = new JPanel();
        contentContainer.setOpaque(false);
        contentContainer.setLayout(new GridBagLayout());

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setPreferredSize(new Dimension(360, 500));

        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);

        String headerTitleText = "Connecting you <span style='color:#00A19C;'>safely</span>";
        JLabel titleLabel = new JLabel("<html><body style='width: 260px; font-weight:700;'>" + headerTitleText + "</body></html>");
        titleLabel.setFont(UIConstants.FONT_PAGE_TITLE.deriveFont(28f));
        titleLabel.setForeground(UIConstants.TEXT_WHITE);
        headerPanel.add(titleLabel);

        headerPanel.add(Box.createVerticalStrut(12));

        String headerSubtitleText = "We set up an encrypted connection and distribute your keys before you sign in — so every message stays private.";
        JLabel subtitleLabel = new JLabel("<html><body style='width: 260px; line-height: 1.5;'>" + headerSubtitleText + "</body></html>");
        subtitleLabel.setFont(UIConstants.FONT_BODY.deriveFont(14f));
        subtitleLabel.setForeground(UIConstants.TEXT_MUTED);
        headerPanel.add(subtitleLabel);

        stack.add(headerPanel);
        stack.add(Box.createVerticalStrut(40));

        JPanel stepsPanel = new JPanel();
        stepsPanel.setOpaque(false);
        stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
        stepsPanel.setAlignmentX(LEFT_ALIGNMENT);

        stepRows = new StepRow[4];
        stepRows[0] = new StepRow(IconType.SERVER, "Servers connected", "3 secure relays · verified", 0);
        stepRows[1] = new StepRow(IconType.SHIELD, "Identity verified", "Certificate trusted", 1);
        stepRows[2] = new StepRow(IconType.KEY, "Keys distributed", "Encryption keys shared across your devices", 2);
        stepRows[3] = new StepRow(IconType.SHARE, "Secure channel ready", "End-to-end encryption active", 3);

        for (int i = 0; i < 4; i++) {
            stepRows[i].setState(StepState.PENDING);
            stepsPanel.add(stepRows[i]);
        }
        stack.add(stepsPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(48, 32, 48, 32);
        contentContainer.add(stack, gbc);

        add(contentContainer, BorderLayout.CENTER);
    }

    private void buildTimelineUI() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIConstants.SURFACE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UIConstants.GLASS_BORDER);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        
        JLabel titleLabel = UiStyles.headingLabel(mainTitle);
        titleLabel.setFont(UIConstants.FONT_HEADING.deriveFont(15f));
        header.add(titleLabel);
        
        if (mainSubtitle != null && !mainSubtitle.isBlank()) {
            JLabel subtitleLabel = UiStyles.mutedLabel(mainSubtitle);
            subtitleLabel.setFont(UIConstants.FONT_SMALL.deriveFont(12f));
            header.add(Box.createVerticalStrut(2));
            header.add(subtitleLabel);
        }
        card.add(header, BorderLayout.NORTH);

        timelineRows = new JPanel();
        timelineRows.setOpaque(false);
        timelineRows.setLayout(new BoxLayout(timelineRows, BoxLayout.Y_AXIS));
        
        scrollPane = UiStyles.styledScrollPane(timelineRows);
        scrollPane.getViewport().setBackground(UIConstants.SURFACE);
        scrollPane.setBackground(UIConstants.SURFACE);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        card.add(scrollPane, BorderLayout.CENTER);

        add(card, BorderLayout.CENTER);
    }

    public void setImportantOnly(boolean importantOnly) {
        this.importantOnly = importantOnly;
    }

    public void setHealthVisible(boolean visible) {
        // Kept for compatibility
    }

    public void setHeader(String title, String subtitle) {
        // Kept for compatibility
    }

    public void clear() {
        if (isChecklistMode) {
            for (StepRow row : stepRows) {
                row.setState(StepState.PENDING);
            }
        } else {
            runOnEdt(() -> {
                timelineRows.removeAll();
                rowRefs.clear();
                refreshTimeline();
            });
        }
    }

    public void seed(String[][] entries) {
        clear();
        if (!isChecklistMode && entries != null) {
            for (String[] entry : entries) {
                Tone tone = entry.length > 2 ? Tone.valueOf(entry[2]) : Tone.INFO;
                addEvent(entry[0], entry[1], tone);
            }
        }
    }

    public void addEvent(String title, String body, Tone tone) {
        Tone effectiveTone = tone == null ? Tone.INFO : tone;
        
        if (isChecklistMode) {
            String key = (title + " " + body).toLowerCase();
            if (isRegisterMode) {
                if (key.contains("sinh identity") || key.contains("keypair")) {
                    setStepState(0, StepState.ACTIVE);
                } else if (key.contains("ký csr") || key.contains("csr")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.ACTIVE);
                } else if (key.contains("gửi tới ca") || key.contains("ca")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.SUCCESS);
                    setStepState(2, StepState.ACTIVE);
                } else if (key.contains("lưu keystore") || key.contains("keystore")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.SUCCESS);
                    setStepState(2, StepState.SUCCESS);
                    setStepState(3, StepState.ACTIVE);
                } else if (key.contains("hoàn tất") || key.contains("thành công")) {
                    for (int i = 0; i < 4; i++) {
                        setStepState(i, StepState.SUCCESS);
                    }
                }
            } else {
                if (key.contains("đồng bộ thời gian") || key.contains("ntp")) {
                    setStepState(0, StepState.ACTIVE);
                } else if (key.contains("thời gian hợp lệ")) {
                    setStepState(0, StepState.SUCCESS);
                } else if (key.contains("xin tgt")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.ACTIVE);
                } else if (key.contains("tgt đã cấp")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.SUCCESS);
                } else if (key.contains("xin st")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.SUCCESS);
                    setStepState(2, StepState.ACTIVE);
                } else if (key.contains("st đã cấp")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.SUCCESS);
                    setStepState(2, StepState.SUCCESS);
                } else if (key.contains("mở csdl") || key.contains("handshake")) {
                    setStepState(0, StepState.SUCCESS);
                    setStepState(1, StepState.SUCCESS);
                    setStepState(2, StepState.SUCCESS);
                    setStepState(3, StepState.ACTIVE);
                } else if (key.contains("phiên sẵn sàng") || key.contains("hoàn tất")) {
                    for (int i = 0; i < 4; i++) {
                        setStepState(i, StepState.SUCCESS);
                    }
                }
            }

            if (effectiveTone == Tone.ERROR || key.contains("thất bại") || key.contains("từ chối")) {
                for (int i = 0; i < 4; i++) {
                    if (stepRows[i].getState() == StepState.ACTIVE) {
                        stepRows[i].setState(StepState.ERROR);
                        break;
                    }
                }
            }
            repaint();
        } else {
            // Timeline mode
            if (importantOnly && !isImportantKeyEvent(title, body, effectiveTone)) {
                return;
            }
            runOnEdt(() -> {
                JPanel row = new TimelineRow(title, body, effectiveTone);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                Component gap = Box.createVerticalStrut(8);
                timelineRows.add(row, 0);
                timelineRows.add(gap, Math.min(1, timelineRows.getComponentCount()));
                rowRefs.addFirst(gap);
                rowRefs.addFirst(row);
                while (rowRefs.size() > MAX_ROWS * 2) {
                    Component last = rowRefs.removeLast();
                    timelineRows.remove(last);
                }
                refreshTimeline();
            });
        }
    }

    private void setStepState(int index, StepState state) {
        if (stepRows != null && index >= 0 && index < stepRows.length) {
            stepRows[index].setState(state);
        }
    }

    private void refreshTimeline() {
        if (timelineRows != null) {
            timelineRows.revalidate();
            timelineRows.repaint();
            if (scrollPane != null) {
                SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
            }
        }
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static boolean isImportantKeyEvent(String title, String body, Tone tone) {
        if (tone == Tone.ERROR) {
            return true;
        }
        String text = (safe(title) + " " + safe(body)).toLowerCase();
        if (text.contains("message key") || text.contains("giải mã") || text.contains("đã route")
                || text.contains("chuẩn bị gửi") || text.contains("mở hội thoại") || text.contains("tin mới")) {
            return false;
        }
        return text.contains("tgt") || text.contains("st ") || text.contains("service ticket")
                || text.contains("kerberos") || text.contains("handshake") || text.contains("pre-key")
                || text.contains("e2ee init") || text.contains("ratchet") || text.contains("opk")
                || text.contains("ml-kem") || text.contains("ecdhe") || text.contains("csr")
                || text.contains("chứng chỉ") || text.contains("certificate") || text.contains("identity keypair")
                || text.contains("proof-of-possession") || text.contains("keystore") || text.contains("phiên sẵn sàng");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ConnectorLine extends JPanel {
        ConnectorLine() {
            setOpaque(false);
            setPreferredSize(new Dimension(46, 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UIConstants.GLASS_BORDER);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{3f, 3f}, 0.0f));
            g2.drawLine(23, 0, 23, getHeight());
            g2.dispose();
        }
    }

    private class StepRow extends JPanel {
        private final IconType iconType;
        private final String title;
        private final String detail;
        private final int idx;
        private StepState state = StepState.PENDING;

        private final JLabel titleLabel;
        private final JLabel detailLabel;
        private final JLabel checkmarkLabel;

        StepRow(IconType iconType, String title, String detail, int idx) {
            this.iconType = iconType;
            this.title = title;
            this.detail = detail;
            this.idx = idx;

            setOpaque(false);
            setLayout(new BorderLayout(16, 0));
            setAlignmentX(LEFT_ALIGNMENT);

            JPanel iconBox = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = getWidth();
                    int h = getHeight();
                    int cx = w / 2;
                    int cy = h / 2;
                    int circleSize = 34;
                    int circleX = cx - circleSize / 2;
                    int circleY = cy - circleSize / 2;

                    // Draw continuous timeline line segments
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{3f, 3f}, 0.0f));

                    // Line above (from 0 to circle top)
                    if (idx > 0) {
                        boolean activeAbove = (state == StepState.SUCCESS || state == StepState.ACTIVE || (stepRows != null && idx - 1 < stepRows.length && stepRows[idx - 1] != null && stepRows[idx - 1].getState() == StepState.SUCCESS));
                        g2.setColor(activeAbove ? UIConstants.SECURE_TEAL : UIConstants.GLASS_BORDER);
                        g2.drawLine(cx, 0, cx, circleY);
                    }

                    // Line below (from circle bottom to h)
                    if (stepRows != null && idx < stepRows.length - 1) {
                        boolean activeBelow = (state == StepState.SUCCESS);
                        g2.setColor(activeBelow ? UIConstants.SECURE_TEAL : UIConstants.GLASS_BORDER);
                        g2.drawLine(cx, circleY + circleSize, cx, h);
                    }

                    Color bg, border;
                    float strokeW = 1.0f;
                    if (state == StepState.SUCCESS) {
                        bg = new Color(0, 161, 156, 25);
                        border = UIConstants.SECURE_TEAL;
                    } else if (state == StepState.ACTIVE) {
                        bg = new Color(0, 161, 156, 45);
                        border = UIConstants.SECURE_TEAL;
                        strokeW = 2.0f;
                    } else if (state == StepState.ERROR) {
                        bg = new Color(255, 76, 76, 30);
                        border = UIConstants.SIGNAL_RED;
                        strokeW = 1.5f;
                    } else {
                        bg = new Color(255, 255, 255, 8);
                        border = UIConstants.GLASS_BORDER;
                    }

                    // Draw glowing circular glass background
                    if (state == StepState.ACTIVE) {
                        Point2D center = new Point2D.Float(cx, cy);
                        RadialGradientPaint glowGrad = new RadialGradientPaint(
                            center, circleSize / 2f,
                            new float[]{0f, 0.8f, 1f},
                            new Color[]{
                                new Color(0, 161, 156, 80),
                                new Color(0, 161, 156, 20),
                                new Color(0, 161, 156, 0)
                            }
                        );
                        g2.setPaint(glowGrad);
                        g2.fillOval(circleX, circleY, circleSize, circleSize);
                    } else if (state == StepState.SUCCESS) {
                        Point2D center = new Point2D.Float(cx, cy);
                        RadialGradientPaint glowGrad = new RadialGradientPaint(
                            center, circleSize / 2f,
                            new float[]{0f, 1f},
                            new Color[]{
                                new Color(0, 161, 156, 35),
                                new Color(0, 161, 156, 10)
                            }
                        );
                        g2.setPaint(glowGrad);
                        g2.fillOval(circleX, circleY, circleSize, circleSize);
                    } else {
                        g2.setColor(bg);
                        g2.fillOval(circleX, circleY, circleSize, circleSize);
                    }

                    // Draw circular border
                    g2.setColor(border);
                    g2.setStroke(new BasicStroke(strokeW));
                    g2.drawOval(circleX, circleY, circleSize - 1, circleSize - 1);

                    // Draw inner icon
                    g2.setColor(state == StepState.PENDING ? UIConstants.TEXT_MUTED : (state == StepState.ERROR ? UIConstants.SIGNAL_RED : UIConstants.SECURE_TEAL));
                    g2.setStroke(new BasicStroke(1.8f));

                    int iw = 16;
                    int ih = 16;
                    int ix = cx - iw / 2;
                    int iy = cy - ih / 2;

                    switch (iconType) {
                        case SERVER -> {
                            g2.drawRoundRect(ix, iy, iw, ih / 3 - 1, 2, 2);
                            g2.drawRoundRect(ix, iy + ih / 3 + 1, iw, ih / 3 - 1, 2, 2);
                            g2.drawRoundRect(ix, iy + 2 * (ih / 3) + 2, iw, ih / 3 - 1, 2, 2);
                            g2.fillOval(ix + 3, iy + 2, 2, 2);
                            g2.fillOval(ix + 3, iy + ih / 3 + 3, 2, 2);
                            g2.fillOval(ix + 3, iy + 2 * (ih / 3) + 4, 2, 2);
                        }
                        case SHIELD -> {
                            Path2D.Double shield = new Path2D.Double();
                            shield.moveTo(ix + iw / 2.0, iy);
                            shield.lineTo(ix + iw - 1, iy + 2);
                            shield.lineTo(ix + iw - 1, iy + ih / 2.0);
                            shield.quadTo(ix + iw - 1, iy + ih * 0.75, ix + iw / 2.0, iy + ih - 1);
                            shield.quadTo(ix + 1, iy + ih * 0.75, ix + 1, iy + ih / 2.0);
                            shield.lineTo(ix + 1, iy + 2);
                            shield.closePath();
                            g2.draw(shield);
                            if (state == StepState.SUCCESS) {
                                g2.drawLine(ix + iw / 2 - 2, iy + ih / 2, ix + iw / 2, iy + ih / 2 + 2);
                                g2.drawLine(ix + iw / 2, iy + ih / 2 + 2, ix + iw / 2 + 3, iy + ih / 2 - 2);
                            }
                        }
                        case KEY -> {
                            int r = iw / 2;
                            g2.drawOval(ix, iy + (ih - r) / 2, r, r);
                            int shankY = iy + ih / 2;
                            g2.drawLine(ix + r - 1, shankY, ix + iw, shankY);
                            g2.drawLine(ix + iw - 2, shankY, ix + iw - 2, shankY + 3);
                            g2.drawLine(ix + iw - 5, shankY, ix + iw - 5, shankY + 3);
                        }
                        case SHARE -> {
                            int nodeSize = 4;
                            g2.fillOval(ix + iw / 2 - nodeSize / 2, iy, nodeSize, nodeSize);
                            g2.fillOval(ix, iy + ih - nodeSize, nodeSize, nodeSize);
                            g2.fillOval(ix + iw - nodeSize, iy + ih - nodeSize, nodeSize, nodeSize);
                            g2.drawLine(ix + iw / 2, iy + nodeSize / 2, ix + nodeSize / 2, iy + ih - nodeSize / 2);
                            g2.drawLine(ix + iw / 2, iy + nodeSize / 2, ix + iw - nodeSize / 2, iy + ih - nodeSize / 2);
                        }
                    }

                    g2.dispose();
                }
            };
            iconBox.setOpaque(false);
            iconBox.setPreferredSize(new Dimension(46, 46));
            add(iconBox, BorderLayout.WEST);

            JPanel textContainer = new JPanel();
            textContainer.setOpaque(false);
            textContainer.setLayout(new BoxLayout(textContainer, BoxLayout.Y_AXIS));
            textContainer.setBorder(new javax.swing.border.EmptyBorder(10, 0, 10, 0));

            JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            titleRow.setOpaque(false);
            titleRow.setAlignmentX(LEFT_ALIGNMENT);

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(UIConstants.FONT_HEADING.deriveFont(17f));
            titleLbl.setForeground(state == StepState.PENDING ? UIConstants.TEXT_MUTED : UIConstants.TEXT_WHITE);
            titleRow.add(titleLbl);

            JLabel checkmark = new JLabel();
            checkmark.setFont(UIConstants.FONT_HEADING.deriveFont(14f));
            titleRow.add(checkmark);

            textContainer.add(titleRow);

            JLabel detailLbl = new JLabel("<html><body style='width: 240px;'>" + detail + "</body></html>");
            detailLbl.setFont(UIConstants.FONT_SMALL.deriveFont(13f));
            detailLbl.setForeground(state == StepState.PENDING ? UIConstants.TEXT_MUTED : (state == StepState.ERROR ? UIConstants.SIGNAL_RED : UIConstants.TEXT_SILVER));
            detailLbl.setAlignmentX(LEFT_ALIGNMENT);

            this.titleLabel = titleLbl;
            this.detailLabel = detailLbl;
            this.checkmarkLabel = checkmark;

            add(textContainer, BorderLayout.CENTER);
            setState(StepState.PENDING);
        }

        public StepState getState() {
            return state;
        }

        public void setState(StepState state) {
            this.state = state;
            titleLabel.setForeground(state == StepState.PENDING ? UIConstants.TEXT_MUTED : UIConstants.TEXT_WHITE);
            detailLabel.setForeground(state == StepState.PENDING ? UIConstants.TEXT_MUTED : (state == StepState.ERROR ? UIConstants.SIGNAL_RED : UIConstants.TEXT_SILVER));
            if (state == StepState.SUCCESS) {
                checkmarkLabel.setText("✓");
                checkmarkLabel.setForeground(UIConstants.SECURE_TEAL);
            } else if (state == StepState.ERROR) {
                checkmarkLabel.setText("✗");
                checkmarkLabel.setForeground(UIConstants.SIGNAL_RED);
            } else if (state == StepState.ACTIVE) {
                checkmarkLabel.setText("•");
                checkmarkLabel.setForeground(UIConstants.SECURE_TEAL);
            } else {
                checkmarkLabel.setText("");
            }
        }
    }

    private static final class TimelineRow extends JPanel {
        private final Tone tone;

        TimelineRow(String title, String body, Tone tone) {
            this.tone = tone;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(0, 0, 0, 2));

            JPanel marker = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(UIConstants.GLASS_BORDER);
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
            JLabel detail = UiStyles.mutedLabel("<html><body style='width:220px;'>"
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

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }

        private static Color colorFor(Tone tone) {
            return switch (tone) {
                case SUCCESS -> UIConstants.SECURE_TEAL;
                case ERROR -> UIConstants.SIGNAL_RED;
                case ACTIVE -> UIConstants.TEXT_WHITE;
                case INFO -> UIConstants.BORDER_SUBTLE;
            };
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
