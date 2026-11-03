package telamin.fluxtion.audit.analyser.analyser.parse;

import com.telamin.fluxtion.runtime.audit.AuditStreamHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key: runs by run key ({@code serverId + runId + group/name#generation}). A new segment only when the
 * key changes — spec acceptance 6 and 7, and the owner's rules of 2026-11-03.
 */
class RunSegmentsTest {

    @TempDir
    Path dir;

    static String header(String serverId, String runId, String generation) {
        Map<String, String> host = new LinkedHashMap<>();
        if (runId != null) {
            host.put("serverId", serverId);
            host.put("runId", runId);
            host.put("group", "pricing");
            host.put("name", "quoter");
            host.put("generation", generation);
            host.put("mongooseVersion", "1.1.2");
        }
        return new AuditStreamHeader("com.acme.TradeProcessor", "5f3c0a9e", "1.0.84", "1.1.4",
                "2026-11-03T09:15:02.118Z", host) + "\n";
    }

    static String record(long t) {
        return "eventLogRecord:\n    logTime: " + t + "\n    event: Tick\n    nodeLogs:\n    - book: { mid: 1.5}\n";
    }

    private List<RunSegments.Segment> segmentsOf(String log) {
        HeapLogStore s = new HeapLogStore(log);
        return RunSegments.of(s.streamHeaders(), s.size());
    }

    @Test
    @DisplayName("Acceptance 6: a static deployment restarted twice is three runs, told apart by runId, each #1")
    void aStaticDeploymentRestartedTwiceIsThreeRuns() {
        String log = "---\n" + header("pricing-1", "2c41aa00-1111", "1") + "---\n" + record(1) + "---\n" + record(2)
                + "---\n" + header("pricing-1", "7d02bb00-2222", "1") + "---\n" + record(3)
                + "---\n" + header("pricing-1", "9e13cc00-3333", "1") + "---\n" + record(4) + "---\n" + record(5) + "---\n";
        List<RunSegments.Segment> runs = segmentsOf(log);
        assertEquals(3, runs.size(), runs.toString());
        assertEquals(List.of("pricing-1 · 2c41aa00 · pricing/quoter#1", "pricing-1 · 7d02bb00 · pricing/quoter#1",
                "pricing-1 · 9e13cc00 · pricing/quoter#1"), runs.stream().map(RunSegments.Segment::label).toList());
        assertEquals(List.of(0, 2, 3), runs.stream().map(RunSegments.Segment::firstRecord).toList());
        assertEquals(List.of(2, 1, 2), runs.stream().map(RunSegments.Segment::records).toList());
        assertEquals("records 1–2", runs.get(0).span());
        assertEquals("record 3", runs.get(1).span());
    }

    @Test
    @DisplayName("A header re-sent with an equal run key (a sink change, a roll) does not split the run")
    void aReSentHeaderWithAnEqualKeyIsTheSameRun() {
        String log = "---\n" + header("s", "run-1", "1") + "---\n" + record(1) + "---\n" + header("s", "run-1", "1")
                + "---\n" + record(2) + "---\n";
        List<RunSegments.Segment> runs = segmentsOf(log);
        assertEquals(1, runs.size());
        assertEquals(2, runs.get(0).records());
        // a new generation inside one process IS a new run (a replacement)
        String replaced = "---\n" + header("s", "run-1", "1") + "---\n" + record(1) + "---\n" + header("s", "run-1", "2")
                + "---\n" + record(2) + "---\n";
        assertEquals(2, segmentsOf(replaced).size());
    }

    @Test
    @DisplayName("An empty host map is one run, unless a later header names a different key")
    void anEmptyHostIsOneRunUntilAKeyAppears() {
        String empty = "---\n" + header(null, null, null) + "---\n" + record(1) + "---\n" + header(null, null, null)
                + "---\n" + record(2) + "---\n";
        List<RunSegments.Segment> one = segmentsOf(empty);
        assertEquals(1, one.size());
        assertEquals("no run identity stated", one.get(0).label());
        String later = empty + header("s", "run-9", "1") + "---\n" + record(3) + "---\n";
        assertEquals(2, segmentsOf(later).size());
    }

    @Test
    @DisplayName("Records before the first header form a leading run of their own; no header, no runs")
    void recordsBeforeTheFirstHeaderAndNoHeaders() {
        List<RunSegments.Segment> runs = segmentsOf("---\n" + record(1) + "---\n" + header("s", "r", "1") + "---\n" + record(2) + "---\n");
        assertEquals(2, runs.size());
        assertFalse(runs.get(0).headed());
        assertEquals("before the first stream header", runs.get(0).label());
        assertTrue(segmentsOf("---\n" + record(1) + "---\n").isEmpty());
    }

    @Test
    @DisplayName("A malformed header states no run and keeps the one in force")
    void aMalformedHeaderKeepsTheRun() {
        String log = "---\n" + header("s", "run-1", "1") + "---\n" + record(1)
                + "---\nauditStreamHeader:\n    schema: 9\n---\n" + record(2) + "---\n";
        assertEquals(1, segmentsOf(log).size());
    }

    @Test
    @DisplayName("Acceptance 7: a capture across a daily roll is one run, one segment, across both files")
    void aRolledCaptureWithOneRunKeyIsOneSegment() throws IOException {
        // Mongoose repeats the header at the start of each roll cycle, with the same run identity
        Path day1 = dir.resolve("audit.2026-11-02.yaml");
        Path day2 = dir.resolve("audit.2026-11-03.yaml");
        Files.writeString(day1, header("pricing-1", "2c41aa00-1111", "1") + record(1000) + "---\n" + record(2000));
        Files.writeString(day2, header("pricing-1", "2c41aa00-1111", "1") + record(3000));
        try (RolledLogStore set = RolledLogStore.open(List.of(day1, day2), 100)) {
            assertEquals(3, set.size());
            assertEquals(2, set.streamHeaders().size(), "one header per roll cycle");
            List<RunSegments.Segment> runs = RunSegments.of(set.streamHeaders(), set.size());
            assertEquals(1, runs.size(), runs.toString());
            assertEquals(3, runs.get(0).records());
        }
    }
}
