package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1 acceptance 1–3 (spec-evidence-integrity.md v3). The graph is the COMMITTED recovery-packet graph,
 * read from the evidence directory itself so this test checks the artefact the client was misled about,
 * not a copy of it. Every log here is CONSTRUCTED and labelled as such in its records: none is a replay of
 * the 2026-09-24 session, whose original logs are not needed for this slice.
 */
class EvidenceIntegrityCoverageTest {

    static final Path PACKET_GRAPH =
            Path.of("docs/handoff/evidence/spring-g14-recovery-2026-09-24/MyProcessor.graphml");

    static ProcessorTopology packetGraph() {
        try {
            return GraphMlParser.parse(Files.readString(PACKET_GRAPH));
        } catch (Exception e) {
            throw new AssertionError("the committed evidence graph must stay where the spec cites it", e);
        }
    }

    /** One record per argument; each argument is the comma-separated node ids that record writes. */
    static String constructedLog(String... idsPerRecord) {
        StringBuilder sb = new StringBuilder();
        long t = 1_000;
        for (String ids : idsPerRecord) {
            sb.append("---\neventLogRecord: \n")
              .append("    eventTime: ").append(t).append('\n')
              .append("    logTime: ").append(t + 1).append('\n')
              .append("    groupingId: null\n")
              .append("    event: PriceUpdate\n")
              .append("    eventToString: CONSTRUCTED for M68.1, not a replay of any session\n")
              .append("    thread: constructed\n")
              .append("    nodeLogs: \n");
            for (String id : ids.split(",")) {
                if (!id.isBlank()) sb.append("        - ").append(id.trim()).append(": { v: 1}\n");
            }
            sb.append("    endTime: ").append(t + 2).append('\n');
            t += 10;
        }
        return sb.append("---\n").toString();
    }

    static CoverageService.Result assess(ProcessorTopology graph, String log) {
        return assess(graph, log, false, null);
    }

