package vn.edu.hcmus.securechat.client.ui.components;

import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AuditLogTable — Bảng hiển thị nhật ký bảo mật (Audit Log).
 * Hỗ trợ filter theo loại sự kiện và thêm dòng log mới.
 *
 * Kết nối UIController:
 *   controller.setAuditLogTable(auditTable);
 *   auditTable.addEntry(new AuditEntry(...));
 */
@SuppressWarnings({"serial", "this-escape"})
public @SuppressWarnings({"serial", "this-escape"})
class AuditLogTable extends JPanel {

    public enum EventType {
        LOGIN("Đăng nhập", new Color(0x9FE1CB), new Color(0x085041)),
        MESSAGE("Tin nhắn",  new Color(0xCECBF6), new Color(0x3C3489)),
        SESSION_KEY("Session key", new Color(0xFAC775), new Color(0x633806)),
        WARNING("Cảnh báo",  new Color(0xF7C1C1), new Color(0x791F1F)),
        KEY_EXCHANGE("Trao đổi key", new Color(0xC0DD97), new Color(0x3B6D11));

        public final String label;
        public final Color  bgColor;
        public final Color  fgColor;
        EventType(String label, Color bg, Color fg) { this.label=label; this.bgColor=bg; this.fgColor=fg; }
    }

    public record AuditEntry(
        LocalDateTime time,
        EventType     type,
        String        actor,
        String        detail,
        boolean       success
    ) {}

    // === Data ===
    private final List<AuditEntry>   allEntries = new ArrayList<>();
    private final DefaultTableModel  model;
    private EventType                activeFilter = null;

    // === UI ===
    private final JTable table;

    private static final String[] COLUMNS = {"Thời gian", "Sự kiện", "Người dùng / Chi tiết", "Kết quả"};
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public AuditLogTable() {
        setLayout(new BorderLayout());
        setBackground(AppTheme.SURFACE);

        model = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setFont(AppTheme.FONT_SMALL.deriveFont(12f));
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(AppTheme.SURFACE);
        table.setSelectionBackground(AppTheme.PRIMARY_BG);
        table.setSelectionForeground(AppTheme.PRIMARY_DARK);
        table.setFocusable(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(0).setMaxWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setMaxWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(999);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(100);

        // Custom renderers
        table.setDefaultRenderer(Object.class, new AuditCellRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new BadgeCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());

        // Header styling
        JTableHeader header = table.getTableHeader();
        header.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        header.setBackground(AppTheme.SURFACE_2);
        header.setForeground(AppTheme.TEXT_SECONDARY);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER));
        header.setReorderingAllowed(false);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.setBackground(AppTheme.SURFACE);
        scroll.getViewport().setBackground(AppTheme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(buildHeader(), BorderLayout.NORTH);
        add(scroll,        BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(AppTheme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
            new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("🛡  Audit Log");
        title.setFont(AppTheme.FONT_MEDIUM.deriveFont(14f));
        title.setForeground(AppTheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Nhật ký bảo mật · Chỉ đọc");
        sub.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        sub.setForeground(AppTheme.TEXT_SECONDARY);
        titleBox.add(title);
        titleBox.add(sub);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.setOpaque(false);
        filters.add(buildFilterBtn("Tất cả", null));
        for (EventType t : EventType.values()) {
            filters.add(buildFilterBtn(t.label, t));
        }

        p.add(titleBox, BorderLayout.WEST);
        p.add(filters,  BorderLayout.EAST);
        return p;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.SURFACE_2);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
            new EmptyBorder(8, 16, 8, 16)
        ));
        JLabel lbl = new JLabel("Hiển thị " + allEntries.size() + " sự kiện gần nhất");
        lbl.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        lbl.setForeground(AppTheme.TEXT_SECONDARY);
        p.add(lbl, BorderLayout.WEST);
        return p;
    }

