package vn.edu.hcmus.securechat.client.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Bảng màu SECURE TEAL &amp; SILVER — bắt buộc theo doc/UI_Constraints.md.
 */
public final class UIConstants {

    public static final Color SECURE_TEAL = Color.decode("#00A19C");
    public static final Color DEEP_CARBON = Color.decode("#101820");
    public static final Color DARK_SILVER = Color.decode("#3A4750");
    public static final Color SIGNAL_RED = Color.decode("#FF4C4C");

    public static final Color TEXT_WHITE = Color.decode("#FFFFFF");
    public static final Color TEXT_SILVER = Color.decode("#E2E8F0");
    /** Chữ phụ / nhãn mờ — dễ phân tầng với TEXT_SILVER */
    public static final Color TEXT_MUTED = Color.decode("#94A3B8");
    /** Viền phân vùng nhẹ trên nền tối */
    public static final Color BORDER_SUBTLE = Color.decode("#2D3748");

    public static final int CORNER_RADIUS = 14;
    public static final int CORNER_RADIUS_SM = 10;
    public static final int PADDING = 16;
    public static final int PADDING_SM = 10;

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    private UIConstants() {
    }
}
