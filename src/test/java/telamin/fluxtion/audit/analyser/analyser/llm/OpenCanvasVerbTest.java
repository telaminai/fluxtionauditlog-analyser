package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;
import telamin.fluxtion.audit.analyser.analyser.ui.GraphTabs;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTablePanel;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M48.7, folded into {@code open} before it shipped (second reader A6; owner 2026-09-17): the shared canvas is
 * written with {@code open {posture}}, {@code open {record}} and taken back with {@code open {close: "handoff"}},
 * mirroring {@code open {close: "project"}}. The state and its rules are {@link CanvasHandoff}'s and are tested
 * there; this pins the VERB: what reaches the canvas, and that a canvas write is never half of a larger open.
 */
class OpenCanvasVerbTest {

    /** Every AppControl call, in order, as {@code method(args)} — so "nothing else was touched" is assertable. */
    private final List<String> calls = new ArrayList<>();

    private ActionExecutor executor() {
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f) -> { });
        AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                new Class<?>[]{AppControl.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("clearSpotlight")) {
                        calls.add(method.getName() + (args == null ? "()" : List.of(args).toString()));
                    }
                    if (method.getName().equals("handoff")) {
                        return ActionResult.ok("open", "handoff", Map.of("posture", Map.of("value", "as written")));
                    }
                    Class<?> type = method.getReturnType();
                    if (type == ActionResult.class) return ActionResult.ok("open", "applied", Map.of());
                    if (type == boolean.class) return false;
                    if (type == List.class) return List.of();
                    return null;
                });
        ex.bind(null, app);
        return ex;
    }

    @Test
    void postureAndRecordReachTheCanvas_andNothingElseIsTouched() {
        Map<String, Object> record = Map.of("branch", "catalogue", "modes", List.of("0"));
        ActionResult r = executor().render("open", new LinkedHashMap<>(Map.of("posture", "authoring", "record", record)));

        assertTrue(r.ok(), String.valueOf(r.toMap()));
        assertEquals(1, calls.size(), calls.toString());
        assertTrue(calls.get(0).startsWith("handoff[{") && calls.get(0).contains("posture=authoring")
                && calls.get(0).contains("branch=catalogue"), calls.toString());
        assertTrue(r.toMap().containsKey("handoff"), "the echo is the canvas as context.handoff states it");
    }

    @Test
    void closeHandoffTakesBothOff_mirroringCloseProject_andSaysWhatItClosed() {
        ActionResult r = executor().render("open", new LinkedHashMap<>(Map.of("close", "handoff")));

        assertTrue(r.ok(), String.valueOf(r.toMap()));
        assertEquals(List.of("handoff[{clear=all}]"), calls, "the canvas's own clear — never the log/graph close path");
        assertEquals("handoff", ((Map<?, ?>) r.toMap().get("applied")).get("closed"));
    }

    @Test
    void aCanvasWriteGoesALONE_combinedWithAnOpenItIsRefusedWhole_andNeitherHalfHappens() {
        ActionResult r = executor().render("open", new LinkedHashMap<>(Map.of("posture", "authoring", "log", "/run.yaml")));

        assertFalse(r.ok());
        assertTrue(String.valueOf(r.toMap()).contains("goes ALONE") && String.valueOf(r.toMap()).contains("log"),
                String.valueOf(r.toMap()));
        assertEquals(List.of(), calls, "fail-closed: no canvas write, and no log opened either");
    }

    @Test
    void settingItAndRemovingItInOneCallIsRefused_whichHalfWasMeantIsNotGuessed() {
        ActionResult r = executor().render("open", new LinkedHashMap<>(Map.of("close", "handoff", "posture", "research")));

        assertFalse(r.ok());
        assertTrue(String.valueOf(r.toMap()).contains("set it and remove it"), String.valueOf(r.toMap()));
        assertEquals(List.of(), calls);
    }

    @Test
    void theOtherClosesAreUntouched_andDoNotReachTheCanvas() {
        executor().render("open", new LinkedHashMap<>(Map.of("close", "log")));
        assertEquals(List.of("close[log]"), calls.stream().filter(c -> !c.startsWith("ignored")).toList(),
                "'all' and 'log' are about the log and the graph; the canvas has its own close");
    }
}
