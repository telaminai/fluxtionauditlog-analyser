package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.DuplicateChartRepair;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * The in-app repair for duplicate chart names (owner decision, 2026-09-24), in a real frame.
 *
 * <p>Before this, an ambiguous profile disabled every chart control and the only recovery was closing the
 * analyser and hand-editing a config file — a dead end for people whose profiles were made ambiguous by a
 * shipped release. Two things change: unrelated chart work is allowed again, and there is a way out.
 *
 * <p>The chooser is injected rather than clicked, because the real one is a modal; {@link
 * DuplicateChartRepairTest} holds the policy it feeds. What this test adds is that the button reaches the
 * policy, that the result is persisted and rebound, and that cancelling touches nothing.
 */
class DuplicateChartRepairFrameTest {

    private static GraphSpec chart(String name, String explanation) {
        return new GraphSpec(name, List.of("a" + (char) 1 + "x"), List.of(), null, null, null, explanation,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", true);
    }

    /** A home whose global config already holds two charts called "Same" — the legacy shape. */
    private static Path seedAmbiguousHome(Path tmp) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path config = home.resolve(".fluxtion-analyser").resolve("config");
        Files.createDirectories(config.getParent());
        var store = new ConfigStore(config);
        var cfg = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        cfg.savedGraphs.add(chart("Same", "the first one"));
        cfg.savedGraphs.add(chart("Same", "the second one"));
        store.save(cfg);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static void setChooser(MainFrame frame, Object chooser) {
        try {
            var f = MainFrame.class.getDeclaredField("repairChooser");
            f.setAccessible(true);
            f.set(frame, chooser);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private static GraphTabs tabs(MainFrame frame) {
        return (GraphTabs) field(frame, "graphTabs");
    }

    @Test
    void repairingRenamesOneKeepsBothAndLoadsThem(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path config = seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);

                invokeRestore(f.frame);
                assertNotNull(tabs(f.frame).definitionRefusal(), "an ambiguous config is withheld");
                assertTrue(tabs(f.frame).repairButton().isVisible(), "and offers the way out");

                java.util.function.Function<List<GraphSpec>, Map<Integer, DuplicateChartRepair.Choice>> answer =
                        saved -> {
                            Map<Integer, DuplicateChartRepair.Choice> m = new LinkedHashMap<>();
                            m.put(0, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "First"));
                            m.put(1, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "Second"));
                            return m;
                        };
                setChooser(f.frame, answer);

                tabs(f.frame).repairButton().doClick();

                assertNull(tabs(f.frame).definitionRefusal(), "the refusal is lifted once names are unique");
                assertFalse(tabs(f.frame).repairButton().isVisible());
                assertEquals(List.of("First", "Second"),
                        f.frame.config().savedGraphs.stream().map(GraphSpec::name).toList());
                assertEquals("the first one", f.frame.config().savedGraphs.get(0).explanation(),
                        "renaming keeps what the chart contained — the point of repairing rather than deleting");
                assertEquals(List.of("First", "Second"),
                        new ConfigStore(config).load().savedGraphs.stream().map(GraphSpec::name).toList(),
                        "and the repair is persisted, not just shown");
            });
        }
    }

    @Test
    void cancellingTheRepairChangesNothing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path config = seedAmbiguousHome(tmp);
        byte[] before = Files.readAllBytes(config);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);

                setChooser(f.frame, (java.util.function.Function<List<GraphSpec>,
                        Map<Integer, DuplicateChartRepair.Choice>>) saved -> null);   // Cancel
                tabs(f.frame).repairButton().doClick();

                assertNotNull(tabs(f.frame).definitionRefusal(), "cancelling leaves the profile ambiguous");
                assertEquals(2, f.frame.config().savedGraphs.size(), "and leaves BOTH definitions");
                assertArrayEquals(before, readBytes(config), "and writes nothing");
            });
        }
    }

    @Test
    void aPartialAnswerIsRefusedAndNothingIsLost(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path config = seedAmbiguousHome(tmp);
        byte[] before = Files.readAllBytes(config);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);

                // one of the two left on "Choose…" — the dialog contributes no entry for it
                setChooser(f.frame, (java.util.function.Function<List<GraphSpec>,
                        Map<Integer, DuplicateChartRepair.Choice>>) saved -> Map.of(
                        0, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "First")));

                javax.swing.Timer dismiss = new javax.swing.Timer(250, e -> {
                    for (java.awt.Window w : java.awt.Window.getWindows()) {
                        if (w instanceof JDialog d && d.isVisible()) d.dispose();
                    }
                });
                dismiss.setRepeats(true);
                dismiss.start();
                try {
                    tabs(f.frame).repairButton().doClick();
                } finally {
                    dismiss.stop();
                }

                assertNotNull(tabs(f.frame).definitionRefusal(), "a partial repair leaves the refusal in force");
                assertEquals(2, f.frame.config().savedGraphs.size(), "both definitions survive");
                assertArrayEquals(before, readBytes(config));
            });
        }
    }

    @Test
    void anAmbiguousProfileNoLongerBlocksMakingANewChart(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);
                GraphTabs t = tabs(f.frame);
                assertNotNull(t.definitionRefusal());

                assertNull(t.addGraph("Same"),
                        "a WITHHELD definition still cannot be opened — that is what the refusal protects");
                assertEquals(2, f.frame.config().savedGraphs.size(),
                        "and the ambiguous definitions are untouched by any of this");
            });
        }
    }

    private static byte[] readBytes(Path p) {
        try { return Files.readAllBytes(p); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    /** Bind a log so charts can exist, then run the guarded restore that produces the refusal. */
    private static void invokeRestore(MainFrame frame) {
        try {
            var m = MainFrame.class.getDeclaredMethod("restoreGraphDefinitions", List.class);
            m.setAccessible(true);
            m.invoke(frame, List.copyOf(frame.config().savedGraphs));
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
