package telamin.fluxtion.audit.analyser.analyser.topology;

import com.telamin.fluxtion.runtime.audit.AuditStreamHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.parse.EvidenceVerdict;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key, acceptance 3: a folder with two graphml builds of different shape — the log pairs with the one
 * whose {@code fluxtion.sourceFingerprint} equals its stream headers'. Name matching is only the fallback, used when the
 * log states no fingerprint. Discovery judges with the ONE shared judgement over the log's whole shape (review F2), and
 * still offers and never selects (M35.4).
 */
class GraphmlDiscoveryFingerprintTest {

    static final String SHAPE = "de849dc6785ee1f13da506afebd8db510ae090af3f6bbcb8ef1d2bdfad4e2c8e";

    static String header(String fingerprint) {
        return new AuditStreamHeader("com.acme.QuoteProcessor", fingerprint, "1.0.84", "1.1.4",
                "2026-11-03T09:15:02Z", Map.of()) + "\n";
    }

    static String record(long t) {
        return "eventLogRecord:\n    logTime: " + t + "\n    event: Tick\n    nodeLogs:\n    - book: { mid: 1.5}\n";
    }

    /** The shape a real store reads from a log, as the session publishes it. */
    static EvidenceVerdict.LogShape shapeOf(String log) {
        HeapLogStore s = new HeapLogStore(log);
        return EvidenceVerdict.shape(s.streamHeaders(), s.size(), s.streamBreaks());
    }

    @Test
    @DisplayName("Acceptance 3: the build whose shape matches the log is offered first, carrying evidenceSet MATCH")
    void theMatchingShapeRanksFirst(@TempDir Path dir) throws IOException {
        String demo = Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
        assertTrue(demo.contains(SHAPE));
        // the same node names, so name fit cannot tell them apart; and the matching one sorts LAST by path
        Files.writeString(dir.resolve("a-other-build.graphml"), demo.replace(SHAPE, "0000other0shape"));
        Files.writeString(dir.resolve("z-this-build.graphml"), demo);
        Set<String> logged = Set.of("quotePublisher");
        var shape = shapeOf("---\n" + header(SHAPE) + "---\n" + record(1) + "---\n");
        var byShape = GraphmlDiscovery.scan(List.of(dir.toString()), logged, 10, 10, null, shape);
        assertEquals(2, byShape.candidates().size());
        var first = byShape.candidates().get(0);
        assertEquals("z-this-build.graphml", first.file().getFileName().toString());
        assertEquals("MATCH", first.evidenceSet());
        assertEquals("MATCH", first.toMap().get("evidenceSet"));
        assertEquals(SHAPE, first.toMap().get("logSourceFingerprint"));
        assertEquals("MISMATCH", byShape.candidates().get(1).evidenceSet());
        assertTrue(first.describe().contains("graph shape matches the log"), first.describe());
        // no fingerprint from the log: name fit alone ranks, and no candidate claims a shape verdict
        var byName = GraphmlDiscovery.scan(List.of(dir.toString()), logged, 10, 10, null, null);
        assertEquals("a-other-build.graphml", byName.candidates().get(0).file().getFileName().toString());
        assertNull(byName.candidates().get(0).evidenceSet());
        assertFalse(byName.candidates().get(0).toMap().containsKey("evidenceSet"));
    }

    @Test
    @DisplayName("Review F2 (R2): headers A then B — no build is MATCH; the session's judgement and discovery's agree")
    void aLogOfTwoShapesMatchesNeitherBuild(@TempDir Path dir) throws IOException {
        String demo = Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
        Files.writeString(dir.resolve("a.graphml"), demo);                           // shape A
        Files.writeString(dir.resolve("b.graphml"), demo.replace(SHAPE, "bbbb"));   // shape B, the LATEST header's
        var shape = shapeOf("---\n" + header(SHAPE) + "---\n" + record(1) + "---\n" + header("bbbb") + "---\n" + record(2) + "---\n");
        var r = GraphmlDiscovery.scan(List.of(dir.toString()), Set.of(), -1, -1, null, shape);
        for (var c : r.candidates()) {
            assertEquals("MISMATCH", c.evidenceSet(), c.file() + ": a log written by two shapes matches neither");
            assertEquals(EvidenceVerdict.judge(shape, c.fingerprint(), null).verdict().name(), c.evidenceSet(),
                    "one judgement, whoever asks");
            assertFalse(c.describe().contains("graph shape matches the log"), c.describe());
        }
        assertEquals(java.util.List.of(SHAPE, "bbbb"), r.candidates().get(0).toMap().get("logSourceFingerprints"));
    }

    @Test
    @DisplayName("A graph that states no fingerprint is not verified, and ranks between a match and a mismatch")
    void aGraphWithNoFingerprintIsUnverified(@TempDir Path dir) throws IOException {
        String demo = Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
        Files.writeString(dir.resolve("a-mismatch.graphml"), demo.replace(SHAPE, "0000other0shape"));
        Files.writeString(dir.resolve("b-unstated.graphml"), demo.replace(
                "<data key=\"fluxtion.sourceFingerprint\">" + SHAPE + "</data>", ""));
        Files.writeString(dir.resolve("c-match.graphml"), demo);
        var shape = shapeOf("---\n" + header(SHAPE) + "---\n" + record(1) + "---\n");
        var r = GraphmlDiscovery.scan(List.of(dir.toString()), Set.of("quotePublisher"), 10, 10, null, shape);
        assertEquals(List.of("MATCH", "UNVERIFIED", "MISMATCH"), r.candidates().stream().map(GraphmlDiscovery.Candidate::evidenceSet).toList());
    }
}
