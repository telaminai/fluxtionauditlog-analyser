package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundle capture (convergence): the capture skill's decisions, now the {@code evidenceCapture} node's, on the
 * REAL generated processor through {@link SessionDriver}; only the adapter is a fake. Each EP-A1 refusal is its own
 * case, named, and writes nothing: no {@link SessionEffects.CaptureBundleEffect} is ever requested. The coherence rule
 * (a bundle written across a log change is refused and deleted) is provoked both ways. The frame half, where the
 * observations are made and the file work happens, is {@code EvidenceCaptureFrameTest}.
 */
class EvidenceCaptureTest {

    static final String PATH = "/exchange/breach.fexp";

    static SessionDriver opened(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/demo-quote-audit.yaml");
        return d;
    }

    /** A request as the frame makes it: a settled, single, unchanged file unless a case says otherwise. */
    static SessionEvents.BundleCaptureRequested request(long id, String identity, String freshness, boolean onePlainFile) {
        return new SessionEvents.BundleCaptureRequested(id, PATH, null, null, null, identity, freshness, onePlainFile, -1, "test");
    }

    static SessionEvents.BundleCaptureRequested ok(long id) {
        return request(id, null, "unchanged-metadata", true);
    }

    static CaptureState capture(SessionDriver d) {
        return d.snapshot().capture();
    }

    private static void refusedWritingNothing(SessionDriver d, FakeSessionAdapter a, long id, String naming) {
        CaptureState c = capture(d);
        assertEquals(id, c.answer().request(), "the answer is to this request");
        assertFalse(c.answer().accepted(), "refused: " + c.answer().reason());
        assertTrue(c.answer().reason().contains(naming), "refused BY NAME ('" + naming + "'): " + c.answer().reason());
        assertEquals(List.of(), a.captures, "and nothing was asked to be written");
    }

