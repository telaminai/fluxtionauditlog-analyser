package telamin.fluxtion.audit.analyser.analyser.filter;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FilterStateTest {

    private static LogIndex index() {
        return new HeapLogStore(Samples.sample()).index();
    }

    private static int passing(LogIndex idx, FilterState f) {
        int n = 0;
        for (int i = 0; i < idx.size(); i++) if (f.test(idx, i)) n++;
        return n;
    }

    @Test
    void emptyFilterPassesEverything() {
        LogIndex idx = index();
        assertEquals(idx.size(), passing(idx, new FilterState()));
    }

    @Test
    void nullDimensionsMeansAllButEmptyMeansNone() {
        LogIndex idx = index();
        FilterState f = new FilterState();
        f.setDimensions(Set.of());        // "Select none" → no rows pass
        assertEquals(0, passing(idx, f));
        f.setDimensions(null);            // cleared → all rows pass again
        assertEquals(idx.size(), passing(idx, f));
    }

    @Test
    void dimensionFilterRestrictsToSelectedGroups() {
        LogIndex idx = index();
        FilterState f = new FilterState();
        f.setDimensions(Set.of("ScheduledTriggerNode"));
        assertEquals(3, passing(idx, f), "3 ScheduledTriggerNode records in the sample");
    }

    @Test
    void textFilterIsCaseInsensitiveOverEventToString() {
        LogIndex idx = index();
        int expectedOrderUpdate = idx.dimensionCounts().getOrDefault("orderUpdate", 0);
        FilterState f = new FilterState();
        f.setText("ORDERUPDATE");
        assertEquals(expectedOrderUpdate, passing(idx, f));
        assertTrue(expectedOrderUpdate > 0);
    }

    @Test
    void textFilterSearchesNodeLogsViaTextSource() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        LogIndex idx = store.index();
        FilterState f = new FilterState();
        f.setTextSource(store::rawText);
        // "positionNode" appears only inside a nodeLogs entry, never in eventToString/thread
        f.setText("positionNode");
        int viaRaw = passing(idx, f);
        assertTrue(viaRaw >= 1, "matches the record whose nodeLogs contains positionNode");

        FilterState noSource = new FilterState();
        noSource.setText("positionNode");
        assertEquals(0, passing(idx, noSource), "without a text source, nodeLogs are not searched");
    }

    @Test
    void timeRangeIsInclusiveAndNarrows() {
        LogIndex idx = index();
        Long min = null, max = null;
        for (int i = 0; i < idx.size(); i++) {
            Long lt = idx.logTime(i);
            if (lt == null) continue;
            min = (min == null) ? lt : Math.min(min, lt);
            max = (max == null) ? lt : Math.max(max, lt);
        }
        FilterState full = new FilterState();
        full.setTimeRange(min, max);
        assertEquals(idx.size(), passing(idx, full), "full range keeps all");

        FilterState narrow = new FilterState();
        narrow.setTimeRange(max, max);
        assertTrue(passing(idx, narrow) >= 1 && passing(idx, narrow) < idx.size(), "narrow range drops rows");
    }

    @Test
    void rawEventGroupingDiffersFromCallbackGrouping() {
        LogIndex idx = index();
        FilterState f = new FilterState();
        f.setGroupMode(FilterState.GroupMode.RAW_EVENT);
        f.setDimensions(Set.of("ExportFunctionAuditEvent"));
        assertEquals(17, passing(idx, f), "17 ExportFunctionAuditEvents collapse under raw-event grouping");
    }

    @Test
    void listenersFireOnChange() {
        AtomicInteger hits = new AtomicInteger();
        FilterState f = new FilterState();
        f.addListener(hits::incrementAndGet);
        f.setText("x");
        f.setDimensions(Set.of("a"));
        f.setTimeRange(1L, 2L);
        assertEquals(3, hits.get());
    }
}
