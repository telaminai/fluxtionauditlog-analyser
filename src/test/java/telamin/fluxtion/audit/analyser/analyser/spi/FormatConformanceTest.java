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

        private TextEncoding encoding = TextEncoding.LEGACY;

        /** The adapter DECLARES its grammar; nothing in the bytes does. */
        PassThroughReader declaring(TextEncoding e) {
            this.encoding = e;
            return this;
        }

        @Override public TextEncoding textEncoding() { return encoding; }

        @Override public String formatId() { return "conformance-passthrough"; }
        @Override public String displayName() { return "conformance pass-through"; }
        @Override public boolean canOpen(Path source) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, true, ordering); }
        /**
         * §1a rule 1 is the PLUGIN's duty, and this reader is a plugin.
         *
         * <p>It handed an unterminated final marker over as an ordinary item, so the SPI path called the
         * file complete while the built-in reader said the claim was unfinished. The store above cannot
         * fix that — a plugin yields items one at a time, and nothing above it can see where the
         * container ended. Withholding it here is what every plugin over a text container must do.
         */
        @Override public void read(Path source, Consumer<String> out) throws IOException {
            RecordFramer.frameForPlugin(Files.readString(source), raw -> out.accept(raw.text()));
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
        return viaSpi(name, AuditLogReader.TextEncoding.LEGACY);
    }

    private LogStore viaSpi(String name, AuditLogReader.TextEncoding encoding) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, fixture(name));
        return SpiLogStore.open(new PassThroughReader(AuditLogReader.Ordering.TOTAL).declaring(encoding), f);
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
        // Round six S-4: this compared records and never compared what the two paths SAY about the
        // container, so the reference plugin read six files as complete where the built-in reader said
        // the claim was unfinished and nothing failed. Completeness is part of "the two paths agree".
        // Neither path may ever claim more than the other. That is the safety property, and it is exact.
        assertEquals(a.streamEnd().isKnownComplete(), b.streamEnd().isKnownComplete(),
                name + ": one path claims completeness and the other does not");
        // States may differ in PRECISION in exactly one way, and only in the safe direction. The
        // built-in reader sees the bytes and can say "unterminated marker"; a plugin yields items one at
        // a time and, having withheld that item under §1a rule 1, can only report "unknown" — it has no
        // way to say WHY. That is an SPI gap, filed as AF-10, not a licence to disagree: every other
        // combination fails here.
        // EVIDENCE never differs, whatever label each path puts on it. Round seven built a plugin whose
        // only defect was never handing over a marker: identical records, identical isKnownComplete, and
        // the tolerated state pair — so the old tolerance skipped the comparison entirely while one path
        // reported a proven loss and the other said nothing. The runs are the proof, and they are
        // compared unconditionally.
        assertEquals(a.streamEnd().runs(), b.streamEnd().runs(),
                name + ": one path proved a run lost records and the other did not");

        boolean plugInIsLessPrecise =
                a.streamEnd().state() == telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNTERMINATED_MARKER
                        && b.streamEnd().state() == telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNKNOWN;
        if (!plugInIsLessPrecise) {
            assertEquals(a.streamEnd().state(), b.streamEnd().state(), name + ": completeness state");
            assertEquals(a.completenessDiagnostics(), b.completenessDiagnostics(),
                    name + ": what each path says about completeness");
        } else {
            // The ONE thing a plugin may lose is the explanation of why it withheld the item (AF-10).
            // Nothing else: it may not be silent about anything the built-in reader had to say.
            assertTrue(b.completenessDiagnostics().isEmpty(),
                    () -> name + ": a plugin that can say something must say the same thing: "
                            + b.completenessDiagnostics());
            assertEquals(1, a.completenessDiagnostics().size(),
                    () -> name + ": exactly one sentence may be lost, not a set of them: "
                            + a.completenessDiagnostics());
            assertTrue(a.completenessDiagnostics().get(0).contains("no closing ---"),
                    () -> name + ": and it must be the unterminated-marker sentence, nothing else: "
                            + a.completenessDiagnostics().get(0));
        }
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
        assertTrue(AuditTrace.tracesEveryInvocation(traced), "every entry carries method: traced");
        assertFalse(AuditTrace.tracesEveryInvocation(untraced),
                "one business key called 'method' must not make a sparse record look complete");
        // a business property is never a declaration, in either spelling (review, round 7)
        assertFalse(AuditTrace.tracesEveryInvocation(new HeapLogStore(
                "---\neventLogRecord:\n  logTime: 1\n  nodeLogs:\n    - a: { invoked: true, v: 1}\n    - b: { invoked: true}\n").record(0)),
                "an ordinary invoked: true on every logged node is business data, not a tracing declaration");

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
    void c16_aQuotedScalarIsAStringWhateverItSpells_andItsInsidesSplitNothing() throws IOException {
        // The grammar is the READER's declaration. The same bytes through the built-in text reader are
        // legacy: quotes kept, nothing decoded, and "ok, price: 42.0" is one quoted-looking value.
        LogRecord legacy = builtIn("c16-quoted-scalars.yaml").record(0);
        assertEquals("\"ok, price: 42.0\"", legacy.nodeLogs().get(0).last("status").rawValue(),
                "the built-in text reader never decodes: nothing in the text selects a grammar");
        assertFalse(legacy.nodeLogs().get(0).last("status").quoted());
        assertEquals(9, legacy.nodeLogs().get(0).entries().size(), "the quotes protect the separators under both grammars");
        assertEquals(19.5, legacy.nodeLogs().get(0).last("price").numeric().getAsDouble(), 0,
                "the real figure, not one manufactured from the status string");

        LogStore s = viaSpi("c16-quoted-scalars.yaml", AuditLogReader.TextEncoding.QUOTED_SCALARS);
        assertEquals(AuditLogReader.TextEncoding.QUOTED_SCALARS, s.textEncoding());
        LogRecord r = s.record(0);
        assertEquals(EventKind.OK, r.kind());
        assertEquals(2, r.nodeLogs().size(), r.nodeLogs().toString());
        var pricer = r.nodeLogs().get(0);
        assertEquals(9, pricer.entries().size(), "the quoted commas and colons split nothing: " + pricer.entries());
        assertEquals("ok, price: 42.0", pricer.last("status").rawValue());
        assertTrue(pricer.last("status").quoted());
        assertEquals("line\nbreak and \"quotes\" and back\\slash", pricer.last("note").rawValue());
        assertFalse(pricer.last("nullText").isNull(), "\"null\" is a string");
        assertTrue(pricer.last("numberText").numeric().isEmpty(), "\"42.0\" is a string, not a figure");
        assertNull(pricer.last("flagText").asBoolean(), "\"true\" is a string, not a flag");
        assertEquals("", pricer.last("empty").rawValue());
        assertEquals(19.5, pricer.last("price").numeric().getAsDouble(), 0, "a bare number is still a figure");
        assertEquals(Boolean.TRUE, pricer.last("live").asBoolean());
        assertTrue(pricer.last("gone").isNull(), "a bare null is still null");
        var odd = r.nodeLogs().get(1);
        assertEquals("odd}: {node", odd.instanceId(), "a quoted instance id decodes");
        assertEquals("1", odd.last("a, b: c").rawValue(), "and so does a quoted key");
        assertEquals("2", odd.last("plain").rawValue());
    }

    /**
     * REVIEWER PROBE (round 5). The quoted grammar applied to an undeclared record swallowed the entry
     * after {@code prefix "C:\"}, so a following {@code price} vanished and a scorer carried the earlier
     * figure forward: PASS where the previous reader said FAIL. Legacy text is read as legacy text.
     */
    @Test
    void c17_anUndeclaredRecordIsReadWithTheLegacyGrammar_quotesAreData() throws IOException {
        LogStore s = bothPathsAgree("c17-legacy-quotes.yaml");
        LogRecord r = s.record(1);
        assertEquals(EventKind.OK, r.kind());
        var n = r.nodeLogs().get(0);
        assertEquals(4, n.entries().size(), "the closing quote closed: " + n.entries());
        assertEquals("prefix \"C:\\\"", n.last("path").rawValue());
        assertEquals(77, n.last("price").numeric().getAsDouble(), 0, "the figure after it is still a figure");
        assertEquals("\"hello\"", n.last("greeting").rawValue(), "the quotes are the producer's characters");
        assertFalse(n.last("greeting").quoted());
        assertTrue(n.last("count").numeric().isEmpty(), "and a quoted number was never a figure");

        // REVIEWER PROBE (round 6): a multiline legacy VALUE whose middle line is spelled like a control
        // field. It is value data, folded by continuation; nothing in the text selects a grammar, so the
        // price after it is still a figure and the scorer still says 42 -> 77 is a change.
        LogRecord multi = s.record(2);
        assertEquals(EventKind.OK, multi.kind());
        var m = multi.nodeLogs().get(0);
        assertEquals(2, m.entries().size(), "message and price: " + m.entries());
        assertEquals("start nodeLogsEncoding: quoted end", m.last("message").rawValue());
        assertEquals(77, m.last("price").numeric().getAsDouble(), 0);

        // the scorer's verdict, which is what the false PASS was about
        var scorer = new telamin.fluxtion.audit.analyser.analyser.score.ExpectationScorer(
                telamin.fluxtion.audit.analyser.analyser.score.ExpectationScorer.Dialect.NATURAL,
                "stage", "value", java.util.Set.of("Tick", "tick"), 1e-6);
        List<LogRecord> actual = List.of(s.record(0), s.record(1));
        var expected = scorer.snapshots(List.of(s.record(0), s.record(0)));   // price stays 42 on the second Tick
        var result = scorer.score(expected, scorer.snapshots(actual));
        assertFalse(result.pass(), "price moved 42 -> 77 and the verdict must say so: " + result);
        var multiResult = scorer.score(expected, scorer.snapshots(List.of(s.record(0), s.record(2))));
        assertFalse(multiResult.pass(), "the reviewer's multiline record: 42 -> 77 must not PASS: " + multiResult);
    }

    /**
     * The declaration is authoritative, not a hint: an adapter that declares QUOTED_SCALARS and then
     * emits a bare value that happens to be entirely quoted gets it decoded. That is the contract, and it
     * is pinned so nobody later "fixes" it by looking at the bytes. C17's bytes through a declaring
     * adapter read differently from the same bytes through the text reader - by design.
     */
    @Test
    void aDeclaringAdapterIsBelieved_evenOverTextThatWasNeverQuotedOnPurpose() throws IOException {
        String text = "---\neventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - n: { greeting: \"hello\", price: 1}\n";
        Path f = dir.resolve("adapter-declares.yaml");
        Files.writeString(f, text);
        LogStore declared = SpiLogStore.open(
                new PassThroughReader(AuditLogReader.Ordering.TOTAL).declaring(AuditLogReader.TextEncoding.QUOTED_SCALARS), f);
        LogRecord r = declared.record(0);
        assertEquals("hello", r.nodeLogs().get(0).last("greeting").rawValue(), "decoded, because the adapter said so");
        assertTrue(r.nodeLogs().get(0).last("greeting").quoted());
        LogStore legacy = new HeapLogStore(text);
        assertEquals("\"hello\"", legacy.record(0).nodeLogs().get(0).last("greeting").rawValue(), "same bytes, no declaration");
        assertFalse(legacy.record(0).nodeLogs().get(0).last("greeting").quoted());
    }

    /**
     * The hole round seven demonstrated in this very helper, now closed and pinned.
     *
     * <p>A plugin whose ONLY defect is that it never hands a marker over produces identical records and
     * identical {@code isKnownComplete}, and lands on the tolerated state pair — so the old tolerance
     * skipped every remaining comparison while the built-in reader reported a proven loss and the plugin
     * said nothing at all. The evidence is compared unconditionally now, and this proves it.
     */
    @Test
    void aPluginThatSwallowsMarkersIsCaughtEvenOnTheToleratedStatePair() throws IOException {
        String body = "---\neventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - b: { v: 1}\n"
                + "---\neventLogRecord:\n  logTime: 2\n  event: Tick\n  nodeLogs:\n    - b: { v: 1}\n"
                + "---\neventLogRecord:\n  logTime: 3\n  event: Tick\n  nodeLogs:\n    - b: { v: 1}\n"
                + "---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 5\n"   // declares 5, holds 3
                + "---\neventLogRecord:\n  logTime: 4\n  event: Tick\n  nodeLogs:\n    - b: { v: 1}\n"
                + "---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 4";     // unterminated
        Path f = dir.resolve("swallowed.yaml");
        Files.writeString(f, body);

        record MarkerSwallowingReader() implements AuditLogReader {
            @Override public String formatId() { return "swallows-markers"; }
            @Override public String displayName() { return "swallows markers"; }
            @Override public boolean canOpen(Path s) { return true; }
            @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
            @Override public Capabilities capabilities() {
                return new Capabilities(false, false, true, Ordering.TOTAL);
            }
            @Override public void read(Path source, Consumer<String> out) throws IOException {
                RecordFramer.frameForPlugin(Files.readString(source), raw -> {
                    if (telamin.fluxtion.audit.analyser.analyser.parse.StreamEndMarker
                            .of(raw.text()).isEmpty()) out.accept(raw.text());
                });
            }
        }

        LogStore builtIn = new HeapLogStore(body);
        LogStore plugin = SpiLogStore.open(new MarkerSwallowingReader(), f);

        // the shape that made it slip through: same records, same claim, the tolerated state pair
        assertEquals(builtIn.size(), plugin.size(), "identical records, which is what made it invisible");
        assertEquals(builtIn.streamEnd().isKnownComplete(), plugin.streamEnd().isKnownComplete());
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNTERMINATED_MARKER,
                builtIn.streamEnd().state());
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNKNOWN,
                plugin.streamEnd().state());

        // and the evidence differs, which is the thing that must never be skipped
        assertEquals(1, builtIn.streamEnd().runs().size(), "the built-in reader proved run 1 lost records");
        assertEquals(0, plugin.streamEnd().runs().size(), "and the plugin says nothing about it");
        assertNotEquals(builtIn.streamEnd().runs(), plugin.streamEnd().runs(),
                "so the unconditional comparison in bothPathsAgree is what catches this");
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
                    "c12-traced-regime.yaml", "c13-exported-call.yaml", "c16-quoted-scalars.yaml",
                    "c17-legacy-quotes.yaml", "c18-stream-end.yaml", "c19-export-layout.yaml",
                    "c20-marker-lookalike.yaml", "c21-real-export.yaml", "c22-marker-syntax.yaml",
                    "c23-marker-values.yaml", "c24-unterminated-marker.yaml"), names,
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

    /**
     * C18 — the stream-end marker is a container fact and not a record (spec-audit-stream-end D-E4).
     *
     * <p>The assertion that matters is the one about BOTH paths: if the built-in reader suppressed the
     * marker and the SPI path did not, two readers of one file would disagree about how many records it
     * holds. {@code bothPathsAgree} would catch it, which is why this fixture runs through it.
     */
    @Test
    void c18_streamEndMarkerIsNotARecordInEitherPath() throws IOException {
        LogStore s = bothPathsAgree("c18-stream-end.yaml");
        assertEquals(2, s.size(), "two records; the marker is not one of them");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.COMPLETE,
                s.streamEnd().state(), "the marker's count matches what was read");
        assertEquals(Long.valueOf(1001), s.maxLogTime(),
                "the marker's own logTime must not extend the timeline");
    }

    /**
     * C19 — a whole file may end without a trailing separator, and this is the common case.
     *
     * <p>§1 makes {@code ---} a separator, not a terminator, and Mongoose's audit export writes
     * {@code \n---\n} only BETWEEN records. The first version of the stream-end work treated an unclosed
     * trailing record as a writer that stopped mid-record, so every real export was reported as damaged
     * while all fifteen existing fixtures — each of which happens to end with a separator — stayed green.
     * That is what this fixture is for: the suite could not see the dominant real shape.
     */
    @Test
    void c19_aFileThatEndsWithoutASeparatorIsWholeAndOrdinary() throws IOException {
        LogStore s = bothPathsAgree("c19-export-layout.yaml");
        assertEquals(2, s.size(), "both records are read; the last one is not withheld or flagged");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNKNOWN,
                s.streamEnd().state(), "nothing in this file claims completeness either way");
        assertTrue(s.sourceDiagnostics().isEmpty(),
                () -> "an ordinary export must not be reported as damaged: " + s.sourceDiagnostics());
    }

    /**
     * C20 — a producer's own text must never be able to delete a record.
     *
     * <p>{@link telamin.fluxtion.audit.analyser.analyser.parse.RecordParser} is indentation-insensitive,
     * so a line inside a multiline {@code eventToString} looks exactly like a top-level key. Recognising
     * the marker by searching for that key therefore let an ordinary {@code toString} remove its own
     * record from the index on all three paths, and the file then reported records missing. Silent,
     * content-controlled data loss — a D-T8 violation — so recognition is an allow-list instead.
     */
    /**
     * C21 — a REAL export, kept because everything else here is something a person typed.
     *
     * <p>Three review rounds turned on the gap between the layout this project imagined and the layout
     * its own producer writes. Round one measured it: a byte-exact export of 25 records reported as a
     * damaged tail, while every hand-written fixture stayed green. `c19` was the corrective and is still
     * constructed, with invented headers; this is the bytes themselves. It carries details nobody would
     * have thought to invent — four-space indent, a trailing space after `eventLogRecord:`, an empty
     * `nodeLogs:` on lifecycle records, no leading separator and no trailing one.
     */
    @Test
    void c21_theRealProducersOwnLayoutReadsAsAnOrdinaryWholeLog() throws IOException {
        LogStore s = bothPathsAgree("c21-real-export.yaml");
        assertEquals(25, s.size(), "every record of the real export is read");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNKNOWN,
                s.streamEnd().state(), "it carries no marker, so it claims nothing");
        assertTrue(s.sourceDiagnostics().isEmpty(),
                () -> "the dominant real producer must not look damaged: " + s.sourceDiagnostics());
        int parseErrors = 0;
        for (int i = 0; i < s.size(); i++) {
            if (s.record(i).kind() == telamin.fluxtion.audit.analyser.analyser.model.EventKind.PARSE_ERROR) {
                parseErrors++;
            }
        }
        assertEquals(0, parseErrors, "four-space indent and trailing spaces are not parse errors");
        assertEquals("PriceEvent", s.record(24).event());
        assertEquals(2, s.record(24).nodeLogsCount(), "a real cycle's node logs survive the round trip");
    }

    /**
     * C22 — the two shapes where a reader written from §1a's PROSE disagreed with this code.
     *
     * <p>Re-review implemented §1a from the published text alone and compared 53 files; these two
     * differed. Both are now stated in the prose and pinned here, because a normative section exists so
     * that someone else can implement it and agree.
     */
    @Test
    void c22_everyRuleInTheRecognitionTable() throws IOException {
        LogStore s = bothPathsAgree("c22-marker-syntax.yaml");
        // Two real records, plus the two marker LOOKALIKES the table disqualifies, which are records.
        assertEquals(4, s.size(),
                "a duplicate key and an empty value are not markers, so both are ordinary records");
        assertEquals("Tick", s.record(0).event());
        assertEquals("Tick", s.record(1).event());
        assertNull(s.record(2).event(), "the duplicate-streamEnd record, kept as evidence");
        assertNull(s.record(3).event(), "the empty-value record, kept as evidence");

        // Run 1 is closed by a marker whose count is unreadable: an end claimed with nothing behind it.
        // Run 2's marker has no space after the colon and a quoted count followed by a comment, and
        // declares 3 over the 3 records that precede it. The weakest verdict is the file's.
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNVERIFIED,
                s.streamEnd().state(), "an unreadable count is unverified, never missing-records");
        assertEquals(1, s.completenessDiagnostics().size());
        assertTrue(s.completenessDiagnostics().get(0).contains("no readable record count"),
                s.completenessDiagnostics().get(0));
    }

    /**
     * C23 — the value rules of §1a's recognition table, which nothing pinned as a fixture.
     *
     * <p>Round five listed them: a second {@code streamEndRecords}, a negative count, a {@code #} inside
     * quotes, single quotes, a comment line inside a marker, an unknown reason value, flow style, and the
     * unbalanced-quote fallback. They were covered by unit tests only, so an outside adapter author
     * running the published suite never met them — which is the same gap that let a round-three blocker
     * through. Eight Tick records survive; the two disqualified lookalikes are records.
     */
    @Test
    void c23_theValueRulesOfTheRecognitionTable() throws IOException {
        LogStore s = bothPathsAgree("c23-marker-values.yaml");
        assertEquals(10, s.size(),
                "eight ordinary records, plus the duplicate-key and flow-style records that are NOT markers");
        assertEquals("Tick", s.record(6).event());
        assertNull(s.record(7).event(), "a second streamEndRecords disqualifies: this is a record");
        assertNull(s.record(9).event(), "flow style is not a marker either");
        // `stopping`, an unknown value, single quotes, a quoted `#`, an unbalanced quote and a comment
        // line all ARE markers, so none of them reached the index. Asserted as "no indexed row parses as
        // a marker" rather than "no indexed row mentions the key" — the two lookalikes DO mention it, and
        // that is the whole point of c20.
        for (int i = 0; i < s.size(); i++) {
            assertTrue(telamin.fluxtion.audit.analyser.analyser.parse.StreamEndMarker
                            .of(s.rawText(i)).isEmpty(),
                    "row " + i + " is a marker and should never have been indexed");
        }
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNKNOWN,
                s.streamEnd().state(), "a record follows the last marker");
        assertEquals(1, s.streamEnd().runs().size(),
                "and the negative count is still reported as an unverified run");
    }

    /**
     * C24 — §1a rule 1, and the reason it has to be a FIXTURE rather than a unit test.
     *
     * <p>A marker with no closing separator is not a claim. The file is the exact shape the Mongoose
     * exporter produces today with a marker appended: separators between documents and nothing after the
     * last. Round six found the SPI path reading six such files as COMPLETE where the built-in reader
     * said the claim was unfinished — a plugin hands items over one at a time, so applying the rule is
     * the PLUGIN's duty and nothing above it can do the job. {@code bothPathsAgree} is what makes that
     * checkable, and this fixture is what makes it fail when a plugin forgets.
     */
    @Test
    void c24_anUnterminatedMarkerIsNotAClaimOnEitherPath() throws IOException {
        LogStore s = bothPathsAgree("c24-unterminated-marker.yaml");
        assertEquals(2, s.size(), "the marker is held back, not shown as an unexplained empty row");
        // The plugin path withholds it too — the count matches above — but cannot say why (AF-10).
        assertFalse(viaSpi("c24-unterminated-marker.yaml").streamEnd().isKnownComplete(),
                "what must never differ is whether completeness is claimed");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNTERMINATED_MARKER,
                s.streamEnd().state());
        assertFalse(s.streamEnd().isKnownComplete(), "an unfinished claim is not a claim");
        assertEquals(1, s.completenessDiagnostics().size());
        assertTrue(s.completenessDiagnostics().get(0).contains("no closing ---"),
                s.completenessDiagnostics().get(0));
        assertTrue(s.completenessDiagnostics().get(0).contains("writer MUST terminate"),
                () -> "it must name whose job it is: " + s.completenessDiagnostics().get(0));
    }

    @Test
    void c20_aRecordThatMentionsTheMarkerKeyIsStillARecord() throws IOException {
        LogStore s = bothPathsAgree("c20-marker-lookalike.yaml");
        assertEquals(2, s.size(), "both lookalikes are records; only the real marker is suppressed");
        assertEquals("ShutdownRequest", s.record(0).event(), "the multiline toString kept its record");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.COMPLETE,
                s.streamEnd().state(), "and the real marker still counts two");
    }

    /** A file with no marker is UNKNOWN — the state of every fixture here, and of every existing log. */
    @Test
    void c01_aFileWithNoMarkerIsUnknownNotComplete() throws IOException {
        LogStore s = builtIn("c01-minimal.yaml");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd.State.UNKNOWN,
                s.streamEnd().state());
        assertFalse(s.streamEnd().isKnownComplete(), "silence is not a completeness claim");
    }
}
