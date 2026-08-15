package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pair-check (M21.1, spec-graph-replay §2). A topology built from a different build than the log is
 * the failure mode worth engineering against: it renders happily and misleads silently, so the mismatch
 * has to be detectable and sayable.
 */
class TopologyMatchTest {

    private static ProcessorTopology topology() throws IOException {
        try (InputStream in = TopologyMatchTest.class.getResourceAsStream("/topology/sample-processor.graphml")) {
            assertNotNull(in);
            return GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void aLogFromTheSameBuildMatchesCompletely() throws IOException {
        ProcessorTopology.Match m = topology().match(
                List.of("priceListener_2", "midPriceCalculator_1", "positionCalculator_0"));
        assertTrue(m.complete());
        assertEquals(1.0, m.coverage());
        assertTrue(m.unknownToTopology().isEmpty());
        assertTrue(m.describe().contains("3 of 7"), m.describe());
    }

    @Test
    void anInstanceIdMissingFromTheGraphSignalsAVersionMismatch() throws IOException {
        ProcessorTopology.Match m = topology().match(
                List.of("priceListener_2", "hedgeMonitor_7", "riskGate_8"));
        assertFalse(m.complete());
        assertEquals(Set.of("hedgeMonitor_7", "riskGate_8"), m.unknownToTopology());
        assertEquals(1.0 / 3, m.coverage(), 1e-9);

        String said = m.describe();
        assertTrue(said.contains("different build"), said);
        assertTrue(said.contains("hedgeMonitor_7"), "names the offending node: " + said);
    }

    @Test
    void nodesAbsentFromTheLogAreReportedSeparatelyFromMismatches() throws IOException {
        // a node that simply never fired in this window is NOT evidence of a wrong topology
        ProcessorTopology.Match m = topology().match(List.of("priceListener_2"));
        assertTrue(m.complete(), "not firing is not a mismatch");
        assertTrue(m.notInLog().contains("quotePublisher_3"));
        assertFalse(m.notInLog().contains("priceListener_2"));
    }

    @Test
    void manyUnknownsPreviewOnlyAFew() throws IOException {
        ProcessorTopology.Match m = topology().match(List.of("a_1", "b_2", "c_3", "d_4", "e_5"));
        assertEquals(0.0, m.coverage());
        assertTrue(m.describe().contains("…"), "the message stays readable: " + m.describe());
    }

    @Test
    void anEmptyLogIsVacuouslyConsistent() throws IOException {
        ProcessorTopology.Match m = topology().match(List.of());
        assertTrue(m.complete());
        assertEquals(1.0, m.coverage(), "no evidence of a mismatch is not evidence of one");
    }

    @Test
    void nullsAreToleratedRatherThanThrowing() throws IOException {
        assertTrue(topology().match(null).complete());
        ProcessorTopology.Match m = topology().match(java.util.Arrays.asList("priceListener_2", null));
        assertEquals(Set.of("priceListener_2"), m.matched());
    }

    @Test
    void resultsAreSortedSoTheUiAndTestsReadTheSame() throws IOException {
        ProcessorTopology.Match m = topology().match(List.of("z_9", "a_1", "m_5"));
        assertEquals(List.of("a_1", "m_5", "z_9"), List.copyOf(m.unknownToTopology()));
    }

    @Test
    void anEmptyTopologyMatchesNothingAndSaysSo() {
        ProcessorTopology.Match m = ProcessorTopology.empty().match(List.of("priceListener_2"));
        assertFalse(m.complete());
        assertEquals(0.0, m.coverage());
    }
}
