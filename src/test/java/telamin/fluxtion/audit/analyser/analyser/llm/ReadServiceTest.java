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

    // ---- time anchors (M26.2) ----------------------------------------------------------------

    private static IntFunction<Long> times(Long... t) {
        return i -> t[i];
    }

    @Test
    void rowAtOrBeforeFindsTheLatestRecordNotAfterTheMoment() {
        IntFunction<Long> t = times(100L, 200L, 300L, 400L);
        assertEquals(2, ReadService.rowAtOrBefore(4, t, 350), "between two records → the earlier one");
        assertEquals(1, ReadService.rowAtOrBefore(4, t, 200), "exact match");
        assertEquals(3, ReadService.rowAtOrBefore(4, t, 9_999), "after the last → the last");
    }

    @Test
    void rowAtOrBeforeClampsToTheFirstTimedRecord() {
        assertEquals(0, ReadService.rowAtOrBefore(3, times(100L, 200L, 300L), 5));
        assertEquals(1, ReadService.rowAtOrBefore(3, times(null, 200L, 300L), 5), "untimed head is skipped");
    }

    @Test
    void rowAtOrBeforeSkipsUntimedRecords() {
        IntFunction<Long> t = times(100L, null, null, 400L);
        assertEquals(0, ReadService.rowAtOrBefore(4, t, 350), "untimed rows can never anchor");
        assertEquals(3, ReadService.rowAtOrBefore(4, t, 400));
    }

    @Test
    void rowAtOrBeforeIsMinusOneWhenNothingIsTimed() {
        assertEquals(-1, ReadService.rowAtOrBefore(3, times(null, null, null), 100));
    }

    @Test
    void atAnchorsAReadByTime() throws IOException {
        HeapLogStore s = store(40);   // fixture: logTime == recordIndex
        Map<String, Object> out = ReadService.read(s.index().snapshot(), Map.of("at", 20, "count", 3), s::rawText);
        assertEquals(20, out.get("anchor"));
        assertEquals(3, records(out).size());
    }

    @Test
    void atBeforeTheFirstTimedRecordAnchorsToItAndSaysSo() throws IOException {
        HeapLogStore s = store(10);
        Map<String, Object> out = ReadService.read(s.index().snapshot(), Map.of("at", -50, "count", 1), s::rawText);
        assertEquals(0, out.get("anchor"));
        assertTrue(((String) out.get("note")).contains("after 'at'"), "the clamp is declared, not silent");
    }

    // ---- field projection (M26.3) ------------------------------------------------------------

    /** Two records; nodeA logs twice in the first (last occurrence must win, as in graphing). */
    private static HeapLogStore fieldsStore() {
        return new HeapLogStore("""
                ---
                #00:00:01.000 [t] INFO L
                eventLogRecord:
                  logTime: 1000
                  event: PriceEvent
                  nodeLogs:
                    - nodeA: { price: 1.5, side: bid}
                    - nodeB: { qty: 5}
                    - nodeA: { price: 2.5}
                ---
                #00:00:02.000 [t] INFO L
                eventLogRecord:
                  logTime: 2000
                  event: OtherEvent
                  nodeLogs:
                    - nodeB: { qty: 7}
                ---
                """);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fieldsProjectCompactRowsWithLastOccurrenceSemantics() {
        HeapLogStore s = fieldsStore();
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 0, "after", 1, "fields", List.of("nodeA.price", "nodeB.qty")),
                s::rawText);
        List<Map<String, Object>> recs = records(out);
        assertEquals(2, recs.size());

        Map<String, Object> r0 = recs.get(0);
        assertNull(r0.get("text"), "projection replaces raw text");
        assertEquals("PriceEvent", r0.get("event"));
        assertEquals(0, r0.get("recordIndex"));
        Map<String, String> v0 = (Map<String, String>) r0.get("values");
        assertEquals("2.5", v0.get("nodeA.price"), "nodeA logs twice — the LAST occurrence wins, as in graphing");
        assertEquals("5", v0.get("nodeB.qty"));

        Map<String, String> v1 = (Map<String, String>) recs.get(1).get("values");
        assertEquals(Map.of("nodeB.qty", "7"), v1, "a field the record never logged is absent, not null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wildcardTakesEveryKeyTheInstanceLogged() {
        HeapLogStore s = fieldsStore();
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 0, "count", 1, "fields", List.of("nodeA.*")), s::rawText);
        Map<String, String> v = (Map<String, String>) records(out).get(0).get("values");
        assertEquals(Map.of("nodeA.price", "2.5", "nodeA.side", "bid"), v);
    }

    @Test
    void fieldsNeverSeenAreNamedInTheReply() {
        HeapLogStore s = fieldsStore();
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 0, "after", 1, "fields", List.of("nodeA.price", "ghost.value")),
                s::rawText);
        assertTrue(((String) out.get("note")).contains("ghost.value"),
                "a projected field that matched nothing is declared, never silently empty");
    }

    @Test
    void rawTextStaysTheDefault() {
        HeapLogStore s = fieldsStore();
        Map<String, Object> out = ReadService.read(s.index().snapshot(),
                Map.of("recordIndex", 0, "count", 1), s::rawText);
        Map<String, Object> r0 = records(out).get(0);
        assertNotNull(r0.get("text"));
        assertNull(r0.get("values"));
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
