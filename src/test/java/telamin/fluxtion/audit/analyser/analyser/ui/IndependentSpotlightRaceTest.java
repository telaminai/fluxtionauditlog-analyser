package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.BundleProvenance;
import telamin.fluxtion.audit.analyser.analyser.session.CaptureState;
import telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState;
import telamin.fluxtion.audit.analyser.analyser.session.ListenerState;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.PermissionState;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.ProposalState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot;
import telamin.fluxtion.audit.analyser.analyser.session.StartPageState;
import telamin.fluxtion.audit.analyser.analyser.session.StarterState;
import telamin.fluxtion.audit.analyser.analyser.session.StatusLineState;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stranger authoring trial, 2026-10-25 (run h1, acts 37-38): an agent opened a fresh export, got
 * {@code logLoading: true}, and lit a topology node "while it finishes" — the record it meant to show was never lit,
 * and nothing in the reply said a record target was not ready. Now a record target sent while the session has a log
 * open in flight is refused with a clear reason, touching nothing; any other target still lights, and its reply says
 * what was lit is not a record.
 *
 * <p>The in-flight fact is the session's published decision ({@code SessionSnapshot.pending}); the verb only reads it.
 * Witness: make {@code ActionExecutor.logOpenInFlight()} return false — the three loading cases below go red.
 */
class IndependentSpotlightRaceTest {

    /** The executor with an app double that records each call that reaches the screen. */
    private static final class Rig {
        final List<String> reached = new ArrayList<>();
        final ActionExecutor executor;
        final java.util.concurrent.CountDownLatch prepared = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CompletableFuture<ActionResult> preparation = new java.util.concurrent.CompletableFuture<>();
        volatile Runnable reveal;

        Rig(boolean loading) {
            HeapLogStore store = new HeapLogStore("");
            GraphTabs tabs = new GraphTabs();
            FilterState filter = new FilterState();
            tabs.bind(store, filter);
            executor = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
            AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                    new Class<?>[]{AppControl.class}, (proxy, method, args) -> {
                        reached.add(method.getName());
                        if (method.getName().equals("prepareJavaSpotlight")) {
                            reveal = (Runnable) args[1]; prepared.countDown(); return preparation;
                        }
                        if (method.getName().equals("spotlight")) {
                            return ActionResult.ok("spotlight", "spotlight", Map.of("lit", List.of(Map.of("n", 1))));
                        }
                        Class<?> type = method.getReturnType();
                        if (type == ActionResult.class) return ActionResult.error("the double does nothing");
                        if (type == boolean.class) return false;
                        if (type == List.class) return List.of();
                        return null;
                    });
            executor.bind(null, app);
            executor.bindSessionSnapshot(() -> snapshot(loading));
        }

        ActionResult spotlight(Map<String, Object> params) {
            return executor.render("spotlight", params);
        }
    }

    /** {@link SessionSnapshot#EMPTY}, except for whether a log open is in flight. */
    static SessionSnapshot snapshot(boolean pending) {
        return new SessionSnapshot(false, null, 0, 0, 0, false, null, null, 0, null, null, pending, null, null, null,
                null, null, null, null, null, false, null, null, false, WalkPlaybackState.IDLE, CaptureState.IDLE,
                AssistantState.IDLE, BundleProvenance.NONE, LocalAgentState.IDLE, StartPageState.IDLE,
                ProposalState.NONE, ExampleDownloadState.IDLE, PermissionState.NONE, ProfileState.NONE,
                ListenerState.NONE, 0, AiSetupState.IDLE, null, StarterState.IDLE, StatusLineState.NONE);
    }

    @Test
    void loadingBeginsDuringJavaPreparation() throws Exception {
        Rig rig = new Rig(false);
        var loading = new java.util.concurrent.atomic.AtomicBoolean(false);
        rig.executor.bindSessionSnapshot(() -> snapshot(loading.get()));
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future = pool.submit(() -> rig.spotlight(Map.of("targets", List.of("records", "source:java:com.acme.Node"))));
            assertTrue(rig.prepared.await(5, java.util.concurrent.TimeUnit.SECONDS));
            loading.set(true);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { rig.reveal.run(); rig.preparation.complete(ActionResult.ok("spotlight", "spotlight", Map.of())); }
                catch (RuntimeException e) { rig.preparation.complete(ActionResult.error(e.getMessage())); }
            });
            var result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(result.ok());
            assertTrue(result.error().contains(SpotlightTarget.LOG_STILL_LOADING), result.error());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void loadingBeginsWhileSpotlightWaitsForEdt() throws Exception {
        Rig rig = new Rig(false);
        var loading = new java.util.concurrent.atomic.AtomicBoolean(false);
        var sampled = new java.util.concurrent.CountDownLatch(1);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        rig.executor.bindSessionSnapshot(() -> {
            boolean value = loading.get(); sampled.countDown(); return snapshot(value);
        });
        javax.swing.SwingUtilities.invokeLater(() -> {
            entered.countDown();
            try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future = pool.submit(() -> rig.spotlight(Map.of("target", "records")));
            assertTrue(sampled.await(5, java.util.concurrent.TimeUnit.SECONDS));
            loading.set(true);
            release.countDown();
            ActionResult result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(result.ok(), "record spotlight reached app while pending=true: " + rig.reached);
            assertFalse(rig.reached.contains("spotlight"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
