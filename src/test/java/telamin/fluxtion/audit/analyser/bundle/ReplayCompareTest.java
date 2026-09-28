package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Does a replayed audit log reproduce the bundle's (M70.R3, spec-evidence-bundle-replay §6)? Against a real bundle built
 * from the committed replay fixture, and the audit log a fresh processor actually wrote replaying it.
 */
public class ReplayCompareTest {

    /** What the fixture generator's fresh processor wrote, replaying the recorded run: the real thing, not a copy. */
    public static final Path REPLAYED = Path.of("src/test/resources/replay/demo-quote-recorded.replayed-audit.yaml");

    public static Path bundle(Path tmp) throws Exception {
        Path out = tmp.resolve("run.fexp");
        BundleWriter.write(ReplayBundleTest.job(out, ReplayBundleTest.REPLAY, ReplayBundleTest.pairing()));
        return out;
    }

    private static ReplayCompare.Verdict compare(Path tmp, String replayedText) throws Exception {
        Path replayed = Files.writeString(tmp.resolve("replayed-" + System.nanoTime() + ".yaml"), replayedText);
        return ReplayCompare.compare(bundle(tmp), replayed, 256);
    }

    /** Refused, naming why: an assertion, so a comparison that ran instead of refusing fails HERE, by name. */
    private static void refusedWith(ReplayCompare.Verdict c, String naming) {
        assertNotNull(c.refusal(), "expected a refusal naming '" + naming + "', but it compared: agrees=" + c.agrees()
                + ", divergence=" + c.divergence());
        assertTrue(c.refusal().contains(naming), c.refusal());
    }

    private static String replayed() throws Exception {
        return Files.readString(REPLAYED);
    }

    @Test
    @DisplayName("the real replay agrees: every record exact but endTime, which differs on all eight")
    void theRealReplayAgrees(@TempDir Path tmp) throws Exception {
        var c = ReplayCompare.compare(bundle(tmp), REPLAYED, 256);
        assertNull(c.refusal(), c.refusal());
        assertTrue(c.agrees(), c.divergence());
        assertEquals(8, c.records());
        assertEquals(8, c.excepted(), "the replay pins the clock, so every endTime differs, and only that");
    }

    @Test
    @DisplayName("control: the bundled log compared with itself agrees with nothing excepted")
    void itselfAgreesWithNothingExcepted(@TempDir Path tmp) throws Exception {
        var c = compare(tmp, Files.readString(ReplayBundleTest.AUDIT));
        assertTrue(c.agrees(), c.divergence());
        assertEquals(0, c.excepted());
    }

    @Test
    @DisplayName("a node that computed something else diverges at that record, naming the node and both values")
    void aChangedNodeValueDiverges(@TempDir Path tmp) throws Exception {
        var c = compare(tmp, replayed().replaceFirst("- orderTracker: \\{ orderId: ord-1, live: 1}",
                "- orderTracker: { orderId: ord-1, live: 7}"));
        assertFalse(c.agrees());
        assertEquals(1, c.records(), "the one record before it agrees");
        assertEquals("record 1 (OrderUpdateEvent): eventLogRecord.nodeLogs.orderTracker: "
                + "'{ orderId: ord-1, live: 1}' ≠ '{ orderId: ord-1, live: 7}'", c.divergence());
    }

    @Test
    @DisplayName("a node that did not log in the replay is named as a missing line, never as a value change")
    void aMissingNodeEntryIsNamedAsMissing(@TempDir Path tmp) throws Exception {
        var c = compare(tmp, replayed().replaceFirst("\n        - orderTracker: \\{ orderId: ord-1, live: 1}", ""));
        assertFalse(c.agrees());
        assertEquals("record 1 (OrderUpdateEvent): eventLogRecord.nodeLogs.orderTracker: the bundled log has "
                + "'{ orderId: ord-1, live: 1}', and the replay has no such line", c.divergence());
        // and one the replay has that the log does not
        var extra = compare(java.nio.file.Files.createDirectories(tmp.resolve("extra")), replayed().replaceFirst("(- orderTracker: \\{ orderId: ord-1, live: 1})",
                "$1\n        - auditor: { note: extra}"));
        assertEquals("record 1 (OrderUpdateEvent): eventLogRecord.nodeLogs.auditor: the replay has '{ note: extra}', "
                + "and the bundled log has no such line", extra.divergence());
    }

    @Test
    @DisplayName("only endTime is excepted: an input's eventTime that differs is a divergence")
    void anEventTimeIsNeverExcepted(@TempDir Path tmp) throws Exception {
        var c = compare(tmp, replayed().replaceFirst("eventTime: 1767258000100", "eventTime: 1767258000110"));
        assertFalse(c.agrees());
        assertEquals("record 2 (MarketDataEvent): eventLogRecord.eventTime: '1767258000100' ≠ '1767258000110'",
                c.divergence());
    }

    @Test
    @DisplayName("a build that no longer raises the breach: the comparison names the record the replay does not have")
    void aMissingRecordDiverges(@TempDir Path tmp) throws Exception {
        String text = replayed();
        var c = compare(tmp, text.substring(0, text.lastIndexOf("---\neventLogRecord")));
        assertFalse(c.agrees());
        assertEquals("record 7: the bundled log has record 7 (RiskBreachEvent), and the replay does not "
                + "(8 records bundled, 7 replayed)", c.divergence());
    }

    @Test
    @DisplayName("a replay with a record the log does not have diverges there")
    void anExtraRecordDiverges(@TempDir Path tmp) throws Exception {
        String text = replayed();
        String last = text.substring(text.lastIndexOf("---\neventLogRecord"));
        var c = compare(tmp, text + last);
        assertFalse(c.agrees());
        assertTrue(c.divergence().startsWith("record 8: the replay has record 8 (RiskBreachEvent), and the bundled log does not"),
                c.divergence());
    }

