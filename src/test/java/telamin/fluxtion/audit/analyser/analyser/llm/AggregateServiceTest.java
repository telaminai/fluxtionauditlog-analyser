package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code aggregate} query verb (spec-assistant-actions §4.1) over the bundled 21-record sample.
 * Covers index-path vs raw-scan filters, each metric × group-by, population echo, and empty safety.
 */
class AggregateServiceTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());
    private final LogIndex.Snapshot snap = store.index().snapshot();
    private final IntFunction<String> raw = store::rawText;

    @SuppressWarnings("unchecked")
    private Map<String, Object> agg(Map<String, Object> params) {
        return AggregateService.aggregate(snap, params, raw);
    }

    @Test
    void countOverAllIsTheRecordCountAndScanIsIndex() {
        Map<String, Object> r = agg(Map.of("metric", "count", "groupBy", "none"));
        assertEquals(21L, r.get("total"));
        List<?> buckets = (List<?>) r.get("buckets");
        assertEquals(1, buckets.size());
        Map<?, ?> pop = (Map<?, ?>) r.get("population");
        assertEquals(21, pop.get("records"));
        assertEquals("index", pop.get("scan"));
    }

    @Test
    void groupByDimensionSeesScheduledTriggerNode() {
        Map<String, Object> r = agg(Map.of("groupBy", "dimension"));
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) r.get("buckets");
        Map<String, Object> sched = buckets.stream()
                .filter(b -> "ScheduledTriggerNode".equals(b.get("key"))).findFirst().orElseThrow();
        assertEquals(3L, sched.get("count"));
        // dimension buckets are ordered by count descending
        long first = (long) buckets.get(0).get("count");
        long last = (long) buckets.get(buckets.size() - 1).get("count");
        assertTrue(first >= last);
    }

    @Test
    void indexDimensionFilterNarrowsPopulation() {
        Map<String, Object> r = agg(Map.of("metric", "count", "groupBy", "none",
                "filter", Map.of("dimensions", List.of("ScheduledTriggerNode"))));
        assertEquals(3L, r.get("total"));
        Map<?, ?> pop = (Map<?, ?>) r.get("population");
        assertEquals(3, pop.get("records"));
        assertEquals("index", pop.get("scan"));
    }

    @Test
    void textFilterIsRawScanAndMatchesNodeLogs() {
        // every record contains the literal "eventLogRecord"; a raw scan sees all 21
        Map<String, Object> all = agg(Map.of("filter", Map.of("text", "eventLogRecord")));
        assertEquals(21L, all.get("total"));
        assertEquals("raw", ((Map<?, ?>) all.get("population")).get("scan"));

        Map<String, Object> none = agg(Map.of("filter", Map.of("text", "zz-not-present-zz")));
        assertEquals(0L, none.get("total"));
    }

    @Test
    void textFilterWithoutARawSourceIsAnError() {
        // a silent 0 would be a confidently-wrong answer; the service throws so the dispatcher can
        // surface a structured ok:false the model can act on
        assertThrows(IllegalArgumentException.class, () -> AggregateService.aggregate(snap,
                Map.of("filter", Map.of("text", "eventLogRecord")), null));
    }

    @Test
    void unknownMetricOrGroupByIsRejectedNotCoerced() {
        assertThrows(IllegalArgumentException.class, () -> agg(Map.of("metric", "median")));
        assertThrows(IllegalArgumentException.class, () -> agg(Map.of("groupBy", "fortnight")));
    }

    @Test
    void timeBucketsSumToTheRecordCountAndAreIsoUtc() {
        Map<String, Object> r = agg(Map.of("groupBy", "hour"));
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) r.get("buckets");
        long sum = buckets.stream().mapToLong(b -> (long) b.get("count")).sum();
        assertEquals(21L, sum);
        assertTrue(((String) buckets.get(0).get("key")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:00Z"));
    }

    @Test
    void nanAndBreachMetricsMatchTheIndexFlags() {
        long expectedNan = 0, expectedBreach = 0;
        for (int i = 0; i < snap.size(); i++) {
            if (snap.hasNaN(i)) expectedNan++;
            if (snap.hasBreach(i)) expectedBreach++;
        }
        assertEquals(expectedNan, agg(Map.of("metric", "nan_count", "groupBy", "none")).get("total"));
        assertEquals(expectedBreach, agg(Map.of("metric", "breach_count", "groupBy", "none")).get("total"));
    }

    /**
     * PR #24: "when was the limit first breached?" was answered with the first value over the limit, but the
     * application logged its breach as an event. aggregate now says where its counted records begin and end, on the
     * same 0-based index read and goto take: on the demo log the first RiskBreachEvent is record 16.
     */
    @Test
    void aggregateSaysWhereItsCountedRecordsBeginAndEnd() throws Exception {
        var demo = HeapLogStore.fromFile(java.nio.file.Path.of("src/main/resources/demo/demo-quote-series.yaml"));
        var demoSnap = demo.index().snapshot();
        Map<String, Object> breaches = AggregateService.aggregate(demoSnap, Map.of("metric", "count", "groupBy", "none",
                "filter", Map.of("dimensions", List.of("RiskBreachEvent"))), demo::rawText);
        assertEquals(160L, breaches.get("total"), "control: the demo log's RiskBreachEvent count: " + breaches);
        assertEquals(16, breaches.get("firstRecordIndex"), "the first counted RiskBreachEvent: " + breaches);
        assertTrue(demo.rawText(16).contains("RiskBreachEvent"), "record 16 is the application's own breach event");
        int last = (Integer) breaches.get("lastRecordIndex");
        assertTrue(demo.rawText(last).contains("RiskBreachEvent") && last > 16, "and the last one: " + last);
        assertEquals(722, last, "the last counted RiskBreachEvent, as the PR states it: " + breaches);
        // "the same index read and goto take" — asked of read itself, not of rawText: the record read returns at
        // firstRecordIndex is the application's breach event.
        Map<String, Object> read = ReadService.read(demoSnap, Map.of("recordIndex", 16, "count", 1), demo::rawText);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) read.get("records");
        assertEquals(16, records.get(0).get("recordIndex"), "read anchors on the same index: " + read);
        assertTrue(String.valueOf(records.get(0).get("text")).contains("event: RiskBreachEvent"),
                "and returns the breach event there: " + records.get(0));
        Map<String, Object> all = AggregateService.aggregate(demoSnap, Map.of("metric", "count", "groupBy", "none"), demo::rawText);
        assertEquals(0, all.get("firstRecordIndex"));
        assertEquals(demoSnap.size() - 1, all.get("lastRecordIndex"));
        Map<String, Object> none = AggregateService.aggregate(demoSnap, Map.of("metric", "breach_count", "groupBy", "none"), demo::rawText);
        assertEquals(0L, none.get("total"), "control: this log logs no breach flag");
        assertFalse(none.containsKey("firstRecordIndex"), "nothing counted, no position: " + none);
    }

    private static String rec(Long logTime, String nodeLog) {
        return "eventLogRecord:\n" + (logTime == null ? "" : "  logTime: " + logTime + "\n")
                + "  groupingId: null\n  event: Quote\n  nodeLogs:\n    - " + nodeLog + "\n---\n";
    }

    /**
     * PR #24 review: the positions are of COUNTED records — not of every record the filter admits. Two rows the
     * scan visits are not counted: a row the metric does not match (breach_count on a record with no breach flag),
     * and an untimed row under a time grouping. Neither may become the first or last position.
     */
    @Test
    void thePositionsAreOfCountedRecordsNotOfEveryRecordVisited() {
        HeapLogStore s = new HeapLogStore(
                rec(null, "riskMonitor: { limitBreach: false}")        // 0: untimed, no breach
                + rec(1000L, "riskMonitor: { limitBreach: true}")      // 1: timed, breach
                + rec(2000L, "riskMonitor: { limitBreach: false}")     // 2: timed, no breach
                + rec(null, "riskMonitor: { limitBreach: true}"));     // 3: untimed, breach
        LogIndex.Snapshot sn = s.index().snapshot();
        assertEquals(4, sn.size(), "precondition: four records");

        Map<String, Object> all = AggregateService.aggregate(sn, Map.of("metric", "count", "groupBy", "none"), s::rawText);
        assertEquals(0, all.get("firstRecordIndex"), "control: groupBy none counts the untimed rows: " + all);
        assertEquals(3, all.get("lastRecordIndex"), "control: " + all);

        Map<String, Object> hourly = AggregateService.aggregate(sn, Map.of("metric", "count", "groupBy", "hour"), s::rawText);
        assertEquals(2L, hourly.get("total"), "precondition: a time grouping skips the untimed rows: " + hourly);
        assertEquals(1, hourly.get("firstRecordIndex"), "an untimed row skipped by the time grouping is not first: " + hourly);
        assertEquals(2, hourly.get("lastRecordIndex"), "nor last: " + hourly);

        Map<String, Object> breach = AggregateService.aggregate(sn, Map.of("metric", "breach_count", "groupBy", "none"), s::rawText);
        assertEquals(2L, breach.get("total"), "precondition: two records flag a breach: " + breach);
        assertEquals(1, breach.get("firstRecordIndex"), "the first FLAGGED record, not the first visited: " + breach);
        assertEquals(3, breach.get("lastRecordIndex"), breach.toString());
    }

    @Test
    void ratePerMinExposesARate() {
        Map<String, Object> r = agg(Map.of("metric", "rate_per_min", "groupBy", "none"));
        assertTrue(r.containsKey("rate_per_min"), "overall rate present for groupBy:none");
        assertEquals(21L, r.get("total"));
    }

    @Test
    void futureWindowYieldsAnEmptyButWellFormedResult() {
        Map<String, Object> r = agg(Map.of("groupBy", "dimension",
                "filter", Map.of("from", Long.MAX_VALUE / 2)));
        assertEquals(0L, r.get("total"));
        assertTrue(((List<?>) r.get("buckets")).isEmpty());
        assertEquals(0, ((Map<?, ?>) r.get("population")).get("records"));
    }
}
