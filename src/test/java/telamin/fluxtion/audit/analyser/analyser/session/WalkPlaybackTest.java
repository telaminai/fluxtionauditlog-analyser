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

    /** A walk of {@code steps} status steps — the play fact carries the definition (review PR57 R6). */
    static telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec walkOf(String name, int steps) {
        java.util.List<telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Step> list = new java.util.ArrayList<>();
        for (int i = 0; i < steps; i++) {
            list.add(new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Step("step " + (i + 1),
                    telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.View.NONE,
                    List.of(new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Target("status", "", null))));
        }
        return new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec(name, "", "person", "", "", null, List.of(),
                list, java.util.Map.of());
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
        assertEquals(1, a.walkViews.size());
        assertEquals("tour", a.walkViews.get(0).walk().name());
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 1), 0, "test"));
        prepared(d, List.of(target(1, false)));
        assertEquals("NOT_SHOWN", walk(d).phase());
        // review PR57 R1: the node always states what is lit — here, nothing — so a previous light is taken down
        assertEquals(1, a.walkLights.size(), "one light effect, stating the lit set");
        assertEquals(List.of(), a.walkLights.get(0).targets(), "and it names no target: nothing is lit");
    }

    @Test
    @DisplayName("W-A2 (node half): Next and Back move one step, each asking for its view; the ends are refused")
    void navigateAndBoundaries() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 2), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
        d.post(new SessionEvents.WalkNavigated(1));
        d.post(new SessionEvents.WalkEndRequested("a press outside the strip"));
        assertFalse(walk(d).showing());
        assertEquals(1, a.walkEnds.size());
        assertEquals(1, walk(d).lastShown().get("tour"));
        assertTrue(walk(d).reason().contains("a press outside the strip"));
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), -1, "resume"));
        assertEquals(1, a.walkViews.get(a.walkViews.size() - 1).step(), "resumed at step 2");
    }

    @Test
    @DisplayName("W-A8: a new log generation ends the walk")
    void aNewGenerationEndsTheWalk() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
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
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 0, "test"));
        assertEquals("NOT_SHOWN", walk(d).phase());
        assertEquals("refused by the fake", walk(d).reason());
        assertEquals(0, a.walkLights.size());
    }

    @Test
    @DisplayName("a walk with no steps, or a step past its end, is refused and nothing is asked")
    void badPlayRequestsAreRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("empty", 0), 0, "test"));
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 3), 5, "test"));
        assertEquals(0, a.walkViews.size());
        assertFalse(walk(d).showing());
        assertTrue(walk(d).reason().contains("no step 6"), walk(d).reason());
    }

    @Test
    @DisplayName("review PR57 R6: the node decides what a definition change means — rename keeps, replace and delete end")
    void aDefinitionChangeIsTheNodesDecision() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        var tour = walkOf("tour", 3);
        d.post(new SessionEvents.WalkPlayRequested(0, tour, 0, "test"));
        assertEquals(tour, walk(d).definition(), "the published state carries the frozen definition");

        d.post(new SessionEvents.WalkDefinitionChanged("tour", tour, null));
        assertTrue(walk(d).showing(), "a save that changes no step changes nothing shown");

        d.post(new SessionEvents.WalkDefinitionChanged("tour", null, "trip"));
        assertTrue(walk(d).showing(), "a rename keeps the frozen version");
        assertEquals("trip", walk(d).walk());
        assertEquals("trip", walk(d).definition().name());

        d.post(new SessionEvents.WalkDefinitionChanged("tour", null, null));
        assertTrue(walk(d).showing(), "a change to ANOTHER walk (the old name) is not this one's");

        d.post(new SessionEvents.WalkDefinitionChanged("trip", walkOf("trip", 1), null));
        assertFalse(walk(d).showing(), "a replacement with different steps ends the showing");
        assertTrue(walk(d).reason().contains("changed"), walk(d).reason());

        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("again", 2), 0, "test"));
        d.post(new SessionEvents.WalkDefinitionChanged("again", null, null));
        assertFalse(walk(d).showing(), "a delete ends it");
        assertTrue(walk(d).reason().contains("deleted"), walk(d).reason());
    }

    @Test
    @DisplayName("review PR57 R6: navigation asks for steps of the FROZEN definition, whatever config says meanwhile")
    void navigationUsesTheFrozenDefinition() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        var tour = walkOf("tour", 3);
        d.post(new SessionEvents.WalkPlayRequested(0, tour, 0, "test"));
        prepared(d, List.of(target(1, true)));
        d.post(new SessionEvents.WalkNavigated(1));
        var asked = a.walkViews.get(a.walkViews.size() - 1);
        assertSame(tour, asked.walk(), "the effect carries the very definition the play fact carried");
        assertEquals(1, asked.step());
    }

    // ---- review PR57 R5/R1, second round: what "current" can mean on a log nobody has re-checked ----------------

    /** A one-step walk whose target rests on the log's contents — a record basis (§3.5). */
    private static telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec recordWalk() {
        var target = new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Target("records:row:1", "here",
                new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Basis("record", "sha256:abc", "HeapLogStore"));
        return new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec("tour", "", "person", "", "", null, List.of(),
                List.of(new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Step("one",
                        telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.View.NONE, List.of(target))),
                java.util.Map.of());
    }

    private static final String CAVEAT = "has not been re-checked since it was read";

    @Test
    @DisplayName("A record step on a log with NO identity verdict says what 'current' does and does not mean")
    void anUnassessedLogIsSaidSo() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        assertNull(d.snapshot().logIdentity(), "precondition: nothing has assessed the file behind this log");
        d.post(new SessionEvents.WalkPlayRequested(0, recordWalk(), 0, "test"));

        prepared(d, List.of(new SessionEvents.WalkTargetState(1, "records:row:1", "here", "CURRENT", true, "")));

        assertEquals("SHOWN", walk(d).phase(), "the step is shown: this is a caveat, not a refusal");
        assertTrue(walk(d).reason().contains(CAVEAT),
                "a bare 'current' reads as 'verified', and nothing here has looked at the file: " + walk(d).reason());
    }

    @Test
    @DisplayName("Once the file HAS been assessed, the caveat goes — it states a gap, not a mood")
    void anAssessedLogCarriesNoCaveat() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "VERIFIED", "the same file"));
        d.post(new SessionEvents.WalkPlayRequested(0, recordWalk(), 0, "test"));

        prepared(d, List.of(new SessionEvents.WalkTargetState(1, "records:row:1", "here", "CURRENT", true, "")));

        assertFalse(walk(d).reason().contains(CAVEAT), walk(d).reason());
    }

    @Test
    @DisplayName("A structural step claims nothing about the log's contents, so it is not caveated")
    void aStructuralStepIsNotCaveated() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, walkOf("tour", 1), 0, "test"));

        prepared(d, List.of(new SessionEvents.WalkTargetState(1, "status", "", "CURRENT", true, "")));

        assertFalse(walk(d).reason().contains(CAVEAT),
                "a status target rests on no basis — caveating it would make the warning meaningless: " + walk(d).reason());
    }

    @Test
    @DisplayName("The caveat joins the preparation's own note rather than replacing it")
    void theCaveatJoinsTheNote() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, recordWalk(), 0, "test"));
        WalkPlaybackState w = walk(d);

        d.post(new SessionEvents.WalkStepPrepared(w.ticket(), d.snapshot().logGeneration(),
                List.of(new SessionEvents.WalkTargetState(1, "records:row:1", "here", "CURRENT", true, "")),
                "a chart did not finish drawing in 5 s"));

        assertTrue(walk(d).reason().startsWith("a chart did not finish drawing in 5 s; "), walk(d).reason());
        assertTrue(walk(d).reason().contains(CAVEAT), walk(d).reason());
    }
}
