package telamin.fluxtion.audit.analyser.analyser.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan;
import telamin.fluxtion.audit.analyser.analyser.model.EventKind;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordFramer;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphSource;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M34.3 — the conformance suite for the audit record format (spec-source-adapters D-A6). The fixtures
 * in {@code src/test/resources/conformance/} are the published set; this class is what "passes them"
 * means. Each fixture pins a SEMANTIC (review F3: a spec that lists fields licenses emitters that are
 * conformant and meaningless) and every fixture is run TWICE:
 *
 * <ul>
 *   <li>through the built-in text path ({@link HeapLogStore}) — the reference implementation, which
 *       is what D-A6 says must pass; and</li>
 *   <li>through the SPI path ({@link SpiLogStore} over a reader that does nothing but hand the same
 *       records over) — which is what any adapter does, so the two must agree record for record.</li>
 * </ul>
 *
 * The specification page these fixtures accompany is {@code docs/site/format-spec.md}.
 */
class FormatConformanceTest {

    @TempDir
    Path dir;

    // ---- the two paths every fixture takes ----------------------------------------------------------

    /** The adapter that adds nothing: frames the file and hands each record's text over unchanged. */
    private static final class PassThroughReader implements AuditLogReader {
        private final Ordering ordering;

        PassThroughReader(Ordering ordering) {
            this.ordering = ordering;
        }

