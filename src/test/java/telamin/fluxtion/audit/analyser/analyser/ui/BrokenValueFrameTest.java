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
}
