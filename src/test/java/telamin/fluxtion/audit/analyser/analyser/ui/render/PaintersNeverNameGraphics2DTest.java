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

    /**
     * PR #53 review. The class-file check above covers PlotPainter, Glyphs and PlotGeometry — three small classes —
     * while most of the chart's painting (markers, bands, guides, notes, pins, the record marker, the explanation,
     * the decimated series) lives in ChartPanel, which legitimately names Graphics2D and so cannot be checked that
     * way. This pins it at the source: Graphics2D / Graphics may appear in ChartPanel only at its four boundaries —
     * the import, toImage, paintComponent, and the legend's paintGlyph bridge. A paint method that took a Graphics2D
     * again would be drawing around the seam, and nothing else would notice.
     */
    @Test
    @DisplayName("ChartPanel names Graphics2D only at its four boundaries")
    void chartPanelNamesGraphicsOnlyAtItsBoundaries() throws IOException {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ChartPanel.java"));
        // comments blanked to spaces, so every offset still points at the same place in the source
        String code = java.util.regex.Pattern.compile("(?s)/\\*.*?\\*/").matcher(src)
                .replaceAll(r -> " ".repeat(r.group().length()));
        code = java.util.regex.Pattern.compile("//[^\n]*").matcher(code)
                .replaceAll(r -> " ".repeat(r.group().length()));
        List<int[]> allowed = new java.util.ArrayList<>();
        for (String signature : List.of("public java.awt.image.BufferedImage toImage(int w, int h)",
                "protected void paintComponent(Graphics g0)", "public static void paintGlyph(Graphics2D g")) {
            int at = code.indexOf(signature);
            assertTrue(at >= 0, "boundary method not found: " + signature + " — update this guard with it");
            int open = code.indexOf('{', at), depth = 0, end = open;
            for (int k = open; k < code.length(); k++) {
                if (code.charAt(k) == '{') depth++;
                else if (code.charAt(k) == '}' && --depth == 0) { end = k; break; }
            }
            allowed.add(new int[]{at, end});
        }
        var m = java.util.regex.Pattern.compile("\\bGraphics(2D)?\\b").matcher(code);
        List<String> outside = new java.util.ArrayList<>();
        while (m.find()) {
            int pos = m.start();
            int lineStart = code.lastIndexOf('\n', pos) + 1;
            String line = code.substring(lineStart, code.indexOf('\n', pos)).trim();
            if (line.startsWith("import ")) continue;
            if (allowed.stream().anyMatch(r -> pos >= r[0] && pos <= r[1])) continue;
            outside.add(line);
        }
        assertEquals(List.of(), outside, "ChartPanel names a Graphics outside its boundaries — paint onto a Surface");
    }
}
