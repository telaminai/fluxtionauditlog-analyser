package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Does a replay belong to the open log (spec-evidence-bundle-replay §4.1)? Against the committed replay fixture. */
class ReplayPairingTest {

    private static final Path AUDIT = Path.of("src/test/resources/replay/demo-quote-recorded-audit.yaml");
    private static final Path REPLAY = Path.of("src/test/resources/replay/demo-quote-recorded.replay.yaml");
    /** The shipped DEMO log: a different run of the same graph, with two exported-service calls. */
    private static final Path OTHER_RUN = Path.of("src/main/resources/demo/demo-quote-audit.yaml");

    @TempDir
    Path tmp;

    private static ReplayPairing.Observed observe(Path replay, Path log) throws Exception {
        try (LogStore store = LogStores.open(log, 256)) {
            return ReplayPairing.observe(replay, store.index(), store.size());
        }
    }

    @Test
    void theRecordedRunsReplayPairsWithItsLog() throws Exception {
        var o = observe(REPLAY, AUDIT);
        assertTrue(o.pairs(), o.problem());
        assertEquals(7, o.records(), "the inputs; the graph's own breach is in the log, not the replay");
        assertEquals(0, o.serviceCalls());
    }

    @Test
    void aReplayFromAnotherRunIsRefusedAtItsFirstRecord() throws Exception {
        var o = observe(REPLAY, OTHER_RUN);
        assertFalse(o.pairs());
        assertTrue(o.problem().contains("its record 0 (MarketDataEvent at 1767258000060)"), o.problem());
        assertTrue(o.problem().contains("another run"), o.problem());
        assertEquals(2, o.serviceCalls(), "the shipped DEMO log's suspendQuoting and resumeQuoting are counted");
    }

    @Test
    void aReplayRestampedByAWriterThatReadTheClockAgainIsRefused() throws Exception {
        // UP-FLX-53's fault: the recorder's own reading, 10 ms after the cycle's
        Path restamped = tmp.resolve("restamped.replay.yaml");
        Files.writeString(restamped, Files.readString(REPLAY).replace("wallClockTime: 1767258000100", "wallClockTime: 1767258000110"));
        var o = observe(restamped, AUDIT);
        assertFalse(o.pairs());
        assertTrue(o.problem().contains("its record 2 (MarketDataEvent at 1767258000110)"), o.problem());
    }

    @Test
    void recordsOutOfOrderAreRefused() throws Exception {
        String text = Files.readString(REPLAY);
        int second = text.indexOf("---", 1);
        int third = text.indexOf("---", second + 1);
        Path swapped = tmp.resolve("swapped.replay.yaml");
        Files.writeString(swapped, text.substring(second, third) + text.substring(0, second) + text.substring(third));
        assertFalse(observe(swapped, AUDIT).pairs());
    }

    @Test
    void aFileThatIsNotAReplayIsRefusedWithoutLoadingAnything() throws Exception {
        var o = observe(AUDIT, AUDIT);                                   // the audit log, named as a replay
        assertFalse(o.pairs());
        assertTrue(o.problem().contains("it is not a replay file"), o.problem());
        // records whose separators are not the document separator: every other line is right, and it is still not one
        Path unframed = tmp.resolve("unframed.replay.yaml");
        Files.writeString(unframed, Files.readString(REPLAY).replace("---\n", "junk\n"));
        var u = observe(unframed, AUDIT);
        assertFalse(u.pairs(), "a file framed with anything but '---' is not a replay file");
        assertTrue(u.problem().contains("line 1 is not part of a replay record"), u.problem());
        var missing = observe(tmp.resolve("absent.replay.yaml"), AUDIT);
        assertTrue(missing.problem().contains("not a file"), missing.problem());
        Path empty = Files.writeString(tmp.resolve("empty.replay.yaml"), "\n");
        assertEquals("it holds no replay records", observe(empty, AUDIT).problem());
    }

    @Test
    void pairingIsAboutBelongingNotAboutDuplicates() throws Exception {
        // A replay that ALSO recorded the graph's own breach still belongs to this log: the breach is in the log at that
        // instant. Replaying it would raise the breach twice, and that is the comparison's to find (spec §6), not this.
        Path withRaised = tmp.resolve("with-raised.replay.yaml");
        Files.writeString(withRaised, Files.readString(REPLAY) + "---\n!!com.telamin.fluxtion.runtime.event.ReplayRecord\n"
                + "event: !!com.acme.demo.event.Events$RiskBreachEvent {orderId: \"ord-2\", liveOrders: 2}\n"
                + "wallClockTime: 1767258000180\n");
        var o = observe(withRaised, AUDIT);
        assertTrue(o.pairs(), o.problem());
        assertEquals(8, o.records());
    }

    @Test
    void theSimpleNameIsTheOneTheLogUses() {
        assertEquals("MarketDataEvent", ReplayPairing.simpleName("com.acme.demo.event.Events$MarketDataEvent"));
        assertEquals("Tick", ReplayPairing.simpleName("com.acme.Tick"));
    }
}
