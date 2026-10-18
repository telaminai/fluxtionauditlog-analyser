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
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** Integration v3 open findings: real-frame wrong-result witnesses, not passing regression claims. */
class IntegrationCReportAdmissionProbeTest {

    private static telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportAdmission admission(
            AsyncOpenInterleavingFrameTest.Frame f, Map<String, Object> params) throws Exception {
        var live = (telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportState) field(f.frame, "hostedReportState");
        // The request already passed the session's generation gate. Give it ownership of the report as observed
        // here, then exercise the real executor's later, EDT-side admission check after an interleaving.
        var owned = new telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportState() {
            public Path projectRoot() { return live.projectRoot(); }
            public boolean holds(String name) { return live.holds(name); }
            public String createdAt(String name) { return live.createdAt(name); }
            public boolean sessionWrote(long generation, String name, String revision) { return true; }
            public Path resolveOutput(String path) { return live.resolveOutput(path); }
        };
        var allowed = telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.reportInProject(params,
                new telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.OpenScope(live.projectRoot(), true, null, 7, owned));
        assertNotNull(allowed, "control: the write was admitted in the original project");
        return allowed;
    }

    private static telamin.fluxtion.audit.analyser.analyser.llm.ActionResult admittedWrite(
            AsyncOpenInterleavingFrameTest.Frame f, Map<String, Object> params,
            telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportAdmission admission) {
        var dispatcher = new telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher(false, null, () -> null, null, f.ex);
        return dispatcher.dispatchHosted(telamin.fluxtion.audit.analyser.analyser.llm.Json.write(
                Map.of("action", "report", "params", params)), 7, admission);
    }

    private Path openReviewProject(AsyncOpenInterleavingFrameTest.Frame f, String name) throws Exception {
        Path profile = tmp.resolve(name).resolve(".analyser/project.fluxtion-settings");
        java.nio.file.Files.createDirectories(profile.getParent());
        java.nio.file.Files.writeString(profile, "sourceRoot.0=.\n");
        assertTrue(f.ex.render("open", Map.of("project", profile.toString())).ok());
        openLog(f);
        return profile;
    }

    @Test
    void anAdmittedReportCannotMoveToAnotherProject() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openReviewProject(f, "DEMO-A");
            var allowed = admission(f, REPORT);
            openReviewProject(f, "DEMO-B");
            var answer = admittedWrite(f, REPORT, allowed);
            assertFalse(answer.ok(), "an admission for project A must not write into project B: " + answer.toMap());
            AppConfig config = (AppConfig) field(f.frame, "config");
            assertTrue(onEdtGet(() -> config.reports.isEmpty()), "project B stays untouched");
        }
    }

    @Test
    void anExternalRetitleRevokesAPreviouslyAdmittedOwnUpdate() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openReviewProject(f, "DEMO-A");
            assertTrue(f.ex.render("report", REPORT).ok());
            var replacement = Map.<String, Object>of("name", "finding", "title", "Agent replacement");
            var allowed = admission(f, replacement);
            assertTrue(admittedWrite(f, replacement, allowed).ok(), "control: an unchanged own report can be updated");
            allowed = admission(f, replacement);
            assertTrue(f.ex.render("report", Map.of("name", "finding", "title", "Person's title")).ok());
            var answer = admittedWrite(f, replacement, allowed);
            assertFalse(answer.ok(), "a title edit preserving createdAt must revoke the old admission: " + answer.toMap());
            AppConfig config = (AppConfig) field(f.frame, "config");
            assertEquals("Person's title", onEdtGet(() -> config.reports.getFirst().title()));
        }
    }

    @Test
    void anAdmittedFindingCannotFollowAChangedExchangeDirectory() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            Path profile = openReviewProject(f, "DEMO-A");
            Path inside = java.nio.file.Files.createDirectories(profile.getParent().getParent().resolve("reports"));
            Path outside = java.nio.file.Files.createDirectories(tmp.resolve("DEMO-outside"));
            AppConfig config = (AppConfig) field(f.frame, "config");
            onEdt(() -> { config.assistantExports = true; config.assistantExportDir = inside.toString(); });
            Map<String, Object> finding = Map.of("recordIndex", 0, "path", "finding.pdf", "topology", false);
            var allowed = admission(f, finding);
            onEdt(() -> config.assistantExportDir = outside.toString());
            var answer = admittedWrite(f, finding, allowed);
            assertFalse(answer.ok(), "an in-project admission must not follow a changed exchange directory: " + answer.toMap());
            assertFalse(java.nio.file.Files.exists(outside.resolve("finding.pdf")), "no unapproved outside write");
        }
    }

    @TempDir Path tmp;
    private static final Map<String, Object> REPORT = Map.of("name", "finding",
            "sections", List.of(Map.of("kind", "narrative", "text", "DEMO findings.")));

    private void openLog(Frame f) throws Exception {
        Path log = java.nio.file.Files.writeString(tmp.resolve("demo.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        awaitLoaded(f.ex);
    }

    private static <T> T onEdtGet(java.util.concurrent.Callable<T> c) throws Exception {
        var task = new java.util.concurrent.FutureTask<T>(c);
        javax.swing.SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
