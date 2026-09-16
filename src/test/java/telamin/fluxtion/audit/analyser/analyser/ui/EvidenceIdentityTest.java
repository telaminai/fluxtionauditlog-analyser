package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.topology.StepCursor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceIdentityTest {
    private static LogRecord record(String key) {
        return LogRecord.builder().event("Tick").nodeLogsCount(1)
                .nodeLogsSupplier(() -> List.of(new NodeLog("n", List.of(new KV(key, "42")))))
                .build();
    }

    @Test
    void logicalEvidenceDistinguishesAbsentNullAndMarkerNames() {
        String keyless = LogicalLogView.layout(List.of(record(null))).text();
        String namedNull = LogicalLogView.layout(List.of(record("null"))).text();
        String namedMarker = LogicalLogView.layout(List.of(record("@unkeyed"))).text();
        assertNotEquals(keyless, namedNull);
        assertNotEquals(keyless, namedMarker);
        assertTrue(namedMarker.contains("\"@unkeyed\": 42"), namedMarker);
    }

    @Test
    void stepStatusDistinguishesAbsentNullAndMarkerNames() {
        var summaries = new java.util.HashSet<String>();
        for (String key : new String[]{null, "null", "@unkeyed"}) {
            var cursor = StepCursor.over(List.of(record(key)));
            cursor.next();
            assertTrue(summaries.add(cursor.rowSummary()), cursor.rowSummary());
        }
    }

    @Test
    void reportEvidenceUsesTheSameEscapingAndBothAssemblyPathsCallIt() throws Exception {
        var node = new NodeLog("n", List.of(new KV(null, "42"), new KV("null", "42"),
                new KV("@unkeyed", "42"), new KV("line\nbreak", "42"), new KV("text", "42", true)));
        String reportLine = telamin.fluxtion.audit.analyser.analyser.export.EvidenceText.nodeLine(node, "  ", "  ");
        assertEquals("n  @unkeyed=42  null=42  \"@unkeyed\"=42  \"line\\nbreak\"=42  text=\"42\"", reportLine);
        // This pins the wiring, not a rendered PDF. The formatter itself is executed above.
        String frame = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertEquals(2, frame.split("EvidenceText.nodeLine\\(nodeLog", -1).length - 1);
    }

    /** Review F1: a legacy value is shown exactly as the raw line spells it — legacy logs have no quoting. */
    @Test
    void legacyValuesAreShownAsWritten_neverQuotedOrEscaped() {
        String raw = "    - n: { path: C:\\temp\\x, quoted: \"hello\", url: http://h/p, list: [1, 2], msg: done (ok), plain: hello world}";
        String text = "---\neventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n" + raw + "\n";
        LogRecord rec = new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(text).record(0);
        String logical = LogicalLogView.layout(List.of(rec)).text();
        for (String expected : List.of("path: C:\\temp\\x", "quoted: \"hello\"", "url: http://h/p", "list: [1, 2]",
                "msg: done (ok)", "plain: hello world")) {
            assertTrue(logical.contains("      " + expected + "\n"), expected + " not shown as written:\n" + logical);
        }
        var cursor = StepCursor.over(List.of(rec));
        cursor.next();
        assertEquals("n  ·  path=C:\\temp\\x, quoted=\"hello\", url=http://h/p, list=[1, 2], msg=done (ok), plain=hello world",
                cursor.rowSummary());
    }

    /** Under the declared grammar a value the reader quoted keeps its quotes and escapes in the evidence. */
    @Test
    void declaredGrammarQuotedValuesKeepTheirQuotingInTheEvidence() {
        String text = "---\neventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - n: { path: \"C:\\\\temp\", word: plain}\n";
        LogRecord rec = telamin.fluxtion.audit.analyser.analyser.parse.RecordParser.parse(text, 0,
                telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding.QUOTED_SCALARS);
        String logical = LogicalLogView.layout(List.of(rec)).text();
        assertTrue(logical.contains("      path: \"C:\\\\temp\"\n"), logical);
        assertTrue(logical.contains("      word: plain\n"), logical);
    }
}
