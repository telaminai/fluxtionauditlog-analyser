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

            onEdt(() -> poll(f.frame));   // M44.5: the scan it asks for reports on the next EDT turn
            onEdt(() -> {
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

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            // re-review optional 1: a report is selected FIRST, so the tab read below can fail — it read an empty tab
            Path log = emptyFollowedWithAReport(f);
            onEdt(() -> assertTrue(reportsTab(f.frame).contains(EMPTY),
                    "control: the empty file is reported, on the tab this test goes on to read"));

            Files.writeString(log, "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n",
                    java.nio.file.StandardOpenOption.APPEND);
            onEdt(() -> poll(f.frame));   // M44.5: the scan it asks for reports on the next EDT turn
            onEdt(() -> {
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
            onEdt(() -> poll(f.frame));   // M44.5: the scan it asks for reports on the next EDT turn
            String after = onEdtGet(() -> {
                return pdf(render(f.ex, "report", with(REPORT, "path", "one-record.pdf")));
            });
            assertFalse(after.contains("LOG FINDINGS"), "a record arrived: the next export has nothing to say");
            assertFalse(after.contains("No records"), "and no longer calls the file empty");
        }
    }

    /**
     * Review item 2, Follow (H3). The tab is read straight after the poll: no `report`, `context` or other verb in
     * between, because any of those re-renders the tab by itself and would hide a tab that did not refresh.
     *
     * <p><b>Why the first step adds no record.</b> A poll that ADDS records already re-renders the tab — the append
     * re-applies the filter, and a report's evidence is live (D-I3) — so with a record appended the Follow refresh is
     * redundant and removing it changes nothing (the review's H3, run as stated, survived for exactly that reason).
     * The refresh is load-bearing when the findings change with NO new record: here a document still being written
     * appears, the file stops being empty (V2), and only that refresh can tell the tab.
     */
    @Test
    void theTabIsCurrentAfterAFollowPoll_withNoVerbInBetween() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path log = emptyFollowedWithAReport(f);
            onEdt(() -> assertTrue(reportsTab(f.frame).contains(EMPTY), "control: the tab shows the empty log"));

            Files.writeString(log, "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n",
                    java.nio.file.StandardOpenOption.APPEND);   // no closing ---: pending, not a record
            int rows = onEdtGet(() -> ((telamin.fluxtion.audit.analyser.analyser.parse.LogStore) field(f.frame, "store")).size());
            onEdt(() -> poll(f.frame));   // M44.5: the scan it asks for reports on the next EDT turn
            onEdt(() -> {
                assertEquals(rows, ((telamin.fluxtion.audit.analyser.analyser.parse.LogStore) field(f.frame, "store"))
                        .size(), "precondition: this poll added no record, so nothing else re-renders the tab");
                assertFalse(reportsTab(f.frame).contains("No records"),
                        "H3: the findings changed with no new record, and the tab must say so: " + reportsTab(f.frame));
            });

            Files.writeString(log, "  nodeLogs:\n    - node: { value: 1}\n---\n", java.nio.file.StandardOpenOption.APPEND);
            onEdt(() -> poll(f.frame));   // M44.5: the scan it asks for reports on the next EDT turn
            onEdt(() -> {
                assertFalse(reportsTab(f.frame).contains("No records"), "and once the record lands, still current");
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
            while (!onEdtGet(() -> findingsOf(f.frame) != null && findingsOf(f.frame).findings().stream()
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

    // ---- targeted re-review of PR #40: V2 at both call sites that word the empty-log finding ------------------------

    private static final String ZERO_MARKER = "---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 0\n---\n";

    /** What a cold open of these bytes says first — the reference V2 holds Follow to. */
    private static String coldOpenFirstWarning(Path log) throws java.io.IOException {
        var cold = new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(Files.readString(log));
        return telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.of(cold.index(), cold::rawText,
                cold.sourceDiagnostics(), cold.completenessDiagnostics(), cold.completenessIsNote(),
                cold.pendingFrameText(), cold.streamEnd()).firstWarning().orElseThrow().message();
    }

    private static String frameFirstWarning(MainFrame frame) {
        return findingsOf(frame)
                .firstWarning().orElseThrow(() -> new AssertionError("the frame reports nothing")).message();
    }

    /**
     * R1 (X4). An empty unmarked file is followed; a marker declaring zero arrives and the file stays empty, so the state
     * moves UNKNOWN → COMPLETE and the findings are recomputed through the FOLLOW call site. V2: what Follow says must
     * be what a cold open of the same bytes says — and that must be the ended sentence, so the two cannot merely agree
     * on the wrong one.
     */
    @Test
    void aFollowedFileThatGainsAZeroMarkerSaysWhatAColdOpenSays() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path log = emptyFollowedWithAReport(f);
            onEdt(() -> assertTrue(frameFirstWarning(f.frame).startsWith(
                    telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.EMPTY_FILE),
                    "control: before the marker, the file may still be written"));

            Files.writeString(log, ZERO_MARKER, java.nio.file.StandardOpenOption.APPEND);
            String cold = coldOpenFirstWarning(log);
            assertTrue(cold.startsWith(telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.EMPTY_FILE_ENDED),
                    "precondition: a cold open of these bytes gives the ended sentence: " + cold);
            onEdt(() -> poll(f.frame));   // M44.5: the scan it asks for reports on the next EDT turn
            onEdt(() -> {
                String tab = reportsTab(f.frame);            // read before any verb
                String tip = String.valueOf(status(f.frame).getToolTipText());
                assertAll("V2 at the Follow call site",
                        () -> assertEquals(cold, frameFirstWarning(f.frame),
                                "V2: Follow says what a cold open of the same bytes says"),
                        () -> assertTrue(tip.contains(
                                        telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.EMPTY_FILE_ENDED),
                                "the status tooltip carries the ended sentence: " + tip),
                        () -> assertTrue(tab.contains(
                                        telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.EMPTY_FILE_ENDED),
                                "the Reports tab carries it: " + tab));
            });
        }
    }

    /** The COLD-OPEN call site, which had the same gap: a marked-empty file opened is worded from its marker. */
    @Test
    void aColdOpenOfAFileWhoseMarkerSaysItEndedSaysSo() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.writeString(tmp.resolve("ended.yml"), ZERO_MARKER);
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            String cold = coldOpenFirstWarning(log);
            onEdt(() -> {
                assertEquals(cold, frameFirstWarning(f.frame), "the frame's cold open words it as the store does");
                assertTrue(String.valueOf(status(f.frame).getToolTipText()).contains(
                                telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.EMPTY_FILE_ENDED),
                        "and the tooltip says the marker ended it: " + status(f.frame).getToolTipText());
            });
        }
    }

    /**
     * R2 (X5): the frame's LOAD site for a set of one. For every single-file store {@code emptyLogClaim()} IS
     * {@code streamEnd()}, so only a rolled set tells the two apart — and only through the frame proves the load site
     * passes the right one. A report is selected over an empty file first; the set is opened; the test waits on the
     * frame's fields, never a verb (`awaitLoaded` calls `context`, which re-renders the tab), then reads.
     */
    @Test
    void aOneMemberSetOpenedInTheFrameIsWordedAsItsFile() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path empty = Files.writeString(tmp.resolve("empty.yml"), "");
        Path only = Files.writeString(tmp.resolve("run.1.yaml"), ZERO_MARKER);
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", empty.toString())).ok());
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            Object before = onEdtGet(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                render(f.ex, "report", REPORT);   // builds the report AND selects it on the tab
                return findingsOf(f.frame);
            });

            assertTrue(f.ex.render("open", Map.of("logs", List.of(only.toString()))).ok(), "a set of one opens");
            long deadline = System.currentTimeMillis() + 20_000;
            while (!onEdtGet(() -> field(f.frame, "store")
                    instanceof telamin.fluxtion.audit.analyser.analyser.parse.RolledLogStore
                    && findingsOf(f.frame) != null && findingsOf(f.frame) != before)) {
                assertTrue(System.currentTimeMillis() < deadline, "the set's findings never published");
                Thread.sleep(25);
            }
            onEdt(() -> {
                String tab = reportsTab(f.frame);
                String tip = String.valueOf(status(f.frame).getToolTipText());
                String first = frameFirstWarning(f.frame);
                String ended = telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.EMPTY_FILE_ENDED;
                assertAll("R2: the load site words a set of one from its file",
                        () -> assertTrue(first.startsWith(ended),
                                "the frame's first warning is the file's own ended sentence: " + first),
                        () -> assertTrue(tip.contains(ended), "the status tooltip carries it: " + tip),
                        () -> assertTrue(tab.contains(ended), "the Reports tab carries it: " + tab));
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

    // ---- M44.5: the witnesses of 2026-09-27, kept as regression checks -----------------------------------------------

    private static String rec(long logTime) {
        return "eventLogRecord:\n  logTime: " + logTime + "\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n";
    }

    /** Opens {@code log} for the socket, follows it with the timer stopped, and lets the open's scan report. */
    private static void followWithManualPolls(AsyncOpenInterleavingFrameTest.Frame f, Map<String, Object> open)
            throws Exception {
        assertTrue(f.ex.render("open", open).ok(), "the fixture opens: " + open);
        AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
        assertTrue(f.ex.render("open", Map.of("follow", true)).ok(), "and is followed");
        onEdt(() -> ((Timer) field(f.frame, "followTimer")).stop());   // the test drives each poll
        onEdt(() -> { });                                                // the open's scan has reported
    }

    /**
     * W1: a record appended under Follow that runs BACKWARDS in time was never reported — the time order was validated
     * once, at load, by the loader. It is logEvidence's now, re-derived whenever the content moves.
     */
    @Test
    void aTimeOrderViolationAppendedUnderFollowIsReported() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.writeString(tmp.resolve("ordered.yaml"), "---\n" + rec(1000) + rec(2000));
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            followWithManualPolls(f, Map.of("log", log.toString()));
            onEdt(() -> {
                assertNull(find(render(f.ex, "context", Map.of()), "timeOrder"), "control: the file is ordered");
                assertFalse(status(f.frame).getText().contains("time-order"), "control: " + status(f.frame).getText());
            });

            Files.writeString(log, rec(500), java.nio.file.StandardOpenOption.APPEND);
            onEdt(() -> poll(f.frame));
            onEdt(() -> assertAll("W1: the appended violation is reported where a load's would be",
                    () -> assertNotNull(find(render(f.ex, "context", Map.of()), "timeOrder"), "context.timeOrder"),
                    () -> assertTrue(status(f.frame).getText().contains("time-order violations (1)"),
                            "the Follow status line: " + status(f.frame).getText())));
        }
    }

    /**
     * W2: the Follow status line was a second assembly, and it dropped the provenance and the time-order warning that
     * the load line carried. One composer now; Follow changes the head of the line, never the facts on it.
     */
    @Test
    void theFollowLineKeepsTheProvenanceAndTheOrderWarning() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.writeString(tmp.resolve("disordered.yaml"), "---\n" + rec(2000) + rec(1000));
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            followWithManualPolls(f, Map.of("log", log.toString(), "provenance", "DEMO"));
            onEdt(() -> {
                String line = status(f.frame).getText();
                assertAll("W2: what the load line says, the Follow line says: " + line,
                        () -> assertTrue(line.startsWith("Following DEMO  (disordered.yaml)"), "the provenance"),
                        () -> assertTrue(line.contains("time-order violations (1)"), "the order warning"));
            });
            Files.writeString(log, rec(3000), java.nio.file.StandardOpenOption.APPEND);
            onEdt(() -> poll(f.frame));
            onEdt(() -> {
                String line = status(f.frame).getText();
                assertAll("and after an append: " + line,
                        () -> assertTrue(line.startsWith("Following DEMO  (disordered.yaml) · 3 records"), "the count"),
                        () -> assertTrue(line.contains("time-order violations (1)"), "the order warning"));
            });
        }
    }

    /**
     * M44.5 acceptance 4: a project environment supplies the provenance, and the session's copy is the only copy. It
     * is resolved before the log is reported open, so `context`, the snapshot and the Follow line state the same thing.
     */
    @Test
    void anEnvironmentsProvenanceIsTheSessionsAndEverySurfaceStatesIt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path project = Files.createDirectories(tmp.resolve("project"));
        Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(project);
        var seed = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        seed.environments.add(new telamin.fluxtion.audit.analyser.analyser.config.Environment("prod", "DEMO", "logs/prod"));
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, seed,
                new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
        Path log = Files.writeString(Files.createDirectories(project.resolve("logs/prod")).resolve("run.yaml"),
                "---\n" + rec(1000));
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("project", profile.toString())).ok(), "the project opens");
            followWithManualPolls(f, Map.of("log", log.toString()));
            onEdt(() -> {
                var snap = ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session")).snapshot();
                Map<String, Object> ctx = render(f.ex, "context", Map.of());
                assertAll("one provenance, from the environment, everywhere",
                        () -> assertEquals("DEMO", snap.provenance(), "the session's copy"),
                        () -> assertTrue(String.valueOf(snap.provenanceSource()).contains("prod"),
                                "and who supplied it: " + snap.provenanceSource()),
                        () -> assertEquals(snap.provenance(), find(ctx, "provenance"), "context"),
                        () -> assertEquals(snap.provenanceSource(), find(ctx, "provenanceSource"), "context's source"),
                        () -> assertTrue(status(f.frame).getText().startsWith("Following DEMO  (run.yaml)"),
                                "the Follow line: " + status(f.frame).getText()));
            });
        }
    }

    /**
     * M44.5, found while building it: between a poll and the scan it asks for, the line counted the NEW rows beside the
     * PREVIOUS revision's findings — "1 records … ⚠ empty log". The snapshot says a scan is outstanding, and the line
     * waits for it: read in the same EDT turn as the poll, it is still the previous revision's whole line.
     */
    @Test
    void theLineNeverCountsNewRowsBesideTheOldFindings() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path log = emptyFollowedWithAReport(f);
            String before = onEdtGet(() -> status(f.frame).getText());
            assertTrue(before.contains("0 records") && before.contains("empty log"), "control: " + before);
            appendRecord(log);
            onEdt(() -> {
                poll(f.frame);
                String during = status(f.frame).getText();
                assertFalse(during.contains("1 records") && during.contains("empty log"),
                        "one revision per line, never two: " + during);
                // not only the count: the range and the pending note are the previous revision's too, so the whole
                // line is unchanged until the session has the new revision and its evidence
                assertEquals(before, during, "the previous revision's whole line");
            });
            onEdt(() -> {
                String after = status(f.frame).getText();
                assertTrue(after.contains("1 records") && !after.contains("empty log"), "and then the new one: " + after);
            });
        }
    }

    /** M44.5: the log's findings are the session's (logEvidence), published in the snapshot — null until the scan lands. */
    static telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics findingsOf(MainFrame frame) {
        return ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(frame, "session")).snapshot().producerFindings();
    }
}
