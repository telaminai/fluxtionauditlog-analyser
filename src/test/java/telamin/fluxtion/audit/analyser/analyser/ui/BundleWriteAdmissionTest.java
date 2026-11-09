package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Refusing a malformed bundle request must not first create a folder in the person's project. */
class BundleWriteAdmissionTest {
    record Refusal(Map<String, Object> arguments, String message) { }

    static Stream<Refusal> malformed() {
        return Stream.of(
                new Refusal(Map.of("notes", 42), "bundle 'notes' is text"),
                new Refusal(Map.of("from", "invalid"), "bundle 'from' and 'to' are epoch millis"),
                new Refusal(Map.of("to", "invalid"), "bundle 'from' and 'to' are epoch millis"),
                new Refusal(Map.of("from", 2L, "to", 1L), "bundle 'from' is after 'to'"),
                new Refusal(Map.of("replay", 42), "bundle 'replay' is the path of the run's replay records"),
                new Refusal(Map.of("replay", " "), "bundle 'replay' is the path of the run's replay records"));
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void malformedBundleCreatesNothing(Refusal refusal, @TempDir Path root) {
        var app = new Capture();
        var notices = new ArrayList<String>();
        var ex = executor(root, app, notices);
        var bundle = new LinkedHashMap<String, Object>(refusal.arguments());
        bundle.put("path", "DEMO.fexp");

        var result = ex.render("report", Map.of("bundle", bundle));

        assertFalse(result.ok());
        assertEquals(refusal.message(), result.error());
        assertFalse(Files.exists(root.resolve("analyser-exchange")),
                "a malformed bundle request must not create the exchange directory before refusing");
        assertTrue(notices.isEmpty(), "a refusal must not publish a directory-created fact");
        assertEquals(0, app.calls, "no capture may be submitted for invalid arguments");
    }

    /** Review F3 (#198), the reviewer's reproduction: {@code notes: 42} is refused and leaves no folder behind. */
    @Test
    void rejectedBundleNotesMustNotCreateExchangeDirectory(@TempDir Path root) {
        var notices = new ArrayList<String>();
        var app = new Capture();
        var result = executor(root, app, notices).render("report",
                Map.of("bundle", Map.of("path", "DEMO.fexp", "notes", 42)));
        assertFalse(result.ok());
        assertEquals("bundle 'notes' is text", result.error());
        assertFalse(Files.exists(root.resolve("analyser-exchange")), "refused malformed bundle request must create nothing");
        assertEquals(0, app.calls);
    }

    /** Review F3 (#198): a range that ends before it starts is refused before any folder is made. */
    @Test
    void aBackwardsRangeMustNotCreateExchangeDirectory(@TempDir Path root) {
        var notices = new ArrayList<String>();
        var app = new Capture();
        var result = executor(root, app, notices).render("report",
                Map.of("bundle", Map.of("path", "DEMO.fexp", "from", 2_000L, "to", 1_000L)));
        assertFalse(result.ok());
        assertEquals("bundle 'from' is after 'to'", result.error());
        assertFalse(Files.exists(root.resolve("analyser-exchange")), "refused backwards range must create nothing");
        assertTrue(notices.isEmpty());
    }

    @Test
    void unavailableCaptureCreatesNothing(@TempDir Path root) {
        var notices = new ArrayList<String>();
        var ex = executor(root, null, notices);
        var result = ex.render("report", Map.of("bundle", Map.of("path", "DEMO.fexp")));
        assertFalse(result.ok());
        assertEquals("'report' is not enabled here", result.error());
        assertFalse(Files.exists(root.resolve("analyser-exchange")),
                "an unavailable bundle capture must not create the exchange directory before refusing");
        assertTrue(notices.isEmpty());
    }

    @Test
    void validCaptureStillCreatesTheDirectoryOnceAndPassesEveryArgument(@TempDir Path root) throws Exception {
        var app = new Capture();
        var notices = new ArrayList<String>();
        var ex = executor(root, app, notices);
        var bundle = Map.<String, Object>of("path", "DEMO.fexp", "notes", "DEMO notes",
                "from", 1L, "to", 2L, "replay", "DEMO-replay.json");
        assertTrue(ex.render("report", Map.of("bundle", bundle)).ok());
        assertEquals(root.toRealPath().resolve("analyser-exchange/DEMO.fexp").toString(), app.path);
        assertEquals(List.of("DEMO notes", 1L, 2L, "DEMO-replay.json"), app.arguments);
        assertEquals(1, notices.size(), "the accepted write names the created directory once");
        assertTrue(ex.render("report", Map.of("bundle", bundle)).ok());
        assertEquals(2, app.calls);
        assertEquals(1, notices.size(), "an existing directory is not reported as newly created");
    }

    @Test
    void validArgumentsStillCannotEscapeTheExchangeDirectory(@TempDir Path root) {
        var app = new Capture();
        var notices = new ArrayList<String>();
        var ex = executor(root, app, notices);
        assertFalse(ex.render("report", Map.of("bundle", Map.of("path", "../DEMO.fexp"))).ok());
        assertFalse(Files.exists(root.resolve("analyser-exchange")));
        assertEquals(0, app.calls);
        assertTrue(notices.isEmpty());
    }

    private static ActionExecutor executor(Path root, AppControl app, List<String> notices) {
        var config = new AppConfig();
        config.activeProjectPath = ProjectProfile.pathFor(root).toString();
        config.assistantExports = true;
        var ex = new ActionExecutor(() -> null, () -> null, null, null, null);
        ex.bind(null, app);
        ex.bindExportPolicy(() -> config);
        ex.onExchangeCreated(notices::add);
        return ex;
    }

    private static final class Capture implements AppControl {
        int calls;
        String path;
        List<Object> arguments;
        @Override public ActionResult captureBundle(String path, String notes, Long from, Long to, String replay) {
            calls++;
            this.path = path;
            arguments = java.util.Arrays.asList(notes, from, to, replay);
            assertTrue(Files.isDirectory(Path.of(path).getParent()), "the admitted capture has its output directory");
            return ActionResult.ok("report", "bundle", Map.of("path", path));
        }
        @Override public ActionResult openLog(String path) { return ActionResult.error("unused"); }
        @Override public ActionResult openGraphml(String path) { return ActionResult.error("unused"); }
        @Override public ActionResult selectProcessor(String fqn) { return ActionResult.error("unused"); }
        @Override public List<String> sourceRoots() { return List.of(); }
        @Override public boolean addSourceRoot(String path) { return false; }
        @Override public boolean removeSourceRoot(String path) { return false; }
        @Override public ActionResult screenshot(String path, String scope) { return ActionResult.error("unused"); }
        @Override public ActionResult context() { return ActionResult.error("unused"); }
        @Override public boolean showTab(String name) { return false; }
        @Override public ActionResult exportFinding(String path, Integer record, String title, String graph, boolean topology) {
            return ActionResult.error("unused");
        }
    }
}
