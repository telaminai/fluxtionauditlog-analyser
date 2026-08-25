package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;
import telamin.fluxtion.audit.analyser.analyser.ui.GraphTabs;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTablePanel;
import telamin.fluxtion.audit.analyser.analyser.ui.OpenRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M35.9 — the request travels WITH the open. The headless half: the executor hands provenance to the
 * app in the SAME call as the path (no field set before, consumed after), the published surface keeps
 * pre-M35.9 implementors working through the defaults, and the record normalises what it carries.
 * What the load does with the request is Swing and is driven over REST (report §4).
 */
class OpenRequestTest {

    @Test
    void theRecordCarriesWhoAskedAndWhatTheyDeclared() {
        assertFalse(OpenRequest.HUMAN.fromActionSocket());
        assertNull(OpenRequest.HUMAN.provenance());
        assertTrue(OpenRequest.socket("risk · uat").fromActionSocket());
        assertEquals("risk · uat", OpenRequest.socket("  risk · uat  ").provenance(), "trimmed");
        assertNull(OpenRequest.socket("   ").provenance(), "blank is not a declaration");
        assertFalse(OpenRequest.reload("risk").fromActionSocket(), "a follow reload is human-context");
        assertEquals("risk", OpenRequest.reload("risk").provenance(), "and keeps what the log declared");
    }

    @Test
    void provenanceArrivesInTheSameCallAsThePath_neverThroughAFieldSetBeforehand() {
        Fake app = new Fake();
        ActionExecutor ex = executor(app);
        ex.render("open", Map.of("log", "/a.yaml", "provenance", "risk · uat"));
        ex.render("open", Map.of("log", "/b.yaml", "format", "yaml", "provenance", "risk · prod"));
        ex.render("open", Map.of("logs", List.of("/c1.yaml", "/c2.yaml"), "provenance", "risk · dr"));
        ex.render("open", Map.of("log", "/d.yaml"));
        assertEquals(List.of("/a.yaml|null|risk · uat", "/b.yaml|yaml|risk · prod", "[/c1.yaml, /c2.yaml]|risk · dr",
                "/d.yaml|null|null"), app.calls, "each open carries its own provenance, and an undeclared one carries null");
        assertEquals(0, app.setProvenanceCalls, "the pre-open field protocol is no longer used by the executor");
    }

    @Test
    void anImplementorWrittenBeforeM35_9StillReceivesProvenance_throughTheDefaults() {
        // the published AppControl kept setProvenance + openLog(path, format); the new 3-arg default
        // routes through them, so a reader written against 1.5.0 sees exactly what it used to
        Legacy legacy = new Legacy();
        ActionExecutor ex = executor(legacy);
        ex.render("open", Map.of("log", "/a.yaml", "provenance", "risk · uat"));
        assertEquals("risk · uat", legacy.lastProvenance);
        assertEquals("/a.yaml", legacy.lastPath);
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

    /** Only the abstract surface plus the OLD provenance protocol — an implementor from before M35.9. */
    private static class Legacy implements AppControl {
        String lastPath, lastProvenance;
        @Override public ActionResult openLog(String path) { lastPath = path; return ActionResult.ok("open", "opened", Map.of("log", path)); }
        @Override public void setProvenance(String provenance) { lastProvenance = provenance; }
        @Override public ActionResult openGraphml(String path) { return ActionResult.error("no"); }
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

    /** Implements the M35.9 surface: provenance arrives with the open. */
    private static class Fake extends Legacy {
        final List<String> calls = new ArrayList<>();
        int setProvenanceCalls;
        @Override public void setProvenance(String provenance) { setProvenanceCalls++; }
        @Override public ActionResult openLog(String path, String format, String provenance) {
            calls.add(path + "|" + format + "|" + provenance);
            return ActionResult.ok("open", "opened", Map.of("log", path));
        }
        @Override public ActionResult openLogs(List<String> paths, String provenance) {
            calls.add(paths + "|" + provenance);
            return ActionResult.ok("open", "applied", Map.of("files", paths));
        }
    }
}
