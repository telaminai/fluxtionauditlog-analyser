package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A custom two-thumb, draggable date-time range control over {@code [min,max]} log-time (spec §8.5).
 * Dragging a thumb updates the shared {@link FilterState} live; the current bounds are shown as UTC
 * labels. Pure Swing, no dependencies.
 */
public final class TimeRangeSlider extends JComponent {

    private static final int PAD = 12;
    private static final int TRACK_Y = 34;
    private static final int THUMB_R = 7;
    private static final int HIST_TOP = 6;
    private static final int HIST_H = 20;

    private static final int HIT = THUMB_R + 4;   // px tolerance for grabbing a thumb

    private long min, max, lo, hi;      // min/max = the visible outer window; lo/hi = the selection
    private long absMin, absMax;        // the log's absolute time range
    private boolean enabled;
    private int dragMode = -1;                    // 0 = lo thumb, 1 = hi thumb, 2 = pan range
    private long anchorVal, anchorLo, anchorHi;   // for range panning
    private int[] histogram;                      // record-density buckets across [min,max]
    private FilterState filter;
    private boolean syncing;
    private Runnable onWindowChanged = () -> { };  // notifies the owner (pan scrollbar) when min/max move
    private final javax.swing.Timer edgeScroll;    // auto-pans the window while a thumb is held at the edge
    private int lastDragX;

