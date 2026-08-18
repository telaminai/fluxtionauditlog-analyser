package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-R3's second half: within-file monotonicity, finally CHECKED — for single files as much as sets
 * (A2 was always load-bearing; sets are just where it breaks in practice). Violations are reported
 * with anchors and counted; records are never re-sorted.
 */
class TimeOrderValidatorTest {

    @TempDir
    Path dir;

    private static String records(long... logTimes) {
        StringBuilder sb = new StringBuilder("---\n");
        for (long t : logTimes) {
            sb.append("#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: ").append(t)
                    .append("\n  event: e\n---\n");
        }
        return sb.toString();
    }

    @Test
    void aCleanSingleFileIsClean() {
        var store = new HeapLogStore(records(100, 200, 300));
        assertTrue(TimeOrderValidator.validate(store.index()).isClean());
    }

    @Test
    void backwardsRecordsAreCountedWithAFirstAnchor_neverResorted() {
        var store = new HeapLogStore(records(100, 300, 200, 250, 400, 150));
        var report = TimeOrderValidator.validate(store.index());
        assertFalse(report.isClean());
        var v = report.violations().get(0);
        assertEquals(TimeOrderReport.Kind.OUT_OF_ORDER, v.kind());
        assertEquals(2, v.recordIndex(), "first violation: 200 after 300, at record 2");
        assertTrue(v.message().contains("3 record(s)"), "200, 250 and 150 all precede the running max: "
                + v.message());
        assertEquals(100L, store.index().logTime(0), "the index itself is untouched — report, never repair");
    }

    @Test
    void untimedRecordsOrderNothing() {
        String withUntimed = "---\n#x [t] INFO L\neventLogRecord:\n  event: boundary\n---\n" + records(100, 200);
        var store = new HeapLogStore(withUntimed);
        assertTrue(TimeOrderValidator.validate(store.index()).isClean());
    }

    @Test
    void rolledSetsAreCheckedPerFile_aRollBoundaryIsNotAViolation() throws IOException {
        // file 2 starts EARLIER than file 1 ends? No — per-file checks must not fire across the
        // boundary; the CROSS-file check is the resolver's job. Build a set where each file is
        // internally clean but file 2's first time < file 1's last: within-file must stay clean.
        Path a = dir.resolve("m.log.1");
        Path b = dir.resolve("m.log.2");
        Files.writeString(a, records(100, 300));
        Files.writeString(b, records(250, 400));   // overlaps a — resolver territory, not this check
        var store = RolledLogStore.open(List.of(a, b), 512);
        assertTrue(TimeOrderValidator.validate(store.index()).isClean(),
                "within-file monotonicity holds in both members; the overlap is the RESOLVER's finding");

        // and a genuinely disordered member is attributed to ITS file
        Path c = dir.resolve("m.log.3");
        Files.writeString(c, records(500, 450));
        var dirty = RolledLogStore.open(List.of(a, b, c), 512);
        var report = TimeOrderValidator.validate(dirty.index());
        assertEquals(1, report.violations().size());
        assertTrue(report.violations().get(0).message().contains("m.log.3"));
    }
}
