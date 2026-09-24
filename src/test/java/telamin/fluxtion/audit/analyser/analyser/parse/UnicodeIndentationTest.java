package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one BEHAVIOUR CHANGE the F1 consolidation made that is not about byte-order marks, pinned.
 *
 * <p>{@code RecordParser} used {@link String#strip()}, which removes <b>every</b> Unicode space.
 * {@link AuditText#strip} removes only the four §1a characters — space, tab, CR, LF — plus a leading
 * byte-order mark. That is the narrower and more correct rule, and it is what the rest of the parser
 * already did. It is also, for one class of input, a regression: a record indented with a space
 * character §1a does not name no longer parses.
 *
 * <p>The report round 4 recorded this as "untested, because nothing in the corpus produces one". The
 * final review's answer was that untested is not the same as unmeasurable — so it is measured here.
 * Two facts are worth pinning, and the second is the uncomfortable one:
 *
 * <ol>
 *   <li>the fields indented that way are lost;</li>
 *   <li>the record is still {@code OK}, not {@code PARSE_ERROR} — {@code eventLogRecord:} sits at
 *       column 0, so {@code sawFields} is set and nothing says the body went missing. The loss is
 *       QUIET, and the producer finding that does fire names the wrong cause.</li>
 * </ol>
 *
 * <p><b>Why the narrowing is kept anyway.</b> YAML permits only the space character for indentation,
 * so a file indented with U+3000 is malformed at the format level before it reaches this parser; no
 * producer, fixture or conformance case in the corpus emits one; and accepting Unicode spaces here
 * while every framer rejects them would put the two halves of the reader back out of step, which is
 * the drift {@link AuditText} exists to end. If a real producer is ever found doing this, the fix is
 * a widened {@code AuditText.strip} — one place — and this test is what will change.
 */
class UnicodeIndentationTest {

    private static final String IDEOGRAPHIC_SPACE = "　";
    private static final String EM_SPACE = " ";

    /** Indented with {@code indent} rather than ASCII spaces; the record key stays at column 0. */
    private static String record(String indent) {
        return "eventLogRecord:\n"
                + indent + "logTime: 1000\n"
                + indent + "event: Tick\n"
                + indent + "nodeLogs:\n"
                + indent + indent + "- pricer: { price: 1.5}\n";
    }

    private static List<String> kinds(String file) {
        HeapLogStore store = new HeapLogStore(file);
        return ProducerDiagnostics
                .of(store.index(), store::rawText, List.of(), List.of(), false)
                .findings().stream().map(f -> f.kind().name()).toList();
    }

    @Test
    void asciiSpaceIndentationParsesEveryField() {
        var r = RecordParser.parse(record("  "), 0);
        assertEquals("Tick", r.event(), "precondition: the ordinary record parses");
        assertEquals(1000L, r.logTime());
        assertEquals(1, r.nodeLogsCount());
    }

    @Test
    void ideographicSpaceIndentationLosesTheIndentedFields() {
        var r = RecordParser.parse(record(IDEOGRAPHIC_SPACE), 0);

        assertNull(r.event(),
                "U+3000 is not §1a whitespace, so the key fails isIdentifier at character 0 and the "
                        + "scalar is dropped. String.strip() used to remove it and this parsed");
        assertNull(r.logTime(), "same for logTime");
        assertEquals(0, r.nodeLogsCount(), "and the nodeLogs block never opens");
    }

    @Test
    void emSpaceIndentationBehavesIdentically() {
        var r = RecordParser.parse(record(EM_SPACE), 0);
        assertNull(r.event(), "U+2003 is the same class of character as U+3000 here");
        assertEquals(0, r.nodeLogsCount());
    }

    /**
     * The part that matters more than the loss: nothing says the body went missing. The record is
     * still OK because the record KEY parsed, and the producer finding that fires blames the graph.
     */
    @Test
    void theLossIsQuietAndTheFindingNamesTheWrongCause() {
        var r = RecordParser.parse(record(IDEOGRAPHIC_SPACE), 0);
        assertEquals(telamin.fluxtion.audit.analyser.analyser.model.EventKind.OK, r.kind(),
                "eventLogRecord: is at column 0, so sawFields is set: the record does not read as "
                        + "damaged. A reader is told nothing");

        String file = record(IDEOGRAPHIC_SPACE) + "---\n";
        assertTrue(kinds(file).contains("NO_NODE_LOGS"),
                "the only signal is NO_NODE_LOGS — whose message says the graph was built without "
                        + "addEventAudit(). For this file that is the WRONG cause, and it is recorded "
                        + "as a known misattribution rather than fixed: no producer emits such a file, "
                        + "and inventing a finding for an unobserved shape is how diagnostics rot");
    }

    /** The framers were ASCII-only already, so they are not a second story: same file, same answer. */
    @Test
    void theFramersWereAlreadyAsciiOnly() {
        HeapLogStore store = new HeapLogStore(record(IDEOGRAPHIC_SPACE) + "---\n");
        assertEquals(1, store.size(), "the record still frames — only its indented fields are lost");
        assertEquals(0, store.record(0).nodeLogsCount());
    }
}
