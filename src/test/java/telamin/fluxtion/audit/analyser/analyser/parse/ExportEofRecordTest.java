package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExportEofRecordTest {
    @Test void exportLayoutAndShippedDemoKeepTheirFinalRecordInBothBackends(@TempDir Path tmp) throws Exception {
        assertBackends(Path.of("src/test/resources/tool-agreement/export-layout.yaml"), 25, 24);
        assertBackends(Path.of("src/main/resources/demo/demo-quote-series.yaml"), 726, -1);
        Path closed = tmp.resolve("closed.yaml");
        Files.writeString(closed, Files.readString(Path.of("src/test/resources/tool-agreement/export-layout.yaml")) + "---\n");
        try (var h = HeapLogStore.fromFile(closed); var m = new MappedLogStore(closed)) {
            assertEquals(25, h.size()); assertEquals(25, m.size());
            assertEquals(0, h.trailingRecordsIncluded()); assertEquals(0, m.trailingRecordsIncluded());
        }
    }
    private static void assertBackends(Path path, int count, int lastValue) throws Exception {
        try (var h = HeapLogStore.fromFile(path); var m = new MappedLogStore(path);
             var rolledHeap = RolledLogStore.open(List.of(path), 100);
             var rolledMapped = RolledLogStore.open(List.of(path), 0)) {
            for (var store : List.of(h, m, rolledHeap, rolledMapped)) {
                assertEquals(count, store.size(), "ordinary export must include its EOF record: " + store.getClass());
                assertEquals(0, store.trailingRecordsPending());
                assertEquals(1, store.trailingRecordsIncluded());
                assertEquals(h.rawText(count - 1), store.rawText(count - 1));
                if (lastValue >= 0) assertTrue(store.rawText(count - 1).contains("value: " + lastValue));
            }
            assertEquals(-1, h.appendFrom(path), "snapshot EOF cannot silently become an immutable live index");
            var live = h.forFollow();
            assertEquals(count - 1, live.size()); assertEquals(1, live.trailingRecordsPending());
            assertEquals(0, live.trailingRecordsIncluded());
        }
    }
}
