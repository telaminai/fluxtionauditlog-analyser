package telamin.fluxtion.audit.analyser.analyser.parse;

import com.telamin.fluxtion.runtime.audit.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.BinaryLogWriter;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.binary.BinaryAuditReader;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MA-6's record-key check, at the boundary a reader plugin actually hands over — and its wording.
 *
 * <p><b>Independent review, F3.</b> The positional check stopped at the first non-blank line. The
 * analyser's OWN binary reader renders a record as {@code ---\neventLogRecord:\n…}, so every healthy binary
 * record was diagnosed as having no key. Its diagnostics-only test passed a null index and never met the
 * integration. This drives the real runtime writer, the real reader and the real SPI store.
 *
 * <p><b>Independent review, F6.</b> The message said the log "reads as complete while the document's
 * header, keys and newlines are gone" whatever the file was. That described one AFMT-3 reproduction under
 * a marker, and was false of an unmarked, readable, merely headerless document. The finding now states
 * what it saw and what a marker would and would not establish.
 */
class RecordKeyBoundaryTest {

    private static List<String> kinds(LogStore s) {
        return ProducerDiagnostics.of(s.index(), s::rawText).findings().stream().map(f -> f.kind().name()).toList();
    }

    private static String message(LogStore s, String kind) {
        return ProducerDiagnostics.of(s.index(), s::rawText).findings().stream()
                .filter(f -> f.kind().name().equals(kind)).findFirst().orElseThrow().message();
    }

    @Test
    void aHealthyBinaryRecordThroughTheRealReaderHasItsKey(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("healthy.flxa");
        try (OutputStream out = Files.newOutputStream(p); BinaryLogWriter w = new BinaryLogWriter(out)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord r = new BinaryLogRecord(clock);
            int node = r.internName("riskMonitor"), key = r.internName("seen");
            r.triggerObject("DEMO");
            r.addRecord(node, key, true);
            w.processLogRecord(r);
        }
        try (SpiLogStore s = SpiLogStore.open(new BinaryAuditReader(), p)) {
            assertEquals(1, s.size(), "precondition: one record");
            assertTrue(s.rawText(0).startsWith("---\neventLogRecord:"),
                    "precondition: the reader's canonical text opens with a separator — the shape F3 is about");
            assertEquals(1, s.record(0).nodeLogs().size(), "precondition: it decoded");
            assertFalse(kinds(s).contains("NO_RECORD_KEY"),
                    "F3: a healthy record from the analyser's own binary reader was diagnosed as keyless");
        }
    }

    /** A plugin that hands over exactly the texts it is given. */
    private record TextReader(List<String> texts) implements AuditLogReader {
        @Override public String formatId() { return "test-texts"; }
        @Override public String displayName() { return "test texts"; }
        @Override public boolean canOpen(Path source) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, true); }
        @Override public void read(Path source, Consumer<String> out) { texts.forEach(out); }
    }

    private static SpiLogStore plugin(Path dir, String... texts) throws Exception {
        Path p = dir.resolve("plugin.src");
        Files.writeString(p, "");
        return SpiLogStore.open(new TextReader(List.of(texts)), p);
    }

    @Test
    void aGenuinelyHeaderlessPluginRecordIsStillNamed(@TempDir Path dir) throws Exception {
        try (SpiLogStore s = plugin(dir, "---\nevent: Quote\nlogTime: 1000\nnodeLogs:\n- a: { v: 1}")) {
            assertTrue(kinds(s).contains("NO_RECORD_KEY"),
                    "the leading separator is skipped, not the check: a headerless record is still named");
        }
    }

    @Test
    void aKeyMentionedLaterIsStillNotAnOpeningKey(@TempDir Path dir) throws Exception {
        try (SpiLogStore s = plugin(dir, "---\nevent: Quote\neventToString: eventLogRecord:\n")) {
            assertTrue(kinds(s).contains("NO_RECORD_KEY"),
                    "V1: skipping a leading separator must not turn this back into a substring search");
        }
    }

    @Test
    void onlyAPlainSeparatorIsABoundary(@TempDir Path dir) throws Exception {
        try (SpiLogStore s = plugin(dir, "﻿---\neventLogRecord:\n  event: Quote\n")) {
            assertTrue(kinds(s).contains("NO_RECORD_KEY"),
                    "a BOM'd separator is not a boundary here either — the same language the framers speak");
        }
    }

    private static final String HEADERLESS = "event: Quote\nlogTime: 1000\nnodeLogs:\n- priceListener: { seen: true}\n---\n";
    private static final String MARKER_1 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
    private static final String MARKER_2 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n---\n";

    /** Every wording must hold in every state — the finding cannot see the state, so it must not state one. */
    @Test
    void theWordingNeverStatesAVerdictItCannotSeeOrInventsMissingContent() {
        record Case(String label, String file, StreamEnd.State state) {
        }
        List<Case> cases = List.of(
                new Case("unmarked, readable, headerless", HEADERLESS, StreamEnd.State.UNKNOWN),
                new Case("marked, matching", HEADERLESS + MARKER_1, StreamEnd.State.COMPLETE),
                new Case("marked, mismatching", HEADERLESS + MARKER_2, StreamEnd.State.MISSING_RECORDS));
        for (Case c : cases) {
            HeapLogStore s = new HeapLogStore(c.file());
            assertEquals(c.state(), s.streamEnd().state(), c.label() + ": precondition on the state");
            String m = message(s, "NO_RECORD_KEY");
            assertFalse(m.contains("reads as complete"),
                    "F6 (" + c.label() + "): the finding asserted a verdict it cannot see: " + m);
            assertFalse(m.contains("are gone"),
                    "F6 (" + c.label() + "): the keys and newlines are right there; nothing is gone: " + m);
            assertTrue(m.contains("'event: Quote'"), c.label() + ": it says what it actually saw: " + m);
            assertTrue(m.contains("If a stream-end marker covers it"),
                    c.label() + ": what a marker would establish is conditional: " + m);
            assertTrue(m.contains("does not say which cause"),
                    c.label() + ": a possible cause is offered as possible: " + m);
        }
    }
}
