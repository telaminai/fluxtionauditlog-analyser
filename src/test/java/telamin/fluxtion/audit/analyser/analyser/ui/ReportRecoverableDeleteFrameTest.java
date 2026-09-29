package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * PR #33 review (owner, 2026-09-27): the assistant's {@code report {name, delete: true}} is recoverable, through the
 * real frame's verb — the reply says how to undo it, {@code restore: true} lists it, and {@code restore: "<name>"}
 * brings it back exactly; a restore onto a taken name is refused.
 */
class ReportRecoverableDeleteFrameTest {

    @TempDir Path tmp;

    private static final Map<String, Object> REPORT = Map.of("name", "finding",
            "sections", List.of(Map.of("kind", "narrative", "text", "What we found.")));

    @Test
    void theAssistantsDeleteCanBeUndone() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = java.nio.file.Files.writeString(tmp.resolve("sample.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "control: a log opens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);             // the report verb needs a log
            var first = f.ex.render("report", REPORT);
            assertTrue(first.ok(), "control: the report is built: " + first.toMap());
            AppConfig config = (AppConfig) onEdtGet(() -> field(f.frame, "config"));
            var built = onEdtGet(() -> config.reports.stream().filter(r -> r.name().equals("finding")).findFirst().orElseThrow());

            var deleted = f.ex.render("report", Map.of("name", "finding", "delete", true));
            assertTrue(deleted.ok(), "the delete succeeds: " + deleted);
            assertTrue(String.valueOf(deleted.toMap()).contains("restore"), "the reply says how to undo it: " + deleted.toMap());
            assertTrue(onEdtGet(() -> config.reports.isEmpty()), "the report left the live list");

            var listed = f.ex.render("report", Map.of("restore", true));
            assertTrue(String.valueOf(listed.toMap()).contains("finding"), "restore: true lists it: " + listed.toMap());

            var restored = f.ex.render("report", Map.of("restore", "finding"));
            assertTrue(restored.ok(), "restore succeeds: " + restored);
            assertEquals(built, onEdtGet(() -> config.reports.get(0)), "the restored report is exactly the one deleted");

            assertTrue(f.ex.render("report", Map.of("name", "finding", "delete", true)).ok());
            assertTrue(f.ex.render("report", REPORT).ok(), "a new report takes the name meanwhile");
            var refused = f.ex.render("report", Map.of("restore", "finding"));
            assertFalse(refused.ok(), "restoring onto a taken name is refused: " + refused);
            assertEquals(1, (int) onEdtGet(() -> config.reports.size()), "the live report was not replaced");
        }
    }

    /**
     * PR #51 review, #46 through the REAL verb. AbsentSectionsMeanUnchangedTest pins ReportVerb.Parsed.onto(), and
     * MainFrame going back to {@code parsed.spec()} — the original defect — left it and every other test green.
     * This goes through the frame's report verb, and also checks what the reply says the report was written against
     * once a different log is open: the carried fingerprint, not today's log.
     */
    @Test
    void aRetitleThroughTheVerbKeepsTheSectionsAndSaysWhichLogTheyWereWrittenAgainst() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path first = java.nio.file.Files.writeString(tmp.resolve("first.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        Path second = java.nio.file.Files.writeString(tmp.resolve("second.yml"),
                "---\neventLogRecord:\n  logTime: 5000\n  event: Tock\n  nodeLogs:\n    - node: { value: 2}\n---\n"
                        + "eventLogRecord:\n  logTime: 6000\n  event: Tock\n  nodeLogs:\n    - node: { value: 3}\n---\n");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", first.toString())).ok());
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            var built = f.ex.render("report", REPORT);
            assertTrue(built.ok(), "control: " + built.toMap());
            String writtenAgainst = String.valueOf(built.payload().get("writtenAgainst"));

            var retitled = f.ex.render("report", Map.of("name", "finding", "title", "Retitled"));
            assertTrue(retitled.ok(), "the retitle is applied: " + retitled.toMap());
            AppConfig config = (AppConfig) onEdtGet(() -> field(f.frame, "config"));
            var stored = onEdtGet(() -> config.reports.stream().filter(r -> r.name().equals("finding")).findFirst().orElseThrow());
            assertEquals("Retitled", stored.title());
            assertEquals(1, stored.sections().size(), "#46: a retitle through the verb keeps the sections");

            assertTrue(f.ex.render("open", Map.of("log", second.toString())).ok());
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            var again = f.ex.render("report", Map.of("name", "finding", "title", "Retitled again"));
            assertTrue(again.ok(), "control: " + again.toMap());
            assertEquals(writtenAgainst, String.valueOf(again.payload().get("writtenAgainst")),
                    "the sections were kept, so they are still written against the FIRST log — the reply must not "
                            + "name the log that happens to be open now: " + again.toMap());
        }
    }

    private void openLog(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        Path log = java.nio.file.Files.writeString(tmp.resolve("demo.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "fixture log opens");
        AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
    }

    @Test
    void aReportNamedTrueIsRestoredRatherThanListed() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f);
            assertTrue(f.ex.render("report", Map.of("name", "true", "sections", REPORT.get("sections"))).ok(), "fixture report created");
            assertTrue(f.ex.render("report", Map.of("name", "true", "delete", true)).ok(), "fixture report deleted");
            AppConfig c = (AppConfig) onEdtGet(() -> field(f.frame, "config"));
            assertTrue(f.ex.render("report", Map.of("restore", true)).ok(), "boolean true lists");
            assertTrue(onEdtGet(() -> c.reports.isEmpty()), "listing changes nothing");
            assertTrue(f.ex.render("report", Map.of("restore", "true")).ok(), "string true restores its report");
            assertEquals(List.of("true"), onEdtGet(() -> c.reports.stream().map(r -> r.name()).toList()),
                    "a report named true must be restored, not treated as a listing request");
        }
    }

    @Test
    void restoreRefusesMixedOperationsWithoutChangingAnything() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f);
            assertTrue(f.ex.render("report", REPORT).ok(), "fixture report created");
            assertTrue(f.ex.render("report", Map.of("name", "finding", "delete", true)).ok(), "fixture report deleted");
            AppConfig c = (AppConfig) onEdtGet(() -> field(f.frame, "config"));
            var before = onEdtGet(() -> List.copyOf(c.deletedReports));
            for (var extra : Map.<String, Object>of("sections", REPORT.get("sections"), "path", "ignored.pdf",
                    "name", "other", "delete", true, "rename", "other", "unknown", true).entrySet()) {
                var result = f.ex.render("report", Map.of("restore", "finding", extra.getKey(), extra.getValue()));
                assertFalse(result.ok(), "restore must refuse the extra operation: " + extra.getKey());
                assertTrue(result.toMap().toString().contains("used alone"), "refusal explains exclusivity: " + result.toMap());
                assertTrue(onEdtGet(() -> c.reports.isEmpty()), "refused restore must not create a live report");
                assertEquals(before, onEdtGet(() -> List.copyOf(c.deletedReports)), "refused restore leaves the bin intact");
            }
            for (Object invalid : List.of(false, 42, " ")) {
                assertFalse(f.ex.render("report", Map.of("restore", invalid)).ok(), "invalid restore value is refused: " + invalid);
                assertTrue(onEdtGet(() -> c.reports.isEmpty()), "invalid restore leaves live reports unchanged: " + invalid);
                assertEquals(before, onEdtGet(() -> List.copyOf(c.deletedReports)), "invalid restore leaves the bin unchanged: " + invalid);
            }
        }
    }

    @Test
    void theCompactReportActionsDeleteAndRestore() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        var previous = javax.swing.UIManager.getLookAndFeel();
        try {
            onEdt(() -> com.formdev.flatlaf.FlatLightLaf.setup());
            try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
                f.dialogs.stop(); // this test answers actual modal dialogs itself
                openLog(f);
                assertTrue(f.ex.render("report", REPORT).ok(), "fixture report created");
                AppConfig c = (AppConfig) onEdtGet(() -> field(f.frame, "config"));
                ReportsPanel reports = (ReportsPanel) onEdtGet(() -> field(f.frame, "reportsPanel"));
                onEdt(() -> {
                    f.frame.setSize(1200, 800); f.frame.setVisible(true);
                    for (var item : components(f.frame)) if (item instanceof javax.swing.JTabbedPane tabs)
                        for (int i = 0; i < tabs.getTabCount(); i++)
                            if (tabs.getTitleAt(i).equals("Reports")) tabs.setSelectedIndex(i);
                    f.frame.validate();
                });
                awaitUi(reports::isShowing, "the Reports pane is shown");
                onEdt(() -> {
                    for (String label : List.of("Export PDF…", "More ▾")) {
                        var b = button(reports, label);
                        assertTrue(b.isShowing() && b.getVisibleRect().width == b.getWidth()
                                        && b.getVisibleRect().height == b.getHeight() && b.getHeight() > 0 && b.getWidth() > 0,
                                "every report action must be fully visible at the default size: " + label);
                    }
                });
                clickReportAction(reports, "deleteItem");
                click(dialog("Delete report"), "OK");
                awaitUi(() -> c.reports.isEmpty() && c.deletedReports.size() == 1, "delete reached the bin");
                clickReportAction(reports, "restoreItem");
                var restore = dialog("Restore deleted report");
                assertTrue(onEdtGet(() -> components(restore).stream().anyMatch(x -> x instanceof javax.swing.JComboBox<?> box
                        && "finding".equals(box.getSelectedItem()))), "the real dialog offers the deleted report");
                click(restore, "OK");
                awaitUi(() -> c.reports.size() == 1 && c.deletedReports.isEmpty(), "restore returned the report");
                assertFalse(onEdtGet(() -> ((javax.swing.JMenuItem)field(reports, "restoreItem")).isEnabled()),
                        "the empty bin cannot be clicked as if it held a deleted report");
            }
        } finally {
            onEdt(() -> { try { javax.swing.UIManager.setLookAndFeel(previous); } catch (Exception e) { throw new RuntimeException(e); } });
        }
    }

    @Test
    void aProjectExchangeCannotExportThroughANestedOutsideLink() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path root = java.nio.file.Files.createDirectories(tmp.resolve("project/.analyser")).getParent();
            Path exchange = java.nio.file.Files.createDirectory(root.resolve("exchange"));
            Path outside = java.nio.file.Files.createDirectory(tmp.resolve("outside"));
            java.nio.file.Files.createSymbolicLink(exchange.resolve("nested"), outside);
            onEdt(() -> {
                AppConfig c = (AppConfig) field(f.frame, "config");
                c.activeProjectPath = root.resolve(".analyser/project.fluxtion-settings").toString();
                c.projectExchangeDir = "exchange"; c.assistantExports = true;
                f.frame.setSize(1200, 800); f.frame.setVisible(true);
            });
            var inside = f.ex.render("screenshot", Map.of("path", "inside.png"));
            assertTrue(inside.ok(), "positive control: an inside screenshot succeeds: " + inside.toMap());
            assertTrue(java.nio.file.Files.isRegularFile(exchange.resolve("inside.png")),
                    "positive control: the screenshot was written inside the exchange directory");
            var result = f.ex.render("screenshot", Map.of("path", "nested/out.png"));
            assertFalse(result.ok(), "the actual screenshot action must refuse a nested outside link");
            assertFalse(java.nio.file.Files.exists(outside.resolve("out.png")), "no screenshot escaped the project");
        }
    }

    private static java.util.List<java.awt.Component> components(java.awt.Container parent) {
        var result = new java.util.ArrayList<java.awt.Component>();
        for (var c : parent.getComponents()) {
            result.add(c); if (c instanceof java.awt.Container child) result.addAll(components(child));
        }
        return result;
    }

    private static javax.swing.JButton button(java.awt.Container parent, String text) {
        return (javax.swing.JButton) components(parent).stream()
                .filter(c -> c instanceof javax.swing.JButton b && text.equals(b.getText())).findFirst().orElseThrow();
    }

    private static void click(java.awt.Container parent, String text) throws Exception {
        var b = onEdtGet(() -> button(parent, text));
        javax.swing.SwingUtilities.invokeLater(b::doClick); // real button listener and real nested modal loop
    }

    private static void clickReportAction(ReportsPanel reports, String fieldName) throws Exception {
        var item = onEdtGet(() -> (javax.swing.JMenuItem) field(reports, fieldName));
        javax.swing.SwingUtilities.invokeLater(item::doClick);
    }

    private static javax.swing.JDialog dialog(String title) throws Exception {
        awaitUi(() -> java.util.Arrays.stream(java.awt.Window.getWindows()).anyMatch(w -> w instanceof javax.swing.JDialog d
                && d.isShowing() && title.equals(d.getTitle())), "dialog is shown: " + title);
        return onEdtGet(() -> (javax.swing.JDialog) java.util.Arrays.stream(java.awt.Window.getWindows())
                .filter(w -> w instanceof javax.swing.JDialog d && d.isShowing() && title.equals(d.getTitle())).findFirst().orElseThrow());
    }

    private static void awaitUi(java.util.concurrent.Callable<Boolean> ready, String label) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            if (onEdtGet(ready)) return;
            Thread.sleep(20);
        }
        fail(label);
    }

    private static <T> T onEdtGet(java.util.concurrent.Callable<T> c) throws Exception {
        var ref = new java.util.concurrent.atomic.AtomicReference<T>();
        var err = new java.util.concurrent.atomic.AtomicReference<Exception>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try { ref.set(c.call()); } catch (Exception e) { err.set(e); }
        });
        if (err.get() != null) throw err.get();
        return ref.get();
    }
}
