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
 * <p><b>Changed by M68.4 (spec-evidence-integrity D-E3, "what a refusal preserves").</b> This test used to assert that
 * the adapter was told to clear BEFORE the verb ran, whether or not it then succeeded, on the reasoning that the caller
 * had asked for another view. D-E3 names that ordering a defect: a REFUSED call leaves the view as it was, so the
 * spotlight that pointed into it is still pointing at the right thing, and putting it out destroyed context the caller
 * was relying on. Now: a view-changing verb that SUCCEEDS puts it out; one that is refused leaves it lit. The
 * per-verb cases keep their original property — removing a verb from {@link SpotlightTarget#VIEW_CHANGING_VERBS}
 * turns exactly that verb's case red — through {@link ActionExecutor#putsOutSpotlight}, the policy render uses.
 */
class SpotlightEndsWhenTheViewChangesTest {

    private static final ActionResult OK = ActionResult.ok("verb", "applied", Map.of());
    private static final ActionResult REFUSED = ActionResult.error("refused");

    /** The per-verb property, through the policy render applies: succeeded → out, refused → lit. */
    private static int policy(String verb, Map<String, Object> params) {
        assertEquals(false, ActionExecutor.putsOutSpotlight(verb, params, REFUSED), verb + ": a refusal must leave it lit");
        return ActionExecutor.putsOutSpotlight(verb, params, OK) ? 1 : 0;
    }

    private static int clearsCausedBy(String verb, Map<String, Object> params) {
        return clearsCausedBy(verb, params, false);
    }

    /** Through the real {@code render}; the double's app calls fail, or succeed when {@code appSucceeds}. */
    private static int clearsCausedBy(String verb, Map<String, Object> params, boolean appSucceeds) {
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
                    if (type == ActionResult.class) return appSucceeds
                            ? ActionResult.ok("open", "log", Map.of("path", "/run.yaml", "loading", true))
                            : ActionResult.error("the double does nothing");
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
        assertEquals(1, policy("open", Map.of("log", "/run.yaml")));
        assertEquals(1, clearsCausedBy("open", Map.of("log", "/run.yaml"), true), "a real open that succeeded");
    }

    @Test
    void filterPutsItOut() {
        assertEquals(1, policy("filter", Map.of("text", "breach")));
    }

    @Test
    void gotoPutsItOut() {
        assertEquals(1, policy("goto", Map.of("recordIndex", 3)));
    }

    @Test
    void graphPutsItOut() {
        assertEquals(1, policy("graph", Map.of("name", "Spread", "keys", List.of("quotePublisher.spread"))));
    }

    @Test
    void topologyPutsItOut() {
        assertEquals(1, policy("topology", Map.of("select", "priceListener")));
    }

    // ---- M68.4 (D-E3): a refused call leaves the spotlight on the view it did not change ------------------

    @Test
    void aRefusedCallLeavesItLit() {
        // witness: render back to clearing before renderVerb runs
        assertEquals(0, clearsCausedBy("open", Map.of("log", "/run.yaml")), "the open was refused");
        assertEquals(0, clearsCausedBy("goto", Map.of("recordIndex", 3)), "no such record in an empty log");
        assertEquals(0, clearsCausedBy("topology", Map.of("select", "priceListener")), "no topology to drive");
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
