package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamHeader;
import telamin.fluxtion.audit.analyser.analyser.session.EvidenceSetState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key: the human surfaces, headless. The status line's words and tooltip are composed from the snapshot's
 * {@code evidenceSet} only (rule 9); the Project panel's rows read only keys {@code context} puts (D-L1). The frame
 * that renders them is a Swing surface: the reviewer's by-hand checklist is in the slice's handoff report.
 */
class EvidenceSetSurfacesTest {

    static StreamHeader header(String fingerprint) {
        return StreamHeader.parse("auditStreamHeader:\n    schema: 1\n    processor: com.acme.X\n    sourceFingerprint: "
                + (fingerprint == null ? "null" : fingerprint) + "\n    toolchainVersion: 1.0.84\n    runtimeVersion: 1.1.4\n"
                + "    startedAt: '2026-11-03T09:15:02Z'\n    host: {}\n", 0);
    }

    @Test
    @DisplayName("The status line states exactly one of three, a mismatch as a warning, nothing without a pair")
    void theStatusLineStatesOneOfThree() {
        var match = EvidenceSetState.decide(true, 1, List.of(header("aaaa")), 1, true, 1, "aaaa");
        var mismatch = EvidenceSetState.decide(true, 1, List.of(header("aaaa")), 1, true, 1, "bbbb");
        var unverified = EvidenceSetState.decide(true, 1, List.of(), 1, true, 1, "aaaa");
        var noGraph = EvidenceSetState.decide(true, 1, List.of(header("aaaa")), 1, false, 0, null);
        assertEquals("  ·  graph shape matches", MainFrame.evidenceSetLine(match));
        assertEquals("  ·  ⚠ graph shape does not match", MainFrame.evidenceSetLine(mismatch));
        assertEquals("  ·  graph not verified", MainFrame.evidenceSetLine(unverified));
        assertEquals("", MainFrame.evidenceSetLine(noGraph));
        assertEquals("", MainFrame.evidenceSetLine(EvidenceSetState.NONE));
        for (var e : List.of(match, mismatch, unverified)) {
            assertFalse(MainFrame.evidenceSetLine(e).contains("same build"), "D4");
            String tip = MainFrame.evidenceTooltip(null, e);
            assertTrue(tip.contains("Graph shape: " + e.line() + " — " + e.reason()), tip);
        }
        assertTrue(MainFrame.evidenceTooltip("a finding", mismatch).contains("a finding"), "the log's findings stay");
        assertNull(MainFrame.evidenceTooltip(null, EvidenceSetState.NONE));
    }

    @Test
    @DisplayName("The Project panel states the graph shape beside the graph, and the runs under the log")
    void theProjectPanelStatesShapeAndRuns() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("path", "/logs/quote.yaml");
        log.put("records", 5);
        log.put("segments", List.of(
                Map.of("label", "pricing-1 · 2c41aa00 · pricing/quoter#1", "firstRecord", 0, "lastRecord", 1, "records", 2),
                Map.of("label", "pricing-1 · 7d02bb00 · pricing/quoter#1", "firstRecord", 2, "lastRecord", 4, "records", 3)));
        ctx.put("log", log);
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("graph", "quote.graphml");
        pair.put("graphSource", "OPENED");
        pair.put("applies", true);
        pair.put("declaredByGraph", 3);
        pair.put("loggedNodes", 3);
        pair.put("evidenceSet", "MISMATCH");
        pair.put("evidenceSetStatus", "graph shape does not match");
        pair.put("evidenceSetNote", "the shapes differ");
        pair.put("logSourceFingerprint", "aaaaaaaaaaaaaaaaaaaa");
        pair.put("graphSourceFingerprint", "bbbbbbbbbbbbbbbbbbbb");
        ctx.put("graphPairing", pair);
        ProjectModel m = ProjectModel.from(ctx);
        var shape = m.section(ProjectModel.GRAPH).rows().stream()
                .filter(r -> r.primary().contains("graph shape")).findFirst().orElseThrow();
        assertEquals("⚠ graph shape does not match", shape.primary());
        assertEquals(ProjectModel.Tone.WARN, shape.tone());
        assertEquals("the shapes differ", shape.secondary());
        assertEquals("log aaaaaaaaaaaa… · graph bbbbbbbbbbbb…", shape.provenance());
        var runs = m.section(ProjectModel.LOG).rows().stream().filter(r -> r.primary().startsWith("Run ")).toList();
        assertEquals(2, runs.size());
        assertEquals("Run pricing-1 · 7d02bb00 · pricing/quoter#1", runs.get(1).primary());
        assertEquals("records 3–5", runs.get(1).secondary());
        // one run is the log: no row restates it
        log.put("segments", List.of(Map.of("label", "x", "firstRecord", 0, "lastRecord", 4, "records", 5)));
        assertTrue(ProjectModel.from(ctx).section(ProjectModel.LOG).rows().stream().noneMatch(r -> r.primary().startsWith("Run ")));
    }
}
