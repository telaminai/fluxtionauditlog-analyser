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

    @TempDir Path tmp;

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
    void repairingRenamesOneKeepsBothAndLoadsThem() throws Exception {
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
    void cancellingTheRepairChangesNothing() throws Exception {
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
    void aPartialAnswerIsRefusedAndNothingIsLost() throws Exception {
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

    /**
     * R13-1: the chooser is a modal, so a nested event loop runs while it is open and the action socket can
     * switch the chart list underneath it. Applying a repair computed from the list as it WAS would write
     * it over whatever is there now and destroy those definitions.
     *
     * <p>The reviewer's reproduction: with two global charts named "Same", open the dialog, have an agent
     * open a project holding "Project chart", then answer the dialog. The project profile became
     * [First, Second] and "Project chart" was gone.
     */
    @Test
    void aListThatChangedWhileTheDialogWasOpenIsRefused() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);

                // the chooser stands in for the modal: while it is "open", something else replaces the list
                setChooser(f.frame, (java.util.function.Function<List<GraphSpec>,
                        Map<Integer, DuplicateChartRepair.Choice>>) saved -> {
                    f.frame.config().savedGraphs.clear();
                    f.frame.config().savedGraphs.add(chart("Project chart", "someone else's work"));
                    Map<Integer, DuplicateChartRepair.Choice> m = new LinkedHashMap<>();
                    m.put(0, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "First"));
                    m.put(1, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "Second"));
                    return m;
                });

                javax.swing.Timer dismiss = dismissDialogs();
                try {
                    tabs(f.frame).repairButton().doClick();
                } finally {
                    dismiss.stop();
                }

                assertEquals(List.of("Project chart"),
                        f.frame.config().savedGraphs.stream().map(GraphSpec::name).toList(),
                        "a repair computed from the OLD list must not be written over the new one — "
                                + "that destroys definitions nobody was asked about");
            });
        }
    }

    /**
     * R13-2: a chart created during the refusal is never persisted (the save path returns early), and the
     * repair rebuilds every tab from the config. It used to disappear as a side effect of fixing something
     * else. It is now carried into the repaired list and saved.
     */
    @Test
    void aChartMadeDuringTheRefusalSurvivesTheRepair() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path config = seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f, tmp);
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);

                assertNotNull(tabs(f.frame).addGraph("Scratch"), "a new chart is allowed during a refusal");

                setChooser(f.frame, (java.util.function.Function<List<GraphSpec>,
                        Map<Integer, DuplicateChartRepair.Choice>>) saved -> {
                    Map<Integer, DuplicateChartRepair.Choice> m = new LinkedHashMap<>();
                    m.put(0, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "First"));
                    m.put(1, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.DELETE, null));
                    return m;
                });
                tabs(f.frame).repairButton().doClick();

                assertTrue(f.frame.config().savedGraphs.stream().anyMatch(g -> g.name().equals("Scratch")),
                        "work made during the refusal must not vanish because something else was repaired");
                assertTrue(new ConfigStore(config).load().savedGraphs.stream()
                                .anyMatch(g -> g.name().equals("Scratch")),
                        "and it is now persisted, since the profile is no longer ambiguous");
            });
        }
    }

    /** R13-3: the refusal must name the control that fixes it, not send people to a text editor. */
    @Test
    void theRefusalNamesTheRepairControl() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        seedAmbiguousHome(tmp);
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);
                String refusal = tabs(f.frame).definitionRefusal();
                assertTrue(refusal.contains("Repair names"),
                        "the refusal must point at the in-app fix: " + refusal);
                assertFalse(refusal.toLowerCase().contains("restart the analyser"),
                        "and must not still send people away to hand-edit: " + refusal);
            });
        }
    }

    /** O13-1: OK with nothing chosen is not the same as Cancel, and must not look like success. */
    @Test
    void okWithNothingChosenSaysSo() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        seedAmbiguousHome(tmp);
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);
                setChooser(f.frame, (java.util.function.Function<List<GraphSpec>,
                        Map<Integer, DuplicateChartRepair.Choice>>) saved -> Map.of());
                tabs(f.frame).repairButton().doClick();

                JLabel status = (JLabel) field(f.frame, "status");
                assertTrue(status.getText().toLowerCase().contains("nothing was chosen"),
                        "an empty answer is not a silent no-op: " + status.getText());
                assertNotNull(tabs(f.frame).definitionRefusal());
            });
        }
    }

    /**
     * Bind a real log, because a chart panel needs a store: {@code GraphTabs.newPanel} returns null without
     * one, so {@code addGraph} returns null for want of a log rather than because of the refusal. An
     * earlier version of this class claimed to do this and did not, which is why its "a new chart is
     * allowed" case proved nothing (review O13-2).
     */
    private static void openLog(AsyncOpenInterleavingFrameTest.Frame f, Path tmp) throws Exception {
        Path log = Files.writeString(tmp.resolve("sample.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        for (int i = 0; i < 400 && f.status().startsWith("Loading "); i++) Thread.sleep(25);
        assertFalse(f.status().startsWith("Loading "), "the log must finish loading before the test runs");
    }

    private static javax.swing.Timer dismissDialogs() {
        javax.swing.Timer t = new javax.swing.Timer(200, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog d && d.isVisible()) d.dispose();
            }
        });
        t.setRepeats(true);
        t.start();
        return t;
    }


    /**
     * R13-2b: renaming a duplicate onto an UNSAVED chart's name destroyed that chart. The repaired list
     * held the name, so the carry-forward skipped the live tab as "already present", and the person's work
     * went without a word. The reviewer's sequence: create "First" during the refusal, then repair the two
     * "Same" charts to "First" and "Second".
     */
    @Test
    void renamingOntoAnUnsavedChartIsRefusedRatherThanDestroyingIt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path config = seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f, tmp);
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);

                GraphPanel mine = tabs(f.frame).addGraph("First");
                assertNotNull(mine, "a new chart is allowed during the refusal");
                mine.addSpecs(List.of("node\u0001value"));
                var seriesBefore = mine.seriesSpecs();

                setChooser(f.frame, (java.util.function.Function<List<GraphSpec>,
                        Map<Integer, DuplicateChartRepair.Choice>>) saved -> {
                    Map<Integer, DuplicateChartRepair.Choice> m = new LinkedHashMap<>();
                    m.put(0, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "First"));
                    m.put(1, new DuplicateChartRepair.Choice(DuplicateChartRepair.Action.RENAME, "Second"));
                    return m;
                });

                javax.swing.Timer dismiss = dismissDialogs();
                try {
                    tabs(f.frame).repairButton().doClick();
                } finally {
                    dismiss.stop();
                }

                assertSame(mine, tabs(f.frame).graphNamed("First"),
                        "a rename onto an unsaved chart's name must be refused, not silently destroy it");
                assertEquals(seriesBefore, mine.seriesSpecs(), "and the unsaved chart keeps its series");
                assertNotNull(tabs(f.frame).definitionRefusal(), "the profile stays ambiguous");
                // byte equality is wrong here: opening a log legitimately rewrites unrelated settings
                // (recent files). What must not change is the chart definitions.
                assertEquals(List.of("Same", "Same"),
                        new ConfigStore(config).load().savedGraphs.stream().map(GraphSpec::name).toList(),
                        "both duplicates are still on disk, unrepaired");
            });
        }
    }

    /**
     * R13-4b: the assistant could create a chart during the refusal and then not edit it, because
     * "withheld" was answered by {@code hasDefinition}, which counts open tabs as taken. Only the saved
     * definitions are withheld.
     */
    @Test
    void theAssistantCanEditAChartItCreatedDuringTheRefusal() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f, tmp);
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);
                assertNotNull(tabs(f.frame).definitionRefusal(), "definitions are withheld");
            });

            var created = f.ex.render("graph", Map.of("name", "Scratch", "series", List.of("node.value")));
            assertTrue(created.ok(), "creating a new chart during the refusal is allowed: " + created.toMap());

            var edited = f.ex.render("graph", Map.of("name", "Scratch", "style", "line"));
            assertTrue(edited.ok(),
                    "the assistant must be able to edit the chart it just created — an open tab is not a "
                            + "withheld definition: " + edited.toMap());

            var withheld = f.ex.render("graph", Map.of("name", "Same", "style", "line"));
            assertFalse(withheld.ok(), "a WITHHELD definition is still refused");
        }
    }

    /** O13-2: this creates a chart, which its previous version claimed to and did not. */
    @Test
    void anAmbiguousProfileNoLongerBlocksMakingANewChart() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        seedAmbiguousHome(tmp);

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            openLog(f, tmp);
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                invokeRestore(f.frame);
                GraphTabs t = tabs(f.frame);
                assertNotNull(t.definitionRefusal());

                assertNull(t.addGraph("Same"),
                        "a WITHHELD definition still cannot be opened — that is what the refusal protects");
                assertNotNull(t.addGraph("Brand new"),
                        "but an unrelated chart CAN be created — the point of the owner's decision");
                assertTrue(t.graphNames().contains("Brand new"));
                assertEquals(2, f.frame.config().savedGraphs.size(),
                        "and the ambiguous definitions are untouched by any of this");
            });
        }
    }

    private static byte[] readBytes(Path p) {
        try { return Files.readAllBytes(p); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    /** Run the guarded restore that produces the refusal. No log is bound; none is needed. */
    private static void invokeRestore(MainFrame frame) {
        try {
            var m = MainFrame.class.getDeclaredMethod("restoreGraphDefinitions", List.class);
            m.setAccessible(true);
            m.invoke(frame, List.copyOf(frame.config().savedGraphs));
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
