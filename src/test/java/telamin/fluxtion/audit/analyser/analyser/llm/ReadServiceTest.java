package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

class ReadServiceTest {

    private static String rec(int n) {
        return "#00:00:0" + (n % 10) + ".000 [t] INFO L\neventLogRecord:\n"
                + "  logTime: " + n + "\n  event: e" + n + "\n---\n";
    }

    /** A store of {@code count} records (logTime 0..count-1). */
    private static HeapLogStore store(int count) throws IOException {
        StringBuilder sb = new StringBuilder("---\n");
        for (int i = 0; i < count; i++) sb.append(rec(i));
        Path p = Files.createTempFile("read", ".log");
        p.toFile().deleteOnExit();
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
        return HeapLogStore.fromFile(p);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> out) {
        return (List<Map<String, Object>>) out.get("records");
    }

    @Test
    void readAroundRecordIndexCentresTheWindow() throws IOException {
        HeapLogStore s = store(40);
        LogIndex.Snapshot snap = s.index().snapshot();
        IntFunction<String> raw = s::rawText;

        Map<String, Object> out = ReadService.read(snap, Map.of("recordIndex", 20, "count", 5), raw);
        assertEquals(40, out.get("total"));
        assertEquals(20, out.get("anchor"));
        assertEquals(18, out.get("from"));
        assertEquals(22, out.get("to"));
        List<Map<String, Object>> recs = records(out);
        assertEquals(5, recs.size());
        assertEquals(18, recs.get(0).get("recordIndex"));
        assertTrue(((String) recs.get(2).get("text")).contains("logTime: 20"), "centre record text");
    }

    @Test
    void byteOffsetAnchorResolvesToItsRecord() throws IOException {
        HeapLogStore s = store(40);
        LogIndex.Snapshot snap = s.index().snapshot();
        long midOfRecord7 = snap.offset(7) + 3;   // a byte inside record 7

        Map<String, Object> out = ReadService.read(snap, Map.of("byteOffset", midOfRecord7, "count", 1), s::rawText);
        assertEquals(7, out.get("anchor"));
        assertEquals(1, records(out).size());
        assertEquals(7, records(out).get(0).get("recordIndex"));
    }

    @Test
    void beforeAndAfterControlTheWindow() throws IOException {
        HeapLogStore s = store(40);
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 10, "before", 2, "after", 1), s::rawText);
        assertEquals(8, out.get("from"));
        assertEquals(11, out.get("to"));
    }

    @Test
    void windowIsRateLimited() throws IOException {
        HeapLogStore s = store(100);
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 0, "after", 100), s::rawText);
        assertEquals(ReadService.MAX_COUNT, records(out).size(), "capped at MAX_COUNT");
        assertTrue(out.containsKey("note"));
    }

    @Test
    void beforeHeavyReadStillIncludesTheAnchor() throws IOException {
        HeapLogStore s = store(200);
        // before ≫ MAX; the anchor must not be trimmed away by the rate limit
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 150, "before", 100), s::rawText);
        assertEquals(ReadService.MAX_COUNT, records(out).size());
        int from = (int) out.get("from");
        int to = (int) out.get("to");
        assertTrue(from <= 150 && 150 <= to, "the anchor 150 must be within [" + from + "," + to + "]");
        assertEquals(150, to, "before-heavy window ends at the anchor");
        assertTrue(out.containsKey("note"));
    }

    @Test
    void missingAnchorThrows() throws IOException {
        HeapLogStore s = store(5);
        assertThrows(IllegalArgumentException.class,
                () -> ReadService.read(s.index().snapshot(), Map.of("count", 3), s::rawText));
    }

    @Test
    void clampsAtTheEnds() throws IOException {
        HeapLogStore s = store(10);
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 0, "count", 5), s::rawText);
        assertEquals(0, out.get("from"));   // can't go before 0
        assertEquals(2, out.get("to"));     // anchor 0, centred count 5 → before=2 clamped, after=2
        assertEquals(0, records(out).get(0).get("recordIndex"));
    }
}
