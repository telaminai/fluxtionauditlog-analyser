package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.export.RecordExporter;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.ColumnSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.Kind;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M33.3 — the verb's headless half: parameter parsing under the M26.4 echo contract (invalid
 * sections skipped AND named), the D-I1 call-out, and table assembly with the STRICT row rule.
 */
class ReportVerbTest {

    /** Four records; book.mid rises through 17.25 at t=3000; fills carry an order id. */
    private static final HeapLogStore STORE = new HeapLogStore("""
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 1000
              event: Quote
              nodeLogs:
                - book: { mid: 17.1}
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 2000
              event: Fill
              nodeLogs:
                - book: { mid: 17.2}
                - fills: { fillPrice: 17.2, clOrdId: ORD-1}
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 3000
              event: Quote
              nodeLogs:
                - book: { mid: 17.3}
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 4000
              event: Fill
              nodeLogs:
                - book: { mid: 17.4}
                - fills: { fillPrice: 17.4, clOrdId: ORD-2}
            ---
            """);

    private static ReportVerb.Parsed parse(Object sections) {
        return ReportVerb.parse(Map.of("name", "inv", "sections", sections),
                LogFingerprint.of(STORE.index(), "t.yaml"), FilterSnapshot.all());
    }

    // ---- parsing: skip and NAME, never silent ----------------------------------------------------

    @Test
    void textOnAFindingSectionIsNamedInWarnings_dI1SaidWhereTheCallerHears() {
        var p = parse(List.of(Map.of("kind", "finding", "recordIndex", 1,
                "text", "my own diagnosis")));
        assertEquals(1, p.spec().sections().size(), "the section itself survives — only the text drops");
        assertNull(p.spec().sections().get(0).text());
        assertTrue(p.warnings().get(0).contains("never authors them"), p.warnings().toString());
        assertTrue(p.warnings().get(0).contains("'flag' is the one write site"), p.warnings().toString());
    }

    @Test
    void anUnknownKindIsSkippedAndNamed_itsSiblingsSurvive() {
        var p = parse(List.of(
                Map.of("kind", "essay", "text", "..."),
                Map.of("kind", "narrative", "text", "kept")));
        assertEquals(1, p.spec().sections().size());
        assertEquals(Kind.NARRATIVE, p.spec().sections().get(0).kind());
        assertTrue(p.warnings().get(0).contains("unknown kind 'essay'"), p.warnings().toString());
    }

    @Test
    void missingAnchorsAreSkippedAndNamed() {
        var p = parse(List.of(
                Map.of("kind", "finding"),                       // no recordIndex
                Map.of("kind", "chart"),                         // no graph
                Map.of("kind", "narrative")));                   // no text
        assertTrue(p.spec().sections().isEmpty());
        assertEquals(3, p.warnings().size());
        assertTrue(p.warnings().get(0).contains("recordIndex"));
        assertTrue(p.warnings().get(1).contains("'graph'"));
        assertTrue(p.warnings().get(2).contains("'text'"));
    }

    @Test
    void aFullSectionListParsesInOrder() {
        var p = parse(List.of(
                Map.of("kind", "finding", "recordIndex", 1),
                Map.of("kind", "chart", "graph", "spread"),
                Map.of("kind", "table", "call", Map.of("verb", "read", "fields", "book.mid"),
                        "rowWhen", "book.mid > 17.25", "rowWhenLabel", "above cap"),
                Map.of("kind", "narrative", "text", "the account")));
        assertTrue(p.warnings().isEmpty());
        assertEquals(List.of(Kind.FINDING, Kind.CHART, Kind.TABLE, Kind.NARRATIVE),
                p.spec().sections().stream().map(ReportSpec.SectionSpec::kind).toList());
        assertEquals("book.mid > 17.25", p.spec().sections().get(2).rowWhen());
    }

    // ---- table assembly: derived rows, STRICT row rule --------------------------------------------

    private static ReportSpec.SectionSpec tableOverMid(String rowWhen) {
        return ReportSpec.SectionSpec.table(
                Map.of("verb", "read", "fields", "book.mid, fills.clOrdId",
                        "recordIndex", "0", "after", "3"),
                List.of(), rowWhen, rowWhen == null ? null : "above cap");
    }

    @Test
    void tableRowsAreDerivedFromTheCall_defaultColumnsCoverTheFields() {
        var a = ReportVerb.assembleTable(tableOverMid(null), STORE);
        assertEquals(4, a.table().rows().size());
        List<String> keys = a.table().columns().stream().map(ColumnSpec::key).toList();
        assertEquals(List.of("recordIndex", "logTime", "event", "book.mid", "fills.clOrdId"), keys);
        assertEquals("17.1", a.table().rows().get(0).get(3));
        assertEquals("ORD-2", a.table().rows().get(3).get(4), "text payloads ride as display data");
    }

    @Test
    void rowWhenEvaluatesStrictlyAgainstEachRowsOwnRecord() {
        var a = ReportVerb.assembleTable(tableOverMid("book.mid > 17.25"), STORE);
        assertArrayEquals(new boolean[]{false, false, true, true}, a.table().highlighted(),
                "17.3 and 17.4 breach; no carry, no history — each row answers for itself (D-I8)");
    }

    @Test
    void aRefTheRowDidNotLogMeansTheRuleCannotFireOnIt() {
        // fills.fillPrice appears only on records 1 and 3 — records 0 and 2 cannot satisfy the rule,
        // even though a LOCF carry would have held 17.2 through record 2
        var a = ReportVerb.assembleTable(tableOverMid("fills.fillPrice > 17.0"), STORE);
        assertArrayEquals(new boolean[]{false, true, false, true}, a.table().highlighted(),
                "a rule that cannot be checked against its own row does not fire on it");
    }

    @Test
    void aNonReadSourceStatesTheGapInsteadOfAnEmptyAnswer() {
        var s = ReportSpec.SectionSpec.table(Map.of("verb", "aggregate"), List.of(), null, null);
        var a = ReportVerb.assembleTable(s, STORE);
        assertTrue(a.table().rows().isEmpty());
        assertTrue(a.notes().get(0).contains("not assembled yet"), a.notes().toString());
    }

    // ---- CSV: one writer, raw values ---------------------------------------------------------------

    @Test
    void tableCsvCarriesDeclaredHeadingsOverRawValues() {
        var a = ReportVerb.assembleTable(tableOverMid(null), STORE);
        String csv = RecordExporter.tableToCsv(a.table().columns(), a.table().rows());
        String[] lines = csv.split("\n");
        assertEquals("record,time (UTC),event,book.mid,fills.clOrdId", lines[0]);
        assertTrue(lines[1].startsWith("0,1000,Quote,17.1"),
                "raw values, not the page's formatting — CSV is data leaving for a spreadsheet");
        assertEquals(5, lines.length);
    }

    @Test
    void csvQuotesCommasAndQuotes() {
        String csv = RecordExporter.tableToCsv(
                List.of(new ColumnSpec("a", "a", "", "", "")),
                List.of(List.of("x,y"), List.of("say \"hi\"")));
        assertTrue(csv.contains("\"x,y\""));
        assertTrue(csv.contains("\"say \"\"hi\"\"\""));
    }
}
