package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentAdapter;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.ClaudeCodeTabFrameTest.*;

class LocalAgentSwitchModalOrderFrameTest {
    /**
     * PR #171 review (P2) witness: with Claude Code running, accepting the log-offer for a log whose records run
     * backwards queues BOTH the held switch's question and the time-order report. Before ModalSequence the report opened
     * inside the question's event loop, so both showed at once.
     */
    @Test void theRestartOfferAndTheTimeOrderReportAreShownOneAtATime(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path project = Files.createDirectories(tmp.resolve("DEMO"));
        Files.writeString(Files.createDirectories(project.resolve(".analyser")).resolve("project.fluxtion-settings"), "share.version=1\n");
        Path log = Files.writeString(project.resolve("DEMO.yaml"),
                "---\n#00:00:00.000 [DEMO] INFO DEMO\neventLogRecord:\n  logTime: 2000\n  event: DEMO\n---\n"
                + "#00:00:00.000 [DEMO] INFO DEMO\neventLogRecord:\n  logTime: 1000\n  event: DEMO\n---\n");
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        String before = System.getProperty("user.home");
        MainFrame[] made = new MainFrame[1];
        System.setProperty("user.home", home.toString());
        AtomicBoolean stacked = new AtomicBoolean();
        try {
            onEdt(() -> {
                MainFrame f = new MainFrame(); made[0] = f;
                f.localAgentLauncher = agent;
                f.localAgentEnvironmentForTest = Map.of("PATH", home.resolve(".local/bin").toString());
                try {
                    var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                    show.setAccessible(true); show.invoke(f, false);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                f.setSize(1200, 800); f.setVisible(true);
                ((AppConfig) field(f, "config")).claudeCodeAllowed = true;
                ((AssistantPanel) field(f, "assistantPanel")).chooseModeForTest(AssistantMode.LOCAL_AGENT);
            });
            MainFrame f = made[0];
            awaitState(f, s -> s.mode() == AssistantMode.LOCAL_AGENT, "mode");
            onEdt(() -> ((JButton) field(field(f, "localAgentPanel"), "start")).doClick());
            awaitState(f, s -> "READY".equals(s.phase()), "ready");
            onEdt(() -> f.openFile(log, OpenRequest.HUMAN));
            JDialog adoption = awaitDialog("Load this project?");
            onEdt(findButton(adoption, "Yes")::doClick);
            JDialog restart = awaitDialog("Claude Code is running");
            Thread.sleep(500);                                  // time for a stacked report to open, were it able to
            onEdt(() -> stacked.set(showing("Time-order report")));
            onEdt(findButton(restart, MainFrame.SWITCH_KEEP)::doClick);
            awaitDialog("Time-order report");                   // shown — after the question, not over it
        } finally {
            onEdt(() -> {
                for (Window w : Window.getWindows()) if (w instanceof JDialog) w.dispose();
                if (made[0] != null) {
                    var a = (LocalAgentAdapter) field(made[0], "localAgentAdapter");
                    if (a != null) a.shutdown();
                    made[0].dispose();
                }
            });
            System.setProperty("user.home", before);
        }
        assertFalse(stacked.get(), "the time-order report opened over the restart question");
    }

    private static boolean showing(String title) {
        for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) return true;
        return false;
    }
}
