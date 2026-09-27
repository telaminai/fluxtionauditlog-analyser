package telamin.fluxtion.audit.analyser.analyser.ui.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Surface} that records what was drawn instead of drawing it — the test half of the seam.
 *
 * <p>Assertions are made against {@link Mark}s, so a test can ask the questions that matter and no
 * others: <em>is there a horizontal line reaching the right edge</em>, <em>does any text overlap
 * this rectangle</em>. Neither is answerable from a pixel buffer without a tolerance argument, and
 * neither is answerable at all from a hoisted pure function, because both are relationships BETWEEN
 * drawing operations.
 *
 * <p>Font metrics are declared, not measured: a headless JVM's fonts differ from a developer's, and
 * a layout test that depends on which is installed is a test that fails on someone else's machine
 * for a reason that is not a defect.
 */
public final class RecordingSurface implements Surface {

    /** One drawing operation, with the state in force when it happened. */
    public sealed interface Mark {
        Color colour();

        /** The area this mark covers, for overlap questions. */
        Rectangle bounds();
    }

    public record Line(int x1, int y1, int x2, int y2, Color colour) implements Mark {
        public boolean isHorizontal() { return y1 == y2; }
        public boolean isVertical() { return x1 == x2; }
        @Override public Rectangle bounds() {
            return new Rectangle(Math.min(x1, x2), Math.min(y1, y2),
                    Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    public record Filled(int x, int y, int width, int height, Color colour) implements Mark {
        @Override public Rectangle bounds() { return new Rectangle(x, y, width, height); }
    }

    /** {@code y} is the BASELINE, as Java2D has it; {@link #bounds()} converts to a box. */
    public record Text(String text, int x, int y, int width, int ascent, int descent, Color colour) implements Mark {
        @Override public Rectangle bounds() {
            return new Rectangle(x, y - ascent, width, ascent + descent);
        }
    }

    private final List<Mark> marks = new ArrayList<>();
    private final FontMetrics metrics;
    private Color colour = Color.BLACK;
    private Stroke stroke;
    private Rectangle clip;

    /** Fixed metrics: every character {@code charWidth} wide. Enough for layout, and reproducible. */
    public RecordingSurface(int charWidth, int ascent, int descent) {
        this.metrics = new FixedMetrics(charWidth, ascent, descent);
    }

    public RecordingSurface() {
        this(7, 11, 3);
    }

    public List<Mark> marks() {
        return List.copyOf(marks);
    }

    public List<Line> lines() {
        return marks.stream().filter(Line.class::isInstance).map(Line.class::cast).toList();
    }

    public List<Text> texts() {
        return marks.stream().filter(Text.class::isInstance).map(Text.class::cast).toList();
    }

    public List<Filled> fills() {
        return marks.stream().filter(Filled.class::isInstance).map(Filled.class::cast).toList();
    }

    /** The clip in force, or null — so a test can check a mark was not drawn outside the plot. */
    public Rectangle clip() {
        return clip == null ? null : new Rectangle(clip);
    }

    @Override public void setColor(Color c) { this.colour = c; }
    @Override public Color getColor() { return colour; }
    @Override public void setFont(Font font) { }
    @Override public void setStroke(Stroke s) { this.stroke = s; }
    @Override public Stroke getStroke() { return stroke; }
    @Override public void clip(int x, int y, int w, int h) { this.clip = new Rectangle(x, y, w, h); }
    @Override public void clearClip() { this.clip = null; }
    @Override public FontMetrics fontMetrics() { return metrics; }

    @Override public void drawLine(int x1, int y1, int x2, int y2) {
        marks.add(new Line(x1, y1, x2, y2, colour));
    }

    @Override public void drawRect(int x, int y, int w, int h) {
        marks.add(new Line(x, y, x + w, y, colour));
        marks.add(new Line(x + w, y, x + w, y + h, colour));
        marks.add(new Line(x + w, y + h, x, y + h, colour));
        marks.add(new Line(x, y + h, x, y, colour));
    }

    @Override public void fillRect(int x, int y, int w, int h) {
        marks.add(new Filled(x, y, w, h, colour));
    }

    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        marks.add(new Filled(x, y, w, h, colour));
    }

    @Override public void fillOval(int x, int y, int w, int h) {
        marks.add(new Filled(x, y, w, h, colour));
    }

    @Override public void fillPolygon(int[] xs, int[] ys, int n) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
        }
        marks.add(new Filled(minX, minY, maxX - minX, maxY - minY, colour));
    }

    @Override public void drawString(String text, int x, int y) {
        marks.add(new Text(text, x, y, metrics.stringWidth(text),
                metrics.getAscent(), metrics.getDescent(), colour));
    }

    /**
     * Monospaced metrics with declared numbers. Subclassing {@link FontMetrics} needs a {@link Font},
     * and constructing one is safe headless — it is rasterising that is not.
     */
    private static final class FixedMetrics extends FontMetrics {
        private final int charWidth, ascent, descent;

        FixedMetrics(int charWidth, int ascent, int descent) {
            super(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            this.charWidth = charWidth;
            this.ascent = ascent;
            this.descent = descent;
        }

        @Override public int charWidth(char ch) { return charWidth; }
        @Override public int charWidth(int ch) { return charWidth; }
        @Override public int stringWidth(String s) { return s == null ? 0 : s.length() * charWidth; }
        @Override public int getAscent() { return ascent; }
        @Override public int getDescent() { return descent; }
        @Override public int getHeight() { return ascent + descent + 2; }
    }
}
