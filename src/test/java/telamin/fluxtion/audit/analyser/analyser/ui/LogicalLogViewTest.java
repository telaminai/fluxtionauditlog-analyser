package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Logical node-log view (M22.12). Only the <b>layout</b> is tested — colouring is Swing and headless
 * CI cannot see it — but layout is the part that matters beyond looks: the block offsets are what map a
 * click back to a node and what the step cursor highlights, so an off-by-one here sends the user to the
 * wrong source file rather than merely looking wrong.
 */
class LogicalLogViewTest {

    private static List<LogRecord> fixture(String name) throws IOException {
        try (InputStream in = LogicalLogViewTest.class.getResourceAsStream("/topology/" + name)) {
            assertNotNull(in, name + " missing");
            HeapLogStore store = new HeapLogStore(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<LogRecord> out = new ArrayList<>();
            for (int i = 0; i < store.size(); i++) out.add(store.record(i));
            return out;
        }
    }

    private static List<LogRecord> parse(String yaml) {
        HeapLogStore store = new HeapLogStore(yaml);
        List<LogRecord> out = new ArrayList<>();
        for (int i = 0; i < store.size(); i++) out.add(store.record(i));
        return out;
    }

    @Test
    void everyNodeGetsABlockAndEveryValueItsOwnLine() throws IOException {
        List<LogRecord> records = fixture("demo-quote-audit.yaml");
        LogicalLogView.Layout layout = LogicalLogView.layout(records);

        int nodeLogs = records.stream().mapToInt(r -> r.nodeLogs().size()).sum();
        assertEquals(nodeLogs, layout.blocks().size(), "one block per nodeLogs row, repeats included");
        assertEquals(records.size(), layout.recordStarts().size());

        // the raw form puts a node's whole state on one line; the logical form breaks it out
        assertTrue(layout.text().contains("  priceListener\n      symbol: DEMO-A\n      mid: "),
                "values sit under the node that logged them:\n" + layout.text().substring(0, 200));
    }

    @Test
    void aBlockCoversItsOwnValuesAndNotTheNextNodes() {
        LogicalLogView.Layout layout = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: E
                    nodeLogs:
                        - alpha: { a: 1, b: 2}
                        - beta: { c: 3}
                """));
        assertEquals(2, layout.blocks().size());
        LogicalLogView.Block alpha = layout.blocks().get(0);
        LogicalLogView.Block beta = layout.blocks().get(1);

        assertEquals("alpha", layout.blockAt(alpha.start()).instanceId());
        assertEquals("alpha", layout.blockAt(alpha.end() - 1).instanceId(),
                "the last value line still belongs to alpha");
        assertEquals("beta", layout.blockAt(beta.start()).instanceId());
        assertEquals(alpha.end(), beta.start(), "blocks abut — no gap for a click to fall into");
    }

    @Test
    void theHeaderRangeIsJustTheNodeName() {
        LogicalLogView.Layout layout = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: E
                    nodeLogs:
                        - alpha: { a: 1}
                """));
        LogicalLogView.Block b = layout.blocks().get(0);
        assertEquals("  alpha", layout.text().substring(b.headerStart(), b.headerEnd()),
                "what the step cursor selects is the node line, not its values");
    }

    @Test
    void aNodeLoggingTwiceGetsTwoBlocksAddressableByOccurrence() {
        LogicalLogView.Layout layout = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: E
                    nodeLogs:
                        - worker: { pass: 1}
                        - worker: { pass: 2}
                """));
        assertEquals(2, layout.blocks().size());
        LogicalLogView.Block first = layout.block("worker", 0);
        LogicalLogView.Block second = layout.block("worker", 1);
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.start(), second.start(),
                "stepping onto the second firing must not highlight the first");
        assertNull(layout.block("worker", 2));
    }

    @Test
    void theMethodComesFromTheTracedKeyWhenThereIsOne() throws IOException {
        // traced records carry method per node — that is the method to open, not a guess from the first key
        LogicalLogView.Layout layout = LogicalLogView.layout(fixture("demo-quote-audit-traced.yaml"));
        assertTrue(layout.blocks().stream().anyMatch(b -> b.method() != null && !b.method().isBlank()));

        LogicalLogView.Layout sparse = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: E
                    nodeLogs:
                        - alpha: { method: onThing, thread: main}
                """));
        assertEquals("onThing", sparse.blocks().get(0).method());
    }

    @Test
    void frameworkKeysAreMarkedButNotDropped() {
        // they are the marker that says the record is traced — hiding them would hide the regime
        assertTrue(LogicalLogView.isFrameworkKey("thread"));
        assertTrue(LogicalLogView.isFrameworkKey("method"));
        assertFalse(LogicalLogView.isFrameworkKey("mid"));

        LogicalLogView.Layout layout = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: E
                    nodeLogs:
                        - alpha: { method: onThing, mid: 2.5}
                """));
        assertTrue(layout.text().contains("method: onThing"), "muted, still present");
        assertTrue(layout.text().contains("mid: 2.5"));
    }

    @Test
    void aCycleWhereNothingLoggedSaysSoRatherThanRenderingBlank() {
        LogicalLogView.Layout layout = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: QuietEvent
                """));
        assertTrue(layout.text().contains("no node logged in this cycle"),
                "silence is a finding in this app, not an empty panel");
        assertTrue(layout.blocks().isEmpty());
    }

    @Test
    void anEmptySelectionIsEmptyRatherThanThrowing() {
        LogicalLogView.Layout layout = LogicalLogView.layout(List.of());
        assertEquals("", layout.text());
        assertTrue(layout.blocks().isEmpty());
        assertNull(layout.blockAt(0));
        assertNull(layout.block("anything", 0));
    }

    @Test
    void aMultiLineSignatureStaysInsideItsBlock() {
        // the compiler emits "@Override\npublic void suspendQuoting(String arg0)" — a raw newline would
        // break the indent-driven styling and put a stray line at column 0
        LogicalLogView.Layout layout = LogicalLogView.layout(parse("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: ExportFunctionAuditEvent
                    eventToString: "@Override\\npublic void suspendQuoting(String arg0)"
                    nodeLogs:
                        - quotePublisher: { suspended: true}
                """));
        for (String line : layout.text().split("\n")) {
            if (line.contains("suspendQuoting")) {
                assertTrue(line.startsWith("  "), "continuation lines stay indented: '" + line + "'");
            }
        }
    }
}
