package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * M74 P3 (spec §5.7, §10 P3): the permission dialog in the REAL frame. A fake process ({@link FixtureAgent}) replays the
 * P3-0 capture — the real bridge, the real login, sanitised — whose recorded turns wait at their {@code can_use_tool}
 * until the analyser answers. Every press is direct ({@code doClick()}, the root pane's own Escape action, its default
 * button): posted key events and Robot presses are dropped on a Mac without a focus owner.
 */
class LocalAgentPermissionFrameTest {

    static final String DIALOG = "Claude Code asks for your approval";
    static final String RLO = new String(Character.toChars(0x202E));

    /**
     * ★ Witnesses: make Allow the default button — the default-button assertion fails; enable Allow at once — the 500 ms
     * assertion fails; show the input in a {@code JLabel} — the plain-text-component assertion fails.
     */
    @Test
    @DisplayName("★ the dialog alone: Deny is the default, Enter and Escape deny, Allow waits 500 ms, the input is escaped plain text")
    void theDialogAlone() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        String raw = "{\"title\":\"<html><b>x</b>\",\"path\":\"fdp." + RLO + "pdf\"}";
        var content = PermissionText.of(new LocalAgentTranscript.Permission("mcp__fluxtion-analyser-hosted__analyser_report",
                "fluxtion-analyser-hosted", "Analyser Report", raw), ToolClass.HOSTED_DESTRUCTIVE, "report");
        List<String> said = new ArrayList<>();
        LocalAgentPermissionDialog.Answers answers = new LocalAgentPermissionDialog.Answers() {
            @Override public void chosen(boolean allow) { said.add(allow ? "allow" : "deny"); }
            @Override public void stop() { said.add("stop"); }
        };
        for (String how : List.of("escape", "enter", "close", "allow", "stop")) {
            AtomicReference<LocalAgentPermissionDialog> d = new AtomicReference<>();
            onEdt(() -> {
                d.set(new LocalAgentPermissionDialog(null, content, 120_000, answers, false));
                d.get().setVisible(true);
            });
            LocalAgentPermissionDialog dialog = d.get();
            onEdt(() -> {
                assertSame(dialog.denyButton(), dialog.getRootPane().getDefaultButton(), "Deny is the default button");
                assertFalse(dialog.allowButton().isEnabled(), "Allow is not active when the dialog appears");
                assertEquals(0, dialog.allowButton().getMnemonic(), "Allow has no mnemonic");
                assertInstanceOf(JTextArea.class, dialog.inputArea(), "the input is in a plain text component");
                assertFalse(dialog.inputArea().isEditable());
                assertEquals(Boolean.TRUE, dialog.inputArea().getClientProperty(UiText.HTML_DISABLE));
                assertTrue(dialog.inputArea().getText().contains("<html><b>x</b>"), "markup shown as its characters");
                assertTrue(dialog.inputArea().getText().contains("\\u202E"), "the override shown escaped");
                assertFalse(dialog.inputArea().getText().contains(RLO));
                // review M74 usability #6: the title says what THIS call does (a report with a path writes a file)
                assertTrue(labels(dialog).contains("Claude Code asks to write a finding to \u201Cfdp.\\u202Epdf\u201D"),
                        labels(dialog).toString());
                assertTrue(labels(dialog).contains(PermissionText.INPUT_HEADING));
                assertTrue(labels(dialog).contains(PermissionText.ESCAPED_NOTE), "the dialog says it escaped them");
                assertTrue(dialog.countdownLabel().getText().contains("120 s"), dialog.countdownLabel().getText());
                // ★ review P3 N6: with the look and feel's defaultButtonFollowsFocus ON, a focused Allow still does not
                // become the default (witness: drop allow.setDefaultCapable(false) — the default becomes Allow)
                Object follows = javax.swing.UIManager.get("Button.defaultButtonFollowsFocus");
                javax.swing.UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);
                try {
                    for (var l : dialog.allowButton().getFocusListeners()) {
                        l.focusGained(new java.awt.event.FocusEvent(dialog.allowButton(), java.awt.event.FocusEvent.FOCUS_GAINED));
                    }
                    assertSame(dialog.denyButton(), dialog.getRootPane().getDefaultButton(),
                            "a focused Allow never becomes the default: Enter denies wherever the focus is");
                    assertFalse(dialog.allowButton().isDefaultCapable());
                } finally {
                    javax.swing.UIManager.put("Button.defaultButtonFollowsFocus", follows);
                }
            });
            switch (how) {
                case "escape" -> onEdt(() -> dialog.getRootPane().getActionMap().get("deny").actionPerformed(
                        new java.awt.event.ActionEvent(dialog, 0, "deny")));
                case "enter" -> onEdt(() -> dialog.getRootPane().getDefaultButton().doClick());
                case "close" -> onEdt(() -> dialog.dispatchEvent(new java.awt.event.WindowEvent(dialog,
                        java.awt.event.WindowEvent.WINDOW_CLOSING)));
                case "allow" -> {
                    onEdt(() -> dialog.allowButton().doClick());
                    assertTrue(said.isEmpty() || !said.get(said.size() - 1).equals("allow") || said.size() < 4,
                            "a click on the inactive Allow does nothing");
                    awaitEnabled(dialog.allowButton());
                    onEdt(() -> dialog.allowButton().doClick());
                }
                case "stop" -> onEdt(() -> dialog.stopButton().doClick());
                default -> { }
            }
            onEdt(dialog::close);
        }
        assertEquals(List.of("deny", "deny", "deny", "allow", "stop"), said, "Escape, Enter and closing deny; Allow allows");
    }

    /**
     * ★ Review PR151 F1 (2026-10-18): a dialog whose request's words are not held never enables Allow, after the delay
     * and on a click. Witness: enable Allow on the delay regardless of {@code held} — Allow becomes active.
     */
    @Test
    @DisplayName("★ missing words: Allow once is never enabled and a click on it does nothing")
    void missingWordsKeepAllowDisabled() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        var content = PermissionText.of(null, ToolClass.HOSTED_DESTRUCTIVE, "walk");
        List<String> said = new ArrayList<>();
        LocalAgentPermissionDialog.Answers answers = new LocalAgentPermissionDialog.Answers() {
            @Override public void chosen(boolean allow) { said.add(allow ? "allow" : "deny"); }
            @Override public void stop() { said.add("stop"); }
        };
        AtomicReference<LocalAgentPermissionDialog> d = new AtomicReference<>();
        onEdt(() -> {
            d.set(new LocalAgentPermissionDialog(null, content, 120_000, answers, false));
            d.get().setVisible(true);
        });
        LocalAgentPermissionDialog dialog = d.get();
        onEdt(dialog::allowDelayElapsed);
        Thread.sleep(LocalAgentPermissionDialog.ALLOW_DELAY_MS + 200L);
        onEdt(() -> {
            assertFalse(dialog.allowButton().isEnabled(), "Allow stays disabled");
            dialog.allowButton().doClick();
            dialog.denyButton().doClick();
        });
        onEdt(dialog::close);
        assertEquals(List.of("deny"), said, "only Deny answered");
    }

    static void awaitEnabled(AbstractButton b) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicReference<Boolean> on = new AtomicReference<>(false);
        while (System.nanoTime() < until) {
            onEdt(() -> on.set(b.isEnabled()));
            if (on.get()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Allow never became active");
    }

    static List<String> labels(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            if (k instanceof Container inner) out.addAll(labels(inner));
        }
        return out;
    }

    /**
     * ★ The real frame: Send → the node asks → the modal shows the recorded request → Escape denies (the CLI is told) →
     * Allow once (after 500 ms) sends the recorded input back → Stop in the dialog denies before the interrupt. Witness:
     * perform the dialog's buttons as their own decisions (answer the CLI from the frame) — the one-dispatch rule
     * forbids it, and the stdin order assertion catches a Stop that interrupts first.
     */
    @Test
    @DisplayName("★ in the frame: the recorded report request is asked, denied, allowed once, and Stop denies before it interrupts")
    void theDialogInTheFrame(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");

        FixtureAgent agent = FixtureAgent.ofP3("p3-0-default-real.jsonl");
        String before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        try {
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
            MainFrame f = made[0];
            ActionExecutor ex = (ActionExecutor) field(f, "actionExecutor");
            onEdt(() -> { f.setSize(1200, 800); f.setVisible(true); });
            onEdt(() -> ((AppConfig) field(f, "config")).claudeCodeAllowed = true);
            // the recorded report writes a PDF: file exchange is on, as in the P3 smoke — reported as a Settings save
            // reports it (review M74 usability #10a: with it off, the call is refused without a dialog)
            onEdt(() -> {
                ((AppConfig) field(f, "config")).assistantExports = true;
                try {
                    var changed = MainFrame.class.getDeclaredMethod("onConfigChanged");
                    changed.setAccessible(true);
                    changed.invoke(f);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            AssistantPanel assistant = (AssistantPanel) field(f, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));
            ClaudeCodeTabFrameTest.awaitState(f, s -> s.mode() == telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT, "Claude Code");
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog folder = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(folder, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(f, s -> "READY".equals(s.phase()), "READY");

            // turn 1: Escape denies
            send(panel, "Write the DEMO report");
            LocalAgentPermissionDialog d1 = awaitPermission();
            onEdt(() -> {
                assertTrue(labels(d1).stream().anyMatch(l -> l.startsWith("Claude Code asks to ")
                        && !l.contains("run analyser_report")), "this call's own sentence: " + labels(d1));
                assertTrue(d1.inputArea().getText().contains("\"title\": \"p3-zero-deny\""), d1.inputArea().getText());
            });
            // M78 D1 (§4.1 item 16): the header's chip word says it — the waiting sentence is gone
            ClaudeCodeTabFrameTest.awaitState(f, s -> LocalAgentState.Guide.CHIP_NEEDS_YOU.equals(
                    s.extra().guide().chipWord()), "the chip says needs you");
            onEdt(() -> assertTrue(assistant.profileButton().getText().contains(LocalAgentState.Guide.CHIP_NEEDS_YOU),
                    assistant.profileButton().getText()));
            onEdt(() -> assertFalse(((JTextArea) field(panel, "status")).isVisible(), "no status sentence beside it"));
            onEdt(() -> d1.getRootPane().getActionMap().get("deny").actionPerformed(new java.awt.event.ActionEvent(d1, 0, "deny")));
            awaitTurnEnded(f, 1);
            assertTrue(stdin(agent).stream().anyMatch(l -> l.contains("\"behavior\":\"deny\"")
                    && l.contains("Denied by the person in the analyser")), "the CLI was told: " + stdin(agent));
            assertFalse(d1.isDisplayable(), "the node's close effect disposed it");

            // turn 2: Allow once, after its delay, sends the recorded input back exactly
            send(panel, "Write it, allowed");
            LocalAgentPermissionDialog d2 = awaitPermission();
            awaitEnabled(d2.allowButton());
            onEdt(() -> d2.allowButton().doClick());
            awaitTurnEnded(f, 2);
            String allow = stdin(agent).stream().filter(l -> l.contains("\"behavior\":\"allow\"")).findFirst().orElseThrow();
            assertTrue(allow.contains("\"updatedInput\":{\"title\": \"p3-zero-allow\", \"path\": \"p3-zero-allow.pdf\"}"), allow);
            String transcript = awaitTranscript(panel, "approved by you");
            assertTrue(transcript.contains("denied by you"), transcript);
            assertTrue(transcript.contains("approved by you"), transcript);
            // M78 D1 (§5.3): the Approved note is dropped by the node; the Denied note of turn 1 is kept
            assertFalse(transcript.contains("You allowed analyser_report"), transcript);
            assertTrue(transcript.contains("Denied: "), transcript);

            // turn 3: Stop, from the dialog, denies the request before the interrupt
            agent.last().resumeOnAnswer = false;
            send(panel, "Write the third one");
            LocalAgentPermissionDialog d3 = awaitPermission();
            onEdt(() -> d3.stopButton().doClick());
            awaitTurnEnded(f, 3);
            List<String> in = stdin(agent);
            int deny = -1;
            int interrupt = -1;
            for (int i = 0; i < in.size(); i++) {
                if (in.get(i).contains("pressed Stop")) deny = i;
                if (interrupt < 0 && in.get(i).contains("\"interrupt\"")) interrupt = i;
            }
            assertTrue(deny >= 0 && interrupt > deny, "deny " + deny + " before interrupt " + interrupt);
            assertTrue(ClaudeCodeTabFrameTest.state(f).entries().stream().anyMatch(e -> "CANCELLED".equals(e.status())));
        } finally {
            System.setProperty("user.home", before);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    static void send(LocalAgentPanel panel, String text) throws Exception {
        onEdt(() -> {
            ((JTextArea) field(panel, "draft")).setText(text);
            ((JButton) field(panel, "send")).doClick();
        });
    }

    static String statusText(LocalAgentPanel panel) throws Exception {
        AtomicReference<String> s = new AtomicReference<>();
        onEdt(() -> s.set(((JTextArea) field(panel, "status")).getText()));
        return s.get();
    }

    static String transcript(LocalAgentPanel panel) throws Exception {
        AtomicReference<String> s = new AtomicReference<>();
        onEdt(() -> {
            var view = (javax.swing.text.JTextComponent) field(panel, "view");
            s.set(view.getText());
        });
        return s.get();
    }

    static String awaitTranscript(LocalAgentPanel panel, String expected) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String t = "";
        while (System.nanoTime() < until) {
            t = transcript(panel);
            if (t.contains(expected)) return t;
            Thread.sleep(50);
        }
        return t;
    }

    static List<String> stdin(FixtureAgent agent) {
        return new ArrayList<>(agent.last().stdin);
    }

    static LocalAgentPermissionDialog awaitPermission() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            for (Window w : Window.getWindows()) {
                if (w instanceof LocalAgentPermissionDialog d && d.isShowing() && DIALOG.equals(d.getTitle())) return d;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("no permission dialog");
    }

    static void awaitTurnEnded(MainFrame f, long ticket) throws Exception {
        ClaudeCodeTabFrameTest.awaitState(f, s -> s.openTicket() == 0
                && s.entries().stream().anyMatch(e -> LocalAgentState.ANSWER.equals(e.kind()) && e.ticket() == ticket
                && !"STREAMING".equals(e.status())), "turn " + ticket + " ended");
    }

    /** For the record: the session is decided in the processor; the frame only shows and reports. */
    static SessionDriver session(MainFrame f) {
        return (SessionDriver) field(f, "session");
    }
}
