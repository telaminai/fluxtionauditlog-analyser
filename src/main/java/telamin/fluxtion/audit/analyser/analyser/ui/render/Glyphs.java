package telamin.fluxtion.audit.analyser.analyser.ui.render;

/**
 * Marker glyphs, drawn onto a {@link Surface}.
 *
 * <p>One implementation, shared by the plot and the legend (M32.9) — a legend whose swatch is an
 * approximation of the plot's glyph is a second implementation to keep in step, and the whole point
 * of a legend is that it shows the same shape.
 */
public final class Glyphs {

    private Glyphs() {
    }

    /** Half-width of every glyph, so they sit on a common optical size. */
    public static final int R = 4;

    public static void paint(Surface surface, String glyph, int x, int y) {
        switch (glyph) {
            case "triangleUp" -> surface.fillPolygon(new int[]{x - R, x + R, x}, new int[]{y + R, y + R, y - R}, 3);
            case "triangleDown" -> surface.fillPolygon(new int[]{x - R, x + R, x}, new int[]{y - R, y - R, y + R}, 3);
            case "square" -> surface.fillRect(x - R + 1, y - R + 1, 2 * R - 2, 2 * R - 2);
            case "diamond" -> surface.fillPolygon(new int[]{x, x + R, x, x - R}, new int[]{y - R, y, y + R, y}, 4);
            case "x" -> {
                surface.drawLine(x - R + 1, y - R + 1, x + R - 1, y + R - 1);
                surface.drawLine(x - R + 1, y + R - 1, x + R - 1, y - R + 1);
            }
            default -> surface.fillOval(x - R + 1, y - R + 1, 2 * R - 2, 2 * R - 2);
        }
    }
}
