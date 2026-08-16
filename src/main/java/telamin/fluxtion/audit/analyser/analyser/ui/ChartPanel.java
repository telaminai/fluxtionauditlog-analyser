package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.graph.Series;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free multi-series chart (spec §8.7). X = logTime, Y = value; {@code NaN} values
 * break the line (gaps). Default style is a <b>stairs/step</b> plot (values hold until the next
 * update — the natural reading of event-log state); line and points styles are also offered.
 *
 * <p>Interactive: mouse-wheel zoom uses the precise (high-resolution) rotation so it behaves on
 * trackpads / Magic Mouse (Shift = X only, Ctrl = Y only); drag pans; double-click fits. Zoom
 * buttons on the toolbar are a reliable alternative to the wheel.
 */
public final class ChartPanel extends JPanel {

    public enum Style { STEP, LINE, POINTS }

    private static final Color[] PALETTE = {
            new Color(0x0969DA), new Color(0xCF222E), new Color(0x1A7F37),
            new Color(0x8250DF), new Color(0xBC4C00), new Color(0x1B7C83)
    };
    private static final int L = 56, R = 16, T = 14, B = 44;   // legend is a Swing overlay now, not an in-margin paint

    private final List<Series> series = new ArrayList<>();
    private java.util.function.LongConsumer onPlotClick = t -> { };   // click in the plot → time at cursor
    private Style style = Style.STEP;

    private double vx0 = Double.NaN, vx1, vy0, vy1;   // view bounds (data coords) — the LEFT axis
    /** A transient "the cycle under discussion is here" pointer; never persisted (see paintRecordMarker). */
    private Double markerAt;
    private String markerLabel;
    /** Right-axis view bounds. Only meaningful when {@link #axes} puts something on the right. */
    private double ry0, ry1;
    private telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment axes =
            new telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment();
    private telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes notes =
            telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.EMPTY;
    private int plotX, plotY, plotW, plotH;           // last painted plot rect
    private int dragX, dragY;

