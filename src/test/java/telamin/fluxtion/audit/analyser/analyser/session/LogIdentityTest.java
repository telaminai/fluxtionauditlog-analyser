package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** M68.5: the session carries Follow's verdict about the file, and a reopened log says why it was reopened. */
class LogIdentityTest {

    private static SessionDriver open(FakeSessionAdapter a, String path) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, path, "DECLARED", Set.of("a"), 1, 1, "TRACE");
        return d;
    }

    @Test
    @DisplayName("a replacement then a reopen at the SAME path: the new log states why it was reopened")
    void aReopenAfterReplacementSaysWhy() {
        // witness: OpenLog.onLogOpened without the reopenedAfterReplacement rule
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = open(a, "/f.yaml");
        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT", "content already read has changed on disk"));
        assertEquals("REPLACEMENT", d.snapshot().logIdentity());

        SessionFixtures.openLog(d, a, "/f.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");

        assertEquals("REOPENED", d.snapshot().logIdentity());
        assertTrue(d.snapshot().logIdentityReason().contains("content already read has changed on disk"),
                d.snapshot().logIdentityReason());
    }

    @Test
    @DisplayName("a different path, or an ordinary open, is not a reopen")
    void onlyTheSamePathIsAReopen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = open(a, "/f.yaml");
        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT", "shrank"));
        SessionFixtures.openLog(d, a, "/other.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        assertNull(d.snapshot().logIdentity());
    }

    @Test
    @DisplayName("a deliberate close ends it: a later open of the same path is not 'reopened because replaced'")
    void aCloseEndsTheStory() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = open(a, "/f.yaml");
        d.post(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT", "shrank"));
        d.post(new SessionEvents.LogCleared(d.snapshot().logGeneration()));
        SessionFixtures.openLog(d, a, "/f.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        assertNull(d.snapshot().logIdentity());
    }

    @Test
    @DisplayName("a verdict about an earlier generation is refused")
    void aStaleVerdictIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = open(a, "/f.yaml");
        long first = d.snapshot().logGeneration();
        SessionFixtures.openLog(d, a, "/g.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        d.post(new SessionEvents.LogIdentityObserved(first, "REPLACEMENT", "about /f.yaml"));
        assertNull(d.snapshot().logIdentity(), "a verdict about /f.yaml must not describe /g.yaml");
    }
}
