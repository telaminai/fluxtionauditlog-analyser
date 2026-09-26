package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.FocusSpec;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review's gap table, M68.2 (set 13, B): a report's topology section for a saved focus printed NOT RENDERED
 * because no per-focus off-screen render existed. It is drawn now, off-screen, like the cycle views; a focus that no
 * longer resolves still says so.
 */
class TopologyReportFocusTest {

    private static final Path GRAPH =
            Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");

    @Test
    @DisplayName("B: a saved focus renders off-screen at the page size asked for")
    void aSavedFocusRenders() throws Exception {
        // witness: renderFocusForReport returning null
        var out = new AtomicReference<TopologyPanel.FocusPicture>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new TopologyPanel();
            panel.load(GRAPH);
            var focuses = new ArrayList<FocusSpec>(List.of(new FocusSpec("path", "", List.of("rootNode", "riskCheck"))));
            panel.bindNamedFocuses(() -> focuses, () -> { });
            out.set(panel.renderFocusForReport("path", 1200, 800));
        });
        assertNotNull(out.get(), "B: the focus is drawn");
        assertEquals(1200, out.get().image().getWidth());
        assertEquals(800, out.get().image().getHeight());
        assertTrue(out.get().caption().contains("2"), "the caption counts the nodes drawn: " + out.get().caption());
    }

    @Test
    @DisplayName("B: a focus that is not defined, or declares nothing in this graph, is not drawn")
    void anUnresolvedFocusIsNotDrawn() throws Exception {
        var unknown = new AtomicReference<TopologyPanel.FocusPicture>();
        var foreign = new AtomicReference<TopologyPanel.FocusPicture>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new TopologyPanel();
            panel.load(GRAPH);
            var focuses = new ArrayList<FocusSpec>(List.of(new FocusSpec("elsewhere", "", List.of("notInThisGraph"))));
            panel.bindNamedFocuses(() -> focuses, () -> { });
            unknown.set(panel.renderFocusForReport("missing", 1200, 800));
            foreign.set(panel.renderFocusForReport("elsewhere", 1200, 800));
        });
        assertNull(unknown.get());
        assertNull(foreign.get());
    }
}
