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

        assertFalse(a.captures.stream().anyMatch(SessionEffects.CaptureBundleEffect::readSoFar));
    }

    /** A log whose session has read {@code total} records, with Follow on. */
    static SessionDriver following(FakeSessionAdapter a, int total) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/demo-quote-audit.yaml", "DECLARED", java.util.Set.of(), total, total, null);
        d.submit(new SessionEvents.FollowToggled(d.snapshot().logGeneration(), true));
        assertTrue(d.snapshot().following(), "control: Follow is on");
        return d;
    }

    @Test
    @DisplayName("EB.F6: a log still growing under Follow is captured as the records READ SO FAR, not refused")
    void aGrowingLogBundlesWhatWasRead() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = following(a, 10);
        d.submit(request(18, null, "changed-on-disk", true));
        assertTrue(capture(d).answer().accepted(), capture(d).answer().reason());
        assertEquals(1, a.captures.size());
        assertTrue(a.captures.get(0).readSoFar(), "the write is asked for as what was read, never the growing file");
        FakeSessionAdapter still = new FakeSessionAdapter();
        SessionDriver s = following(still, 10);
        s.submit(ok(19));
        assertFalse(still.captures.get(0).readSoFar(), "control: a log that is not growing is captured whole");
    }

    @Test
    @DisplayName("EB.F6: a log growing under Follow with nothing read yet is refused by name")
    void aGrowingLogWithNothingReadIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = following(a, 0);
        d.submit(request(20, null, "changed-on-disk", true));
        refusedWritingNothing(d, a, 20, "nothing has been read from it yet");
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

    // ---- a replay (M70.R2, replay spec §4.1): the frame observes the pairing, the node decides -----------------

    static final String REPLAY = "/logs/demo-quote-recorded.replay.yaml";

    /** A request carrying a replay, as the frame makes it: observed to pair (problem null) unless a case says otherwise. */
    static SessionEvents.BundleCaptureRequested withReplay(long id, Long from, String freshness, String problem, int serviceCalls) {
        return new SessionEvents.BundleCaptureRequested(id, PATH, null, from, null, null, freshness, true,
                from == null ? -1 : 3, "test", REPLAY, 7, serviceCalls, problem, problem == null ? "abc123" : null);
    }

    @Test
    @DisplayName("replay: a replay that pairs travels with the one write, with what the pairing read")
    void aPairedReplayTravelsWithTheWrite() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(withReplay(20, null, "unchanged-metadata", null, 0));
        assertTrue(capture(d).answer().accepted(), capture(d).answer().reason());
        var e = a.captures.get(0);
        assertEquals(REPLAY, e.replay());
        assertEquals(7, e.replayRecords());
        assertEquals("abc123", e.replaySha256(), "the digest of the paired bytes, to hold the copy to");
    }

    @Test
    @DisplayName("replay: one that does not pair with the log is refused by name, nothing written")
    void aReplayFromAnotherRunIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(withReplay(21, null, "unchanged-metadata", "its record 0 (MarketDataEvent at 1) matches no log record", 0));
        refusedWritingNothing(d, a, 21, "the replay does not belong to this log: its record 0");
    }

    @Test
    @DisplayName("replay: with a window, refused by name — a replay needs the whole run")
    void aReplayWithAWindowIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(withReplay(22, 1L, "unchanged-metadata", null, 0));
        refusedWritingNothing(d, a, 22, "a replay needs the whole run");
    }

    @Test
    @DisplayName("replay: while the log is still growing, refused by name — the bundle would hold only what was read")
    void aReplayOfAGrowingLogIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = following(a, 10);
        d.submit(withReplay(23, null, "changed-on-disk", null, 0));
        refusedWritingNothing(d, a, 23, "a replay needs the whole run");
    }

    @Test
    @DisplayName("PR #70 review 1: inputs the log cannot check by content are stated as matched by type and instant only")
    void unprovenInputsAreStated() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(new SessionEvents.BundleCaptureRequested(29, PATH, null, null, null, null, "unchanged-metadata", true,
                -1, "test", REPLAY, 7, 0, null, "abc", 0, 3));
        var e = a.captures.get(0);
        assertEquals(3, e.replayUnproven(), "the count travels with the write, to the manifest");
        d.post(new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of()));
        List<String> lines = capture(d).lines();
        assertTrue(lines.get(0).contains("by type, instant and content for 4 of them"), lines.toString());
        assertTrue(lines.get(1).contains("3 input(s) are matched by type and instant only"), lines.toString());
    }

    @Test
    @DisplayName("second review S4: under Follow a replay is refused even before any growth is seen — one moment needs Follow off")
    void aReplayUnderFollowIsRefusedBeforeGrowthIsSeen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = following(a, 10);
        d.submit(withReplay(27, null, "unchanged-metadata", null, 0));
        refusedWritingNothing(d, a, 27, "turn Follow off once it has ended");
        // control: the same request without Follow is accepted
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver still = opened(b);
        still.submit(withReplay(28, null, "unchanged-metadata", null, 0));
        assertTrue(capture(still).answer().accepted(), capture(still).answer().reason());
    }

    @Test
    @DisplayName("replay: written, the node says what it carries, and names the service calls it cannot")
    void aWrittenReplayIsDescribedAndItsLimitNamed() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.submit(withReplay(24, null, "unchanged-metadata", null, 2));
        var e = a.captures.get(0);
        d.post(new SessionEvents.BundleWritten(e.ticket(), e.generation(), PATH, "sha256:demo", List.of("left out: DEMO")));
        List<String> lines = capture(d).lines();
        assertEquals("left out: DEMO", lines.get(0), "the writer's lines first");
        assertTrue(lines.get(1).contains("the run's 7 recorded inputs, matched to the log in order by type, instant and content"), lines.toString());
        assertTrue(lines.get(2).contains("2 exported-service call(s) the replay does not carry"), lines.toString());

        // review S1: a replay that does not carry every record of its own types says so, rather than read as the run
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver cut = opened(c);
        cut.submit(new SessionEvents.BundleCaptureRequested(26, PATH, null, null, null, null, "unchanged-metadata", true,
                -1, "test", REPLAY, 1, 0, null, "abc", 6));
        var g = c.captures.get(0);
        cut.post(new SessionEvents.BundleWritten(g.ticket(), g.generation(), PATH, "sha256:demo", List.of()));
        assertTrue(capture(cut).lines().stream().anyMatch(l -> l.contains("6 record(s) of the replay's own event types that it does not carry")),
                capture(cut).lines().toString());
        assertTrue(capture(d).lines().stream().noneMatch(l -> l.contains("does not carry: raised")),
                "control: the whole replay says nothing of the kind");

        // and a bundle with no replay says nothing about one
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver plain = opened(b);
        plain.submit(ok(25));
        var f = b.captures.get(0);
        plain.post(new SessionEvents.BundleWritten(f.ticket(), f.generation(), PATH, "sha256:demo", List.of()));
        assertEquals(List.of(), capture(plain).lines());
        assertEquals(null, f.replay());
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
