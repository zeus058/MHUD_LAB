package vn.edu.hcmus.securechat.client.ui;

import vn.edu.hcmus.securechat.client.ui.components.*;
import vn.edu.hcmus.securechat.client.ui.theme.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * LoginDialog — Màn hình đăng nhập SecureChat E2EE.
 *
 * Luồng: User nhập username + password → UIController.handleLogin()
 * UIController sẽ gọi KDC AS (port 8881) để xin TGT.
 *
 * Kết nối với UIController:
 * dialog.setLoginListener((username, password) -> controller.handleLogin(username, password));
 */
@SuppressWarnings({"serial", "this-escape"})
public @SuppressWarnings({"serial", "this-escape"})
class LoginDialog extends JDialog {

    // === UI Components ===
    private JTextField      tfUsername;
    private JPasswordField  pfPassword;
    private PrimaryButton   btnLogin;
    private JLabel          lblError;
    private JCheckBox       cbShowPassword;

    // === Callback ===
    private LoginListener loginListener;
    private Runnable      onRegisterClick;

    // === Trạng thái kết quả đăng nhập (Bổ sung để ClientApp gọi) ===
    private boolean succeeded = false;
    private String username = "";

    public interface LoginListener {
        void onLogin(String username, char[] password);
    }

    public LoginDialog(Frame owner) {
        super(owner, "SecureChat — Đăng nhập", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        // Root panel với background trắng
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(AppTheme.SURFACE_2);
        root.setBorder(new EmptyBorder(40, 40, 40, 40));
        setContentPane(root);

        // Card trung tâm
        RoundedPanel card = new RoundedPanel(AppTheme.RADIUS_LG, AppTheme.BORDER);
        card.setBackground(AppTheme.SURFACE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(AppTheme.PADDING_XL, AppTheme.PADDING_XL,
                                        AppTheme.PADDING_XL, AppTheme.PADDING_XL));
        card.setPreferredSize(new Dimension(380, 460));

        // ── Logo + App name ──
        card.add(buildLogoRow());
        card.add(Box.createVerticalStrut(6));
        card.add(buildE2EEBadge());
        card.add(Box.createVerticalStrut(20));

        // ── Username field ──
        card.add(buildFieldLabel("Tên đăng nhập"));
        card.add(Box.createVerticalStrut(5));
        tfUsername = new JTextField();
        tfUsername.setFont(AppTheme.FONT_REGULAR);
        tfUsername.putClientProperty("JTextField.placeholderText", "username@hcmus.edu.vn");
        tfUsername.setPreferredSize(new Dimension(0, 38));
        tfUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        card.add(tfUsername);
        card.add(Box.createVerticalStrut(12));

        // ── Password field ──
        card.add(buildFieldLabel("Mật khẩu"));
        card.add(Box.createVerticalStrut(5));
        pfPassword = new JPasswordField();
        pfPassword.setFont(AppTheme.FONT_REGULAR);
        pfPassword.putClientProperty("JTextField.placeholderText", "••••••••••");
        pfPassword.setPreferredSize(new Dimension(0, 38));
        pfPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        pfPassword.addActionListener(e -> doLogin());
        card.add(pfPassword);
        card.add(Box.createVerticalStrut(6));

        // ── Show password checkbox ──
        cbShowPassword = new JCheckBox("Hiện mật khẩu");
        cbShowPassword.setFont(AppTheme.FONT_SMALL);
        cbShowPassword.setForeground(AppTheme.TEXT_SECONDARY);
        cbShowPassword.setBackground(AppTheme.SURFACE);
        cbShowPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        cbShowPassword.addActionListener(e -> {
            pfPassword.setEchoChar(cbShowPassword.isSelected() ? (char) 0 : '•');
        });
        card.add(cbShowPassword);
        card.add(Box.createVerticalStrut(16));

        // ── Error label ──
        lblError = new JLabel(" ");
        lblError.setFont(AppTheme.FONT_SMALL);
        lblError.setForeground(AppTheme.DANGER);
        lblError.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lblError);
        card.add(Box.createVerticalStrut(6));

        // ── Login button ──
        btnLogin = new PrimaryButton("Đăng nhập");
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogin.addActionListener(e -> doLogin());
        card.add(btnLogin);
        card.add(Box.createVerticalStrut(16));

        // ── Divider ──
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(16));

        // ── Register link ──
        card.add(buildRegisterRow());
        card.add(Box.createVerticalStrut(12));

        // ── Info box ──
        card.add(buildInfoBox());

