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

    private static java.util.List<?> selected(TopologyPanel panel) {
        return (java.util.List<?>) panel.cursorState().get("selected");
    }

    @Test
    @DisplayName("R5: saveFocusAs with no focus to save refuses the WHOLE call — the selection it carried is not applied")
    void aRefusedSaveLeavesTheSelection() {
        // witness: topologyProblem without the saveFocusAs precondition
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var reply = executor(panel, null).render("topology", Map.of("select", "rootNode", "saveFocusAs", "demo"));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertTrue(String.valueOf(reply.toMap()).contains("nothing to save"), reply.toMap().toString());
        assertTrue(selected(panel).isEmpty(), "R5: a refused call changed the selection: " + selected(panel));
    }

    @Test
    @DisplayName("R5: popping to the full graph in the same call as saveFocusAs is refused whole, and the focus stays")
    void aPopThatLeavesNothingToSaveIsRefusedWhole() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        panel.bindNamedFocuses(() -> saved, () -> { });
        var ex = executor(panel, null);
        assertTrue(ex.render("topology", Map.of("select", "rootNode", "scope", "node", "focus", true)).ok());
        String crumbs = String.valueOf(panel.cursorState());
        var reply = ex.render("topology", Map.of("pop", "all", "saveFocusAs", "demo"));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertEquals(crumbs, String.valueOf(panel.cursorState()), "R5: the focus was not popped by a refused call");
        assertTrue(saved.isEmpty());
    }

    @Test
    @DisplayName("R5: focus:true then saveFocusAs in ONE call still saves — the request establishes what it names")
    void focusThenSaveInOneCallStillSaves() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        panel.bindNamedFocuses(() -> saved, () -> { });
        var reply = executor(panel, null).render("topology",
                Map.of("select", "rootNode", "scope", "node", "focus", true, "saveFocusAs", "demo"));
        assertTrue(reply.ok(), reply.toMap().toString());
        assertEquals(java.util.List.of("demo"), saved.stream().map(f -> f.name()).toList());
    }

    @Test
    @DisplayName("R5: a blank saveFocusAs name is refused before anything is applied")
    void aBlankNameIsRefusedWhole() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        var reply = executor(panel, null).render("topology", Map.of("select", "rootNode", "saveFocusAs", "  "));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertTrue(selected(panel).isEmpty(), "R5: " + selected(panel));
    }

    // ---- re-review N1 (2026-09-26): the save is judged on the state the request's OWN transitions leave ----------------

    /** A panel with a one-node focus already applied — the state N1's refused call destroyed. */
    private static TopologyPanel focused(java.util.List<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec> saved) {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        panel.bindNamedFocuses(() -> saved, () -> { });
        var ex = executor(panel, null);
        assertTrue(ex.render("topology", Map.of("select", "rootNode", "scope", "node", "focus", true)).ok());
        assertEquals(1, panel.cursorState().get("contextDepth"), "precondition: one focus context applied");
        return panel;
    }

    @Test
    @DisplayName("N1: {showAll, saveFocusAs} refuses and leaves the existing focus exactly as it was")
    void aRefusedSaveAfterShowAllLeavesTheExistingFocus() {
        // witness: the trial run omitting showAll's transition (review-n1-showall-in-preparation)
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        var panel = focused(saved);
        var before = new java.util.LinkedHashMap<>(panel.cursorState());
        var reply = executor(panel, null).render("topology", Map.of("showAll", true, "saveFocusAs", "review-focus"));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertEquals(before, panel.cursorState(), "N1: a refused call leaves the prior topology state unchanged");
        assertTrue(saved.isEmpty(), "and saves nothing");
    }

    @Test
    @DisplayName("N1: showAll with select and saveFocusAs, but no focus, refuses without changing anything")
    void showAllAndSelectWithoutFocusRefusesWhole() {
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        var panel = focused(saved);
        var before = new java.util.LinkedHashMap<>(panel.cursorState());
        var reply = executor(panel, null).render("topology",
                Map.of("showAll", true, "select", "riskCheck", "saveFocusAs", "x"));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertEquals(before, panel.cursorState(), "N1: unchanged");
    }

    @Test
    @DisplayName("N1: showAll, select, focus and saveFocusAs in one call succeed — the request establishes what it saves")
    void showAllThenSelectFocusAndSaveInOneCallSaves() {
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        var panel = focused(saved);
        var reply = executor(panel, null).render("topology", Map.of("showAll", true, "select", "riskCheck",
                "scope", "node", "focus", true, "saveFocusAs", "risk"));
        assertTrue(reply.ok(), reply.toMap().toString());
        assertEquals(java.util.List.of("risk"), saved.stream().map(f -> f.name()).toList());
        assertEquals(1, panel.cursorState().get("contextDepth"), "the new focus replaced the old one");
    }

    @Test
    @DisplayName("N1 preserved: pop:1 at depth 1 refuses; pop:1 at depth 2 saves; pop:all refuses — each whole")
    void popsAreJudgedOnTheirRealEffect() {
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        var panel = focused(saved);
        var ex = executor(panel, null);
        var before = new java.util.LinkedHashMap<>(panel.cursorState());
        assertFalse(ex.render("topology", Map.of("pop", 1, "saveFocusAs", "x")).ok());
        assertEquals(before, panel.cursorState(), "pop:1 at depth 1 would leave the full graph: refused, unchanged");
        assertFalse(ex.render("topology", Map.of("pop", "all", "saveFocusAs", "x")).ok());
        assertEquals(before, panel.cursorState());
        // a second level, then pop one: depth 1 remains, so the save succeeds (built from a wider first level)
        var wide = new TopologyPanel();
        wide.load(GRAPH);
        wide.bindNamedFocuses(() -> saved, () -> { });
        var wex = executor(wide, null);
        assertTrue(wex.render("topology", Map.of("select", "rootNode", "scope", "all", "focus", true)).ok());
        assertTrue(wex.render("topology", Map.of("select", "rootNode", "scope", "node", "focus", true)).ok());
        assertEquals(2, wide.cursorState().get("contextDepth"), "precondition: two levels");
        var popped = wex.render("topology", Map.of("pop", 1, "saveFocusAs", "outer"));
        assertTrue(popped.ok(), popped.toMap().toString());
        assertEquals(1, wide.cursorState().get("contextDepth"));
        assertTrue(saved.stream().anyMatch(f -> f.name().equals("outer")));
    }

    @Test
    @DisplayName("N1 preserved: a no-op focus, scope and routeBound keep an existing focus saveable")
    void aNoOpFocusScopeAndRouteBoundStillSave() {
        var saved = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>();
        var panel = focused(saved);
        var reply = executor(panel, null).render("topology",
                Map.of("focus", true, "scope", "neighbours", "routeBound", false, "saveFocusAs", "kept"));
        assertTrue(reply.ok(), reply.toMap().toString());
        assertEquals(java.util.List.of("kept"), saved.stream().map(f -> f.name()).toList());
    }
}
