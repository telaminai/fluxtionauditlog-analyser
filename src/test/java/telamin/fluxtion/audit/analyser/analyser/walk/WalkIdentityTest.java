package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.MappedLogStore;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S1 (spec-spotlight-walks.md §3.5; review R4): what each digest binds, and that unknown is never equal.
 */
class WalkIdentityTest {

    private static String log(int middleValue) {
        String rec = "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - a: { v: %d}\n---\n";
        return "---\n" + rec.formatted(1000, 1) + rec.formatted(2000, middleValue) + rec.formatted(3000, 3);
    }

    @Test
    @DisplayName("W-A16: same count and first/last times, a changed middle record — the fingerprint passes, the digest does not")
    void theRecordDigestCatchesWhatTheFingerprintMisses() {
        HeapLogStore a = new HeapLogStore(log(2)), b = new HeapLogStore(log(9));
        assertEquals(a.size(), b.size());
        assertEquals(a.minLogTime(), b.minLogTime());
        assertEquals(a.maxLogTime(), b.maxLogTime());
        var fa = new LogFingerprint("x.yaml", a.size(), a.minLogTime(), a.maxLogTime());
        var fb = new LogFingerprint("x.yaml", b.size(), b.minLogTime(), b.maxLogTime());
        assertEquals(fa, fb, "control: the coarse fingerprint cannot tell these logs apart");
        assertEquals(WalkIdentity.State.HISTORICAL,
                WalkIdentity.compare(WalkIdentity.recordDigest(a.rawText(1)), WalkIdentity.recordDigest(b.rawText(1))));
        assertEquals(WalkIdentity.State.CURRENT,
                WalkIdentity.compare(WalkIdentity.recordDigest(a.rawText(0)), WalkIdentity.recordDigest(b.rawText(0))),
                "an unchanged record is still current");
    }

    @Test
    @DisplayName("P-3: a record's digest is the same across two opens of one file, heap and mapped")
    void theDigestIsStableAcrossOpensAndStores(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("x.yaml");
        Files.writeString(f, log(2));
        HeapLogStore heap1 = new HeapLogStore(Files.readString(f)), heap2 = new HeapLogStore(Files.readString(f));
        try (MappedLogStore mapped = new MappedLogStore(f)) {
            for (int i = 0; i < heap1.size(); i++) {
                String d = WalkIdentity.recordDigest(heap1.rawText(i));
                assertEquals(d, WalkIdentity.recordDigest(heap2.rawText(i)), "row " + i + ", two heap opens");
                assertEquals(d, WalkIdentity.recordDigest(mapped.rawText(i)), "row " + i + ", heap and mapped");
            }
        }
    }

    @Test
    @DisplayName("the digest is over the exact text: whitespace counts, nothing is trimmed")
    void theDigestIsOverTheExactText() {
        assertNotEquals(WalkIdentity.recordDigest("a: 1"), WalkIdentity.recordDigest("a: 1 "));
        assertEquals("", WalkIdentity.recordDigest(null), "no text, no digest — unknown");
        assertTrue(WalkIdentity.sha256("x").startsWith("sha256:"));
    }

    @Test
    @DisplayName("unknown is never equal: any unknown side is UNRESOLVED")
    void unknownIsNeverEqual() {
        assertEquals(WalkIdentity.State.UNRESOLVED, WalkIdentity.compare("", ""));
        assertEquals(WalkIdentity.State.UNRESOLVED, WalkIdentity.compare(null, "sha256:a"));
        assertEquals(WalkIdentity.State.UNRESOLVED, WalkIdentity.compare("sha256:a", ""));
        assertEquals(WalkIdentity.State.UNRESOLVED, WalkIdentity.compareRuns(List.of(), List.of()));
        assertEquals(WalkIdentity.State.CURRENT, WalkIdentity.compareRuns(List.of("sha256:a"), List.of("sha256:a")));
        assertEquals(WalkIdentity.State.HISTORICAL, WalkIdentity.compareRuns(List.of("sha256:a"), List.of("sha256:b")));
    }

    @Test
    @DisplayName("a run basis with any unknown file is unknown as a whole")
    void aRunBasisWithAnUnknownFileIsUnknown() {
        // the blank case first: it fails as an assertion if the check is missing, where a null would throw
        assertEquals(List.of(), WalkIdentity.runBasis(List.of("sha256:a", "")), "a blank digest is an unknown file");
        assertEquals(List.of(), WalkIdentity.runBasis(Arrays.asList("sha256:a", null)));
        assertEquals(List.of(), WalkIdentity.runBasis(null));
        assertEquals(List.of("sha256:a"), WalkIdentity.runBasis(List.of("sha256:a")));
    }

    @Test
    @DisplayName("a step is never better than its worst target")
    void worse() {
        assertEquals(WalkIdentity.State.UNRESOLVED, WalkIdentity.worse(WalkIdentity.State.CURRENT, WalkIdentity.State.UNRESOLVED));
        assertEquals(WalkIdentity.State.HISTORICAL, WalkIdentity.worse(WalkIdentity.State.HISTORICAL, WalkIdentity.State.CURRENT));
        assertEquals(WalkIdentity.State.CURRENT, WalkIdentity.worse(WalkIdentity.State.CURRENT, WalkIdentity.State.CURRENT));
    }

    @Test
    @DisplayName("review PR57 R1: a Follow append moves the chart run basis — the opening digests alone did not")
    void aFollowAppendMovesTheRunBasis() {
        var before = WalkIdentity.runBasisOf(java.util.List.of("sha256:file"), 25);
        var after = WalkIdentity.runBasisOf(java.util.List.of("sha256:file"), 26);
        assertEquals(WalkIdentity.State.HISTORICAL, WalkIdentity.compareRuns(before, after),
                "the same file, one record longer, is a different run for a chart");
        assertEquals(WalkIdentity.State.CURRENT, WalkIdentity.compareRuns(before, WalkIdentity.runBasisOf(java.util.List.of("sha256:file"), 25)));
        assertEquals(java.util.List.of(), WalkIdentity.runBasisOf(java.util.List.of(), 25), "no digests: the run is unknown");
    }
}
