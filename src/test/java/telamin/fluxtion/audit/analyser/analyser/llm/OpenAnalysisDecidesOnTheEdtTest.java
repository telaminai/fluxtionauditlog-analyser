package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;
import telamin.fluxtion.audit.analyser.analyser.ui.GraphTabs;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTablePanel;

import javax.swing.SwingUtilities;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code open {analysis}} runs OFF the EDT on purpose — it waits for each asynchronous open to settle,
 * and a wait on the EDT blocks the completion it waits for. But the ignored-parameters decision it makes
 * afterwards is an event submitted to the session processor, and that driver is confined to the EDT
 * (M44.3 D-A1).
 *
 * <p>Released in 1.13.x: the decision was made on the caller's thread, so every saved-analysis recall
 * over the socket ran its steps and then failed with <i>"SessionDriver is confined to the thread that
 * created it … submit(OpenRequestReceived) was called on pool-1-thread-2"</i>. It was found only when
 * {@code tools/capture-conversations.py} was next re-run, because nothing else recalls an analysis
 * through the real frame. This pins the thread, which is the whole defect.
 */
class OpenAnalysisDecidesOnTheEdtTest {

    @Test
    void theDecisionIsSubmittedOnTheEdt_evenThoughTheRecallItselfIsNot() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test must call from a non-EDT thread, as the socket does");
        AtomicReference<Boolean> decidedOnEdt = new AtomicReference<>();
        AtomicReference<Boolean> recalledOnEdt = new AtomicReference<>();

        ActionExecutor ex = executor(recalledOnEdt);
        ex.bindIgnoredParameters(supplied -> {
            decidedOnEdt.set(SwingUtilities.isEventDispatchThread());   // what SessionDriver.submit would check
            return new IgnoredParameters().apply(supplied);
        });

        ActionResult result = ex.render("open", Map.of("analysis", "spread breach", "bind", Map.of("log", "/run.yaml")));

        assertTrue(result.ok(), String.valueOf(result.toMap()));
        assertEquals(Boolean.FALSE, recalledOnEdt.get(),
                "the recall stays off the EDT — putting it there is the deadlock its javadoc describes");
        assertEquals(Boolean.TRUE, decidedOnEdt.get(),
                "the decision is a session event and must reach the driver on the driver's thread");
    }

    private static ActionExecutor executor(AtomicReference<Boolean> recalledOnEdt) {
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
        AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                new Class<?>[]{AppControl.class}, (proxy, method, args) -> {
                    if (method.getName().equals("runAnalysis")) {
                        recalledOnEdt.set(SwingUtilities.isEventDispatchThread());
                        return ActionResult.ok("open", "analysis", Map.of("analysis", args[0], "completed", "0/0 steps"));
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == List.class) return List.of();
                    return null;
                });
        ex.bind(null, app);
        return ex;
    }
}
