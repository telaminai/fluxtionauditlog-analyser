package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * A robot walked a newcomer's first run (2026-10-18; problems #1, #2, #3, #6). In the REAL frame at the default
 * 1200×800, with a FAKE Claude Code ({@link FixtureAgent}; no live {@code claude}) and an isolated home: a question asked
 * from a record before any assistant is chosen reaches Claude Code after Apply — Safe analysis (it took the robot 16
 * acts and four dead Sends); the docked panel keeps Send and the transcript visible; Configure provider opens the page
 * that holds the provider.
 */
class AssistantFirstRunFrameTest {

    /** M77-2: these are the profile cards' first-use tests — the cards stay reachable until M77-4; pinned to them. */
    private boolean wizardBefore;

    @org.junit.jupiter.api.BeforeEach
    void cardsForFirstUse() {
        wizardBefore = telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup.wizardForFirstUse();
        telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup.useWizardForFirstUse(false);
    }

    @org.junit.jupiter.api.AfterEach
    void restoreFirstUse() {
        telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup.useWizardForFirstUse(wizardBefore);
    }

    static final class Fresh implements AutoCloseable {
        final MainFrame frame;
        final FixtureAgent agent;
        final ActionExecutor ex;
        private final String home;

        Fresh(Path tmp, int width, int height) throws Exception {
            Path home = Files.createDirectories(tmp.resolve("home"));
            Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
            Files.writeString(claude, "#!/bin/sh\nexit 0\n");
            Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
            Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
            agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
            agent.conformInit = true;
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
            ex = (ActionExecutor) field(frame, "actionExecutor");
            onEdt(() -> { frame.setSize(width, height); frame.setVisible(true); });
        }

        AssistantPanel assistant() {
            return (AssistantPanel) field(frame, "assistantPanel");
        }

        LocalAgentPanel claude() {
            return (LocalAgentPanel) field(frame, "localAgentPanel");
        }

        void openSampleLog(Path tmp) throws Exception {
            Path log = Files.copy(Path.of("src/test/resources/sample.yml"), tmp.resolve("run.yml"));
            onEdt(() -> render(ex, "open", Map.of("log", log.toString())));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < until) {
                boolean[] loaded = new boolean[1];
                onEdt(() -> loaded[0] = field(frame, "store") != null);
                if (loaded[0]) break;
                Thread.sleep(50);
            }
            onEdt(() -> ((LogTablePanel) field(frame, "tablePanel")).selectModelRow(0));
            onEdt(() -> { });
        }