    @Test
    @DisplayName("EP-A1: no log open — refused by name, nothing written")
    void noLogIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.submit(ok(1));
        refusedWritingNothing(d, a, 1, "no log is open");
        assertEquals("REFUSED", capture(d).phase());
    }

    @Test
    @DisplayName("EP-A1: a load pending — refused by name, nothing written")
    void aPendingLoadIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        a.pendingOpens = true;
        d.submit(new SessionEvents.OpenLogRequested(d.nextOpId(), "/logs/next.yaml", null, null, false));
        d.submit(ok(2));
        refusedWritingNothing(d, a, 2, "a load is pending");
    }

    @Test
    @DisplayName("EP-A1: the file's identity replacement or unverified — refused by name, from the observation or the session")
    void anUnestablishedIdentityIsRefused() {
        for (String identity : List.of("replacement", "unverified")) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = opened(a);
            d.submit(request(3, identity, "unchanged-metadata", true));
            refusedWritingNothing(d, a, 3, "identity: " + identity);
        }
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(new SessionEvents.LogIdentityObserved(d.snapshot().logGeneration(), "REPLACEMENT", "DEMO: the file was replaced"));
        d.submit(ok(4));
        refusedWritingNothing(d, a, 4, "identity: replacement");
    }

    @Test
    @DisplayName("EP-A1: the file changed on disk — refused by name, nothing written")
    void aChangedFileIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(request(5, null, "changed-on-disk", true));
        refusedWritingNothing(d, a, 5, "changed on disk");

        FakeSessionAdapter live = new FakeSessionAdapter();
        SessionDriver f = opened(live);
        f.submit(new SessionEvents.FollowToggled(f.snapshot().logGeneration(), true));
        f.submit(request(5, null, "changed-on-disk", true));
        refusedWritingNothing(f, live, 5, "still growing under Follow");
        assertFalse(f.snapshot().capture().answer().reason().contains("reopen"), "reopening does not stop a producer");
    }

    @Test
    @DisplayName("EP-A1: not exactly one plain file — refused by name, nothing written")
    void notOnePlainFileIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(request(6, null, "unchanged-metadata", false));
        refusedWritingNothing(d, a, 6, "not one plain file");
    }

    @Test
    @DisplayName("an excerpt window that selects no records — observed by the frame — is refused by the node, by name")
    void anEmptyWindowIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(new SessionEvents.BundleCaptureRequested(16, PATH, null, 1L, 2L, null, "unchanged-metadata", true, 0, "test"));
        refusedWritingNothing(d, a, 16, "nothing to excerpt");
        d.submit(new SessionEvents.BundleCaptureRequested(17, PATH, null, 1L, 2L, null, "unchanged-metadata", true, 3, "test"));
        assertTrue(capture(d).answer().accepted(), "control: a window that selects records is accepted: " + capture(d).answer().reason());
    }

    @Test
    @DisplayName("a second capture while one is being written is refused, and the first is left alone")
    void oneCaptureAtATime() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(ok(7));
        d.submit(ok(8));
        assertFalse(capture(d).answer().accepted());
        assertTrue(capture(d).answer().reason().contains("already being written"), capture(d).answer().reason());
        assertEquals("WRITING", capture(d).phase(), "the first capture carries on");
        assertEquals(1, a.captures.size());
    }

    @Test
    @DisplayName("accepted: one write is asked for, under the generation the capture was decided in")
    void anAcceptedCaptureAsksForOneWrite() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long generation = d.snapshot().logGeneration();
        d.submit(ok(9));
        assertTrue(capture(d).answer().accepted(), capture(d).answer().reason());
        assertEquals("WRITING", capture(d).phase());
        assertEquals(1, a.captures.size());
        assertEquals(generation, a.captures.get(0).generation(), "the generation it was decided in travels with the write");
        assertEquals(PATH, a.captures.get(0).path());
    }

    @Test
    @DisplayName("written under the same log: it stands, with its identity and lines")
    void writtenUnderTheSameLogStands() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(ok(10));
        var e = a.captures.get(0);
        d.post(new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of("left out: DEMO")));
        assertEquals("WRITTEN", capture(d).phase());
        assertEquals("sha256:demo", capture(d).identity());
        assertEquals(List.of("left out: DEMO"), capture(d).lines());
        assertEquals(List.of(), a.deletes, "nothing is deleted");
    }

    @Test
    @DisplayName("PROVOKED: another log opened while it was written — refused, and the bundle is deleted")
    void anotherLogOpenedMeanwhileDeletesTheBundle() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(ok(11));
        var e = a.captures.get(0);
        SessionFixtures.openLog(d, a, "/logs/another.yaml");                 // the generation moves under the copy
        assertNotEquals(e.generation(), d.snapshot().logGeneration(), "control: the generation really moved");
        d.post(new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of()));
        assertEquals("REFUSED", capture(d).phase());
        assertTrue(capture(d).reason().startsWith("another log was opened while the bundle was being written"), capture(d).reason());
        assertNull(capture(d).identity(), "a refused bundle has no identity to hand anyone");
        assertEquals(1, a.deletes.size(), "and the file it wrote is deleted");
        assertEquals(PATH, a.deletes.get(0).path());
    }

    @Test
    @DisplayName("PROVOKED: the log closed while it was written — refused, and the bundle is deleted")
    void theLogClosedMeanwhileDeletesTheBundle() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(ok(12));
        var e = a.captures.get(0);
        d.post(new SessionEvents.LogCleared(d.snapshot().logGeneration()));
        assertFalse(d.snapshot().logOpen(), "control: the log really closed");
        d.post(new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of()));
        assertEquals("REFUSED", capture(d).phase());
        assertTrue(capture(d).reason().startsWith("the log was closed while the bundle was being written"), capture(d).reason());
        assertEquals(1, a.deletes.size());
    }

    @Test
    @DisplayName("Follow is paused for the capture and restored afterwards, on success and on failure")
    void followIsPausedAndRestored() {
        for (boolean fails : new boolean[]{false, true}) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = opened(a);
            d.submit(new SessionEvents.FollowToggled(d.snapshot().logGeneration(), true));
            assertTrue(d.snapshot().following(), "control: Follow is on");
            d.submit(ok(13));
            assertEquals(1, a.followSets.size(), "Follow is paused first");
            assertFalse(a.followSets.get(0).on());
            var e = a.captures.get(0);
            d.post(fails ? new SessionEvents.BundleWriteFailed(e.ticket(), e.generation(), "DEMO: disk full")
                    : new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of()));
            assertEquals(2, a.followSets.size(), "and restored " + (fails ? "after a failure" : "after success"));
            assertTrue(a.followSets.get(1).on());
        }
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(ok(14));
        var e = a.captures.get(0);
        d.post(new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of()));
        assertEquals(List.of(), a.followSets, "Follow that was off is never touched");
    }

    @Test
    @DisplayName("a result for another ticket is ignored")
    void aStaleResultIsIgnored() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(ok(15));
        var e = a.captures.get(0);
        d.post(new SessionEvents.BundleWritten(e.ticket() + 1, e.generation(), PATH, "sha256:stale", List.of()));
        assertEquals("WRITING", capture(d).phase(), "a stale result changes nothing");
        assertNull(capture(d).identity());
    }
}
