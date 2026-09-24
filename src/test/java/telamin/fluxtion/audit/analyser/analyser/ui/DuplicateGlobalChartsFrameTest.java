package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import javax.swing.JTextArea;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.ChartLifecycleReviewFrameTest.*;

/** Constructed legacy configuration; neither duplicate is selected or silently renamed. */
class DuplicateGlobalChartsFrameTest {
    @TempDir Path tmp;

    private Path seed() throws Exception {
        assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path path = tmp.resolve("home/.fluxtion-analyser/config");
        AppConfig config = new AppConfig();
        config.savedGraphs.add(chart("Same", true));
        config.savedGraphs.add(chart("Same", false));
        new ConfigStore(path).save(config);
        return path;
    }

    private void openLog(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        Path log = Files.writeString(tmp.resolve("sample.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        for (int i = 0; i < 400 && edt(() -> f.status().startsWith("Loading ")); i++) Thread.sleep(25);
        edt(() -> {
            assertFalse(f.status().startsWith("Loading "), "duplicate global charts must not interrupt log loading");
            assertNotNull(field(f.frame, "store"));
            f.frame.setSize(1200, 850);
            f.frame.setVisible(true);
            return null;
        });
    }

    @Test void duplicateGlobalChartsAreWithheldAndLogLoadingCompletes() throws Exception {
        Path path = seed();
        List<GraphSpec> original = List.copyOf(new ConfigStore(path).load().savedGraphs);
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f);
            edt(() -> {
                GraphTabs tabs = (GraphTabs) field(f.frame, "graphTabs");
                assertTrue(tabs.graphNames().isEmpty(), "neither duplicate is chosen as a winner");
                assertTrue(tabs.definitionRefusal().contains("Duplicate chart name 'Same'"));
                assertTrue(tabs.definitionRefusal().contains(path.toString()));
                assertTrue(components(tabs).stream().anyMatch(c -> c instanceof JTextArea a
                        && a.isVisible() && a.getText().equals(tabs.definitionRefusal())),
                        "the refusal must be visible on the Graph panel");
                assertNull(tabs.addGraph("Other"), "withheld definitions must not acquire unsaved edits");
                assertEquals(original, f.frame.config().savedGraphs);
                assertDoesNotThrow(() -> invoke(f.frame, "saveConfigQuietly"), "autosave must leave withheld definitions intact");
                assertDoesNotThrow(() -> invoke(f.frame, "applyImportedConfig"),
                        "importing other settings must retain the refusal without throwing");
                return null;
            });
            var refusal = f.ex.render("graph", Map.of("name", "Same", "style", "line"));
            assertFalse(refusal.ok());
            assertTrue(refusal.error().contains("Charts not loaded:"), "the action must state the actual refusal");
            assertEquals(original, new ConfigStore(path).load().savedGraphs, "both serialized definitions survive autosave");
        }
    }

    @Test void validProjectWorksAndClosingItRestoresTheGlobalRefusal() throws Exception {
        Path path = seed();
        List<GraphSpec> original = List.copyOf(new ConfigStore(path).load().savedGraphs);
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f);
            AppConfig valid = new AppConfig();
            valid.savedGraphs.add(chart("Project chart", true));
            Path profile = ProjectProfile.pathFor(tmp.resolve("project"));
            ProjectProfile.save(profile, valid, new SettingsShare());
            assertTrue(f.ex.render("open", Map.of("project", profile.toString())).ok());
            openLog(f);
            edt(() -> {
                GraphTabs tabs = (GraphTabs) field(f.frame, "graphTabs");
                assertNull(tabs.definitionRefusal());
                assertNotNull(tabs.graphNamed("Project chart"));
                return null;
            });
            assertTrue(f.ex.render("open", Map.of("close", "project")).ok());
            openLog(f);
            edt(() -> {
                GraphTabs tabs = (GraphTabs) field(f.frame, "graphTabs");
                assertNotNull(tabs.definitionRefusal(), "returning to global definitions must restore their refusal");
                assertTrue(tabs.graphNames().isEmpty());
                assertEquals(original, f.frame.config().savedGraphs);
                return null;
            });
            assertEquals(original, new ConfigStore(path).load().savedGraphs);
        }
    }
}
