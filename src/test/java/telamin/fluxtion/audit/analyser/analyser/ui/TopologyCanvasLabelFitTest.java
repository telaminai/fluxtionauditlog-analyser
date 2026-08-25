package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polish H5 — the M24 finding: node labels elide to "Category…" at the default box width, which is fine
 * on screen (hover reveals the rest) and wrong in a report PDF (nothing does). The offscreen render now
 * widens the box to fit; on screen nothing changes. Headless: a JPanel needs no display to measure text.
 */
class TopologyCanvasLabelFitTest {

    private static ProcessorTopology graph(String... ids) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>"
                + "<graph edgedefault=\"directed\">");
        for (String id : ids) {
            sb.append("<node id=\"").append(id).append("\"><data key=\"vertex_label\"><jGraph:ShapeNode>")
              .append("<jGraph:label text=\"id:").append(id).append("&#10;class:com.acme.").append(id).append("\"/>")
              .append("<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>");
        }
        return GraphMlParser.parse(sb.append("</graph></graphml>").toString());
    }

    @Test
    void aLongLabelWidensTheBoxForAnExportedPicture() {
        TopologyCanvas canvas = new TopologyCanvas();
        canvas.setTopology(graph("venueMonitorQuoteCalculatorForTheSecondaryHedgeBook"));
        double before = canvas.config().nodeWidth();
        assertEquals(LayeredLayout.Config.defaults().nodeWidth(), before, "on screen: the default box");

        double after = canvas.fitNodeWidthToLabels();

        assertTrue(after > before, "the box grew to fit the label: " + before + " -> " + after);
        assertEquals(after, canvas.config().nodeWidth(), "and the layout config carries it");
    }

    @Test
    void shortLabelsLeaveTheDefaultAlone() {
        TopologyCanvas canvas = new TopologyCanvas();
        canvas.setTopology(graph("clock", "book"));
        assertEquals(LayeredLayout.Config.defaults().nodeWidth(), canvas.fitNodeWidthToLabels(),
                "fitting never SHRINKS the box — short labels keep the default so pictures stay comparable");
    }

    @Test
    void theWidthIsTheWidestLabelPlusTheInset() {
        TopologyCanvas canvas = new TopologyCanvas();
        String longest = "aRatherLongInstanceIdentifierUsedOnlyHere";
        canvas.setTopology(graph("x", longest));
        double width = canvas.fitNodeWidthToLabels();
        var fm = canvas.getFontMetrics(canvas.getFont().deriveFont(canvas.labelSize()));
        assertEquals(fm.stringWidth(longest) + 16, width, 0.001, "8px inset each side, as drawClipped assumes");
    }
}
