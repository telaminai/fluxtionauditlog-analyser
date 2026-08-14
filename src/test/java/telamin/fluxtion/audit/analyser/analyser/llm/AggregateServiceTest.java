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