        /** The record's "Ask the assistant" button, as the person presses it. */
        void askFromTheRecord() throws Exception {
            onEdt(() -> {
                try {
                    var m = MainFrame.class.getDeclaredMethod("askSingle", AskCatalogue.Surface.class);
                    m.setAccessible(true);
                    m.invoke(frame, AskCatalogue.Surface.DETAIL_BUTTON);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            onEdt(() -> { });
        }

        void applyProfile(AssistantProfile p) throws Exception {
            ProfileOverlayFrameTest.await(frame, x -> x.open(), "the cards are open");
            onEdt(() -> {
                ProfileOverlay o = assistant().profileOverlay();
                o.card(p).doClick();
                o.applyButton().doClick();
            });
            ProfileOverlayFrameTest.await(frame, x -> !x.open() && x.profile() == p, p + " applied");
            onEdt(() -> { });
        }

        @Override
        public void close() throws Exception {
            System.setProperty("user.home", home);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    static int occurrences(String text, String of) {
        int n = 0;
        for (int i = text.indexOf(of); i >= 0; i = text.indexOf(of, i + 1)) n++;
        return n;
    }

    /**
     * ★ Problem #1, the robot's path (16 acts, four Sends that did nothing) in its few real steps: sample log, the
     * record's Ask, the cards, Apply — Safe analysis, Send. The question reaches Claude Code. Witness (today's behaviour
     * before this change): compose the question for the mode's default in {@code composeDraft} (drop the
     * {@code observedNone()} branch) — Send is refused UNKNOWN_DRAFT and no turn starts.
     */
    @Test
    @DisplayName("★ a question asked from a record before any assistant is chosen is sent to Claude Code after Apply — Safe analysis")
    void askBeforeChoosingThenApplySafeThenSend(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Fresh f = new Fresh(tmp, 1200, 800)) {
            f.openSampleLog(tmp);
            f.askFromTheRecord();
            Thread.sleep(300);
            onEdt(() -> { });
            if (!ProfileOverlayFrameTest.profiles(f.frame).open()) {
                // the robot's path before this change: the draft was placed for the in-app assistant, and Send opened
                // the cards
                onEdt(() -> f.assistant().sendButton().doClick());
            }
            var waiting = ProfileOverlayFrameTest.await(f.frame, x -> x.open(), "the cards are open");
            assertTrue(waiting.questionWaits() || waiting.why().equals(telamin.fluxtion.audit.analyser.analyser.session
                    .ProfileState.FIRST_USE), "the question waits for the choice");
            f.applyProfile(AssistantProfile.SAFE);
            UnifiedAssistantFrameTest.awaitMode(f.frame, AssistantMode.LOCAL_AGENT);
            String[] draft = new String[1];
            onEdt(() -> draft[0] = f.assistant().draftText());
            assertFalse(draft[0].isBlank(), "the question is placed for Claude Code");
            onEdt(() -> f.claude().sendButton().doClick());
            LocalAgentState answering = ClaudeCodeTabFrameTest.awaitState(f.frame,
                    s -> s.openTicket() != 0 || !"".equals(s.verdict()) && !"BOUND".equals(s.verdict()) && !s.running(),
                    "Send started Claude Code and sent the question");
            assertNotEquals("UNKNOWN_DRAFT", answering.verdict(), "★ never refused as a question for the other assistant");
            assertTrue(answering.openTicket() != 0, "★ the turn started: " + answering.verdict() + " " + answering.reason());
            assertEquals(1, f.agent.processes.size(), "one Send started Claude Code");
            // problem #1 (c): asking again replaces the draft, it never appends a second copy
            String firstLine = draft[0].lines().findFirst().orElseThrow();
            f.askFromTheRecord();
            f.askFromTheRecord();
            onEdt(() -> draft[0] = f.assistant().draftText());
            assertEquals(1, occurrences(draft[0], firstLine), "★ asked again: one copy, not two:\n" + draft[0]);
            // problem #3: while it runs there is no Start (no disabled "Running…"), and Stop is the primary action
            onEdt(() -> {
                assertFalse(f.claude().startButton().isShowing(), "★ no Start while Claude Code runs");
                assertTrue(f.claude().stopButton().getFont().isBold(), "Stop is the primary action now");
            });
            String instruction = ClaudeCodeTabFrameTest.state(f.frame).extra().guide().instruction();
            onEdt(() -> assertEquals(instruction, f.claude().instructionLine().getText(),
                    "the status line is the node's one instruction"));
        }
    }

    /**
     * ★ Problem #2: at the default 1200×800 the docked panel gave the transcript a negative height and hid Start and
     * Send. Witness: return {@code BorderLayout} to the panel (drop {@code FloorLayout}) — the transcript height
     * assertion fails.
     */
    @Test
    @DisplayName("★ at 1200×800 the docked assistant keeps Send, Pop out and a transcript of positive height")
    void theDockedPanelFitsTheDefaultWindow(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Fresh f = new Fresh(tmp, 1200, 800)) {
            f.openSampleLog(tmp);
            f.askFromTheRecord();
            f.applyProfile(AssistantProfile.SAFE);
            onEdt(() -> {
                AssistantPanel a = f.assistant();
                JComponent transcript = a.surface().viewScroll;
                assertTrue(transcript.isShowing(), "the transcript is shown");
                assertTrue(transcript.getHeight() >= AssistantPanel.TRANSCRIPT_FLOOR, "★ the transcript keeps its floor: "
                        + transcript.getHeight());
                for (JButton b : new JButton[]{a.sendButton(), (JButton) field(a, "hostButton")}) {
                    assertTrue(b.isShowing(), b.getText() + " is shown");
                    Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), a);
                    assertTrue(new Rectangle(0, 0, a.getWidth(), a.getHeight()).contains(r),
                            "★ " + b.getText() + " lies wholly inside the panel: " + r + " in " + a.getSize());
                    Rectangle visible = b.getVisibleRect();
                    assertEquals(b.getWidth(), visible.width, b.getText() + " is not clipped");
                }
                assertFalse(a.aboutOpen(), "the notes sit behind ⓘ until asked for");
            });
        }
    }

    /**
     * ★ Problem #6: Configure provider opened Settings on Source roots. Witness: drop the page from
     * {@code configureProvider} — the selected page is the first one.
     */
    @Test
    @DisplayName("★ Configure provider opens Settings on the page with the provider, model and API key")
    void configureProviderOpensTheProviderPage(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Fresh f = new Fresh(tmp, 1200, 800)) {
            JButton configure = (JButton) field(f.assistant(), "configure");
            SwingUtilities.invokeLater(configure::doClick);      // the dialog is modal: pressed off the test's thread
            ConfigPanel settings = null;
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (settings == null && System.nanoTime() < until) {
                for (Window w : Window.getWindows()) if (w instanceof ConfigPanel c && c.isShowing()) settings = c;
                Thread.sleep(40);
            }
            assertNotNull(settings, "Settings opened");
            ConfigPanel shown = settings;
            String[] page = new String[1];
            onEdt(() -> {
                JTabbedPane tabs = (JTabbedPane) field(shown, "tabs");
                page[0] = tabs.getTitleAt(tabs.getSelectedIndex());
                assertTrue(((JComponent) field(shown, "apiKeyField")).isShowing(), "★ on the provider's page: the API key field is on screen");
                shown.dispose();
            });
            assertEquals(ConfigPanel.PROVIDER_PAGE, page[0], "★ on the provider's page");
        }
    }
}
