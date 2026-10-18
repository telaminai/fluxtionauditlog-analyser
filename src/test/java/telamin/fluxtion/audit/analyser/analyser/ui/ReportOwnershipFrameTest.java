package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.llm.*;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.*;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** Real report/project/export effects, real generated decisions; only the CLI is simulated. DEMO only. */
class ReportOwnershipFrameTest {
    @TempDir Path tmp;
    static final Map<String, Object> REPORT = Map.of("name", "finding",
            "sections", List.of(Map.of("kind", "narrative", "text", "DEMO findings.")));

    private Frame frame() throws Exception {
        var f = new Frame(tmp);
        onEdt(() -> {
            try {
                var perform = MainFrame.class.getDeclaredMethod("performSessionEffect", SessionEffects.class);
                perform.setAccessible(true);
                var driver = ReportTestSession.create(effect -> (SessionEvents.Result) perform.invoke(f.frame, effect));
                var slot = MainFrame.class.getDeclaredField("session");
                slot.setAccessible(true);
                slot.set(f.frame, driver);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        return f;
    }

    private SessionDriver driver(Frame f) { return (SessionDriver) field(f.frame, "session"); }
    private long generation(Frame f) { return driver(f).snapshot().localAgent().admittedGeneration(); }
    private AppConfig config(Frame f) { return (AppConfig) field(f.frame, "config"); }
    private McpTools.ReportState live(Frame f) { return (McpTools.ReportState) field(f.frame, "hostedReportState"); }
    private McpTools.ReportAdmission admission(Frame f, Map<String, Object> params) {
        var state = live(f);
        var allowed = McpTools.reportInProject(params, new McpTools.OpenScope(state.projectRoot(), true,
                null, generation(f), state));
        assertNotNull(allowed, "control: the write is admitted in the original project");
        return allowed;
    }

    private ActionResult apply(Frame f, Map<String, Object> params, McpTools.ReportAdmission a) throws Exception {
        var dispatcher = new ActionDispatcher(false, null, () -> null, null, f.ex);
        var answer = dispatcher.dispatchHosted(Json.write(Map.of("action", "report", "params", params)), generation(f), a);
        // The HTTP adapter forwards this exact receipt; it never reads the report back after dispatch.
        var receipt = answer.reportReceipt();
        if (receipt != null) onEdt(() -> driver(f).post(SessionEvents.HostedReportWritten.of(
                receipt.generation(), receipt.ownerKey(), receipt.revision())));
        return answer;
    }

    private Path project(Frame f, String name) throws Exception {
        Path profile = tmp.resolve(name).resolve(".analyser/project.fluxtion-settings");
        Files.createDirectories(profile.getParent());
        if (!Files.exists(profile)) Files.writeString(profile, "sourceRoot.0=.\n");
        assertTrue(f.ex.render("open", Map.of("project", profile.toString())).ok());
        Path log = Files.writeString(tmp.resolve("DEMO.yml"), "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n"
                + "  nodeLogs:\n    - node: { value: 1}\n---\n");
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        awaitLoaded(f.ex);
        return profile.getParent().getParent();
    }

    private Map<String, Object> finding() { return Map.of("recordIndex", 0, "path", "finding.pdf", "topology", false); }
    private void exchange(Frame f, Path dir) throws Exception {
        Files.createDirectories(dir);
        onEdt(() -> { config(f).assistantExports = true; config(f).assistantExportDir = dir.toString(); });
    }

    @Test void anAdmittedReportCannotMoveToAnotherProject() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            project(f, "DEMO-A");
            var a = admission(f, REPORT);
            project(f, "DEMO-B");
            var result = apply(f, REPORT, a);
            assertFalse(result.ok(), "an admission for project A must not write into project B: " + result.toMap());
            assertTrue(readEdt(() -> config(f).reports.isEmpty()), "project B stays untouched");
        }
    }

    @Test void anExternalRetitleRevokesAPreviouslyAdmittedOwnUpdate() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            project(f, "DEMO-A");
            assertTrue(apply(f, REPORT, admission(f, REPORT)).ok(), "control: a new report is written");
            var edit = Map.<String, Object>of("name", "finding", "title", "Agent replacement");
            assertTrue(apply(f, edit, admission(f, edit)).ok(), "control: an unchanged own report can be updated");
            var a = admission(f, edit);
            String created = readEdt(() -> config(f).reports.getFirst().createdAt());
            assertTrue(f.ex.render("report", Map.of("name", "finding", "title", "Person's title")).ok());
            assertEquals(created, readEdt(() -> config(f).reports.getFirst().createdAt()), "the old timestamp cannot detect this");
            var result = apply(f, edit, a);
            assertFalse(result.ok(), "a title edit preserving createdAt must revoke the old admission: " + result.toMap());
            assertEquals("Person's title", readEdt(() -> config(f).reports.getFirst().title()));
            assertNull(McpTools.reportInProject(edit, new McpTools.OpenScope(live(f).projectRoot(), true, null,
                    generation(f), live(f))), "a fresh classification asks too: the session did not write this revision");
        }
    }

    @Test void anAdmittedFindingCannotFollowAChangedExchangeDirectory() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            Path root = project(f, "DEMO-A");
            exchange(f, root.resolve("reports"));
            var a = admission(f, finding());
            Path outside = tmp.resolve("DEMO-outside");
            exchange(f, outside);
            var result = apply(f, finding(), a);
            assertFalse(result.ok(), "an in-project admission must not follow a changed exchange directory: " + result.toMap());
            assertFalse(Files.exists(outside.resolve("finding.pdf")), "no unapproved outside write");
        }
    }

    @Test void aProjectLifetimeCannotBeReusedAfterSwitchingBack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            project(f, "DEMO-A");
            var a = admission(f, REPORT);
            project(f, "DEMO-B"); project(f, "DEMO-A");
            assertFalse(apply(f, REPORT, a).ok(), "returning to the same path does not revive an old admission");
            assertTrue(apply(f, REPORT, admission(f, REPORT)).ok(), "a fresh admission in this lifetime runs");
        }
    }

    @Test void aValidFindingWritesButAnAppearedFileIsNeverOverwritten() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            Path root = project(f, "DEMO-A");
            exchange(f, root.resolve("reports"));
            var a = admission(f, finding());
            assertTrue(apply(f, finding(), a).ok(), "control: valid in-project finding is exported");
            Path out = root.resolve("reports/finding.pdf");
            assertTrue(Files.size(out) > 100, "the real PDF was written");
            byte[] original = Files.readAllBytes(out);
            assertFalse(apply(f, finding(), a).ok(), "a second use cannot overwrite the file");
            assertArrayEquals(original, Files.readAllBytes(out));
        }
    }

    @Test void anAdmittedOutputCannotBecomeAnOutsideSymlink() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            Path root = project(f, "DEMO-A");
            Path reports = root.resolve("reports");
            exchange(f, reports);
            var a = admission(f, finding());
            Files.delete(reports);
            Path outside = Files.createDirectories(tmp.resolve("DEMO-outside"));
            Files.createSymbolicLink(reports, outside);
            assertFalse(apply(f, finding(), a).ok());
            assertFalse(Files.exists(outside.resolve("finding.pdf")), "no write through a substituted link");
        }
    }

    @Test void aReceiptDescribesTheWriteEvenWhenThePersonEditsBeforeItIsRecorded() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = frame()) {
            project(f, "DEMO-A");
            var a = admission(f, REPORT);
            var dispatcher = new ActionDispatcher(false, null, () -> null, null, f.ex);
            var result = dispatcher.dispatchHosted(Json.write(Map.of("action", "report", "params", REPORT)), generation(f), a);
            assertTrue(result.ok());
            var receipt = result.reportReceipt();
            assertNotNull(receipt, "the actual write carries a receipt");
            assertFalse(result.toJson().contains(receipt.revision()), "receipt metadata never changes the external API");
            assertTrue(f.ex.render("report", Map.of("name", "finding", "notes", "Person's later note")).ok());
            onEdt(() -> driver(f).post(SessionEvents.HostedReportWritten.of(receipt.generation(), receipt.ownerKey(), receipt.revision())));
            assertNull(McpTools.reportInProject(Map.of("name", "finding", "title", "Agent title"),
                    new McpTools.OpenScope(live(f).projectRoot(), true, null, generation(f), live(f))),
                    "a later human revision must not be attributed to the agent");
        }
    }

    private static <T> T readEdt(java.util.concurrent.Callable<T> read) throws Exception {
        var task = new java.util.concurrent.FutureTask<>(read); SwingUtilities.invokeAndWait(task); return task.get();
    }
}