        root.add(card);
    }

    private JPanel buildLogoRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Icon tím
        JPanel iconBox = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppTheme.PRIMARY);
                g2.fill(new RoundRectangle2D.Float(0, 0, 40, 40, 10, 10));
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 20));
                FontMetrics fm = g2.getFontMetrics();
                String icon = "🔒";
                g2.setFont(AppTheme.FONT_MEDIUM.deriveFont(16f));
                g2.drawString("SC", 10, 26);
                g2.dispose();
            }
        };
        iconBox.setPreferredSize(new Dimension(40, 40));
        iconBox.setOpaque(false);

        JPanel textBox = new JPanel();
        textBox.setOpaque(false);
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));
        JLabel lblName = new JLabel("SecureChat E2EE");
        lblName.setFont(AppTheme.FONT_MEDIUM.deriveFont(16f));
        lblName.setForeground(AppTheme.TEXT_PRIMARY);
        JLabel lblSub = new JLabel("End-to-End Encrypted Messaging");
        lblSub.setFont(AppTheme.FONT_SMALL);
        lblSub.setForeground(AppTheme.TEXT_SECONDARY);
        textBox.add(lblName);
        textBox.add(lblSub);

        row.add(iconBox);
        row.add(Box.createHorizontalStrut(10));
        row.add(textBox);
        return row;
    }

    private JPanel buildE2EEBadge() {
        RoundedPanel badge = new RoundedPanel(AppTheme.RADIUS_PILL);
        badge.setBackground(AppTheme.SUCCESS_BG);
        badge.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel("🔐  Kerberos V5 · PKI · Kyber ML-KEM");
        lbl.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        lbl.setForeground(new Color(0x085041));
        badge.add(lbl);

        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(badge);
        return wrap;
    }

    private JLabel buildFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(AppTheme.FONT_SMALL);
        lbl.setForeground(AppTheme.TEXT_SECONDARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JSeparator buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppTheme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private JPanel buildRegisterRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl1 = new JLabel("Chưa có tài khoản?");
        lbl1.setFont(AppTheme.FONT_SMALL);
        lbl1.setForeground(AppTheme.TEXT_SECONDARY);

        JLabel lbl2 = new JLabel("Đăng ký ngay");
        lbl2.setFont(AppTheme.FONT_SMALL.deriveFont(Font.BOLD));
        lbl2.setForeground(AppTheme.PRIMARY);
        lbl2.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl2.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                dispose();
                if (onRegisterClick != null) onRegisterClick.run();
            }
            @Override public void mouseEntered(MouseEvent e) { lbl2.setForeground(AppTheme.PRIMARY_HOVER); }
            @Override public void mouseExited(MouseEvent e)  { lbl2.setForeground(AppTheme.PRIMARY); }
        });

        row.add(lbl1);
        row.add(lbl2);
        return row;
    }

    private RoundedPanel buildInfoBox() {
        RoundedPanel box = new RoundedPanel(AppTheme.RADIUS_MD, AppTheme.BORDER);
        box.setBackground(AppTheme.SURFACE_2);
        box.setLayout(new BorderLayout(8, 0));
        box.setBorder(new EmptyBorder(10, 12, 10, 12));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JLabel icon = new JLabel("ℹ");
        icon.setFont(AppTheme.FONT_REGULAR.deriveFont(14f));
        icon.setForeground(AppTheme.TEXT_SECONDARY);

        JLabel msg = new JLabel("<html>Yêu cầu <b>CA Server</b> (8443) và <b>KDC Server</b> (8881/8882) đang chạy trước khi đăng nhập.</html>");
        msg.setFont(AppTheme.FONT_SMALL.deriveFont(11f));
        msg.setForeground(AppTheme.TEXT_SECONDARY);

        box.add(icon, BorderLayout.WEST);
        box.add(msg,  BorderLayout.CENTER);
        return box;
    }

    // ── Actions ──

    private void doLogin() {
        String usernameInput = tfUsername.getText().trim();
        char[] password = pfPassword.getPassword();

        lblError.setText(" ");

        if (usernameInput.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập.");
            tfUsername.requestFocus();
            return;
        }
        if (password.length == 0) {
            showError("Vui lòng nhập mật khẩu.");
            pfPassword.requestFocus();
            return;
        }

        btnLogin.setLoading(true);

        // Chạy async để không block EDT
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                if (loginListener != null) loginListener.onLogin(usernameInput, password);
                return null;
            }
            @Override protected void done() {
                btnLogin.setLoading(false);
                // UIController sẽ gọi loginSuccess() hoặc showError() sau
            }
        };
        worker.execute();
    }

    public void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            lblError.setText(message);
            btnLogin.setLoading(false);
        });
    }

    public void loginSuccess() {
        SwingUtilities.invokeLater(() -> {
            // Lưu lại tên đăng nhập và cập nhật trạng thái thành công trước khi đóng Dialog
            this.username = tfUsername.getText().trim();
            this.succeeded = true;
            dispose();
        });
    }

    // ── Setters & Getters ──

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getUsername() {
        return username;
    }

    public void setLoginListener(LoginListener listener) {
        this.loginListener = listener;
    }

    public void setOnRegisterClick(Runnable r) {
        this.onRegisterClick = r;
    }
}