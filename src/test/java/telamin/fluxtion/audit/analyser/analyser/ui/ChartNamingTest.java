package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.6 (spec-evidence-integrity D-E5, acceptance 6; owner 2026-09-24, Q2: REFUSE at creation). A chart name no
 * spotlight address can carry is refused where it is given; one already saved stays reachable, quoted.
 * DX-05's case: {@code graph} accepted a ':' in a name that {@code spotlight} then could not address.
 */
class ChartNamingTest {

    private static final HeapLogStore STORE = new HeapLogStore(
            "eventLogRecord:\n  event: Tick\n  logTime: 1000\n  nodeLogs:\n    - n: {v: 10}\n---\n"
                    + "eventLogRecord:\n  event: Tick\n  logTime: 2000\n  nodeLogs:\n    - n: {v: 20}\n---\n");

    @Test
    @DisplayName("the rule: ':' , '\"' and the part words are refused; ordinary names are not")
    void theRule() {
        assertNotNull(SpotlightTarget.chartNameProblem("a:b"));
        assertNotNull(SpotlightTarget.chartNameProblem("say \"hi\""));
        assertNotNull(SpotlightTarget.chartNameProblem("note"));
        assertNotNull(SpotlightTarget.chartNameProblem("Series"));
        assertNull(SpotlightTarget.chartNameProblem("Series A"), "a name that merely STARTS with the word is a chart (review F1)");
        assertNull(SpotlightTarget.chartNameProblem("Spread"));
        assertNull(SpotlightTarget.chartNameProblem(null), "no name: the default is chosen for it");
    }

    @Test
    @DisplayName("every chart has an address that lights it, and a saved odd name is reached quoted, exactly")
    void everyNameHasAnAddress() {
        // witness: the quoted form removed from SpotlightTarget.parse
        for (String name : List.of("Spread", "a:b", "note", "series", "x:note:2")) {
            String address = SpotlightTarget.graphAddress(name);
            var parsed = SpotlightTarget.parse(address);
            assertTrue(parsed.ok(), name + " → " + address + ": " + parsed.error());
            assertEquals(name, parsed.target().graph(), address);
        }
        var note = SpotlightTarget.parse("graph:\"a:b\":note:2");
        assertTrue(note.ok(), note.error());
        assertEquals("a:b", note.target().graph());
        assertEquals(SpotlightTarget.Family.GRAPH_NOTE, note.target().family());
        var series = SpotlightTarget.parse("graph:\"note\":series:n.v");
        assertTrue(series.ok(), series.error());
        assertEquals("note", series.target().graph());
        assertFalse(SpotlightTarget.parse("graph:\"a:b").ok(), "an unclosed quote is refused, not guessed");
        assertFalse(SpotlightTarget.parse("graph:a:b").ok(), "and the unquoted colon form stays refused");
    }

    private static ActionExecutor executor(GraphTabs tabs) {
        var filter = new FilterState();
        tabs.bind(STORE, filter);
        return new ActionExecutor(() -> STORE, () -> filter, tabs, new LogTablePanel(), (rows, note, fix, kind) -> { });
    }

    @Test
    @DisplayName("DX-05: the graph verb refuses to CREATE an unaddressable name, and creates nothing")
    void theVerbRefusesAtCreation() throws Exception {
        // witness: ActionExecutor.doGraph without the chartNameProblem check
        AtomicReference<GraphTabs> tabs = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> tabs.set(new GraphTabs()));
        var ex = executor(tabs.get());
        var before = tabs.get().graphNames();
        var reply = ex.render("graph", Map.of("name", "risk:limits", "series", List.of("n.v")));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertTrue(String.valueOf(reply.toMap()).contains("cannot contain ':'"), reply.toMap().toString());
        assertEquals(before, tabs.get().graphNames(), "nothing created");
    }

    @Test
    @DisplayName("a SAVED unaddressable name stays reachable by the verb, and a rename INTO one is refused")
    void savedNamesStayReachable() throws Exception {
        AtomicReference<GraphTabs> tabs = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> tabs.set(new GraphTabs()));
        var ex = executor(tabs.get());
        SwingUtilities.invokeAndWait(() -> tabs.get().addGraph("legacy:chart"));   // the restore path, as saved
        var reply = ex.render("graph", Map.of("name", "legacy:chart", "series", List.of("n.v")));
        assertTrue(reply.ok(), "the compatibility promise: " + reply.toMap());
        var rename = ex.render("graph", Map.of("name", "legacy:chart", "rename", "still:bad"));
        assertFalse(rename.ok());
        assertTrue(tabs.get().graphNames().contains("legacy:chart"), "nothing changed");
        var fixed = ex.render("graph", Map.of("name", "legacy:chart", "rename", "legacy chart"));
        assertTrue(fixed.ok(), "and it can be renamed to an addressable name: " + fixed.toMap());
    }
}
