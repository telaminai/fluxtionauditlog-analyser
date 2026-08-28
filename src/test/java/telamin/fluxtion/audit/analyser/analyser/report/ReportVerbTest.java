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
    void aSERIEScallGetsTheSameTreatment_theSeamIsSharedSoTheTestMustBeToo() {
        // the author flagged this gap themselves: SERIES and TABLE both build their call through
        // callMap, so the fix already covers series — but an untested half of a shared seam is one
        // refactor away from being a bug again, and this one cost a live capture to find the first time
        var parsed = parse(List.of(Map.of("kind", "series", "call",
                Map.of("verb", "series", "ref", "book.mid", "from", 1.0E12, "to", 2.0E12))));
        assertEquals(List.of(), parsed.warnings());
        var call = parsed.spec().sections().get(0).call();
        assertEquals("1000000000000", call.get("from"), "epoch millis must not re-issue as 1.0E12");
        assertEquals("2000000000000", call.get("to"));
    }

    @Test
    void nestedCallValuesStayStructuredRatherThanBeingFlattenedToJavaText() {
        Map<String, Object> filter = Map.of("from", 1_000.0, "dimensions", List.of("Quote", "Fill"));
        var parsed = parse(List.of(Map.of("kind", "table", "call",
                Map.of("verb", "aggregate", "groupBy", "dimension", "filter", filter))));

        Object actual = parsed.spec().sections().get(0).call().get("filter");
        assertInstanceOf(Map.class, actual);
        assertEquals(filter, actual);
    }

    @Test
    void aGenuINELYfractionalParameterKeepsItsFraction() {
        // the fix must not turn every number into an integer: a threshold is not an anchor
        var parsed = parse(List.of(Map.of("kind", "table", "call",
                Map.of("verb", "read", "fields", "book.mid", "recordIndex", 0.0, "above", 17.25))));
        assertEquals("17.25", parsed.spec().sections().get(0).call().get("above"));
        assertEquals("0", parsed.spec().sections().get(0).call().get("recordIndex"));
    }

    @Test
    void aNumberBeyondExactDoubleRangeIsLeftAloneRatherThanRoundedSilently() {
        // above ~2^53 a double no longer holds consecutive integers, so converting would invent a value.
        // Leaving it as-is makes the verb refuse it, which is the honest failure. Every numeric parameter
        // the schemas expose — epoch millis (~1.75e12), byteOffset, recordIndex, count — is far below this.
        var parsed = parse(List.of(Map.of("kind", "table", "call",
                Map.of("verb", "read", "fields", "book.mid", "recordIndex", 1.0E16))));
        assertNotEquals("10000000000000000", parsed.spec().sections().get(0).call().get("recordIndex"),
                "a value this size cannot be trusted through a double — do not print it as exact");
    }

    @Test
    void numericCallParametersSurviveAsIntegers_aJsonDoubleAnchorStillAnchors() {
        // JSON numbers arrive as doubles; recordIndex 0.0 re-issued as "0.0" is no anchor at all
        var parsed = parse(List.of(Map.of("kind", "table", "call",
                Map.of("verb", "read", "fields", "book.mid", "recordIndex", 0.0, "after", 3.0))));
        assertEquals(List.of(), parsed.warnings());
        var section = parsed.spec().sections().get(0);
        assertEquals("0", section.call().get("recordIndex"));
        assertEquals("3", section.call().get("after"));
        var a = ReportVerb.assembleTable(section, STORE);
        assertEquals(4, a.table().rows().size());
        assertEquals(List.of(), a.notes());
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
    void aWindowedRuleIsRefusedAndNamed_neverAppliedToAOneSampleWindow() {
        // Every mid here is under 17.25, so a genuine 5-minute mean cannot exceed it. Evaluated one
        // row at a time the window holds a single sample and mean() returns that sample, so records
        // 2 and 3 would highlight under a label still reading "mean(book.mid,"5m") > 17.25" — the
        // report would state a rule the analyser never applied.
        var a = ReportVerb.assembleTable(tableOverMid("mean(book.mid, \"5m\") > 17.25"), STORE);
        assertArrayEquals(new boolean[]{false, false, false, false}, a.table().highlighted(),
                "a rule needing history is refused, not silently collapsed to its bare argument");
        assertTrue(a.notes().stream().anyMatch(n -> n.contains("mean") && n.contains("history")),
                "and the refusal is NAMED on the page: " + a.notes());
    }

    @Test
    void theSameRefusalReachesTheEchoThroughResolution() {
        var r = ReportResolver.resolve(
                new ReportSpec("inv", "t", "", "", null, FilterSnapshot.all(),
                        List.of(tableOverMid("rate(book.mid, \"1s\") > 0"))),
                STORE.index(), Map.of(), java.util.Set.of(), java.util.Set.of(), null);
        assertTrue(r.sections().get(0).resolved(), "acceptance 7: the table still renders");
        assertTrue(r.sections().get(0).warning().contains("rate"),
                "the verb echo names it too: " + r.sections().get(0).warning());
    }

    @Test
    void aPointwiseRuleIsUnaffectedByTheWindowCheck() {
        var r = ReportResolver.resolve(
                new ReportSpec("inv", "t", "", "", null, FilterSnapshot.all(),
                        List.of(tableOverMid("if(book.mid > 17.25, 1, 0)"))),
                STORE.index(), Map.of(), java.util.Set.of(), java.util.Set.of(), null);
        assertNull(r.sections().get(0).warning(), "if/and/or/not are point-wise — nothing to refuse");
    }

    @Test
    void aggregateRowsComeFromTheVerbEchoAndCarryOneScalarLine() {
        var s = ReportSpec.SectionSpec.table(Map.of("verb", "aggregate", "metric", "count",
                "groupBy", "dimension"), List.of(), null, null);
        var a = ReportVerb.assembleTable(s, STORE);
        assertFalse(a.table().rows().isEmpty());
        assertEquals(List.of("key", "count"),
                a.table().columns().stream().map(ColumnSpec::key).toList());
        assertTrue(a.table().scalarLine().contains("metric count · groupBy dimension"));
        assertTrue(a.table().scalarLine().contains("population 4 records (filter: all · scan: index)"));
    }

    @Test
    void anAggregateReissuesItsNestedFilterAndTopLevelLimitAfterReportParsing() {
        var parsed = parse(List.of(Map.of("kind", "table", "call", Map.of(
                "verb", "aggregate", "metric", "count", "groupBy", "dimension", "limit", 1.0,
                "filter", Map.of("from", 1_000.0, "dimensions", List.of("Fill", "Quote"))))));
        var a = ReportVerb.assembleTable(parsed.spec().sections().get(0), STORE);

        assertEquals(1, a.table().rows().size(), "the persisted scalar limit is parsed by the verb");
        assertTrue(a.table().scalarLine().contains("time 1000–…"));
        assertTrue(a.table().scalarLine().contains("dims: Fill, Quote"));
        assertTrue(a.notes().stream().anyMatch(note -> note.contains("truncated")), a.notes().toString());
    }

    /**
     * M40.2 / review N1: the PDF is the surface that LEAVES the session, so a coverage ratio printed
     * there without its exclusions would be the one number a stranger cannot sanity-check.
     *
     * <p>The action echo is intentionally a compact gap list, but the exported report needs the whole
     * ledger: covered and excluded rows are the denominator a reader needs to check the ratio.
     */
    @Test
    void coverageTableCarriesTheWholeLedgerAndExclusions() {
        var s = ReportSpec.SectionSpec.table(Map.of("verb", "coverage"), List.of(), null, null);
        var a = ReportVerb.assembleTable(s, STORE, filtered -> new ReportVerb.CoverageData(
                List.of(
                        Map.of("instanceId", "book", "class", "Book", "status", "covered",
                                "reason", "wrote audit output"),
                        Map.of("instanceId", "event", "class", "Event", "status", "excluded",
                                "reason", "an event class")),
                "declared 1 · covered 1 · uncovered 0 · ratio 1.0 · 4 records · scope: whole log",
                List.of("excluded 1 declared item"), null));

        assertEquals(List.of("covered", "excluded"), a.table().rows().stream().map(r -> r.get(2)).toList());
        assertTrue(a.notes().get(0).contains("excluded"));
        assertTrue(a.table().scalarLine().contains("ratio 1.0"));
    }

    @Test
    void coverageLedgerCapsAndNamesTheOmittedTail() {
        List<Map<String, Object>> ledger = new java.util.ArrayList<>();
        for (int i = 0; i < ReportVerb.COVERAGE_TABLE_CAP + 2; i++) {
            ledger.add(Map.of("instanceId", "node" + i, "status", "covered", "reason", "logged"));
        }
        var s = ReportSpec.SectionSpec.table(Map.of("verb", "coverage"), List.of(), null, null);
        var a = ReportVerb.assembleTable(s, STORE, filtered -> new ReportVerb.CoverageData(
                ledger, "declared 502", List.of(), null));

        assertEquals(ReportVerb.COVERAGE_TABLE_CAP, a.table().rows().size());
        assertTrue(a.notes().stream().anyMatch(note -> note.contains("and 2 more")), a.notes().toString());
    }

    @Test
    void aggregateRowWhenIsRefusedAtResolutionBecauseBucketsHaveNoRecord() {
        var s = ReportSpec.SectionSpec.table(Map.of("verb", "aggregate", "groupBy", "dimension"),
                List.of(), "book.mid > 17", "above cap");
        var r = ReportResolver.resolve(new ReportSpec("inv", "t", "", "", null, FilterSnapshot.all(),
                        List.of(s)), STORE.index(), Map.of(), java.util.Set.of(), java.util.Set.of(), null);

        assertTrue(r.sections().get(0).resolved());
        assertEquals(ReportResolver.rowWhenWithoutRecord("aggregate buckets"), r.sections().get(0).warning());
        var a = ReportVerb.assembleTable(s, STORE);
        assertNull(a.table().rowWhen(), "the table must not print a rule it cannot apply");
        assertFalse(a.table().highlighted().length == 0);
        assertFalse(java.util.stream.IntStream.range(0, a.table().highlighted().length)
                .anyMatch(i -> a.table().highlighted()[i]));
    }

    @Test
    void seriesBucketsAndStatsUseTheirDistinctEchoShapes() {
        var buckets = ReportVerb.assembleTable(ReportSpec.SectionSpec.table(
                Map.of("verb", "series", "expr", "book.mid", "buckets", "minute"),
                List.of(), null, null), STORE);
        assertEquals(List.of("key", "count", "min", "max", "mean"),
                buckets.table().columns().stream().map(ColumnSpec::key).toList());
        assertEquals(1, buckets.table().rows().size());
        assertTrue(buckets.table().scalarLine().contains("expr book.mid · resolve STRICT · 4 points"));

        var stats = ReportVerb.assembleTable(ReportSpec.SectionSpec.table(
                Map.of("verb", "series", "expr", "book.mid"), List.of(), null, null), STORE);
        assertEquals(1, stats.table().rows().size());
        assertEquals(List.of("min", "minAt", "max", "maxAt", "mean", "first", "firstAt", "last", "lastAt"),
                stats.table().columns().stream().map(ColumnSpec::key).toList());
    }

    @Test
    void seriesCrossingsKeepRecordAnchorsAndApplyRowWhen() {
        var section = ReportSpec.SectionSpec.table(Map.of("verb", "series", "expr", "book.mid",
                "crossings", Map.of("above", 17.25)), List.of(), "book.mid > 17.25", "above cap");
        var table = ReportVerb.assembleTable(section, STORE);

        assertEquals(List.of(2), table.rowRecords(), "a crossing row is a navigation surface");
        assertEquals(List.of("above"), table.table().rows().stream().map(row -> row.get(0)).toList());
        assertArrayEquals(new boolean[]{true}, table.table().highlighted());
        assertTrue(table.table().scalarLine().contains("above 1 · below 0"));
    }

    @Test
    void seriesWithBothRowShapesDoesNotResolveToAnArbitraryTable() {
        var section = ReportSpec.SectionSpec.table(Map.of("verb", "series", "expr", "book.mid",
                "buckets", "minute", "crossings", Map.of("above", 17.25)), List.of(), null, null);
        var resolved = ReportResolver.resolve(new ReportSpec("inv", "t", "", "", null, FilterSnapshot.all(),
                        List.of(section)), STORE.index(), Map.of(), java.util.Set.of(), java.util.Set.of(), null);

        assertFalse(resolved.sections().get(0).resolved());
        assertEquals("a series table needs one row shape — buckets or crossings, not both",
                resolved.sections().get(0).reason());
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

    // ---- the PDF markers table (M32.7) ------------------------------------------------------------

    @Test
    void markersTableCarriesLabelGlyphTimePayloadRecord() {
        var ms = new telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries("fills", "triangleUp",
                List.of(new telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries.MarkerPoint(
                        2000L, 17.2, "ORD-1", 1)), null);
        var t = ReportVerb.markersTable(List.of(ms));
        assertTrue(t.notes().isEmpty());
        assertEquals(List.of("fills", "triangleUp", "2000", "ORD-1", "1"), t.table().rows().get(0));
        assertEquals("time", t.table().columns().get(2).format(),
                "raw millis in the data; the DECLARED format renders it — one value, one rule");
    }

    @Test
    void markersTableCapsAndNamesTheCap() {   // D-M3 in table form
        List<telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries.MarkerPoint> pts =
                new java.util.ArrayList<>();
        for (int i = 0; i < ReportVerb.MARKER_TABLE_CAP + 50; i++) {
            pts.add(new telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries.MarkerPoint(
                    1000L + i, 1.0, null, i));
        }
        var t = ReportVerb.markersTable(List.of(
                new telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries("dense", "x", pts, null)));
        assertEquals(ReportVerb.MARKER_TABLE_CAP, t.table().rows().size());
        assertTrue(t.notes().get(0).contains("first " + ReportVerb.MARKER_TABLE_CAP + " of "
                + (ReportVerb.MARKER_TABLE_CAP + 50)), t.notes().toString());
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
