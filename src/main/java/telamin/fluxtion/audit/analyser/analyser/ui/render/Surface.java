package telamin.fluxtion.audit.analyser.analyser.ui.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Stroke;

/**
 * What a chart draws ONTO — the seam between deciding what to draw and rasterising it.
 *
 * <p>Swing painting is untested by rule 4, and the rule is right: a paint method cannot be run
 * headless and asserting on pixels is a test nobody maintains. The response so far has been to hoist
 * individual decisions out into pure functions and pin those. That works for arithmetic and fails for
 * everything else, because <b>the interesting chart defects are RELATIONSHIPS BETWEEN DRAWING
 * OPERATIONS</b>, and a hoisted function cannot see another function's output.
 *
 * <p>The defect that prompted this: the x-axis labels were drawn at the component's bottom edge,
 * inside the explanation footer, and the footer <em>fills its background</em> — so a chart with one
 * pinned note lost its time axis. Both operations were individually correct. Nothing short of
 * comparing the emitted text's bounds against the emitted rect could have caught it, and no pure
 * function has both.
 *
 * <p>So: the painter talks to this, the application hands it {@link Graphics2DSurface}, and a test
 * hands it a recorder and asserts on the primitives. One implementation of the drawing, two
 * surfaces — the thing asserted is the thing drawn.
 *
 * <h2>The invariant that makes it worth anything</h2>
 *
 * <p><b>No layout decision may consult the surface.</b> The moment a painter asks Java2D a question
 * and branches on the answer, the recorder sees a different picture from the screen and the test is
 * theatre. The one legitimate query is text measurement, which is why {@link #fontMetrics()} is here
 * and is the only getter that returns anything a decision may read — a test supplies deterministic
 * metrics. The existing {@code ChartAnnotationLayout} already takes {@code FontMetrics} as a
 * parameter for exactly this reason, so the codebase had reached the same conclusion.
 */
public interface Surface {

    // ---- state ----------------------------------------------------------------------------------

    void setColor(Color color);

    /** The current colour, so a painter can restore what it borrowed. */
    Color getColor();

    void setFont(Font font);

    void setStroke(Stroke stroke);

    Stroke getStroke();

    /** Clip to a rectangle; {@link #clearClip()} undoes it. */
    void clip(int x, int y, int width, int height);

    void clearClip();

    /**
     * Metrics for the current font. The ONE query a layout decision may make — see the class note.
     * A recording surface returns metrics a test declares, so wrapped text and centred labels land
     * in the same place every run.
     */
    FontMetrics fontMetrics();

    // ---- marks ----------------------------------------------------------------------------------

    void drawLine(int x1, int y1, int x2, int y2);

    void drawRect(int x, int y, int width, int height);

    void fillRect(int x, int y, int width, int height);

    void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight);

    void fillOval(int x, int y, int width, int height);

    void fillPolygon(int[] xs, int[] ys, int count);

    /** {@code x} is the left edge, {@code y} the BASELINE — Java2D's convention, kept deliberately. */
    void drawString(String text, int x, int y);
}
