package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key, acceptance 3: a folder with two graphml builds of different shape — the log pairs with the one
 * whose {@code fluxtion.sourceFingerprint} equals its stream header's. Name matching is only the fallback, used when
 * the log states no fingerprint. Discovery still offers and never selects (M35.4).
 */
class GraphmlDiscoveryFingerprintTest {

    static final String SHAPE = "de849dc6785ee1f13da506afebd8db510ae090af3f6bbcb8ef1d2bdfad4e2c8e";

    @Test
    @DisplayName("Acceptance 3: the build whose shape matches the log is offered first, carrying evidenceSet MATCH")
    void theMatchingShapeRanksFirst(@TempDir Path dir) throws IOException {
        String demo = Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
        assertTrue(demo.contains(SHAPE));
        // the same node names, so name fit cannot tell them apart; and the matching one sorts LAST by path
        Files.writeString(dir.resolve("a-other-build.graphml"), demo.replace(SHAPE, "0000other0shape"));
        Files.writeString(dir.resolve("z-this-build.graphml"), demo);
        Set<String> logged = Set.of("quotePublisher");
        var byShape = GraphmlDiscovery.scan(List.of(dir.toString()), logged, 10, 10, null, SHAPE);
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
    @DisplayName("A graph that states no fingerprint is not verified, and ranks between a match and a mismatch")
    void aGraphWithNoFingerprintIsUnverified(@TempDir Path dir) throws IOException {
        String demo = Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
        Files.writeString(dir.resolve("a-mismatch.graphml"), demo.replace(SHAPE, "0000other0shape"));
        Files.writeString(dir.resolve("b-unstated.graphml"), demo.replace(
                "<data key=\"fluxtion.sourceFingerprint\">" + SHAPE + "</data>", ""));
        Files.writeString(dir.resolve("c-match.graphml"), demo);
        var r = GraphmlDiscovery.scan(List.of(dir.toString()), Set.of("quotePublisher"), 10, 10, null, SHAPE);
        assertEquals(List.of("MATCH", "UNVERIFIED", "MISMATCH"), r.candidates().stream().map(GraphmlDiscovery.Candidate::evidenceSet).toList());
    }
}
