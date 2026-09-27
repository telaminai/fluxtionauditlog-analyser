package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S0 (review R5): the topology panel's graph digest belongs to the graph now shown. It used to survive
 * {@code clearGraph} and a source-supplied graph, so graph A's digest could describe graph B. Session recovery reads
 * it (guarded by a path check that happens to fail for a source graph); M69's structural walk targets read it without
 * that guard.
 */
class TopologyGraphDigestTest {

    private static final Path GRAPH = Path.of("src/test/resources/topology/sample-processor.graphml");

    private static AuditLogReader.SourceGraph sourceGraph() {
        return new AuditLogReader.SourceGraph(
                List.of(new ProcessorTopology.Node("a", "a", "com.acme.A", ProcessorTopology.Kind.NODE, java.util.Map.of()),
                        new ProcessorTopology.Node("b", "b", "com.acme.B", ProcessorTopology.Kind.NODE, java.util.Map.of())),
                List.of(new ProcessorTopology.Edge("e", "a", "b")), AuditLogReader.Provenance.DECLARED);
    }

    @Test
    @DisplayName("a file load sets the digest; clearing the graph drops it")
    void clearDropsTheDigest() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        assertNotNull(panel.loadedGraphSha256(), "control: a stable file load has a digest");
        panel.clearGraph();
        assertNull(panel.loadedGraphSha256(), "no graph is shown, so no digest may describe one");
    }

    @Test
    @DisplayName("file graph A, then clear, then source graph B: A's digest cannot qualify B")
    void aSourceGraphHasNoDigest() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        panel.clearGraph();
        assertTrue(panel.loadFromSource(sourceGraph()), "control: the source graph took the empty slot");
        assertNull(panel.loadedGraphSha256(),
                "a source-supplied graph has no established content identity; A's digest must not stand for it");
    }

    @Test
    @DisplayName("a source graph refused because a file graph is open keeps that file's digest")
    void aRefusedSourceGraphKeepsTheOpenedGraphsDigest() {
        var panel = new TopologyPanel();
        panel.load(GRAPH);
        String a = panel.loadedGraphSha256();
        assertFalse(panel.loadFromSource(sourceGraph()), "control: an opened graph wins over a supplied one");
        assertEquals(a, panel.loadedGraphSha256(), "the opened graph is still the one shown");
    }
}
