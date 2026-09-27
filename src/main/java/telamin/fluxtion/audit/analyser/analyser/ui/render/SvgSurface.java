package telamin.fluxtion.audit.analyser.analyser.ui.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Stroke;
import java.awt.BasicStroke;

/**
 * A {@link Surface} that writes SVG — the same chart, drawn for a browser instead of a window.
 *
 * <p>This exists because of where an exported chart ends up. A PNG in a ticket is a picture of
 * evidence: it cannot be zoomed without blurring, its text cannot be selected or searched, and
 * nothing in it can be inspected. An SVG is the marks themselves, and it opens in anything.
 *
 * <p>It is emphatically <b>not</b> a second renderer. {@code ChartPanel.renderTo} is the only one,
 * and this is a consumer of it. That matters more here than anywhere: a chart exported for somebody
 * else to read is precisely the one nobody checks against the screen, so it must not be able to
 * diverge from it.
 *
 * <h2>Text, and why the caller declares the metrics</h2>
 *
 * <p>Layout needs text widths, and the browser — not this JVM — will do the rasterising. So the font
 * is <b>declared on both sides</b>: this surface emits a {@code font-family} and {@code font-size},
 * and the caller supplies the {@link Metrics} that match them. Measure with one font and render with
 * another and every centred label drifts.
 *
 * <p>That is the same constraint the seam already imposes — {@link Surface#fontMetrics()} is its one
 * query — which is not a coincidence. The single place layout touches the rasteriser is the single
 * thing standing between a chart and a browser.
 */
public final class SvgSurface implements Surface {

    /**
     * Declared text measurement for the declared font.
     *
     * @param family    a CSS font stack, emitted verbatim
     * @param sizePx    font size in pixels
     * @param charWidth advance width of one character — monospace is assumed, deliberately: a
     *                  proportional font cannot be measured without the font itself, and a chart
     *                  whose labels drift by a few pixels per glyph is worse than one in monospace
     * @param ascent    baseline to top
     * @param descent   baseline to bottom
     */
    public record Metrics(String family, int sizePx, int charWidth, int ascent, int descent) {

        /** A monospace stack every major browser resolves, with its 11px advances. */
        public static Metrics monospace11() {
            return new Metrics("ui-monospace, SFMono-Regular, Menlo, Consolas, monospace", 11, 7, 11, 3);
        }
    }

    private final int width, height;
    private final Metrics metrics;
    private final FontMetrics awtMetrics;
    private final StringBuilder body = new StringBuilder();

    private Color colour = Color.BLACK;
    private Stroke stroke;
    private String clipId;
    private int clipCount;
    private final StringBuilder defs = new StringBuilder();

    public SvgSurface(int width, int height, Metrics metrics) {
        this.width = width;
        this.height = height;
        this.metrics = metrics;
        this.awtMetrics = new DeclaredMetrics(metrics);
    }