    public ChartPanel() {
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragX = e.getX();
                dragY = e.getY();
                maybeNoteMenu(e);   // popup fires on press on some platforms, release on others
            }
            @Override public void mouseDragged(MouseEvent e) { pan(e.getX() - dragX, e.getY() - dragY); dragX = e.getX(); dragY = e.getY(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { resetView(); return; }
                // a click in the plot area → the time under the cursor (jump the table to the nearest record)
                if (!Double.isNaN(vx0) && e.getX() >= plotX && e.getX() <= plotX + plotW
                        && e.getY() >= plotY && e.getY() <= plotY + plotH) {
                    onPlotClick.accept((long) pxToX(e.getX()));
                }
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { zoom(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeNoteMenu(e); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    public void setStyle(Style style) {
        this.style = style;
        repaint();
    }


    /** Called with the UTC epoch-millis under the cursor when the plot (not a legend) is clicked. */
    public void setOnPlotClick(java.util.function.LongConsumer onPlotClick) {
        this.onPlotClick = onPlotClick == null ? t -> { } : onPlotClick;
    }

    public void setSeries(List<Series> s) {
        series.clear();
        series.addAll(s);
        resetView();
    }

    public void clear() {
        series.clear();
        vx0 = Double.NaN;
        repaint();
    }

    /** Renders the current chart to an image (for PNG/JPEG export) at the current on-screen size. */
    public java.awt.image.BufferedImage toImage() {
        int w = Math.max(getWidth(), 640), h = Math.max(getHeight(), 360);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        paintComponent(g);
        g.dispose();
        return img;
    }

    /** Which series are measured against the right-hand scale. */
    public void setAxes(telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment axes) {
        this.axes = axes == null
                ? new telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment() : axes;
        resetView();
    }

    public telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment axes() {
        return axes;
    }

    /** The explanation block and the pinned notes drawn over the plot. */
    public void setNotes(telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes notes) {
        this.notes = notes == null
                ? telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.EMPTY : notes;
        repaint();
    }

    public telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes notes() {
        return notes;
    }

    /** True when a right-hand scale is being drawn, so the caller can widen the right margin. */
    private int rightMargin() {
        return axes.hasRightAxis() ? 56 : R;
    }

    /** The [minX, maxX] across all series, or null if empty — used to pin an unbounded filter to real data. */
    public long[] dataBounds() {
        long gx0 = Long.MAX_VALUE, gx1 = Long.MIN_VALUE;
        for (Series s : series) {
            if (s.size() == 0) continue;
            gx0 = Math.min(gx0, s.minX());
            gx1 = Math.max(gx1, s.maxX());
        }
        return gx1 < gx0 ? null : new long[]{gx0, gx1};
    }

    /** Reset the view to fit all data (autoscale). */
    public void resetView() {
        long gx0 = Long.MAX_VALUE, gx1 = Long.MIN_VALUE;
        // each axis is scaled to ITS OWN series: sharing one range is what reduces a small series to a
        // flat smear when it is plotted beside a large one
        double ly0 = Double.POSITIVE_INFINITY, ly1 = Double.NEGATIVE_INFINITY;
        double gr0 = Double.POSITIVE_INFINITY, gr1 = Double.NEGATIVE_INFINITY;
        for (Series s : series) {
            if (s.size() == 0) continue;
            gx0 = Math.min(gx0, s.minX());
            gx1 = Math.max(gx1, s.maxX());
            if (axes.isRight(s.label())) {
                gr0 = Math.min(gr0, s.minFiniteY());
                gr1 = Math.max(gr1, s.maxFiniteY());
            } else {
                ly0 = Math.min(ly0, s.minFiniteY());
                ly1 = Math.max(ly1, s.maxFiniteY());
            }
        }
        if (gx1 < gx0) { vx0 = Double.NaN; repaint(); return; }
        vx0 = gx0; vx1 = gx1;
        if (vx1 <= vx0) vx1 = vx0 + 1;
        double[] left = padded(ly0, ly1);
        vy0 = left[0]; vy1 = left[1];
        double[] right = padded(gr0, gr1);
        ry0 = right[0]; ry1 = right[1];
        repaint();
    }

    /** A usable range from possibly-degenerate bounds: empty or flat still has to map to pixels. */
    private static double[] padded(double lo, double hi) {
        if (Double.isInfinite(lo) || Double.isInfinite(hi)) {
            return new double[]{0, 1};
        }
        if (!(hi > lo)) {
            lo -= 1;
            hi += 1;
        }
        double pad = (hi - lo) * 0.05;
        return new double[]{lo - pad, hi + pad};
    }

    /**
     * Window the X axis to {@code [from,to]} (nulls = full data extent) and fit Y to the visible points,
     * <b>without re-extracting</b>. The graph calls this on a time-range change so dragging the slider only
     * pans/zooms the cached series (cheap) instead of re-parsing the log.
     */
    public void setViewWindow(Long from, Long to) {
        if (series.isEmpty()) { vx0 = Double.NaN; repaint(); return; }
        long gx0 = Long.MAX_VALUE, gx1 = Long.MIN_VALUE;
        for (Series s : series) {
            if (s.size() == 0) continue;
            gx0 = Math.min(gx0, s.minX());
            gx1 = Math.max(gx1, s.maxX());
        }
        if (gx1 < gx0) { vx0 = Double.NaN; repaint(); return; }
        double lo = from != null ? from : gx0;
        double hi = to != null ? to : gx1;
        if (hi <= lo) hi = lo + 1;
        double gy0 = Double.POSITIVE_INFINITY, gy1 = Double.NEGATIVE_INFINITY;
        for (Series s : series) {
            for (int i = 0; i < s.size(); i++) {
                double y = s.y(i);
                if (Double.isFinite(y) && s.x(i) >= lo && s.x(i) <= hi) {
                    gy0 = Math.min(gy0, y);
                    gy1 = Math.max(gy1, y);
                }
            }
        }
        if (gy0 == Double.POSITIVE_INFINITY) { gy0 = 0; gy1 = 1; }   // no points in window
        else if (!(gy1 > gy0)) { gy0 -= 1; gy1 += 1; }
        double padY = (gy1 - gy0) * 0.05;
        vx0 = lo; vx1 = hi;
        vy0 = gy0 - padY; vy1 = gy1 + padY;
        repaint();
    }

    // ---- zoom buttons (reliable alternative to the wheel) ----
    public void zoomIn() { zoomAroundCentre(1 / 1.25); }
    public void zoomOut() { zoomAroundCentre(1.25); }

    private void zoomAroundCentre(double factor) {
        if (Double.isNaN(vx0)) return;
        double cx = (vx0 + vx1) / 2, cy = (vy0 + vy1) / 2;
        vx0 = cx + (vx0 - cx) * factor; vx1 = cx + (vx1 - cx) * factor;
        vy0 = cy + (vy0 - cy) * factor; vy1 = cy + (vy1 - cy) * factor;
        repaint();
    }

    private void zoom(MouseWheelEvent e) {
        if (Double.isNaN(vx0) || plotW <= 0 || plotH <= 0) return;
        double rot = e.getPreciseWheelRotation();     // high-res on trackpad/Magic Mouse
        if (rot == 0) return;
        double factor = Math.pow(1.1, rot);
        factor = Math.max(0.5, Math.min(2.0, factor));  // clamp so momentum can't jump wildly
        boolean zx = !e.isControlDown();   // Ctrl = Y only
        boolean zy = !e.isShiftDown();     // Shift = X only
        if (zx) { double cx = pxToX(e.getX()); vx0 = cx + (vx0 - cx) * factor; vx1 = cx + (vx1 - cx) * factor; }
        if (zy) { double cy = pyToY(e.getY()); vy0 = cy + (vy0 - cy) * factor; vy1 = cy + (vy1 - cy) * factor; }
        repaint();
    }

    private void pan(int dxPix, int dyPix) {
        if (Double.isNaN(vx0) || plotW <= 0 || plotH <= 0) return;
        vx0 -= dxPix / (double) plotW * (vx1 - vx0);
        vx1 -= dxPix / (double) plotW * (vx1 - vx0);
        vy0 += dyPix / (double) plotH * (vy1 - vy0);
        vy1 += dyPix / (double) plotH * (vy1 - vy0);
        repaint();
    }

    private double pxToX(int px) { return vx0 + (px - plotX) / (double) plotW * (vx1 - vx0); }
    private double pyToY(int py) { return vy0 + (plotY + plotH - py) / (double) plotH * (vy1 - vy0); }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (Double.isNaN(vx0) || plotW <= 0) return null;
        if (e.getX() < plotX || e.getX() > plotX + plotW) return null;
        return TimeFormat.utc((long) pxToX(e.getX())) + "  y=" + formatY(pyToY(e.getY()));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean dark = ThemeManager.isDark();
        // margins take the surrounding panel colour; the plot rect is a subtly distinct "canvas card"
        Color panelBg = javax.swing.UIManager.getColor("Panel.background");
        if (panelBg == null) panelBg = dark ? new Color(0x1E1E1E) : new Color(0xF2F2F2);
        Color canvas = dark ? new Color(0x0D1117) : new Color(0xF6F8FA);
        Color axis = dark ? new Color(0x3D444D) : new Color(0xC2CAD3);
        Color grid = dark ? new Color(0x2C333C) : new Color(0xD5DBE3);
        Color text = dark ? new Color(0x9DA7B3) : new Color(0x57606A);
        int w = getWidth(), h = getHeight();
        g.setFont(getFont().deriveFont(11f));
        g.setColor(panelBg);
        g.fillRect(0, 0, w, h);

        plotX = L; plotY = T; plotW = w - L - rightMargin(); plotH = h - T - B;
        if (plotW > 10 && plotH > 10) {
            g.setColor(canvas);
            g.fillRect(plotX, plotY, plotW, plotH);   // the plot card, distinct from the panel margins
            g.setColor(axis);
            g.drawRect(plotX, plotY, plotW, plotH);    // thin frame delineates the card
        }
        if (plotW <= 10 || plotH <= 10 || Double.isNaN(vx0)) {
            g.setColor(text);
            g.drawString("No numeric series selected — pick a nodeLogs key and Add.", L, h / 2);
            g.dispose();
            return;
        }

        // subtle gridlines with evenly-spaced Y value labels
        int yDivs = 4;
        for (int i = 0; i <= yDivs; i++) {
            int gy = plotY + plotH - (int) Math.round((double) i / yDivs * plotH);
            g.setColor(grid);
            g.drawLine(plotX, gy, plotX + plotW, gy);
            g.setColor(text);
            String lbl = formatY(vy0 + (double) i / yDivs * (vy1 - vy0));
            g.drawString(lbl, plotX - 6 - g.getFontMetrics().stringWidth(lbl), gy + 4);
            if (axes.hasRightAxis()) {
                // the second scale, read against the same gridlines — that shared grid is what lets the
                // eye compare two series whose numbers have nothing in common
                g.drawString(formatY(ry0 + (double) i / yDivs * (ry1 - ry0)), plotX + plotW + 6, gy + 4);
            }
        }
        int xDivs = 5;
        for (int i = 1; i < xDivs; i++) {
            int gx = plotX + (int) Math.round((double) i / xDivs * plotW);
            g.setColor(grid);
            g.drawLine(gx, plotY, gx, plotY + plotH);
        }
        g.setColor(axis);
        g.drawLine(plotX, plotY, plotX, plotY + plotH);
        g.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH);
        g.setColor(text);
        g.drawString(TimeFormat.utc((long) vx0), plotX, h - 6);
        String hiLabel = TimeFormat.utc((long) vx1);
        g.drawString(hiLabel, plotX + plotW - g.getFontMetrics().stringWidth(hiLabel), h - 6);

        g.setClip(plotX, plotY, plotW, plotH);
        int ci = 0;
        for (Series s : series) {
            g.setColor(PALETTE[ci % PALETTE.length]);
            g.setStroke(new BasicStroke(1.5f));
            drawingRight = axes.isRight(s.label());
            drawSeries(g, s);
            ci++;
        }
        drawingRight = false;
        paintNotes(g, dark);
        g.setClip(null);
        paintRecordMarker(g, dark);
        paintExplanation(g, dark);
        // the legend is a Swing overlay component (GraphPanel), not painted here — so labels are readable,
        // untruncated, and support right-click actions
        g.dispose();
    }

    private void drawSeries(Graphics2D g, Series s) {
        // decimate dense series to ~one column per pixel so paint stays O(plotW), not O(points) — this is
        // what keeps the EDT smooth while dragging the time window on a big log
        if (s.size() > 3 * plotW) {
            drawDecimated(g, s);
            return;
        }
        int prevX = 0, prevY = 0;
        boolean have = false;
        boolean markers = s.size() <= 600;   // dots only when sparse enough to be legible/cheap
        for (int i = 0; i < s.size(); i++) {
            double v = s.y(i);
            if (Double.isNaN(v) || Double.isInfinite(v)) { have = false; continue; }  // gap
            int px = xToPx(s.x(i));
            int py = yToPx(v);
            if (have && style != Style.POINTS) {
                if (style == Style.STEP) {
                    g.drawLine(prevX, prevY, px, prevY);   // hold value…
                    g.drawLine(px, prevY, px, py);         // …then step
                } else {
                    g.drawLine(prevX, prevY, px, py);
                }
            }
            if (markers || style == Style.POINTS) g.fillOval(px - 2, py - 2, 4, 4);
            prevX = px; prevY = py; have = true;
        }
    }

    /**
     * Downsample to one representative point per pixel column, then render with the <b>same</b>
     * STEP/LINE/POINTS logic as the exact path — so decimation stays O(plot width) without changing how a
     * style looks. A faint min/max envelope behind it preserves spikes lost between pixels.
     */
    private void drawDecimated(Graphics2D g, Series s) {
        int cols = Math.max(1, plotW);
        double[] rep = new double[cols], mn = new double[cols], mx = new double[cols];
        boolean[] has = new boolean[cols];
        java.util.Arrays.fill(mn, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(mx, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < s.size(); i++) {
            double v = s.y(i);
            if (!Double.isFinite(v)) continue;
            int col = xToPx(s.x(i)) - plotX;
            if (col < 0 || col >= cols) continue;                 // outside the visible window
            rep[col] = v;                                         // last sample in the column ≈ its value
            has[col] = true;
            if (v < mn[col]) mn[col] = v;
            if (v > mx[col]) mx[col] = v;
        }

        // faint density envelope (min..max per column) behind the styled line
        Color prev = g.getColor();
        g.setColor(new Color(prev.getRed(), prev.getGreen(), prev.getBlue(), 60));
        for (int col = 0; col < cols; col++) {
            if (!has[col] || mn[col] == mx[col]) continue;
            int x = plotX + col;
            g.drawLine(x, yToPx(mx[col]), x, yToPx(mn[col]));
        }
        g.setColor(prev);

        // the styled line/steps/points through the per-column representative value
        int prevX = 0, prevY = 0;
        boolean have = false;
        for (int col = 0; col < cols; col++) {
            if (!has[col]) continue;
            int px = plotX + col, py = yToPx(rep[col]);
            if (have && style != Style.POINTS) {
                if (style == Style.STEP) {
                    g.drawLine(prevX, prevY, px, prevY);   // hold…
                    g.drawLine(px, prevY, px, py);         // …then step
                } else {
                    g.drawLine(prevX, prevY, px, py);
                }
            }
            if (style == Style.POINTS) g.fillOval(px - 2, py - 2, 4, 4);
            prevX = px; prevY = py; have = true;
        }
    }

    private int xToPx(long x) { return plotX + (int) Math.round((x - vx0) / (vx1 - vx0) * plotW); }
    /**
     * Notes pinned to moments: a dashed rule at the time, a numbered marker, and the text.
     *
     * <p>Numbered rather than labelled in place, because a note long enough to be worth writing is long
     * enough to cover the data it is about. The number sits on the plot; the words sit under it, in the
     * same order.
     */
    private void paintNotes(Graphics2D g, boolean dark) {
        if (notes.notes().isEmpty() || Double.isNaN(vx0)) {
            return;
        }
        Color rule = dark ? new Color(0x6E7681) : new Color(0x8C959F);
        Color pin = dark ? new Color(0xE3B341) : new Color(0x9A6700);
        var columns = notes.byColumn((long) vx0, (long) vx1, plotW);
        int index = 0;
        for (var entry : columns.entrySet()) {
            int px = plotX + entry.getKey();
            g.setColor(rule);
            g.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT,
                    java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{3f, 4f}, 0f));
            g.drawLine(px, plotY, px, plotY + plotH);
            // notes sharing a column stack downwards rather than overprinting each other
            int stack = 0;
            for (var ignored : entry.getValue()) {
                index++;
                int cy = plotY + 12 + stack * 16;
                g.setColor(pin);
                g.fillOval(px - 7, cy - 7, 14, 14);
                g.setColor(dark ? Color.BLACK : Color.WHITE);
                String n = String.valueOf(index);
                g.drawString(n, px - g.getFontMetrics().stringWidth(n) / 2, cy + 4);
                stack++;
            }
        }
    }

    /**
     * A vertical rule at the moment being diagnosed, labelled with the record.
     *
     * <p>Deliberately <b>not</b> a {@link telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.Note}.
     * A note is something a person wrote and it is saved with the graph; this is a pointer that says
     * "the cycle under discussion is here", and it belongs to whatever is currently being looked at. Held
     * as its own transient field so it can never leak into a saved graph's annotations.
     *
     * <p>It earns its place in a report: a plot pasted beside a finding shows a trend, but nothing on it
     * says which point of that trend the finding is about, and the reader is left to infer it from the
     * timestamp in the header. Marking it turns two artefacts into one argument.
     */
    private void paintRecordMarker(Graphics2D g, boolean dark) {
        if (markerAt == null || Double.isNaN(vx0) || markerAt < vx0 || markerAt > vx1) {
            return;
        }
        int px = xToPx((long) (double) markerAt);
        Color accent = dark ? new Color(0xE3A008) : new Color(0xB45309);
        g.setColor(accent);
        g.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_BUTT,
                java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
        g.drawLine(px, plotY, px, plotY + plotH);
        g.setStroke(new java.awt.BasicStroke(1f));

        if (markerLabel == null || markerLabel.isBlank()) {
            return;
        }
        java.awt.FontMetrics fm = g.getFontMetrics();
        int pad = 4;
        int w = fm.stringWidth(markerLabel) + pad * 2;
        int h = fm.getHeight() + 2;
        // flip the label to the left of the rule when it would otherwise run off the plot
        int bx = px + 4 + w > plotX + plotW ? px - 4 - w : px + 4;
        int by = plotY + 4;
        g.setColor(accent);
        g.fillRoundRect(bx, by, w, h, 4, 4);
        g.setColor(Color.WHITE);
        g.drawString(markerLabel, bx + pad, by + fm.getAscent() + 1);
    }

    /**
     * Point the chart at a moment — typically the record being diagnosed. {@code null} clears it.
     * Transient: never saved, never exported as an annotation.
     */
    public void setRecordMarker(Long atMillis, String label) {
        this.markerAt = atMillis == null ? null : atMillis.doubleValue();
        this.markerLabel = label;
        repaint();
    }

    /**
     * The explanation block, bottom-left inside the plot: what this chart is for, in the author's words.
     *
     * <p>Drawn on the chart rather than beside it so it survives an exported PNG — a rationale that lives
     * only in the app is lost the moment the picture is shared, which is exactly when it is needed.
     */
    private void paintExplanation(Graphics2D g, boolean dark) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (!notes.explanation().isBlank()) {
            lines.addAll(java.util.Arrays.asList(notes.explanation().split("\n")));
        }
        int n = 0;
        for (var note : notes.between((long) vx0, (long) vx1)) {
            lines.add(++n + ". " + note.text()
                    + (note.series() == null ? "" : "  [" + note.series() + "]"));
        }
        if (lines.isEmpty() || plotW < 120) {
            return;
        }
        java.awt.FontMetrics fm = g.getFontMetrics();
        int pad = 8;
        int lineH = fm.getHeight();
        int boxW = 0;
        for (String line : lines) {
            boxW = Math.max(boxW, fm.stringWidth(line));
        }
        boxW = Math.min(boxW + pad * 2, plotW - 16);
        int boxH = lines.size() * lineH + pad * 2;
        int bx = plotX + 8;
        int by = plotY + plotH - boxH - 8;

        Color fill = dark ? new Color(0x1B1F24) : new Color(0xFFFFFF);
        g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 235));
        g.fillRoundRect(bx, by, boxW, boxH, 8, 8);
        g.setColor(dark ? new Color(0x3D444D) : new Color(0xC2CAD3));
        g.drawRoundRect(bx, by, boxW, boxH, 8, 8);
        g.setColor(dark ? new Color(0xC9D1D9) : new Color(0x24292F));
        int ty = by + pad + fm.getAscent();
        for (String line : lines) {
            g.drawString(clip(fm, line, boxW - pad * 2), bx + pad, ty);
            ty += lineH;
        }
    }

    private static String clip(java.awt.FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && fm.stringWidth(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    /**
     * Right-click inside the plot: pin a note at the time under the cursor, or clear the pins.
     *
     * <p>The gesture matters as much as the feature. Reading a chart is when you notice the thing worth
     * writing down, and an annotation you have to leave the chart to add is one you mostly do not add.
     * The moment is taken from the cursor's x, so the note lands where you were looking.
     */
    private void maybeNoteMenu(MouseEvent e) {
        if (!e.isPopupTrigger() || Double.isNaN(vx0)) {
            return;
        }
        if (e.getX() < plotX || e.getX() > plotX + plotW || e.getY() < plotY || e.getY() > plotY + plotH) {
            return;
        }
        long at = (long) pxToX(e.getX());
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        javax.swing.JMenuItem add = new javax.swing.JMenuItem(
                "Add note at " + TimeFormat.utc(at) + "…");
        add.addActionListener(a -> {
            String text = javax.swing.JOptionPane.showInputDialog(this,
                    "Note at " + TimeFormat.utc(at) + " UTC:", "Add note",
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (text != null && !text.isBlank()) {
                setNotes(notes.plus(new telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.Note(
                        at, text.strip(), nearestSeriesLabel(e.getX(), e.getY()))));
                notesChanged.run();
            }
        });
        menu.add(add);

        javax.swing.JMenuItem explain = new javax.swing.JMenuItem(
                notes.explanation().isBlank() ? "Add explanation…" : "Edit explanation…");
        explain.addActionListener(a -> {
            javax.swing.JTextArea area = new javax.swing.JTextArea(notes.explanation(), 6, 44);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            int ok = javax.swing.JOptionPane.showConfirmDialog(this,
                    new javax.swing.JScrollPane(area), "What does this chart show, and why does it matter?",
                    javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (ok == javax.swing.JOptionPane.OK_OPTION) {
                setNotes(notes.withExplanation(area.getText()));
                notesChanged.run();
            }
        });
        menu.add(explain);

        if (!notes.notes().isEmpty()) {
            menu.addSeparator();
            javax.swing.JMenuItem clear = new javax.swing.JMenuItem(
                    "Clear " + notes.notes().size() + " note(s)");
            clear.addActionListener(a -> {
                setNotes(notes.withoutNotes());   // keeps the explanation: they are different statements
                notesChanged.run();
            });
            menu.add(clear);
        }
        menu.show(this, e.getX(), e.getY());
    }

    /**
     * The series whose line passes closest to the click, so a note picks up what it is about without the
     * user saying. Returns null when nothing is near — a note about the chart rather than a series is a
     * real case, and guessing would put the wrong label on it.
     */
    private String nearestSeriesLabel(int px, int py) {
        String best = null;
        int bestDistance = 18;                    // pixels; beyond this the click was not "on" a line
        for (Series s : series) {
            if (s.size() == 0) continue;
            drawingRight = axes.isRight(s.label());
            for (int i = 0; i < s.size(); i++) {
                double v = s.y(i);
                if (Double.isNaN(v) || Double.isInfinite(v)) continue;
                // near in BOTH axes: matching on y alone picks a series that happens to cross that
                // height at some other time entirely, and labels the note with the wrong thing
                if (Math.abs(xToPx(s.x(i)) - px) > 6) continue;
                int d = Math.abs(yToPx(v) - py);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = s.label();
                }
            }
        }
        drawingRight = false;
        return best;
    }

    /** Told when the user edits notes here, so the graph can persist them. */
    public void onNotesChanged(Runnable listener) {
        this.notesChanged = listener == null ? () -> { } : listener;
    }

    private Runnable notesChanged = () -> { };

    /** The axis the series currently being drawn belongs to — set once per series, read by yToPx. */
    private boolean drawingRight;

    private int yToPx(double y) {
        double lo = drawingRight ? ry0 : vy0;
        double hi = drawingRight ? ry1 : vy1;
        return plotY + plotH - (int) Math.round((y - lo) / (hi - lo) * plotH);
    }

    private static String formatY(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.format("%.5g", v);
    }

    /** The series colour at index {@code i} — so a series list can match the plot's colours. */
    public static java.awt.Color paletteColor(int i) {
        return PALETTE[Math.floorMod(i, PALETTE.length)];
    }
}
