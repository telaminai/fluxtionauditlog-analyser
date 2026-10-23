package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M79-3 (spec-first-minute §8.1, rule 9): the open log's file observation is the session's. The {@code logObservation}
 * node decides WHEN the file is observed, the adapter observes and answers, and the snapshot publishes one value for
 * {@code context}, the Project panel and the evidence chip. Driven through the real generated processor.
 */
class LogObservationTest {

    private static List<SessionEffects.ObserveLogFileEffect> observes(FakeSessionAdapter a) {
        return a.performed.stream().filter(SessionEffects.ObserveLogFileEffect.class::isInstance)
                .map(SessionEffects.ObserveLogFileEffect.class::cast).toList();
    }

    @Test
    @DisplayName("Nothing is observed until a surface says it is due; then the answer is published for that generation")
    void observedOnRequestAndPublished() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        assertEquals(0, observes(a).size(), "an open alone observes nothing: the store is not installed yet");
        assertEquals(LogObservationState.NONE, d.snapshot().logObservation());
        d.post(new SessionEvents.LogFileObservationDue(g, "installed"));
        assertEquals(1, observes(a).size());
        assertEquals(g, observes(a).get(0).generation());
        var o = d.snapshot().logObservation();
        assertTrue(o.about(g));
        assertEquals("unchanged-metadata", o.state());
        assertEquals(1, o.freshnessRef());
    }

    @Test
    @DisplayName("Every due moment observes again; an unchanged answer publishes nothing, a changed one does")
    void eachReadObservesAndOnlyAChangePublishes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogFileObservationDue(g, "installed"));
        var first = d.snapshot();
        d.post(new SessionEvents.LogFileObservationDue(g, "context"));
        assertEquals(2, observes(a).size(), "a context read observes again: the file may have changed meanwhile");
        assertSame(first, d.snapshot(), "the same observation publishes no new snapshot");
        a.fileState = "changed-on-disk";
        a.fileRef = 2;
        d.post(new SessionEvents.LogFileObservationDue(g, "window focused"));
        assertEquals("changed-on-disk", d.snapshot().logObservation().state());
        assertEquals(2, d.snapshot().logObservation().freshnessRef());
    }

    @Test
    @DisplayName("A report or an answer for another generation is refused; a new log clears what was held")
    void staleIsRefusedAndANewLogClears() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogFileObservationDue(g + 7, "context"));
        assertEquals(0, observes(a).size(), "a due report about another generation asks for nothing");
        d.post(new SessionEvents.FileObservationObserved(0L, g + 7, "changed-on-disk", "b", 9, true, true));
        assertEquals(LogObservationState.NONE, d.snapshot().logObservation(), "an answer about another log is refused");
        d.post(new SessionEvents.LogFileObservationDue(g, "installed"));
        assertTrue(d.snapshot().logObservation().about(g));
        SessionFixtures.openLog(d, a, "/g.yaml");
        assertNotEquals(g, d.snapshot().logGeneration());
        assertEquals(LogObservationState.NONE, d.snapshot().logObservation(), "the previous log's observation is gone");
    }
}
