package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.4 (D-E3, D-E4, acceptance 5): a topology call is validated whole before any field is applied, and its record
 * parameter refuses naming what is missing. (That it SELECTS the record from a fresh load is asserted end to end by
 * the verifier's scenario 14, because it needs the frame's table-to-topology wiring.)
 */
class TopologyWholeOrRefusedTest {

    private static final Path GRAPH =
            Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");

    private static ActionExecutor executor(TopologyPanel panel, HeapLogStore store) {
        var filter = new FilterState();
        var tabs = new GraphTabs();
        tabs.bind(store, filter);
        var table = new LogTablePanel();
        if (store != null) table.setModel(new LogTableModel(store));
        var ex = new ActionExecutor(() -> store, () -> filter, tabs, table, (r, n, f, k) -> { });
        ex.bind(panel, null);
        return ex;
    }

    @Test
    @DisplayName("one invalid field refuses the call, and the valid fields before it are NOT applied")
    void aRefusalIsWhole() {
        // witness: doTopology without the topologyProblem pre-check (select applied, then the bad scope refused)
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var ex = executor(panel, null);
        var reply = ex.render("topology", Map.of("select", "rootNode", "scope", "sideways"));
        assertFalse(reply.ok());
        assertTrue(String.valueOf(reply.toMap()).contains("nothing was changed"), reply.toMap().toString());
        assertTrue(panel.cursorState().get("selected") instanceof java.util.List<?> l && l.isEmpty(),
                "the selection before the bad scope was not applied: " + panel.cursorState().get("selected"));
    }

    @Test
    @DisplayName("an orientation the verb does not know is refused — it used to become top-down silently")
    void anUnknownOrientationIsRefused() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var reply = executor(panel, null).render("topology", Map.of("orientation", "diagonal"));
        assertFalse(reply.ok(), reply.toMap().toString());
    }

    @Test
    @DisplayName("recordIndex refuses naming what is missing: no log, or a record the log does not have")
    void aRecordIndexSaysWhatIsMissing() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var noLog = executor(panel, null).render("topology", Map.of("recordIndex", 2));
        assertFalse(noLog.ok());
        assertTrue(String.valueOf(noLog.toMap()).contains("needs an open log"), noLog.toMap().toString());
        var store = new HeapLogStore("eventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - rootNode: {v: 1}\n---\n");
        var outOfRange = executor(panel, store).render("topology", Map.of("recordIndex", 7));
        assertFalse(outOfRange.ok());
        assertTrue(String.valueOf(outOfRange.toMap()).contains("not a record of this log"), outOfRange.toMap().toString());
    }
}
