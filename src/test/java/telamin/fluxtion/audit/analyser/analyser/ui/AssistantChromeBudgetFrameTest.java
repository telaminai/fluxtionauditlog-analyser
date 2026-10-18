package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * ★ M78 TQ1 (spec-quiet-assistant-panel.md §6): the docked assistant's chrome budget, state by state, in the REAL frame at
 * the default 1200×800 with the panel docked, light and dark, driven with a FAKE Claude Code ({@link FixtureAgent}; no
 * live {@code claude}, no key read). Chrome is every visible label, button and text area outside the transcript and the
 * composer's own text, counted by {@link ChromeCount} as the UX review counted it.
 *
 * <ul>
 *   <li>Ready, Safe and Full: at most 15 words and 4 controls (the mode control, ✎, ⋯ and Send);</li>
 *   <li>a turn running, a permission waiting, answered: at most 15 words;</li>
 *   <li>Claude Code exited (error), the in-app assistant with no key, a restart offered: at most 20 words.</li>
 * </ul>
 *
 * <p>With {@code -Dm78.shots=<dir>} it also paints the panel in each state and theme there (review scratch, never
 * committed). Witnesses: re-add the model chip in {@code LocalAgentPanel.renderChips}, or show the word "Ready" (make the
 * status line visible whatever the banner) — the Ready budget fails and names the extra words.
 */
class AssistantChromeBudgetFrameTest {

    static final int READY_WORDS = 15;
    static final int READY_CONTROLS = 4;
    static final int BUSY_WORDS = 15;
    static final int ALERT_WORDS = 20;

    record Measured(String state, String theme, ChromeCount.Count count, int width, int height) {
    }

    private final List<Measured> measured = new ArrayList<>();
    private MainFrame frame;
    private AssistantPanel assistant;

