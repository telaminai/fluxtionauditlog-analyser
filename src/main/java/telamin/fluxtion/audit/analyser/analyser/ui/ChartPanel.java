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

    private double vx0 = Double.NaN, vx1, vy0, vy1;   // view bounds (data coords)
    private int plotX, plotY, plotW, plotH;           // last painted plot rect
    private int dragX, dragY;

    public ChartPanel() {
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragX = e.getX(); dragY = e.getY(); }
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
        double gy0 = Double.POSITIVE_INFINITY, gy1 = Double.NEGATIVE_INFINITY;
        for (Series s : series) {
            if (s.size() == 0) continue;
            gx0 = Math.min(gx0, s.minX());
            gx1 = Math.max(gx1, s.maxX());
            gy0 = Math.min(gy0, s.minFiniteY());
            gy1 = Math.max(gy1, s.maxFiniteY());
        }
        if (gx1 < gx0) { vx0 = Double.NaN; repaint(); return; }
        vx0 = gx0; vx1 = gx1;
        vy0 = gy0; vy1 = gy1;
        if (vx1 <= vx0) vx1 = vx0 + 1;
        if (!(vy1 > vy0)) { vy0 -= 1; vy1 += 1; }
        double padY = (vy1 - vy0) * 0.05;
        vy0 -= padY; vy1 += padY;
        repaint();
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

        plotX = L; plotY = T; plotW = w - L - R; plotH = h - T - B;
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
            drawSeries(g, s);
            ci++;
        }
        g.setClip(null);
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
    private int yToPx(double y) { return plotY + plotH - (int) Math.round((y - vy0) / (vy1 - vy0) * plotH); }

    private static String formatY(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.format("%.5g", v);
    }

    /** The series colour at index {@code i} — so a series list can match the plot's colours. */
    public static java.awt.Color paletteColor(int i) {
        return PALETTE[Math.floorMod(i, PALETTE.length)];
    }
}
