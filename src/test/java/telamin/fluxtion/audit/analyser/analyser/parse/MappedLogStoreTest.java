package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M7: byte framer + memory-mapped store must match the in-heap store on the same file. */
class MappedLogStoreTest {

    private static Path sampleFile(Path dir) throws IOException {
        Path f = dir.resolve("sample.yml");
        Files.writeString(f, Samples.sample(), StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void byteFramerFramesRecordsWithReadableOffsets() throws IOException {
        Path f = sampleFile(Files.createTempDirectory("mls"));
        byte[] all = Files.readAllBytes(f);
        List<long[]> spans = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        ByteRecordFramer.frame(f, (offset, length, text) -> {
            spans.add(new long[]{offset, length});
            texts.add(text);
        });
        assertEquals(21, spans.size());
        for (int i = 0; i < spans.size(); i++) {
            long off = spans.get(i)[0];
            int len = (int) spans.get(i)[1];
            String slice = new String(all, (int) off, len, StandardCharsets.UTF_8);
            assertEquals(texts.get(i), slice, "byte offset+length re-slices the exact record");
        }
    }

    @Test
    void mappedStoreMatchesHeapStore() throws IOException {
        Path f = sampleFile(Files.createTempDirectory("mls"));
        HeapLogStore heap = new HeapLogStore(Samples.sample());
        try (MappedLogStore mapped = new MappedLogStore(f)) {
            assertEquals(heap.size(), mapped.size());
            assertEquals(heap.minLogTime(), mapped.minLogTime());
            assertEquals(heap.maxLogTime(), mapped.maxLogTime());
            assertEquals(heap.index().dimensionCounts(), mapped.index().dimensionCounts());
            for (int i = 0; i < heap.size(); i++) {
                assertEquals(heap.record(i).eventToString(), mapped.record(i).eventToString(), "row " + i);
                assertEquals(heap.record(i).nodeLogs().size(), mapped.record(i).nodeLogs().size(), "row " + i);
            }
            assertEquals("StartComplete", mapped.record(0).eventToString());
        }
    }

    @Test
    void factoryPicksBackendByThreshold() throws IOException {
        Path f = sampleFile(Files.createTempDirectory("mls"));
        assertInstanceOf(HeapLogStore.class, LogStores.open(f, 500), "small file → heap");
        try (LogStore s = LogStores.open(f, /*thresholdMb*/ 0)) {   // force below-threshold=false
            assertInstanceOf(MappedLogStore.class, s, "threshold 0 → memory-mapped");
            assertEquals(21, s.size());
        }
    }

    @Test
    void handlesCrlf() throws IOException {
        Path dir = Files.createTempDirectory("mls");
        Path f = dir.resolve("crlf.yml");
        Files.writeString(f, "---\r\n#00:00:00.000 [t] INFO L\r\neventLogRecord:\r\n  logTime: 5\r\n---\r\n");
        try (MappedLogStore s = new MappedLogStore(f)) {
            assertEquals(1, s.size());
            assertEquals(5L, s.record(0).logTime());
        }
    }
}
