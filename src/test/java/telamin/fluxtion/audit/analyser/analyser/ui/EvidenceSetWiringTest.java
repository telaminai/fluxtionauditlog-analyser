package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review F4 (rule 8): executable witnesses for the evidence-set WIRING, so removing any piece of it fails a named test.
 * Headless: the coverage verb through a real {@link ActionExecutor} over a real session; the frame's three pieces as
 * the pure helpers they call, plus a source witness that the frame still calls each (the frame itself needs a display).
 *
 * <ul>
 *   <li>{@link #coverageCarriesTheEvidenceSet} — the {@code coverage} reply's evidenceSet put ({@code ActionExecutor}).</li>
 *   <li>{@link #contextPutsTheEvidenceSetKeys} and {@link #theFrameCallsEachPiece} — {@code context.graphPairing}.</li>
 *   <li>{@link #theScanReportsTheHeadersFirst} and {@link #theFrameCallsEachPiece} — the StreamHeadersObserved post.</li>
 *   <li>{@link #theGraphFactCarriesTheGraphmlsFingerprint} and {@link #theFrameCallsEachPiece} — the fingerprint on
 *       GraphOpened.</li>
 * </ul>
 */
class EvidenceSetWiringTest {

    static final Path GRAPHML = Path.of("src/test/resources/topology/demo-quote-processor.graphml");
    static final String SHAPE = "de849dc6785ee1f13da506afebd8db510ae090af3f6bbcb8ef1d2bdfad4e2c8e";

    static String log() {
        String header = new com.telamin.fluxtion.runtime.audit.AuditStreamHeader("com.acme.QuoteProcessor", SHAPE, "1.0.84",
                "1.1.4", "2026-11-03T09:15:02Z", Map.of()).toString();
        return "---\n" + header + "\n---\neventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - orderTracker: {v: 1}\n---\n";
    }

    @Test
    @DisplayName("The evidence scan reports the log's headers (with its breaks) FIRST, then the findings and time order")
    void theScanReportsTheHeadersFirst() {
        HeapLogStore s = new HeapLogStore(log());
        List<Object> facts = MainFrame.evidenceScanFacts(7, s, null, null);
        assertEquals(3, facts.size());
        var headers = assertInstanceOf(SessionEvents.StreamHeadersObserved.class, facts.get(0), "headers first");
        assertEquals(7, headers.generation());
        assertEquals(1, headers.headers().size());
        assertEquals(SHAPE, headers.headers().get(0).sourceFingerprint());
        assertEquals(1, headers.records());
        assertEquals(s.streamBreaks(), headers.breaks());
        assertInstanceOf(SessionEvents.ProducerFindingsObserved.class, facts.get(1));
        assertInstanceOf(SessionEvents.TimeOrderObserved.class, facts.get(2));
    }

    @Test
    @DisplayName("GraphOpened carries the graphml's own fingerprint and the builder that digested it")
    void theGraphFactCarriesTheGraphmlsFingerprint() {
        var topology = GraphMlParser.parse(GRAPHML);
        var fact = MainFrame.graphOpenedFact(GRAPHML, "OPENED", topology, List.of(), null);
        assertEquals(SHAPE, fact.sourceFingerprint());
        assertEquals(topology.vocabulary().graphFacts().get("fluxtion.toolchainVersion"), fact.toolchainVersion());
        assertNotNull(fact.toolchainVersion());
    }

    @Test
    @DisplayName("context.graphPairing gets the verdict and both fingerprints from the snapshot, only with a graph shown")
    void contextPutsTheEvidenceSetKeys() {
        SessionDriver d = new SessionDriver(e -> e instanceof SessionEffects.OpenLogEffect
                ? new SessionEvents.Pending(e.opId(), "openLog") : new SessionEvents.StatusShown(e.opId(), "shown"));
        open(d);
        HeapLogStore s = new HeapLogStore(log());
        for (Object f : MainFrame.evidenceScanFacts(d.snapshot().logGeneration(), s, null, null)) d.post(f);
        d.post(MainFrame.graphOpenedFact(GRAPHML, "OPENED", GraphMlParser.parse(GRAPHML), List.of(), null));
        Map<String, Object> pair = new LinkedHashMap<>();
        MainFrame.putEvidenceSet(pair, d.snapshot(), true);
        assertEquals("MATCH", pair.get("evidenceSet"));
        assertEquals("graph shape matches", pair.get("evidenceSetStatus"));
        assertEquals(SHAPE, pair.get("logSourceFingerprint"));
        assertEquals(SHAPE, pair.get("graphSourceFingerprint"));
        assertNotNull(pair.get("evidenceSetNote"));
        assertEquals("full", pair.get("coverageClaim"), "the session's coverage claim beside the verdict (O3)");
        Map<String, Object> none = new LinkedHashMap<>();
        MainFrame.putEvidenceSet(none, d.snapshot(), false);
        assertTrue(none.isEmpty(), "no graph shown: nothing is said about a pair");
    }

    @Test
    @DisplayName("The frame calls each piece: scan facts, the graph fact, the context keys (source witness — the frame needs a display)")
    void theFrameCallsEachPiece() throws Exception {
        String src = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        String scan = body(src, "private void scanLogEvidence(long generation) {");
        assertTrue(scan.contains("for (Object fact : evidenceScanFacts(generation, s, findings, order)) session.post(fact);"), scan);
        String graphFact = body(src, "private Object graphFact() {");
        assertTrue(graphFact.contains("graphOpenedFact(graphFile, topologyPanel.graphSource().name(), full, types,"), graphFact);
        String context = body(src, "ActionResult context(\n");
        assertTrue(context.contains("putEvidenceSet(pair, snap, gf);"), "context.graphPairing's evidence-set keys");
        int put = context.indexOf("putEvidenceSet(pair, snap, gf);");
        int early = context.indexOf("if (filter == null)");
        assertTrue(early < 0 || put < early, "ABOVE the fresh-start return (ONBOARDING gotcha)");
    }

    @Test
    @DisplayName("coverage over a pair carries evidenceSet with both fingerprints (ActionExecutor, socket level)")
    void coverageCarriesTheEvidenceSet() throws Exception {
        ActionResult reply = coverage(null);
        assertTrue(reply.ok(), String.valueOf(reply.toMap()));
        assertEquals("MATCH", reply.payload().get("evidenceSet"), String.valueOf(reply.payload()));
        assertEquals(SHAPE, reply.payload().get("logSourceFingerprint"));
        assertEquals(SHAPE, reply.payload().get("graphSourceFingerprint"));
        assertNull(reply.payload().get("claim"), "a match leaves a FULL claim alone");
    }

    @Test
    @DisplayName("O3: coverage over a graph of another shape is QUALIFIED, and says why in the evidence set's words")
    void coverageOverAMismatchIsQualified() throws Exception {
        ActionResult reply = coverage("0000other0shape");
        assertTrue(reply.ok(), "qualified, never refused (D2): " + reply.toMap());
        assertEquals("MISMATCH", reply.payload().get("evidenceSet"));
        assertEquals("qualified", reply.payload().get("claim"));
        assertTrue(String.valueOf(reply.payload().get("claimNote")).startsWith(
                "the graph shape does not match the log: the fingerprints differ"), String.valueOf(reply.payload()));
    }

    /** Coverage through a real executor over a real session; {@code otherShape} replaces the graph's fingerprint. */
    private static ActionResult coverage(String otherShape) throws Exception {
        var holder = new java.util.concurrent.atomic.AtomicReference<ActionResult>();
        var store = new HeapLogStore(log());
        SwingUtilities.invokeAndWait(() -> {
            SessionDriver d = new SessionDriver(e -> e instanceof SessionEffects.OpenLogEffect
                    ? new SessionEvents.Pending(e.opId(), "openLog") : new SessionEvents.StatusShown(e.opId(), "shown"));
            open(d);
            for (Object f : MainFrame.evidenceScanFacts(d.snapshot().logGeneration(), store, null, null)) d.post(f);
            TopologyPanel panel = new TopologyPanel();
            panel.load(GRAPHML);
            var fact = MainFrame.graphOpenedFact(GRAPHML, "OPENED", GraphMlParser.parse(GRAPHML), List.of("EventLogManager"), null);
            d.post(otherShape == null ? fact : new SessionEvents.GraphOpened(fact.graphPath(), fact.source(),
                    fact.declaredNodeIds(), fact.nodeTypes(), null, otherShape, fact.toolchainVersion()));
            var filter = new FilterState();
            var tabs = new GraphTabs();
            tabs.bind(store, filter);
            ActionExecutor executor = new ActionExecutor(() -> (LogStore) store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
            AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                    new Class<?>[]{AppControl.class}, (o, m, a) -> m.isDefault() ? InvocationHandler.invokeDefault(o, m, a) : null);
            executor.bind(panel, app);
            executor.bindSessionSnapshot(d::snapshot);
            holder.set(executor.render("coverage", Map.of()));
        });
        return holder.get();
    }

    private static void open(SessionDriver d) {
        long id = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(id, "/logs/quote.yaml", null, "DECLARED", false));
        d.submit(new SessionEvents.LogOpened(id, "/logs/quote.yaml", "DECLARED", Set.of("orderTracker"), 1, 1, "TRACE"));
    }

    /** From the line that opens a method to the next method's javadoc or signature at the same indent. */
    private static String body(String src, String opener) {
        int at = src.indexOf(opener);
        assertTrue(at > 0, "not found: " + opener);
        int end = src.indexOf("\n    }\n", at);
        int alt = src.indexOf("\n        }\n\n        @Override", at);
        if (opener.startsWith("ActionResult context(")) end = alt > 0 ? alt : end;
        return src.substring(at, end);
    }
}
