package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * Owner, 2026-10-18 (the M74 hands-on test: "a problem when toggling an assistant setting"), in the REAL frame with a
 * FAKE Claude Code ({@link FixtureAgent}; no live {@code claude}). Each assistant switch is toggled while a session is
 * idle and while Claude is answering, then put back, exactly as a Settings save reports it ({@code onConfigChanged}).
 *
 * <p>What the probe found before this change: <i>Allow</i> off stopped the session (right) but, switched back on, the
 * panel still said it was switched off; <i>Full</i> on stopped an idle session, or doomed one mid-turn, even when put
 * back before the turn ended; the model and the spend limit changed nothing and said nothing, so they silently did not
 * take effect; External MCP/REST access leaves the session alone (right). Now a change that may wait is OFFERED and the
 * session runs on; a change that takes a power away stops it at once; a change put back withdraws the offer.
 */
class AssistantSettingsTogglesFrameTest {

    /** A real frame with a fake Claude Code, a project, Claude Code mode, and a session READY (and answering, if asked). */
    static final class Session implements AutoCloseable {
        final MainFrame frame;
        final FixtureAgent agent;
        final LocalAgentPanel panel;
        private final String home;

        Session(Path tmp, boolean answering) throws Exception {
            Path home = Files.createDirectories(tmp.resolve("home"));
            Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
            Files.writeString(claude, "#!/bin/sh\nexit 0\n");
            Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
            Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
            Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
            Files.writeString(profile, "share.version=1\n");
            agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
            agent.conformInit = true;
            // a turn never completes on its own, so "answering" lasts until the test ends it
            agent.rewrite = l -> l.contains("command_lifecycle") && l.contains("completed")
                    || l.contains("\"type\": \"result\"") ? null : l;
            this.home = System.getProperty("user.home");
            System.setProperty("user.home", home.toString());
            MainFrame[] made = new MainFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.localAgentLauncher = agent;
                try {
                    var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                    show.setAccessible(true);
                    show.invoke(f, false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                made[0] = f;
            });
            frame = made[0];
            ActionExecutor ex = (ActionExecutor) field(frame, "actionExecutor");
            onEdt(() -> { frame.setSize(1200, 820); frame.setVisible(true); });
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            // Safe analysis chosen on this machine (as its card writes it), so the cards never open by themselves here
            settings(c -> {
                c.claudeCodeAllowed = true;
                c.assistantProfile = "SAFE";
            });
            panel = (LocalAgentPanel) field(frame, "localAgentPanel");
            onEdt(() -> ((AssistantPanel) field(frame, "assistantPanel")).chooseModeForTest(AssistantMode.LOCAL_AGENT));
            ClaudeCodeTabFrameTest.awaitState(frame, s -> s.mode() == AssistantMode.LOCAL_AGENT, "Claude Code chosen");
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog dialog = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            AbstractButton yes = ClaudeCodeTabFrameTest.findButton(dialog, LocalAgentFolderDialog.SAFE_TRUST);
            onEdt(yes::doClick);
            ClaudeCodeTabFrameTest.awaitState(frame, s -> "READY".equals(s.phase()), "READY");
            if (answering) ask("Reply with exactly: one");
        }

        void ask(String words) throws Exception {
            onEdt(() -> {
                ((JTextArea) field(panel, "draft")).setText(words);
                ((JButton) field(panel, "send")).doClick();
            });
            ClaudeCodeTabFrameTest.awaitState(frame, s -> s.openTicket() != 0, "Claude is answering");
        }

        AppConfig config() {
            return (AppConfig) field(frame, "config");
        }