    public TimeRangeSlider() {
        setPreferredSize(new Dimension(400, 66));
        setToolTipText("Drag a thumb to bound the log-time; drag the middle to pan the window; double-click to reset");
        edgeScroll = new javax.swing.Timer(40, e -> edgeScrollTick());
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2) resetToFull(); else pick(e.getX());
            }
            @Override public void mouseDragged(MouseEvent e) { drag(e.getX()); }
            @Override public void mouseReleased(MouseEvent e) { dragMode = -1; edgeScroll.stop(); }
            @Override public void mouseMoved(MouseEvent e) { updateCursor(e.getX()); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    /** Called whenever the visible window (min/max) moves, so the owner can resync its pan control. */
    public void setWindowChangeListener(Runnable r) {
        this.onWindowChanged = r == null ? () -> { } : r;
    }

    /** Record-density histogram (buckets across the full range) drawn behind the track. */
    public void setHistogram(int[] histogram) {
        this.histogram = histogram;
        repaint();
    }

    private void resetToFull() {
        if (!enabled) return;
        min = absMin;
        max = absMax;
        lo = absMin;
        hi = absMax;
        onWindowChanged.run();
        repaint();
        publish();
    }

    public void bind(FilterState filter) {
        this.filter = filter;
        filter.addListener(this::syncFromState);
    }

    /** Sets the absolute range; the visible window starts as the whole span. */
    public void setRange(Long minMillis, Long maxMillis) {
        this.enabled = minMillis != null && maxMillis != null && maxMillis > minMillis;
        this.absMin = minMillis == null ? 0 : minMillis;
        this.absMax = maxMillis == null ? 0 : maxMillis;
        this.min = absMin;
        this.max = absMax;
        this.lo = min;
        this.hi = max;
        repaint();
    }

    /**
     * Grow the absolute upper bound as new records arrive (follow/tail mode) without disturbing the
     * selection. If the window was showing "all" it tracks the new end; if the top thumb was pinned to
     * the old end (unbounded), it follows so newly-arrived records stay inside the filter.
     */
    public void extendAbsMax(long newMax) {
        if (!enabled || newMax <= absMax) return;
        boolean full = !isWindowed();
        boolean topFollowing = hi >= absMax;
        absMax = newMax;
        if (full) max = newMax;
        if (topFollowing) hi = newMax;
        repaint();
        onWindowChanged.run();
        publish();
    }

    /** Set the visible outer window length; {@code <=0} or {@code >= full span} means "all". */
    public void setWindowMillis(long windowMillis) {
        if (!enabled) return;
        long absSpan = absMax - absMin;
        if (windowMillis <= 0 || windowMillis >= absSpan) {
            min = absMin;
            max = absMax;
        } else {
            long centre = (lo + hi) / 2;
            long winMin = clampWinStart(centre - windowMillis / 2, windowMillis);
            min = winMin;
            max = winMin + windowMillis;
            lo = Math.max(min, Math.min(lo, max));
            hi = Math.max(min, Math.min(hi, max));
        }
        onWindowChanged.run();
        repaint();
        publish();
    }

    /** Position the visible window across the absolute range ({@code 0..1}); no-op when showing all. */
    public void setWindowStartFraction(double fraction) {
        if (!enabled || !isWindowed()) return;
        long w = max - min;
        long winMin = clampWinStart(absMin + Math.round(fraction * ((absMax - absMin) - w)), w);
        min = winMin;
        max = winMin + w;
        lo = Math.max(min, Math.min(lo, max));
        hi = Math.max(min, Math.min(hi, max));
        repaint();
        publish();
    }

    public boolean isWindowed() {
        return enabled && (max - min) < (absMax - absMin);
    }

    /** Window span as a fraction of the absolute span (1.0 = all). */
    public double windowSpanFraction() {
        long absSpan = absMax - absMin;
        return absSpan <= 0 ? 1.0 : (max - min) / (double) absSpan;
    }

    /** Window start position as a fraction of the pannable range (0..1). */
    public double windowStartFraction() {
        long pannable = (absMax - absMin) - (max - min);
        return pannable <= 0 ? 0.0 : (min - absMin) / (double) pannable;
    }

    private long clampWinStart(long winMin, long w) {
        return Math.max(absMin, Math.min(winMin, absMax - w));
    }

    private void syncFromState() {
        if (filter == null || syncing) return;
        long nlo = filter.fromMillis() == null ? min : filter.fromMillis();
        long nhi = filter.toMillis() == null ? max : filter.toMillis();
        if (nlo != lo || nhi != hi) {
            lo = clamp(nlo);
            hi = clamp(nhi);
            repaint();
        }
    }

    private void pick(int x) {
        if (!enabled) return;
        int loX = valToX(lo), hiX = valToX(hi);
        if (Math.abs(x - loX) <= HIT) {
            dragMode = 0;
            drag(x);
        } else if (Math.abs(x - hiX) <= HIT) {
            dragMode = 1;
            drag(x);
        } else if (x > loX && x < hiX) {
            // grabbed the middle → pan the whole window, keeping its width
            dragMode = 2;
            anchorVal = xToVal(x);
            anchorLo = lo;
            anchorHi = hi;
        } else {
            dragMode = Math.abs(x - loX) <= Math.abs(x - hiX) ? 0 : 1;
            drag(x);
        }
    }

    private void drag(int x) {
        if (!enabled || dragMode < 0) return;
        lastDragX = x;
        if (dragMode == 2) {
            long delta = xToVal(x) - anchorVal;
            long width = anchorHi - anchorLo;
            long nlo = anchorLo + delta;
            if (nlo < min) nlo = min;
            if (nlo + width > max) nlo = max - width;   // clamp shift; width preserved
            lo = nlo;
            hi = nlo + width;
        } else {
            long v = xToVal(x);
            if (dragMode == 0) lo = Math.min(v, hi);
            else hi = Math.max(v, lo);
        }
        // dragging a thumb to the window edge (with room to pan) auto-scrolls the visible window
        boolean atLeft = dragMode == 0 && x <= PAD + HIT && min > absMin;
        boolean atRight = dragMode == 1 && x >= getWidth() - PAD - HIT && max < absMax;
        if (isWindowed() && (atLeft || atRight)) edgeScroll.start();
        else edgeScroll.stop();
        repaint();
        publish();
    }

    /** While a thumb is held at a window edge, pan the window and keep the thumb pinned to that edge. */
    private void edgeScrollTick() {
        if (!enabled || !isWindowed()) { edgeScroll.stop(); return; }
        long step = Math.max(1, (max - min) / 20);   // ~5% of the window per tick
        if (dragMode == 0 && lastDragX <= PAD + HIT && min > absMin) {
            long d = min - Math.max(absMin, min - step);
            min -= d; max -= d; lo = min;
        } else if (dragMode == 1 && lastDragX >= getWidth() - PAD - HIT && max < absMax) {
            long d = Math.min(absMax, max + step) - max;
            min += d; max += d; hi = max;
        } else {
            edgeScroll.stop();
            return;
        }
        onWindowChanged.run();
        repaint();
        publish();
    }

    private void updateCursor(int x) {
        if (!enabled) {
            setCursor(java.awt.Cursor.getDefaultCursor());
            return;
        }
        int loX = valToX(lo), hiX = valToX(hi);
        boolean onThumb = Math.abs(x - loX) <= HIT || Math.abs(x - hiX) <= HIT;
        boolean inSpan = x > loX && x < hiX;
        setCursor(java.awt.Cursor.getPredefinedCursor(
                onThumb ? java.awt.Cursor.W_RESIZE_CURSOR
                        : inSpan ? java.awt.Cursor.MOVE_CURSOR
                        : java.awt.Cursor.DEFAULT_CURSOR));
    }

    private void publish() {
        if (filter == null) return;
        syncing = true;
        try {
            Long from = (lo <= absMin) ? null : lo;   // unbounded only at the absolute edges
            Long to = (hi >= absMax) ? null : hi;
            filter.setTimeRange(from, to);
        } finally {
            syncing = false;
        }
    }

    private int valToX(long v) {
        int w = getWidth() - 2 * PAD;
        if (max <= min || w <= 0) return PAD;
        return PAD + (int) Math.round((v - min) / (double) (max - min) * w);
    }

    private long xToVal(int x) {
        int w = getWidth() - 2 * PAD;
        if (w <= 0) return min;
        double frac = (x - PAD) / (double) w;
        return clamp(min + Math.round(frac * (max - min)));
    }

    private long clamp(long v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean dark = ThemeManager.isDark();
        Color track = dark ? new Color(0x30363D) : new Color(0xC9D1D9);
        Color span = dark ? new Color(0x388BFD) : new Color(0x0969DA);
        Color thumb = dark ? new Color(0x58A6FF) : new Color(0x0550AE);
        Color text = dark ? new Color(0xE6EDF3) : new Color(0x424A53);   // high-contrast labels
        Color hist = dark ? new Color(63, 185, 80, 90) : new Color(45, 164, 78, 70);
        Color histIn = dark ? new Color(63, 185, 80, 200) : new Color(45, 164, 78, 170);
        int w = getWidth();

        if (!enabled) {
            g.setColor(track);
            g.fillRoundRect(PAD, TRACK_Y - 2, w - 2 * PAD, 4, 4, 4);
            g.setColor(text);
            g.drawString("(no timestamps to range)", PAD, TRACK_Y + 20);
            g.dispose();
            return;
        }

        int loX = valToX(lo), hiX = valToX(hi);

        // density histogram, positioned by TIME so it's correct when the window is zoomed;
        // only in-window buckets are drawn, selection buckets brighter
        if (histogram != null && histogram.length > 0) {
            int peak = 1;
            for (int c : histogram) peak = Math.max(peak, c);
            double bucketMs = (absMax - absMin) / (double) histogram.length;
            for (int i = 0; i < histogram.length; i++) {
                long t0 = absMin + (long) (i * bucketMs);
                long t1 = absMin + (long) ((i + 1) * bucketMs);
                if (t1 < min || t0 > max) continue;                 // outside the visible window
                int x0 = valToX(Math.max(t0, min));
                int x1 = valToX(Math.min(t1, max));
                int bwPix = Math.max(1, x1 - x0);
                int bh = (int) Math.round((double) histogram[i] / peak * HIST_H);
                long mid = (t0 + t1) / 2;
                g.setColor(mid >= lo && mid <= hi ? histIn : hist);
                g.fillRect(x0, HIST_TOP + (HIST_H - bh), bwPix, bh);
            }
        }

        g.setColor(track);
        g.fillRoundRect(PAD, TRACK_Y - 2, w - 2 * PAD, 4, 4, 4);
        g.setColor(span);
        g.fillRoundRect(loX, TRACK_Y - 2, Math.max(2, hiX - loX), 4, 4, 4);

        // grip lines in the centre of the span (pan affordance)
        int mid = (loX + hiX) / 2;
        if (hiX - loX > 24) {
            g.setColor(dark ? new Color(0xC9D1D9) : Color.WHITE);
            for (int dx = -3; dx <= 3; dx += 3) g.drawLine(mid + dx, TRACK_Y - 4, mid + dx, TRACK_Y + 4);
        }

        g.setColor(thumb);
        g.fillOval(loX - THUMB_R, TRACK_Y - THUMB_R, 2 * THUMB_R, 2 * THUMB_R);
        g.fillOval(hiX - THUMB_R, TRACK_Y - THUMB_R, 2 * THUMB_R, 2 * THUMB_R);

        // the SELECTION bounds, centred directly under each thumb (the ends of the blue drag bar),
        // rounded to the unit that suits the window's granularity — so it reads as the selected window,
        // not absolute log time
        g.setColor(text);
        drawCentred(g, fmtByWindow(lo), loX, TRACK_Y + 22, w);
        drawCentred(g, fmtByWindow(hi), hiX, TRACK_Y + 22, w);
        String dur = TimeFormat.duration(hi - lo);
        int dw = g.getFontMetrics().stringWidth(dur);
        g.drawString(dur, Math.max(PAD, mid - dw / 2), TRACK_Y - 12);
        g.dispose();
    }

    private static void drawCentred(Graphics2D g, String s, int cx, int y, int w) {
        int sw = g.getFontMetrics().stringWidth(s);
        int x = Math.max(PAD, Math.min(cx - sw / 2, w - PAD - sw));
        g.drawString(s, x, y);
    }

    private static final java.time.format.DateTimeFormatter F_SEC = utcFmt("HH:mm:ss");
    private static final java.time.format.DateTimeFormatter F_MIN = utcFmt("HH:mm");
    private static final java.time.format.DateTimeFormatter F_DAYMIN = utcFmt("MMM d HH:mm");
    private static final java.time.format.DateTimeFormatter F_DATE = utcFmt("yyyy-MM-dd");

    /** Format a bound rounded to the unit that suits the current window span (finer window → finer unit). */
    private String fmtByWindow(long millis) {
        long span = max - min;
        java.time.Instant at = java.time.Instant.ofEpochMilli(millis);
        if (span <= 3 * 60_000L) return F_SEC.format(at);          // ≤ 3 min → seconds
        if (span <= 3 * 3_600_000L) return F_MIN.format(at);       // ≤ 3 h   → minutes
        if (span <= 3 * 86_400_000L) return F_DAYMIN.format(at);   // ≤ 3 d   → day + minutes
        return F_DATE.format(at);                                  // else    → date
    }

    private static java.time.format.DateTimeFormatter utcFmt(String p) {
        return java.time.format.DateTimeFormatter.ofPattern(p).withZone(java.time.ZoneOffset.UTC);
    }
}
