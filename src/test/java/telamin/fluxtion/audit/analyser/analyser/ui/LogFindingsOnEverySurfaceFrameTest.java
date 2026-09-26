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
                // review item 7: V2 on EVERY surface the first test covers, not two of them
                assertFalse(reportsTab(f.frame).contains("No records"), "the Reports tab, read before any verb");
                Map<String, Object> report = render(f.ex, "report", REPORT);
                assertAll("V2: a document still being written is not an empty file, anywhere",
                        () -> assertFalse(status(f.frame).getText().contains("empty log"),
                                "the status bar: " + status(f.frame).getText()),
                        () -> assertFalse(String.valueOf(status(f.frame).getToolTipText()).contains("No records"),
                                "the status tooltip: " + status(f.frame).getToolTipText()),
                        () -> assertFalse(producer(render(f.ex, "context", Map.of())).contains("No records"),
                                "context's producer"),
                        () -> assertFalse(producer(report).contains("No records"), "the report reply: " + report));
            });
        }
    }

    // ---- review items 1 and 2: the PDF through the frame, and the tab read with no verb in between ------------------

    /** Opens an empty file, follows it with the timer stopped (the test drives each poll), and makes a report. */
    private Path emptyFollowedWithAReport(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        Path log = Files.writeString(tmp.resolve("empty.yml"), "");
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "an empty file opens");
        AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
        assertTrue(f.ex.render("open", Map.of("follow", true)).ok(), "and is followed");
        onEdt(() -> {
            ((Timer) field(f.frame, "followTimer")).stop();
            f.frame.setSize(1300, 850);
            f.frame.setVisible(true);
            render(f.ex, "report", REPORT);   // builds the report AND selects it on the tab
        });
        return log;
    }

    private static void appendRecord(Path log) throws java.io.IOException {
        Files.writeString(log, "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n"
                + "    - node: { value: 1}\n---\n", java.nio.file.StandardOpenOption.APPEND);
    }

    /** The text of a PDF this analyser wrote: uncompressed {@code (…) Tj} runs, so the bytes are the text. */
    private static String pdf(Map<String, Object> reply) throws java.io.IOException {
        Object path = find(reply, "path");
        assertNotNull(path, "the report was written: " + reply);
        return new String(Files.readAllBytes(Path.of(path.toString())), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    /**
     * Review item 1 (H1) — the PDF the FRAME exports, through {@code report {path}}, which is
     * {@code MainFrame.renderReportPdf}: the same path the Reports tab's Export uses. {@code ReportLogFindingsTest}
     * proves the renderer draws findings it is given; only this proves the frame gives them.
     */
    @Test
    void theExportedPdfCarriesTheLogsFindings_andLosesThemWhenARecordArrives() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path out = Files.createDirectories(tmp.resolve("exports"));
            onEdt(() -> {
                var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
                config.assistantExports = true;
                config.assistantExportDir = out.toString();
            });
            Path log = emptyFollowedWithAReport(f);

            String before = onEdtGet(() -> pdf(render(f.ex, "report", with(REPORT, "path", "empty.pdf"))));
            assertTrue(before.contains("LOG FINDINGS"), "H1: the exported PDF states what the file itself shows");
            assertTrue(before.contains("No records in this file yet."),
                    "H1: the exported PDF carries the empty-log finding");

            appendRecord(log);
            String after = onEdtGet(() -> {
                poll(f.frame);
                return pdf(render(f.ex, "report", with(REPORT, "path", "one-record.pdf")));
            });
            assertFalse(after.contains("LOG FINDINGS"), "a record arrived: the next export has nothing to say");
            assertFalse(after.contains("No records"), "and no longer calls the file empty");
        }
    }

    /**
     * Review item 2, Follow (H3). The tab is read straight after the poll: no `report`, `context` or other verb in
     * between, because any of those re-renders the tab by itself and would hide a tab that did not refresh.
     */
    @Test
    void theTabIsCurrentAfterAFollowPoll_withNoVerbInBetween() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path log = emptyFollowedWithAReport(f);
            onEdt(() -> assertTrue(reportsTab(f.frame).contains(EMPTY), "control: the tab shows the empty log"));

            appendRecord(log);
            onEdt(() -> {
                poll(f.frame);
                assertFalse(reportsTab(f.frame).contains("No records"),
                        "H3: after the Follow poll, the tab must not still call the file empty: " + reportsTab(f.frame));
            });
        }
    }

    /**
     * Review item 2, load (H2). With a report selected over an empty file, a second log with DIFFERENT findings is
     * opened — c29's, a complete file holding a document with no record key — and the tab is read as soon as the load
     * has published, waiting on the frame's fields rather than a verb.
     */
    @Test
    void theTabIsCurrentAfterASecondLogLoads_withNoVerbInBetween() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            emptyFollowedWithAReport(f);
            onEdt(() -> assertTrue(reportsTab(f.frame).contains(EMPTY), "control: the tab shows the empty log"));

            Path second = tmp.resolve("no-record-key.yaml");
            try (var in = getClass().getResourceAsStream("/conformance/c29-no-record-key.yaml")) {
                Files.write(second, in.readAllBytes());
            }
            assertTrue(f.ex.render("open", Map.of("log", second.toString())).ok(), "the second log opens");
            long deadline = System.currentTimeMillis() + 20_000;
            while (!onEdtGet(() -> ((telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics)
                    field(f.frame, "producerDiagnostics")).findings().stream()
                    .anyMatch(x -> x.kind().name().equals("NO_RECORD_KEY")))) {
                assertTrue(System.currentTimeMillis() < deadline, "the second log's findings never published");
                Thread.sleep(25);
            }
            onEdt(() -> {
                String tab = reportsTab(f.frame);
                assertAll("H2: the tab states the log that is open now",
                        () -> assertTrue(tab.contains("Record 2"),
                                "the new log's finding — its record with no record key — is on the tab: " + tab),
                        () -> assertFalse(tab.contains("No records in this file"),
                                "and the old log's empty-file finding is gone: " + tab));
            });
        }
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> m = new java.util.LinkedHashMap<>(base);
        m.put(key, value);
        return m;
    }

    private static <T> T onEdtGet(java.util.concurrent.Callable<T> c) throws Exception {
        var ref = new java.util.concurrent.atomic.AtomicReference<T>();
        var err = new java.util.concurrent.atomic.AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try { ref.set(c.call()); } catch (Exception e) { err.set(e); }
        });
        if (err.get() != null) throw err.get();
        return ref.get();
    }

    private static void poll(MainFrame frame) {
        try {
            var method = MainFrame.class.getDeclaredMethod("pollFollow");
            method.setAccessible(true);
            method.invoke(frame);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