        /** What Settings ▸ Assistant's OK does: write the switches, then report them. */
        void settings(Consumer<AppConfig> change) throws Exception {
            onEdt(() -> {
                change.accept(config());
                try {
                    var m = MainFrame.class.getDeclaredMethod("onConfigChanged");
                    m.setAccessible(true);
                    m.invoke(frame);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            onEdt(() -> { });
        }

        LocalAgentState state() throws Exception {
            return ClaudeCodeTabFrameTest.state(frame);
        }

        int terminated() {
            return agent.processes.stream().mapToInt(p -> p.terminated).sum();
        }

        String statusLine() throws Exception {
            String[] s = new String[1];
            onEdt(() -> s[0] = ((WrapLine) field(panel, "status")).getText());
            return s[0];
        }

        @Override
        public void close() throws Exception {
            System.setProperty("user.home", home);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    record Toggle(String name, Consumer<AppConfig> change, Consumer<AppConfig> back) { }

    /**
     * ★ Each switch, idle and mid-turn, then put back. Witnesses: make {@code consentWhileRunning} kill for an offerable
     * change (as before) — the "keeps running" assertions fail for Full, the model and the spend limit; drop the
     * withdrawal branch — the "put back" assertions fail; drop the stale-verdict clearing in {@code onConsentChanged} —
     * the Allow row's "allowed again" assertion fails.
     */
    @Test
    @DisplayName("★ every assistant switch toggled idle and mid-turn: Allow off stops at once; Full, the model and the spend limit are offered; REST leaves it alone; put back, nothing is left")
    void everySwitchIdleAndMidTurn(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        int n = 0;
        for (boolean answering : new boolean[]{false, true}) {
            String when = answering ? " (Claude answering)" : " (idle)";

            // Allow the analyser to start Claude Code: off stops at once, never offered; on again, the panel says so
            try (Session s = new Session(tmp.resolve("t" + n++), answering)) {
                s.settings(c -> c.claudeCodeAllowed = false);
                LocalAgentState off = ClaudeCodeTabFrameTest.awaitState(s.frame, x -> "OFF".equals(x.phase()), "stopped");
                assertEquals("SWITCHED_OFF", off.verdict(), when);
                assertEquals(1, s.terminated(), "★ Allow off stops the process at once" + when);
                assertFalse(off.extra().restart().any(), "never offered" + when);
                s.settings(c -> c.claudeCodeAllowed = true);
                assertEquals("", s.state().verdict(), "★ allowed again: no longer said to be switched off" + when);
                assertEquals(s.state().extra().guide().instruction(), s.statusLine(), "the one line");
                assertTrue(s.statusLine().toLowerCase(java.util.Locale.ROOT).contains("allowed again"), s.statusLine());
            }

            for (Toggle t : java.util.List.of(
                    new Toggle("Full Claude Code on", c -> { c.claudeCodeFull = true; c.claudeCodeFullMode = "default"; },
                            c -> c.claudeCodeFull = false),
                    new Toggle("the model", c -> c.claudeCodeModel = "opus", c -> c.claudeCodeModel = "sonnet"),
                    new Toggle("the spend limit", c -> c.claudeCodeBudgetUsd = 3.0, c -> c.claudeCodeBudgetUsd = null))) {
                try (Session s = new Session(tmp.resolve("t" + n++), answering)) {
                    s.settings(t.change());
                    LocalAgentState offered = s.state();
                    assertTrue(offered.extra().restart().offered(), "★ " + t.name() + " is offered" + when);
                    assertEquals(0, s.terminated(), "★ " + t.name() + ": nothing is stopped" + when);
                    assertEquals(answering, offered.busy(), "the turn, if any, goes on" + when);
                    onEdt(() -> {
                        assertTrue(s.panel.restartBanner().isShowing(), "the offer shows in the panel" + when);
                        assertTrue(s.panel.restartYesButton().isShowing() && s.panel.restartLaterButton().isShowing());
                    });
                    s.settings(t.back());
                    LocalAgentState back = s.state();
                    assertFalse(back.extra().restart().any(), "★ " + t.name() + " put back: the offer is withdrawn" + when);
                    assertEquals(0, s.terminated(), "and still nothing is stopped" + when);
                    assertTrue(back.running(), when);
                    onEdt(() -> assertFalse(s.panel.restartBanner().isShowing()));
                }
            }

            // External MCP/REST access: the session keeps its own connection; nothing to restart
            try (Session s = new Session(tmp.resolve("t" + n++), answering)) {
                s.settings(c -> c.assistantActionsRest = true);
                assertFalse(s.state().extra().restart().any(), "REST is not the session's setting" + when);
                assertEquals(0, s.terminated(), when);
                s.settings(c -> c.assistantActionsRest = false);
                assertTrue(s.state().running(), when);
                assertEquals(0, s.terminated(), when);
            }
        }
    }

    /**
     * ★ Yes / Later in the panel. Witnesses: kill on Later — the "still running" assertion fails; drop the chip from
     * {@code renderChips} — the chip assertion fails; drop {@code start(request, c)} in {@code restartNow} — no second
     * process starts.
     */
    @Test
    @DisplayName("★ the offer: Later keeps it running with the chip; the chip offers again; Yes restarts it on the new model")
    void laterThenTheChipThenYes(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Session s = new Session(tmp, false)) {
            s.settings(c -> c.claudeCodeModel = "opus");
            onEdt(() -> {
                assertTrue(s.panel.restartText().getText().startsWith("Settings changed — restart Claude Code now to "
                        + "apply them?"), s.panel.restartText().getText());
                assertTrue(s.panel.restartText().getText().contains("the model (sonnet → opus)"));
                assertEquals(LocalAgentSession.RESTART_STATUS, ((javax.swing.JLabel) field(s.frame, "status")).getText(),
                        "the main window says where to answer");
                s.panel.restartLaterButton().doClick();
            });
            LocalAgentState later = ClaudeCodeTabFrameTest.awaitState(s.frame, x -> x.extra().restart().deferred(), "Later");
            assertTrue(later.running(), "★ Later: still running on the previous settings");
            onEdt(() -> assertEquals(LocalAgentSession.LATER_STATUS,
                    ((javax.swing.JLabel) field(s.frame, "status")).getText(), "the main window's line follows"));
            assertEquals(0, s.terminated());
            onEdt(() -> {
                assertFalse(s.panel.restartBanner().isShowing(), "the prompt goes");
                assertTrue(s.panel.restartChip().isShowing(), "★ and the chip stays");
                assertEquals(LocalAgentPanel.RESTART_CHIP, s.panel.restartChip().getText());
                s.panel.restartChip().doClick();
            });
            ClaudeCodeTabFrameTest.awaitState(s.frame, x -> x.extra().restart().offered(), "the chip offers again");
            onEdt(() -> s.panel.restartYesButton().doClick());
            ClaudeCodeTabFrameTest.awaitState(s.frame, x -> "READY".equals(x.phase()) && x.generation() == 2,
                    "restarted and ready");
            assertEquals(2, s.agent.processes.size(), "★ a second process");
            assertEquals(1, s.agent.processes.get(0).terminated, "the first was stopped");
            assertTrue(String.join(" ", lastCommand(s.agent)).contains("--model opus"), "★ on the new model");
            assertFalse(s.state().extra().restart().any());
            onEdt(() -> assertFalse(s.panel.restartChip().isShowing(), "the chip goes with the restart"));
        }
    }

    static java.util.List<String> lastCommand(FixtureAgent agent) {
        try {
            var f = FixtureAgent.class.getDeclaredField("commands");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            var all = (java.util.List<java.util.List<String>>) f.get(agent);
            return all.get(all.size() - 1);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * ★ The quick switch is a settings change too (M77-4: the cards' Off row): Off stops at once (never offered).
     * Witness: offer withdrawal of consent — the kill assertion fails.
     */
    @Test
    @DisplayName("★ Assistant ▾ ▸ Off stops a running session at once, with no offer")
    void theOffRowStopsAtOnce(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Session s = new Session(tmp, false)) {
            QuickSwitchFrameTest.choose(s.frame, AssistantLiveFrameTest.panel(s.frame), AssistantProfile.OFF);
            LocalAgentState off = ClaudeCodeTabFrameTest.awaitState(s.frame, x -> "OFF".equals(x.phase()), "stopped");
            assertEquals(1, s.terminated(), "★ stopped at once");
            assertFalse(off.extra().restart().any(), "never offered");
        }
    }
}
