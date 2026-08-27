package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M37 D-L1/D-L2 — the panel's model is built from the {@code context} payload and nothing else, every
 * empty state is a sentence, and the rows follow the lifecycle (D-L6) as the payload changes. Headless:
 * the model has no Swing in it.
 */
class ProjectModelTest {

    private static Map<String, Object> empty() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("project", Map.of("active", false, "note", "your own settings — no project is open"));
        ctx.put("filter", Map.of());
        return ctx;
    }

    private static Map<String, Object> full() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("log", Map.of("path", "/work/demo/logs/demo-quote-audit.yaml",
                "openedFrom", "/work/demo/logs/demo-quote-audit.yaml", "records", 10, "openedBy", "you"));
        ctx.put("provenance", "DEMO-quote-service");
        ctx.put("project", Map.of("active", true, "name", "DemoQuote",
                "settings", "/work/demo/.analyser/project.fluxtion-settings", "root", "/work/demo"));
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("graph", "demo-quote-processor.graphml");
        pair.put("graphSource", "OPENED");
        pair.put("graphPath", "/work/demo/build/demo-quote-processor.graphml");
        pair.put("applies", true);
        pair.put("loggedNodes", 5);
        pair.put("declaredByGraph", 5);
        pair.put("verdict", "5/5 logged nodes are declared by the graph");
        ctx.put("graphPairing", pair);
        ctx.put("processors", List.of(
                Map.of("class", "com.acme.demo.generated.DemoQuoteProcessor", "selected", true, "source", "found", "from", "project"),
                Map.of("class", "com.acme.demo.generated.HedgeProcessor", "selected", false, "source", "not found", "from", "project")));
        ctx.put("source", Map.of("roots", List.of("/work/demo/src/main/java"),
                "rootTiers", List.of(Map.of("path", "/work/demo/src/main/java", "tier", "project"))));
        return ctx;
    }

    @Test
    void everyEmptyStateIsASentence_neverABlankSection() {
        ProjectModel m = ProjectModel.from(empty());
        assertEquals(List.of(ProjectModel.PROJECT, ProjectModel.LOG, ProjectModel.GRAPH, ProjectModel.PROCESSORS, ProjectModel.ROOTS, ProjectModel.REPORTS),
                m.sections().stream().map(ProjectModel.Section::title).toList(), "six sections, always, in this order");
        for (ProjectModel.Section s : m.sections()) {
            if (s.title().equals(ProjectModel.REPORTS)) {
                // two states, both sentences: the exchange directory (off, here) and the saved reports (none)
                assertEquals(List.of("File exchange off", "No saved reports"), s.rows().stream().map(ProjectModel.Row::primary).toList());
                continue;
            }
            assertEquals(1, s.rows().size(), s.title() + " has exactly one row when empty");
            ProjectModel.Row r = s.rows().get(0);
            assertTrue(r.primary().startsWith("No "), s.title() + ": " + r.primary());
            assertNotNull(r.secondary(), s.title() + " says what would fill it");
            assertEquals(ProjectModel.Tone.MUTED, r.tone());
        }
        assertEquals("using your own settings (~/.fluxtion-analyser)", m.section(ProjectModel.PROJECT).rows().get(0).secondary());
        // a null payload — context threw, or nothing has ever been bound — is the same five sentences
        assertEquals(6, ProjectModel.from(null).sections().size());
    }

    @Test
    void aFullSessionRendersEveryFactWithItsProvenance() {
        ProjectModel m = ProjectModel.from(full());
        ProjectModel.Row proj = m.section(ProjectModel.PROJECT).rows().get(0);
        assertEquals("DemoQuote", proj.primary());
        assertEquals("/work/demo", proj.secondary());
        assertEquals("/work/demo/.analyser/project.fluxtion-settings", proj.path(), "C1: the real profile path");

        ProjectModel.Row log = m.section(ProjectModel.LOG).rows().get(0);
        assertEquals("demo-quote-audit.yaml", log.primary());
        assertEquals("10 records", log.secondary());
        assertEquals("opened by you · from DEMO-quote-service", log.provenance(), "§E provenance rides the row");

        ProjectModel.Row graph = m.section(ProjectModel.GRAPH).rows().get(0);
        assertEquals("demo-quote-processor.graphml", graph.primary());
        assertEquals("opened by you", graph.provenance());
        assertTrue(graph.secondary().startsWith("applies — 5/5"), "D-L4: the verdict is the row's second line: " + graph.secondary());
        assertEquals(ProjectModel.Target.TOPOLOGY, graph.target());

        List<ProjectModel.Row> procs = m.section(ProjectModel.PROCESSORS).rows();
        assertEquals(2, procs.size());
        assertEquals("DemoQuoteProcessor", procs.get(0).primary(), "the class name leads; the package is detail");
        assertEquals("selected · source found · com.acme.demo.generated", procs.get(0).secondary());
        assertEquals("source NOT found under any root · com.acme.demo.generated", procs.get(1).secondary());
        assertEquals(ProjectModel.Tone.WARN, procs.get(1).tone(), "a processor whose source cannot be found is a warning, not a muted fact");
        assertEquals("project", procs.get(0).provenance());

        ProjectModel.Row root = m.section(ProjectModel.ROOTS).rows().get(0);
        assertEquals("project", root.provenance(), "the tier is the provenance column");
    }

    @Test
    void anS3LogShowsTheOriginTheUserNamed_notTheTempCopy() {
        Map<String, Object> ctx = full();
        ctx.put("log", Map.of("path", "/var/folders/xx/T/fetched-123.yaml",
                "openedFrom", "s3://demo-bucket/audit/2026-08-26.yaml", "records", 10));
        ProjectModel.Row log = ProjectModel.from(ctx).section(ProjectModel.LOG).rows().get(0);
        assertEquals("2026-08-26.yaml", log.primary());
        assertEquals("s3://demo-bucket/audit/2026-08-26.yaml", log.path(), "C2: Copy copies the origin");
        assertEquals("10 records · fetched to a local copy", log.secondary());
        assertTrue(log.provenance().startsWith("opened by you"), "openedBy absent means a human: " + log.provenance());
    }

    @Test
    void aGraphWithNoLogSaysSo_andAMismatchIsAWarning() {
        Map<String, Object> ctx = empty();
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("graph", "x.graphml");
        pair.put("graphSource", "OPENED");
        ctx.put("graphPairing", pair);
        ProjectModel.Row g = ProjectModel.from(ctx).section(ProjectModel.GRAPH).rows().get(0);
        assertEquals("no log to pair with", g.secondary());
        assertEquals(ProjectModel.Tone.MUTED, g.tone());

        pair.put("applies", false);
        pair.put("verdict", "0/5 logged nodes are declared by the graph");
        ctx.put("log", Map.of("path", "/a/b.yaml", "openedFrom", "/a/b.yaml", "records", 3));
        g = ProjectModel.from(ctx).section(ProjectModel.GRAPH).rows().get(0);
        assertEquals(ProjectModel.Tone.WARN, g.tone());
        assertTrue(g.secondary().contains("does not fit this log"), g.secondary());

        pair.put("graphSource", "READER_INFERRED");
        pair.put("sourceGraphOffered", "reader.graphml");
        pair.put("sourceGraphNote", "the reader found no graph beside the log");
        List<ProjectModel.Row> rows = ProjectModel.from(ctx).section(ProjectModel.GRAPH).rows();
        assertEquals("supplied by the reader (INFERRED)", rows.get(0).provenance(), "INFERRED is shouted, as everywhere else");
        assertEquals("the reader's graph — not shown: opened beats supplied", rows.get(1).secondary(), "D-L7: the loser of the precedence says so");
        assertEquals("the reader found no graph beside the log", rows.get(2).secondary(), "N1: sourceGraphNote is the second line");
    }

    @Test
    void theRowsFollowTheLifecycle() {
        // open log → open graph → open project → close log: the model at each step is the payload at that step
        Map<String, Object> ctx = empty();
        assertEquals("No log loaded", ProjectModel.from(ctx).section(ProjectModel.LOG).rows().get(0).primary());
        ctx.put("log", Map.of("path", "/a/b.yaml", "openedFrom", "/a/b.yaml", "records", 3));
        assertEquals("b.yaml", ProjectModel.from(ctx).section(ProjectModel.LOG).rows().get(0).primary());
        Map<String, Object> pair = new LinkedHashMap<>(Map.of("graph", "g.graphml", "graphSource", "OPENED", "applies", true,
                "loggedNodes", 2, "declaredByGraph", 2));
        ctx.put("graphPairing", pair);
        assertEquals("g.graphml", ProjectModel.from(ctx).section(ProjectModel.GRAPH).rows().get(0).primary());
        ctx.put("project", Map.of("active", true, "name", "P", "settings", "/p/.analyser/project.fluxtion-settings", "root", "/p"));
        assertEquals("P", ProjectModel.from(ctx).section(ProjectModel.PROJECT).rows().get(0).primary());
        ctx.remove("log");
        pair.remove("applies");
        ProjectModel m = ProjectModel.from(ctx);
        assertEquals("No log loaded", m.section(ProjectModel.LOG).rows().get(0).primary());
        assertEquals("no log to pair with", m.section(ProjectModel.GRAPH).rows().get(0).secondary(), "the graph outlives the log and says it is unpaired");
        assertEquals("P", m.section(ProjectModel.PROJECT).rows().get(0).primary(), "the project survives a log close");
    }

    @Test
    void reportsSectionShowsWhereFilesLeave_andWhatTheProjectSaved() {
        Map<String, Object> ctx = full();
        ctx.put("exports", Map.of("enabled", true, "dir", "/work/demo/exchange"));
        ctx.put("reports", List.of(
                Map.of("name", "spread-breach", "title", "Spread widened before the breach", "sections", 3, "from", "project"),
                Map.of("name", "one", "title", "One", "sections", 1, "from", "project")));
        List<ProjectModel.Row> rows = ProjectModel.from(ctx).section(ProjectModel.REPORTS).rows();
        assertEquals(3, rows.size());
        assertEquals("Exports to exchange", rows.get(0).primary());
        assertEquals("/work/demo/exchange", rows.get(0).path(), "Copy copies the directory");
        assertEquals("own settings", rows.get(0).provenance(), "the exchange directory is machine-tier, never shared");
        assertEquals("Spread widened before the breach", rows.get(1).primary(), "the title leads, not the file-safe name");
        assertEquals("3 sections · saved report", rows.get(1).secondary());
        assertEquals("1 section · saved report", rows.get(2).secondary());
        assertEquals("project", rows.get(1).provenance());
        assertEquals(ProjectModel.Target.REPORTS, rows.get(1).target(), "Go leads to the Reports tab — navigation, not rendering");
        assertEquals(ProjectModel.Target.SETTINGS_ASSISTANT, rows.get(0).target(), "the exchange directory lives on Settings ▸ Assistant, and the button must land there (owner, 2026-08-27)");

        ctx.put("exports", Map.of("enabled", false));
        rows = ProjectModel.from(ctx).section(ProjectModel.REPORTS).rows();
        assertEquals("File exchange off", rows.get(0).primary());
        assertEquals(ProjectModel.Tone.MUTED, rows.get(0).tone());
        assertTrue(rows.get(0).secondary().contains("Settings ▸ Assistant"), "says where to turn it on");
    }

    @Test
    void aRunbookPointerIsAVisibleRowOfTheProjectSection() {
        Map<String, Object> ctx = full();
        ctx.put("runbooks", List.of(
                Map.of("name", "deploy", "path", "ops/deploy.md", "resolved", "/work/demo/ops/deploy.md", "exists", true, "from", "project"),
                Map.of("name", "restart", "path", "ops/restart.md", "resolved", "/work/demo/ops/restart.md", "exists", false, "from", "project")));
        List<ProjectModel.Row> rows = ProjectModel.from(ctx).section(ProjectModel.PROJECT).rows();
        assertEquals(3, rows.size(), "the project row, then one row per pointer (D-C7)");
        assertEquals("deploy runbook: ops/deploy.md", rows.get(1).primary(), "drawn as the profile holds it — a relative pointer");
        assertEquals("/work/demo/ops/deploy.md", rows.get(1).path(), "Copy/Show act on where it lands here");
        assertEquals("a pointer into the repository — never contents", rows.get(1).secondary());
        assertEquals(ProjectModel.Target.NONE, rows.get(1).target(), "nothing on the panel runs it");
        assertEquals(ProjectModel.Tone.WARN, rows.get(2).tone());
        assertEquals("file NOT found under the project root", rows.get(2).secondary());
    }

    @Test
    void abbreviationKeepsHeadAndTail_andNeverTouchesWhatIsCopied() {
        assertEquals("~/projects/demo/logs/a.yaml", ProjectModel.abbreviate("/Users/someone/projects/demo/logs/a.yaml", "/Users/someone", 44));
        String longPath = "/Users/someone/very/deep/directory/structure/for/a/project/build/generated/x.graphml";
        String abbr = ProjectModel.abbreviate(longPath, "/Users/someone", 44);
        assertTrue(abbr.length() <= 44, abbr);
        assertTrue(abbr.startsWith("~/") && abbr.endsWith("/generated/x.graphml") && abbr.contains("…"), abbr);
        assertEquals("s3://bucket/key.yaml", ProjectModel.abbreviate("s3://bucket/key.yaml", "/Users/someone", 44), "short stays as is");
        assertNull(ProjectModel.abbreviate(null, "/h", 44));
    }
}
