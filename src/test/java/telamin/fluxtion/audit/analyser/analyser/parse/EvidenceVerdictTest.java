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
 * The shared evidence-set judgement through REAL stores (independent review of slice 5: F1, F5, F6, F12, and
 * acceptance 5 and 8 driven through a store rather than hand-built header lists — F10).
 */
class EvidenceVerdictTest {

    @TempDir
    Path dir;

    static final String A = "aaaa1111aaaa1111";
    static final String B = "bbbb2222bbbb2222";

    static String header(String fingerprint, String runId, String builder) {
        Map<String, String> host = new LinkedHashMap<>();
        if (runId != null) {
            host.put("serverId", "pricing-1");
            host.put("runId", runId);
            host.put("group", "pricing");
            host.put("name", "quoter");
            host.put("generation", "1");
        }
        return new AuditStreamHeader("com.acme.TradeProcessor", fingerprint, builder, "1.1.4",
                "2026-11-03T09:15:02.118Z", host) + "\n";
    }

    static String header(String fingerprint, String runId) {
        return header(fingerprint, runId, "1.0.84");
    }

    static String record(long t) {
        return "eventLogRecord:\n    logTime: " + t + "\n    event: Tick\n    nodeLogs:\n    - book: { mid: 1.5}\n";
    }

    static EvidenceVerdict.LogShape shape(LogStore s) {
        return EvidenceVerdict.shape(s.streamHeaders(), s.size(), s.streamBreaks());
    }

    @Test
    @DisplayName("Review F1, R3a: records after a stream-end marker with no header are not verified, and a run of their own")
    void recordsAfterAMarkerWithNoHeaderAreUnverified() throws IOException {
        Path p = dir.resolve("r3a.yaml");
        Files.writeString(p, "---\n" + header(A, "run-1") + "---\n" + record(1) + "---\n" + record(2)
                + "---\neventLogRecord:\n  streamEnd: stopping\n  streamEndRecords: 2\n---\n" + record(3) + "---\n" + record(4) + "---\n");
        HeapLogStore s = HeapLogStore.fromFile(p);
        assertEquals(4, s.size());
        var shape = shape(s);
        var j = EvidenceVerdict.judge(shape, A, "1.0.84");
        assertEquals(EvidenceVerdict.Verdict.UNVERIFIED, j.verdict(), j.reason());
        assertTrue(j.reason().contains("records 3–4 follow a stream end or the start of a file with no stream header"), j.reason());
        assertEquals(List.of(RunSegments.Kind.HEADED, RunSegments.Kind.UNHEADED),
                shape.segments().stream().map(RunSegments.Segment::kind).toList());
        assertEquals("no stream header", shape.segments().get(1).label());
        // the same log with a header after the marker is MATCH
        Path ok = dir.resolve("r3a-ok.yaml");
        Files.writeString(ok, "---\n" + header(A, "run-1") + "---\n" + record(1)
                + "---\neventLogRecord:\n  streamEnd: stopping\n  streamEndRecords: 1\n---\n" + header(A, "run-2") + "---\n" + record(2) + "---\n");
        assertEquals(EvidenceVerdict.Verdict.MATCH, EvidenceVerdict.judge(shape(HeapLogStore.fromFile(ok)), A, null).verdict());
    }

    @Test
    @DisplayName("Review F1, R3b: a rolled day with no header is not verified, and a run of its own")
    void aRolledFileWithNoHeaderIsUnverified() throws IOException {
        Path day1 = dir.resolve("audit.2026-11-02.yaml");
        Path day2 = dir.resolve("audit.2026-11-03.yaml");
        Files.writeString(day1, header(A, "run-1") + record(1000));
        Files.writeString(day2, record(2000) + "---\n" + record(3000));
        try (RolledLogStore set = RolledLogStore.open(List.of(day1, day2), 100)) {
            var shape = shape(set);
            assertEquals(EvidenceVerdict.Verdict.UNVERIFIED, EvidenceVerdict.judge(shape, A, null).verdict());
            assertEquals(2, shape.segments().size(), shape.segments().toString());
            assertEquals(RunSegments.Kind.UNHEADED, shape.segments().get(1).kind());
            assertEquals(2, shape.segments().get(1).records());
        }
    }