        @Override public String formatId() { return "conformance-passthrough"; }
        @Override public String displayName() { return "conformance pass-through"; }
        @Override public boolean canOpen(Path source) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, true, ordering); }
        @Override public void read(Path source, Consumer<String> out) throws IOException {
            RecordFramer.frame(Files.readString(source), raw -> out.accept(raw.text()));
        }
    }

    private static String fixture(String name) {
        try (InputStream in = FormatConformanceTest.class.getResourceAsStream("/conformance/" + name)) {
            if (in == null) throw new IllegalStateException("fixture missing: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private LogStore builtIn(String name) {
        return new HeapLogStore(fixture(name));
    }

    private LogStore viaSpi(String name) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, fixture(name));
        return SpiLogStore.open(new PassThroughReader(AuditLogReader.Ordering.TOTAL), f);
    }

    /** The two paths agree on everything the record model exposes. Returns the built-in store. */
    private LogStore bothPathsAgree(String name) throws IOException {
        LogStore a = builtIn(name);
        LogStore b = viaSpi(name);
        assertEquals(a.size(), b.size(), name + ": record count");
        for (int i = 0; i < a.size(); i++) {
            LogRecord ra = a.record(i), rb = b.record(i);
            assertEquals(ra.kind(), rb.kind(), name + "[" + i + "] kind");
            assertEquals(ra.logTime(), rb.logTime(), name + "[" + i + "] logTime");
            assertEquals(ra.eventTime(), rb.eventTime(), name + "[" + i + "] eventTime");
            assertEquals(ra.event(), rb.event(), name + "[" + i + "] event");
            assertEquals(ra.thread(), rb.thread(), name + "[" + i + "] thread");
            assertEquals(ra.eventDimension(), rb.eventDimension(), name + "[" + i + "] dimension");
            assertEquals(ra.nodeLogsCount(), rb.nodeLogsCount(), name + "[" + i + "] nodeLogs count");
            assertEquals(a.rawText(i).strip(), b.rawText(i).strip(), name + "[" + i + "] D-P2 canonical text");
        }
        assertEquals(a.index().minLogTime(), b.index().minLogTime(), name + ": timeline start");
        assertEquals(a.index().maxLogTime(), b.index().maxLogTime(), name + ": timeline end");
        return a;
    }

    private static long points(LogStore store, String expr) {
        return ((Number) SeriesScan.scan(store, Map.of("expr", expr)).get("points")).longValue();
    }

    // ---- C01–C13 --------------------------------------------------------------------------------------

    @Test
    void c01_theMinimalRecordIsLogTimePlusOneNodeLog() throws IOException {
        LogStore s = bothPathsAgree("c01-minimal.yaml");
        assertEquals(1, s.size());
        LogRecord r = s.record(0);
        assertEquals(EventKind.OK, r.kind());
        assertEquals(1000L, r.logTime());
        assertNull(r.event(), "event is optional");
        assertNull(r.thread(), "so is thread, with no header to supply it");
        assertEquals("book", r.nodeLogs().get(0).instanceId());
        assertEquals(1L, points(s, "book.mid"), "and it is already graphable");
    }

    @Test
    void c02_unknownFieldsAreIgnoredNeverRejected() throws IOException {
        LogStore s = bothPathsAgree("c02-unknown-fields.yaml");
        LogRecord r = s.record(0);
        assertEquals(EventKind.OK, r.kind(), "forward tolerance: a newer producer must not break an older analyser");
        assertEquals(1000L, r.logTime());
        assertEquals("Tick", r.event());
        assertEquals(1, r.nodeLogsCount(), "the unknown mapping after nodeLogs did not leak into it");
        assertTrue(s.rawText(0).contains("futureField: 42"), "ignored for meaning, kept in the text");
    }

    @Test
    void c03_theHeaderLineIsOptional_andTheScalarThreadWins() throws IOException {
        LogStore s = bothPathsAgree("c03-header.yaml");
        LogRecord first = s.record(0), second = s.record(1);
        assertEquals("worker-1", first.thread(), "no thread scalar: the header supplies it");
        assertEquals("DEMO_LOGGER", first.logger());
        assertEquals("INFO", first.level());
        assertEquals("scalar-wins", second.thread(), "a thread scalar beats the header");
        assertEquals("WARN", second.level());
    }

    @Test
    void c04_logTimeIsTheTimeline_eventTimeMinusOneMeansNotEventDriven() throws IOException {
        LogStore s = bothPathsAgree("c04-times.yaml");
        LogRecord timer = s.record(0), tick = s.record(1);
        assertNull(timer.eventTime(), "-1 is a sentinel, not a time");
        assertEquals(1000L, timer.logTime());
        assertEquals(1003L, timer.endTime());
        assertEquals(999L, tick.eventTime());
        assertNull(tick.endTime(), "endTime is optional");
        assertEquals(1000L, s.index().minLogTime());
        assertEquals(1005L, s.index().maxLogTime());
        assertTrue(TimeOrderValidator.validate(s.index()).isClean(), "eventTime is never consulted for order");
    }

    @Test
    void c05_anUntimedRecordIsKept_isOffTheTimeline_andOrdersNothing() throws IOException {
        LogStore s = bothPathsAgree("c05-untimed.yaml");
        assertEquals(3, s.size(), "kept");
        assertEquals(EventKind.OK, s.record(1).kind(), "a record, not an error");
        assertNull(s.index().logTime(1));
        assertEquals("LifecycleEvent", s.record(1).event());
        assertEquals(1000L, s.index().minLogTime(), "off the timeline");
        assertEquals(3000L, s.index().maxLogTime());
        assertTrue(TimeOrderValidator.validate(s.index()).isClean(), "it orders nothing, so it violates nothing");
    }

    @Test
    void c06_backwardsLogTimeIsReported_neverRepaired() throws IOException {
        LogStore s = bothPathsAgree("c06-out-of-order.yaml");
        TimeOrderReport report = TimeOrderValidator.validate(s.index());
        assertFalse(report.isClean(), "an emitter that goes backwards is TOLD");
        TimeOrderReport.Violation v = report.violations().get(0);
        assertEquals(TimeOrderReport.Kind.OUT_OF_ORDER, v.kind());
        assertEquals(2, v.recordIndex(), "named with its first offending record");
        assertEquals(300L, s.index().logTime(1), "and the records stay where they were written");
        assertEquals(200L, s.index().logTime(2));
    }

    @Test
    void c07_aDuplicateInstanceIdKeepsEveryOccurrence_lastWinsForOneValue() throws IOException {
        LogStore s = bothPathsAgree("c07-duplicate-instance.yaml");
        LogRecord r = s.record(0);
        assertEquals(3, r.nodeLogsCount(), "every occurrence is a separate entry, in order");
        assertEquals("book", r.nodeLogs().get(0).instanceId());
        assertEquals("book", r.nodeLogs().get(2).instanceId());
        assertEquals("1", r.nodeLogs().get(0).last("mid").rawValue());
        assertEquals("2", r.nodeLogs().get(2).last("mid").rawValue(), "the last occurrence is the record's value");
        assertEquals(1L, points(s, "book.mid"), "one record, one point — not two");
    }

    @Test
    void c08_valuesAreNotYaml_onlyTopLevelSeparatorsSplit() throws IOException {
        LogStore s = bothPathsAgree("c08-lenient-values.yaml");
        LogRecord r = s.record(0);
        assertEquals(EventKind.OK, r.kind(), "nothing here fails the record");
        var entries = r.nodeLogs().get(0).entries();
        assertEquals(4, entries.size(), "inner commas, brackets and = runs did not split: " + entries);
        assertEquals("MutableOrder(clOrdId=1, venue=null)", r.nodeLogs().get(0).last("upd").rawValue());
        assertEquals("[a, b]", r.nodeLogs().get(0).last("venues").rawValue());
        assertEquals("connected=true required=[x]", r.nodeLogs().get(0).last("status").rawValue());
        assertTrue(r.hasNaN(), "NaN is not a YAML float and is detected, not choked on");
    }

    @Test
    void c09_aGarbageSliceIsKeptAsAParseErrorWithItsText_neighboursUnaffected() throws IOException {
        LogStore s = bothPathsAgree("c09-garbage.yaml");
        assertEquals(3, s.size(), "nothing silently dropped");
        assertEquals(EventKind.OK, s.record(0).kind());
        assertEquals(EventKind.PARSE_ERROR, s.record(1).kind());
        assertTrue(s.rawText(1).contains("not a record at all"), "the evidence is retained verbatim");
        assertEquals(EventKind.OK, s.record(2).kind());
        assertEquals(2000L, s.record(2).logTime());
    }

    @Test
    void c10_theOrderingClaimIsTheReaders_andReachesTheIndex() throws IOException {
        // D-A1a: position in nodeLogs is dispatch order ONLY when the source says so. The built-in
        // text container is totally ordered by construction; a foreign reader declares.
        Path f = dir.resolve("c01-minimal.yaml");
        Files.writeString(f, fixture("c01-minimal.yaml"));
        assertTrue(builtIn("c01-minimal.yaml").index().totalOrder(), "the reference implementation is TOTAL");
        assertTrue(SpiLogStore.open(new PassThroughReader(AuditLogReader.Ordering.TOTAL), f).index().totalOrder());
        assertFalse(SpiLogStore.open(new PassThroughReader(AuditLogReader.Ordering.PARTIAL), f).index().totalOrder(),
                "a PARTIAL claim reaches the index, where every order-consuming feature reads it");
        assertTrue(new AuditLogReader.Capabilities(false, false, false).ordering() == AuditLogReader.Ordering.TOTAL,
                "the pre-M34 constructor defaults to TOTAL — true of every container that predates the claim");
    }

    @Test
    void c11_theCoreAttributesByPosition_soBroadcastingSharedStateDuplicatesSeries() throws IOException {
        // D-A3: a value appears under a component only if THAT component produced or changed it. The
        // core cannot enforce this — it has no idea what "produced" means in a foreign engine — so it
        // pins the CONSEQUENCE: two entries carrying the same key are two series, never merged.
        LogStore s = bothPathsAgree("c11-attribution.yaml");
        assertEquals(1L, points(s, "producer.price"));
        assertEquals(1L, points(s, "consumer.price"), "the duplicate is a second series, not a de-duplicated one");
        var ex = assertThrows(IllegalArgumentException.class, () -> points(s, "price"),
                "there is no component-less 'price' to fall back on — the grammar has no such thing");
        assertTrue(ex.getMessage().contains("instanceId.key"), ex.getMessage());
    }

    @Test
    void c12_absenceMeansDidNotRunOnlyUnderTracing_otherwiseItMeansNothing() throws IOException {
        LogStore s = bothPathsAgree("c12-traced-regime.yaml");
        LogRecord traced = s.record(0), untraced = s.record(1);
        assertTrue(AuditTrace.tracesEveryInvocation(traced.nodeLogs()), "every entry carries method: traced");
        assertFalse(AuditTrace.tracesEveryInvocation(untraced.nodeLogs()),
                "one business key called 'method' must not make a sparse record look complete");

        ProcessorTopology t = GraphMlParser.parse(graphml(List.of("a", "b", "c"), List.of("a>b", "b>c")));
        assertEquals(3, t.nodeCount());
        var whenTraced = t.classifyCycle(List.of("a", "b"), List.of("a"), true);
        var whenNot = t.classifyCycle(List.of("a", "b"), List.of("a"), false);
        assertEquals(ProcessorTopology.Execution.DID_NOT_RUN, whenTraced.get("c"),
                "under tracing the record is complete, so absence is proof");
        assertNotEquals(ProcessorTopology.Execution.DID_NOT_RUN, whenNot.get("c"),
                "untraced, the analyser must never say 'did not run' — it cannot know: " + whenNot.get("c"));
        assertNotEquals(ProcessorTopology.Execution.LOGGED, whenNot.get("c"));
    }

    @Test
    void c13_anExportedCallIsDimensionedByItsCallback() throws IOException {
        LogStore s = bothPathsAgree("c13-exported-call.yaml");
        LogRecord r = s.record(0);
        assertNull(r.eventTime(), "no event drove it");
        assertEquals("orderVenueConnected", r.callback());
        assertEquals("com.acme.demo.VenueHedgeMonitor", r.declaringType());
        assertEquals("orderVenueConnected", r.eventDimension(), "the filter/group key is the callback, not the event class");
        assertNull(r.groupingId(), "the literal null is null");
        assertTrue(r.hasNaN());
    }

    /**
     * C15 — §7's own clauses, which could not be asserted until M34.1 merged (review F1: a normative
     * clause nobody can write a test for has nothing to conform to yet). No fixture: the subject is
     * the graph a READER hands over, not a record.
     */
    @Test
    void c15_aSourceGraphMustDeclareProvenance_andProvenanceDecidesWhatCoverageMayClaim() {
        // "It MUST say whether the graph is DECLARED or INFERRED. A graph without a provenance cannot be constructed."
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new AuditLogReader.SourceGraph(List.of(), List.of(), null));
        assertTrue(ex.getMessage().contains("DECLARED or INFERRED"), ex.getMessage());
        // "coverage is declared minus observed" — meaningful against DECLARED, a tautology against INFERRED
        assertTrue(GraphSource.of(AuditLogReader.Provenance.DECLARED).supportsCoverage());
        assertFalse(GraphSource.of(AuditLogReader.Provenance.INFERRED).supportsCoverage(),
                "against a graph built from what ran, the subtraction is empty by construction");
        // "A graph the user opened by hand always wins over one the source supplied"
        assertFalse(GraphSource.OPENED.replacedBy(GraphSource.READER_DECLARED));
        assertTrue(GraphSource.READER_INFERRED.replacedBy(GraphSource.OPENED));
        // "An edge to an undeclared node is dropped."
        var a = new ProcessorTopology.Node("a", "a", "com.acme.a", ProcessorTopology.Kind.NODE);
        var b = new ProcessorTopology.Node("b", "b", "com.acme.b", ProcessorTopology.Kind.NODE);
        var t = ProcessorTopology.of(List.of(a, b), List.of(
                new ProcessorTopology.Edge("ok", "a", "b"), new ProcessorTopology.Edge("dangling", "a", "ghost")));
        assertEquals(1, t.edgeCount());
        assertEquals(2, t.nodeCount(), "and the ids are the join key — declared as given");
    }

    // ---- the set as a whole -----------------------------------------------------------------------------

    /**
     * C14 — an adapter that CONSTRUCTS record text, which is what every real one does. The suite's
     * pass-through reader slices a file with the same framer the built-in uses, so it proves the two
     * STORES agree; it cannot prove that text an adapter synthesised is read the same way. The shapes
     * below are the ones a generator actually produces, and the `.strip()` in the agreement check
     * would have hidden a difference in any of them. No fixture: the subject is the reader's output,
     * not a file (same reason as C10).
     */
    @Test
    void c14_textAnAdapterSYNTHESISEDreadsTheSameAsTextSlicedFromAFile() throws IOException {
        String body = "eventLogRecord:\n  logTime: 1000\n  event: E\n  nodeLogs:\n    - n: { v: 1}\n";
        record Emitter(String text) implements AuditLogReader {
            @Override public String formatId() { return "emit"; }
            @Override public String displayName() { return "emit"; }
            @Override public boolean canOpen(Path s) { return true; }
            @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
            @Override public Capabilities capabilities() { return new Capabilities(false, false, true); }
            @Override public void read(Path s, Consumer<String> out) { out.accept(text); }
        }
        for (String shape : List.of(body, body.stripTrailing(), "---\n" + body,
                body.replace("\n", "\r\n"))) {
            LogStore st = SpiLogStore.open(new Emitter(shape), dir.resolve("emitted"));
            assertEquals(1, st.size(), () -> "record count for " + shape.replace("\n", "\\n"));
            assertEquals(1000L, st.record(0).logTime(), () -> "logTime for " + shape.replace("\n", "\\n"));
            assertEquals(1, st.record(0).nodeLogsCount(), () -> "nodeLogs for " + shape.replace("\n", "\\n"));
        }
    }

    @Test
    void everyFixtureInTheSetIsExercised() throws IOException {
        // the set is the published artefact; a fixture nobody asserts on is a promise nobody keeps
        Path res = Path.of("src/test/resources/conformance");
        try (var files = Files.list(res)) {
            List<String> names = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".yaml")).sorted().toList();
            assertEquals(List.of("c01-minimal.yaml", "c02-unknown-fields.yaml", "c03-header.yaml", "c04-times.yaml",
                    "c05-untimed.yaml", "c06-out-of-order.yaml", "c07-duplicate-instance.yaml",
                    "c08-lenient-values.yaml", "c09-garbage.yaml", "c11-attribution.yaml",
                    "c12-traced-regime.yaml", "c13-exported-call.yaml"), names,
                    "add a fixture here AND a test above — c10 needs no file, it is about the reader's claim");
            assertTrue(Files.exists(res.resolve("README.md")), "the set is published with its table");
            for (String n : names) bothPathsAgree(n);
        }
    }

    /** A tiny declared graph in the analyser's own GraphML dialect: authored nodes, directed edges. */
    private static String graphml(List<String> ids, List<String> edges) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>"
                + "<graph edgedefault=\"directed\">");
        for (String id : ids) {
            sb.append("<node id=\"").append(id).append("\"><data key=\"vertex_label\"><jGraph:ShapeNode>")
                    .append("<jGraph:label text=\"id:").append(id).append("&#10;class:com.acme.").append(id).append("\"/>")
                    .append("<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>");
        }
        int n = 0;
        for (String e : edges) {
            String[] st = e.split(">");
            sb.append("<edge id=\"e").append(n++).append("\" source=\"").append(st[0])
                    .append("\" target=\"").append(st[1]).append("\"/>");
        }
        return sb.append("</graph></graphml>").toString();
    }
}
