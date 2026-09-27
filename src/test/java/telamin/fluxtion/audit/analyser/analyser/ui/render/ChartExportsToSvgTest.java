package telamin.fluxtion.audit.analyser.analyser.ui.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import telamin.fluxtion.audit.analyser.analyser.ui.ChartPanel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A chart drawn for a browser, by the same paint path that draws it on screen.
 *
 * <p>The point is not that SVG is nicer than PNG. It is that an exported chart is the one nobody
 * checks against the screen — it goes into a ticket and is read by someone who was not there — so it
 * must not be able to diverge from what the screen showed. Here it cannot, because
 * {@code ChartPanel.renderTo} is the only renderer and SVG is a consumer of it.
 *
 * <p>Runs headless: no display, no rasterising, no font installed on the machine. That is the seam
 * doing its job — the only thing the exporter needs from the environment is text measurement, and
 * that is declared.
 */
class ChartExportsToSvgTest {

    private static ChartPanel chart() {
        ChartPanel panel = new ChartPanel();
        Series mid = new Series("priceListener.mid");
        long t = 1_767_258_000_090L;
        double[] values = {101.2, 101.2, 99.4, 96.1, 96.1, 54.0};
        for (int i = 0; i < values.length; i++) mid.add(t + i * 30_000L, values[i]);
        panel.setSeries(List.of(mid));
        panel.setStyle(ChartPanel.Style.STEP);
        panel.setGuides(List.of(new GraphSpec.GuideSpec(95.0, "a threshold rule", false)));
        panel.setNotes(new ChartNotes(
                "Exported straight to SVG by the same paint path that draws the screen.",
                List.of(new ChartNotes.Note(t + 150_000L, "the step this chart is about", null))));
        // pin past the last sample, so the closing hold has somewhere to be drawn
        panel.setViewWindow(t, t + 270_000L);
        return panel;
    }

    private static String svg() {
        return chart().toSvg(900, 420, SvgSurface.Metrics.monospace11());
    }

    @Test
    @DisplayName("It produces a whole SVG document, headless, with no display")
    void itProducesADocument() {
        String svg = svg();

        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""), svg.substring(0, 80));
        assertTrue(svg.trim().endsWith("</svg>"));
        assertTrue(svg.contains("width=\"900\"") && svg.contains("height=\"420\""));
        assertTrue(svg.contains("viewBox=\"0 0 900 420\""), "scalable, or it is just a picture again");
    }

    @Test
    @DisplayName("The marks are there: the series, its closing hold, the axis labels and the guide")
    void theChartIsActuallyDrawn() {
        String svg = svg();

        assertTrue(svg.contains("<line "), "no series line");
        assertTrue(svg.contains("<text "), "no labels");
        assertTrue(svg.contains("a threshold rule"), "the guide's label is missing");
        assertTrue(svg.contains("stroke-dasharray"),
                "the guide and the note rule are DASHED; drawn solid they read as data");
        assertTrue(svg.contains("the step this chart is about"),
                "the pinned note travels with the chart — that is most of why an exported chart is worth having");
    }

    @Test
    @DisplayName("Text is text — selectable and searchable, not pixels")
    void textIsText() {
        String svg = svg();

        assertTrue(svg.contains("2026-01-01"), "the axis labels should be readable strings: " + svg.lines()
                .filter(l -> l.contains("<text")).limit(4).toList());
        assertTrue(svg.contains("font-family="), "the font is DECLARED, so the browser measures what we measured");
    }

    @Test
    @DisplayName("Nothing is escaped wrongly, so the document is well formed")
    void itParses() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(svg().getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertEquals("svg", doc.getDocumentElement().getNodeName());
    }

    /**
     * Writes the document out for a human to open. Not an assertion — the tests above are — but an
     * exported chart is a thing whose value is visual, and a reviewer should be able to look at one.
     */
    @Test
    @DisplayName("…and one is written to target/ so it can be opened")
    void writeOneToLookAt() throws Exception {
        Path out = Path.of("target", "chart-export.svg");
        Files.createDirectories(out.getParent());
        Files.writeString(out, svg());

        assertTrue(Files.size(out) > 500, "wrote " + Files.size(out) + " bytes to " + out.toAbsolutePath());
    }
}
