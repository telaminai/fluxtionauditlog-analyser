package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeapLogStoreTest {

    private static HeapLogStore store() {
        return new HeapLogStore(Samples.sample());
    }

    @Test
    void indexesAllRecords() {
        HeapLogStore s = store();
        assertEquals(21, s.size());
        assertEquals(s.size(), s.index().size());
    }

    @Test
    void indexColumnsMatchLazyRecords() {
        HeapLogStore s = store();
        LogIndex idx = s.index();
        for (int i = 0; i < s.size(); i++) {
            LogRecord r = s.record(i);
            assertEquals(idx.logTime(i), r.logTime(), "logTime column matches parsed record at row " + i);
            assertEquals(idx.eventTime(i), r.eventTime(), "eventTime column matches at row " + i);
            assertEquals(idx.dimension(i), r.eventDimension(), "dimension column matches at row " + i);
            assertEquals(idx.thread(i), r.thread());
            assertEquals(idx.logger(i), r.logger());
        }
    }

    @Test
    void reportsLogTimeRangeMatchingARecordScan() {
        HeapLogStore s = store();
        long expectMin = Long.MAX_VALUE, expectMax = Long.MIN_VALUE;
        for (int i = 0; i < s.size(); i++) {
            Long lt = s.record(i).logTime();
            assertNotNull(lt, "every sample record has a logTime");
            expectMin = Math.min(expectMin, lt);
            expectMax = Math.max(expectMax, lt);
        }
        assertEquals(expectMin, s.minLogTime(), "index min matches the earliest record (not necessarily row 0)");
        assertEquals(expectMax, s.maxLogTime());
        assertTrue(s.minLogTime() <= s.maxLogTime());
    }

    @Test
    void groupsByDerivedDimensionNotRawExportEvent() {
        Map<String, Integer> counts = store().index().dimensionCounts();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(21, total, "every record counted once");
        assertFalse(counts.containsKey("ExportFunctionAuditEvent"),
                "ExportFunctionAuditEvent is grouped by its callback, not the raw event");
        assertEquals(3, counts.get("ScheduledTriggerNode"));
        assertEquals(1, counts.get("LifecycleEvent"));
        assertEquals(2, counts.get("orderVenueConnected"));
        assertTrue(counts.getOrDefault("orderUpdate", 0) >= 8, "many orderUpdate callbacks");
    }

    @Test
    void firstRecordIsStartComplete() {
        assertEquals("StartComplete", store().record(0).eventToString());
    }
}