    static CoverageService.Result assess(ProcessorTopology graph, String log, boolean filtered, FilterState f) {
        return CoverageService.assess(new HeapLogStore(log), filtered, f,
                new CoverageService.Input(graph, Scaffolding.authoredNodes(graph), null));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> membership(CoverageService.Result r) {
        return (Map<String, Object>) r.echo().get("membership");
    }

    static String everythingSaid(CoverageService.Result r) {
        return (r.echo() + " " + r.scalarLine() + " " + r.notes()).toLowerCase();
    }

    // ---------------------------------------------------------------- acceptance 1

    @Test
    @DisplayName("A1 — the packet graph with its three declared authored nodes reports 3 declared, 3 covered")
    void thePacketGraphGivesThreeAndThree() {
        ProcessorTopology graph = packetGraph();
        assertEquals(18, graph.nodeCount(), "the raw graph is eighteen vertices and stays eighteen");

        CoverageService.Result r = assess(graph, constructedLog("checked,child", "rootNode"));

        assertEquals(3, r.echo().get("declared"), r.echo().toString());
        assertEquals(3, r.echo().get("covered"));
        assertEquals(0, r.echo().get("uncovered"));
        assertEquals(true, r.echo().get("ratioAvailable"));
        assertEquals(1.0, r.echo().get("ratio"));
        assertEquals("declared", r.echo().get("authorshipBasis"));
        assertNull(r.echo().get("warning"), "no out-of-topology warning: " + r.echo().get("warning"));
        assertNull(r.echo().get("loggedButNotInTopology"));
        assertFalse(everythingSaid(r).contains("different build"), "no build-provenance claim anywhere");
        assertTrue(r.ledger().stream().anyMatch(row -> "checked".equals(row.get("instanceId"))
                && "covered".equals(row.get("status"))), "the sink node is a covered ledger row");
    }

    @Test
    @DisplayName("A1 — a declared FRAMEWORK node that writes neither warns nor enters the population")
    void aDeclaredFrameworkNodeThatLogsDoesNotWarn() {
        CoverageService.Result r = assess(packetGraph(), constructedLog("child,serviceRegistry"));

        assertNull(r.echo().get("warning"), "serviceRegistry is declared, so it is not absent: " + r.echo());
        assertEquals(3, r.echo().get("declared"), "the population is unchanged by a framework node logging");
        assertEquals(1, r.echo().get("covered"));
        assertTrue(r.ledger().stream().noneMatch(row -> "serviceRegistry".equals(row.get("instanceId"))),
                "not scored, so not a ledger row");
        @SuppressWarnings("unchecked")
        Map<String, Object> fw = (Map<String, Object>) r.echo().get("frameworkNodesNotScored");
        assertNotNull(fw, "framework exclusions need a destination that is not the denominator");
        assertTrue(((List<?>) fw.get("ids")).contains("serviceRegistry"));
        assertEquals(true, membership(r).get("established"));
        assertEquals(2, membership(r).get("loggedIds"));
        assertEquals(2, membership(r).get("declaredOfLogged"));
    }

    @Test
    @DisplayName("A1 — declared-first classification, and every fallback says it is a fallback")
    void declaredFirstClassification() {
        ProcessorTopology packet = packetGraph();
        GraphVocabulary trusted = packet.vocabulary();
        assertTrue(trusted.trustedForNodeFacts(), "the packet graph is metaVersion 1.0");

        // declared FALSE overrides a framework package prefix — the incident itself
        ProcessorTopology.Node checked = packet.node("checked");
        assertTrue(checked.className().startsWith("com.telamin.fluxtion.runtime."));
        assertTrue(Scaffolding.isScaffolding(checked), "the class-name fallback alone gets this wrong");
        assertEquals(new Scaffolding.Authorship(false, Scaffolding.Basis.DECLARED),
                Scaffolding.classify(checked, trusted));

        // declared TRUE overrides an ordinary class name
        var ordinary = node("helper", "com.acme.demo.Helper", ProcessorTopology.Kind.NODE, "true");
        assertFalse(Scaffolding.isScaffolding(ordinary));
        assertEquals(new Scaffolding.Authorship(true, Scaffolding.Basis.DECLARED),
                Scaffolding.classify(ordinary, trusted));

        // absent key, an invalid value, and an unsupported major: all fall back, all say INFERRED
        var framework = "com.telamin.fluxtion.runtime.output.SinkPublisher";
        assertInferred(true, node("a", framework, ProcessorTopology.Kind.NODE, null), trusted);
        assertInferred(true, node("b", framework, ProcessorTopology.Kind.NODE, "yes"), trusted);
        assertInferred(true, node("c", framework, ProcessorTopology.Kind.NODE, "false"),
                new GraphVocabulary(GraphVocabulary.Mode.PARALLEL, "2.0", Map.of()));
        assertInferred(true, node("d", framework, ProcessorTopology.Kind.NODE, "false"), null);

        // aggregated files keep usable node facts: aggregation merges edges, not nodes
        assertEquals(Scaffolding.Basis.DECLARED, Scaffolding.classify(
                node("e", framework, ProcessorTopology.Kind.NODE, "false"),
                new GraphVocabulary(GraphVocabulary.Mode.AGGREGATED, "1.0", Map.of())).basis());

        // NODE-scoped: an event or exported-service vertex is never read, whatever it carries
        assertInferred(true, node("ClockStrategyEvent", null, ProcessorTopology.Kind.EVENT, "false"), trusted);
        assertInferred(false, node("svc", "com.acme.demo.Svc", ProcessorTopology.Kind.EXPORT_SERVICE, "true"),
                trusted);
    }

    @Test
    @DisplayName("A1 — hiding scaffolding and the authored subgraph keep the declared authored node")
    void visibilityAndSubgraphKeepTheDeclaredNode() {
        ProcessorTopology graph = packetGraph();
        Set<String> hidden = TopologyFocus.visible(graph, false, null);
        assertTrue(hidden.contains("checked"), "hide-scaffolding must not hide a declared authored node");
        assertFalse(hidden.contains("serviceRegistry"));
        assertEquals(18, TopologyFocus.visible(graph, true, null).size());

        ProcessorTopology authored = graph.subgraph(Scaffolding.authoredNodes(graph));
        assertTrue(authored.contains("checked"));
        assertTrue(Scaffolding.authoredNodes(authored).contains("checked"),
                "a subgraph keeps the vocabulary, so re-classifying it stays declared");
        assertEquals("declared", Scaffolding.authorshipBasis(authored));

        // the coverage figures come from the FULL graph and do not depend on what the view hides
        var full = assess(graph, constructedLog("checked,child,rootNode"));
        assertEquals(3, full.echo().get("declared"));
    }

    @Test
    @DisplayName("A1 — audit readiness is judged over the full graph and is not switched off by hiding")
    void auditReadinessOverTheFullGraph() {
        ProcessorTopology graph = packetGraph();
        AuditReadiness full = AuditReadiness.of(graph);
        assertEquals(AuditReadiness.Verdict.ENABLED, full.verdict(), full.message());
        assertEquals(18, full.nodeCount());
    }

    // ---------------------------------------------------------------- acceptance 2

    @Test
    @DisplayName("A2 — no eligible node means NO RATIO, while membership is fully established")
    void zeroPopulationIsNoRatioNotNoMembership() {
        ProcessorTopology onlyFramework = packetGraph().subgraph(Set.of("serviceRegistry"));
        assertTrue(Scaffolding.authoredNodes(onlyFramework).isEmpty());

        CoverageService.Result r = assess(onlyFramework, constructedLog("serviceRegistry"));

        assertEquals(false, r.echo().get("ratioAvailable"));
        assertFalse(r.echo().containsKey("ratio"), "a vacuous 1.0 must not be printed: " + r.echo());
        assertNotNull(r.echo().get("ratioNote"));
        assertTrue(r.scalarLine().contains("ratio none"), r.scalarLine());
        assertEquals(true, membership(r).get("established"), "one of one declared: established");
        assertEquals(1, membership(r).get("declaredOfLogged"));
        assertNull(r.echo().get("warning"));
    }

    @Test
    @DisplayName("A2 — eligible nodes with no node output: the ratio is PRESENT and zero, not absent")
    void noOutputKeepsARatioOfZero() {
        // review R4, the brief's third mutation: "derive no-ratio from no-membership". A log with no node
        // output has no membership evidence, but three nodes are eligible, so 0 of 3 is a real ratio.
        CoverageService.Result r = assess(packetGraph(), constructedLog(""));
        assertEquals(3, r.echo().get("declared"));
        assertEquals(0, r.echo().get("covered"));
        assertEquals(true, r.echo().get("ratioAvailable"), "no membership evidence is not no ratio: " + r.echo());
        assertEquals(0.0, r.echo().get("ratio"));
        assertNull(r.echo().get("ratioNote"));
        assertEquals(false, membership(r).get("established"));
    }

    @Test
    @DisplayName("A2 — a foreign id still warns when the eligible population is empty")
    void aForeignIdWarnsEvenWithNoPopulation() {
        ProcessorTopology onlyFramework = packetGraph().subgraph(Set.of("serviceRegistry"));
        CoverageService.Result r = assess(onlyFramework, constructedLog("serviceRegistry,foreignNode"));

        assertEquals(List.of("foreignNode"), r.echo().get("loggedButNotInTopology"));
        String warning = String.valueOf(r.echo().get("warning"));
        assertTrue(warning.contains("not declared anywhere in the graph"), warning);
        assertFalse(warning.toLowerCase().contains("probably"), "states the fact only: " + warning);
        assertEquals(false, r.echo().get("ratioAvailable"));
    }

    @Test
    @DisplayName("A2 — no node output: no pairing established, and the coverage path says so")
    void noNodeOutputEstablishesNothing() {
        GraphPairing p = GraphPairing.of(GraphPairing.declaredNodeIds(packetGraph()), Set.of());
        assertTrue(p.applies(), "retention may keep the graph");
        assertFalse(p.evidenced(), "…but nothing was compared");
        assertEquals(false, p.facts().get("membershipEstablished"));
        assertTrue(p.note().startsWith("kept, not confirmed"), p.note());
    }

    @Test
    @DisplayName("A2 — the existing foreign-graph negative control still refuses to keep")
    void foreignGraphNegativeControl() {
        GraphPairing p = GraphPairing.of(GraphPairing.declaredNodeIds(packetGraph()),
                Set.of("bidMakerOrder", "askMakerOrder", "positionNode"));
        assertFalse(p.applies());
        assertTrue(p.note().contains("DOES NOT FIT THIS LOG"), p.note());
    }

    // ---------------------------------------------------------------- acceptance 3

    @Test
    @DisplayName("A3 — a foreign id past the 500-record sample: both verdicts true, each at its stated scope")
    void aForeignIdBeyondTheSample() {
        ProcessorTopology graph = packetGraph();
        List<String> records = new ArrayList<>();
        for (int i = 0; i < 500; i++) records.add("child");
        records.add("foreignNode");
        String log = constructedLog(records.toArray(String[]::new));
        HeapLogStore store = new HeapLogStore(log);
        assertEquals(501, store.size());

        // the same sampling the open path performs (MainFrame.pairingAgainst, PAIRING_SAMPLE = 500)
        Set<String> sampledIds = new java.util.LinkedHashSet<>();
        for (int row = 0; row < 500; row++) {
            for (var nodeLog : store.record(row).nodeLogs()) sampledIds.add(nodeLog.instanceId());
        }
        GraphPairing sampled = GraphPairing.of(GraphPairing.declaredNodeIds(graph), sampledIds)
                .withScope(500, store.size());
        assertTrue(sampled.everyObservedIdDeclared(), "true of the sample");
        assertTrue(sampled.sampled());
        assertEquals("first 500 of 501 records", sampled.scope());
        assertTrue(sampled.note().contains("first 500 of 501 records"), sampled.note());
        assertTrue(sampled.reason().contains("judged on the first 500 of 501 records"), sampled.reason());

        CoverageService.Result whole = assess(graph, log);
        assertEquals(List.of("foreignNode"), whole.echo().get("loggedButNotInTopology"),
                "the whole-log comparison finds what the sample could not see");
        assertEquals("whole log", membership(whole).get("scope"));
    }

    @Test
    @DisplayName("A3 — a filtered scope is disclosed on the membership verdict")
    void aFilteredScopeIsDisclosed() {
        FilterState noRows = new FilterState();
        noRows.setTimeRange(Long.MAX_VALUE / 2, null);
        CoverageService.Result r = assess(packetGraph(), constructedLog("child"), true, noRows);
        assertEquals("current filter", membership(r).get("scope"));
        assertEquals(false, membership(r).get("established"), "nothing in scope, so nothing compared");
        assertNotNull(membership(r).get("note"));
    }

    @Test
    @DisplayName("A3 — a retained partial match says so rather than 'fits'")
    void aRetainedPartialMatchIsNotAFit() {
        GraphPairing partial = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "x"));
        assertTrue(partial.applies(), "two of three is kept by the unchanged threshold");
        assertFalse(partial.everyObservedIdDeclared());
        assertTrue(partial.note().startsWith("kept on a partial match"), partial.note());
        assertTrue(partial.reason().contains("1 written id(s) are not in the graph"), partial.reason());
        assertEquals(0.5, GraphPairing.KEEP_ABOVE, "the retention threshold is not moved by this slice");

        assertTrue(GraphPairing.of(Set.of("a", "b"), Set.of("a", "b")).note()
                .startsWith("every node id checked is declared"));
    }

    // ---------------------------------------------------------------- helpers

    private static ProcessorTopology.Node node(String id, String cls, ProcessorTopology.Kind kind, String fw) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (fw != null) facts.put(Scaffolding.FRAMEWORK_FACT, fw);
        return new ProcessorTopology.Node(id, id, cls, kind, facts);
    }

    private static void assertInferred(boolean framework, ProcessorTopology.Node n, GraphVocabulary v) {
        assertEquals(new Scaffolding.Authorship(framework, Scaffolding.Basis.INFERRED), Scaffolding.classify(n, v),
                n.id() + " must fall back to the class-name rule and say so");
    }
}
