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
 * <p><b>Several at once</b> (M64.6): a finding is often a relation — <i>this</i> node, <i>that</i> record, the
 * crossing on the chart — so up to {@link SpotlightTarget#MAX_LIT} things can be lit together, each with its
 * own callout. They are NUMBERED when there is more than one, so the tutor's sentence ("② never logged")
 * finds its cut-out. A number is kept for the life of its spotlight: putting one out does not renumber the
 * rest, because the chat that named them has already been read.
 *
 * <p><b>Transient by construction</b> (D-SP4): the only state is the list below. Nothing here is read by the
 * config, the profile, a saved graph or a report.
 */
public final class SpotlightOverlay extends JComponent {

    private static final String TAG = "assistant";
    private static final int BADGE = 22;

    /** One lit thing: its number, the target's name, where it is (overlay coordinates), its callout or null. */
    public record Lit(int n, String target, Rectangle bounds, String caption) {
    }

    private final List<Lit> lit = new ArrayList<>();
    /**
     * The next number to hand out — a HIGH-WATER MARK, not "the current maximum plus one" (review of M64.6,
     * F1). Deriving it from what is lit let a number be reused: light 1 and 2, put out 2, add another, and
     * the newcomer was "2" — so the chat's earlier "2" now named a different thing, the exact failure stable
     * numbers exist to prevent. It restarts only when the set is replaced or emptied.
     */
    private int nextNumber = 1;
    private final Runnable onDismissed;
    private java.util.function.BiConsumer<Point, List<Lit>> onPressed = (p, l) -> { };

    public SpotlightOverlay(Runnable onDismissed) {
        this.onDismissed = onDismissed == null ? () -> { } : onDismissed;
        setOpaque(false);
        setVisible(false);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                List<Lit> was = lit();
                dismiss();
                onPressed.accept(e.getPoint(), was);   // M64.11: the frame may choose a lit menu item under the press
            }
        });
        registerKeyboardAction(e -> dismiss(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** The dismissing press, with what was lit at that moment and where it was pressed (overlay coordinates). */
    public void setOnPressed(java.util.function.BiConsumer<Point, List<Lit>> onPressed) {
        this.onPressed = onPressed == null ? (p, l) -> { } : onPressed;
    }

    /** Light ONE thing, replacing whatever was lit. */
    public void light(String targetName, Rectangle bounds, String caption) {
        lit.clear();
        nextNumber = 1;
        add(targetName, bounds, caption);
    }

    /**
     * Light one MORE thing, keeping what is lit. A target already lit is re-lit in place (same number, new
     * callout) rather than lit twice. Returns the number it carries. The caller holds the bound
     * ({@link SpotlightTarget#MAX_LIT}); the overlay draws whatever it is given.
     */
    public int add(String targetName, Rectangle bounds, String caption) {
        String words = caption == null || caption.isBlank() ? null : caption.trim();
        for (int i = 0; i < lit.size(); i++) {
            if (lit.get(i).target().equalsIgnoreCase(targetName)) {
                int n = lit.get(i).n();
                lit.set(i, new Lit(n, targetName, new Rectangle(bounds), words));
                setVisible(true);
                repaint();
                return n;
            }
        }
        int n = nextNumber++;
        lit.add(new Lit(n, targetName, new Rectangle(bounds), words));
        setVisible(true);
        repaint();
        return n;
    }

    /** Put ONE out. True when it was lit. The others keep their numbers. */
    public boolean remove(String targetName) {
        boolean was = lit.removeIf(l -> l.target().equalsIgnoreCase(targetName));
        if (lit.isEmpty()) wentDark();
        repaint();
        return was;
    }

    /** Preserve the original spotlight number when an edited design qualifies its testimony. */
    public void markDesignEdited(String targetName) {
        for (int i = 0; i < lit.size(); i++) {
            Lit l = lit.get(i);
            if (l.target().equals(targetName) && (l.caption() == null || !l.caption().contains("(design edited since this caption)")))
                lit.set(i, new Lit(l.n(), l.target(), l.bounds(), (l.caption() == null ? "" : l.caption() + " ") + "(design edited since this caption)"));
        }
        repaint();
    }

    /**
     * Measure every lit target again — the frame was resized, or the layout a reveal queued has now run.
     * One that can no longer be measured goes OUT (pointing at where something used to be is the failure
     * this feature is careful about); the names that went out are returned so the caller can say so.
     */
    public List<String> remeasure(java.util.function.Function<String, java.util.Optional<Rectangle>> whereIs) {
        List<String> out = new ArrayList<>();
        for (int i = lit.size() - 1; i >= 0; i--) {
            Lit l = lit.get(i);
            java.util.Optional<Rectangle> at = whereIs.apply(l.target());
            if (at.isPresent() && !at.get().isEmpty()) lit.set(i, new Lit(l.n(), l.target(), new Rectangle(at.get()), l.caption()));
            else out.add(0, lit.remove(i).target());
        }
        if (lit.isEmpty()) wentDark();
        repaint();
        return out;
    }

    /** Put them all out. Silent when nothing is lit. */
    public void clearSpotlight() {
        if (!isLit()) return;
        lit.clear();
        wentDark();
        repaint();
    }

    /** Nothing is lit: stop swallowing clicks, and let the numbers start again — nobody is referring to them. */
    private void wentDark() {
        nextNumber = 1;
        setVisible(false);
    }

    private void dismiss() {
        if (!isLit()) return;
        clearSpotlight();
        onDismissed.run();
    }

    public boolean isLit() {
        return !lit.isEmpty();
    }

    /** What is lit, in the order it was lit. A copy. */
    public List<Lit> lit() {
        return List.copyOf(lit);
    }

    /** One target's cut-out as drawn, in overlay coordinates; null when it is not lit. */
    public Rectangle cutOutOf(String targetName) {
        List<Rectangle> cuts = cuts(getSize());
        for (int i = 0; i < lit.size(); i++) {
            if (lit.get(i).target().equalsIgnoreCase(targetName)) return cuts.get(i);
        }
        return null;
    }

    /** Every lit target's cut-out, in lit order; neighbours share one separator instead of crossing each other. */
    private List<Rectangle> cuts(Dimension size) {
        List<Rectangle> targets = new ArrayList<>();
        for (Lit l : lit) targets.add(l.bounds());
        return SpotlightGeometry.cutOuts(targets, size);
    }

    /** A number is drawn once there is more than one thing to tell apart — or once this one has been called "②". */
    private boolean numbered(Lit l) {
        return lit.size() > 1 || l.n() > 1;
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
     * Paint the live spotlights onto another component's image — the {@code screenshot} verb paints the
     * CONTENT PANE (or one panel), which does not include the glass pane, so without this the tutor's own
     * verification shot would show no spotlight. {@code g} is that component's graphics; the overlay is
     * translated so each cut-out lands on the same pixels it covers on screen.
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
        g.setFont(getFont() == null ? g.getFont() : getFont().deriveFont(13f));
        FontMetrics fm = g.getFontMetrics();
        Color accent = UiTheme.accent();

        // ONE dim with a hole per spotlight: overlapping cut-outs merge, and nothing lit is ever tinted
        List<Rectangle> cuts = cuts(size);
        Area dim = new Area(new Rectangle(0, 0, size.width, size.height));
        for (Rectangle cut : cuts) dim.subtract(new Area(hole(cut)));
        g.setColor(new Color(0, 0, 0, dark ? 150 : 115));
        g.fill(dim);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2f));
        for (Rectangle cut : cuts) g.draw(hole(cut));

        int pad = 10, tagGap = 4;
        int maxText = Math.max(160, Math.min(380, size.width - 80));
        List<List<String>> wrapped = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        List<Dimension> boxes = new ArrayList<>();
        for (Lit l : lit) {
            String tag = numbered(l) ? TAG + " · " + l.n() : TAG;
            tags.add(tag);
            if (l.caption() == null) {
                wrapped.add(List.of());
                boxes.add(null);
                continue;
            }
            List<String> lines = wrap(fm, l.caption(), maxText);
            int textW = fm.stringWidth(tag);
            for (String line : lines) textW = Math.max(textW, fm.stringWidth(line));
            wrapped.add(lines);
            boxes.add(new Dimension(textW + pad * 2 + 6, (lines.size() + 1) * fm.getHeight() + tagGap + pad * 2));
        }
        List<Rectangle> callouts = SpotlightGeometry.layout(cuts, boxes, size);

        // arrows first, so a callout box is never crossed by another spotlight's arrow
        for (int i = 0; i < lit.size(); i++) {
            Rectangle at = callouts.get(i);
            if (at == null) continue;
            Point[] arrow = SpotlightGeometry.arrow(at, cuts.get(i));
            if (arrow[0].equals(arrow[1])) continue;
            g.setColor(accent);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(arrow[0].x, arrow[0].y, arrow[1].x, arrow[1].y);
            g.fill(arrowHead(arrow[0], arrow[1]));
        }

        for (int i = 0; i < lit.size(); i++) {
            Rectangle at = callouts.get(i);
            if (at == null) continue;
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
            g.drawString(tags.get(i), at.x + pad + 6, y);      // testimony is labelled as testimony
            y += fm.getHeight() + tagGap;
            g.setColor(dark ? new Color(0xC9D1D9) : new Color(0x24292F));
            for (String line : wrapped.get(i)) {
                g.drawString(line, at.x + pad + 6, y);
                y += fm.getHeight();
            }
        }

        // the numbers last: a badge is what ties a cut-out to its callout and to the tutor's sentence
        for (int i = 0; i < lit.size(); i++) {
            Lit l = lit.get(i);
            if (!numbered(l)) continue;
            Rectangle b = SpotlightGeometry.badge(cuts.get(i), BADGE, size);
            g.setColor(accent);
            g.fillOval(b.x, b.y, b.width, b.height);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 12f));
            FontMetrics bm = g.getFontMetrics();
            String n = Integer.toString(l.n());
            g.drawString(n, b.x + (b.width - bm.stringWidth(n)) / 2, b.y + (b.height - bm.getHeight()) / 2 + bm.getAscent());
        }
    }

    private static RoundRectangle2D hole(Rectangle cut) {
        return new RoundRectangle2D.Double(cut.x, cut.y, cut.width, cut.height, 10, 10);
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
