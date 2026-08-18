package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ReadService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The composite store (M30.2): one gap-free global recordIndex, per-file backends, FILE-LOCAL byte
 * offsets with the file id carried in the merged index (D-R2), and the corrected D-R6 — the heap
 * threshold applies to the SET TOTAL.
 */
class RolledLogStoreTest {

    @TempDir
    Path dir;

    private static String records(String event, long... logTimes) {
        StringBuilder sb = new StringBuilder("---\n");
        for (long t : logTimes) {
            sb.append("#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: ").append(t)
                    .append("\n  event: ").append(event).append("\n---\n");
        }
        return sb.toString();
    }

    private List<Path> threeFiles() throws IOException {
        Path a = dir.resolve("m.log.1");
        Path b = dir.resolve("m.log.2");
        Path c = dir.resolve("m.log");
        Files.writeString(a, records("A", 100, 110));
        Files.writeString(b, records("B", 200, 210, 220));
        Files.writeString(c, records("C", 300));
        return List.of(a, b, c);
    }

    @Test
    void oneLogicalLog_globalIndexIsGapFreeAndFileAware() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        assertEquals(6, store.size());
        var idx = store.index();
        assertEquals(3, idx.fileCount());
        assertEquals(List.of("m.log.1", "m.log.2", "m.log"), idx.files());
        assertEquals(0, idx.fileId(0));
        assertEquals(1, idx.fileId(2));
        assertEquals(2, idx.fileId(5));
        assertEquals(100L, idx.logTime(0));
        assertEquals(300L, idx.logTime(5));
        assertEquals(100L, store.minLogTime());
        assertEquals(300L, store.maxLogTime());
        // record()/rawText() route to the right member with member-local rows
        assertTrue(store.rawText(2).contains("event: B"), "global row 2 is m.log.2's first record");
        assertEquals("C", store.record(5).event());
        // dimension interning survived the column-copy merge
        assertEquals(6, idx.dimensionCounts().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void offsetsStayFileLocal_soTheSameOffsetExistsInSeveralFiles() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        var idx = store.index();
        // each member starts at its own "---\n": the first record of EVERY file has the same offset
        assertEquals(idx.offset(0), idx.offset(2), "file-local offsets — a real offset into a real file");
        assertEquals(idx.offset(0), idx.offset(5));
    }

    @Test
    void bareByteOffsetReadsAreRefusedWithTheFileList_fileDiscriminatorResolves() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        var snap = store.index().snapshot();

        var e = assertThrows(IllegalArgumentException.class,
                () -> ReadService.read(snap, Map.of("byteOffset", 4L), store::rawText));
        assertTrue(e.getMessage().contains("rolled set"), e.getMessage());
        assertTrue(e.getMessage().contains("m.log.2"), "the refusal lists the files: " + e.getMessage());

        Map<String, Object> out = ReadService.read(snap,
                Map.of("byteOffset", 4L, "file", "m.log.2", "count", 1), store::rawText);
        assertEquals(2, out.get("anchor"), "offset 4 IN m.log.2 is its first record = global row 2");
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) out.get("records");
        assertEquals("m.log.2", rows.get(0).get("file"), "every row names its member file");
    }

    @Test
    void theHeapThresholdAppliesToTheSetTotal() throws IOException {
        // three files of ~200 bytes each; a threshold of 0 MB puts the TOTAL over → all mapped (D-R6)
        RolledLogStore mapped = RolledLogStore.open(threeFiles(), 0);
        assertEquals(6, mapped.size(), "mapped members behave identically");
        assertTrue(mapped.rawText(0).contains("event: A"));
        mapped.close();
    }

    @Test
    void recordIndexAnchorsNeedNoFileAndKeepWorking() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        Map<String, Object> out = ReadService.read(store.index().snapshot(),
                Map.of("recordIndex", 3, "count", 1), store::rawText);
        assertEquals(3, out.get("anchor"), "recordIndex is global and gap-free — the primary anchor");
    }
}
