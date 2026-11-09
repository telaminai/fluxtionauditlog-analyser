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
class SpotlightWhileLoadingTest {

    /** The executor with an app double that records each call that reaches the screen. */
    private static final class Rig {
        final List<String> reached = new ArrayList<>();
        final ActionExecutor executor;

        Rig(boolean loading) {
            HeapLogStore store = new HeapLogStore("");
            GraphTabs tabs = new GraphTabs();
            FilterState filter = new FilterState();
            tabs.bind(store, filter);
            executor = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
            AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                    new Class<?>[]{AppControl.class}, (proxy, method, args) -> {
                        reached.add(method.getName());
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
    void aRecordRowWhileTheLogLoadsIsRefusedAndTouchesNothing() {
        Rig rig = new Rig(true);
        ActionResult r = rig.spotlight(Map.of("target", "records:row:3", "caption", "the alert"));
        assertFalse(r.ok());
        assertTrue(r.error().startsWith("'records:row:3': " + SpotlightTarget.LOG_STILL_LOADING), r.error());
        assertTrue(r.error().contains("send this spotlight again"), r.error());
        assertEquals(List.of(), rig.reached, "refused before any reveal: no goto, no spotlight, no clear");
    }

    @Test
    void everyRecordFamilyIsRefused_includingOneMemberOfASet() {
        for (String target : List.of("records", "records:row:0", "detail", "detail:node:riskCheck")) {
            Rig rig = new Rig(true);
            ActionResult r = rig.spotlight(Map.of("target", target));
            assertFalse(r.ok(), target);
            assertTrue(r.error().contains(SpotlightTarget.LOG_STILL_LOADING), target + ": " + r.error());
        }
        Rig rig = new Rig(true);
        ActionResult set = rig.spotlight(Map.of("targets", List.of("topology:node:riskCheck", "detail:node:riskCheck")));
        assertFalse(set.ok(), "a set is all or nothing: the node must not light without its record");
        assertEquals(List.of(), rig.reached);
    }

    @Test
    void aNodeLightsWhileTheLogLoads_andTheReplySaysItIsNotTheRecord() {
        Rig rig = new Rig(true);
        ActionResult r = rig.spotlight(Map.of("target", "topology:node:tradeAverager", "caption", "dispatch here"));
        assertTrue(r.ok(), String.valueOf(r.error()));
        assertTrue(rig.reached.contains("spotlight"));
        assertEquals(SpotlightTarget.LOADING_NOTE, r.payload().get("logLoading"));
        assertTrue(r.payload().containsKey("lit"), "the lit set is passed through unchanged");
    }

    @Test
    void withNoLoadInFlightNothingChanges() {
        Rig rig = new Rig(false);
        ActionResult node = rig.spotlight(Map.of("target", "topology:node:tradeAverager"));
        assertTrue(node.ok());
        assertNull(node.payload().get("logLoading"));
        // an empty log refuses the row for its own reason, not the loading one
        ActionResult row = new Rig(false).spotlight(Map.of("target", "records:row:3"));
        assertFalse(row.ok());
        assertFalse(row.error().contains(SpotlightTarget.LOG_STILL_LOADING), row.error());
    }

    @Test
    void puttingASpotlightOutIsNeverRefused() {
        Rig rig = new Rig(true);
        rig.spotlight(Map.of("clear", true, "target", "records:row:3"));
        assertTrue(rig.reached.contains("spotlight"), "clear reaches the screen while loading");
    }

    @Test
    void thePureRule() {
        var row = SpotlightTarget.requests(Map.of("target", "records:row:1"));
        assertNull(SpotlightTarget.loadingRefusal(row, false));
        assertTrue(SpotlightTarget.loadingRefusal(row, true).contains(SpotlightTarget.LOG_STILL_LOADING));
        assertNull(SpotlightTarget.loadingRefusal(SpotlightTarget.requests(Map.of("target", "tab:topology")), true));
        assertEquals("log still loading; record targets not ready", SpotlightTarget.LOG_STILL_LOADING,
                "the words a client is told to expect (McpBridge.FIRST_MOVE, the trial report)");
    }
}
