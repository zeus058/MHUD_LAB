package vn.edu.hcmus.securechat.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Arrays;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Redesigned LoginPanel with 64/36 Split Layout matching Figma templates.
 */
public class LoginPanel extends JPanel {

    private static final int AUTH_FORM_WIDTH = 352;

    public interface AuthListener {
        void onLoginSuccess(String username, char[] password);
        void onNavigateRegister();
        void onAuthError(String message);
    }

    private final AuthListener listener;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel statusLabel;
    private final JButton loginButton;
    private final ActivityFlowPanel flowPanel;
    private final JPanel formContainer;

    public LoginPanel(AuthListener listener) {
        this.listener = listener;
        setOpaque(true);
        setBackground(UIConstants.DEEP_CARBON);
        setLayout(null); // Custom layout in doLayout for 64/36 split

        // Left Container: Form Container (64%)
        formContainer = new JPanel(new GridBagLayout());
        formContainer.setOpaque(false);

        // Centered Wrapper inside Form Container (max width 480)
        JPanel formWrapper = new JPanel();
        formWrapper.setOpaque(false);
        formWrapper.setLayout(new BoxLayout(formWrapper, BoxLayout.Y_AXIS));
        formWrapper.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 500));

        // 1. Brand Row
        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(LEFT_ALIGNMENT);

        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.LinearGradientPaint gp = new java.awt.LinearGradientPaint(
                    new java.awt.Point(0, 0), new java.awt.Point(0, getHeight()),
                    new float[]{0f, 1f},
                    new Color[]{new Color(0, 191, 184), new Color(0, 140, 136)}
                );
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.5f));
                int pad = 14;
                int w = getWidth() - pad * 2;
                int h = getHeight() - pad * 2;
                int rx = pad;
                int ry = pad;
                int rw = w;
                int rh = h - 4;

                Path2D.Double bubble = new Path2D.Double();
                bubble.moveTo(rx + 6, ry);
                bubble.lineTo(rx + rw - 6, ry);
                bubble.quadTo(rx + rw, ry, rx + rw, ry + 6);
                bubble.lineTo(rx + rw, ry + rh - 6);
                bubble.quadTo(rx + rw, ry + rh, rx + rw - 6, ry + rh);
                bubble.lineTo(rx + 12, ry + rh);
                bubble.lineTo(rx + 6, ry + rh + 6);
                bubble.lineTo(rx, ry + rh - 4);
                bubble.lineTo(rx, ry + 6);
                bubble.quadTo(rx, ry, rx + 6, ry);
                bubble.closePath();
                g2.draw(bubble);
                g2.dispose();
            }
        };
        logoPanel.setPreferredSize(new Dimension(52, 52));
        logoPanel.setMinimumSize(new Dimension(52, 52));
        logoPanel.setMaximumSize(new Dimension(52, 52));
        brandRow.add(logoPanel);

        JLabel brandName = new JLabel("SecureChat");
        brandName.setFont(UIConstants.FONT_TITLE.deriveFont(Font.BOLD, 28f));
        brandName.setForeground(UIConstants.TEXT_WHITE);
        brandRow.add(brandName);

        formWrapper.add(brandRow);
        formWrapper.add(Box.createVerticalStrut(12));

        // 2. Title & Subtitle
        JLabel titleLabel = new JLabel("Welcome back");
        titleLabel.setFont(UIConstants.FONT_PAGE_TITLE.deriveFont(34f));
        titleLabel.setForeground(UIConstants.TEXT_WHITE);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(titleLabel);

        formWrapper.add(Box.createVerticalStrut(8));

        JLabel subtitleLabel = new JLabel("Sign in to continue to your conversations");
        subtitleLabel.setFont(UIConstants.FONT_BODY.deriveFont(14f));
        subtitleLabel.setForeground(UIConstants.TEXT_MUTED);
        subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(subtitleLabel);

        formWrapper.add(Box.createVerticalStrut(20));

        // 3. Form Inputs
        JLabel userLbl = UiStyles.mutedLabel("Email");
        userLbl.setFont(UIConstants.FONT_HEADING.deriveFont(12f));
        userLbl.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(userLbl);
        formWrapper.add(Box.createVerticalStrut(6));

        usernameField = UiStyles.styledTextField(24);
        UiStyles.setPlaceholder(usernameField, "you@example.com");
        usernameField.setAlignmentX(LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 46));
        usernameField.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 46));
        formWrapper.add(usernameField);

        formWrapper.add(Box.createVerticalStrut(14));

        JLabel passLbl = UiStyles.mutedLabel("Password");
        passLbl.setFont(UIConstants.FONT_HEADING.deriveFont(12f));
        passLbl.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(passLbl);
        formWrapper.add(Box.createVerticalStrut(6));

        passwordField = UiStyles.styledPasswordField(24);
        UiStyles.setPlaceholder(passwordField, "Enter your password");
        passwordField.setAlignmentX(LEFT_ALIGNMENT);
        passwordField.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 46));
        passwordField.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 46));
        formWrapper.add(passwordField);

        formWrapper.add(Box.createVerticalStrut(18));

        // 4. Login Button
        loginButton = UiStyles.primaryButton("Sign In");
        loginButton.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 16f));
        loginButton.setAlignmentX(LEFT_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 46));
        loginButton.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 46));
        loginButton.addActionListener(e -> performLogin());
        formWrapper.add(loginButton);

        // 5. Status Label
        statusLabel = UiStyles.mutedLabel(" ");
        statusLabel.setForeground(UIConstants.SIGNAL_RED);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(Box.createVerticalStrut(10));
        formWrapper.add(statusLabel);

        // 6. Navigation Link
        formWrapper.add(Box.createVerticalStrut(8));
        JPanel registerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        registerRow.setOpaque(false);
        registerRow.setAlignmentX(LEFT_ALIGNMENT);
        registerRow.add(UiStyles.mutedLabel("New here?"));
        JButton registerLink = UiStyles.linkButton("Create an account");
        registerLink.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 14f));
        registerLink.addActionListener(e -> listener.onNavigateRegister());
        registerRow.add(registerLink);
        formWrapper.add(registerRow);
        formWrapper.add(Box.createVerticalGlue());

        // Add wrapper to GridBagLayout center
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(32, 64, 32, 64);
        formContainer.add(formWrapper, gbc);

        add(formContainer);

        // Right Container: Showcase Panel (36%)
        flowPanel = new ActivityFlowPanel("Login", "Secure connection setup");
        add(flowPanel);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Base dark gradient
        java.awt.LinearGradientPaint bgGrad = new java.awt.LinearGradientPaint(
            new java.awt.Point(0, 0), new java.awt.Point(0, h),
            new float[]{0f, 1f},
            new Color[]{
                new Color(11, 15, 25),
                new Color(20, 26, 38)
            }
        );
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, w, h);

        // 2. Large soft glow orb on the right side (Teal glow behind connection panel)
        java.awt.geom.Point2D centerTeal = new java.awt.geom.Point2D.Float(w * 0.8f, h * 0.4f);
        float radiusTeal = Math.max(350, w * 0.5f);
        java.awt.RadialGradientPaint pTeal = new java.awt.RadialGradientPaint(
            centerTeal, radiusTeal,
            new float[]{0f, 0.6f, 1f},
            new Color[]{
                new Color(0, 161, 156, 45),
                new Color(0, 161, 156, 10),
                new Color(0, 0, 0, 0)
            }
        );
        g2.setPaint(pTeal);
        g2.fillOval((int) (centerTeal.getX() - radiusTeal), (int) (centerTeal.getY() - radiusTeal), (int) (radiusTeal * 2), (int) (radiusTeal * 2));

        // 3. Medium soft glow orb on the left side (Purple glow behind login form)
        java.awt.geom.Point2D centerPurple = new java.awt.geom.Point2D.Float(w * 0.2f, h * 0.7f);
        float radiusPurple = Math.max(300, w * 0.4f);
        java.awt.RadialGradientPaint pPurple = new java.awt.RadialGradientPaint(
            centerPurple, radiusPurple,
            new float[]{0f, 0.7f, 1f},
            new Color[]{
                new Color(139, 92, 246, 30),
                new Color(139, 92, 246, 5),
                new Color(0, 0, 0, 0)
            }
        );
        g2.setPaint(pPurple);
        g2.fillOval((int) (centerPurple.getX() - radiusPurple), (int) (centerPurple.getY() - radiusPurple), (int) (radiusPurple * 2), (int) (radiusPurple * 2));

        // 4. Subtle vertical gradient divider line
        int showcaseW = (int) (w * 0.36);
        int dividerX = w - showcaseW;
        java.awt.LinearGradientPaint lineGrad = new java.awt.LinearGradientPaint(
            new java.awt.geom.Point2D.Float(dividerX, 0), new java.awt.geom.Point2D.Float(dividerX, h),
            new float[]{0f, 0.15f, 0.85f, 1f},
            new Color[]{
                new Color(255, 255, 255, 0),
                new Color(255, 255, 255, 20),
                new Color(255, 255, 255, 20),
                new Color(255, 255, 255, 0)
            }
        );
        g2.setPaint(lineGrad);
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawLine(dividerX, 0, dividerX, h);

        g2.dispose();
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        int showcaseW = (int) (w * 0.36);
        int formW = w - showcaseW;

        formContainer.setBounds(0, 0, formW, h);
        flowPanel.setBounds(formW, 0, showcaseW, h);
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        statusLabel.setText(" ");
        if (username.isEmpty() || password.length == 0) {
            statusLabel.setForeground(UIConstants.SIGNAL_RED);
            statusLabel.setText("Please enter both email and password.");
            Arrays.fill(password, '\0');
            return;
        }

        trace("Starting Kerberos", "Opening the identity keystore for @" + username
                + " and preparing to sign TGT/ST requests.", ActivityFlowPanel.Tone.ACTIVE);
        listener.onLoginSuccess(username, password.clone());
        Arrays.fill(password, '\0');
    }

    public void setConnecting(boolean connecting) {
        setFormEnabled(!connecting);
        if (connecting) {
            statusLabel.setForeground(UIConstants.SECURE_TEAL);
            statusLabel.setText("Connecting securely...");
            loginButton.setText("Connecting...");
            trace("Processing session", "NTP, TGT, ST, Chat handshake, and Pre-Key upload will run in sequence.",
                    ActivityFlowPanel.Tone.ACTIVE);
        } else {
            loginButton.setText("Sign In");
            passwordField.setText("");
        }
    }

    public void showAuthError(String message) {
        statusLabel.setForeground(UIConstants.SIGNAL_RED);
        statusLabel.setText(message);
        trace("Sign-in rejected", message, ActivityFlowPanel.Tone.ERROR);
        setFormEnabled(true);
        loginButton.setText("Sign In");
    }

    public void trace(String title, String body, ActivityFlowPanel.Tone tone) {
        flowPanel.addEvent(title, body, tone);
    }

    public void reset() {
        passwordField.setText("");
        statusLabel.setText(" ");
        loginButton.setText("Sign In");
        setFormEnabled(true);
        flowPanel.clear();
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }
}
