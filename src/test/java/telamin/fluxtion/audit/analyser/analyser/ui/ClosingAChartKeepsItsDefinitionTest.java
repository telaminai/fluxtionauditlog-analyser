package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.3 — closing a chart must not delete it.
 *
 * <p>The defect, found by the owner at a real display on 2026-09-24: closing a chart tab removed the
 * chart for good and its row vanished from the Project panel. {@code syncOpenGraphsIntoConfig} cleared
 * {@code config.savedGraphs} and refilled it from the OPEN TABS, so the profile's saved-chart list was a
 * mirror of what was open rather than what was saved. Closing a tab was therefore a silent, unconfirmed
 * delete of the chart's series, formulas, right axis, explanation and pinned notes — the annotations the
 * code elsewhere calls "the part worth keeping". Reproduced in the profile bytes: {@code graph.count}
 * went 2 to 1 and every {@code graph.1.*} key was gone.
 *
 * <p>These tests are on the MERGE and on the round trip, not on the button: the loss happened in the
 * bookkeeping between the tabs and the config, which is where it has to be prevented.
 */
class ClosingAChartKeepsItsDefinitionTest {

    private static GraphSpec chart(String name, boolean open) {
        return new GraphSpec(name, List.of("a" + (char) 1 + "x"), List.of(), null, null, "caption",
                "why this chart exists", List.of(new GraphSpec.NoteSpec(1_000L, "the moment it broke", null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "step", open);
    }

    private static AppConfig roundTrip(GraphSpec... graphs) throws Exception {
        Path dir = Files.createTempDirectory("cfg");
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        AppConfig cfg = new AppConfig();
        cfg.savedGraphs.addAll(List.of(graphs));
        store.save(cfg);
        return store.load();
    }

    @Test
    void aClosedChartSurvivesTheProfileWithItsAnnotations() throws Exception {
        AppConfig back = roundTrip(chart("Prices", true), chart("Tick rate", false));

        assertEquals(2, back.savedGraphs.size(),
                "a closed chart is still a saved chart — this is the data loss: the list used to lose it");
        GraphSpec closed = back.savedGraphs.get(1);
        assertFalse(closed.open(), "and it is remembered as closed, so a reload does not reopen it");
        assertEquals("why this chart exists", closed.explanation(),
                "the explanation is the part worth keeping; losing it silently is the whole defect");
        assertEquals(1, closed.notes().size(), "pinned notes travel with the closed chart");
        assertEquals("the moment it broke", closed.notes().get(0).text());
    }

    @Test
    void openIsTheDefaultSoAnOlderProfileReopensEverythingAsBefore() throws Exception {
        Path dir = Files.createTempDirectory("cfg");
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        AppConfig cfg = new AppConfig();
        // the pre-M68.3 constructor — what every existing profile and caller produces
        cfg.savedGraphs.add(new GraphSpec("legacy", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        store.save(cfg);

        assertTrue(store.load().savedGraphs.get(0).open(),
                "a chart that never said otherwise is open, so upgrading changes nobody's layout");
    }

    @Test
    void onlyAClosedChartWritesTheKey() throws Exception {
        Path dir = Files.createTempDirectory("cfg");
        Path file = dir.resolve("config");
        ConfigStore store = new ConfigStore(file);
        AppConfig cfg = new AppConfig();
        cfg.savedGraphs.add(chart("Prices", true));
        store.save(cfg);
        String written = Files.readString(findWritten(file, dir));
        assertFalse(written.contains(".open="),
                "an open chart adds no key, so a profile gains nothing until someone closes a chart");

        cfg.savedGraphs.set(0, chart("Prices", false));
        store.save(cfg);
        assertTrue(Files.readString(findWritten(file, dir)).contains("graph.0.open=false"),
                "closing one is what writes it");
    }

    /** The store decides its own filename; find whatever it actually wrote. */
    private static Path findWritten(Path preferred, Path dir) throws Exception {
        if (Files.isRegularFile(preferred)) return preferred;
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).findFirst()
                    .orElseThrow(() -> new AssertionError("the store wrote no file in " + dir));
        }
    }

    @Test
    void withOpenChangesNothingElseAboutTheChart() {
        GraphSpec original = chart("Prices", true);
        GraphSpec closed = original.withOpen(false);
        assertFalse(closed.open());
        assertEquals(original.name(), closed.name());
        assertEquals(original.explanation(), closed.explanation());
        assertEquals(original.notes(), closed.notes());
        assertEquals(original.style(), closed.style());
        assertEquals(original.series(), closed.series());
        assertEquals(original, closed.withOpen(true),
                "closing and reopening a chart must be a round trip, not a slow erosion of it");
    }
}
