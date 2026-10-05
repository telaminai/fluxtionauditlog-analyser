package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * UPS-1 in the real frame: Mongoose 1.0.32 / fluxtion 1.1.0 admin command records whose operator-typed arguments carry a
 * line break (conformance c31), opened with the graph generated for that processor. Before the fix the analyser said
 * the graph failed to declare a node called {@code forged} and showed an event type {@code Forged]]}; every surface must
 * now say the graph declares what the log writes, show only dispatched event types, and name the broken records.
 */
class BrokenValueFrameTest {

    @Test
    void aBrokenValueForgesNoNodeAndNoEventOnAnySurface(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = tmp.resolve("DEMO-admin-commands.yaml");
        Path graph = tmp.resolve("DEMO-CycleAlarmProcessor.graphml");
        try (var in = getClass().getResourceAsStream("/conformance/c31-broken-value.yaml")) { Files.write(log, in.readAllBytes()); }
        try (var in = getClass().getResourceAsStream("/topology/demo-cycle-alarm-processor.graphml")) { Files.write(graph, in.readAllBytes()); }
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString(), "graphml", graph.toString())).ok());
            long deadline = System.currentTimeMillis() + 20_000;
            AtomicReference<String> pairing = new AtomicReference<>("");
            while (true) {
                String p = f.ex.render("context", Map.of("sections", List.of("log", "pairing"))).toJson();
                pairing.set(p);
                if (p.contains("\"records\":5") && p.contains("\"verdict\"") && !p.contains("pending")) break;
                assertTrue(System.currentTimeMillis() < deadline, "the log and its pairing never published: " + p);
                Thread.sleep(50);
            }
            String context = pairing.get();
            assertTrue(context.contains("the graph declares all 2 node(s) this log writes"),
                    "the graph is not blamed for operator text: " + context);
            assertFalse(context.contains("forged"), context);

            String all = f.ex.render("context", Map.of()).toJson();
            assertTrue(all.contains("break their own structure"),
                    "context.producer names the broken records: " + all.substring(0, Math.min(all.length(), 400)));

            onEdt(() -> {
                LogTableModel model = (LogTableModel) ((javax.swing.JTable) field(field(f.frame, "tablePanel"), "table")).getModel();
                for (int row = 0; row < model.getRowCount(); row++) {
                    for (int col = 0; col < model.getColumnCount(); col++) {
                        assertNotEquals("Forged]]", String.valueOf(model.getValueAt(row, col)),
                                "row " + row + " shows a forged event type");
                    }
                }
                String status = ((JLabel) field(f.frame, "status")).getText();
                assertTrue(status.contains("a value broke its record"), "the status bar says so in words: " + status);
            });
        }
    }

    /**
     * The review of 9474c687, finding 3, in the real frame: every record is broken and each withheld block holds genuine
     * node output, so the {@code coverage} verb, the pairing in {@code context} and the records table must say the node
     * logs were withheld — never that a node never wrote, that no node output was recorded, or that a record holds 0.
     */
    @Test
    void withheldNodeLogsAreNeverReportedAsAbsent(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = tmp.resolve("DEMO-all-broken.yaml");
        Path graph = tmp.resolve("DEMO-CycleAlarmProcessor.graphml");
        try (var in = getClass().getResourceAsStream("/ups1/all-broken-with-node-output.yaml")) { Files.write(log, in.readAllBytes()); }
        try (var in = getClass().getResourceAsStream("/topology/demo-cycle-alarm-processor.graphml")) { Files.write(graph, in.readAllBytes()); }
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString(), "graphml", graph.toString())).ok());
            long deadline = System.currentTimeMillis() + 20_000;
            String context;
            while (true) {
                context = f.ex.render("context", Map.of("sections", List.of("log", "pairing"))).toJson();
                if (context.contains("\"records\":2") && context.contains("\"verdict\"") && !context.contains("pending")) break;
                assertTrue(System.currentTimeMillis() < deadline, "the log and its pairing never published: " + context);
                Thread.sleep(50);
            }
            assertFalse(context.contains("no node output was recorded"), "the pairing does not call withheld output silence: " + context);
            assertTrue(context.contains("withheld"), "the pairing states the withheld records: " + context);

            String coverage = f.ex.render("coverage", Map.of()).toJson();
            assertFalse(coverage.contains("never wrote audit output"), "coverage never says a node never wrote: " + coverage);
            assertTrue(coverage.contains("\"nodeLogsWithheld\":2"), "coverage states the withheld records: " + coverage);

            onEdt(() -> {
                LogTableModel model = (LogTableModel) ((javax.swing.JTable) field(field(f.frame, "tablePanel"), "table")).getModel();
                for (int row = 0; row < model.getRowCount(); row++) {
                    assertNull(model.getValueAt(row, LogTableModel.COL_NODE_LOGS), "row " + row + " shows a count it never read");
                }
            });
        }
    }
}
