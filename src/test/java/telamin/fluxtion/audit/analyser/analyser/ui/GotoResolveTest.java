package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code goto}/{@code flag} offset resolver (spec §4.4): a byte offset landing <b>inside</b> a record
 * (as a grep match does) resolves to that record; out-of-range offsets <b>clamp</b> to the first/last
 * record rather than missing.
 */
class GotoResolveTest {

    private final LogIndex idx = new HeapLogStore(Samples.sample()).index();

    @Test
    void exactStartResolvesToThatRecord() {
        for (int r = 0; r < idx.size(); r++) {
            assertEquals(r, ActionExecutor.floorRow(idx, idx.offset(r)), "the record's own start resolves to it");
        }
    }

    @Test
    void midRecordOffsetFloorsToTheContainingRecord() {
        int r = 5;
        long mid = idx.offset(r) + idx.length(r) / 2;   // a byte inside record 5
        assertEquals(r, ActionExecutor.floorRow(idx, mid), "an offset inside a record resolves to that record");
    }

    @Test
    void outOfRangeClampsToFirstAndLast() {
        assertEquals(0, ActionExecutor.floorRow(idx, Long.MIN_VALUE / 2), "before the first record → clamp to 0");
        assertEquals(idx.size() - 1, ActionExecutor.floorRow(idx, Long.MAX_VALUE / 2), "past EOF → clamp to last");
    }

    @Test
    void recordIndexClamps() {
        assertEquals(0, ActionExecutor.clampRow(idx, -3));
        assertEquals(idx.size() - 1, ActionExecutor.clampRow(idx, 9999));
        assertEquals(4, ActionExecutor.clampRow(idx, 4));
    }
}
