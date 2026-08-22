package telamin.fluxtion.audit.analyser.analyser.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan;
import telamin.fluxtion.audit.analyser.analyser.llm.ReadService;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M31.1 — the SPI seam, proven with a TOY reader (a line-per-record container that is not YAML): the
 * reader streams canonical record text, the CORE builds everything, and the downstream features work
 * with no container knowledge anywhere above the reader. The built-in YAML reader answers canOpen by
 * CONTENT, and the registry routes it to the optimised text stores.
 */
class ReaderSpiTest {

    @TempDir
    Path dir;

    /** A pretend foreign container: {@code time|node|key|value} per line → canonical YAML per record. */
    private static class ToyReader implements AuditLogReader {
        @Override public String formatId() { return "toy"; }
        @Override public String displayName() { return "Toy line format"; }
        @Override public boolean canOpen(Path source) {
            return source.getFileName().toString().endsWith(".toy");
        }
        @Override public TimeBase timeBase() { return new TimeBase("millis", "UTC", "wallClock"); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, false); }
        @Override public void read(Path source, Consumer<String> recordText) throws IOException {
            for (String line : Files.readAllLines(source)) {
                if (line.isBlank()) continue;
                String[] f = line.split("\\|");
                recordText.accept("eventLogRecord:\n  logTime: " + f[0] + "\n  event: ToyEvent\n"
                        + "  nodeLogs:\n    - " + f[1] + ": { " + f[2] + ": " + f[3] + "}\n");
            }
        }
    }

    @Test
    void aForeignContainerGetsEveryFeatureAboveTheReader() throws IOException {
        Path toy = dir.resolve("prices.toy");
        Files.writeString(toy, "1000|book|mid|17.1\n2000|book|mid|17.5\n3000|book|mid|17.2\n");

        LogStore store = SpiLogStore.open(new ToyReader(), toy);
        assertEquals(3, store.size());
        assertEquals(1000L, store.index().logTime(0));
        assertEquals("ToyEvent", store.record(1).event());
        assertTrue(store.rawText(0).contains("mid: 17.1"), "D-P2: the canonical text IS the raw text");

        // the verbs work with zero container knowledge: read projects, series computes
        Map<String, Object> read = ReadService.read(store.index().snapshot(),
                Map.of("recordIndex", 0, "count", 1, "fields", java.util.List.of("book.mid")), store::rawText);
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) read.get("records");
        assertEquals(Map.of("book.mid", "17.1"), rows.get(0).get("values"));

        Map<String, Object> scan = SeriesScan.scan(store, Map.of("expr", "book.mid"));
        assertEquals(3L, scan.get("points"));

        // and the filter columns exist (dimension interning happened in the core's index build)
        FilterState filter = new FilterState();
        assertTrue(filter.test(store.index(), 0));
    }

    @Test
    void theBuiltInReaderAnswersByContentNotExtension(@TempDir Path d) throws IOException {
        Path noExt = d.resolve("mystery");
        Files.writeString(noExt, Samples.sample());
        Path fake = d.resolve("pretty.yaml");
        Files.writeString(fake, "not: an audit log\n");
        YamlAuditReader yaml = new YamlAuditReader();
        assertTrue(yaml.canOpen(noExt), "a real audit log with no extension is still ours");
        assertFalse(yaml.canOpen(fake), "a .yaml that is not an audit log is not");
    }

    @Test
    void aSourceWithoutByteAnchorsRefusesOffsetAnchoring_loudly() throws IOException {
        Path toy = dir.resolve("prices.toy");
        Files.writeString(toy, "1000|book|mid|17.1\n2000|book|mid|17.5\n");
        LogStore store = SpiLogStore.open(new ToyReader(), toy);
        assertFalse(store.index().byteAnchors());
        var e = assertThrows(IllegalArgumentException.class, () -> ReadService.read(
                store.index().snapshot(), Map.of("byteOffset", 0L), store::rawText));
        assertTrue(e.getMessage().contains("no byte anchors"), e.getMessage());
        assertTrue(e.getMessage().contains("recordIndex"), "the refusal teaches the alternative: " + e.getMessage());
        // recordIndex anchoring is untouched
        assertEquals(0, ReadService.read(store.index().snapshot(),
                Map.of("recordIndex", 0, "count", 1), store::rawText).get("anchor"));
    }

    @Test
    void aReaderWithoutATimeBaseIsRefusedAtRegistration() {
        AuditLogReader lawless = new ToyReader() {
            @Override public TimeBase timeBase() { return null; }
        };
        var e = assertThrows(IllegalArgumentException.class,
                () -> new ReaderRegistry().register(lawless));
        assertTrue(e.getMessage().contains("timeBase"), e.getMessage());
    }

    @Test
    void theRegistryRoutesTextToTheOptimisedStores_andForeignToTheSpiStore() throws IOException {
        Path yamlLog = dir.resolve("real.log");
        Files.writeString(yamlLog, Samples.sample());
        Path toy = dir.resolve("prices.toy");
        Files.writeString(toy, "1000|book|mid|17.1\n");

        ReaderRegistry reg = new ReaderRegistry();
        AuditLogReader yaml = reg.readerFor(yamlLog, null);
        assertEquals("yaml", yaml.formatId());
        LogStore textStore = reg.open(yaml, yamlLog, 512);
        assertInstanceOf(HeapLogStore.class, textStore,
                "text keeps the thresholded byte-anchored stores — the seam must not cost the fast path");

        ReaderRegistry reg2 = new ReaderRegistry();
        reg2.register(new ToyReader());   // the same seam the plugin loader uses
        assertEquals("toy", reg2.readerFor(toy, null).formatId(), "canOpen sweep finds the plugin");
        assertEquals("toy", reg2.readerFor(yamlLog, "toy").formatId(), "an explicit format override wins");
        assertNull(reg2.readerFor(yamlLog, "parquet"), "an unknown format is a null, refused upstream");
        assertInstanceOf(SpiLogStore.class, reg2.open(reg2.readerFor(toy, null), toy, 512));
    }

    // ---- M34.1 · ordering (D-A1a) ---------------------------------------------------------------

    @Test
    void theShippedTextReaderClaimsTOTALorder_andSaysSoRatherThanLeavingItToBeAssumed() {
        var caps = new YamlAuditReader().capabilities();
        assertEquals(AuditLogReader.Ordering.TOTAL, caps.ordering(),
                "a byte stream of records IS totally ordered, and Fluxtion's order is compiler-derived");
    }

    @Test
    void thePreM34ConstructorStillCompilesAndDefaultsToTOTAL() {
        // Capabilities is a PUBLISHED compatibility surface (shipped 1.5.0). A reader written
        // against the 3-arg shape must keep working, and TOTAL is the only correct default for
        // every container that existed when that was the only constructor.
        var old = new AuditLogReader.Capabilities(true, true, true);
        assertEquals(AuditLogReader.Ordering.TOTAL, old.ordering());
    }

    @Test
    void aNullOrderingIsNormalisedRatherThanTrusted() {
        var caps = new AuditLogReader.Capabilities(false, false, false, null);
        assertEquals(AuditLogReader.Ordering.TOTAL, caps.ordering(),
                "absent means the source did not say; the safe reading is the one that matches "
                        + "every container that can only be totally ordered");
    }

    @Test
    void aPartialOrderReaderMarksTheIndex_soConsumersCanQualifyRatherThanGuess() throws Exception {
        var reader = new PartialOrderReader();
        try (var store = SpiLogStore.open(reader, java.nio.file.Path.of("unused"))) {
            assertFalse(store.index().totalOrder(),
                    "the claim must reach the index — this is what step-through and the topology "
                            + "badges will read to decide whether position means causality");
            assertEquals(2, store.size());
        }
    }

    @Test
    void aTotalOrderReaderLeavesTheIndexAsItWas_theNativePathIsUnchanged() throws Exception {
        var reader = new TotalOrderReader();
        try (var store = SpiLogStore.open(reader, java.nio.file.Path.of("unused"))) {
            assertTrue(store.index().totalOrder(), "no behaviour change for a totally-ordered source");
        }
    }

    /** Two records, and the reader admits it cannot say what ran first inside a cycle. */
    private static final class PartialOrderReader implements AuditLogReader {
        @Override public String formatId() { return "partial-probe"; }
        @Override public String displayName() { return "partial order probe"; }
        @Override public boolean canOpen(java.nio.file.Path source) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() {
            return new Capabilities(false, false, true, Ordering.PARTIAL);
        }
        @Override public void read(java.nio.file.Path source,
                                   java.util.function.Consumer<String> recordText) {
            recordText.accept(RECORD.formatted(1000, "a"));
            recordText.accept(RECORD.formatted(2000, "b"));
        }
    }

    private static final class TotalOrderReader implements AuditLogReader {
        @Override public String formatId() { return "total-probe"; }
        @Override public String displayName() { return "total order probe"; }
        @Override public boolean canOpen(java.nio.file.Path source) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() {
            return new Capabilities(false, false, true, Ordering.TOTAL);
        }
        @Override public void read(java.nio.file.Path source,
                                   java.util.function.Consumer<String> recordText) {
            recordText.accept(RECORD.formatted(1000, "a"));
        }
    }

    private static final String RECORD = """
            eventLogRecord:
              logTime: %d
              event: Probe
              nodeLogs:
                - n: { v: %s}
            """;
}
