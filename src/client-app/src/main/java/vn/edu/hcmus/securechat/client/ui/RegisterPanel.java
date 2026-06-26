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
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.hcmus.securechat.client.crypto.PkiManager;
import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;
import vn.edu.hcmus.securechat.client.network.SocketClient;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateSigningRequest;

/**
 * Redesigned RegisterPanel with inverted 36/64 Split Layout matching Figma templates.
 */
public class RegisterPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(RegisterPanel.class);
    private static final int AUTH_FORM_WIDTH = 352;

    public interface RegisterListener {
        void onRegisterSuccess(String username);
        void onNavigateLogin();
        void onAuthError(String message);
    }

    private final RegisterListener listener;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmField;
    private final JLabel statusLabel;
    private final JButton registerButton;
    private final ActivityFlowPanel flowPanel;
    private final JPanel formContainer;

    public RegisterPanel(RegisterListener listener) {
        this.listener = listener;
        setOpaque(true);
        setBackground(UIConstants.DEEP_CARBON);
        setLayout(null); // Custom layout in doLayout for 36/64 split

        // Left Container: Showcase Panel (36%)
        flowPanel = new ActivityFlowPanel("Register", "Trusted identity setup");
        add(flowPanel);

        // Right Container: Form Container (64%)
        formContainer = new JPanel(new GridBagLayout());
        formContainer.setOpaque(false);

        // Centered Wrapper inside Form Container (max width 480)
        JPanel formWrapper = new JPanel();
        formWrapper.setOpaque(false);
        formWrapper.setLayout(new BoxLayout(formWrapper, BoxLayout.Y_AXIS));
        formWrapper.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 540));

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
        JLabel titleLabel = new JLabel("Create your account");
        titleLabel.setFont(UIConstants.FONT_PAGE_TITLE.deriveFont(32f));
        titleLabel.setForeground(UIConstants.TEXT_WHITE);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(titleLabel);

        formWrapper.add(Box.createVerticalStrut(6));

        JLabel subtitleLabel = new JLabel("Get started in less than a minute");
        subtitleLabel.setFont(UIConstants.FONT_BODY.deriveFont(14f));
        subtitleLabel.setForeground(UIConstants.TEXT_MUTED);
        subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(subtitleLabel);

        formWrapper.add(Box.createVerticalStrut(16));

        // 3. Form Inputs
        JLabel userLbl = UiStyles.mutedLabel("Email");
        userLbl.setFont(UIConstants.FONT_HEADING.deriveFont(12f));
        userLbl.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(userLbl);
        formWrapper.add(Box.createVerticalStrut(6));

        usernameField = UiStyles.styledTextField(24);
        UiStyles.setPlaceholder(usernameField, "you@example.com");
        usernameField.setAlignmentX(LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 40));
        usernameField.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 40));
        formWrapper.add(usernameField);

        formWrapper.add(Box.createVerticalStrut(10));

        JLabel passLbl = UiStyles.mutedLabel("Password");
        passLbl.setFont(UIConstants.FONT_HEADING.deriveFont(12f));
        passLbl.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(passLbl);
        formWrapper.add(Box.createVerticalStrut(6));

        passwordField = UiStyles.styledPasswordField(24);
        UiStyles.setPlaceholder(passwordField, "Enter your password");
        passwordField.setAlignmentX(LEFT_ALIGNMENT);
        passwordField.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 40));
        passwordField.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 40));
        formWrapper.add(passwordField);

        formWrapper.add(Box.createVerticalStrut(10));

        JLabel confirmLbl = UiStyles.mutedLabel("Confirm Password");
        confirmLbl.setFont(UIConstants.FONT_HEADING.deriveFont(12f));
        confirmLbl.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(confirmLbl);
        formWrapper.add(Box.createVerticalStrut(6));

        confirmField = UiStyles.styledPasswordField(24);
        UiStyles.setPlaceholder(confirmField, "Repeat your password");
        confirmField.setAlignmentX(LEFT_ALIGNMENT);
        confirmField.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 40));
        confirmField.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 40));
        formWrapper.add(confirmField);

        formWrapper.add(Box.createVerticalStrut(16));

        // 4. Register Button
        registerButton = UiStyles.primaryButton("Create Account");
        registerButton.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 15f));
        registerButton.setAlignmentX(LEFT_ALIGNMENT);
        registerButton.setMaximumSize(new Dimension(AUTH_FORM_WIDTH, 40));
        registerButton.setPreferredSize(new Dimension(AUTH_FORM_WIDTH, 40));
        registerButton.addActionListener(e -> performRegister());
        formWrapper.add(registerButton);

        // 5. Status Label
        statusLabel = UiStyles.mutedLabel(" ");
        statusLabel.setForeground(UIConstants.SIGNAL_RED);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        formWrapper.add(Box.createVerticalStrut(8));
        formWrapper.add(statusLabel);

        // 6. Navigation Link
        formWrapper.add(Box.createVerticalStrut(6));
        JPanel loginRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        loginRow.setOpaque(false);
        loginRow.setAlignmentX(LEFT_ALIGNMENT);
        loginRow.add(UiStyles.mutedLabel("Already have an account?"));
        JButton loginLink = UiStyles.linkButton("Sign in");
        loginLink.setFont(UIConstants.FONT_BODY.deriveFont(Font.BOLD, 14f));
        loginLink.addActionListener(e -> listener.onNavigateLogin());
        loginRow.add(loginLink);
        formWrapper.add(loginRow);
        formWrapper.add(javax.swing.Box.createVerticalGlue());

        // Add wrapper to GridBagLayout center
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(24, 64, 24, 64);
        formContainer.add(formWrapper, gbc);

        add(formContainer);
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

        // 2. Large soft glow orb on the left side (Teal glow behind connection panel)
        java.awt.geom.Point2D centerTeal = new java.awt.geom.Point2D.Float(w * 0.2f, h * 0.4f);
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

        // 3. Medium soft glow orb on the right side (Purple glow behind registration form)
        java.awt.geom.Point2D centerPurple = new java.awt.geom.Point2D.Float(w * 0.8f, h * 0.7f);
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
        int dividerX = showcaseW;
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

        flowPanel.setBounds(0, 0, showcaseW, h);
        formContainer.setBounds(showcaseW, 0, formW, h);
    }

    private void performRegister() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();

        statusLabel.setText(" ");
        try {
            if (username.isEmpty() || password.length == 0) {
                statusLabel.setText("Please fill in all required information.");
                Arrays.fill(password, '\0');
                return;
            }
            if (!Arrays.equals(password, confirm)) {
                statusLabel.setText("Password confirmation does not match.");
                Arrays.fill(password, '\0');
                return;
            }
            if (ClientStoragePaths.keystoreExists(username)
                    || java.nio.file.Files.isRegularFile(
                            java.nio.file.Path.of("data/client", "keystore_" + username + ".p12"))) {
                statusLabel.setForeground(UIConstants.SECURE_TEAL);
                statusLabel.setText("Account \"" + username + "\" already exists. Please sign in.");
                trace("Certificate already exists", "@" + username
                        + " already has a local keystore, so you can switch to sign-in.", ActivityFlowPanel.Tone.INFO);
                Arrays.fill(password, '\0');
                return;
            }
        } finally {
            Arrays.fill(confirm, '\0');
        }

        setFormEnabled(false);
        statusLabel.setForeground(UIConstants.SECURE_TEAL);
        statusLabel.setText("Registering your account...");
        trace("Starting registration", "Generating keys, creating a CSR, and sending it to the CA Server.", ActivityFlowPanel.Tone.ACTIVE);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    trace("Generate identity keypair", "The private key is created locally and never sent over the network.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    PkiManager.generateKeyPair(username);
                    
                    trace("Sign CSR", "The CSR is signed with the private key so the CA can verify Proof-of-Possession.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    CertificateSigningRequest req = PkiManager.createCsrPayload("CN=" + username + ", O=Mock");
                    
                    PacketFrame frame = new PacketFrame(PacketFrame.TYPE_CSR_REQUEST, (byte)1, (short)0, JsonSerializer.toBytes(req));
                    
                    trace("Send to CA", "PacketFrame TYPE_CSR_REQUEST is sent over the length-prefixed socket.",
                            ActivityFlowPanel.Tone.ACTIVE);
                    PacketFrame response = SocketClient.sendRequest(vn.edu.hcmus.securechat.common.config.ServerConfig.CA_HOST, vn.edu.hcmus.securechat.common.config.ServerConfig.CA_PORT, frame);
                    
                    if (response.getType() == PacketFrame.TYPE_CERT_RESPONSE) {
                        CertificateResponse certResp = JsonSerializer.fromBytes(response.getPayload(), CertificateResponse.class);
                        trace("Save keystore", "The X.509 certificate and CA chain are written to the PKCS#12 keystore for @"
                                + username + ".", ActivityFlowPanel.Tone.ACTIVE);
                        PkiManager.saveKeyStore(username, password, certResp.getCertificate(), certResp.getCaChain());
                        return true;
                    }
                } catch (Exception e) {
                    log.error("PKI registration failed", e);
                }
                return false;
            }

            @Override
            protected void done() {
                try {
                    if (Boolean.TRUE.equals(get())) {
                        trace("Registration complete", "@" + username
                                + " now has a certificate for requesting TGT/ST during sign-in.", ActivityFlowPanel.Tone.SUCCESS);
                        listener.onRegisterSuccess(username);
                    } else {
                        statusLabel.setForeground(UIConstants.SIGNAL_RED);
                        statusLabel.setText("Registration failed. Please try again.");
                        trace("Registration failed", "The CA did not return a valid certificate response.",
                                ActivityFlowPanel.Tone.ERROR);
                        listener.onAuthError("Registration failed. Please try again.");
                    }
                } catch (Exception e) {
                    log.error("Register failed", e);
                    statusLabel.setForeground(UIConstants.SIGNAL_RED);
                    statusLabel.setText("Registration failed. Please try again.");
                    trace("Registration failed", e.getMessage(), ActivityFlowPanel.Tone.ERROR);
                    listener.onAuthError("Registration failed. Please try again.");
                } finally {
                    Arrays.fill(password, '\0');
                    passwordField.setText("");
                    confirmField.setText("");
                    setFormEnabled(true);
                }
            }
        }.execute();
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmField.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    public void trace(String title, String body, ActivityFlowPanel.Tone tone) {
        flowPanel.addEvent(title, body, tone);
    }
}
