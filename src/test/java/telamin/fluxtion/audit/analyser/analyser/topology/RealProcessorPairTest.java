package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology.Execution.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The one end-to-end check against artefacts that are <b>genuinely emitted</b>, not written by hand:
 * {@code demo-quote-processor.graphml} came out of the Fluxtion compiler, and
 * {@code demo-quote-audit.yaml} came out of running that generated processor.
 *
 * <p>It exists because the interesting case cannot be faked convincingly. The demo graph is
 * {@code MarketDataEvent → priceListener → spreadCalculator → quotePublisher}, and
 * <b>spreadCalculator writes no audit output</b> while the two nodes either side of it do. So a real
 * cycle contains a node that certainly executed and certainly did not log — which is exactly the
 * situation the UI must not render as "did not run".
 */
class RealProcessorPairTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = RealProcessorPairTest.class.getResourceAsStream("/topology/" + name)) {
            assertNotNull(in, name + " missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ProcessorTopology topology() throws IOException {
        return GraphMlParser.parse(resource("demo-quote-processor.graphml"));
    }

    private static List<LogRecord> tracedRecords() throws IOException {
        return recordsOf("demo-quote-audit-traced.yaml");
    }

    private static List<LogRecord> records() throws IOException {
        return recordsOf("demo-quote-audit.yaml");
    }

    private static List<LogRecord> recordsOf(String fixture) throws IOException {
        HeapLogStore store = new HeapLogStore(resource(fixture));
        List<LogRecord> out = new ArrayList<>();
        for (int row = 0; row < store.size(); row++) out.add(store.record(row));
        return out;
    }

    private static List<String> loggedIn(LogRecord record) {
        List<String> ids = new ArrayList<>();
        for (NodeLog node : record.nodeLogs()) ids.add(node.instanceId());
        return ids;
    }

    @Test
    void theCompilerEmittedGraphParses() throws IOException {
        ProcessorTopology t = topology();
        assertFalse(t.isEmpty());
        assertTrue(t.contains("priceListener"));
        assertTrue(t.contains("spreadCalculator"));
        assertTrue(t.contains("quotePublisher"));
        assertEquals(ProcessorTopology.Kind.EVENT, t.node("MarketDataEvent").kind());
        assertEquals(ProcessorTopology.Kind.EVENT_HANDLER, t.node("priceListener").kind());
    }

    @Test
    void exportedServicesAreEntryPointsInARealGraph() throws IOException {
        // measured, not assumed: this is why the docs call them entry points rather than outputs
        ProcessorTopology t = topology();
        for (ProcessorTopology.Node node : t.nodes()) {
            if (node.kind() == ProcessorTopology.Kind.EXPORT_SERVICE
                || node.kind() == ProcessorTopology.Kind.EVENT) {
                assertTrue(t.parentsOf(node.id()).isEmpty(),
                        node.id() + " (" + node.kind() + ") should have nothing feeding it");
            }
        }
    }

    @Test
    void theTopologyMatchesTheLogItWasGeneratedWith() throws IOException {
        ProcessorTopology t = topology();
        for (LogRecord record : records()) {
            assertTrue(t.match(loggedIn(record)).complete(),
                    "same build, so every logged node must be in the graph: " + record.event());
        }
    }

    @Test
    void aNodeThatRanWithoutLoggingIsNotReportedAsAbsent() throws IOException {
        ProcessorTopology t = topology();
        LogRecord marketData = records().stream()
                .filter(r -> "MarketDataEvent".equals(r.event())).findFirst().orElseThrow();

        List<String> logged = loggedIn(marketData);
        assertTrue(logged.contains("priceListener"));
        assertTrue(logged.contains("quotePublisher"));
        assertFalse(logged.contains("spreadCalculator"),
                "the fixture depends on this node being silent");

        List<String> entries = List.copyOf(EntryPointResolver.resolve(
                t, marketData.event(), marketData.eventToString()));
        assertEquals(List.of("MarketDataEvent"), entries);

        Map<String, ProcessorTopology.Execution> state = t.classifyCycle(logged, entries);
        assertNotEquals(OFF_PATH, state.get("spreadCalculator"),
                "it demonstrably ran — the value it computes reached quotePublisher's log line");
        assertEquals(MAY_HAVE_RUN, state.get("spreadCalculator"));
    }

    // ---- the two audit regimes, both real -------------------------------------------------------

    @Test
    void theSparseLogDoesNotTraceInvocations() throws IOException {
        for (LogRecord record : records()) {
            assertFalse(AuditTrace.tracesEveryInvocation(record.nodeLogs()),
                    "built with addEventAudit() and no level — no auditInvocation calls are generated");
        }
    }

    @Test
    void theTracedLogRecordsEveryNodeThatRan() throws IOException {
        List<LogRecord> traced = tracedRecords();
        LogRecord marketData = traced.stream()
                .filter(r -> "MarketDataEvent".equals(r.event())).findFirst().orElseThrow();

        assertTrue(AuditTrace.tracesEveryInvocation(marketData.nodeLogs()));
        assertTrue(loggedIn(marketData).contains("spreadCalculator"),
                "tracing records the silent node too — this is the same graph, a different audit level");
    }

    @Test
    void withTracingAbsenceBecomesProof() throws IOException {
        ProcessorTopology t = topology();
        LogRecord marketData = tracedRecords().stream()
                .filter(r -> "MarketDataEvent".equals(r.event())).findFirst().orElseThrow();
        List<String> logged = loggedIn(marketData);

        Map<String, ProcessorTopology.Execution> state = t.classifyCycle(
                logged,
                List.copyOf(EntryPointResolver.resolve(t, marketData.event(), marketData.eventToString())),
                AuditTrace.tracesEveryInvocation(marketData.nodeLogs()));

        assertEquals(LOGGED, state.get("spreadCalculator"), "it ran, and tracing recorded it");
        assertEquals(DID_NOT_RUN, state.get("orderTracker"),
                "a complete record turns silence into evidence — no hedging needed");
        assertFalse(state.containsValue(MAY_HAVE_RUN), "nothing is unknown when every invocation is traced");
        assertFalse(state.containsValue(RAN_SILENTLY));
    }

    @Test
    void anEventOnlyLightsItsOwnBranch() throws IOException {
        ProcessorTopology t = topology();
        LogRecord marketData = records().stream()
                .filter(r -> "MarketDataEvent".equals(r.event())).findFirst().orElseThrow();
        Map<String, ProcessorTopology.Execution> state = t.classifyCycle(
                loggedIn(marketData),
                List.copyOf(EntryPointResolver.resolve(t, marketData.event(), marketData.eventToString())));
        assertEquals(OFF_PATH, state.get("orderTracker"),
                "an order-update handler is not reachable from a market-data event");
    }
}
