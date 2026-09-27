package telamin.fluxtion.audit.analyser.analyser.ui.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The invariant that makes the {@link Surface} seam worth anything, pinned structurally.
 *
 * <p><b>A painter must never reach past the surface.</b> The moment one asks Java2D a question and
 * branches on the answer, or draws through a {@code Graphics2D} it obtained some other way, the
 * recording surface sees a different picture from the screen — and every assertion made against it
 * becomes theatre. The tests would stay green while the chart was wrong, which is worse than having
 * no tests, because it is the same failure the seam was built to remove.
 *
 * <p>Checked in the bytecode rather than by eye, because a reference on a branch nobody takes is
 * still a reference, and the whole class of mistake is one a reviewer skims past. Same constant-pool
 * check {@code ProjectPanelIsRevealOnlyTest} uses to keep that panel reveal-only.
 */
class PaintersNeverNameGraphics2DTest {

    /** Everything that decides WHAT to draw. {@link Graphics2DSurface} is the adapter and is exempt. */
    private static final List<Class<?>> PAINTERS = List.of(PlotPainter.class, Glyphs.class, PlotGeometry.class);

    private static String bytecodeOf(Class<?> type) throws IOException {
        URL url = type.getResource(type.getSimpleName() + ".class");
        assertNotNull(url, "no class file for " + type);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    @DisplayName("No painter names Graphics2D, Graphics, or a Swing component")
    void paintersOnlyKnowTheSurface() throws IOException {
        for (Class<?> painter : PAINTERS) {
            String bytes = bytecodeOf(painter);
            for (String forbidden : List.of("java/awt/Graphics2D", "java/awt/Graphics",
                    "javax/swing", "java/awt/image")) {
                assertFalse(bytes.contains(forbidden),
                        painter.getSimpleName() + " names " + forbidden + ". A painter that can reach "
                                + "the real graphics context can behave differently on screen from what a "
                                + "RecordingSurface saw, which makes every assertion against it worthless.");
            }
        }
    }

    /**
     * {@link Surface} is the whole vocabulary. If a painter needed something the interface does not
     * offer, the right move is to add it here — where the recorder is obliged to implement it too —
     * and not to widen the parameter type back to {@code Graphics2D}.
     */
    @Test
    @DisplayName("The surface interface does not leak a Graphics2D back to its callers")
    void theInterfaceItselfIsClean() throws IOException {
        String bytes = bytecodeOf(Surface.class);
        assertFalse(bytes.contains("java/awt/Graphics"),
                "Surface exposes a Graphics — a painter could then obtain one and draw around the seam");
    }

    /**
     * The one query a layout decision may make. Stated as a test so that adding a second getter is a
     * deliberate act with a reason, rather than a convenience someone adds while debugging.
     */
    @Test
    @DisplayName("fontMetrics is the only query a layout decision can read")
    void onlyOneQuery() {
        var readable = java.util.Arrays.stream(Surface.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() != void.class)
                .map(java.lang.reflect.Method::getName)
                .sorted()
                .toList();
        assertEquals(List.of("fontMetrics", "getColor", "getStroke"), readable,
                "getColor and getStroke exist so a painter can restore what it borrowed; fontMetrics "
                        + "is the only one a layout decision may branch on, and a test supplies it. A new "
                        + "getter here is a new way for the screen and the recorder to disagree.");
    }
}
