package vn.edu.hcmus.securechat.client.ui.theme;

// Removed FlatLaf imports

import javax.swing.*;
import java.awt.*;

/**
 * AppTheme — Bảng màu và font dùng chung cho toàn bộ SecureChat UI.
 * Dựa trên FlatLaf với bảng màu tím (Purple) là chủ đạo.
 */
public class AppTheme {

    // === PRIMARY PALETTE ===
    public static final Color PRIMARY         = new Color(0x534AB7);
    public static final Color PRIMARY_LIGHT   = new Color(0xAFA9EC);
    public static final Color PRIMARY_DARK    = new Color(0x3C3489);
    public static final Color PRIMARY_HOVER   = new Color(0x6259C4);
    public static final Color PRIMARY_BG      = new Color(0xEEEDFE);

    // === SURFACE ===
    public static final Color SURFACE         = new Color(0xFFFFFF);
    public static final Color SURFACE_2       = new Color(0xF5F5F8);
    public static final Color SURFACE_3       = new Color(0xEEEEF4);

    // === TEXT ===
    public static final Color TEXT_PRIMARY    = new Color(0x1A1A2E);
    public static final Color TEXT_SECONDARY  = new Color(0x6B6B80);
    public static final Color TEXT_HINT       = new Color(0xA0A0B0);
    public static final Color TEXT_ON_PRIMARY = new Color(0xFFFFFF);

    // === BORDER ===
    public static final Color BORDER          = new Color(0xE0E0EC);
    public static final Color BORDER_FOCUS    = new Color(0x534AB7);

    // === STATUS ===
    public static final Color SUCCESS         = new Color(0x1D9E75);
    public static final Color SUCCESS_BG      = new Color(0xE1F5EE);
    public static final Color WARNING         = new Color(0xBA7517);
    public static final Color WARNING_BG      = new Color(0xFAEEDA);
    public static final Color DANGER          = new Color(0xA32D2D);
    public static final Color DANGER_BG       = new Color(0xFCEBEB);
    public static final Color INFO            = new Color(0x185FA5);
    public static final Color INFO_BG         = new Color(0xE6F1FB);

    // === CHAT BUBBLES ===
    public static final Color BUBBLE_ME       = new Color(0x534AB7);
    public static final Color BUBBLE_ME_TEXT  = new Color(0xFFFFFF);
    public static final Color BUBBLE_OTHER    = new Color(0xF0F0F8);
    public static final Color BUBBLE_OTHER_TEXT = new Color(0x1A1A2E);

    // === AVATAR COLORS (theo từng user) ===
    public static final Color[][] AVATAR_PALETTES = {
        {new Color(0xCECBF6), new Color(0x3C3489)}, // purple
        {new Color(0x9FE1CB), new Color(0x085041)}, // teal
        {new Color(0xF5C4B3), new Color(0x993C1D)}, // coral
        {new Color(0xB5D4F4), new Color(0x0C447C)}, // blue
        {new Color(0xC0DD97), new Color(0x3B6D11)}, // green
        {new Color(0xF4C0D1), new Color(0x993556)}, // pink
    };

    // === CORNER RADIUS ===
    public static final int RADIUS_SM  = 6;
    public static final int RADIUS_MD  = 8;
    public static final int RADIUS_LG  = 12;
    public static final int RADIUS_XL  = 16;
    public static final int RADIUS_PILL = 20;

    // === FONT ===
    public static Font FONT_REGULAR;
    public static Font FONT_MEDIUM;
    public static Font FONT_SMALL;
    public static Font FONT_TITLE;
    public static Font FONT_MONO;

    // === SPACING ===
    public static final int PADDING_SM  = 8;
    public static final int PADDING_MD  = 14;
    public static final int PADDING_LG  = 20;
    public static final int PADDING_XL  = 28;

    static {
        FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 14);
        FONT_MEDIUM  = new Font("Segoe UI", Font.BOLD,  14);
        FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 12);
        FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  18);
        FONT_MONO    = new Font("JetBrains Mono", Font.PLAIN, 13);
        if (FONT_MONO.getFamily().equals("Dialog")) {
            FONT_MONO = new Font("Consolas", Font.PLAIN, 13);
        }
    }

    /**
     * Áp dụng FlatLaf Light theme và tuỳ chỉnh toàn cục.
     */
    public static void apply() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        // Override FlatLaf defaults
        UIManager.put("Button.arc",                   RADIUS_MD * 2);
        UIManager.put("Component.arc",                RADIUS_MD * 2);
        UIManager.put("TextComponent.arc",            RADIUS_MD * 2);
        UIManager.put("PasswordField.arc",            RADIUS_MD * 2);
        UIManager.put("ProgressBar.arc",              RADIUS_SM * 2);

        UIManager.put("Button.background",            PRIMARY);
        UIManager.put("Button.foreground",            TEXT_ON_PRIMARY);
        UIManager.put("Button.hoverBackground",       PRIMARY_HOVER);
        UIManager.put("Button.pressedBackground",     PRIMARY_DARK);
        UIManager.put("Button.focusedBorderColor",    BORDER_FOCUS);
        UIManager.put("Button.borderWidth",           0);

        UIManager.put("TextField.background",         SURFACE_2);
        UIManager.put("PasswordField.background",     SURFACE_2);
        UIManager.put("TextField.focusedBackground",  SURFACE);
        UIManager.put("TextField.focusedBorderColor", BORDER_FOCUS);

        UIManager.put("Panel.background",             SURFACE);
        UIManager.put("Label.foreground",             TEXT_PRIMARY);

        UIManager.put("ScrollBar.width",              6);
        UIManager.put("ScrollBar.thumbArc",           RADIUS_PILL * 2);
        UIManager.put("ScrollBar.thumb",              new Color(0xCCCCDD));
        UIManager.put("ScrollBar.track",              SURFACE_2);
        UIManager.put("ScrollBar.hoverThumbColor",    PRIMARY_LIGHT);

        UIManager.put("Component.focusWidth",         1);
        UIManager.put("Component.innerFocusWidth",    0);
        UIManager.put("Component.borderWidth",        1);

        UIManager.put("defaultFont",                  FONT_REGULAR);

        UIManager.put("TabbedPane.selectedBackground", PRIMARY_BG);
        UIManager.put("TabbedPane.selectedForeground", PRIMARY);
        UIManager.put("TabbedPane.underlineColor",     PRIMARY);

        UIManager.put("Table.selectionBackground",    PRIMARY_BG);
        UIManager.put("Table.selectionForeground",    PRIMARY_DARK);
        UIManager.put("TableHeader.background",       SURFACE_2);
        UIManager.put("TableHeader.foreground",       TEXT_SECONDARY);
    }

    /**
     * Trả về màu avatar theo index (vòng tròn nếu quá 6).
     */
    public static Color[] avatarColors(int index) {
        return AVATAR_PALETTES[index % AVATAR_PALETTES.length];
    }

    /**
     * Trả về chữ viết tắt từ display name (vd: "Gia Hiển" → "GH").
     */
    public static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}