    private JButton buildFilterBtn(String label, EventType type) {
        JButton btn = new JButton(label);
        btn.setFont(AppTheme.FONT_SMALL.deriveFont(12f));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 8, 28));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateFilterBtnStyle(btn, type == activeFilter || (type == null && activeFilter == null));
        btn.addActionListener(e -> {
            activeFilter = type;
            refreshTable();
            // Reset all, then activate this
            Container parent = btn.getParent();
            for (Component c : parent.getComponents()) {
                if (c instanceof JButton b) updateFilterBtnStyle(b, false);
            }
            updateFilterBtnStyle(btn, true);
        });
        return btn;
    }

    private void updateFilterBtnStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(AppTheme.PRIMARY_BG);
            btn.setForeground(AppTheme.PRIMARY_DARK);
            btn.setBorder(BorderFactory.createLineBorder(AppTheme.PRIMARY_LIGHT, 1, true));
        } else {
            btn.setBackground(AppTheme.SURFACE);
            btn.setForeground(AppTheme.TEXT_SECONDARY);
            btn.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER, 1, true));
        }
    }

    // ─── Public API ───

    public void addEntry(AuditEntry entry) {
        SwingUtilities.invokeLater(() -> {
            allEntries.add(0, entry); // Newest first
            refreshTable();
        });
    }

    public void addEntries(List<AuditEntry> entries) {
        SwingUtilities.invokeLater(() -> {
            allEntries.addAll(0, entries);
            refreshTable();
        });
    }

    private void refreshTable() {
        model.setRowCount(0);
        List<AuditEntry> filtered = activeFilter == null
            ? allEntries
            : allEntries.stream().filter(e -> e.type() == activeFilter).toList();

        for (AuditEntry e : filtered) {
            model.addRow(new Object[]{
                e.time().format(FMT),
                e.type(),
                e.actor() + (e.detail().isEmpty() ? "" : " — " + e.detail()),
                e.success()
            });
        }

        // Update footer count
        Component footer = ((BorderLayout)getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (footer instanceof JPanel fp) {
            for (Component c : fp.getComponents()) {
                if (c instanceof JLabel lbl) {
                    lbl.setText("Hiển thị " + filtered.size() + " sự kiện");
                }
            }
        }
    }

    // ─── Cell Renderers ───

    static @SuppressWarnings({"serial", "this-escape"})
class AuditCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
            lbl.setFont(AppTheme.FONT_SMALL.deriveFont(12f));
            lbl.setForeground(sel ? AppTheme.PRIMARY_DARK : AppTheme.TEXT_SECONDARY);
            lbl.setBorder(new EmptyBorder(0, 12, 0, 12));
            lbl.setBackground(row % 2 == 0 ? AppTheme.SURFACE : AppTheme.SURFACE_2);
            if (sel) lbl.setBackground(AppTheme.PRIMARY_BG);
            return lbl;
        }
    }

    static @SuppressWarnings({"serial", "this-escape"})
class BadgeCellRenderer extends JPanel implements TableCellRenderer {
        private final JLabel badge = new JLabel();

        BadgeCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));
            setOpaque(true);
            badge.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
            badge.setBorder(new EmptyBorder(2, 8, 2, 8));
            badge.setOpaque(true);
            add(badge);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
            setBackground(row % 2 == 0 ? AppTheme.SURFACE : AppTheme.SURFACE_2);
            if (sel) setBackground(AppTheme.PRIMARY_BG);

            if (val instanceof EventType et) {
                badge.setText(et.label);
                badge.setBackground(et.bgColor);
                badge.setForeground(et.fgColor);
                // Round corners via border
                badge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(et.bgColor.darker(), 0, true),
                    new EmptyBorder(2, 8, 2, 8)
                ));
            }
            return this;
        }
    }

    static @SuppressWarnings({"serial", "this-escape"})
class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
            lbl.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
            lbl.setBorder(new EmptyBorder(0, 6, 0, 12));
            lbl.setBackground(row % 2 == 0 ? AppTheme.SURFACE : AppTheme.SURFACE_2);
            if (sel) lbl.setBackground(AppTheme.PRIMARY_BG);

            boolean success = val instanceof Boolean b && b;
            lbl.setText(success ? "✓ OK" : "⚠ Bị chặn");
            lbl.setForeground(success ? AppTheme.SUCCESS : AppTheme.DANGER);
            lbl.setFont(AppTheme.FONT_SMALL.deriveFont(Font.BOLD, 11f));
            return lbl;
        }
    }
}