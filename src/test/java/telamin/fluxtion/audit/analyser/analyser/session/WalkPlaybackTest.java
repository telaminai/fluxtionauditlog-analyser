package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S2 (spec-spotlight-walks.md §3.8; owner: "the logic, transitions and state mutation in the orchestrator in a
 * single place") — walk playback on the REAL generated processor. Facts go in; what the node asks the adapter to do,
 * and what the snapshot publishes, come out. Nothing here has a frame.
 */
class WalkPlaybackTest {

    private static SessionDriver opened(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/f.yaml");
        return d;
    }

    private static WalkPlaybackState walk(SessionDriver d) {
        return d.snapshot().walkPlayback();
    }

    private static SessionEvents.WalkTargetState target(int n, boolean available) {
        return new SessionEvents.WalkTargetState(n, "records:row:" + n, "c" + n, "CURRENT", available,
                available ? "" : "hidden");
    }

    private static void prepared(SessionDriver d, List<SessionEvents.WalkTargetState> targets) {
        WalkPlaybackState w = walk(d);
        d.post(new SessionEvents.WalkStepPrepared(w.ticket(), d.snapshot().logGeneration(), targets, ""));
    }

    @Test
    @DisplayName("play asks for step 1's view, and the snapshot says it is preparing that step")
    void playAsksForTheFirstStep() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        assertEquals(1, a.walkViews.size());
        assertEquals("tour", a.walkViews.get(0).walk());
        assertEquals(0, a.walkViews.get(0).step());
        assertTrue(walk(d).showing());
        assertEquals("PREPARING", walk(d).phase());
        assertEquals(a.walkViews.get(0).ticket(), walk(d).ticket(), "the view request carries the node's ticket");
    }

    @Test
    @DisplayName("a prepared step lights only its available targets, and says how much it showed")
    void preparedLightsTheAvailableTargets() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        prepared(d, List.of(target(1, true), target(2, false), target(3, true)));
        assertEquals("PARTLY_SHOWN", walk(d).phase());
        assertEquals(1, a.walkLights.size());
        assertEquals(List.of(1, 3), a.walkLights.get(0).targets().stream().map(SessionEvents.WalkTargetState::n).toList(),
                "the unavailable target keeps its number and is not lit");
        assertEquals(3, walk(d).targets().size(), "and it is still listed, with its reason");
    }

    @Test
    @DisplayName("a step with nothing available is NOT_SHOWN and lights nothing")
    void nothingAvailableLightsNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 1, "test"));
        prepared(d, List.of(target(1, false)));
        assertEquals("NOT_SHOWN", walk(d).phase());
        assertEquals(0, a.walkLights.size());
    }

    @Test
    @DisplayName("W-A2 (node half): Next and Back move one step, each asking for its view; the ends are refused")
    void navigateAndBoundaries() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 2, "test"));
        d.post(new SessionEvents.WalkNavigated(-1));
        assertEquals(1, a.walkViews.size(), "Back on the first step asks for nothing");
        assertEquals("this is the first step", walk(d).reason());
        d.post(new SessionEvents.WalkNavigated(1));
        assertEquals(1, a.walkViews.get(1).step());
        d.post(new SessionEvents.WalkNavigated(1));
        assertEquals(2, a.walkViews.size(), "Next on the last step asks for nothing");
        d.post(new SessionEvents.WalkNavigated(-1));
        assertEquals(0, a.walkViews.get(2).step());
    }

    @Test
    @DisplayName("R8: a preparation from a superseded step cannot light anything")
    void aStalePreparationIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        long first = walk(d).ticket();
        d.post(new SessionEvents.WalkNavigated(1));                     // rapid Next before step 1 was ready
        d.post(new SessionEvents.WalkStepPrepared(first, d.snapshot().logGeneration(), List.of(target(1, true)), ""));
        assertEquals(0, a.walkLights.size(), "step 1's late preparation must not light over step 2");
        assertEquals("PREPARING", walk(d).phase());
        assertEquals(1, walk(d).step());
    }

    @Test
    @DisplayName("an ended walk remembers its step, and Play from step N resumes there")
    void endRemembersTheStep() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        d.post(new SessionEvents.WalkNavigated(1));
        d.post(new SessionEvents.WalkEndRequested("a press outside the strip"));
        assertFalse(walk(d).showing());
        assertEquals(1, a.walkEnds.size());
        assertEquals(1, walk(d).lastShown().get("tour"));
        assertTrue(walk(d).reason().contains("a press outside the strip"));
        d.post(new SessionEvents.WalkPlayRequested("tour", -1, 3, "resume"));
        assertEquals(1, a.walkViews.get(a.walkViews.size() - 1).step(), "resumed at step 2");
    }

    @Test
    @DisplayName("W-A8: a new log generation ends the walk")
    void aNewGenerationEndsTheWalk() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        SessionFixtures.openLog(d, a, "/logs/g.yaml");
        assertFalse(walk(d).showing());
        assertEquals(1, a.walkEnds.size());
        assertTrue(walk(d).reason().contains("another log was opened"), walk(d).reason());
    }

    @Test
    @DisplayName("W-A8: closing the log ends the walk, though a close does not advance the generation")
    void aCloseEndsTheWalk() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        // what MainFrame.closeLog reports: the close as a fact about this generation (as SessionFactsTest drives it)
        d.post(new SessionEvents.LogCleared(d.snapshot().logGeneration()));
        assertFalse(d.snapshot().logOpen(), "control: the log is closed");
        assertFalse(walk(d).showing());
        assertTrue(walk(d).reason().contains("the log was closed"), walk(d).reason());
    }

    @Test
    @DisplayName("W-A8: a changed log identity, same generation, re-resolves the showing step's targets")
    void anIdentityChangeReResolves() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        prepared(d, List.of(target(1, true)));
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogIdentityObserved(g, "REPLACEMENT", "the file was replaced"));
        assertTrue(walk(d).showing(), "a changed identity is not a new log: the walk stays");
        assertEquals(1, a.walkResolves.size());
        assertTrue(a.walkResolves.get(0).why().contains("REPLACEMENT"));
        assertEquals(walk(d).ticket(), a.walkResolves.get(0).ticket(), "under a new ticket, so the old step's facts are stale");
    }

    @Test
    @DisplayName("W-A8: a late preparation after a log switch lights nothing")
    void aLatePreparationAfterALogSwitchLightsNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        long ticket = walk(d).ticket(), oldGeneration = d.snapshot().logGeneration();
        SessionFixtures.openLog(d, a, "/logs/g.yaml");
        d.post(new SessionEvents.WalkStepPrepared(ticket, oldGeneration, List.of(target(1, true)), ""));
        assertEquals(0, a.walkLights.size());
    }

    @Test
    @DisplayName("a refused view is NOT_SHOWN with its reason, and lights nothing")
    void aRefusedViewIsNotShown() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        a.refuseWalkViews = true;
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("tour", 0, 3, "test"));
        assertEquals("NOT_SHOWN", walk(d).phase());
        assertEquals("refused by the fake", walk(d).reason());
        assertEquals(0, a.walkLights.size());
    }

    @Test
    @DisplayName("a walk with no steps, or a step past its end, is refused and nothing is asked")
    void badPlayRequestsAreRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested("empty", 0, 0, "test"));
        d.post(new SessionEvents.WalkPlayRequested("tour", 5, 3, "test"));
        assertEquals(0, a.walkViews.size());
        assertFalse(walk(d).showing());
        assertTrue(walk(d).reason().contains("no step 6"), walk(d).reason());
    }
}