    @Test
    @DisplayName("Review F6, R1: K, then an empty host, then K is ONE run")
    void anEmptyHostContinuesTheRun() {
        HeapLogStore s = new HeapLogStore("---\n" + header(A, "run-1") + "---\n" + record(1) + "---\n" + header(A, null)
                + "---\n" + record(2) + "---\n" + header(A, "run-1") + "---\n" + record(3) + "---\n");
        var segments = shape(s).segments();
        assertEquals(1, segments.size(), segments.toString());
        assertEquals(3, segments.get(0).records());
        assertEquals("pricing-1 · run-1 · pricing/quoter#1", segments.get(0).label());
    }

    @Test
    @DisplayName("Review F12: an unreadable header from a new process is never merged into the previous run")
    void anUnreadableHeaderStartsItsOwnSegment() {
        HeapLogStore s = new HeapLogStore("---\n" + header(A, "run-1") + "---\n" + record(1)
                + "---\nauditStreamHeader:\n    schema: 2\n    processor: com.acme.Next\n---\n" + record(2) + "---\n");
        var shape = shape(s);
        assertEquals(List.of(RunSegments.Kind.HEADED, RunSegments.Kind.UNREADABLE),
                shape.segments().stream().map(RunSegments.Segment::kind).toList());
        assertEquals("run identity unreadable", shape.segments().get(1).label());
        assertEquals(EvidenceVerdict.Verdict.UNVERIFIED, EvidenceVerdict.judge(shape, A, null).verdict());
    }

    @Test
    @DisplayName("Review F5: fingerprints from different builders say the digest may differ, not the shape")
    void differentBuildersAreNamedInTheMismatch() {
        var shape = shape(new HeapLogStore("---\n" + header(A, "r", "1.0.80") + "---\n" + record(1) + "---\n"));
        var j = EvidenceVerdict.judge(shape, B, "1.0.84");
        assertEquals(EvidenceVerdict.Verdict.MISMATCH, j.verdict(), "the verdict is kept");
        assertTrue(j.reason().startsWith("the fingerprints differ"), j.reason());
        assertTrue(j.reason().contains("digested by two builder versions (1.0.80 vs 1.0.84), so the difference may be "
                + "in how the shape was digested rather than in the shape"), j.reason());
        var same = EvidenceVerdict.judge(shape, B, "1.0.80");
        assertTrue(same.reason().startsWith("the fingerprints differ"), same.reason());
        assertFalse(same.reason().contains("builder versions"), "same builder: the shape differs");
        assertFalse(EvidenceVerdict.judge(shape, B, null).reason().contains("builder versions"), "graph builder unknown");
    }

    @Test
    @DisplayName("F10: acceptance 5 and 8 through a store — no header is UNVERIFIED; a header-only file pairs by shape")
    void acceptanceFiveAndEightThroughAStore() throws IOException {
        Path old = dir.resolve("old.log");
        Files.writeString(old, Files.readString(Path.of("src/test/resources/sample.yml")));
        var noHeader = shape(HeapLogStore.fromFile(old));
        assertEquals(0, noHeader.headers());
        assertEquals(EvidenceVerdict.Verdict.UNVERIFIED, EvidenceVerdict.judge(noHeader, A, null).verdict());
        assertTrue(noHeader.segments().isEmpty());
        Path idle = dir.resolve("idle.log");
        Files.writeString(idle, header(A, "run-1"));
        var headerOnly = shape(HeapLogStore.fromFile(idle));
        assertEquals(EvidenceVerdict.Verdict.MATCH, EvidenceVerdict.judge(headerOnly, A, null).verdict());
        assertEquals(1, headerOnly.segments().size());
        assertEquals(0, headerOnly.segments().get(0).records());
    }
}
