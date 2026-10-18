package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.Usability.button;

/**
 * ★ The usability scorecard's budgets in CI (owner, 2026-10-18; docs/usability/scorecard.md): at 1200×800, docked, the
 * always-visible chrome words and the clipped strings of three key states, and the acts of the eight scripted tasks,
 * stay at today's measured values or better ({@link UsabilityBudgets}). The tasks run in the REAL frame with the
 * {@link UsabilityTasks} harness — the same scripts {@code tools/usability/run.py} runs against the built jar.
 *
 * <p>Witnesses (rule 8), executed by {@link #aBudgetCatchesAnExtraLabelAndAnExtraStep}: one more label on the start
 * page breaks S1's word budget, and one more required click in a task breaks its act budget. A real product mutation
 * did the same (scorecard.md § Witnesses).
 */
class UsabilityBudgetFrameTest {

    private static LookAndFeel original;

    @BeforeAll
    static void rememberTheLookAndFeel() {
        original = UIManager.getLookAndFeel();
    }

    /** The next suite in this JVM must meet the look and feel it would have met (SpotlightFrameTest samples pixels). */
    @AfterAll
    static void restoreTheLookAndFeel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(original);
            } catch (javax.swing.UnsupportedLookAndFeelException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @DisplayName("★ the start page, the assistant when Ready and the Records area keep their chrome-word budgets and show no clipped text")
    void keyStatesStayInBudget(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        List<UsabilityTasks.StateMeasure> states = List.of(
                UsabilityTasks.startPage(tmp.resolve("S1")),
                UsabilityTasks.assistantReady(tmp.resolve("S2")),
                UsabilityTasks.recordsWithList(tmp.resolve("S3")));
        for (var s : states) System.out.println("[usability] " + s.toMap());
        List<String> broken = UsabilityBudgets.violations(states, List.of());
        assertTrue(broken.isEmpty(), "★ over budget:\n  " + String.join("\n  ", broken));
    }

    @Test
    @DisplayName("★ the eight scripted tasks, T4c and the scripted authoring task succeed within their act budgets")
    void tasksStayInBudget() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        // not @TempDir: a closed project's debounced settings write may land after the frame is gone, and JUnit's
        // cleanup then fails on a directory that is no longer empty; this one is removed best-effort below
        Path tmp = java.nio.file.Files.createTempDirectory("usability-tasks");
        try {
            runTasks(tmp);
        } finally {
            try (var walk = java.nio.file.Files.walk(tmp)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private void runTasks(Path tmp) throws Exception {
        List<Usability.Run> runs = new ArrayList<>();
        List<UsabilityTasks.Task> all = new ArrayList<>(UsabilityTasks.tasks());
        all.addAll(UsabilityTasks.authoringTasks());
        all.addAll(UsabilityTasks.targetTasks());          // T4c must pass since the records-filter fix (integration)
        for (UsabilityTasks.Task t : all) {
            Usability.Run r = UsabilityTasks.run(t, tmp.resolve(t.id()));
            System.out.println("[usability] " + r.toMap());
            runs.add(r);
        }
        assertEquals(UsabilityBudgets.ACTS.keySet(), java.util.Set.copyOf(runs.stream().map(r -> r.id).toList()),
                "every task has a budget and every budget a task");
        List<String> broken = UsabilityBudgets.violations(List.of(), runs);
        assertTrue(broken.isEmpty(), "★ over budget:\n  " + String.join("\n  ", broken));
    }

    /**
     * ★ The right-record check (for M79's flag-and-select tasks) tells records apart by digest: with the first record
     * selected it says "not the breach record"; with the breach record selected, it says it is. Witness: compare row
     * numbers instead of digests and sort the table — the check follows the row, not the record.
     */
    @Test
    @DisplayName("★ the right-record check compares the selected record's digest with the expected one")
    void theRightRecordCheckTellsRecordsApart(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (UsabilityTasks.Session s = new UsabilityTasks.Session(tmp, UsabilityTasks.answering(), false)) {
            s.openDemo();
            Usability.onEdt(() -> {
                String breach = Usability.expectedRecordDigest(s.frame, raw -> raw.contains("RiskBreachEvent"));
                assertTrue(breach != null && !breach.isBlank(), "the expected record exists");
                javax.swing.JTable t = s.table();
                t.setRowSelectionInterval(0, 0);
                assertFalse(Usability.rightRecordSelected(s.frame, breach), "★ the first record is not the breach record");
                for (int r = 0; r < t.getRowCount(); r++) {
                    for (int c = 0; c < t.getColumnCount(); c++) {
                        if ("RiskBreachEvent".equals(String.valueOf(t.getValueAt(r, c)))) t.setRowSelectionInterval(r, r);
                    }
                }
                assertTrue(Usability.rightRecordSelected(s.frame, breach), "★ the breach record is");
            });
        }
    }

    /**
     * ★ The witnesses, executed: a three-word label added to the start page puts S1 over its word budget; T7 with one
     * more required click (the person first clicks the question box) puts it over its act budget.
     */
    @Test
    @DisplayName("★ witness: one more label on the start page, or one more step in a task, breaks its budget")
    void aBudgetCatchesAnExtraLabelAndAnExtraStep(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        UsabilityTasks.StateMeasure plain = UsabilityTasks.startPage(tmp.resolve("plain"));
        UsabilityTasks.StateMeasure more = UsabilityTasks.startPage(tmp.resolve("more"), s -> {
            var hero = s.startPanel().prompt().getParent();
            hero.add(new JLabel("One more hint"), 0);
            hero.revalidate();
        });
        // its three words are counted (the taller hero may push a word of the page below the fold)
        assertTrue(more.words() > plain.words(), "the label's words are counted: " + plain.words() + " → " + more.words());
        List<String> broken = UsabilityBudgets.violations(List.of(more), List.of());
        assertFalse(broken.isEmpty(), "★ a label more is over the start page's budget");

        UsabilityTasks.Task longer = new UsabilityTasks.Task("T7", "Open the sample, one click longer", List.of("sample"),
                (run, dir) -> {
                    try (UsabilityTasks.Session s = new UsabilityTasks.Session(dir, UsabilityTasks.answering(), true)) {
                        StartPromptFrameTest.session(s.frame);
                        run.begin();
                        run.click("click the question box first", Usability.component(() -> s.startPanel().prompt().box(),
                                () -> StartPromptView.PLACEHOLDER));
                        run.click("open the sample project", Usability.anyButton("Sample project", "Open sample project"));
                        StartPromptFrameTest.awaitDemo(s.frame);
                        return run.endScreen(true);
                    }
                });
        Usability.Run r = UsabilityTasks.run(longer, tmp.resolve("T7-longer"));
        assertTrue(r.success, "the longer path still opens the sample: " + r.failure);
        assertEquals(UsabilityBudgets.ACTS.get("T7") + 1, r.totalActs(), "one more act is counted");
        assertFalse(UsabilityBudgets.violations(List.of(), List.of(r)).isEmpty(), "★ a step more is over T7's budget");

        // the old T6 route, still possible by hand: the switch, its confirmation, THEN Check connection — one act and one
        // hesitation more than the fix's route, so T6's act and hesitation budgets both catch a return to it
        UsabilityTasks.Task oldT6 = new UsabilityTasks.Task("T6", "Set up an external client, the switch first",
                List.of("client", "connect", "mcp"), (run, dir) -> {
                    try (UsabilityTasks.Session s = new UsabilityTasks.Session(dir, UsabilityTasks.answering(), true)) {
                        StartPromptFrameTest.session(s.frame);
                        run.begin();
                        run.click("choose your own AI client", Usability.button(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.CLIENT_SHORT));
                        run.click("set up my client", Usability.button(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.SET_UP_MY_CLIENT));
                        Usability.await("Screen 2b", () -> s.setup().open() && s.setup().step()
                                == telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step.CLIENT);
                        run.click("the switch", Usability.buttonInWindow(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.TITLE, telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.ACCESS_SWITCH));
                        run.click("confirm", Usability.buttonInWindow(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.ACCESS_CONFIRM_TITLE,
                                telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.ACCESS_CONFIRM_TURN_ON));
                        Usability.await("access on", () -> s.setup().external().access()
                                == telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Access.ON);
                        run.click("check the connection", Usability.buttonInWindow(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.TITLE,
                                telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.CHECK_CONNECTION));
                        Usability.await("checked", () -> s.setup().external().bridge()
                                != telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Bridge.CHECKING);
                        return run.endScreen(true);
                    }
                });
        Usability.Run old = UsabilityTasks.run(oldT6, tmp.resolve("T6-old"));
        assertTrue(old.success, "the switch-first route still works: " + old.failure);
        List<String> oldBroken = UsabilityBudgets.violations(List.of(), List.of(old));
        assertTrue(oldBroken.stream().anyMatch(v -> v.contains("acts > budget"))
                && oldBroken.stream().anyMatch(v -> v.contains("hesitations > budget")),
                "★ the old T6 route is over both budgets: " + oldBroken);
    }
}
