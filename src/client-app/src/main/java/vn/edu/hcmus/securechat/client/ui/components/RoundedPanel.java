package vn.edu.hcmus.securechat.client.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * RoundedPanel — JPanel với bo góc và border tuỳ chỉnh.
 * Dùng làm card nền, bubble chat, badge, v.v.
 */
public class RoundedPanel extends JPanel {

    private int radius;
    private Color borderColor;
    private int borderWidth;

    public RoundedPanel(int radius) {
        this(radius, null, 0);
    }

    public RoundedPanel(int radius, Color borderColor) {
        this(radius, borderColor, 1);
    }

    public RoundedPanel(int radius, Color borderColor, int borderWidth) {
        this.radius      = radius;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        setOpaque(false);
    }

    public void setRadius(int radius) { this.radius = radius; repaint(); }
    public void setBorderColor(Color c) { this.borderColor = c; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int bw = (borderWidth > 0 && borderColor != null) ? borderWidth : 0;

        // Fill
        g2.setColor(getBackground());
        g2.fill(new RoundRectangle2D.Float(bw, bw, w - bw * 2, h - bw * 2, radius, radius));

        // Border
        if (bw > 0 && borderColor != null) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(bw));
            g2.draw(new RoundRectangle2D.Float(bw / 2f, bw / 2f, w - bw, h - bw, radius, radius));
        }

        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        // Bỏ border mặc định của JPanel, dùng border trong paintComponent
    }
}