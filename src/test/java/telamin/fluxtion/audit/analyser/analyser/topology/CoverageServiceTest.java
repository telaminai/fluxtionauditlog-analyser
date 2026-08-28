package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** M33.7: the report ledger is the action's own denominator and must include every status. */
class CoverageServiceTest {

    private static String resource(String path) {
        try (InputStream in = CoverageServiceTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "not in the test runtime: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void noAuditFixtureProducesAGraphOrderedCompleteLedger() {
        ProcessorTopology topology = GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        HeapLogStore store = new HeapLogStore(resource("/topology/demo-quote-audit.yaml"));
        String nodes = resource("/demo/com/acme/demo/node/Nodes.java");
        CoverageService.Result result = CoverageService.assess(store, false, null,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology),
                        ignored -> Optional.of(nodes)));

        int declared = ((Number) result.echo().get("declared")).intValue();
        @SuppressWarnings("unchecked")
        Map<String, String> excluded = (Map<String, String>) result.echo().get("excludedFromDenominator");
        assertEquals(declared + excluded.size(), result.ledger().size(),
                "the report ledger carries every scored node and every excluded node");
        assertTrue(result.ledger().stream().anyMatch(row -> "covered".equals(row.get("status"))));
        Map<String, Object> silent = result.ledger().stream()
                .filter(row -> "spreadCalculator".equals(row.get("instanceId"))).findFirst().orElseThrow();
        assertEquals("excluded", silent.get("status"));
        assertTrue(silent.get("reason").toString().contains("cannot reach an audit logger"));
        assertTrue(result.scalarLine().contains("declared " + declared));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("excluded")));
    }

    @Test
    void aFilteredOutPopulationLeavesEveryScoredNodeExplicitlyUncovered() {
        ProcessorTopology topology = GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        HeapLogStore store = new HeapLogStore(resource("/topology/demo-quote-audit.yaml"));
        FilterState noRows = new FilterState();
        noRows.setTimeRange(Long.MAX_VALUE / 2, null);
        CoverageService.Result result = CoverageService.assess(store, true, noRows,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));

        assertEquals("current filter", result.echo().get("scope"));
        assertTrue(result.ledger().stream().anyMatch(row -> "uncovered".equals(row.get("status"))));
        assertTrue(result.scalarLine().contains("0 records · scope: current filter"));
    }
}
