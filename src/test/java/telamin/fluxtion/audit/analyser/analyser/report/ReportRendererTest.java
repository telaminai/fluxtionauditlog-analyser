package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.ColumnSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M33.2 — the section-list renderer: D-I2's narrative treatment (visibly narrative, standing label),
 * D-I3/D-I3a rendered loudly and FIRST, D-I7/D-I8's table (declared presentation, printed rule,
 * header repeated across page breaks). Layout is asserted through the PDF byte stream, the same way
 * FindingReportTest does.
 */
class ReportRendererTest {

    private static String records(long... logTimes) {
        StringBuilder sb = new StringBuilder("---\n");
        for (long t : logTimes) {
            sb.append("#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: ").append(t)
                    .append("\n  event: e\n---\n");
        }
        return sb.toString();
    }

    private static final HeapLogStore STORE = new HeapLogStore(records(100, 200, 300));

    private static String body(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    private static ReportSpec spec(SectionSpec... sections) {
        return new ReportSpec("inv-1", "Oversell investigation", "2026-08-20T10:00:00Z", "",
                LogFingerprint.of(STORE.index(), "demo.yaml"), FilterSnapshot.all(),
                List.of(sections));
    }

    private static ReportResolver.Resolution resolve(ReportSpec spec, Map<Integer, Finding> findings) {
        return ReportResolver.resolve(spec, STORE.index(), findings, Set.of("spread"), Set.of(),
                new FilterState());
    }

    // ---- D-I2: narrative is visibly narrative ---------------------------------------------------

    @Test
    void narrativeCarriesItsStandingLabel() {   // acceptance 1, the PDF half
        ReportSpec spec = spec(
                SectionSpec.narrative("The venue paused quoting and our spread widened in response."));
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()), List.of(), "demo.yaml", null));
        assertTrue(pdf.contains("NOT LOG EVIDENCE"),
                "the standing label rides every narrative block, not a byline at the top");
        assertTrue(pdf.contains("our spread widened"));
    }

    @Test
    void aFindingRendersWhatFlagWrote_byteIdentical() {   // acceptance 2, the render half
        ReportSpec spec = spec(SectionSpec.finding(1));
        Finding written = new Finding(1, "liveOrders exceeded the limit at record 1", "raise the cap review");
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of(1, written)),
                List.of(), "demo.yaml", null));
        assertTrue(pdf.contains("liveOrders exceeded the limit at record 1"),
                "the note passes through untouched — flag is the one write site");
        assertTrue(pdf.contains("raise the cap review"));
    }

    // ---- D-I3/D-I3a: loud, and first --------------------------------------------------------------

    @Test
    void anUnresolvedSectionRendersItsReason_andTheRestStillRenders() {   // acceptance 3
        ReportSpec spec = spec(
                SectionSpec.chart("gone graph"),
                SectionSpec.narrative("the account survives a missing chart"));
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()), List.of(), "demo.yaml", null));
        assertTrue(pdf.contains("DID NOT RESOLVE"));
        assertTrue(pdf.contains("is not defined"));
        assertTrue(pdf.contains("survives a missing chart"), "later sections render regardless");
    }

    @Test
    void aFingerprintMismatchRendersBeforeAnySection() {   // acceptance 9
        ReportSpec spec = new ReportSpec("inv-1", "t", "", "",
                new LogFingerprint("other.yaml", 999, 5L, 6L), FilterSnapshot.all(),
                List.of(SectionSpec.narrative("SECTION-PROSE-MARKER")));
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()),
                List.of(), "demo.yaml", null));
        int banner = pdf.indexOf("NOT THE LOG THE REPORT WAS WRITTEN AGAINST");
        int section = pdf.indexOf("SECTION-PROSE-MARKER");
        assertTrue(banner >= 0, "the mismatch is announced");
        assertTrue(section >= 0, "announce, never forbid: the section still renders");
        assertTrue(banner < section, "the announcement comes first");
    }

    // ---- D-I7/D-I8: the table ----------------------------------------------------------------------

    private static ReportRenderer.TableData table(int rows, String rowWhen, String label) {
        List<List<String>> data = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            data.add(List.of("2026-01-01T09:00", String.valueOf(i), String.valueOf(i * 0.5)));
        }
        boolean[] hot = new boolean[rows];
        if (rows > 1) hot[1] = true;
        return new ReportRenderer.TableData(
                List.of(new ColumnSpec("time", "TIME-HEADING-MARK", "", "left", ""),
                        new ColumnSpec("count", "count", "0", "", ""),
                        new ColumnSpec("price", "price", "0.00", "", "bold")),
                data, hot, rowWhen, label);
    }

    @Test
    void aTablePrintsItsHighlightRule() {   // acceptance 7, the render half
        ReportSpec spec = spec(SectionSpec.table(Map.of("verb", "read"), List.of(),
                "book.mid > 17", "in breach"));
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()),
                List.of(new ReportRenderer.SectionContent("Breaches", null, null,
                        table(3, "book.mid > 17", "in breach"))), "demo.yaml", null));
        assertTrue(pdf.contains("in breach"), "the label says what the colour MEANS");
        assertTrue(pdf.contains("rows where book.mid > 17"), "and the rule that selected them is printed");
    }

    @Test
    void aTablePrintsTheSameProvidedScalarLineAndItsEmptyReason() {
        String scalar = "SCALAR-LINE-MARKER";
        ReportSpec spec = spec(SectionSpec.table(Map.of("verb", "aggregate"), List.of(), null, null));
        ReportRenderer.TableData empty = new ReportRenderer.TableData(
                List.of(new ColumnSpec("key", "key", "", "", "")), List.of(), new boolean[0],
                null, null, scalar, "empty result");
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()),
                List.of(new ReportRenderer.SectionContent("Aggregate", null, null, empty)), "demo.yaml", null));

        assertTrue(pdf.contains(scalar), "the renderer receives the scalar string assembled for the panel");
        assertTrue(pdf.contains("empty result"), "the PDF names why the table has no rows");
    }

    @Test
    void declaredFormatsAreApplied() {
        assertEquals("1.50", ReportRenderer.formatCell("1.5", "0.00"));
        assertEquals("1,250", ReportRenderer.formatCell("1250", "0"));
        assertEquals("12.5%", ReportRenderer.formatCell("0.125", "percent"));
        assertEquals("1.5s", ReportRenderer.formatCell("1500", "duration"));
        assertEquals("00:00:00.100", ReportRenderer.formatCell("100", "time"));
        assertEquals("not a number", ReportRenderer.formatCell("not a number", "0.00"),
                "a non-numeric cell passes through rather than throwing");
    }

    @Test
    void aLongTableRepeatsItsHeaderOnEveryPage() {
        ReportSpec spec = spec(SectionSpec.table(Map.of("verb", "read"), List.of(), null, null));
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()),
                List.of(new ReportRenderer.SectionContent("Big", null, null,
                        table(120, null, null))), "demo.yaml", null));
        int first = pdf.indexOf("TIME-HEADING-MARK");
        int second = pdf.indexOf("TIME-HEADING-MARK", first + 1);
        assertTrue(first >= 0 && second > first,
                "120 rows cannot fit one page; the header must repeat where the table resumes");
    }

    @Test
    void theMetaStripNamesTheAuthoringContext() {   // D-I3a on the page even when nothing differs
        ReportSpec spec = spec(SectionSpec.narrative("x"));
        String pdf = body(ReportRenderer.render(spec, resolve(spec, Map.of()), List.of(), "demo.yaml", null));
        assertTrue(pdf.contains("WRITTEN AGAINST"));
        assertTrue(pdf.contains("AUTHORED VIEW"));
        assertTrue(pdf.contains("all event types"));
    }
}
