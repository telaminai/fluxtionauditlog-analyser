package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;

/**
 * Small hand-drawn vector toolbar icons — no image assets, no dependency. Each icon paints in the host
 * component's foreground colour, so it adapts to the FlatLaf theme and dims automatically when the
 * button is disabled. 16&nbsp;px, antialiased, 1.5&nbsp;px round strokes. Matches the app's
 * custom-painting style (see {@code ChartPanel}).
 */
final class ToolIcons {
    private ToolIcons() { }

    private static final int SZ = 16;

    /** A drawing lambda: paint into a translated 16×16 box with the pen colour already set. */
    private interface Draw {
        void paint(Graphics2D g);
    }

    private static Icon of(Draw draw) {
        return new Icon() {
            @Override public int getIconWidth() { return SZ; }
            @Override public int getIconHeight() { return SZ; }

            @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    Color col = c.getForeground();
                    if (c instanceof AbstractButton b && !b.isEnabled()) {
                        col = new Color(col.getRed(), col.getGreen(), col.getBlue(), 110);
                    }
                    g.setColor(col);
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    draw.paint(g);
                } finally {
                    g.dispose();
                }
            }
        };
    }

    /** Open — a folder with a tab. */
    static Icon open() {
        return of(g -> {
            GeneralPath p = new GeneralPath();
            p.moveTo(2, 5.5); p.lineTo(6, 5.5); p.lineTo(7.2, 4); p.lineTo(2, 4); p.closePath();
            g.draw(p);
            g.draw(new java.awt.geom.RoundRectangle2D.Float(2, 5.5f, 12, 8, 2, 2));
        });
    }

    /** Open from S3 — a storage bucket (cylinder). */
    static Icon bucket() {
        return of(g -> {
            g.draw(new Ellipse2D.Float(3, 2.5f, 10, 3));
            g.draw(new Line2D.Float(3, 4, 3, 12));
            g.draw(new Line2D.Float(13, 4, 13, 12));
            GeneralPath bottom = new GeneralPath();
            bottom.moveTo(3, 12); bottom.curveTo(3, 13.6, 13, 13.6, 13, 12);
            g.draw(bottom);
        });
    }

    /** Flag — a filled pennant on a pole. */
    static Icon flag() {
        return of(g -> {
            g.draw(new Line2D.Float(4, 2, 4, 14));
            GeneralPath f = new GeneralPath();
            f.moveTo(4, 3); f.lineTo(13, 5); f.lineTo(4, 7.5); f.closePath();
            g.fill(f);
        });
    }

    /** Filter / flagged-only — a funnel. */
    static Icon funnel() {
        return of(g -> {
            GeneralPath p = new GeneralPath();
            p.moveTo(2.5, 3); p.lineTo(13.5, 3); p.lineTo(9.2, 8); p.lineTo(9.2, 13); p.lineTo(6.8, 13);
            p.lineTo(6.8, 8); p.closePath();
            g.draw(p);
        });
    }

    /** Anomaly — a warning triangle with a bang. */
    static Icon warning() {
        return of(g -> {
            GeneralPath t = new GeneralPath();
            t.moveTo(8, 2.5); t.lineTo(14.5, 13.5); t.lineTo(1.5, 13.5); t.closePath();
            g.draw(t);
            g.draw(new Line2D.Float(8, 6.5f, 8, 10));
            g.fill(new Ellipse2D.Float(7.25f, 11, 1.5f, 1.5f));
        });
    }

    /** Explain — a speech bubble. */
    static Icon chat() {
        return of(g -> {
            g.draw(new java.awt.geom.RoundRectangle2D.Float(2, 3, 12, 8, 3, 3));
            GeneralPath tail = new GeneralPath();
            tail.moveTo(5, 11); tail.lineTo(5, 14); tail.lineTo(8, 11); g.draw(tail);
            g.fill(new Ellipse2D.Float(5, 6.4f, 1.2f, 1.2f));
            g.fill(new Ellipse2D.Float(7.4f, 6.4f, 1.2f, 1.2f));
            g.fill(new Ellipse2D.Float(9.8f, 6.4f, 1.2f, 1.2f));
        });
    }

    /** Export — a tray with a down arrow. */
    static Icon download() {
        return of(g -> {
            g.draw(new Line2D.Float(8, 2.5f, 8, 9.5f));
            GeneralPath head = new GeneralPath();
            head.moveTo(5, 7); head.lineTo(8, 10); head.lineTo(11, 7); g.draw(head);
            GeneralPath tray = new GeneralPath();
            tray.moveTo(3, 11); tray.lineTo(3, 13.5); tray.lineTo(13, 13.5); tray.lineTo(13, 11); g.draw(tray);
        });
    }

    /** Follow / tail — a play triangle. */
    static Icon play() {
        return of(g -> {
            GeneralPath p = new GeneralPath();
            p.moveTo(5, 3.5); p.lineTo(5, 12.5); p.lineTo(13, 8); p.closePath();
            g.fill(p);
        });
    }
}
