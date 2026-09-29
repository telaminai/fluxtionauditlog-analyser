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

    // ---- M69: a walk's control strip, and the right-click save menu (spec-spotlight-walks.md §3.7) ------------
    //
    // The overlay stays dumb: it DRAWS the strip it is given (rendered by the frame from the session snapshot) and
    // REPORTS presses on it and right-clicks; the session decides what a press means. Input is classified BEFORE the
    // dismissal below, because the M64.11 hook runs after the spotlight has already gone (review R7).

    /** What the strip shows: every field a rendering of the session's walkPlayback state. */
    public record Strip(String title, String position, String state, List<String> reasons,
                        boolean canBack, boolean canNext, String action) {
        public Strip {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }
    }

    /** The strip's controls, as they are reported. */
    public enum StripControl { BACK, NEXT, END, ACTION }

    private Strip strip;
    private java.util.function.Consumer<StripControl> onStrip = c -> { };
    private java.util.function.Consumer<java.awt.event.MouseEvent> onPopup = e -> { };
    /** Presses up to this event time are swallowed: they closed the save menu, and must not also end the walk. */
    private long swallowPressesUntil = Long.MIN_VALUE;
    private long popupShownAt = Long.MIN_VALUE;
    private java.awt.Component focusBeforeStrip;

    public SpotlightOverlay(Runnable onDismissed) {
        this.onDismissed = onDismissed == null ? () -> { } : onDismissed;
        setOpaque(false);
        setVisible(false);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                // M69 R7: classify first. A popup request never dismisses — on platforms whose trigger is the
                // RELEASE, the right button's press must not dismiss either, or the spotlight is gone before it.
                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    if (e.isPopupTrigger()) popup(e);
                    return;
                }
                if (e.getWhen() <= swallowPressesUntil) return;      // it closed the save menu
                StripControl hit = stripControlAt(e.getPoint());
                if (hit != null) {
                    onStrip.accept(hit);
                    return;
                }
                if (strip != null && stripBounds(getSize()).contains(e.getPoint())) return;   // the strip's own face
                List<Lit> was = lit();
                dismiss();
                onPressed.accept(e.getPoint(), was);   // M64.11: the frame may choose a lit menu item under the press
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) popup(e);                    // the release-trigger platforms
            }
        });
        registerKeyboardAction(e -> dismiss(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        // M69: the strip's arrows are bound on the OVERLAY, which takes focus while a walk shows — so a focused table
        // or combo underneath cannot consume them first (R7)
        registerKeyboardAction(e -> { if (strip != null && strip.canBack()) onStrip.accept(StripControl.BACK); },
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_FOCUSED);
        registerKeyboardAction(e -> { if (strip != null && strip.canNext()) onStrip.accept(StripControl.NEXT); },
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_FOCUSED);
    }

    /**
     * OA-2 (spec-onboard-assistant-journeys.md §3): a region whose presses are the ASSISTANT's, not an outside click. Swing
     * routes a glass-pane press by {@link #contains}, so answering false there lets the press reach the docked assistant
     * through the ordinary route — no event is re-dispatched by hand — and a showing walk is not ended by scrolling or
     * copying its conversation. The assistant's own window needs no such rule: its presses never reach this overlay.
     */
    private java.util.function.Supplier<Rectangle> passThrough = () -> null;

    public void setPassThrough(java.util.function.Supplier<Rectangle> region) {
        this.passThrough = region == null ? () -> null : region;
    }

    @Override
    public boolean contains(int x, int y) {
        Rectangle region = passThrough.get();
        if (region != null && region.contains(x, y)) return false;
        return super.contains(x, y);
    }

    /** M69: where strip presses are reported. */
    public void setOnStrip(java.util.function.Consumer<StripControl> listener) {
        this.onStrip = listener == null ? c -> { } : listener;
    }

    /** M69: where a right-click on the overlay is reported, with the event (the frame shows the save menu). */
    public void setOnPopup(java.util.function.Consumer<java.awt.event.MouseEvent> listener) {
        this.onPopup = listener == null ? e -> { } : listener;
    }

    /** M69: the save menu closed at {@code when}; a press delivered with that timestamp closed it, and is swallowed. */
    public void swallowPressesUntil(long when) {
        this.swallowPressesUntil = when;
    }

    private void popup(MouseEvent e) {
        if (e.getWhen() == popupShownAt) return;                  // press AND release both report the trigger: act once
        popupShownAt = e.getWhen();
        onPopup.accept(e);
    }

    /**
     * M69: show (non-null) or remove (null) the walk's control strip. While it shows, the overlay is visible even with
     * nothing lit — a step can be NOT_SHOWN and still have to say why — and it holds keyboard focus, restoring the
     * previous focus owner when the strip goes.
     */
    public void setStrip(Strip next) {
        boolean was = strip != null;
        strip = next;
        if (next != null && !was) {
            focusBeforeStrip = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            setFocusable(true);
            setVisible(true);
            requestFocusInWindow();
        } else if (next == null && was) {
            setFocusable(false);
            if (!isLit()) setVisible(false);
            if (focusBeforeStrip != null && focusBeforeStrip.isShowing()) focusBeforeStrip.requestFocusInWindow();
            focusBeforeStrip = null;
        }
        if (next != null) setVisible(true);
        repaint();
    }

    public Strip strip() {
        return strip;
    }

    /** The dismissing press, with what was lit at that moment and where it was pressed (overlay coordinates). */
    public void setOnPressed(java.util.function.BiConsumer<Point, List<Lit>> onPressed) {
        this.onPressed = onPressed == null ? (p, l) -> { } : onPressed;
    }

    /**
     * Review PR57 R3: give lit targets the numbers a caller assigned, instead of their order here. A walk's step can
     * leave target 1 unavailable and light only target 2, and the strip and {@code context} say "2", so the callout
     * must too. Targets not named keep their number; later additions continue after the highest.
     */
    public void renumber(java.util.Map<String, Integer> numbers) {
        int highest = 0;
        for (int i = 0; i < lit.size(); i++) {
            Lit l = lit.get(i);
            Integer n = numbers.get(l.target());
            if (n != null) lit.set(i, new Lit(n, l.target(), l.bounds(), l.caption()));
            highest = Math.max(highest, lit.get(i).n());
        }
        nextNumber = highest + 1;
        repaint();
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
        setVisible(strip != null);   // M69: a walk's strip keeps the overlay up, to say why nothing is lit
    }

    private void dismiss() {
        if (!isLit() && strip == null) return;
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
        if (!isLit() && strip == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (isLit()) paintSpotlight(g2, getSize());
            if (strip != null) paintStrip(g2, getSize());
        } finally {
            g2.dispose();
        }
    }

    // ---- M69: the strip's layout, shared by paint and hit-testing so a press lands where the eye sees ----------

    private static final int STRIP_H = 34, STRIP_BTN = 30, STRIP_MARGIN = 16, STRIP_MAX_W = 760;

    /** Where the strip sits: bottom centre, above the frame's bottom edge; grows a line per reason. */
    Rectangle stripBounds(Dimension size) {
        int reasons = strip == null ? 0 : Math.min(3, strip.reasons().size());
        int w = Math.min(STRIP_MAX_W, Math.max(360, size.width - 2 * STRIP_MARGIN));
        int h = STRIP_H + reasons * 16 + (reasons > 0 ? 6 : 0);
        return new Rectangle((size.width - w) / 2, size.height - h - STRIP_MARGIN, w, h);
    }

    private Rectangle stripControl(StripControl c, Dimension size) {
        Rectangle b = stripBounds(size);
        int y = b.y + (STRIP_H - 24) / 2;
        return switch (c) {
            case BACK -> new Rectangle(b.x + 6, y, STRIP_BTN, 24);
            case NEXT -> new Rectangle(b.x + b.width - 2 * STRIP_BTN - 12, y, STRIP_BTN, 24);
            case END -> new Rectangle(b.x + b.width - STRIP_BTN - 6, y, STRIP_BTN, 24);
            case ACTION -> strip == null || strip.action() == null ? null
                    : new Rectangle(b.x + b.width - 2 * STRIP_BTN - 132, y, 114, 24);
        };
    }

    /** Which control is under {@code p}, or null — disabled controls answer null. */
    StripControl stripControlAt(Point p) {
        if (strip == null) return null;
        Dimension size = getSize();
        for (StripControl c : StripControl.values()) {
            Rectangle r = stripControl(c, size);
            if (r == null || !r.contains(p)) continue;
            if (c == StripControl.BACK && !strip.canBack()) return null;
            if (c == StripControl.NEXT && !strip.canNext()) return null;
            return c;
        }
        return null;
    }

    /** A control's centre in overlay coordinates, for tests that press it for real; null when absent. */
    Point stripControlCentre(StripControl c) {
        Rectangle r = strip == null ? null : stripControl(c, getSize());
        return r == null ? null : new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    private void paintStrip(Graphics2D g, Dimension size) {
        boolean dark = ThemeManager.isDark();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont() == null ? g.getFont() : getFont().deriveFont(13f));
        FontMetrics fm = g.getFontMetrics();
        Rectangle b = stripBounds(size);
        Color fill = dark ? new Color(0x1B1F24) : new Color(0xFFFFFF);
        g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 248));
        g.fillRoundRect(b.x, b.y, b.width, b.height, 10, 10);
        g.setColor(UiTheme.accent());
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(b.x, b.y, b.width, b.height, 10, 10);
        Color text = dark ? new Color(0xC9D1D9) : new Color(0x24292F);
        for (StripControl c : StripControl.values()) {
            Rectangle r = stripControl(c, size);
            if (r == null) continue;
            boolean enabled = c != StripControl.BACK && c != StripControl.NEXT
                    || (c == StripControl.BACK ? strip.canBack() : strip.canNext());
            g.setColor(enabled ? UiTheme.accent() : UiTheme.mutedForeground());
            g.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);
            String label = switch (c) {
                case BACK -> "◀";
                case NEXT -> "▶";
                case END -> "✕";
                case ACTION -> strip.action();
            };
            g.setColor(enabled ? text : UiTheme.mutedForeground());
            g.drawString(clip(fm, label, r.width - 8), r.x + (r.width - Math.min(fm.stringWidth(label), r.width - 8)) / 2,
                    r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
        }
        int left = b.x + 6 + STRIP_BTN + 10;
        Rectangle action = stripControl(StripControl.ACTION, size);
        int right = (action != null ? action.x : stripControl(StripControl.NEXT, size).x) - 10;
        String head = strip.title() + " — " + strip.position() + " · " + strip.state();
        g.setColor(text);
        g.drawString(clip(fm, head, right - left), left, b.y + (STRIP_H - fm.getHeight()) / 2 + fm.getAscent());
        g.setColor(UiTheme.mutedForeground());
        int y = b.y + STRIP_H + fm.getAscent() - 4;
        for (int i = 0; i < Math.min(3, strip.reasons().size()); i++) {
            g.drawString(clip(fm, strip.reasons().get(i), b.width - 20), b.x + 10, y);
            y += 16;
        }
    }

    private static String clip(FontMetrics fm, String s, int width) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= width) return s;
        String out = s;
        while (out.length() > 1 && fm.stringWidth(out + "…") > width) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    /**
     * Paint the live spotlights onto another component's image — the {@code screenshot} verb paints the
     * CONTENT PANE (or one panel), which does not include the glass pane, so without this the tutor's own
     * verification shot would show no spotlight. {@code g} is that component's graphics; the overlay is
     * translated so each cut-out lands on the same pixels it covers on screen.
     */
    public void paintOnto(Graphics2D g, Component painted) {
        if ((!isLit() && strip == null) || painted == null) return;
        Point origin = javax.swing.SwingUtilities.convertPoint(this, 0, 0, painted);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(origin.x, origin.y);
            if (isLit()) paintSpotlight(g2, getSize());
            if (strip != null) paintStrip(g2, getSize());   // M69: a screenshot shows the walk's strip too
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
