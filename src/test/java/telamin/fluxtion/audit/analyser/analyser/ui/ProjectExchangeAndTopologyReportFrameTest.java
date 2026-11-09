package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.awaitLoaded;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * #182 and #183 on a REAL frame, through the verbs. A person's project that declares no exchange directory: a
 * screenshot lands in the project's own {@code analyser-exchange/} — not the machine directory, which in use was
 * another project's folder — and the reply says where. A report's topology section with no saved focus renders the
 * whole graph into the PDF, with nothing in the reply saying it was skipped.
 */
class ProjectExchangeAndTopologyReportFrameTest {

    private static final Path GRAPH = Path.of("src/test/resources/topology/demo-quote-processor.graphml").toAbsolutePath();
    private static final Path LOG = Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath();

    @SuppressWarnings("unchecked")
    static Map<String, Object> payload(Map<String, Object> reply) {
        return (Map<String, Object>) reply.values().stream().filter(Map.class::isInstance).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("★ #182: a project with no exchange setting — the screenshot lands under the project, and the reply says so; ★ #183: a topology section with no focus is drawn, not skipped")
    @SuppressWarnings("unchecked")
    void exchangeDefaultsToTheProjectAndTheTopologySectionRenders(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path projectDir = Files.createDirectories(tmp.resolve("demo-project"));
        Path machine = Files.createDirectories(tmp.resolve("another-projects-folder"));
        ProjectProfile.save(ProjectProfile.pathFor(projectDir), new AppConfig(), new SettingsShare());
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            onEdt(() -> {
                var c = (AppConfig) field(f.frame, "config");
                c.assistantExports = true;
                c.assistantExportDir = machine.toString();       // the machine tier: someone else's folder
            });
            onEdt(() -> render(f.ex, "open", Map.of("project", projectDir.toString())));
            onEdt(() -> render(f.ex, "open", Map.of("graphml", GRAPH.toString())));
            onEdt(() -> render(f.ex, "open", Map.of("log", LOG.toString())));
            awaitLoaded(f.ex);

            AtomicReference<Map<String, Object>> shot = new AtomicReference<>();
            onEdt(() -> shot.set(render(f.ex, "screenshot", Map.of("path", "shot.png"))));
            Path expected = projectDir.toRealPath().resolve("analyser-exchange").resolve("shot.png");
            assertTrue(Files.isRegularFile(expected), "★ the screenshot is under the project: " + shot.get());
            try (var s = Files.list(machine)) {
                assertEquals(List.of(), s.toList(), "and nothing went to the machine directory");
            }
            Map<String, Object> exchange = (Map<String, Object>) payload(shot.get()).get("exchange");
            assertNotNull(exchange, "the reply says where it went: " + shot.get());
            assertEquals("project default", exchange.get("source"));
            assertEquals(projectDir.toRealPath().resolve("analyser-exchange").toString(), exchange.get("dir"));
            assertEquals("Created analyser-exchange/ in the project; you may want to add it to .gitignore", exchange.get("created"),
                    "★ the first write that makes the folder says so, once (owner, #182)");
            WalkPlaybackFrameTest.await("the status line says it too",
                    () -> String.valueOf(((javax.swing.JLabel) field(f.frame, "status")).getText()).startsWith("Created analyser-exchange/"));
            AtomicReference<Map<String, Object>> again = new AtomicReference<>();
            onEdt(() -> again.set(render(f.ex, "screenshot", Map.of("path", "shot2.png"))));
            assertNull(((Map<String, Object>) payload(again.get()).get("exchange")).get("created"), "and only the first time");

            AtomicReference<Map<String, Object>> report = new AtomicReference<>();
            onEdt(() -> report.set(render(f.ex, "report", Map.of("name", "topo", "title", "DEMO topology",
                    "sections", List.of(Map.of("kind", "topology")), "path", "topo.pdf"))));
            Map<String, Object> echo = payload(report.get());
            assertEquals(1, echo.get("sections"), "★ the topology section is kept (red before #183: skipped): " + echo);
            assertNull(echo.get("unresolved"), "and resolves without a saved focus: " + echo);
            Object warnings = echo.get("warnings");
            assertTrue(warnings == null || !String.valueOf(warnings).contains("topology"),
                    "nothing says the topology was skipped or not rendered: " + warnings);
            assertTrue(Files.isRegularFile(projectDir.toRealPath().resolve("analyser-exchange").resolve("topo.pdf")),
                    "the PDF lands under the project too: " + echo);
        }
    }

    static javax.swing.AbstractButton button(java.awt.Container in, String text) {
        for (java.awt.Component c : in.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) return b;
            if (c instanceof java.awt.Container inner) {
                var found = button(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("★ #198 review F4: with a narrowed focus in force, a report's \"show the whole graph\" link shows the whole graph")
    void showTheWholeGraphLeavesANarrowedFocus(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            onEdt(() -> render(f.ex, "open", Map.of("graphml", GRAPH.toString())));
            onEdt(() -> render(f.ex, "report", Map.of("name", "topo", "title", "DEMO topology",
                    "sections", List.of(Map.of("kind", "topology")))));
            onEdt(() -> render(f.ex, "topology", Map.of("select", "quotePublisher", "scope", "node", "focus", true)));
            TopologyPanel topology = (TopologyPanel) field(f.frame, "topologyPanel");
            ReportsPanel reports = (ReportsPanel) field(f.frame, "reportsPanel");
            AtomicReference<Map<String, Object>> view = new AtomicReference<>();
            onEdt(() -> view.set(topology.cursorState()));
            assertEquals(1, view.get().get("contextDepth"), "control: a narrowed focus is in force: " + view.get());
            Object narrowed = view.get().get("visibleNodes");

            AtomicReference<javax.swing.AbstractButton> link = new AtomicReference<>();
            onEdt(() -> {
                reports.refresh();
                reports.select("topo");
                link.set(button(reports, "show the whole graph"));
            });
            assertNotNull(link.get(), "the report's topology section offers the whole graph");
            onEdt(() -> link.get().doClick());

            onEdt(() -> view.set(topology.cursorState()));
            assertEquals(0, view.get().get("contextDepth"),
                    "★ the link shows the whole graph (red before: it only switched tabs, the focus stayed): " + view.get());
            assertNotEquals(narrowed, view.get().get("visibleNodes"), "every node is back in view: " + view.get());
            AtomicReference<java.awt.Component> tab = new AtomicReference<>();
            onEdt(() -> tab.set(((javax.swing.JTabbedPane) field(f.frame, "sideTabs")).getSelectedComponent()));
            assertSame(topology, tab.get(), "and the topology view is the one shown");
        }
    }

    @Test
    @DisplayName("#183: with no graph loaded, a topology section says why it was not rendered — in the reply, never silently")
    @SuppressWarnings("unchecked")
    void aTopologySectionThatCannotRenderSaysWhy(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            EvidenceCaptureFrameTest.exchange(f, tmp);
            EvidenceCaptureFrameTest.openLog(f, LOG);
            AtomicReference<Map<String, Object>> report = new AtomicReference<>();
            onEdt(() -> report.set(render(f.ex, "report", Map.of("name", "topo",
                    "sections", List.of(Map.of("kind", "topology")), "path", "topo.pdf"))));
            Map<String, Object> echo = payload(report.get());
            assertTrue(String.valueOf(echo.get("warnings")).contains("topology not rendered — no graph is loaded"),
                    "the reply says why: " + echo);
        }
    }
}
