package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.4 (spec-evidence-integrity D-E3, the disposition table): a call is honoured whole or refused whole, and never
 * reports success while dropping part of what it was asked. Each test names the row it covers and the mutation that
 * must turn it red.
 */
class WholeOrRefusedTest {

    private static final HeapLogStore STORE = new HeapLogStore(
            "eventLogRecord:\n  event: Tick\n  logTime: 1000\n  nodeLogs:\n    - n: {v: 10}\n---\n"
                    + "eventLogRecord:\n  event: Tick\n  logTime: 2000\n  nodeLogs:\n    - n: {v: 20}\n---\n");

    /** A stand-in application that records which calls reached it. graphml fails when told to. */
    private static final class App {
        final List<String> calls = new ArrayList<>();
        boolean graphmlFails;
        final AtomicInteger flagged = new AtomicInteger();

        AppControl proxy() {
            return (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(), new Class<?>[]{AppControl.class},
                    (p, m, args) -> {
                        String name = m.getName();
                        if (m.isDefault() && !name.equals("openLog") && !name.equals("openLogs")
                                && !name.equals("discoverGraphs")) {
                            return java.lang.reflect.InvocationHandler.invokeDefault(p, m, args);
                        }
                        calls.add(name + (args == null ? "" : java.util.Arrays.toString(args)));
                        return switch (name) {
                            case "openLog" -> ActionResult.ok("open", "log", Map.of("path", String.valueOf(args[0]), "loading", true));
                            case "openLogs" -> ActionResult.ok("open", "applied", Map.of("loading", true));
                            case "openGraphml" -> graphmlFails ? ActionResult.error("not a graphml")
                                    : ActionResult.ok("open", "graphml", Map.of("path", String.valueOf(args[0])));
                            case "discoverGraphs" -> ActionResult.ok("open", "discover", Map.of("candidates", List.of()));
                            case "sourceRoots" -> List.of();
                            case "addSourceRoot", "removeSourceRoot" -> false;
                            default -> m.getReturnType() == ActionResult.class ? ActionResult.ok(name, "applied", Map.of())
                                    : m.getReturnType() == boolean.class ? false : null;
                        };
                    });
        }
    }

    private static ActionExecutor executor(App app) {
        var filter = new FilterState();
        var tabs = new GraphTabs();
        tabs.bind(STORE, filter);
        var ex = new ActionExecutor(() -> STORE, () -> filter, tabs, new LogTablePanel(),
                (rows, note, fix, kind) -> app.flagged.addAndGet(rows.length));
        ex.bind(null, app.proxy());
        return ex;
    }

    @Test
    @DisplayName("DX-03: a rolled set with a graphml opens BOTH — it used to return early and drop the graphml")
    void aRolledSetAndAGraphAreBothOpened() {
        // witness: the rolledSet branch back to returning openLogs whatever else the call carried
        App app = new App();
        var reply = executor(app).render("open", Map.of("logs", List.of("/a.yaml", "/b.yaml"), "graphml", "/g.graphml"));
        assertTrue(reply.ok(), reply.toMap().toString());
        assertTrue(app.calls.stream().anyMatch(c -> c.startsWith("openLogs")), app.calls.toString());
        assertTrue(app.calls.stream().anyMatch(c -> c.startsWith("openGraphml")), "the graphml is opened too: " + app.calls);
    }

    @Test
    @DisplayName("a log with an explicit format and a graphml opens both, and the format still travels")
    void aFormattedLogAndAGraphAreBothOpened() {
        App app = new App();
        var reply = executor(app).render("open", Map.of("log", "/a.log", "format", "test-slow", "graphml", "/g.graphml"));
        assertTrue(reply.ok(), reply.toMap().toString());
        assertTrue(app.calls.stream().anyMatch(c -> c.startsWith("openLog") && c.contains("test-slow")), app.calls.toString());
        assertTrue(app.calls.stream().anyMatch(c -> c.startsWith("openGraphml")), app.calls.toString());
    }

    @Test
    @DisplayName("discover opens nothing, and says what else it did not do")
    void discoverNamesWhatItDidNotOpen() {
        App app = new App();
        var reply = executor(app).render("open", Map.of("discover", "graphs", "log", "/a.yaml"));
        assertTrue(reply.ok());
        assertEquals(List.of("log"), reply.payload().get("ignored"), reply.toMap().toString());
        assertFalse(app.calls.stream().anyMatch(c -> c.startsWith("openLog")), "and nothing was opened");
    }

    @Test
    @DisplayName("a graphml that fails after the log started says the log stays open")
    void aLateFailureSaysWhatAlreadyHappened() {
        App app = new App();
        app.graphmlFails = true;
        var reply = executor(app).render("open", Map.of("log", "/a.yaml", "graphml", "/bad"));
        assertFalse(reply.ok());
        assertTrue(String.valueOf(reply.toMap()).contains("stays open"), reply.toMap().toString());
    }

    @Test
    @DisplayName("flag never attaches a finding to a record nobody named — an out-of-log index is refused, not clamped")
    void flagRefusesAnIndexTheLogDoesNotHave() {
        // witness: flag back to clampRow for recordIndexes
        App app = new App();
        var reply = executor(app).render("flag", Map.of("recordIndexes", List.of(1000), "note", "fault"));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertTrue(String.valueOf(reply.toMap()).contains("1000"), reply.toMap().toString());
        assertEquals(0, app.flagged.get(), "nothing was flagged — it used to flag the last record");
    }

    @Test
    @DisplayName("goto keeps its clamp, and names it; several anchors name the losers")
    void gotoNamesItsClampAndItsLosers() {
        App app = new App();
        var reply = executor(app).render("goto", Map.of("recordIndex", 99));
        assertTrue(reply.ok(), reply.toMap().toString());
        assertEquals(Map.of("asked", 99, "used", 1), reply.payload().get("clamped"));
        var both = executor(app).render("goto", Map.of("recordIndex", 0, "at", 2000));
        assertEquals(List.of("at"), both.payload().get("ignored"), both.toMap().toString());
    }

    @Test
    @DisplayName("a rename does only the rename: other fields in the call are refused, not dropped")
    void aRenameRefusesTheRest() throws Exception {
        App app = new App();
        var ex = executor(app);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        var reply = ex.render("graph", Map.of("name", "x", "rename", "y", "series", List.of("n.v")));
        assertFalse(reply.ok(), reply.toMap().toString());
        assertTrue(String.valueOf(reply.toMap()).contains("series"), reply.toMap().toString());
    }

    @Test
    @DisplayName("source_root names a removal that matched nothing")
    void sourceRootNamesAFailedRemove() {
        App app = new App();
        var reply = executor(app).render("source_root", Map.of("remove", List.of("/not/a/root")));
        assertTrue(reply.ok());
        assertEquals(List.of("/not/a/root"), reply.payload().get("notRemoved"), reply.toMap().toString());
    }

    @Test
    @DisplayName("a refusal names misspelled parameters too — they are often why it was refused")
    void aRefusalNamesUnknownKeys() {
        // witness: withIgnoredParams back to returning early on a refusal
        App app = new App();
        var d = new ActionDispatcher(false, null, () -> STORE.index().snapshot(), STORE::rawText, STORE::record, executor(app));
        var reply = d.dispatch(Map.of("action", "goto", "params", Map.of("recordIndx", 1)));
        assertFalse(reply.ok());
        assertTrue(String.valueOf(reply.toMap()).contains("recordIndx"), reply.toMap().toString());
    }
}
