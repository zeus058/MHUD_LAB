package vn.edu.hcmus.securechat.client.ui.components;

import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * AvatarLabel — Hiển thị avatar hình tròn với chữ viết tắt và màu sắc theo palette.
 * Dùng ở sidebar contact list, chat header, sidebar footer.
 */
@SuppressWarnings({"serial", "this-escape"})
public @SuppressWarnings({"serial", "this-escape"})
class AvatarLabel extends JComponent {

    private String initials;
    private Color bgColor;
    private Color textColor;
    private int size;

    public AvatarLabel(String displayName, int colorIndex, int size) {
        this.size     = size;
        this.initials = AppTheme.initials(displayName);
        Color[] palette = AppTheme.avatarColors(colorIndex);
        this.bgColor   = palette[0];
        this.textColor = palette[1];
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setOpaque(false);
    }

    public void update(String displayName, int colorIndex) {
        this.initials = AppTheme.initials(displayName);
        Color[] palette = AppTheme.avatarColors(colorIndex);
        this.bgColor  = palette[0];
        this.textColor = palette[1];
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int d = Math.min(w, h);
        int x = (w - d) / 2, y = (h - d) / 2;

        g2.setColor(bgColor);
        g2.fill(new Ellipse2D.Float(x, y, d, d));

        float fontSize = d * 0.35f;
        g2.setFont(AppTheme.FONT_MEDIUM.deriveFont(fontSize));
        g2.setColor(textColor);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (d - fm.stringWidth(initials)) / 2;
        int ty = y + (d - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(initials, tx, ty);
        g2.dispose();
    }
}