    @Test
    @DisplayName("★ TQ1: the docked panel's chrome — Ready ≤15 words and ≤4 controls; running, permission, answered ≤15; error, no key, restart ≤20")
    void theChromeBudget(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        javax.swing.LookAndFeel lafBefore = javax.swing.UIManager.getLookAndFeel();
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
        try {
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
            assistant = (AssistantPanel) field(frame, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(frame, "localAgentPanel");
            ActionExecutor ex = (ActionExecutor) field(frame, "actionExecutor");
            onEdt(() -> { frame.setBounds(40, 40, 1200, 800); frame.setVisible(true); });
            settings(c -> {
                c.claudeCodeAllowed = true;
                c.assistantProfile = "SAFE";
                c.assistantExports = true;        // the recorded report writes a PDF (review M74 usability #10a)
            });
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));
            ClaudeCodeTabFrameTest.awaitState(frame, s -> s.mode() == AssistantMode.LOCAL_AGENT, "Claude Code");
            selectAssistantTab();

            // Ready, Safe
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog folder = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(folder, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(frame, s -> "READY".equals(s.phase()), "READY");
            measure("ready-safe");

            // a turn running: the fake's stdin waits, so the turn stays open
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            var bw = FixtureAgent.Proc.class.getDeclaredField("blockWrites");
            bw.setAccessible(true);
            bw.set(agent.last(), latch);
            LocalAgentPermissionFrameTest.send(panel, "Write the DEMO report");
            ClaudeCodeTabFrameTest.awaitState(frame, s -> s.openTicket() != 0, "answering");
            measure("running");

            // a permission waiting
            latch.countDown();
            LocalAgentPermissionDialog ask = LocalAgentPermissionFrameTest.awaitPermission();
            ClaudeCodeTabFrameTest.awaitState(frame, s -> LocalAgentState.Guide.CHIP_NEEDS_YOU.equals(
                    s.extra().guide().chipWord()), "needs you");
            measure("permission");
            LocalAgentPermissionFrameTest.awaitEnabled(ask.allowButton());
            onEdt(() -> ask.allowButton().doClick());
            LocalAgentPermissionFrameTest.awaitTurnEnded(frame, 1);
            LocalAgentPermissionFrameTest.awaitTranscript(panel, "approved by you");
            measure("answered");

            // a restart offered: Full Claude Code switched on while Safe analysis runs
            settings(c -> { c.claudeCodeFull = true; c.claudeCodeFullMode = "default"; });
            ClaudeCodeTabFrameTest.awaitState(frame, s -> s.extra().restart().offered(), "the restart is offered");
            measure("restart");

            // Ready, Full: Restart, then the full folder question
            onEdt(() -> panel.restartYesButton().doClick());
            JDialog full = ClaudeCodeTabFrameTest.awaitDialog(LocalAgentFolderDialog.FULL_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(full, LocalAgentFolderDialog.TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(frame, s -> "READY".equals(s.phase()) && s.posture().isFull(), "READY, Full");
            measure("ready-full");

            // Claude Code exited (error)
            FixtureAgent.Proc p = agent.last();
            var stderr = FixtureAgent.Proc.class.getDeclaredField("stderr");
            stderr.setAccessible(true);
            stderr.set(p, "Error: demo failure");
            var exit = FixtureAgent.Proc.class.getDeclaredMethod("exit", int.class);
            exit.setAccessible(true);
            exit.invoke(p, 1);
            ClaudeCodeTabFrameTest.awaitState(frame, s -> "EXITED".equals(s.verdict()), "exited");
            measure("error");

            // the in-app assistant with no key: M77-4 retired the cards, and the quick switch opens Set up AI for a
            // profile with no key stored — so the no-key state is what Settings ▸ Assistant can leave behind
            settings(c -> {
                c.claudeCodeAllowed = false;
                c.claudeCodeFull = false;
                c.assistantProfile = "IN_APP";
                c.apiKey = "";
            });
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.IN_APP));
            QuickSwitchFrameTest.await(frame, x -> x.profile()
                    == telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile.IN_APP && x.inAppNeedsKey(), "in-app, no key");
            UnifiedAssistantFrameTest.awaitMode(frame, AssistantMode.IN_APP);
            measure("no-key");
        } finally {
            System.setProperty("user.home", before);
            onEdt(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(lafBefore);
                } catch (Exception ignored) {
                    // the next suite applies its own
                }
            });
            if (frame != null) SwingUtilities.invokeAndWait(frame::dispose);
        }
        report();
        List<String> over = new ArrayList<>();
        for (Measured m : measured) {
            int words = switch (m.state()) {
                case "ready-safe", "ready-full" -> READY_WORDS;
                case "running", "permission", "answered" -> BUSY_WORDS;
                default -> ALERT_WORDS;
            };
            if (m.count().words() > words) over.add(m.state() + " (" + m.theme() + "): " + m.count().describe());
            // Ready is the four controls and nothing else (the proposal's 6 words): a model chip, a posture chip or the
            // word "Ready" there is exactly what §1 removed
            if (m.state().startsWith("ready") && m.count().seen().stream().anyMatch(x -> !x.kind().equals("button"))) {
                over.add(m.state() + " (" + m.theme() + ") shows more than its controls: " + m.count().describe());
            }
            if (m.state().startsWith("ready") && m.count().controls() > READY_CONTROLS) {
                over.add(m.state() + " (" + m.theme() + ") has more than " + READY_CONTROLS + " controls: "
                        + m.count().describe());
            }
            assertTrue(m.width() <= 260, "★ measured DOCKED at 1200×800: the panel is " + m.width() + " px wide");
        }
        assertEquals(List.of(), over, "★ the chrome budget (spec §6)");
        assertEquals(16, measured.size(), "eight states, in both themes");
    }

    private void selectAssistantTab() throws Exception {
        onEdt(() -> {
            JTabbedPane tabs = (JTabbedPane) field(frame, "sideTabs");
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tabs.getTitleAt(i).startsWith("Analyser assistant")) tabs.setSelectedIndex(i);
            }
        });
    }

    /** What Settings ▸ Assistant's OK does: write the switches, then report them. */
    private void settings(Consumer<AppConfig> change) throws Exception {
        onEdt(() -> {
            change.accept((AppConfig) field(frame, "config"));
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

    /** Count the chrome in this state, in each theme (and paint it, when asked). */
    private void measure(String state) throws Exception {
        for (String theme : List.of("Light", "Dark")) {
            onEdt(() -> {
                ThemeManager.apply(theme);
                for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
            });
            for (int i = 0; i < 6; i++) {
                onEdt(() -> frame.validate());
                Thread.sleep(60);
            }
            ChromeCount.Count[] c = new ChromeCount.Count[1];
            int[] size = new int[2];
            onEdt(() -> {
                c[0] = ChromeCount.of(assistant);
                size[0] = assistant.getWidth();
                size[1] = assistant.getHeight();
            });
            measured.add(new Measured(state, theme, c[0], size[0], size[1]));
            shot(state + "-" + theme.toLowerCase());
        }
    }

    private void shot(String name) throws Exception {
        String dir = System.getProperty("m78.shots");
        if (dir == null || dir.isBlank()) return;
        Files.createDirectories(Path.of(dir));
        onEdt(() -> {
            try {
                BufferedImage img = new BufferedImage(assistant.getWidth(), assistant.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                assistant.paint(g);
                for (Window w : Window.getWindows()) {
                    // a permission dialog over the panel is part of what the person sees
                    if (w instanceof LocalAgentPermissionDialog d && d.isShowing()) {
                        Point at = SwingUtilities.convertPoint(d.getRootPane(), 0, 0, assistant);
                        Graphics2D dg = (Graphics2D) g.create(at.x, at.y, d.getRootPane().getWidth(),
                                d.getRootPane().getHeight());
                        d.getRootPane().paint(dg);
                        dg.dispose();
                    }
                }
                g.dispose();
                ImageIO.write(img, "png", Path.of(dir, name + ".png").toFile());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private void report() throws Exception {
        StringBuilder sb = new StringBuilder();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Measured m : measured) {
            sb.append(m.state()).append(" [").append(m.theme()).append(", ").append(m.width()).append('×').append(m.height())
                    .append("]: ").append(m.count().describe()).append('\n');
            seen.put(m.state(), m.count().words() + " words / " + m.count().controls() + " controls");
        }
        System.out.println("M78 chrome budget\n" + sb);
        String dir = System.getProperty("m78.shots");
        if (dir != null && !dir.isBlank()) Files.writeString(Path.of(dir, "chrome-counts.txt"), sb.toString());
    }
}
