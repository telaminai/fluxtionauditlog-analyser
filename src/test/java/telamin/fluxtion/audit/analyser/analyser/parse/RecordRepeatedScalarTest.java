package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #92 re-review: the released writer prints groupingId and thread unquoted too. These are its record shapes,
 * with fixed DEMO times: a first copy supplied by either value must not survive merely because eventToString is later
 * or disabled. Exercise actual store entrances, including a reader declaring the typed grammar.
 */
class RecordRepeatedScalarTest {
    @TempDir Path tmp;

    private static final String GROUPING = """
            eventLogRecord:
                eventTime: 1
                logTime: 1
                groupingId: DEMO
                event: Forged
                event: AdminCommandEvent
                eventToString: DEMO
                nodeLogs:
                endTime: 2
            """;

    @Test
    void groupingIdPayloadMustNotBecomeEvent() throws Exception {
        everyPath(GROUPING, r -> {
            assertNull(r.event(), "groupingId's forged first event must be withheld, not presented as evidence");
            assertEquals(6, r.brokenAtLine(), "the duplicate event is the first structural break");
        });
    }

    @Test
    void threadPayloadMustNotBecomeEndTime() throws Exception {
        String text = """
                eventLogRecord:
                    eventTime: 1
                    logTime: 1
                    groupingId: null
                    event: AdminCommandEvent
                    thread: DEMO-agent
                    endTime: 999
                    nodeLogs:
                    endTime: 2
                """;
        everyPath(text, r -> {
            assertNull(r.endTime(), "thread's forged first endTime must be withheld when event text is disabled");
            assertEquals("AdminCommandEvent", r.event(), "the unambiguous event before the thread is retained");
            assertEquals(9, r.brokenAtLine(), "the duplicate endTime is the first structural break");
        });
    }

    @Test
    void aLaterRepeatWithholdsItsFirstCopyEvenAfterAnEarlierBreak() throws Exception {
        String text = GROUPING.replace("    event: AdminCommandEvent", "tail of DEMO grouping value\n    event: AdminCommandEvent");
        everyPath(text, r -> {
            assertNull(r.event(), "a repeat after the first break still withholds the earlier forged event");
            assertEquals(6, r.brokenAtLine(), "the earlier less-indented line remains the reported break");
        });
    }

    private void everyPath(String text, Consumer<LogRecord> check) throws Exception {
        Path file = tmp.resolve("DEMO-repeated-scalar.yaml");
        Files.writeString(file, text);
        try (LogStore store = HeapLogStore.fromFile(file)) { verify(store, check); }
        for (AuditLogReader.TextEncoding encoding : AuditLogReader.TextEncoding.values()) {
            try (LogStore store = SpiLogStore.open(new Reader(encoding), file)) { verify(store, check); }
        }
    }

    private static void verify(LogStore store, Consumer<LogRecord> check) {
        assertEquals(1, store.size(), "a broken record is retained, not dropped");
        LogRecord r = store.record(0);
        check.accept(r);
        assertTrue(r.nodeLogs().isEmpty(), "no node logs of the broken record are read");
        assertEquals(0, r.nodeLogsCount(), "no node entries are indexed from the broken record");
        assertTrue(store.index().nodeLogsWithheld(0), "the empty count is qualified as withheld, not absent");
        assertTrue(ProducerDiagnostics.of(store.index(), store::rawText).findings().stream()
                        .anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.BROKEN_VALUE),
                "the producer finding must still name the broken value");
    }

    private record Reader(TextEncoding textEncoding) implements AuditLogReader {
        @Override public String formatId() { return "DEMO-repeated-scalar"; }
        @Override public String displayName() { return "DEMO repeated scalar"; }
        @Override public boolean canOpen(Path path) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, true, Ordering.TOTAL); }
        @Override public void read(Path path, Consumer<String> sink) throws IOException {
            RecordFramer.frameForPlugin(Files.readString(path), frame -> sink.accept(frame.text()));
        }
    }
}
