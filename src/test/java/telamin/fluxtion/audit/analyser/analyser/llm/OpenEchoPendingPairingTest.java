package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;
import telamin.fluxtion.audit.analyser.analyser.ui.GraphTabs;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTablePanel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code open {log, graphml}} in one call: the frame loads the log in the background, so a pairing verdict
 * formed in the same call was about the PREVIOUS log or none. The 2026-09-16 session report saw both
 * ("no log is open" on a first open; the old log's node counts on a re-open) while {@code context} was
 * right a moment later. The echo must say the verdict is pending, not pass on one about another log.
 */
class OpenEchoPendingPairingTest {

    @Test
    @SuppressWarnings("unchecked")
    void aVerdictFormedWhileTheLogIsStillLoadingIsReplacedByPending() {
        Frame frame = new Frame(true);
        ActionExecutor ex = executor(frame);

        Map<String, Object> out = ex.render("open", Map.of("log", "/run.yaml", "graphml", "/p.graphml")).toMap();
        Map<String, Object> echo = (Map<String, Object>) out.get("opened");

        assertEquals(Boolean.TRUE, echo.get("logLoading"), "the caller is told the log is not loaded yet");
        Map<String, Object> g = (Map<String, Object>) echo.get("graphml");
        assertEquals(ActionExecutor.PAIRING_PENDING, g.get("pairing"));
        for (String stale : List.of("appliesToOpenLog", "loggedNodes", "declaredByGraph", "verdict")) {
            assertFalse(g.containsKey(stale), stale + " was judged against the previous log and must not be echoed");
        }
        assertEquals("/p.graphml", g.get("path"), "the rest of the graph echo is kept");
        assertEquals(5, g.get("graphNodes"));
        assertEquals(3, g.get("authoredNodes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void control_aSynchronousAdapterKeepsItsVerdict() {
        Frame frame = new Frame(false);          // an adapter whose openLog has finished when it returns
        ActionExecutor ex = executor(frame);

        Map<String, Object> out = ex.render("open", Map.of("log", "/run.yaml", "graphml", "/p.graphml")).toMap();
        Map<String, Object> echo = (Map<String, Object>) out.get("opened");

        assertNull(echo.get("logLoading"));
        Map<String, Object> g = (Map<String, Object>) echo.get("graphml");
        assertEquals(2, g.get("loggedNodes"), "a verdict about THIS log is the useful half and stays");
        assertEquals(2, g.get("declaredByGraph"));
        assertFalse(g.containsKey("pairing"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aGraphOpenedAloneIsNeverRewritten() {
        Frame frame = new Frame(true);
        ActionExecutor ex = executor(frame);

        Map<String, Object> out = ex.render("open", Map.of("graphml", "/p.graphml")).toMap();
        Map<String, Object> g = (Map<String, Object>) ((Map<String, Object>) out.get("opened")).get("graphml");
        assertEquals(2, g.get("loggedNodes"), "no log opened in this call — whatever is loaded is what the graph was judged against");
    }

    @Test
    void designAndSourceReadsReachTheAdapterWithoutALogAndOffTheEdt() {
        Frame frame = new Frame(false);
        ActionExecutor ex = executor(frame);
        assertTrue(ex.render("source", Map.of("bean", "gate")).ok());
        assertTrue(ex.render("open", Map.of("design", "design.xml", "diagnostics", "result.json")).ok());
        assertEquals(List.of("source", "design", "diagnostics"), frame.designCalls);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static ActionExecutor executor(AppControl app) {
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f) -> { });
        ex.bind(null, app);
        return ex;
    }

    /** An adapter that judges a graph against whatever log it holds — the previous one, while a load is in flight. */
    private static final class Frame implements AppControl {
        private final boolean asynchronousLoad;
        Frame(boolean asynchronousLoad) { this.asynchronousLoad = asynchronousLoad; }

        final java.util.ArrayList<String> designCalls = new java.util.ArrayList<>();
        private ActionResult designRead(String kind) {
            assertFalse(javax.swing.SwingUtilities.isEventDispatchThread(), "file reads belong off the UI thread");
            designCalls.add(kind);
            return ActionResult.ok(kind, kind, Map.of("relationship", "unverified"));
        }
        @Override public ActionResult source(Map<String,Object> params) { return designRead("source"); }
        @Override public ActionResult openDesign(String path) { return designRead("design"); }
        @Override public ActionResult openDiagnostics(String path) { return designRead("diagnostics"); }
        @Override public ActionResult openLog(String path) { return openLog(path, null, null); }
        @Override public ActionResult openLog(String path, String format, String provenance) {
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("path", path);
            if (asynchronousLoad) echo.put("loading", true);
            return ActionResult.ok("open", "log", echo);
        }
        @Override public ActionResult openGraphml(String path) {
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("path", path);
            echo.put("graphNodes", 5);               // the production echo's keys (M46 A3): two facts, two names
            echo.put("authoredNodes", 3);
            echo.put("appliesToOpenLog", true);      // judged against the log in force when called
            echo.put("loggedNodes", 2);
            echo.put("declaredByGraph", 2);
            echo.put("verdict", "graph declares 2 of 2 logged nodes");
            return ActionResult.ok("open", "graphml", echo);
        }
        @Override public void setProvenance(String provenance) { }
        @Override public ActionResult selectProcessor(String fqn) { return ActionResult.error("no"); }
        @Override public List<String> sourceRoots() { return List.of(); }
        @Override public boolean addSourceRoot(String path) { return false; }
        @Override public boolean removeSourceRoot(String path) { return false; }
        @Override public ActionResult screenshot(String path, String scope) { return ActionResult.error("no"); }
        @Override public ActionResult context() { return ActionResult.error("no"); }
        @Override public boolean showTab(String name) { return false; }
        @Override public ActionResult exportFinding(String path, Integer recordIndex, String title, String graph, boolean withTopology) {
            return ActionResult.error("no");
        }
    }
}
