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
}
