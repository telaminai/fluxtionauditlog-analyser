package telamin.fluxtion.audit.analyser.analyser.ui.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * The real surface: {@link Surface} straight onto a {@link Graphics2D}.
 *
 * <p>Deliberately nothing but delegation — no decisions, no state of its own, no convenience. Every
 * line of judgement that leaks in here is a line the recording surface does not have and therefore a
 * line no test can see.
 */
public final class Graphics2DSurface implements Surface {

    private final Graphics2D g;

    public Graphics2DSurface(Graphics2D g) {
        this.g = g;
    }

    @Override public void setColor(Color color) { g.setColor(color); }
    @Override public Color getColor() { return g.getColor(); }
    @Override public void setFont(Font font) { g.setFont(font); }
    @Override public void setStroke(Stroke stroke) { g.setStroke(stroke); }
    @Override public Stroke getStroke() { return g.getStroke(); }
    @Override public void clip(int x, int y, int width, int height) { g.setClip(x, y, width, height); }
    @Override public void clearClip() { g.setClip(null); }
    @Override public FontMetrics fontMetrics() { return g.getFontMetrics(); }

    @Override public void drawLine(int x1, int y1, int x2, int y2) { g.drawLine(x1, y1, x2, y2); }
    @Override public void drawRect(int x, int y, int w, int h) { g.drawRect(x, y, w, h); }
    @Override public void fillRect(int x, int y, int w, int h) { g.fillRect(x, y, w, h); }
    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { g.fillRoundRect(x, y, w, h, aw, ah); }
    @Override public void fillOval(int x, int y, int w, int h) { g.fillOval(x, y, w, h); }
    @Override public void fillPolygon(int[] xs, int[] ys, int n) { g.fillPolygon(xs, ys, n); }
    @Override public void drawString(String text, int x, int y) { g.drawString(text, x, y); }
}
