package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.find;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * MA-0.5 and D-MA0c, in the frame — an empty file followed before its first record shows the finding on EVERY surface
 * that states the log's findings, and every one of them clears when a record arrives.
 *
 * <p>The surfaces: the status bar and its tooltip, {@code context}'s {@code producer}, the {@code report} verb's reply,
 * and the Reports tab. A unit test can prove each one reads {@code ProducerDiagnostics}; only the frame proves each one
 * reads the CURRENT one — a surface that captured the findings at open would keep saying "empty" after the first
 * record, which is the failure this exists to catch.
 */
class LogFindingsOnEverySurfaceFrameTest {

    @TempDir Path tmp;

    private static final String EMPTY = "No records in this file yet.";

    private static final Map<String, Object> REPORT = Map.of("name", "inv",
            "sections", List.of(Map.of("kind", "narrative", "text", "What we saw while it was empty.")));

    private static JLabel status(MainFrame frame) {
        return (JLabel) field(frame, "status");
    }

    /** Every piece of text the Reports tab is showing. */
    private static String reportsTab(MainFrame frame) {
        StringBuilder sb = new StringBuilder();
        collect((Component) field(frame, "reportsPanel"), sb);
        return sb.toString();
    }

    private static void collect(Component c, StringBuilder sb) {
        if (c instanceof JTextArea t) sb.append(t.getText()).append('\n');
        if (c instanceof JLabel l) sb.append(l.getText()).append('\n');
        if (c instanceof Container k) for (Component child : k.getComponents()) collect(child, sb);
    }

    private static String producer(Map<String, Object> reply) {
        Object p = find(reply, "producer");
        return p == null ? "" : String.valueOf(p);
    }

    @Test
    void anEmptyFollowedFileSaysSoEverywhere_andEverySurfaceClearsWhenARecordArrives() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.writeString(tmp.resolve("empty.yml"), "");

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "an empty file opens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            assertTrue(f.ex.render("open", Map.of("follow", true)).ok(), "and is followed");
            onEdt(() -> ((Timer) field(f.frame, "followTimer")).stop());   // the test drives each poll

            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                Map<String, Object> report = render(f.ex, "report", REPORT);
                assertAll("MA-0.5: the empty file is stated on every surface",
                        // the bar carries the finding's short label; the tooltip carries its sentence
                        () -> assertTrue(status(f.frame).getText().contains("empty log"),
                                "the status bar, on the line Follow starts with: " + status(f.frame).getText()),
                        () -> assertTrue(String.valueOf(status(f.frame).getToolTipText()).contains(EMPTY),
                                "the status tooltip: " + status(f.frame).getToolTipText()),
                        () -> assertTrue(producer(render(f.ex, "context", Map.of())).contains(EMPTY),
                                "context's producer"),
                        () -> assertTrue(producer(report).contains(EMPTY),
                                "D-MA0c: the report verb's reply: " + report),
                        () -> assertTrue(reportsTab(f.frame).contains("LOG FINDINGS")
                                        && reportsTab(f.frame).contains(EMPTY),
                                "D-MA0c: the Reports tab: " + reportsTab(f.frame)));
            });

            Files.writeString(log, "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n"
                    + "    - node: { value: 1}\n---\n", java.nio.file.StandardOpenOption.APPEND);

            onEdt(() -> {
                poll(f.frame);
                Map<String, Object> report = render(f.ex, "report", REPORT);
                assertAll("MA-0.5: a record arrived, and no surface still says the file is empty",
                        () -> assertFalse(status(f.frame).getText().contains("empty log"),
                                "the status bar: " + status(f.frame).getText()),
                        () -> assertFalse(String.valueOf(status(f.frame).getToolTipText()).contains("No records"),
                                "the status tooltip: " + status(f.frame).getToolTipText()),
                        () -> assertFalse(producer(render(f.ex, "context", Map.of())).contains("No records"),
                                "context's producer"),
                        () -> assertFalse(producer(report).contains("No records"),
                                "the report verb's reply: " + report),
                        () -> assertFalse(reportsTab(f.frame).contains("No records"),
                                "the Reports tab: " + reportsTab(f.frame)));
            });
        }
    }

    /**
     * V2 — Follow and a cold open may differ by exactly one pending document. A record being written has not arrived,
     * but a cold open of those bytes reads it as a record, so under Follow it must not be called an empty file either.
     */
    @Test
    void aRecordStillBeingWrittenIsNotAnEmptyFile() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.writeString(tmp.resolve("empty.yml"), "");

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            assertTrue(f.ex.render("open", Map.of("follow", true)).ok());
            onEdt(() -> ((Timer) field(f.frame, "followTimer")).stop());
            onEdt(() -> assertTrue(producer(render(f.ex, "context", Map.of())).contains(EMPTY),
                    "control: the empty file is reported"));

            Files.writeString(log, "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n",
                    java.nio.file.StandardOpenOption.APPEND);
            onEdt(() -> {
                poll(f.frame);
                assertFalse(producer(render(f.ex, "context", Map.of())).contains("No records"),
                        "a document still being written is not an empty file");
                assertFalse(reportsTab(f.frame).contains("No records"), "on the Reports tab either");
            });
        }
    }

    private static void poll(MainFrame frame) {
        try {
            var method = MainFrame.class.getDeclaredMethod("pollFollow");
            method.setAccessible(true);
            method.invoke(frame);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
