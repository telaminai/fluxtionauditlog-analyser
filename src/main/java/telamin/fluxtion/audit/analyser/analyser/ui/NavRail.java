package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.function.Consumer;

/**
 * A narrow vertical rail of tool buttons down the edge of the window, IntelliJ-style: the label reads
 * bottom-to-top so a full word fits in the width of a button (M22.17).
 *
 * <p>It exists to make a docked panel <b>optional</b> without hiding it in a menu. The event-type
 * checklist is the case that prompted it: permanently docked it costs 240px of a window whose whole job
 * is showing wide records, but demoted to a menu item it stops being discoverable and people stop
 * filtering. A rail keeps the affordance visible and its cost near zero.
 */
public final class NavRail extends JPanel {

    private static final int PAD_X = 6;
    private static final int PAD_Y = 10;

    public NavRail() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.surfaceEdge()));
    }

    /** Re-apply theme-derived chrome after a look-and-feel change. */
    public void refreshTheme() {
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.surfaceEdge()));
    }

    /**
     * A latching button for showing/hiding a panel.
     *
     * @param text     the rail label, drawn rotated
     * @param selected initial state — true means the panel it controls is showing
     * @param onToggle told the new state
     * @return the button, for callers that want to drive it programmatically
     */
    public JToggleButton addToggle(String text, boolean selected, Consumer<Boolean> onToggle) {
        JToggleButton button = new JToggleButton(new RotatedText(text), selected);
        style(button, text);
        button.addActionListener(e -> onToggle.accept(button.isSelected()));
        add(button);
        add(Box.createVerticalStrut(2));
        return button;
    }

    /** A momentary button — used where the rail opens a popup rather than toggling a panel. */
    public JButton addAction(String text, Runnable action) {
        JButton button = new JButton(new RotatedText(text));
        style(button, text);
        button.addActionListener(e -> action.run());
        add(button);
        add(Box.createVerticalStrut(2));
        return button;
    }

    /** Pushes everything added after this call to the bottom of the rail. */
    public void addGap() {
        add(Box.createVerticalGlue());
    }

    private static void style(AbstractButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setMaximumSize(button.getPreferredSize());
    }

    /**
     * The rail label, painted rotated a quarter turn anticlockwise so it reads bottom-to-top. Drawing it
     * as an {@link Icon} rather than overriding a button's paint keeps every look-and-feel detail — hover,
     * pressed, selected, focus — in the hands of FlatLaf.
     */
    private static final class RotatedText implements Icon {
        private final String text;
        private final Font font;
        private final int width;
        private final int height;

        RotatedText(String text) {
            this.text = text;
            Font base = UIManager.getFont("Button.font");
            this.font = base != null ? base.deriveFont(Font.PLAIN, base.getSize2D() - 1f)
                    : new Font(Font.SANS_SERIF, Font.PLAIN, 11);
            FontMetrics fm = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    .createGraphics().getFontMetrics(font);
            // rotated: the text's width becomes the icon's height
            this.height = fm.stringWidth(text) + PAD_Y * 2;
            this.width = fm.getHeight() + PAD_X;
        }

        @Override public int getIconWidth() {
            return width;
        }

        @Override public int getIconHeight() {
            return height;
        }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font);
            g2.setColor(c.getForeground());
            AffineTransform at = new AffineTransform();
            at.translate(x, y + height);
            at.rotate(-Math.PI / 2);
            g2.transform(at);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, PAD_Y, fm.getAscent());
            g2.dispose();
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(getPreferredSize().width, Integer.MAX_VALUE);
    }
}
