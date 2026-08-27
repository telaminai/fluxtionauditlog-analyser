package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Will this processor produce an audit log at all?" — answered from the graph, before any log exists.
 *
 * <p>The behaviour under test was measured rather than assumed (2026-08-27): one program, one
 * {@code EventLogNode} calling {@code auditLog.info(…)}, a level set and a sink attached, run twice
 * with a single difference. With {@code addEventAudit()} it wrote two records; without it, an EMPTY
 * FILE. The verdict is flat because the behaviour is.
 */
class AuditReadinessTest {

    /**
     * A graph in the shape the COMPILER actually emits — the type lives inside a jGraph label's
     * {@code class:} line, not as the element's text. Built the real way on purpose: a helper that
     * invents a simpler shape tests a parser nobody ships, which is how the first cut of this test
     * failed while the production code was right.
     */
    private static ProcessorTopology graphOf(String... types) {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            nodes.append("<node id=\"n").append(i).append("\">")
                 .append("<data key=\"vertex_label\"><jGraph:ShapeNode>")
                 .append("<jGraph:label text=\"&lt;&lt;Node&gt;&gt;&#10;id:n").append(i)
                 .append("&#10;class:com.acme.demo.").append(types[i]).append("\"/>")
                 .append("<jGraph:Style properties=\"NODE\"/>")
                 .append("</jGraph:ShapeNode></data></node>");
        }
        return GraphMlParser.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>"
                + "<graph edgedefault=\"directed\">" + nodes + "</graph></graphml>");
    }

    @Test
    void theRealDemoGraphWasBuiltWithAudit() throws Exception {
        // the fixture is emitted by a build that calls addEventAudit() — the positive control, and the
        // reason this check can be trusted against real compiler output rather than a hand-made graph
        ProcessorTopology t = GraphMlParser.parse(
                Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml")));
        AuditReadiness r = AuditReadiness.of(t);

        assertEquals(AuditReadiness.Verdict.ENABLED, r.verdict(), r.message());
        assertTrue(r.isEnabled());
        assertFalse(r.isProblem(), "a working processor must never be flagged");
    }

    /**
     * The real negative the M40.1 review asked for: the same fixture generator with ONE change — addEventAudit()
     * removed — compiled by the real Fluxtion compiler (2026-08-27). 18 nodes, no EventLogManager. Checked in so
     * the check is exercised against compiler output in both directions, not a hand-made graph in one of them.
     */
    @Test
    void theRealCompilerOutputWithoutAddEventAuditIsFlagged() throws Exception {
        ProcessorTopology t = GraphMlParser.parse(
                Files.readString(Path.of("src/test/resources/topology/demo-quote-processor-noaudit.graphml")));
        AuditReadiness r = AuditReadiness.of(t);
        assertEquals(AuditReadiness.Verdict.NOT_ENABLED, r.verdict(), r.message());
        assertTrue(r.isProblem());
        assertEquals(18, t.nodeCount(), "the audited build has 20: the auditor and its control event are the difference");
    }

    @Test
    void aGraphWithoutTheAuditorSaysSoFlatly() {
        AuditReadiness r = AuditReadiness.of(graphOf("PriceListener", "QuotePublisher", "Clock"));

        assertEquals(AuditReadiness.Verdict.NOT_ENABLED, r.verdict());
        assertTrue(r.isProblem());
        assertTrue(r.message().contains("addEventAudit()"), "name the fix: " + r.message());
        assertTrue(r.message().contains("not a sparse one, none"),
                "say it writes NOTHING — a hedge here reads as 'might be fine': " + r.message());
    }

    @Test
    void theAuditorAloneIsEnough() {
        AuditReadiness r = AuditReadiness.of(graphOf("PriceListener", "EventLogManager"));
        assertEquals(AuditReadiness.Verdict.ENABLED, r.verdict());
    }

    @Test
    void theControlEventWithoutItsHandlerIsCalledOutRatherThanIgnored() {
        // an unfamiliar producer might emit the event type without the auditor; better to say the
        // combination is odd than to be confidently wrong about someone else's build
        AuditReadiness r = AuditReadiness.of(graphOf("PriceListener", "EventLogControlEvent"));
        assertEquals(AuditReadiness.Verdict.NOT_ENABLED, r.verdict());
        assertTrue(r.message().contains("unusual"), r.message());
    }

    @Test
    void noGraphIsUNKNOWN_neverGuessedFromTheLog() {
        // declared-never-inferred (D-A2, D-A1a, §E): with no graph there is no evidence, and "probably
        // fine" is the answer that would let the real case through
        assertEquals(AuditReadiness.Verdict.UNKNOWN, AuditReadiness.of(null).verdict());
        assertEquals(AuditReadiness.Verdict.UNKNOWN, AuditReadiness.unknown().verdict());
        assertFalse(AuditReadiness.unknown().isProblem(), "unknown is not a problem to report");
    }

    @Test
    void theEchoCarriesTheVerdictAsData() {
        var echo = AuditReadiness.of(graphOf("PriceListener")).echo();
        assertEquals("not_enabled", echo.get("auditLogging"));
        assertTrue(String.valueOf(echo.get("auditLoggingNote")).contains("addEventAudit()"));
    }

    @Test
    void enabledStillRefusesToPromiseThatEveryNodeLogs() {
        // the adjacent wrong conclusion: audit installed does NOT mean a silent node is a defect
        String m = AuditReadiness.of(graphOf("EventLogManager")).message();
        assertTrue(m.contains("did not log") && m.contains("did not run"),
                "keep the coverage caveat attached to the good news too: " + m);
    }
}
