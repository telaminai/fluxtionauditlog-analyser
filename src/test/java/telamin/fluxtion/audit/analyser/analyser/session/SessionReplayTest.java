package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44 §9 replay acceptance — <b>against the processor, not against the UI.</b>
 *
 * <p>These are the rules M35 spent eleven slices establishing, and until now they could only be checked
 * by running the application: they lived in {@code MainFrame} listeners. Each one is now a fixed
 * sequence of typed facts with an expected decision, an expected state and an expected set of things
 * that actually happened.
 *
 * <p>Slice 1 covers the four journeys that turn on {@link TransitionKind}, plus the two properties that
 * make the record trustworthy: a stale result changes nothing, and the driver refuses to run two
 * operations at once.
 */
class SessionReplayTest {

    private static final String A = "/projects/alpha.properties";
    private static final String B = "/projects/beta.properties";

    @Test
    @DisplayName("replay 1 — an explicit switch with a log and graph open closes both")
    void explicitSwitchIsASessionBoundary() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A).withProfile(B);
        SessionDriver driver = new SessionDriver(adapter);
        SessionProcessor processor = driver.processor();

        open(driver, A, TransitionKind.STARTUP_ACTIVATION);
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        driver.submit(new SessionEvents.GraphObserved(true, "/graphs/run.graphml", "OPENED"));
        assertTrue(processor.openLog.isOpen(), "precondition: a log is open");
        assertTrue(processor.openGraph.isOpen(), "precondition: a graph is open");
        adapter.forget();

        open(driver, B, TransitionKind.EXPLICIT_SWITCH);

        // The decision asked for both closes...
        assertEquals(1, adapter.countOf(SessionEffects.CloseLogEffect.class));
        assertEquals(1, adapter.countOf(SessionEffects.CloseGraphEffect.class));
        // ...and this is the part that proves they happened rather than were merely requested.
        assertTrue(adapter.logClosed, "the log actually closed");
        assertTrue(adapter.graphClosed, "the graph actually closed");
        assertFalse(processor.openLog.isOpen());
        assertFalse(processor.openGraph.isOpen());
        assertEquals(B, processor.activeProject.profilePath());
        assertTrue(adapter.lastStatus.contains("a project is a session boundary"), adapter.lastStatus);
    }

    @Test
    @DisplayName("replay 5 — adopting the project offered FOR the open log keeps that log")
    void adoptionForAnOpenLogDoesNotCloseIt() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(B);
        SessionDriver driver = new SessionDriver(adapter);
        SessionProcessor processor = driver.processor();

        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        adapter.forget();

        // Same surface, same state as replay 1, opposite rule — because the KIND is different. This is
        // the exception that would have been lost by inferring intent from the surface or from what
        // happens to be open.
        open(driver, B, TransitionKind.ADOPT_FOR_OPEN_LOG);

        assertEquals(0, adapter.countOf(SessionEffects.CloseLogEffect.class),
                "closing here would destroy the log that caused the offer");
        assertFalse(adapter.logClosed);
        assertTrue(processor.openLog.isOpen());
        assertEquals(B, processor.activeProject.profilePath());
    }

    @Test
    @DisplayName("replay 6 — a failed load closes nothing and changes nothing")
    void aFailedLoadIsNotATransition() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);
        SessionProcessor processor = driver.processor();

        open(driver, A, TransitionKind.STARTUP_ACTIVATION);
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        adapter.forget();

        open(driver, "/projects/missing.properties", TransitionKind.EXPLICIT_SWITCH);

        assertEquals(0, adapter.countOf(SessionEffects.CloseLogEffect.class));
        assertFalse(adapter.logClosed, "a bad path must not cost you the log you were reading");
        assertTrue(processor.openLog.isOpen());
        assertEquals(A, processor.activeProject.profilePath(), "the active project is unchanged");
        assertNotNull(adapter.lastWarning);
        assertTrue(adapter.lastWarning.contains("missing.properties"), adapter.lastWarning);
    }

    @Test
    @DisplayName("replay 6b — an adapter that THROWS is a failure, not a crash")
    void aThrowingAdapterBecomesATypedFailure() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A).withProfile(B);
        SessionDriver driver = new SessionDriver(adapter);

        open(driver, A, TransitionKind.STARTUP_ACTIVATION);
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        adapter.loadThrows = true;
        adapter.forget();

        open(driver, B, TransitionKind.EXPLICIT_SWITCH);

        assertFalse(adapter.logClosed);
        assertEquals(A, driver.processor().activeProject.profilePath());
        assertTrue(driver.auditSink().matching("disk went away").size() >= 1,
                "the reason has to reach the record, or the failure is invisible");
    }

    @Test
    @DisplayName("replay 7 — reopening the already-active project is a recorded no-op")
    void reopeningTheActiveProjectDoesNothing() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);

        open(driver, A, TransitionKind.EXPLICIT_SWITCH);
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        adapter.forget();

        open(driver, A, TransitionKind.EXPLICIT_SWITCH);

        assertEquals(0, adapter.performed.size(),
                "not even a load — re-applying an already-active project is work with a visible cost");
        assertTrue(driver.processor().openLog.isOpen());
        // Silence would be indistinguishable from a dropped request, so the no-op says so.
        assertFalse(driver.auditSink().matching("noOp").isEmpty(), "the no-op must be in the record");
    }

    @Test
    @DisplayName("replay 7b — ADOPT_FOR_OPEN_LOG is never a no-op, even for the active project")
    void adoptionIsNotSubjectToTheNoOpRule() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);

        open(driver, A, TransitionKind.EXPLICIT_SWITCH);
        adapter.forget();

        open(driver, A, TransitionKind.ADOPT_FOR_OPEN_LOG);

        assertEquals(1, adapter.countOf(SessionEffects.LoadProfileEffect.class),
                "adoption means 'make this the project for the log I just opened' — it is a real act");
    }

    @Test
    @DisplayName("replay 9 — a result for an operation that is not in flight changes nothing")
    void aStaleResultIsRefusedAndRecorded() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);
        SessionProcessor processor = driver.processor();

        open(driver, A, TransitionKind.EXPLICIT_SWITCH);
        assertEquals(A, processor.activeProject.profilePath());

        // An outcome from an operation nobody is waiting for — the shape a background or overlapping
        // operation would take if the driver ever stopped being single-in-flight.
        driver.submit(new SessionEvents.ProfileApplied(9999, "/projects/ghost.properties", "ghost"));

        assertEquals(A, processor.activeProject.profilePath(), "state must not follow a stale result");
        assertFalse(driver.auditSink().matching("staleResult").isEmpty(),
                "and it must be visible — silently dropping it is the same class of defect");
    }

    @Test
    @DisplayName("the driver refuses to run two operations at once")
    void singleInFlightIsEnforced() {
        // An adapter that tries to start a second operation from inside the first. Without the guard
        // this interleaves two cycles and the audit record cannot tell them apart afterwards.
        SessionDriver[] holder = new SessionDriver[1];
        SessionDriver driver = new SessionDriver(effect -> {
            holder[0].submit(new SessionEvents.LogObserved(true, "/logs/other.yaml", "DECLARED"));
            return new SessionEvents.StatusShown(effect.opId(), "never gets here");
        });
        holder[0] = driver;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> open(driver, A, TransitionKind.EXPLICIT_SWITCH));
        assertTrue(thrown.getMessage().contains("single-in-flight"), thrown.getMessage());
    }

    @Test
    @DisplayName("an adapter that answers nothing is a failure, not a silent success")
    void anUnansweredEffectIsRecordedAsAFailure() {
        SessionDriver driver = new SessionDriver(effect -> null);

        open(driver, A, TransitionKind.EXPLICIT_SWITCH);

        assertNull(driver.processor().activeProject.profilePath());
        assertFalse(driver.auditSink().matching("must be answered").isEmpty(),
                "an effect with no result is a hole in the record and has to say so");
    }

    private static void open(SessionDriver driver, String path, TransitionKind kind) {
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), path, kind, "test"));
    }
}
