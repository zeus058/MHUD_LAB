package vn.edu.hcmus.securechat.client.ui.components;

import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * PrimaryButton — Nút bấm chính với màu PRIMARY, hiệu ứng hover và loading state.
 */
public class PrimaryButton extends JButton {

    private boolean loading = false;
    private boolean hovered = false;
    private boolean pressed = false;
    private Color bgColor;
    private Color fgColor;
    private int radius;

    public PrimaryButton(String text) {
        this(text, AppTheme.PRIMARY, AppTheme.TEXT_ON_PRIMARY);
    }

    public PrimaryButton(String text, Color bg, Color fg) {
        super(text);
        this.bgColor = bg;
        this.fgColor = fg;
        this.radius  = AppTheme.RADIUS_MD;

        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(fg);
        setFont(AppTheme.FONT_MEDIUM.deriveFont(14f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(getPreferredSize().width, 40));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { pressed = true;  repaint(); }
            @Override public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
        });
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        setEnabled(!loading);
        setText(loading ? "Đang xử lý..." : getText());
        repaint();
    }

    public void setRadius(int r) { this.radius = r; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        Color bg = bgColor;
        if (!isEnabled() || loading) bg = AppTheme.PRIMARY_LIGHT;
        else if (pressed)            bg = AppTheme.PRIMARY_DARK;
        else if (hovered)            bg = AppTheme.PRIMARY_HOVER;

        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, radius * 2, radius * 2));

        // Scale effect when pressed
        if (pressed) {
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(0.98, 0.98);
            g2.translate(-w / 2.0, -h / 2.0);
        }

        // Text
        g2.setFont(getFont());
        g2.setColor(fgColor);
        FontMetrics fm = g2.getFontMetrics();
        String txt = getText();
        int tx = (w - fm.stringWidth(txt)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(txt, tx, ty);

        g2.dispose();
    }
}