    @Test
    @DisplayName("a bundle without replay records, or one that fails verification, is refused: nothing compared")
    void whatCannotBeComparedIsRefused(@TempDir Path tmp) throws Exception {
        Path plain = tmp.resolve("plain.fexp");
        BundleWriter.write(new BundleWriter.Job(plain, ReplayBundleTest.AUDIT, ReplayBundleTest.GRAPH,
                "project.fluxtion-settings", Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, Instant.now(),
                "test", 256, null));
        refusedWith(ReplayCompare.compare(plain, REPLAYED, 256), "carries no replay records");

        var entries = EvidenceBundleTest.entries(bundle(tmp));
        entries.put("log/demo-quote-recorded-audit.yaml", "tampered".getBytes(StandardCharsets.UTF_8));
        refusedWith(ReplayCompare.compare(EvidenceBundleTest.zip(tmp.resolve("bad.fexp"), entries), REPLAYED, 256),
                "changed member: log/demo-quote-recorded-audit.yaml");

        refusedWith(ReplayCompare.compare(bundle(tmp.resolve("again")), tmp.resolve("absent.yaml"), 256), "not a file");
    }

    @Test
    @DisplayName("the thread a cycle ran on is excepted too: a recipient's replay runs on its own (found end to end, M70.R4)")
    void theThreadIsExcepted(@TempDir Path tmp) throws Exception {
        var c = compare(tmp, replayed().replace("thread: com.acme.demo.GenerateFixtures.main()", "thread: main"));
        assertTrue(c.agrees(), c.divergence());
        assertEquals(8, c.excepted());
        // but only as that key, in that place: a thread line cannot stand in for an endTime line
        assertEquals("eventLogRecord.endTime: the bundled log has '5', and the replay has no such line",
                ReplayCompare.firstDifference(java.util.List.of("eventLogRecord: ", "    endTime: 5"),
                        java.util.List.of("eventLogRecord: ", "    thread: x")));
    }

    @Test
    @DisplayName("review R3: a log written with CRLF line endings is the same log, and agrees")
    void aCrlfLogAgrees(@TempDir Path tmp) throws Exception {
        var c = compare(tmp, replayed().replace("\n", "\r\n"));
        assertTrue(c.agrees(), c.divergence());
        assertEquals(8, c.records());
    }

    @Test
    @DisplayName("review N2: only the record's OWN endTime and thread are excepted, never a node's nested value of that name")
    void aNestedThreadValueIsCompared() {
        var a = java.util.List.of("eventLogRecord: ", "    eventTime: 1", "    nodeLogs: ", "        thread:", "            name: a");
        var b = java.util.List.of("eventLogRecord: ", "    eventTime: 1", "    nodeLogs: ", "        thread:", "            name: b");
        assertEquals("eventLogRecord.nodeLogs.thread.name: 'a' ≠ 'b'", ReplayCompare.firstDifference(a, b));
        var nestedThread = java.util.List.of("eventLogRecord: ", "    nodeLogs: ", "        thread: x");
        var otherThread = java.util.List.of("eventLogRecord: ", "    nodeLogs: ", "        thread: y");
        assertEquals("eventLogRecord.nodeLogs.thread: 'x' ≠ 'y'", ReplayCompare.firstDifference(nestedThread, otherThread));
        // control: the record's own thread IS excepted
        assertEquals(null, ReplayCompare.firstDifference(java.util.List.of("eventLogRecord: ", "    thread: x"),
                java.util.List.of("eventLogRecord: ", "    thread: y")));
    }

    @Test
    @DisplayName("PR #70 review 1: a comment never sets the record's field scope, so a nested thread is still compared")
    void aCommentDoesNotSetTheHeaderScope() throws Exception {
        // the review's probe: an indented comment first under the header, then a node value called thread
        String log = replayed().replaceFirst("eventLogRecord: \n", "eventLogRecord: \n            # DEMO indented YAML comment\n")
                .replaceFirst("        - priceListener: \\{ symbol: DEMO-A, mid: 100.19999999999999}",
                        "        - priceListener:\n            thread: DEMO-old");
        assertTrue(log.contains("thread: DEMO-old"), "fixture anchor moved");
        var c = ReplayCompare.firstDifference(ReplayCompare.lines(log), ReplayCompare.lines(log.replace("DEMO-old", "DEMO-new")));
        assertEquals("eventLogRecord.nodeLogs.priceListener.thread: 'DEMO-old' ≠ 'DEMO-new'", c);
        // the record's own thread, under the same comment, is still excepted
        String header = log.replaceFirst("    thread: com.acme.demo.GenerateFixtures.main\\(\\)", "    thread: main");
        assertNotEquals(log, header, "header anchor moved");
        assertNull(ReplayCompare.firstDifference(ReplayCompare.lines(log), ReplayCompare.lines(header)));
    }

    @Test
    @DisplayName("an endTime line that moved or is missing still differs: the exception is by position, not a filter")
    void theExceptionIsPositional() {
        var a = java.util.List.of("eventLogRecord: ", "    eventTime: 1", "    endTime: 5");
        assertNull(ReplayCompare.firstDifference(a, java.util.List.of("eventLogRecord: ", "    eventTime: 1", "    endTime: 9")));
        assertEquals("eventLogRecord.endTime: the bundled log has '5', and the replay has no such line",
                ReplayCompare.firstDifference(a, java.util.List.of("eventLogRecord: ", "    eventTime: 1")));
    }
}
