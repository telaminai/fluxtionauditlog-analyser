package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * Robot re-walk (2026-10-18), the remaining first-run confusion points #1, #3 and #4, in the REAL frame at the default
 * 1200×800 with a FAKE Claude Code ({@link telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent};
 * no live {@code claude}) and an isolated home.
 */
class FirstRunPolishFrameTest {

    /** The in-app assistant in force with no key, as a Settings ▸ Assistant save reaches it: saved, then reported. */
    static void inAppWithNoKeyFromSettings(MainFrame f) throws Exception {
        onEdt(() -> {
            telamin.fluxtion.audit.analyser.analyser.config.AppConfig c =
                    (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f, "config");
            c.claudeCodeAllowed = false;
            c.claudeCodeFull = false;
            c.apiKey = null;
            c.assistantProfile = AssistantProfile.IN_APP.stored();
            try {
                var save = MainFrame.class.getDeclaredMethod("onConfigChanged");
                save.setAccessible(true);
                save.invoke(f);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            AssistantLiveFrameTest.panel(f).chooseModeForTest(AssistantMode.IN_APP);
        });
    }

    /** Every visible string under {@code root}: labels, buttons and read-only text. */
    static List<String> visibleWords(Component root) {
        List<String> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<String> out) {
        if (!c.isShowing()) return;
        if (c instanceof JLabel l && l.getText() != null) out.add(l.getText());
        if (c instanceof javax.swing.AbstractButton b && b.getText() != null) out.add(b.getText());
        if (c instanceof javax.swing.text.JTextComponent t && !t.isEditable()) out.add(t.getText());
        if (c instanceof Container ct && !(c instanceof javax.swing.text.JTextComponent)) {
            for (Component k : ct.getComponents()) collect(k, out);
        }
    }

    /**
     * ★ #4: the in-app assistant with no key — the robot's path: a question placed for Claude Code (Safe analysis), then
     * the in-app assistant in force with no key (M77-4: the quick switch sends that to Set up AI's key field, so the
     * state is reached as Settings ▸ Assistant reaches it — the profile saved, then reported). The ONE guide line is "No API key — Configure provider", Send is disabled, and
     * nothing says "Ready" or "Press Send". Witnesses: show the composer's hint whatever assistant it was placed for
     * ({@code renderHint} ignoring {@code hintMode}) — "Press Send to ask Claude" is still on screen; drop
     * {@code renderGuide}'s status rule — "Ready" shows above the line.
     */
    @Test
    @DisplayName("★ In-app with no key: one line \"No API key — Configure provider\", Send disabled, no \"Ready\", no \"Press Send\"")
    void inAppWithNoKeySaysOneTrueThing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (AssistantFirstRunFrameTest.Fresh f = new AssistantFirstRunFrameTest.Fresh(tmp, 1200, 800)) {
            f.openSampleLog(tmp);
            f.askFromTheRecord();
            f.applyProfile(AssistantProfile.SAFE);
            UnifiedAssistantFrameTest.awaitMode(f.frame, AssistantMode.LOCAL_AGENT);
            FirstRunLayoutFrameTest.select(f.frame, "Analyser assistant");
            onEdt(() -> assertTrue(f.assistant().composerHint().isShowing()
                    && f.assistant().composerHint().getText().equals("Draft — not sent"),     // M78 D1 item 37
                    "the question is placed for Claude Code, and the hint says so"));
            inAppWithNoKeyFromSettings(f.frame);
            UnifiedAssistantFrameTest.awaitMode(f.frame, AssistantMode.IN_APP);
            var profile = QuickSwitchFrameTest.await(f.frame, x -> x.inAppNeedsKey(), "the in-app assistant has no key");
            assertEquals(AiSetup.IN_APP_NO_KEY, profile.status());
            FirstRunLayoutFrameTest.settle();
            onEdt(() -> {
                AssistantPanel a = f.assistant();
                // M78 D2 (§4.1 item 26): one conditional banner — the node's sentence and Add key…
                assertEquals("No API key — nothing is sent. Add key…", a.guideLine(), "★ the one guide line");
                JButton configure = (JButton) field(a, "configure");
                assertTrue(configure.isShowing(), "Configure provider is on the line");
                assertFalse(a.sendButton().isEnabled(), "★ Send cannot be pressed");
                List<String> words = visibleWords(a);
                assertTrue(words.stream().noneMatch(w -> w.contains("Press Send") || w.contains("Draft — not sent")), "★ no Claude Code draft hint: " + words);
                assertTrue(words.stream().noneMatch(w -> w.strip().equals("Ready")), "★ no \"Ready\": " + words);
                assertEquals(1, words.stream().filter(w -> w.contains("No API key")).count(),
                        "★ said once: " + words);
            });
            // the line and its button are shown whole at 1200×800 docked, and popped out
            FirstRunLayoutFrameTest.assertFits(f.assistant(), "with no API key, docked at 1200×800");
            FirstRunLayoutFrameTest.popOut(f.frame);
            FirstRunLayoutFrameTest.assertFits(f.assistant(), "with no API key, popped out");
        }
    }

