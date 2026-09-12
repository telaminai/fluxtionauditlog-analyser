package telamin.fluxtion.audit.analyser.analyser.export;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecordExporterTest {

    private static HeapLogStore store() {
        return new HeapLogStore(Samples.sample());
    }

    @Test
    void csvHasHeaderAndOneRowPerFilteredRecord() {
        HeapLogStore s = store();
        String csv = RecordExporter.toCsv(s, new FilterState());
        String[] lines = csv.split("\n", -1);
        assertTrue(lines[0].startsWith("eventTimeUtc,logTimeUtc"));
        long data = csv.lines().skip(1).filter(l -> !l.isBlank()).count();
        assertEquals(21, data, "one CSV row per record");
        assertTrue(csv.contains("StartComplete"));
    }

    @Test
    void csvRespectsTheFilter() {
        HeapLogStore s = store();
        FilterState f = new FilterState();
        f.setDimensions(Set.of("ScheduledTriggerNode"));
        long data = RecordExporter.toCsv(s, f).lines().skip(1).filter(l -> !l.isBlank()).count();
        assertEquals(3, data);
    }

    @Test
    void yamlRoundTripsThroughTheParser() {
        HeapLogStore s = store();
        String yaml = RecordExporter.toYaml(s, new FilterState());
        assertEquals(21, new HeapLogStore(yaml).size(), "exported YAML re-parses to the same records");
    }

    /**
     * REVIEWER PROBE (round 7). A YAML export of a binary-derived store re-opened as legacy text, so
     * every quoted String came back with its quotes inside the value. The export contract is "re-loads
     * as the same records"; for a store whose reader declares a grammar the text has no way to carry,
     * that contract cannot be met, so the export is refused and the .flxa named as the artefact.
     */
    @org.junit.jupiter.api.Test
    void aYamlExportOfABinaryStoreIsRefused_theFlxaIsTheArtefact(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("export.flxa");
        try (var out = java.nio.file.Files.newOutputStream(file);
             var w = new com.telamin.fluxtion.runtime.audit.BinaryLogWriter(out)) {
            var clock = new com.telamin.fluxtion.runtime.time.Clock();
            clock.init();
            var r = new com.telamin.fluxtion.runtime.audit.BinaryLogRecord(clock);
            r.triggerObject(new Object());
            r.addRecord("n", "message", (CharSequence) "ok, invented: 99");
            r.addRecord("n", "price", 77);
            w.processLogRecord(r);
        }
        var store = telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore.open(
                new telamin.fluxtion.audit.analyser.analyser.spi.binary.BinaryAuditReader(), file);
        var refused = org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> RecordExporter.toYaml(store, new FilterState()));
        org.junit.jupiter.api.Assertions.assertTrue(refused.getMessage().contains(".flxa"), refused.getMessage());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> RecordExporter.toCsv(store, new FilterState()),
                "CSV is index columns and is unaffected");
    }
}
