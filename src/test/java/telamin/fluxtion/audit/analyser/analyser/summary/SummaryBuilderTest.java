package telamin.fluxtion.audit.analyser.analyser.summary;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SummaryBuilderTest {

    private static LogIndex index() {
        return new HeapLogStore(Samples.sample()).index();
    }

    @Test
    void groupsAllRecordsSortedByCountDescending() {
        LogIndex idx = index();
        List<SummaryRow> rows = SummaryBuilder.build(idx, new FilterState());
        long total = rows.stream().mapToLong(SummaryRow::count).sum();
        assertEquals(21, total);
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).count() >= rows.get(i).count(), "sorted by count desc");
        }
        SummaryRow scheduled = rows.stream()
                .filter(r -> r.dimension().equals("ScheduledTriggerNode")).findFirst().orElseThrow();
        assertEquals(3, scheduled.count());
    }

    @Test
    void spanIsMaxMinusMinLogTimeWithinGroup() {
        LogIndex idx = index();
        SummaryRow scheduled = SummaryBuilder.build(idx, new FilterState()).stream()
                .filter(r -> r.dimension().equals("ScheduledTriggerNode")).findFirst().orElseThrow();
        assertNotNull(scheduled.firstLog());
        assertNotNull(scheduled.lastLog());
        assertEquals(scheduled.lastLog() - scheduled.firstLog(), scheduled.spanMillis());
        assertTrue(scheduled.spanMillis() > 0);
    }

    @Test
    void respectsTheActiveFilter() {
        LogIndex idx = index();
        FilterState f = new FilterState();
        f.setDimensions(Set.of("ScheduledTriggerNode"));
        List<SummaryRow> rows = SummaryBuilder.build(idx, f);
        assertEquals(1, rows.size(), "only the selected group survives");
        assertEquals("ScheduledTriggerNode", rows.get(0).dimension());
        assertEquals(3, rows.get(0).count());
    }
}
