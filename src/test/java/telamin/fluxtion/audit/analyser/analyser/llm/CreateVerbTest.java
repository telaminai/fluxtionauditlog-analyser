package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;
import telamin.fluxtion.audit.analyser.analyser.ui.GraphTabs;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTablePanel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M73 (spec-create-project.md §3.1, D-73.1, D-73.10) — the contract of {@code open {project, create}} at the
 * executor: routing, the shapes refused whole, the published surface. Session decisions are
 * {@code session.CreateProjectDecisionTest}'s; the filesystem is {@code ui.CreateProjectFlowTest}'s.
 */
class CreateVerbTest {

    // ---- the surface does not grow (D-73.1) -------------------------------------------------------------

    @Test
    @DisplayName("no new verb: still 18, and open's schema names create, the preview and that it never overwrites")
    void theSurfaceDoesNotGrow() {
        assertEquals(18, VerbSchemas.all().size());
        assertFalse(VerbSchemas.all().containsKey("create"));
        String open = VerbSchemas.all().get("open").toString();
        assertTrue(open.contains("create="), "the param is published");
        assertTrue(open.contains("PREVIEW"), "the preview is named");
        assertTrue(open.contains("never overwrites"), "and the guarantee");
        assertTrue(open.contains("Allow the assistant to create projects"), "and the consent it needs");
    }

    @Test
    void anImplementorThatPredatesTheParamRefuses() {
        ActionResult r = new Legacy().createProject("/work/demo", Boolean.TRUE);
        assertFalse(r.ok());
        assertTrue(r.error().contains("not enabled"), r.error());
    }

    // ---- routing (§3.1) ------------------------------------------------------------------------------------

    @Test
    @DisplayName("create is routed FIRST: with follow, canvas, close, analysis, restore or bundle it is refused WHOLE")
    void refusedCombinations() {
        for (String other : List.of("follow", "posture", "record", "close", "analysis", "restore", "bundle")) {
            Fake app = new Fake();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("project", "/work/demo");
            params.put("create", true);
            params.put(other, other.equals("follow") ? Boolean.TRUE : "x");
            ActionResult r = executor(app).render("open", params);
            assertFalse(r.ok(), other);
            assertTrue(r.error().contains("goes with 'project' alone") && r.error().contains(other), r.error());
            assertTrue(app.calls.isEmpty(), "nothing reached the app for " + other);
            assertTrue(app.other.isEmpty(), "and the other act was not performed either: " + other);
        }
    }

    @Test
    void createWithoutProjectIsRefused() {
        Fake app = new Fake();
        ActionResult r = executor(app).render("open", Map.of("create", true));
        assertFalse(r.ok());
        assertTrue(r.error().contains("needs 'project'"), r.error());
        assertTrue(app.calls.isEmpty());
    }

    @Test
    void aRelativeDirectoryIsRefused() {
        Fake app = new Fake();
        ActionResult r = executor(app).render("open", Map.of("project", "demo", "create", true));
        assertFalse(r.ok());
        assertTrue(r.error().contains("ABSOLUTE"), r.error());
        assertTrue(app.calls.isEmpty());
    }

    @Test
    @DisplayName("D-73.10: an unknown key is refused, and 'profile' is reserved")
    void unknownKeysAndProfileAreRefused() {
        Fake app = new Fake();
        ActionResult unknown = executor(app).render("open", Map.of("project", "/work/demo",
                "create", Map.of("processors", List.of("com.acme.Demo"))));
        assertFalse(unknown.ok());
        assertTrue(unknown.error().contains("unknown 'create' key(s) [processors]"), unknown.error());
        ActionResult profile = executor(app).render("open", Map.of("project", "/work/demo",
                "create", Map.of("profile", "ws-feed")));
        assertFalse(profile.ok());
        assertTrue(profile.error().contains("named profiles are not created over the socket yet"), profile.error());
        ActionResult wrongType = executor(app).render("open", Map.of("project", "/work/demo",
                "create", Map.of("sourceRoots", ".")));
        assertFalse(wrongType.ok());
        assertTrue(app.calls.isEmpty());
        ActionResult notABoolean = executor(app).render("open", Map.of("project", "/work/demo", "create", false));
        assertFalse(notABoolean.ok(), "create: false means nothing; it is refused rather than guessed");
    }

