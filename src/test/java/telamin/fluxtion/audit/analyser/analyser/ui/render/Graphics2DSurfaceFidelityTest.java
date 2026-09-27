package telamin.fluxtion.audit.analyser.analyser.ui.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.ui.ChartPanel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #53 review: the REAL surface, and the one drawing the seam still leaves outside it. Both run headless — a
 * BufferedImage needs no display.
 */
class Graphics2DSurfaceFidelityTest {

    private static int rgb(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFFFFFF;
    }

    /**
     * {@code Surface.clip} is "clip to a rectangle; clearClip undoes it" — a REPLACE, which is what
     * {@code Graphics2D.setClip} does and what ChartPanel relies on. An implementation using {@code clipRect}
     * (intersect) passed every test while clipping a second region to nothing.
     */
    @Test
    @DisplayName("clip REPLACES the previous clip, as Graphics2D.setClip does")
    void clipReplaces() {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Surface s = new Graphics2DSurface(g);
        s.clip(0, 0, 5, 5);
        s.clip(10, 10, 5, 5);
        s.setColor(Color.RED);
        s.fillRect(0, 0, 20, 20);
        g.dispose();
        assertEquals(0xFF0000, rgb(img, 12, 12), "the second clip is the one in force");
        assertEquals(0x000000, rgb(img, 2, 2), "the first no longer is");
    }

    /**
     * The legend is a Swing overlay in GraphPanel and is not painted through the seam; ChartPanel.paintGlyph is its
     * only bridge to the one glyph implementation. Replacing that bridge with the legend's own drawing left every
     * test green — so the legend and the plot could disagree on a marker's shape with nothing noticing.
     */
    @Test
    @DisplayName("The legend's glyph is pixel-for-pixel the plot's glyph")
    void theLegendDrawsThePlotsGlyph() {
        for (String glyph : List.of("triangleUp", "triangleDown", "square", "diamond", "x", "circle")) {
            BufferedImage legend = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            BufferedImage plot = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            Graphics2D gl = legend.createGraphics(), gp = plot.createGraphics();
            gl.setColor(Color.WHITE);
            gp.setColor(Color.WHITE);
            ChartPanel.paintGlyph(gl, glyph, 10, 10);
            Glyphs.paint(new Graphics2DSurface(gp), glyph, 10, 10);
            gl.dispose();
            gp.dispose();
            for (int x = 0; x < 20; x++) {
                for (int y = 0; y < 20; y++) {
                    assertEquals(rgb(plot, x, y), rgb(legend, x, y), glyph + " differs at " + x + "," + y);
                }
            }
        }
    }
}
