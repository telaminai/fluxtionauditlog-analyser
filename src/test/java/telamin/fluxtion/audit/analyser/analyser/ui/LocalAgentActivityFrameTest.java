package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * M81 A review P2: the real panel renders the node's stalled Guide, and its real buttons report the rendered scope.
 * Fixture process and isolated home only. Wrong-result control: disconnect Keep waiting's action listener; the
 * named acknowledgement assertion fails, rather than a test-only invocation of the intent making it pass.
 */
class LocalAgentActivityFrameTest {
    @Test
    @DisplayName("★ stalled Guide: Keep waiting acknowledges the rendered scope and hides the banner; Stop interrupts the turn")
    void stalledGuideButtonsAcknowledgeTheirRenderedScopeAndStop(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path profile = Files.createDirectories(tmp.resolve("DEMO-project/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        agent.rewrite = line -> line.contains("command_lifecycle") && line.contains("completed")
                || line.contains("\"type\": \"result\"") ? null : line;
        String before = System.getProperty("user.home");
        MainFrame[] made = new MainFrame[1];
        System.setProperty("user.home", home.toString());
        try {
            onEdt(() -> {
                MainFrame frame = made[0] = new MainFrame();
                frame.localAgentLauncher = agent;
                frame.setBounds(40, 40, 1200, 800);
                frame.setVisible(true);
                ((AppConfig) field(frame, "config")).claudeCodeAllowed = true;
                render((ActionExecutor) field(frame, "actionExecutor"), "open", Map.of("project", profile.toString()));
                ((AssistantPanel) field(frame, "assistantPanel")).chooseModeForTest(AssistantMode.LOCAL_AGENT);
            });
            MainFrame frame = made[0];
            LocalAgentPanel panel = (LocalAgentPanel) field(frame, "localAgentPanel");
            ClaudeCodeTabFrameTest.awaitState(frame, s -> s.mode() == AssistantMode.LOCAL_AGENT, "Claude Code chosen");
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog folder = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(folder, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(frame, s -> "READY".equals(s.phase()), "READY");
            onEdt(() -> {
                JTabbedPane tabs = (JTabbedPane) field(frame, "sideTabs");
                tabs.setSelectedComponent((Component) field(frame, "assistantDock"));
            });
            LocalAgentPermissionFrameTest.send(panel, "Reply with exactly: one");
            LocalAgentPermissionFrameTest.awaitTranscript(panel, "one");
            var running = ClaudeCodeTabFrameTest.awaitState(frame, s -> s.openTicket() != 0, "open turn");
            long generation = running.generation();
            long ticket = running.openTicket();
            SessionDriver driver = (SessionDriver) field(frame, "session");
            JPanel banner = (JPanel) field(panel, "activityStall");
            JButton keepWaiting = (JButton) field(panel, "keepWaiting");
            JButton stop = (JButton) field(panel, "stallStop");
            long observed = System.currentTimeMillis() + 1_000;
            onEdt(() -> {
                driver.post(new SessionEvents.LocalAgentProgressObserved(generation, ticket, observed));
                driver.post(new SessionEvents.LocalAgentActivityTick(generation, ticket, observed + 61_000));
                assertTrue(driver.snapshot().localAgent().extra().guide().stalled(), "the real node publishes a stalled Guide");
                assertTrue(banner.isShowing(), "the stalled banner is on the real visible panel");
                assertEquals("No activity for 1:01 — still working, or stalled?",
                        ((WrapLine) field(panel, "activityStallText")).getText());
                assertEquals("Keep waiting", keepWaiting.getText());
                assertEquals("Stop", stop.getText());
                assertTrue(keepWaiting.isShowing() && keepWaiting.isEnabled());
                assertTrue(stop.isShowing() && stop.isEnabled());
                assertTrue(driver.auditSink().matching("event: LocalAgentActivityAcknowledged").isEmpty());
                keepWaiting.doClick();
                var acknowledgements = driver.auditSink().matching("event: LocalAgentActivityAcknowledged");
                assertEquals(1, acknowledgements.size(), "Keep waiting posts exactly one acknowledgement fact");
                assertTrue(acknowledgements.getFirst().contains(
                                new SessionEvents.LocalAgentActivityAcknowledged(generation, ticket).toString()),
                        "the acknowledgement fact carries BOTH the rendered generation and openTicket");
                assertFalse(driver.snapshot().localAgent().extra().guide().stalled());
                assertFalse(banner.isVisible(), "Keep waiting's accepted fact hides the banner");
                assertEquals(ticket, driver.snapshot().localAgent().openTicket(), "Keep waiting does not end the turn");
                driver.post(new SessionEvents.LocalAgentActivityTick(generation, ticket, observed + 241_000));
                assertFalse(banner.isVisible(), "the same silence episode never repeats on a timer");
                driver.post(new SessionEvents.LocalAgentProgressObserved(generation, ticket, observed + 242_000));
                driver.post(new SessionEvents.LocalAgentActivityTick(generation, ticket, observed + 302_000));
                assertTrue(banner.isShowing(), "a fresh stream event then 60 seconds rearms the banner");
                stop.doClick();
            });
            var stopped = ClaudeCodeTabFrameTest.awaitState(frame, s -> s.openTicket() == 0 && "READY".equals(s.phase()),
                    "the stalled banner's Stop ends the turn");
            assertEquals(1, agent.last().stdin.stream().filter(line -> line.contains("\"interrupt\"")).count(),
                    "the banner's Stop really sends one interrupt to the fixture process");
            assertTrue(stopped.entries().stream().anyMatch(entry -> "CANCELLED".equals(entry.status())));
            onEdt(() -> assertFalse(banner.isVisible(), "a stopped turn has no stale stall banner"));
        } finally {
            if (made[0] != null) onEdt(made[0]::dispose);
            System.setProperty("user.home", before);
        }
    }
}