    @Test
    @DisplayName("the preview and the apply reach the app OFF the event thread; 'create' is never named as ignored")
    void routesOffTheEdt() {
        Fake app = new Fake();
        ActionResult preview = executor(app).render("open", Map.of("project", "/work/demo", "create", true,
                "log", "/work/a.yaml"));
        assertTrue(preview.ok(), () -> "" + preview);
        assertEquals(List.of("/work/demo:true"), app.calls);
        assertEquals(List.of(false), app.onEdt, "discovery walks the directory: never on the event thread");
        assertEquals(List.of("log"), preview.payload().get("ignored"), "create belongs to the project act");
        ActionResult apply = executor(app).render("open", Map.of("project", "/work/demo",
                "create", Map.of("sourceRoots", List.of("."), "referenceGuide", false, "offerDigest", "sha256:demo")));
        assertTrue(apply.ok());
        assertEquals(List.of(false, false), app.onEdt);
        assertNull(apply.payload().get("ignored"));
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private static ActionExecutor executor(AppControl app) {
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (r, n, f, k) -> { });
        ex.bind(null, app);
        return ex;
    }

    private static class Legacy implements AppControl {
        @Override public ActionResult openLog(String path) { return ActionResult.error("no"); }
        @Override public ActionResult openGraphml(String path) { return ActionResult.error("no"); }
        @Override public ActionResult selectProcessor(String fqn) { return ActionResult.error("no"); }
        @Override public List<String> sourceRoots() { return List.of(); }
        @Override public boolean addSourceRoot(String path) { return false; }
        @Override public boolean removeSourceRoot(String path) { return false; }
        @Override public ActionResult screenshot(String path, String scope) { return ActionResult.error("no"); }
        @Override public ActionResult context() { return ActionResult.error("no"); }
        @Override public boolean showTab(String name) { return false; }
        @Override public ActionResult exportFinding(String path, Integer recordIndex, String title,
                                                    String graph, boolean withTopology) {
            return ActionResult.error("no");
        }
    }

    private static class Fake extends Legacy {
        final List<String> calls = new ArrayList<>();
        final List<Boolean> onEdt = new ArrayList<>();
        final List<String> other = new ArrayList<>();

        @Override public ActionResult createProject(String dir, Object create) {
            calls.add(dir + ":" + (create instanceof Map ? "apply" : create));
            onEdt.add(javax.swing.SwingUtilities.isEventDispatchThread());
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("dir", dir);
            return ActionResult.ok("open", create instanceof Map ? "opened" : "createPreview", echo);
        }

        @Override public ActionResult follow(boolean on) { other.add("follow"); return ActionResult.ok("open", "follow", Map.of()); }
        @Override public ActionResult close(String what) { other.add("close"); return ActionResult.ok("open", "applied", Map.of()); }
        @Override public ActionResult openBundle(String path) { other.add("bundle"); return ActionResult.ok("open", "bundle", Map.of()); }
        @Override public ActionResult openProject(String path) { other.add("project"); return ActionResult.ok("open", "opened", Map.of()); }
        @Override public ActionResult handoff(Map<String, Object> params) { other.add("canvas"); return ActionResult.ok("open", "handoff", Map.of()); }
        @Override public ActionResult restoreSession() { other.add("restore"); return ActionResult.ok("open", "restore", Map.of()); }
        @Override public ActionResult runAnalysis(String name, Map<String, String> bindings) { other.add("analysis"); return ActionResult.ok("open", "analysis", Map.of()); }
    }
}
