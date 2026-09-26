package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.topology.CoverageService;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;
import telamin.fluxtion.audit.analyser.analyser.topology.Scaffolding;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-MA0c and MA-8's report path — what the log itself shows reaches the report, not only the status bar and
 * {@code context}.
 *
 * <p>An exported report is the surface that LEAVES the session. Before this, a PDF made over an empty file, a
 * damaged one or one with a corrupt document said nothing about it, and a coverage table listed a node the log had
 * set to a quiet level as plainly uncovered. The status bar knew both; the page did not.
 */
class ReportLogFindingsTest {

    private static final String RECORD = "---\n#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: 100\n  event: e\n---\n";
    private static final HeapLogStore STORE = new HeapLogStore(RECORD);

    private static String body(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    private static ReportSpec spec(ReportSpec.SectionSpec... sections) {
        return new ReportSpec("inv-1", "Empty log investigation", "2026-09-26T10:00:00Z", "",
                LogFingerprint.of(STORE.index(), "demo.yaml"), FilterSnapshot.all(), List.of(sections));
    }

    private static byte[] render(ReportSpec spec, List<ReportRenderer.SectionContent> content, ProducerDiagnostics d) {
        var resolution = ReportResolver.resolve(spec, STORE.index(), Map.of(), Set.of(), Set.of(), new FilterState());
        return ReportRenderer.render(spec, resolution, content, "demo.yaml", null, d);
    }

    // ---- D-MA0c: the PDF -------------------------------------------------------------------------

    /** The findings are on the page, damage first — it explains why the file is empty (MA-0.6). */
    @Test
    void theLogsFindingsAreOnThePdf_damageFirst() {
        var d = ProducerDiagnostics.of(new LogIndex(), i -> null, List.of("DAMAGE-MARKER the tail was cut"),
                List.of(), false);
        String pdf = body(render(spec(ReportSpec.SectionSpec.narrative("What we saw.")), List.of(), d));

        assertTrue(pdf.contains("LOG FINDINGS"), "the page states what the file itself shows");
        int damage = pdf.indexOf("DAMAGE-MARKER");
        int empty = pdf.indexOf("No records in this file");
        assertTrue(damage >= 0, "the damage is on the page");
        assertTrue(empty >= 0, "D-MA0c: the empty-log finding is on the page");
        assertTrue(damage < empty, "damage first, as on the status bar");
    }

    /** A clean log adds nothing — the callout is not boilerplate. */
    @Test
    void aCleanLogHasNoFindingsCallout() {
        String pdf = body(render(spec(ReportSpec.SectionSpec.narrative("What we saw.")), List.of(),
                ProducerDiagnostics.clean()));
        assertFalse(pdf.contains("LOG FINDINGS"), "nothing to say, nothing said");
        String none = body(render(spec(ReportSpec.SectionSpec.narrative("What we saw.")), List.of(), null));
        assertFalse(none.contains("LOG FINDINGS"), "and no log at all is not a finding either");
    }

    // ---- MA-8's report path ----------------------------------------------------------------------

    private static final String LEVEL_CHANGE = """
            ---
            eventLogRecord:
              logTime: 1000
              groupingId: null
              event: EventLogControlEvent
              eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=riskMonitor, groupId=null}
              nodeLogs:
            ---
            eventLogRecord:
              logTime: 1001
              groupingId: null
              event: Quote
              nodeLogs:
                - quoteHandler: { seen: true}
            ---
            """;

    private static ReportVerb.CoverageData coverage(String yaml) {
        ProcessorTopology topology;
        try (InputStream in = ReportLogFindingsTest.class.getResourceAsStream("/topology/demo-quote-processor-noaudit.graphml")) {
            topology = GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        var input = new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null);
        return ReportCoverage.forReport(new HeapLogStore(yaml), input, null, false, null);
    }

    /** The row a report prints carries the annotation — and stays uncovered (annotate, never excuse). */
    @Test
    void aReportsCoverageRowCarriesTheLevelChange_andStaysUncovered() {
        var data = coverage(LEVEL_CHANGE);
        var row = data.rows().stream().filter(r -> "riskMonitor".equals(r.get("instanceId"))).findFirst()
                .orElseThrow(() -> new AssertionError("the graph declares riskMonitor"));
        assertEquals("uncovered", row.get("status"), "MA-8.2: the annotation never excuses the node");
        assertTrue(String.valueOf(row.get("levelChange")).contains("WARN"),
                () -> "the row carries the level change the coverage verb returns: " + row);
        assertTrue(data.rows().stream().filter(r -> !"riskMonitor".equals(r.get("instanceId")))
                        .noneMatch(r -> r.containsKey("levelChange")),
                "only the node the control record names is annotated");

        var plain = coverage("---\neventLogRecord:\n  logTime: 1001\n  event: Quote\n  nodeLogs:\n"
                + "    - quoteHandler: { seen: true}\n---\n");
        assertTrue(plain.rows().stream().noneMatch(r -> r.containsKey("levelChange")),
                "no control record, no annotation");
    }

    /** The table's notes say what the annotation is — and those notes are on the page. */
    @Test
    void theLevelChangeIsStatedInTheTablesNotes_onThePage() {
        var data = coverage(LEVEL_CHANGE);
        assertTrue(data.notes().stream().anyMatch(n -> n.startsWith("riskMonitor: ") && n.contains("WARN")),
                () -> "the notes name the node and the level: " + data.notes());

        var section = ReportSpec.SectionSpec.table(Map.of("verb", "coverage"), List.of(), null, null);
        var assembled = ReportVerb.assembleTable(section, STORE, filtered -> data);
        assertTrue(assembled.notes().stream().anyMatch(n -> n.startsWith("riskMonitor: ")),
                () -> "the assembled table keeps them: " + assembled.notes());
        String pdf = body(render(spec(section),
                List.of(new ReportRenderer.SectionContent("Coverage", null, null, assembled.table(), assembled.notes())),
                null));
        assertTrue(pdf.contains("riskMonitor:"), "and they are printed under the table");
    }
}
