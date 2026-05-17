package vn.edu.hcmus.securechat.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Thanh cuộn mảnh, tối — phù hợp dark theme. */
public class SlimScrollBarUI extends BasicScrollBarUI {

  private static final Color TRACK = UIConstants.DEEP_CARBON;
  private static final Color THUMB = UIConstants.DARK_SILVER;
  private static final Color THUMB_HOVER = UIConstants.SECURE_TEAL;

  @Override
  protected void configureScrollBarColors() {
    thumbColor = THUMB;
    trackColor = TRACK;
  }

  @Override
  protected JButton createDecreaseButton(int orientation) {
    return zeroSizeButton();
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return zeroSizeButton();
  }

  private JButton zeroSizeButton() {
    JButton b = new JButton();
    b.setPreferredSize(new Dimension(0, 0));
    b.setMinimumSize(new Dimension(0, 0));
    b.setMaximumSize(new Dimension(0, 0));
    return b;
  }

  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
    g.setColor(TRACK);
    g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
  }

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(isThumbRollover() ? THUMB_HOVER : THUMB);
    int inset = 4;
    if (scrollbar.getOrientation() == VERTICAL) {
      g2.fillRoundRect(thumbBounds.x + inset, thumbBounds.y,
          thumbBounds.width - inset * 2, thumbBounds.height, 8, 8);
    } else {
      g2.fillRoundRect(thumbBounds.x, thumbBounds.y + inset,
          thumbBounds.width, thumbBounds.height - inset * 2, 8, 8);
    }
    g2.dispose();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return new Dimension(8, 8);
  }
}