    /** The finished document. */
    public String document() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height
                + "\" viewBox=\"0 0 " + width + " " + height + "\" "
                + "font-family=\"" + escape(metrics.family()) + "\" font-size=\"" + metrics.sizePx() + "px\">\n"
                + (defs.isEmpty() ? "" : "<defs>\n" + defs + "</defs>\n")
                + body
                + "</svg>\n";
    }

    // ---- state ----------------------------------------------------------------------------------

    @Override public void setColor(Color color) { this.colour = color; }
    @Override public Color getColor() { return colour; }
    @Override public void setFont(Font font) { /* the document declares one font; see the class note */ }
    @Override public void setStroke(Stroke s) { this.stroke = s; }
    @Override public Stroke getStroke() { return stroke; }
    @Override public FontMetrics fontMetrics() { return awtMetrics; }

    @Override
    public void clip(int x, int y, int w, int h) {
        clipId = "clip" + (++clipCount);
        defs.append("  <clipPath id=\"").append(clipId).append("\">")
                .append("<rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(w).append("\" height=\"").append(h).append("\"/>")
                .append("</clipPath>\n");
    }

    @Override public void clearClip() { clipId = null; }

    // ---- marks ----------------------------------------------------------------------------------

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        body.append("  <line x1=\"").append(x1).append("\" y1=\"").append(y1)
                .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
                .append("\" stroke=\"").append(css(colour)).append('"')
                .append(opacity()).append(strokeAttrs()).append(clipAttr()).append("/>\n");
    }

    @Override
    public void drawRect(int x, int y, int w, int h) {
        body.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(w).append("\" height=\"").append(h)
                .append("\" fill=\"none\" stroke=\"").append(css(colour)).append('"')
                .append(opacity()).append(clipAttr()).append("/>\n");
    }

    @Override
    public void fillRect(int x, int y, int w, int h) {
        body.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(w).append("\" height=\"").append(h)
                .append("\" fill=\"").append(css(colour)).append('"')
                .append(opacity()).append(clipAttr()).append("/>\n");
    }

    @Override
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        body.append("  <rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(w).append("\" height=\"").append(h)
                .append("\" rx=\"").append(aw / 2).append("\" ry=\"").append(ah / 2)
                .append("\" fill=\"").append(css(colour)).append('"')
                .append(opacity()).append(clipAttr()).append("/>\n");
    }

    @Override
    public void fillOval(int x, int y, int w, int h) {
        body.append("  <ellipse cx=\"").append(x + w / 2.0).append("\" cy=\"").append(y + h / 2.0)
                .append("\" rx=\"").append(w / 2.0).append("\" ry=\"").append(h / 2.0)
                .append("\" fill=\"").append(css(colour)).append('"')
                .append(opacity()).append(clipAttr()).append("/>\n");
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int count) {
        body.append("  <polygon points=\"");
        for (int i = 0; i < count; i++) {
            if (i > 0) body.append(' ');
            body.append(xs[i]).append(',').append(ys[i]);
        }
        body.append("\" fill=\"").append(css(colour)).append('"')
                .append(opacity()).append(clipAttr()).append("/>\n");
    }

    @Override
    public void drawString(String text, int x, int y) {
        // y is the BASELINE in both Java2D and SVG, so it carries across unchanged
        body.append("  <text x=\"").append(x).append("\" y=\"").append(y)
                .append("\" fill=\"").append(css(colour)).append('"')
                .append(opacity()).append(clipAttr()).append(">")
                .append(escape(text)).append("</text>\n");
    }

    // ---- helpers --------------------------------------------------------------------------------

    private String clipAttr() {
        return clipId == null ? "" : " clip-path=\"url(#" + clipId + ")\"";
    }

    /** Alpha rides separately in SVG; Java2D carries it in the colour, so it is split out here. */
    private String opacity() {
        int a = colour.getAlpha();
        return a == 255 ? "" : " opacity=\"" + String.format(java.util.Locale.ROOT, "%.3f", a / 255.0) + "\"";
    }

    /** Dashes matter: a guide rule and a note's rule are dashed, and a solid one reads as data. */
    private String strokeAttrs() {
        if (!(stroke instanceof BasicStroke bs)) return "";
        StringBuilder out = new StringBuilder();
        if (bs.getLineWidth() != 1f) {
            out.append(" stroke-width=\"").append(String.format(java.util.Locale.ROOT, "%.2f", bs.getLineWidth())).append('"');
        }
        float[] dash = bs.getDashArray();
        if (dash != null && dash.length > 0) {
            out.append(" stroke-dasharray=\"");
            for (int i = 0; i < dash.length; i++) {
                if (i > 0) out.append(',');
                out.append(String.format(java.util.Locale.ROOT, "%.0f", dash[i]));
            }
            out.append('"');
        }
        return out.toString();
    }

    private static String css(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** {@link FontMetrics} over the declared numbers — the browser will do the real measuring. */
    private static final class DeclaredMetrics extends FontMetrics {
        private final Metrics m;

        DeclaredMetrics(Metrics m) {
            super(new Font(Font.MONOSPACED, Font.PLAIN, m.sizePx()));
            this.m = m;
        }

        @Override public int charWidth(char ch) { return m.charWidth(); }
        @Override public int charWidth(int ch) { return m.charWidth(); }
        @Override public int stringWidth(String s) { return s == null ? 0 : s.length() * m.charWidth(); }
        @Override public int getAscent() { return m.ascent(); }
        @Override public int getDescent() { return m.descent(); }
        @Override public int getHeight() { return m.ascent() + m.descent() + 2; }
    }
}
