package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The click-escalation cycle on the topology view, driven through the SAME entry point the mouse uses
 * (the canvas listener delegates to {@code onNodeClicked}). Headless-safe: the panel is constructed and
 * loaded, never shown; assertions read {@code cursorState()} and the canvas's emphasis set.
 *
 * <p>Exists because step 3 of the cycle (routes) shipped broken — selecting the whole connected
 * component — and only the pure {@code FocusStack} layer had tests, so nothing exercised the cycle a
 * user actually clicks through (owner report 2026-08-17).
 */
class TopologyClickEscalationTest {

    @TempDir
    Path dir;

    /** Diamond: a → b → c, d → c, c → e — d feeds c but lies on no route through a. */
    private TopologyPanel diamondPanel() throws IOException {
        StringBuilder g = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
                + "xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<graph edgedefault=\"undirected\">");
        for (String id : new String[]{"a", "b", "c", "d", "e"}) {
            g.append("<node id=\"").append(id).append("\"><data key=\"vertex_label\">"
                    + "<jGraph:ShapeNode><jGraph:label text=\"id:").append(id)
                    .append("&#10;class:com.acme.").append(id).append("\"/>"
                    + "<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>");
        }
        g.append("<edge id=\"1\" source=\"a\" target=\"b\"/>")
                .append("<edge id=\"2\" source=\"b\" target=\"c\"/>")
                .append("<edge id=\"3\" source=\"d\" target=\"c\"/>")
                .append("<edge id=\"4\" source=\"c\" target=\"e\"/>")
                .append("</graph></graphml>");
        Path file = dir.resolve("diamond.graphml");
        Files.writeString(file, g.toString());

        TopologyPanel panel = new TopologyPanel();
        panel.load(file);
        assertEquals(5, (Integer) panel.cursorState().get("totalNodes"), "the fixture must parse");
        return panel;
    }

    private static void click(TopologyPanel panel, String id) {
        panel.onNodeClicked(id, false);
    }

    private static String scope(TopologyPanel panel) {
        return (String) panel.cursorState().get("scope");
    }

    @Test
    void repeatedClicksWalkTheFourStepsAndWrapBackToNode() throws IOException {
        TopologyPanel panel = diamondPanel();

        click(panel, "a");   // 1. the node
        assertEquals("node", scope(panel));
        assertEquals(Set.of("a"), panel.canvas().emphasis());

        click(panel, "a");   // 2. direct parents and children
        assertEquals("neighbours", scope(panel));
        assertEquals(Set.of("a", "b"), panel.canvas().emphasis(), "a's only neighbour is b");

        click(panel, "a");   // 3. transitive parents and transitive children — the step that broke
        assertEquals("routes", scope(panel));
        assertEquals(Set.of("a", "b", "c", "e"), panel.canvas().emphasis(),
                "routes of a: its descendants and ancestors — NEVER sibling-feeder d, never the whole graph");

        click(panel, "a");   // 4. the whole graph
        assertEquals("all", scope(panel));
        assertEquals(Set.of("a", "b", "c", "d", "e"), panel.canvas().emphasis());

        click(panel, "a");   // 5. back to 1
        assertEquals("node", scope(panel));
        assertEquals(Set.of("a"), panel.canvas().emphasis());
    }

    @Test
    void routesFromAMidGraphNodeReachBothDirectionsButNotSiblingFeeders() throws IOException {
        TopologyPanel panel = diamondPanel();
        click(panel, "e");
        click(panel, "e");
        click(panel, "e");   // routes of e: ALL its ancestors — this diamond legitimately lights up
        assertEquals(Set.of("a", "b", "c", "d", "e"), panel.canvas().emphasis());

        click(panel, "e");   // all
        click(panel, "e");   // wrap to node
        click(panel, "d");   // a DIFFERENT node restarts the cycle at step 1
        assertEquals("node", scope(panel));
        click(panel, "d");
        click(panel, "d");
        assertEquals(Set.of("d", "c", "e"), panel.canvas().emphasis(),
                "routes of d must not climb back up c's other parents");
    }

    @Test
    void clickingADifferentNodeRestartsTheCycleAtThatNode() throws IOException {
        TopologyPanel panel = diamondPanel();
        click(panel, "a");
        click(panel, "a");
        assertEquals("neighbours", scope(panel));

        click(panel, "c");
        assertEquals("node", scope(panel), "at the full graph a new node starts the cycle fresh");
        assertEquals(Set.of("c"), panel.canvas().emphasis());
    }

    @Test
    void additiveClickBuildsTheSelectionWithoutMovingTheScope() throws IOException {
        TopologyPanel panel = diamondPanel();
        click(panel, "a");
        click(panel, "a");                    // neighbours
        panel.onNodeClicked("d", true);       // Cmd/Ctrl-click adds d
        Map<String, Object> state = panel.cursorState();
        assertEquals("neighbours", state.get("scope"), "additive selection leaves the width alone");
        assertEquals(Set.of("a", "b", "d", "c"), panel.canvas().emphasis(),
                "the scope now covers both seeds: a+b and d+c");
    }

    @Test
    void canvasClickClearsDimmingButNotTheScopeCycleState() throws IOException {
        TopologyPanel panel = diamondPanel();
        click(panel, "a");
        click(panel, "a");
        panel.onNodeClicked(null, false);     // empty canvas
        assertEquals(Set.of(), panel.canvas().emphasis(), "dimming cleared");
        assertTrue(((java.util.List<?>) panel.cursorState().get("selected")).isEmpty());
    }
}
