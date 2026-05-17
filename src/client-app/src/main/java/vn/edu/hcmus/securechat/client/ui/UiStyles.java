package vn.edu.hcmus.securechat.client.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * Tiện ích tạo component Swing theo theme Secure Teal &amp; Silver.
 */
public final class UiStyles {

    private UiStyles() {
    }

    public static void applyGlobalTheme() {
        UIManager.put("Panel.background", UIConstants.DEEP_CARBON);
        UIManager.put("Label.foreground", UIConstants.TEXT_SILVER);
        UIManager.put("OptionPane.background", UIConstants.DEEP_CARBON);
        UIManager.put("OptionPane.messageForeground", UIConstants.TEXT_SILVER);
    }

    public static JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIConstants.FONT_TITLE);
        label.setForeground(UIConstants.TEXT_WHITE);
        return label;
    }

    public static JLabel headingLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIConstants.FONT_HEADING);
        label.setForeground(UIConstants.TEXT_WHITE);
        return label;
    }

    public static JLabel bodyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIConstants.FONT_BODY);
        label.setForeground(UIConstants.TEXT_SILVER);
        return label;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIConstants.FONT_SMALL);
        label.setForeground(UIConstants.TEXT_SILVER);
        return label;
    }

    public static JTextField styledTextField(int columns) {
        JTextField field = new JTextField(columns);
        styleInput(field);
        return field;
    }

    public static JPasswordField styledPasswordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        styleInput(field);
        return field;
    }

    public static JTextArea styledTextArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setFont(UIConstants.FONT_BODY);
        area.setForeground(UIConstants.TEXT_SILVER);
        area.setBackground(UIConstants.DARK_SILVER);
        area.setCaretColor(UIConstants.SECURE_TEAL);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
        return area;
    }

    private static void styleInput(JTextField field) {
        field.setFont(UIConstants.FONT_BODY);
        field.setForeground(UIConstants.TEXT_WHITE);
        field.setBackground(UIConstants.DARK_SILVER);
        field.setCaretColor(UIConstants.SECURE_TEAL);
        field.setBorder(BorderFactory.createCompoundBorder(
                focusBorder(false),
                new EmptyBorder(10, 12, 10, 12)));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        focusBorder(true),
                        new EmptyBorder(10, 12, 10, 12)));
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        focusBorder(false),
                        new EmptyBorder(10, 12, 10, 12)));
            }
        });
    }

    public static JButton primaryButton(String text) {
        return styledButton(text, UIConstants.SECURE_TEAL, UIConstants.TEXT_WHITE, true);
    }

    public static JButton dangerButton(String text) {
        return styledButton(text, UIConstants.SIGNAL_RED, UIConstants.TEXT_WHITE, true);
    }

    public static JButton ghostButton(String text) {
        return styledButton(text, UIConstants.DARK_SILVER, UIConstants.TEXT_SILVER, false);
    }

    public static JButton linkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(UIConstants.FONT_BODY);
        button.setForeground(UIConstants.SECURE_TEAL);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton styledButton(String text, Color bg, Color fg, boolean filled) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (filled) {
                    g2.setColor(getModel().isPressed() ? bg.darker() : (getModel().isRollover() ? bg.brighter() : bg));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIConstants.CORNER_RADIUS_SM, UIConstants.CORNER_RADIUS_SM);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD));
        button.setForeground(fg);
        button.setBackground(filled ? bg : UIConstants.DARK_SILVER);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(!filled);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(0, 42));
        button.setBorder(new EmptyBorder(8, 20, 8, 20));
        return button;
    }

    public static RoundedPanel cardPanel() {
        RoundedPanel panel = new RoundedPanel(UIConstants.DARK_SILVER, UIConstants.CORNER_RADIUS);
        panel.setBorder(new EmptyBorder(UIConstants.PADDING, UIConstants.PADDING,
                UIConstants.PADDING, UIConstants.PADDING));
        return panel;
    }

    public static RoundedPanel surfacePanel(Color color) {
        return new RoundedPanel(color, UIConstants.CORNER_RADIUS_SM);
    }

    public static JScrollPane styledScrollPane(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIConstants.DEEP_CARBON);
        scroll.setBackground(UIConstants.DEEP_CARBON);
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        return scroll;
    }

    public static Border focusBorder(boolean focused) {
        return new RoundedLineBorder(
                focused ? UIConstants.SECURE_TEAL : UIConstants.DARK_SILVER.darker(),
                focused ? 2 : 1,
                UIConstants.CORNER_RADIUS_SM);
    }

    public static void pad(JComponent c, int top, int left, int bottom, int right) {
        Border existing = c.getBorder();
        Border padding = new EmptyBorder(top, left, bottom, right);
        c.setBorder(existing == null ? padding : BorderFactory.createCompoundBorder(existing, padding));
    }

    /** Panel bo góc với nền tùy chỉnh. */
    public static @SuppressWarnings({"serial", "this-escape"})
class RoundedPanel extends JPanel {

        private final int radius;

        public RoundedPanel(Color background, int radius) {
            this.radius = radius;
            setOpaque(false);
            setBackground(background);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final @SuppressWarnings({"serial", "this-escape"})
class RoundedLineBorder extends AbstractBorder {

        private final Color color;
        private final int thickness;
        private final int radius;

        RoundedLineBorder(Color color, int thickness, int radius) {
            this.color = color;
            this.thickness = thickness;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new java.awt.BasicStroke(thickness));
            int inset = thickness;
            g2.drawRoundRect(x + inset, y + inset, width - inset * 2 - 1, height - inset * 2 - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 2, thickness + 4, thickness + 2, thickness + 4);
        }
    }
}
