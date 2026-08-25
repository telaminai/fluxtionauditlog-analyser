package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;
import telamin.fluxtion.audit.analyser.analyser.ui.GraphTabs;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTablePanel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M35.8 — the headless half. What the switch actually replaces on screen is Swing and is driven over
 * REST (report §E7–E10); what is pinned here is the CONTRACT: the manifest publishes the param, the
 * published surface refuses by default so every implementor keeps compiling, the executor routes to
 * the app and NAMES what it ignored, and a bad path degrades to a reason rather than an exception.
 */
class ProjectVerbTest {

    // ---- the manifest is the single source of truth -----------------------------------------------

    @Test
    void theSchemaPublishesProject_andCloseProject() {
        String open = VerbSchemas.all().get("open").toString();
        assertTrue(open.contains("project"), "an agent that cannot discover the param cannot use it");
        assertTrue(open.contains("REPLACED"), "the schema states the size of the mutation up front");
        assertTrue(open.contains("session boundary"), "and that the log and graph close with it");
        assertTrue(open.contains("projectOffer"), "and that this is how the offer context reports is accepted");
    }

    @Test
    void projectIsNotItsOwnVerb_theSurfaceDoesNotGrow() {
        assertFalse(VerbSchemas.all().containsKey("project"));
        assertEquals(14, VerbSchemas.all().size(), "M35.8 extends 'open' like close and discover did");
    }

    // ---- the published surface -----------------------------------------------------------------

    @Test
    void anImplementorThatPredatesTheVerbRefusesRatherThanFailingToCompile() {
        AppControl legacy = new Legacy();
        ActionResult r = legacy.openProject("/anywhere");
        assertFalse(r.ok());
        assertTrue(r.error().contains("not enabled"), r.error());
        assertFalse(legacy.close("project").ok(), "and the same for the way back");
    }

    // ---- routing and the R2 rule: ignored params are named -----------------------------------------

    @Test
    void projectRoutesToTheApp_andNamesEveryParamItDidNotHonour() {
        Fake app = new Fake();
        ActionResult r = executor(app).render("open", Map.of(
                "project", "/repo",
                "log", "/repo/logs/a.yaml",
                "graphml", "/repo/x.graphml",
                "close", "log"));
        assertTrue(r.ok(), () -> "" + r);
        assertEquals(List.of("/repo"), app.projectsOpened, "the switch happened, once");
        assertTrue(app.logsOpened.isEmpty(), "the log was NOT opened into a session the switch ends");
        assertTrue(app.closed.isEmpty(), "and 'close' was not honoured beside it either");
        Map<String, Object> echo = r.payload();
        assertEquals("proj", echo.get("project"), "the app's echo is carried up, not replaced");
        assertEquals(List.of("log", "graphml", "close"), echo.get("ignored"),
                "a param silently dropped reads to the caller as one that was honoured");
        assertTrue(echo.get("ignoredWhy").toString().contains("session boundary"));
    }

    @Test
    void projectAloneCarriesTheAppEchoUntouched() {
        Fake app = new Fake();
        ActionResult r = executor(app).render("open", Map.of("project", "/repo"));
        assertTrue(r.ok());
        assertNull(r.payload().get("ignored"), "nothing to name when nothing was dropped");
        assertEquals("proj", r.payload().get("project"));
    }

    @Test
    void aFailedSwitchIsTheAppsReason_notAWrappedSuccess() {
        Fake app = new Fake();
        app.fail = "project settings not found: /nowhere/.analyser/project.fluxtion-settings";
        ActionResult r = executor(app).render("open", Map.of("project", "/nowhere", "log", "/x.yaml"));
        assertFalse(r.ok());
        assertEquals(app.fail, r.error(),
                "the refusal names why (M26.4), and 'ignored' is not appended to a failure");
    }

    @Test
    void closeProjectRoutesThroughClose_soTheWayBackIsOneCall() {
        Fake app = new Fake();
        ActionResult r = executor(app).render("open", Map.of("close", "project"));
        assertTrue(r.ok());
        assertEquals(List.of("project"), app.closed);
    }

    // ---- never an exception: the machinery this verb routes to keeps its promise --------------------

    @Test
    void aMissingOrGarbageProfileDegradesToANamedReason(@TempDir Path dir) throws Exception {
        AppConfig config = new AppConfig();
        SettingsShare share = new SettingsShare();

        ProjectProfile.LoadResult missing = ProjectProfile.load(dir.resolve("none"), config, share);
        assertFalse(missing.loaded());
        assertTrue(missing.message().contains("not found"), missing.message());

        Path garbage = dir.resolve("garbage.fluxtion-settings");
        Files.writeString(garbage, "  not = a [profile");
        ProjectProfile.LoadResult bad = assertDoesNotThrow(() -> ProjectProfile.load(garbage, config, share));
        // either it parsed as an (empty) properties file or it refused — both are answers, neither a throw
        assertNotNull(bad.message());

        assertFalse(ProjectProfile.load(null, config, share).loaded(), "null is 'no project file', not an NPE");
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private static ActionExecutor executor(AppControl app) {
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (r, n, f) -> { });
        ex.bind(null, app);
        return ex;
    }

    /** Only the abstract surface, the way an implementor written before M35.8 would have — no overrides. */
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

    /** A fake app that records what the executor asked of it. */
    private static class Fake extends Legacy {
        final List<String> projectsOpened = new ArrayList<>();
        final List<String> logsOpened = new ArrayList<>();
        final List<String> closed = new ArrayList<>();
        String fail;

        @Override public ActionResult openLog(String path) {
            logsOpened.add(path);
            return ActionResult.ok("open", "opened", Map.of("log", path));
        }

        @Override public ActionResult openProject(String path) {
            if (fail != null) return ActionResult.error(fail);
            projectsOpened.add(path);
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("project", "proj");
            echo.put("settings", path + "/.analyser/project.fluxtion-settings");
            return ActionResult.ok("open", "opened", echo);
        }

        @Override public ActionResult close(String what) {
            closed.add(what);
            return ActionResult.ok("open", "applied", Map.of("closed", what));
        }
    }
}
