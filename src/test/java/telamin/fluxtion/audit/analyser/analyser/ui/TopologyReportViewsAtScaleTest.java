package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polish H6 — the whole-graph report picture at scale. Against a 309-node graph the estate view was a
 * grey band at 8% zoom (the check the brief asked for; it failed). Above
 * {@link TopologyPanel#REPORT_WHOLE_GRAPH_MAX} nodes the second picture is the cycle's neighbourhood and
 * its note counts what was left out; below it nothing changes. Headless: offscreen painting needs no
 * display.
 */
class TopologyReportViewsAtScaleTest {

    @TempDir
    Path dir;

    /** {@code layers} layers of {@code width} nodes, each node fed by two nodes of the previous layer. */
    private TopologyPanel panel(int layers, int width) throws IOException {
        StringBuilder g = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<graph edgedefault=\"directed\">");
        int e = 0;
        for (int l = 0; l < layers; l++) {
            for (int w = 0; w < width; w++) {
                String id = "l" + l + "n" + w;
                g.append("<node id=\"").append(id).append("\"><data key=\"vertex_label\"><jGraph:ShapeNode>")
                 .append("<jGraph:label text=\"id:").append(id).append("&#10;class:com.acme.big.").append(id).append("\"/>")
                 .append("<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>");
                if (l > 0) {
                    g.append("<edge id=\"e").append(e++).append("\" source=\"l").append(l - 1).append("n").append(w)
                     .append("\" target=\"").append(id).append("\"/>");
                    g.append("<edge id=\"e").append(e++).append("\" source=\"l").append(l - 1).append("n").append((w + 1) % width)
                     .append("\" target=\"").append(id).append("\"/>");
                }
            }
        }
        Path file = dir.resolve("g" + layers + "x" + width + ".graphml");
        Files.writeString(file, g.append("</graph></graphml>").toString());
        TopologyPanel panel = new TopologyPanel();
        panel.load(file);
        assertEquals(layers * width, (Integer) panel.cursorState().get("totalNodes"), "the fixture must parse");
        return panel;
    }

    private static LogRecord cycle(String... ids) {
        StringBuilder sb = new StringBuilder("eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n");
        for (String id : ids) sb.append("    - ").append(id).append(": { v: 1}\n");
        return RecordParser.parse(sb.toString(), 0);
    }

    @Test
    void aLargeProcessorGetsTheNeighbourhood_andTheNoteCountsWhatWasLeftOut() throws IOException {
        TopologyPanel p = panel(10, 12);                                  // 120 nodes
        var views = p.renderCycleViews(cycle("l0n3", "l1n3", "l2n3", "l3n3"), 600, 400);
        assertNotNull(views.trace());
        assertNotNull(views.wholeGraph());
        assertNotNull(views.wholeNote(), "above the threshold the picture is a neighbourhood and says so");
        assertTrue(views.wholeNote().contains("120 nodes"), views.wholeNote());
        assertTrue(views.wholeNote().matches("(?s).*\\+\\d+ nodes not shown.*"), views.wholeNote());
        // 4 path nodes; each layer-l node has two parents and feeds two children → the neighbourhood is
        // small, and the count of what was hidden is the rest
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d+) nodes\\); \\+(\\d+) nodes not shown").matcher(views.wholeNote());
        assertTrue(m.find(), views.wholeNote());
        int shown = Integer.parseInt(m.group(1)), hidden = Integer.parseInt(m.group(2));
        assertEquals(120, shown + hidden, "shown + hidden is the whole estate — nothing double-counted or lost");
        assertTrue(shown < 20 && shown >= 4, "a neighbourhood, not the estate: " + shown);
    }

    @Test
    void aSmallProcessorIsUnchanged_theWholeGraphIsThePicture() throws IOException {
        TopologyPanel p = panel(4, 5);                                    // 20 nodes
        var views = p.renderCycleViews(cycle("l0n1", "l1n1", "l2n1"), 600, 400);
        assertNotNull(views.wholeGraph());
        assertNull(views.wholeNote(), "below the threshold the second picture IS the whole graph, as before");
    }

    @Test
    void aCycleThatTouchesNothingKnownStillGetsTheEstate_notAnEmptyNeighbourhood() throws IOException {
        TopologyPanel p = panel(10, 12);
        var views = p.renderCycleViews(cycle("ghost1", "ghost2"), 600, 400);
        assertNull(views.trace(), "nothing this event reached is in the graph — no trace picture (as before)");
        assertNotNull(views.wholeGraph());
        assertNull(views.wholeNote(), "with no path to neighbour, the estate is shown rather than an empty box");
    }
}
