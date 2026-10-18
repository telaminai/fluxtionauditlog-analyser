package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.OpenScope;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportAdmission;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner, 2026-10-18: in FULL Claude Code, a {@code report} call that writes something NEW into the ACTIVE project — a
 * new report, a section added to (or another update of) a report this session wrote, a file output into the project's
 * exchange folder — needs no approval. It never overwrites an existing file or a report the person made: those, a delete,
 * a rename, a restore, a bundle, anything outside the project and every call under Safe analysis or from an external
 * client are judged exactly as before. The judgement ({@link McpTools#reportInProject}) is the one the adapter (whether to
 * ask) and the action server (whether a grant is needed, and what the call may then write) share.
 *
 * <p>Witnesses (all run 2026-10-18, each red): drop the {@code reportInProject} branch from {@code hostedCallKind} — the
 * create is asked again; treat every name as free — the person's report is pre-allowed; drop the existing-file test in
 * {@code newFileInside} AND the exchange guard's own — the existing file is pre-allowed (either alone keeps it asked);
 * judge a delete as a build — the delete is pre-allowed; drop the real-ancestor check — the outside path is pre-allowed;
 * compare lexical paths in both this check and the exchange guard — the link out is pre-allowed; drop
 * {@code scope.full()} — Safe is pre-allowed; drop the createdAt match — a report the person replaced is pre-allowed;
 * drop the generation check in {@code onHostedReportWritten} — another session's write counts as this one's.
 */
class InProjectReportTest {

    @TempDir Path tmp;

    private FakeReportState reports() throws Exception {
        FakeReportState r = new FakeReportState();
        r.root = Files.createDirectories(tmp.resolve("diamond"));
        r.exchange = Files.createDirectories(r.root.resolve("reports"));
        r.reports.put("Person's findings", "2026-10-18T09:00:00Z");
        return r;
    }

    private static OpenScope full(FakeReportState r) {
        return new OpenScope(r.root, true, null, 7, r);
    }

    private static HostedCallKind kind(Map<String, Object> params, OpenScope scope) {
        return McpTools.hostedCallKind("report", params, null, scope);
    }

    static final List<Map<String, Object>> SECTIONS = List.of(Map.of("kind", "narrative", "text", "DEMO"));

    @Test
    @DisplayName("★ Full: a new report, and its PDF into the project's exchange folder, need no approval")
    void anInProjectCreateIsPreAllowed() throws Exception {
        FakeReportState r = reports();
        assertEquals(HostedCallKind.PRE_ALLOWED, kind(Map.of("name", "DEMO trace", "sections", SECTIONS), full(r)));
        assertEquals(new ReportAdmission("DEMO trace", null), McpTools.reportInProject(Map.of("name", "DEMO trace",
                "sections", SECTIONS), full(r)), "a create: no report of that name may exist when it runs");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind(Map.of("name", "DEMO trace", "sections", SECTIONS,
                "path", "demo-trace.pdf"), full(r)), "with a new file inside the project");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind(Map.of("path", "finding.pdf", "recordIndex", 3), full(r)),
                "the single-record finding: a new file inside the project");
        assertEquals(new ReportAdmission(null, null), McpTools.reportInProject(Map.of("path", "finding.pdf"), full(r)),
                "a file output writes no report");
    }

    @Test
    @DisplayName("★ the session's own report: adding a section needs no approval while nobody else replaced it")
    void anUpdateOfTheSessionsOwnReport() throws Exception {
        FakeReportState r = reports();
        r.reports.put("DEMO trace", "2026-10-18T10:00:00.123Z");
        Map<String, Object> addSection = Map.of("name", "DEMO trace", "sections", List.of(SECTIONS.get(0), SECTIONS.get(0)));
        assertEquals(HostedCallKind.CHANGES, kind(addSection, full(r)), "not written by this session: asked");
        r.sessionGeneration = 7;
        r.session.put("DEMO trace", "2026-10-18T10:00:00.123Z");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind(addSection, full(r)));
        assertEquals(new ReportAdmission("DEMO trace", "2026-10-18T10:00:00.123Z"), McpTools.reportInProject(addSection,
                full(r)), "an update: the report must still carry what the session left");
        r.reports.put("DEMO trace", "2026-10-18T11:30:00Z");          // the person rebuilt it meanwhile
        assertEquals(HostedCallKind.CHANGES, kind(addSection, full(r)), "replaced by someone else: asked");
        r.reports.put("DEMO trace", "2026-10-18T10:00:00.123Z");
        assertEquals(HostedCallKind.CHANGES, kind(addSection, new OpenScope(r.root, true, null, 8, r)),
                "another session's write is not this one's");
    }

    @Test
    @DisplayName("★ an overwrite asks: the person's report, an existing file")
    void anOverwriteAsks() throws Exception {
        FakeReportState r = reports();
        assertEquals(HostedCallKind.CHANGES, kind(Map.of("name", "Person's findings", "sections", SECTIONS), full(r)));
        assertEquals(HostedCallKind.CHANGES, kind(Map.of("name", "Person's findings", "title", "Renamed by Claude"), full(r)),
                "a retitle is a write over the person's report too");
        Files.writeString(r.exchange.resolve("taken.pdf"), "%PDF");
        assertEquals(HostedCallKind.WRITES_FILE, kind(Map.of("name", "DEMO trace", "sections", SECTIONS,
                "path", "taken.pdf"), full(r)), "an existing file: asked as before");
    }

    @Test
    @DisplayName("★ a delete, a rename, a restore and a bundle ask as before")
    void deletesAndTheRestAsk() throws Exception {
        FakeReportState r = reports();
        r.reports.put("DEMO trace", "x");
        r.sessionGeneration = 7;
        r.session.put("DEMO trace", "x");                    // even the session's own report
        assertEquals(HostedCallKind.CHANGES, kind(Map.of("name", "DEMO trace", "delete", true), full(r)));
        assertEquals(HostedCallKind.CHANGES, kind(Map.of("name", "DEMO trace", "rename", "Other"), full(r)));
        assertEquals(HostedCallKind.CHANGES, kind(Map.of("restore", "Old"), full(r)));
        assertEquals(HostedCallKind.WRITES_FILE, kind(Map.of("bundle", Map.of("path", "x.fexp")), full(r)));
        assertEquals(HostedCallKind.NON_MUTATING, kind(Map.of("restore", true), full(r)), "listing the bin is as before");
    }

    @Test
    @DisplayName("★ outside the project asks: a path that leaves it, a link leading out, no active project")
    void outsideTheProjectAsks(@TempDir Path elsewhere) throws Exception {
        FakeReportState r = reports();
        Path outside = Files.createDirectories(elsewhere.resolve("outside"));
        r.exchange = outside;                                 // the exchange folder is not the project's
        assertEquals(HostedCallKind.WRITES_FILE, kind(Map.of("name", "DEMO trace", "sections", SECTIONS,
                "path", "x.pdf"), full(r)));
        r.exchange = r.root;
        Files.createSymbolicLink(r.root.resolve("out-link"), outside);
        assertEquals(HostedCallKind.WRITES_FILE, kind(Map.of("path", "out-link/x.pdf", "recordIndex", 1), full(r)),
                "a link inside the project that leads out");
        r.root = null;                                        // no project is active: reports go to the person's settings
        assertEquals(HostedCallKind.CHANGES, kind(Map.of("name", "DEMO trace", "sections", SECTIONS), full(r)));
    }

    @Test
    @DisplayName("★ Safe analysis asks for every report call; an external client (no scope) is unchanged")
    void safeAndExternalAreUnchanged() throws Exception {
        FakeReportState r = reports();
        Map<String, Object> create = Map.of("name", "DEMO trace", "sections", SECTIONS);
        assertEquals(HostedCallKind.CHANGES, kind(create, new OpenScope(r.root, false, null, 7, r)), "Safe");
        assertNull(McpTools.reportInProject(create, new OpenScope(r.root, false, null, 7, r)));
        for (Map<String, Object> p : List.of(create, Map.<String, Object>of("path", "a.pdf"),
                Map.<String, Object>of("name", "x", "delete", true), Map.<String, Object>of("restore", true))) {
            assertEquals(McpTools.hostedCallKind("report", p), kind(p, null), "no scope (external): as before, " + p);
            assertEquals(McpTools.hostedCallKind("report", p), kind(p, new OpenScope(r.root, true)),
                    "a scope with no reports: as before, " + p);
        }
    }

    /** ★ The session's record: per generation, only the current one, replaced by a later write. */
    @Test
    @DisplayName("★ localAgentPermission records the session's own report writes for the current generation only, as digests")
    void theSessionRecordsItsOwnWrites() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        long g = start.generation();
        d.post(SessionEvents.HostedReportWritten.of(g + 1, "DEMO trace", "a"));
        assertFalse(d.snapshot().permission().sessionWrote(g + 1, "DEMO trace", "a"), "not the current generation: stale");
        d.post(SessionEvents.HostedReportWritten.of(g, "DEMO trace", "a"));
        assertTrue(d.snapshot().permission().sessionWrote(g, "DEMO trace", "a"));
        d.post(SessionEvents.HostedReportWritten.of(g, "DEMO trace", "b"));
        assertTrue(d.snapshot().permission().sessionWrote(g, "DEMO trace", "b"), "the write after an update");
        assertFalse(d.snapshot().permission().sessionWrote(g, "DEMO trace", "a"), "…replaces the earlier one");
        assertFalse(d.snapshot().permission().sessionWrote(g, "Person's findings", "b"));
        assertFalse(d.snapshot().permission().sessionWrote(g + 1, "DEMO trace", "b"), "another generation reads nothing");
        assertFalse(d.snapshot().permission().toString().contains("DEMO trace"), "the record holds no report name");
    }

    /** ★ The node: in Full, the adapter's PRE_ALLOWED report is allowed with no dialog and no grant. */
    @Test
    @DisplayName("★ localAgentPermission allows Full's PRE_ALLOWED report without a dialog or a grant")
    void theNodeAllowsItWithoutADialog() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentFullPostureTest.startFull(d, a, FullMode.DEFAULT);
        var t = LocalAgentSessionTest.send(d, a, 10, LocalAgentFullPostureTest.full(FullMode.DEFAULT));
        d.post(LocalAgentFullPostureTest.fullInit(start, t.ticket(), "default", List.of(), "connected"));
        d.post(new SessionEvents.LocalAgentPermissionRequested(start.generation(), t.ticket(), 81,
                SessionEvents.ToolClass.HOSTED_DESTRUCTIVE, "report", "digest", "tool", true, HostedCallKind.PRE_ALLOWED));
        assertEquals(0, a.permissionDialogs.size(), "not asked");
        var answer = a.permissionAnswers.get(a.permissionAnswers.size() - 1);
        assertTrue(answer.allow());
        assertEquals(SessionEvents.PermissionBy.PRE_ALLOWED, answer.by());
        assertEquals(0, answer.grantId(), "no server-side grant: the server re-judges the call itself");
        assertEquals("it writes a new report inside this project",
                telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission.REPORT_IN_PROJECT);
    }
}
