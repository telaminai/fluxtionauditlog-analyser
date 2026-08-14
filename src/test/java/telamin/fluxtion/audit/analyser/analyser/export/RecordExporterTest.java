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
}
