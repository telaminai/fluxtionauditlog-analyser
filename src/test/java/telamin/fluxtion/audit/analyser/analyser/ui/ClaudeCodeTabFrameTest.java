package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * M74 review H8: the Claude Code tab in the REAL frame, with a FAKE process ({@link FixtureAgent}) replaying a recorded
 * CLI — no live {@code claude} runs. Start → the folder dialog → Yes → READY; a turn and Stop; and a programmatic
 * reveal of the Assistant tab never starts a process. Every press is a direct {@code doClick()} on the component:
 * posted key events and Robot presses are dropped on a Mac without a focus owner.
 *
 * <p>The home is isolated, so the analyser's REST switch is off: the session's bridge reaches a HOSTED-ONLY listener
 * (review H4), and the well-known endpoint file is never written.
 */
class ClaudeCodeTabFrameTest {

    static final String DIALOG_TITLE = "Claude Code — confirm this folder";

    @Test
    void aRecoveryDraftKeepsThePersonsWordsAndIsNeverSent(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        String before = System.getProperty("user.home");
        MainFrame[] made = new MainFrame[1];
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        String request = "Check the DEMO validation first.\nThen explain what changed.";
        System.setProperty("user.home", home.toString());
        try {
            onEdt(() -> {
                MainFrame f = made[0] = new MainFrame();
                f.localAgentLauncher = agent;
                f.setSize(1200, 800);
                f.setVisible(true);
                ((AppConfig) field(f, "config")).claudeCodeAllowed = true;
                ((AssistantPanel) field(f, "assistantPanel")).chooseModeForTest(AssistantMode.LOCAL_AGENT);
                var store = (telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript)
                        field(f, "localAgentStore");
                long ref = store.add(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript.Kind.USER,
                        request);
                try {
                    var method = MainFrame.class.getDeclaredMethod("performSessionEffect",
                            telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.class);
                    method.setAccessible(true);
                    method.invoke(f, new telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.PlaceRecoveryDraftEffect(
                            0, 0, ref, telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.RECOVERY_LINE));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            onEdt(() -> {
                var panel = (LocalAgentPanel) field(made[0], "localAgentPanel");
                assertEquals(request + "\n\n" + telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.RECOVERY_LINE,
                        ((JTextArea) field(panel, "draft")).getText(), "the original request is placed verbatim and unsent");
                assertTrue(agent.processes.isEmpty(), "placing a recovery draft starts no Claude process");
                assertEquals(0, ((SessionDriver) field(made[0], "session")).snapshot().localAgent().openTicket());
                ((JButton) field(panel, "send")).doClick();
            });
            awaitState(made[0], s -> "READY".equals(s.phase()) && s.openTicket() == 0
                            && s.entries().stream().anyMatch(e -> LocalAgentState.USER.equals(e.kind())),
                    "the recovered draft can be sent by the person");
            assertEquals(1, agent.processes.size());
            var lines = agent.last().stdin.stream().filter(line -> line.contains("\"type\":\"user\"")).toList();
            assertEquals(1, lines.size(), "only the person's Send writes the recovered question");
            assertTrue(lines.getFirst().contains("Check the DEMO validation first."));
        } finally {
            if (made[0] != null) onEdt(made[0]::dispose);
            System.setProperty("user.home", before);
        }
    }

    @Test
    @DisplayName("H8: Start → folder dialog → Yes → READY over a hosted-only bridge; Send then Stop; a reveal starts nothing")
    void startConfirmReadyStopAndReveal(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        // the fake process runs nothing, but the adapter still resolves `claude` and a bridge launcher first
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");

        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        // the turn never completes on its own, so Stop has something to interrupt
        agent.rewrite = l -> l.contains("command_lifecycle") && l.contains("completed") || l.contains("\"type\": \"result\"")
                ? null : l;

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
            onEdt(() -> {
                AppConfig cfg = (AppConfig) field(f, "config");
                cfg.claudeCodeAllowed = true;
                assertFalse(cfg.assistantActionsRest, "the isolated home has the external REST switch off");
            });
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            AssistantPanel assistant = (AssistantPanel) field(f, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));
            awaitState(f, s -> s.mode() == telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT,
                    "Claude Code chosen");

            // Start: the folder is not yet trusted, so the dialog asks; Yes trusts it and starts
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog dialog = awaitDialog(DIALOG_TITLE);
            assertEquals(0, agent.processes.size(), "nothing starts while the folder question is open");
            AbstractButton yes = findButton(dialog, LocalAgentFolderDialog.SAFE_TRUST);
            assertNotNull(yes, "the dialog offers Yes");
            onEdt(yes::doClick);
            LocalAgentState ready = awaitState(f, s -> "READY".equals(s.phase()), "READY after Yes");
            assertEquals("BOUND", ready.verdict());
            assertEquals(1, agent.processes.size(), "one process, started by the person's Start and Yes");
            onEdt(() -> assertTrue(((AppConfig) field(f, "config")).claudeCodeTrustedFolders.stream()
                    .anyMatch(p -> p.endsWith("proj")), "the confirmed folder is remembered on this machine"));

            // review H4: the bridge's listener is hosted-only — external clients refused, no well-known file
            onEdt(() -> {
                var server = (telamin.fluxtion.audit.analyser.analyser.net.ActionServer) field(f, "actionServer");
                assertNotNull(server, "a listener for the hosted bridge is up although the REST switch is off");
                assertFalse(server.externalAllowed(), "it is hosted-only");
            });
            assertFalse(Files.exists(home.resolve(".fluxtion-analyser/rest-endpoint")),
                    "no well-known endpoint file was written with REST off");

            // Send a turn, then Stop: one interrupt, the turn ends cancelled, the process stays
            onEdt(() -> {
                ((JTextArea) field(panel, "draft")).setText("Reply with exactly: one");
                ((JButton) field(panel, "send")).doClick();
            });
            awaitState(f, s -> s.openTicket() != 0, "the turn is open");
            onEdt(() -> ((JButton) field(panel, "stop")).doClick());
            LocalAgentState stopped = awaitState(f, s -> s.openTicket() == 0 && "READY".equals(s.phase()),
                    "the turn ended after Stop");
            assertEquals(1, agent.processes.get(0).stdin.stream().filter(l -> l.contains("\"interrupt\"")).count(),
                    "Stop sent one interrupt");
            assertTrue(stopped.entries().stream().anyMatch(e -> "CANCELLED".equals(e.status())), "the turn was cancelled");
            assertEquals(1, agent.processes.size());

            // New chat ends the process; then a programmatic reveal of the tab, and a programmatic selection, start nothing
            onEdt(() -> ((JButton) field(panel, "newChat")).doClick());
            awaitState(f, s -> "OFF".equals(s.phase()), "New chat stopped the process");
            onEdt(() -> {
                JTabbedPane tabs = (JTabbedPane) field(f, "sideTabs");
                tabs.setSelectedIndex(tabs.indexOfTab("Summary"));
            });
            onEdt(() -> {
                try {
                    var reveal = MainFrame.class.getDeclaredMethod("revealAssistant");
                    reveal.setAccessible(true);
                    reveal.invoke(f);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            onEdt(() -> {
                JTabbedPane tabs = (JTabbedPane) field(f, "sideTabs");
                tabs.setSelectedIndex(tabs.indexOfTab("Summary"));
                tabs.setSelectedComponent((Component) field(f, "assistantDock"));
            });
            Thread.sleep(300);
            onEdt(() -> { });
            assertEquals(1, agent.processes.size(), "revealing or selecting the Assistant tab never starts a process");
            assertEquals("OFF", state(f).phase());
        } finally {
            System.setProperty("user.home", before);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    static LocalAgentState state(MainFrame f) throws Exception {
        AtomicReference<LocalAgentState> s = new AtomicReference<>();
        onEdt(() -> s.set(((SessionDriver) field(f, "session")).snapshot().localAgent()));
        return s.get();
    }

    static LocalAgentState awaitState(MainFrame f, Predicate<LocalAgentState> done, String what) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        LocalAgentState s;
        do {
            s = state(f);
            if (done.test(s)) return s;
            Thread.sleep(40);
        } while (System.nanoTime() < until);
        throw new AssertionError(what + ": phase " + s.phase() + ", verdict " + s.verdict() + ", reason " + s.reason());
    }

    static JDialog awaitDialog(String title) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) return d;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("no dialog titled " + title);
    }

    static AbstractButton findButton(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (k instanceof Container inner) {
                AbstractButton found = findButton(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
