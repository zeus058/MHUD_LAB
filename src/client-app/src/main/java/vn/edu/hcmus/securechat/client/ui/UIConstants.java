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
    /** Các surface mở rộng theo template HTML trong thư mục template/. */
    public static final Color SURFACE = Color.decode("#0C141C");
    public static final Color PANEL_BG = Color.decode("#1A242D");
    public static final Color CARD_BG = Color.decode("#242F38");
    public static final Color INPUT_BG = Color.decode("#212C36");
    public static final Color INPUT_BG_ALT = Color.decode("#2D3843");
    public static final Color LOG_BG = Color.decode("#24303A");
    public static final Color SURFACE_HIGH = Color.decode("#232B33");
    public static final Color OUTLINE = Color.decode("#3D4948");

    // Glassmorphism (RGBA Colors)
    public static final Color GLASS_CARD    = new Color(36, 47, 56, 90);    // ~35% alpha
    public static final Color GLASS_BORDER  = new Color(255, 255, 255, 20);  // ~8% alpha
    public static final Color GLASS_SIDEBAR = new Color(12, 20, 28, 115);   // ~45% alpha
    public static final Color ACCENT_DIM    = new Color(0, 161, 156, 38);    // ~15% alpha
    public static final Color ACCENT_GLOW   = new Color(0, 161, 156, 102);   // ~40% alpha

    public static final int CORNER_RADIUS = 12;
    public static final int CORNER_RADIUS_SM = 8;
    public static final int CORNER_RADIUS_LG = 20;
    public static final int PADDING = 16;
    public static final int PADDING_SM = 10;

    public static final Font FONT_TITLE = new Font("Manrope", Font.BOLD, 24);
    public static final Font FONT_PAGE_TITLE = new Font("Manrope", Font.BOLD, 30);
    public static final Font FONT_HEADING = new Font("Manrope", Font.BOLD, 16);
    public static final Font FONT_BODY = new Font("Inter", Font.PLAIN, 14);
    public static final Font FONT_SMALL = new Font("Inter", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("JetBrains Mono", Font.PLAIN, 12);

    private UIConstants() {
    }
}
