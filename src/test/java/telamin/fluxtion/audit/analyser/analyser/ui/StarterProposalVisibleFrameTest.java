package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * Owner hands-on test 2026-10-18b: Claude Code proposed a starter, the session held it as the person's to answer, and the
 * person "can't see a prompt in the analyser" — the only surface was the banner in the assistant panel, which was a side
 * tab behind another (a spotlight on {@code tab:topology} or {@code tab:report} selects one). {@link StarterProposalFrameTest}
 * passed throughout because it opens the review by calling {@code reviewProposal} directly.
 *
 * <p>This test goes through the REAL surfaces only: the proposal arrives over the hosted action endpoint, exactly as the
 * onboard Claude sends it, and the review is reached by finding and clicking a Review… button that is SHOWING on screen.
 * No method of the review is called. Witness: never show the frame's proposal strip (keep {@code proposalCue} hidden in
 * {@code MainFrame.renderProposalCue}) — the strip assertion fails, because the banner is behind the Topology tab.
 */
class StarterProposalVisibleFrameTest {

    @Test
    @DisplayName("★ a starter proposed over the hosted endpoint is visible with the assistant behind another tab; its Review… opens the review")
    void aProposedStarterCanBeSeenAndReviewed(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");

        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        StarterProposalFrameTest.Stub stub = new StarterProposalFrameTest.Stub();
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();

        String before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.localAgentLauncher = agent;
                f.starterSource = stub;
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
            onEdt(() -> { f.setSize(1280, 820); f.setVisible(true); });
            onEdt(() -> ((AppConfig) field(f, "config")).claudeCodeAllowed = true);
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            AssistantPanel assistant = (AssistantPanel) field(f, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));

            // a session: Start, the folder's Yes, one turn — admitted, so the hosted endpoint takes propose
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog folder = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(folder, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(f, s -> "READY".equals(s.phase()), "READY");
            StarterProposalFrameTest.awaitSnapshot(f, s -> "read".equals(s.starter().catalogue()), "the catalogue read");
            onEdt(() -> {
                ((JTextArea) field(panel, "draft")).setText("Reply with exactly: one");
                ((JButton) field(panel, "send")).doClick();
            });
            ClaudeCodeTabFrameTest.awaitState(f, s -> s.admittedGeneration() != 0 && s.openTicket() == 0, "admitted");

            // the person is looking at the graph: the assistant's tab is behind Topology (what a spotlight on it does)
            JTabbedPane tabs = (JTabbedPane) field(f, "sideTabs");
            JPanel dock = (JPanel) field(f, "assistantDock");
            onEdt(() -> tabs.setSelectedIndex(tabs.indexOfTab("Topology")));

            // Claude's propose, over the hosted endpoint exactly as the onboard session sends it
            Path hostedFile;
            try (var st = Files.walk(home)) {
                hostedFile = st.filter(p -> p.getFileName().toString().matches("endpoint-.*\\.json")).findFirst().orElseThrow();
            }
            var he = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(hostedFile).read();
            var rp = ExternalRestWithHostedSessionFrameTest.post(http, he.url(), "{\"action\":\"propose\",\"params\":{"
                    + "\"summary\":\"a Spring application\",\"items\":[{\"kind\":\"starter\",\"starter\":\"fluxtion-spring\","
                    + "\"reason\":\"Spring is the preferred route for a new app\"}]}}", null, he.hosted());
            assertEquals(200, rp.statusCode(), rp.body());
            assertTrue(rp.body().contains("\"ok\":true"), rp.body());
            StarterProposalFrameTest.awaitSnapshot(f, s -> s.proposal().awaitingPerson(), "the starter is the person's to answer");
            Thread.sleep(300);

            // the banner exists but is behind the Topology tab — the owner's state
            AtomicBoolean bannerShowing = new AtomicBoolean(true);
            onEdt(() -> bannerShowing.set(panel.proposalBanner().isShowing()));
            assertFalse(bannerShowing.get(), "precondition: the assistant's banner is behind another tab");

            // ★ the frame's strip is on screen, and says what is waiting
            AtomicReference<AbstractButton> visibleReview = new AtomicReference<>();
            AtomicReference<String> words = new AtomicReference<>();
            onEdt(() -> {
                visibleReview.set(showingButton(f, MainFrame.PROPOSAL_CUE_REVIEW));
                words.set(StarterProposalFrameTest.text(f.proposalCueBar()));
            });
            assertNotNull(visibleReview.get(), "★ a Review… is SHOWING somewhere in the window while a proposal waits");
            assertTrue(words.get().contains("new project from a starter"), words.get());
            assertTrue(f.proposalCueBar().isShowing(), "★ the proposal strip is on screen");

            // the person's click — nothing else opens the review
            SwingUtilities.invokeLater(() -> visibleReview.get().doClick(0));   // the review is modal
            JDialog review = ClaudeCodeTabFrameTest.awaitDialog(StarterProposalFrameTest.REVIEW_TITLE);
            onEdt(() -> assertSame(dock, tabs.getSelectedComponent(), "Review… brought the assistant into sight"));
            onEdt(() -> assertTrue(panel.proposalBanner().isShowing(), "the assistant's own banner is in sight too"));
            onEdt(review::dispose);
            Thread.sleep(300);

            // closed without answering: still waiting, and the assistant's banner offers its own Review…
            assertTrue(StarterProposalFrameTest.snapshot(f).proposal().awaitingPerson(), "closing answers nothing");
            AtomicReference<AbstractButton> bannerReview = new AtomicReference<>();
            onEdt(() -> bannerReview.set(showingButton(panel.proposalBanner(), "Review…")));
            assertNotNull(bannerReview.get(), "the banner's Review… is showing");
            SwingUtilities.invokeLater(() -> bannerReview.get().doClick(0));
            JDialog again = ClaudeCodeTabFrameTest.awaitDialog(StarterProposalFrameTest.REVIEW_TITLE);
            onEdt(again::dispose);
        } finally {
            System.setProperty("user.home", before);
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing()) SwingUtilities.invokeLater(d::dispose);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    /** The first button with these words that is actually on screen (showing, enabled), or null. */
    static AbstractButton showingButton(java.awt.Container c, String text) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText()) && b.isShowing() && b.isEnabled()) return b;
            if (k instanceof java.awt.Container cc) {
                AbstractButton found = showingButton(cc, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
