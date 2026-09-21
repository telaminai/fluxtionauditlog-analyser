package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M64 acceptance 3 — {@code open}, {@code filter}, {@code goto}, {@code graph} and {@code topology} each
 * put out a live spotlight: <i>a spotlight that outlives its context points at the wrong thing, which is
 * worse than none</i> (D-SP3). One test per verb, so that removing one verb from
 * {@link SpotlightTarget#VIEW_CHANGING_VERBS} turns exactly that verb's test red.
 *
 * <p>What is asserted is that the adapter was TOLD to clear before the verb ran — whether the verb then
 * succeeds is irrelevant (here most are refused for want of a log), because the view a spotlight pointed
 * at is no longer something the caller is looking at once it has asked for another.
 */
class SpotlightEndsWhenTheViewChangesTest {

    private static int clearsCausedBy(String verb, Map<String, Object> params) {
        AtomicInteger clears = new AtomicInteger();
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
        AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                new Class<?>[]{AppControl.class}, (proxy, method, args) -> {
                    if (method.getName().equals("clearSpotlight")) {
                        clears.incrementAndGet();
                        return null;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == ActionResult.class) return ActionResult.error("the double does nothing");
                    if (type == boolean.class) return false;
                    if (type == List.class) return List.of();
                    return null;
                });
        ex.bind(null, app);
        ex.render(verb, params);
        return clears.get();
    }

    // ---- the five that change the view ---------------------------------------------------------------

    @Test
    void openPutsItOut() {
        assertEquals(1, clearsCausedBy("open", Map.of("log", "/run.yaml")));
    }

    @Test
    void filterPutsItOut() {
        assertEquals(1, clearsCausedBy("filter", Map.of("text", "breach")));
    }

    @Test
    void gotoPutsItOut() {
        assertEquals(1, clearsCausedBy("goto", Map.of("recordIndex", 3)));
    }

    @Test
    void graphPutsItOut() {
        assertEquals(1, clearsCausedBy("graph", Map.of("name", "Spread", "keys", List.of("quotePublisher.spread"))));
    }

    @Test
    void topologyPutsItOut() {
        assertEquals(1, clearsCausedBy("topology", Map.of("select", "priceListener")));
    }

    // ---- the ones that must NOT: they are how the tutor checks what it lit, or they change no view ----

    @Test
    void screenshotLeavesItLit_itIsTheShotThatProvesTheSpotlightIsOnTheRightThing() {
        assertEquals(0, clearsCausedBy("screenshot", Map.of("path", "shot.png")));
    }

    @Test
    void contextLeavesItLit_itIsHowTheTutorReadsBackWhatIsLit() {
        assertEquals(0, clearsCausedBy("context", Map.of()));
    }

    @Test
    void theQueryAndCanvasVerbsLeaveItLit() {
        assertEquals(0, clearsCausedBy("coverage", Map.of()));
        assertEquals(0, clearsCausedBy("series", Map.of("expr", "quotePublisher.spread")));
        // the canvas form of `open` (M48.7, folded in from the one-day `handoff` verb) changes no view
        assertEquals(0, clearsCausedBy("open", Map.of("posture", "authoring")));
        assertEquals(0, clearsCausedBy("open", Map.of("close", "handoff")));
        assertEquals(0, clearsCausedBy("flag", Map.of("recordIndex", 3)));
    }

    @Test
    void aNewSpotlightDoesNotFirstPutItselfOut_theAdapterReplacesTheOldOne() {
        assertEquals(0, clearsCausedBy("spotlight", Map.of("target", "status")));
    }
}
