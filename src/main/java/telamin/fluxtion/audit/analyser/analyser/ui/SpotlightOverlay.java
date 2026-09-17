package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * M64 — the spotlight itself: the frame's glass pane ({@code spec-spotlight.md} D-SP1, D-SP3).
 *
 * <p><b>It is dumb on purpose.</b> It does four things and nothing else: dim the frame, cut the target
 * out, draw an arrow from the caption to the cut-out, and go away. It does not know what a "target" is —
 * it is handed a rectangle and a sentence. Where the rectangle came from is {@link SpotlightTarget}'s
 * business, and that half is pure and tested headless.
 *
 * <p><b>It goes away easily</b>, because a spotlight that outlives its context points at the wrong thing,
 * which is worse than none: any click, Escape, {@code spotlight {clear}}, and any verb that changes the
 * view. It is never a modal and never blocks (M35.9): the click that dismisses it is the only input it
 * takes.
 *
 * <p><b>The caption is TESTIMONY</b> (D-SP2) — the tutor's words, not a fact the app established — so it
 * is tagged "assistant" and drawn in the muted callout style, never in the app's own status colours.
 *
 * <p><b>Transient by construction</b> (D-SP4): the only state is the two fields below. Nothing here is
 * read by the config, the profile, a saved graph or a report.
 */
public final class SpotlightOverlay extends JComponent {

    private static final String TAG = "assistant";

    private String targetName;
    private Rectangle target;
    private String caption;
    private final Runnable onDismissed;

    public SpotlightOverlay(Runnable onDismissed) {
        this.onDismissed = onDismissed == null ? () -> { } : onDismissed;
        setOpaque(false);
        setVisible(false);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dismiss();
            }
        });
        registerKeyboardAction(e -> dismiss(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Light {@code bounds} (overlay coordinates). One spotlight at a time: a new one replaces the last. */
    public void light(String targetName, Rectangle bounds, String caption) {
        this.targetName = targetName;
        this.target = new Rectangle(bounds);
        this.caption = caption == null || caption.isBlank() ? null : caption.trim();
        setVisible(true);
        repaint();
    }

    /** Move a live spotlight — the frame was resized and its target is somewhere else now. */
    public void moveTo(Rectangle bounds) {
        if (!isLit()) return;
        this.target = new Rectangle(bounds);
        repaint();
    }

    /** Put it out. Silent when nothing is lit. */
    public void clearSpotlight() {
        if (!isLit()) return;
        targetName = null;
        target = null;
        caption = null;
        setVisible(false);
        repaint();
    }

    private void dismiss() {
        if (!isLit()) return;
        clearSpotlight();
        onDismissed.run();
    }

    public boolean isLit() {
        return target != null;
    }

    public String targetName() {
        return targetName;
    }

    public String caption() {
        return caption;
    }

    /** The cut-out as drawn, in overlay coordinates; null when nothing is lit. */
    public Rectangle cutOut() {
        return target == null ? null : SpotlightGeometry.cutOut(target, getSize());
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isLit()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            paintSpotlight(g2, getSize());
        } finally {
            g2.dispose();
        }
    }

    /**
     * Paint a live spotlight onto another component's image — the {@code screenshot} verb paints the
     * CONTENT PANE (or one panel), which does not include the glass pane, so without this the tutor's own
     * verification shot would show no spotlight. {@code g} is that component's graphics; the spotlight is
     * translated so the cut-out lands on the same pixels it covers on screen.
     */
    public void paintOnto(Graphics2D g, Component painted) {
        if (!isLit() || painted == null) return;
        Point origin = javax.swing.SwingUtilities.convertPoint(this, 0, 0, painted);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(origin.x, origin.y);
            paintSpotlight(g2, getSize());
        } finally {
            g2.dispose();
        }
    }

    private void paintSpotlight(Graphics2D g, Dimension size) {
        boolean dark = ThemeManager.isDark();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Rectangle cut = SpotlightGeometry.cutOut(target, size);
        RoundRectangle2D hole = new RoundRectangle2D.Double(cut.x, cut.y, cut.width, cut.height, 10, 10);
        Area dim = new Area(new Rectangle(0, 0, size.width, size.height));
        dim.subtract(new Area(hole));
        g.setColor(new Color(0, 0, 0, dark ? 150 : 115));
        g.fill(dim);

        Color accent = UiTheme.accent();
        g.setColor(accent);
        g.setStroke(new BasicStroke(2f));
        g.draw(hole);

        if (caption == null) return;
        g.setFont(getFont() == null ? g.getFont() : getFont().deriveFont(13f));
        FontMetrics fm = g.getFontMetrics();
        int pad = 10, tagGap = 4;
        int maxText = Math.max(160, Math.min(380, size.width - 80));
        List<String> lines = wrap(fm, caption, maxText);
        int textW = fm.stringWidth(TAG);
        for (String line : lines) textW = Math.max(textW, fm.stringWidth(line));
        Dimension box = new Dimension(textW + pad * 2 + 6, (lines.size() + 1) * fm.getHeight() + tagGap + pad * 2);
        Rectangle at = SpotlightGeometry.captionBox(cut, box, size);

        Point[] arrow = SpotlightGeometry.arrow(at, cut);
        if (!arrow[0].equals(arrow[1])) {
            g.setColor(accent);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(arrow[0].x, arrow[0].y, arrow[1].x, arrow[1].y);
            g.fill(arrowHead(arrow[0], arrow[1]));
        }

        Color fill = dark ? new Color(0x1B1F24) : new Color(0xFFFFFF);
        g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 244));
        g.fillRoundRect(at.x, at.y, at.width, at.height, 8, 8);
        g.setColor(dark ? new Color(0x3D444D) : new Color(0xC2CAD3));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(at.x, at.y, at.width, at.height, 8, 8);
        g.setColor(accent);
        g.fillRoundRect(at.x, at.y, 4, at.height, 4, 4);

        int y = at.y + pad + fm.getAscent();
        g.setColor(UiTheme.mutedForeground());
        g.drawString(TAG, at.x + pad + 6, y);              // testimony is labelled as testimony
        y += fm.getHeight() + tagGap;
        g.setColor(dark ? new Color(0xC9D1D9) : new Color(0x24292F));
        for (String line : lines) {
            g.drawString(line, at.x + pad + 6, y);
            y += fm.getHeight();
        }
    }

    private static Path2D arrowHead(Point from, Point to) {
        double angle = Math.atan2(to.y - from.y, to.x - from.x);
        double size = 9, spread = Math.toRadians(26);
        Path2D head = new Path2D.Double();
        head.moveTo(to.x, to.y);
        head.lineTo(to.x - size * Math.cos(angle - spread), to.y - size * Math.sin(angle - spread));
        head.lineTo(to.x - size * Math.cos(angle + spread), to.y - size * Math.sin(angle + spread));
        head.closePath();
        return head;
    }

    /** Greedy word wrap to a pixel width; a word wider than the box is left long rather than broken. */
    private static List<String> wrap(FontMetrics fm, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && fm.stringWidth(trial) > maxWidth) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
