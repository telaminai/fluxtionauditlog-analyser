package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MA-8 driven through {@link CoverageService#assess}, which is where it has to work.
 *
 * <p><b>Why these exist.</b> The first pass tested the parsing helper only, and review showed what that
 * left open: removing the coverage wiring, dropping the event-type check, treating every level as quiet,
 * or annotating the first change instead of the last all left the suite green. A helper test proves a
 * helper works, not that the product uses it.
 *
 * <p>The behaviour under test: a node set to a quiet level still RUNS, but its lines are suppressed, so
 * coverage lists it as uncovered with no explanation — while the control record naming it sits in the
 * log.
 */
class CoveragePerNodeLevelTest {

    private static String resource(String path) {
        try (InputStream in = CoveragePerNodeLevelTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "not in the test runtime: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** One control record, rendered as the runtime renders it. */
    private static String control(long logTime, String sourceId, String level) {
        return """
                eventLogRecord:
                  logTime: %d
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=%s, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(logTime, level, sourceId);
    }

    private static String plainRecord(long logTime) {
        return """
                eventLogRecord:
                  logTime: %d
                  event: Quote
                  nodeLogs:
                    - quoteHandler: { seen: true}
                ---
                """.formatted(logTime);
    }

    private static CoverageService.Result assess(String yaml, boolean filtered,
                                                 telamin.fluxtion.audit.analyser.analyser.filter.FilterState f) {
        ProcessorTopology topology =
                GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        HeapLogStore store = new HeapLogStore(yaml);
        return CoverageService.assess(store, filtered, f,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));
    }

    /** A filter admitting only records in [from, to]. */
    private static telamin.fluxtion.audit.analyser.analyser.filter.FilterState window(long from, long to) {
        var f = new telamin.fluxtion.audit.analyser.analyser.filter.FilterState();
        f.setTimeRange(from, to);
        return f;
    }

    private static CoverageService.Result assess(String yaml) {
        ProcessorTopology topology =
                GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        HeapLogStore store = new HeapLogStore(yaml);
        return CoverageService.assess(store, false, null,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> annotations(CoverageService.Result r) {
        Object a = r.echo().get("levelAnnotations");
        return a == null ? Map.of() : (Map<String, String>) a;
    }

    /** The node the graph declares and the log never covers; the one MA-8 should explain. */
    private static String anUncoveredNode(CoverageService.Result r) {
        return r.ledger().stream()
                .filter(row -> "uncovered".equals(row.get("status")))
                .map(row -> String.valueOf(row.get("instanceId")))
                .findFirst().orElseThrow(() -> new AssertionError("fixture has no uncovered node"));
    }

    /** MA-8.1 — a per-node quiet level is stated against the node it names. */
    @Test
    void aQuietPerNodeLevelIsAnnotatedAgainstThatNode() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        assertTrue(annotations(base).isEmpty(), "no level change, no annotation");

        CoverageService.Result withLevel = assess(control(1000, node, "WARN") + plainRecord(1001));
        assertTrue(annotations(withLevel).containsKey(node),
                "MA-8.1: the log names this node's level; coverage must say so rather than listing it "
                        + "as plainly uncovered. annotations=" + annotations(withLevel));
        assertTrue(annotations(withLevel).get(node).contains("WARN"), "and names the level");
    }

    /** MA-8.2 — annotate, NEVER excuse: the node stays uncovered and in the ratio. */
    @Test
    void anAnnotatedNodeStaysUncoveredAndInTheRatio() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        CoverageService.Result withLevel = assess(control(1000, node, "WARN") + plainRecord(1001));

        assertEquals(base.echo().get("uncovered"), withLevel.echo().get("uncovered"),
                "MA-8.2: the count must not move — excusing it would hide a node that never ran");
        assertEquals(base.echo().get("ratio"), withLevel.echo().get("ratio"),
                "MA-8.2: and neither must the ratio");
        assertTrue(withLevel.ledger().stream()
                        .anyMatch(row -> node.equals(row.get("instanceId"))
                                && "uncovered".equals(row.get("status"))),
                "MA-8.2: it is still uncovered in the ledger");
    }

    /** A level that does NOT suppress info lines must not be offered as an explanation. */
    @Test
    void aLoudLevelIsNotAnExplanation() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        CoverageService.Result withDebug = assess(control(1000, node, "DEBUG") + plainRecord(1001));

        assertFalse(annotations(withDebug).containsKey(node),
                "DEBUG does not suppress info lines, so it explains nothing: "
                        + annotations(withDebug));
    }

    /** MA-8.5 — keyed on the event TYPE. A record that merely looks like one is not a level change. */
    @Test
    void aRecordThatIsNotAControlEventIsNotReadAsALevelChange() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String lookalike = """
                eventLogRecord:
                  logTime: 1000
                  event: Quote
                  eventToString: EventLogConfig{level=WARN, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(node);

        assertFalse(annotations(assess(lookalike + plainRecord(1001))).containsKey(node),
                "MA-8.5: the event TYPE selects a control record; content that imitates one does not");
    }

    /**
     * MA-8.4 — intervals. A node quietened and later restored is annotated inside the window, and the
     * restore must not erase it.
     *
     * <p>Review found both directions wrong when only the LAST change was used: a node set to WARN and
     * restored to INFO was annotated NOWHERE, because the last change is the restore.
     */
    @Test
    void aQuietenedThenRestoredNodeIsStillAnnotatedForItsWindow() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);

        CoverageService.Result r = assess(
                control(1000, node, "WARN") + plainRecord(1001) + control(1007, node, "INFO"));

        assertTrue(annotations(r).containsKey(node),
                "MA-8.4: the WARN window is real even though the LAST change is the restore — "
                        + "using only the last change annotated nothing. annotations=" + annotations(r));
        assertTrue(annotations(r).get(node).contains("between"),
                "and the annotation states the window: " + annotations(r).get(node));
    }

    /** MA-8.2's note must travel with the annotations, or a reader may take them as an excuse. */
    @Test
    void theAnnotationCarriesItsOwnCaveat() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        CoverageService.Result r = assess(control(1000, node, "WARN") + plainRecord(1001));

        assertTrue(String.valueOf(r.echo().get("levelAnnotationsNote")).contains("still counted as uncovered"),
                "the echo must say these are not excuses: " + r.echo().get("levelAnnotationsNote"));
    }

    // ------------------------------------------------------------------ filtered scope

    /**
     * MA-8.3 — a filter that hides the control record must NOT drop the annotation.
     *
     * <p>A level change is configuration state, not an event you happen to be looking at. Every earlier
     * test ran unfiltered, which is why "drop all annotations when filtered" survived the whole suite.
     */
    @Test
    void aFilterHidingTheControlRecordStillAnnotates() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String yaml = control(1000, node, "WARN") + plainRecord(1001) + plainRecord(1002);

        CoverageService.Result filtered = assess(yaml, true, window(1001, 1002));
        assertEquals("current filter", filtered.echo().get("scope"), "precondition: the filter is on");
        assertTrue(annotations(filtered).containsKey(node),
                "MA-8.3: the control record is outside the filter, but the level still applies to it. "
                        + "annotations=" + annotations(filtered));
    }

    /**
     * MA-8.4, scope END — a change that has not happened yet cannot explain earlier silence.
     */
    @Test
    void aChangeAfterTheScopeDoesNotExplainSilenceBeforeIt() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String yaml = plainRecord(1000) + plainRecord(1001) + control(5000, node, "WARN");

        CoverageService.Result filtered = assess(yaml, true, window(1000, 1001));
        assertFalse(annotations(filtered).containsKey(node),
                "the WARN was set at 5000, after everything in view — it explains nothing here: "
                        + annotations(filtered));
    }

    /**
     * MA-8.4, scope START — a window that CLOSED before the scope began explains nothing in it.
     *
     * <p>Clipping only the end left this annotated "WARN between 1001 and 1007" for a filter entirely
     * after the restore — a window the scope never overlapped.
     */
    @Test
    void aWindowThatClosedBeforeTheScopeDoesNotExplainSilenceInIt() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String yaml = control(1001, node, "WARN") + plainRecord(1002)
                + control(1007, node, "INFO") + plainRecord(3000) + plainRecord(3001);

        CoverageService.Result filtered = assess(yaml, true, window(3000, 3001));
        assertFalse(annotations(filtered).containsKey(node),
                "the WARN window closed at 1007, long before this scope began: " + annotations(filtered));
    }

    /** LOW — one group's change must not close another group's window. */
    @Test
    void oneGroupsChangeDoesNotCloseAnothersWindow() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String groupControl = """
                eventLogRecord:
                  logTime: 1000
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=null, groupId=alpha}
                  nodeLogs:
                ---
                eventLogRecord:
                  logTime: 1005
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=INFO, logRecordProcessor=null, sourceId=null, groupId=beta}
                  nodeLogs:
                ---
                """;

        CoverageService.Result r = assess(groupControl + plainRecord(1006));
        assertTrue(annotations(r).containsKey(node),
                "alpha's WARN window is still open — beta's change must not close it: " + annotations(r));
        assertTrue(annotations(r).get(node).contains("alpha"), "and it names the group");
    }

    /** LOW — a lookalike event name is not a control event. */
    @Test
    void aLookalikeEventNameIsNotAControlEvent() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String fake = """
                eventLogRecord:
                  logTime: 1000
                  event: FakeEventLogControlEventX
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(node);

        assertFalse(annotations(assess(fake + plainRecord(1001))).containsKey(node),
                "contains() accepted this; the simple name must match exactly");
    }

    /** LOW — a fully-qualified control event name is still recognised. */
    @Test
    void aFullyQualifiedControlEventNameIsRecognised() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String qualified = """
                eventLogRecord:
                  logTime: 1000
                  event: com.telamin.fluxtion.runtime.audit.EventLogControlEvent
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(node);

        assertTrue(annotations(assess(qualified + plainRecord(1001))).containsKey(node),
                "the qualified name is the same event");
    }
}