    /** The snapshot the surfaces render. */
    static telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot snapshot(MainFrame f) throws Exception {
        return StartPromptFrameTest.session(f).snapshot();
    }

    static void await(MainFrame f, java.util.function.Predicate<telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot> done,
                      String what) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            if (done.test(snapshot(f))) return;
            Thread.sleep(40);
        }
        fail("timed out waiting: " + what);
    }

    /**
     * ★ #1: after an answer, a spotlight that needs another tab takes the canvas there — and the canvas says where the
     * answer is, with ONE action back to it that the spotlight does not eat. Popped out, the answer never moved, so
     * nothing is offered. Witnesses: drop {@code reportCanvasLeftAssistant} from {@code applySpotlight} — no way back
     * shows; drop the way back from {@code assistantPassThrough} — the overlay takes the press.
     */
    @Test
    @DisplayName("★ a spotlight on another tab after an answer: \"↩ Back to the answer\" shows, and one press shows the answer")
    void theAnswerIsOneActionAway(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (AssistantFirstRunFrameTest.Fresh f = new AssistantFirstRunFrameTest.Fresh(tmp, 1200, 800)) {
            f.openSampleLog(tmp);
            f.askFromTheRecord();
            f.applyProfile(AssistantProfile.SAFE);
            FirstRunLayoutFrameTest.select(f.frame, "Analyser assistant");
            onEdt(() -> f.claude().sendButton().doClick());
            ClaudeCodeTabFrameTest.awaitState(f.frame, x -> x.entries().stream()
                    .anyMatch(e -> telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState.ANSWER.equals(e.kind())),
                    "Claude answered");
            FirstRunLayoutFrameTest.select(f.frame, "Analyser assistant");
            Object[] lit = new Object[1];
            onEdt(() -> lit[0] = AsyncOpenInterleavingFrameTest.render(f.ex, "spotlight",
                    java.util.Map.of("target", "topology", "caption", "the topology")));
            assertTrue(String.valueOf(lit[0]).contains("ok=true"), "the spotlight was lit: " + lit[0]);
            await(f.frame, x -> x.assistant().answerBehindTab(), "the answer is behind the Topology tab");
            FirstRunLayoutFrameTest.settle();
            onEdt(() -> {
                javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) field(f.frame, "sideTabs");
                assertEquals("Topology", tabs.getTitleAt(tabs.getSelectedIndex()), "the spotlight shows what it points at");
                assertTrue(f.frame.answerAwayBar().isShowing(), "★ the canvas says where the answer is");
                JButton back = f.frame.answerAwayButton();
                assertTrue(back.isShowing() && back.isEnabled(), "★ one action back");
                assertTrue(FirstRunPolishFrameTest.visibleWords(f.frame.answerAwayBar()).stream()
                        .anyMatch(w -> w.contains("the answer is in Analyser assistant")));
                SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
                assertTrue(overlay.isLit(), "the spotlight still stands");
                java.util.List<String> cut = TextFit.problems(f.frame.answerAwayBar());
                assertTrue(cut.isEmpty(), "★ the way back is shown whole at 1200×800: " + cut);
                java.awt.Point mid = javax.swing.SwingUtilities.convertPoint(back, back.getWidth() / 2, back.getHeight() / 2,
                        overlay);
                assertFalse(overlay.contains(mid.x, mid.y), "★ the press reaches the button, not the spotlight");
                back.doClick();
            });
            FirstRunLayoutFrameTest.settle();
            await(f.frame, x -> !x.assistant().answerBehindTab(), "back at the answer");
            FirstRunLayoutFrameTest.settle();
            onEdt(() -> {
                assertTrue(f.assistant().conversationView().isShowing(), "★ one press: the answer is on screen");
                assertFalse(f.frame.answerAwayBar().isShowing(), "and the way back goes");
            });
            // popped out: the answer stays in its own window — nothing to offer
            FirstRunLayoutFrameTest.popOut(f.frame);
            onEdt(() -> AsyncOpenInterleavingFrameTest.render(f.ex, "spotlight",
                    java.util.Map.of("target", "topology", "caption", "the topology")));
            FirstRunLayoutFrameTest.settle();
            assertFalse(snapshot(f.frame).assistant().answerBehindTab(), "★ popped out: nothing is behind a tab");
            onEdt(() -> assertFalse(f.frame.answerAwayBar().isShowing()));
        }
    }

    /**
     * ★ #3: a start-page question about evidence with no log open — nothing opens and nothing is placed; the page says
     * "No log is open — the answer will be general" with Open the sample, Open a log… and Ask anyway. Ask anyway goes on
     * as before (Set up AI, as for any first question); Open the sample opens it and then goes on. Witness: drop the
     * {@code holds} check from {@code AiSetup.onStartPageSent} — the wizard opens at the first press.
     */
    @Test
    @DisplayName("★ start page, no log open, \"What happened here?\": one line with three actions; nothing placed until one is pressed")
    void noLogOpenSaysSoFirst(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        // the no-log line first, then the Set up AI wizard
        for (StartPromptView.NoLogAction act : new StartPromptView.NoLogAction[]{StartPromptView.NoLogAction.ASK_ANYWAY,
                StartPromptView.NoLogAction.OPEN_SAMPLE}) {
            Path dir = java.nio.file.Files.createDirectories(tmp.resolve(act.name()));
            try (AssistantFirstRunFrameTest.Fresh f = new AssistantFirstRunFrameTest.Fresh(dir, 1200, 800)) {
                StartPromptFrameTest.showStart(f.frame);
                StartPromptFrameTest.session(f.frame);
                onEdt(() -> {
                    StartPromptView prompt = StartPromptFrameTest.startPanel(f.frame).prompt();
                    prompt.box().setText("What happened here?");
                    prompt.actionButton().doClick();
                });
                await(f.frame, x -> x.startPage().noLogHeld() != 0, "held");
                FirstRunLayoutFrameTest.settle();
                StartPromptView prompt = StartPromptFrameTest.startPanel(f.frame).prompt();
                onEdt(() -> {
                    assertTrue(prompt.noLogRow().isShowing(), "★ the page's one line");
                    assertTrue(visibleWords(prompt.noLogRow()).contains(
                            telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint.HINT));
                    for (var a : StartPromptView.NoLogAction.values()) {
                        assertTrue(prompt.noLogButton(a).isShowing(), "★ " + a + " is offered");
                    }
                    assertTrue(f.assistant().draftText().isBlank(), "★ nothing is placed");
                });
                var held = snapshot(f.frame);
                assertFalse(held.aiSetup().open(), "★ the Set up AI wizard does not open before the person acts");
                assertEquals(0, f.agent.processes.size(), "★ nothing started, nothing sent");
                onEdt(() -> prompt.noLogButton(act).doClick());
                if (act == StartPromptView.NoLogAction.OPEN_SAMPLE) {
                    await(f.frame, x -> x.logOpen(), "the sample's log opened");
                }
                await(f.frame, x -> x.startPage().noLogHeld() == 0 && x.aiSetup().open(),
                        act + ": the question goes on — the wizard opens for it, as for any first question");
                assertEquals(telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.PendingKind.START_BOX,
                        snapshot(f.frame).aiSetup().pending().kind(), "★ the question waits behind the wizard");
                assertTrue(f.assistant().draftText().isBlank(), "★ placed only after Finish");
                assertEquals(act == StartPromptView.NoLogAction.OPEN_SAMPLE, snapshot(f.frame).logOpen(),
                        "Ask anyway opens nothing; Open the sample opens it");
                assertEquals(0, f.agent.processes.size(), "still nothing sent");
            }
        }
    }

    /**
     * ★ Robot walk (P3, shot 35): Open a log… then Cancel in the chooser left Open the sample and Open a log… DISABLED with
     * the line still shown — a dead end. Now the cancel is reported, the line returns to offering, and both opens are
     * usable: Open the sample opens it and the question goes on. Witness: drop the {@code noLogOpenEnded} report from the
     * LOG_FLOW branch of {@code MainFrame.runStartPageFlow} — the buttons stay disabled.
     */
    @Test
    @DisplayName("★ start page, no log open: Open a log… then Cancel — the line stays, both opens are enabled, Open the sample works")
    void cancelledChooserLeavesTheOffer(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (AssistantFirstRunFrameTest.Fresh f = new AssistantFirstRunFrameTest.Fresh(tmp, 1200, 800)) {
            StartPromptFrameTest.showStart(f.frame);
            StartPromptFrameTest.session(f.frame);
            onEdt(() -> {
                StartPromptView prompt = StartPromptFrameTest.startPanel(f.frame).prompt();
                prompt.box().setText("What happened here?");
                prompt.actionButton().doClick();
            });
            await(f.frame, x -> x.startPage().noLogHeld() != 0, "held");
            FirstRunLayoutFrameTest.settle();
            StartPromptView prompt = StartPromptFrameTest.startPanel(f.frame).prompt();
            java.util.Set<java.awt.Window> before = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            onEdt(() -> { for (java.awt.Window w : java.awt.Window.getWindows()) if (w.isShowing()) before.add(w); });
            javax.swing.SwingUtilities.invokeLater(prompt.noLogButton(StartPromptView.NoLogAction.OPEN_LOG)::doClick);
            javax.swing.JFileChooser chooser = awaitChooser(before);
            assertTrue(snapshot(f.frame).startPage().noLogOpening(), "the chooser is up: the line says it is opening");
            onEdtLater(chooser::cancelSelection);
            await(f.frame, x -> x.startPage().noLogHeld() != 0 && !x.startPage().noLogOpening(),
                    "★ the cancel returned the line to offering");
            FirstRunLayoutFrameTest.settle();
            onEdt(() -> {
                assertTrue(prompt.noLogRow().isShowing(), "★ the line stays");
                for (var a : StartPromptView.NoLogAction.values()) {
                    JButton b = prompt.noLogButton(a);
                    assertTrue(b.isShowing() && b.isEnabled(), "★ " + a + " is usable after the cancel");
                }
            });
            assertFalse(snapshot(f.frame).logOpen(), "the cancel opened nothing");
            onEdt(() -> prompt.noLogButton(StartPromptView.NoLogAction.OPEN_SAMPLE).doClick());
            await(f.frame, x -> x.logOpen(), "★ Open the sample works after the cancel");
            await(f.frame, x -> x.startPage().noLogHeld() == 0 && x.aiSetup().open(),
                    "the question goes on about the sample — the wizard opens for it (M77-4: the cards are gone)");
            assertEquals(0, f.agent.processes.size(), "nothing sent");
        }
    }

    /** The new file chooser among the windows showing now. */
    private static javax.swing.JFileChooser awaitChooser(java.util.Set<java.awt.Window> before) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Thread.sleep(20);
            java.util.concurrent.atomic.AtomicReference<javax.swing.JFileChooser> seen =
                    new java.util.concurrent.atomic.AtomicReference<>();
            onEdtLater(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w.isShowing() && w instanceof javax.swing.JDialog d && !before.contains(d)) {
                        javax.swing.JFileChooser c = findChooser(d.getContentPane());
                        if (c != null) seen.set(c);
                    }
                }
            });
            if (seen.get() != null) return seen.get();
        }
        fail("Open a log… opened no file chooser");
        return null;
    }

    private static javax.swing.JFileChooser findChooser(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof javax.swing.JFileChooser chooser) return chooser;
            if (child instanceof Container inner) {
                javax.swing.JFileChooser deeper = findChooser(inner);
                if (deeper != null) return deeper;
            }
        }
        return null;
    }

    /** Runs on the EDT without blocking behind a modal dialog's nested event loop. */
    private static void onEdtLater(Runnable r) throws Exception {
        var task = new java.util.concurrent.FutureTask<Void>(r, null);
        javax.swing.SwingUtilities.invokeLater(task);
        task.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
