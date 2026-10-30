package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M77-2 (spec-ai-setup-wizard.md §16): Set up AI in the REAL frame, under an isolated {@code user.home}, at the default
 * 1200×800, with a STUB {@code claude} (a shell script in the isolated home's {@code ~/.local/bin} that answers
 * {@code --version} and {@code auth status --json} with DEMO values; the session's stream is the recorded
 * {@link FixtureAgent} replay). No live {@code claude} runs and no prompt is sent anywhere.
 *
 * <p>Set {@code -Dm77.shots=<dir>} to also paint every screen, light and dark, at 1200×800 and narrow, into that folder.
 */
class AiSetupWizardFrameTest {

    /** The stub: DEMO values only; it never reads anything and never contacts anything. */
    static final String STUB = """
            #!/bin/sh
            if [ "$1" = "--version" ]; then echo "2.1.282 (Claude Code)"; exit 0; fi
            if [ "$1" = "auth" ] && [ "$2" = "status" ]; then
              echo '{"loggedIn":true,"authMethod":"claude.ai","apiProvider":"firstParty","email":"demo@example.invalid","orgId":"00000000-DEMO","orgName":"DEMO Org","subscriptionType":"max"}'
              exit 0
            fi
            exit 0
            """;

    static final class Robot implements AutoCloseable {
        final MainFrame frame;
        final FixtureAgent agent;
        final Path home;
        private final String homeBefore;
        int acts;

        Robot(Path tmp, int width, int height) throws Exception {
            home = Files.createDirectories(tmp.resolve("home"));
            Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
            Files.writeString(claude, STUB);
            Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
            Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
            agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
            agent.conformInit = true;
            agent.rewrite = l -> l.contains("command_lifecycle") && l.contains("completed")
                    || l.contains("\"type\": \"result\"") ? null : l;
            homeBefore = System.getProperty("user.home");
            System.setProperty("user.home", home.toString());
            MainFrame[] made = new MainFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.localAgentLauncher = agent;
                // a GUI-shaped PATH (T24): claude is found in ~/.local/bin by the one resolver, not on PATH
                f.setupEnvironmentForTest = java.util.Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
                made[0] = f;
            });
            frame = made[0];
            onEdt(() -> { frame.setSize(width, height); frame.setVisible(true); });
            onEdt(() -> { });
        }

        SessionDriver session() {
            return (SessionDriver) field(frame, "session");
        }

        AiSetupState setup() throws Exception {
            AiSetupState[] s = new AiSetupState[1];
            onEdt(() -> s[0] = session().snapshot().aiSetup());
            return s[0];
        }

        AiSetupState await(Predicate<AiSetupState> done, String what) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            AiSetupState s = setup();
            while (!done.test(s) && System.nanoTime() < until) {
                Thread.sleep(40);
                s = setup();
            }
            assertTrue(done.test(s), what + ": " + s);
            onEdt(() -> { });
            return s;
        }

        StartPanel start() {
            return (StartPanel) field(frame, "startPanel");
        }

        AssistantPanel assistant() {
            return (AssistantPanel) field(frame, "assistantPanel");
        }

        LocalAgentPanel claude() {
            return (LocalAgentPanel) field(frame, "localAgentPanel");
        }

        AiSetupWindow window() {
            return frame.aiSetupWindow;
        }

        /** One act of the person: a press, counted. */
        void act(Runnable press) throws Exception {
            acts++;
            onEdt(press);
            onEdt(() -> { });
        }

        void awaitLog() throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < until) {
                boolean[] loaded = new boolean[1];
                onEdt(() -> loaded[0] = field(frame, "store") != null);
                if (loaded[0]) return;
                Thread.sleep(50);
            }
            fail("the sample's log did not load");
        }

        @Override
        public void close() throws Exception {
            System.setProperty("user.home", homeBefore);
            SwingUtilities.invokeAndWait(() -> {
                if (frame.aiSetupWindow != null) frame.aiSetupWindow.dispose();
                frame.dispose();
            });
        }
    }

    static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) out.add(type.cast(c));
            if (c instanceof Container k) out.addAll(all(k, type));
        }
        return out;
    }

    static AbstractButton button(Container root, String text) {
        for (AbstractButton b : all(root, AbstractButton.class)) {
            if (text.equals(b.getText()) || text.equals(b.getAccessibleContext().getAccessibleName())) return b;
        }
        return null;
    }

    /** No text in {@code root} is clipped: every wrapping line is at least as tall as its text at its width. */
    static List<String> clipped(Container root) {
        List<String> out = new ArrayList<>();
        for (JTextComponent t : all(root, JTextComponent.class)) {
            if (!t.isShowing() || t.getWidth() <= 0) continue;
            int need = t.getUI().getPreferredSize(t).height;
            if (t.getHeight() + 1 < need) out.add("'" + t.getText() + "' " + t.getHeight() + " < " + need);
            // a line outside any scroll pane must be wholly on screen: there is no way to scroll it into view
            if (SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, t) == null
                    && t.getVisibleRect().height + 1 < t.getHeight()) {
                out.add("'" + t.getText() + "' cut off: " + t.getVisibleRect().height + " of " + t.getHeight() + " shown");
            }
        }
        return out;
    }

    static void assertWhollyInside(AbstractButton b, Window w) {
        assertTrue(b.isShowing(), b.getText() + " is shown");
        Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), w);
        Rectangle inside = new Rectangle(0, 0, w.getWidth(), w.getHeight());
        assertTrue(inside.contains(r), b.getText() + " lies wholly inside the window: " + r + " in " + w.getSize());
    }

    static void shot(Component c, String name) throws Exception {
        String dir = System.getProperty("m77.shots");
        if (dir == null || dir.isBlank()) return;
        if (c instanceof javax.swing.RootPaneContainer rp) c = rp.getRootPane();   // the window's own pixels, no title bar
        Component painted = c;
        BufferedImage img = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        onEdt(() -> {
            var g = img.createGraphics();
            painted.paint(g);
            g.dispose();
        });
        Path out = Files.createDirectories(Path.of(dir)).resolve(name + ".png");
        ImageIO.write(img, "png", out.toFile());
    }

    @Test
    void onboardBuildReachesTheComposerWithoutExternalRegistration(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Robot r = new Robot(tmp, 1200, 800)) {
            r.await(AiSetupState::firstRun, "onboarding");
            onEdt(() -> r.frame.fullProfileConfirmModal = false);
            shot(r.frame, "onboarding-1-start");
            r.act(() -> button(r.start(), AiSetupLines.ONBOARD_BUILD).doClick());
            r.await(s -> s.signedIn().verdict() == AiSetupState.SignedIn.SIGNED_IN, "Claude checks");
            assertEquals(Step.INSIDE, r.setup().step());
            assertEquals(AiSetupState.Posture.FULL, r.setup().posture());
            assertTrue(r.agent.processes.isEmpty(), "setup has not started an AI session");
            shot(r.window(), "onboarding-2-checked");
            r.act(() -> r.window().primaryButton().doClick());
            SafeChoice.Asked consent = QuickSwitchFrameTest.awaitConfirmation(r.frame);
            assertTrue(r.agent.processes.isEmpty(), "consent must precede any AI session");
            shot(consent.dialog(), "onboarding-3-consent");
            r.act(() -> consent.riskyButton().doClick());
            r.await(s -> s.step() == Step.SUMMARY, "build ready");
            assertEquals(AiSetupState.Suggested.BUILD, r.setup().suggested());
            shot(r.window(), "onboarding-4-ready");
            r.act(() -> r.window().suggestedButton().doClick());
            r.await(s -> !s.open(), "describe application");
            onEdt(() -> {
                assertEquals("S7", r.start().prompt().chosen().id());
                assertTrue(r.start().prompt().box().isShowing());
                r.start().prompt().box().setText("Help me create a small order-alert application. Ask what events and rules I need first.");
            });
            shot(r.frame, "onboarding-5-describe");
            r.act(() -> r.start().prompt().actionButton().doClick());
            UnifiedAssistantFrameTest.awaitMode(r.frame, AssistantMode.LOCAL_AGENT);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            String[] draft = new String[1];
            do {
                onEdt(() -> draft[0] = r.assistant().draftText());
                if (draft[0].contains("order-alert")) break;
                Thread.sleep(40);
            } while (System.nanoTime() < until);
            assertTrue(draft[0].contains("order-alert"), "the user's requirements reach the assistant");
            assertTrue(r.agent.processes.isEmpty(), "placing the question is not sending it");
            assertFalse(((telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(r.frame, "config")).assistantActionsRest);
            shot((Component) SwingUtilities.getWindowAncestor(r.assistant()), "onboarding-6-composer");
        }
    }

    // ---- ★ T19: the robot walkthrough ----------------------------------------------------------------------------

    /**
     * ★ T19 — the newcomer's path in ≤ 6 acts with no dead end: (1) Sample project; (2) right-click a record; (3) its
     * <i>Ask: What happened here? (set up AI first)</i> — the wizard opens at Screen 1 with Claude inside preselected;
     * (4) <i>Check Claude Code</i> (Continue until the scorecard fix) — both checks ✓ (the stub answered), Safe
     * preselected; (5) <i>Finish, then send my question</i> (a bare Finish before) — the window closes, the question is
     * placed once and bound; (6) Send — a turn starts and the fixture's answer streams. Witness: open nothing on
     * (3) (drop {@code openWizard} from {@code AiSetup.onDraftPending}) — the wizard never opens.
     */
    @Test
    @DisplayName("★ T19: Sample → right-click → Ask → Check Claude Code → Finish, then send → Send, in ≤ 6 acts at 1200×800")
    void theRobotWalkthrough(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Robot r = new Robot(tmp, 1200, 800)) {
            r.await(s -> s.firstRun(), "a fresh home shows the first-run block on the start page");
            // (1) Sample project — the start page's chip
            AbstractButton sample = button(r.start(), "Open sample project");
            assertNotNull(sample, "the start page offers the sample project");
            r.act(sample::doClick);
            r.awaitLog();
            shot(r.frame, "t19-1-sample");
            // (2) right-click a record — the popup the table builds on a popup trigger
            JTable table = ((LogTablePanel) field(r.frame, "tablePanel")).table();
            r.act(() -> {
                Rectangle cell = table.getCellRect(0, 0, true);
                for (int id : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED}) {
                    table.dispatchEvent(new MouseEvent(table, id, System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK,
                            cell.x + 5, cell.y + 5, 1, true, MouseEvent.BUTTON3));
                }
            });
            JPopupMenu popup = null;
            for (int i = 0; i < 50 && popup == null; i++) {
                for (Window w : Window.getWindows()) {
                    for (JPopupMenu p : all(w, JPopupMenu.class)) if (p.isShowing()) popup = p;
                }
                if (popup == null) Thread.sleep(40);
            }
            JMenuItem ask = null;
            if (popup != null) {
                for (JMenuItem m : all(popup, JMenuItem.class)) {
                    if (!(m instanceof JMenu) && m.getText().contains("What happened here?")) ask = m;
                }
            }
            assertNotNull(ask, "the record's menu offers the question");
            assertTrue(ask.getText().endsWith(AiSetupLines.ASK_CUE), "★ the cue says set up AI first: " + ask.getText());
            JPopupMenu shown = popup;
            JMenuItem item = ask;
            // (3) Ask
            r.act(() -> {
                shown.setVisible(false);
                item.doClick();
            });
            AiSetupState opened = r.await(s -> s.open(), "★ the Ask opens Set up AI (not the cards)");
            assertEquals(Step.CHOICE, opened.step());
            assertEquals(Choice.INSIDE, opened.choice(), "Claude inside preselected: claude was found (T24's half)");
            assertEquals(AiSetupState.PendingKind.ASK_DRAFT, opened.pending().kind(), "the question waits");
            AiSetupWindow w = r.window();
            assertNotNull(w);
            onEdt(() -> {
                assertWhollyInside(w.primaryButton(), w);
                // the scorecard's T1/T2 fix: the button says what it does (it opens 2a, which runs the two checks)
                assertEquals(AiSetupLines.CHECK_CLAUDE_CODE, w.primaryButton().getText(), "★ not Continue");
            });
            shot(w, "t19-3-screen1");
            // (4) Continue
            r.act(() -> w.primaryButton().doClick());
            AiSetupState checked = r.await(s -> s.signedIn().verdict() == AiSetupState.SignedIn.SIGNED_IN,
                    "★ both checks pass against the stub");
            assertEquals("2.1.282", checked.installed().version());
            assertEquals(AiSetupState.Posture.SAFE, checked.posture(), "Safe analysis preselected");
            onEdt(() -> {
                assertTrue(w.primaryButton().isEnabled(), "Finish is enabled once Installed passed");
                // a question waits, so this press closes the window and places it (OD-77.2): the label says so
                assertEquals(AiSetupLines.FINISH_THEN_SEND, w.primaryButton().getText(), "★ not a bare Finish");
                assertWhollyInside(w.primaryButton(), w);
                String shownText = String.join("\n", all(w.getContentPane(), JTextComponent.class).stream()
                        .map(JTextComponent::getText).toList());
                for (String personal : List.of("demo@example.invalid", "DEMO Org", "00000000-DEMO")) {
                    assertFalse(shownText.contains(personal), "★ T7 on screen: " + personal);
                }
            });
            shot(w, "t19-4-inside");
            // (5) Finish
            r.act(() -> w.primaryButton().doClick());
            r.await(s -> !s.open(), "★ Finish closes the window (a question waits: no Summary)");
            UnifiedAssistantFrameTest.awaitMode(r.frame, AssistantMode.LOCAL_AGENT);
            String[] draft = new String[1];
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do {
                onEdt(() -> draft[0] = r.assistant().draftText());
                if (!draft[0].isBlank()) break;
                Thread.sleep(40);
            } while (System.nanoTime() < until);
            assertFalse(draft[0].isBlank(), "★ the question is placed");
            String firstLine = draft[0].lines().findFirst().orElseThrow();
            assertEquals(1, AssistantFirstRunFrameTest.occurrences(draft[0], firstLine), "★ exactly one copy");
            onEdt(() -> assertFalse(w.isVisible(), "the window is gone"));
            assertEquals(AiSetupLines.placedLine(telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile.SAFE),
                    r.setup().line(), "the Summary's words became the one status line");
            onEdt(() -> {
                JButton send = r.claude().sendButton();
                assertTrue(send.isShowing() && send.isEnabled(), "★ Send is there to press at 1200×800");
                AssistantPanel a = r.assistant();
                Rectangle rect = SwingUtilities.convertRectangle(send.getParent(), send.getBounds(), a);
                assertTrue(new Rectangle(0, 0, a.getWidth(), a.getHeight()).contains(rect), "★ Send is not hidden: " + rect);
            });
            shot(r.frame, "t19-5-placed");
            // (6) Send
            r.act(() -> r.claude().sendButton().doClick());
            LocalAgentState answering = ClaudeCodeTabFrameTest.awaitState(r.frame,
                    s -> s.openTicket() != 0 || !"".equals(s.verdict()) && !"BOUND".equals(s.verdict()) && !s.running(),
                    "Send started Claude Code and sent the question");
            assertNotEquals("UNKNOWN_DRAFT", answering.verdict(), "★ never a dead Send");
            assertNotEquals("STALE_DRAFT", answering.verdict(), "★ never a dead Send");
            assertTrue(answering.openTicket() != 0, "★ the turn started: " + answering.verdict() + " " + answering.reason());
            assertEquals(1, r.agent.processes.size(), "one Send started Claude Code");
            for (Window x : Window.getWindows()) {
                if (x.isShowing() && x instanceof javax.swing.JDialog d) {
                    assertFalse(d.getTitle() != null && d.getTitle().toLowerCase(java.util.Locale.ROOT).contains("folder"),
                            "★ no folder question: the sample runs in the scratch folder (" + d.getTitle() + ")");
                }
            }
            assertTrue(r.acts <= 6, "★ ≤ 6 acts: " + r.acts);
            shot(r.frame, "t19-6-sent");
        }
    }

    // ---- T15 and the entry points --------------------------------------------------------------------------------

    /**
     * ★ T15 (frame): the start page shows the first-run block, then — after Not now — exactly one AI line with its
     * link, and the link opens the wizard; the panel shows one line and Set up AI…; the AI menu's first item is Set up
     * AI…; Settings ▸ Assistant's button opens it too. Witness: drop {@code renderAiSetup} from the snapshot render — the
     * block never shows.
     */
    @Test
    @DisplayName("★ T15: the first-run block, the one AI line, and every door opens the one window")
    void theStartPageAndTheDoors(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Robot r = new Robot(tmp, 1200, 800)) {
            r.await(AiSetupState::firstRun, "first run");
            onEdt(() -> assertTrue(r.start().aiSetupBlock().showsFirstRun(), "★ the block is on the start page"));
            shot(r.frame, "start-first-run");
            onEdt(() -> { r.frame.setSize(640, 800); r.frame.validate(); });
            onEdt(() -> { });
            onEdt(() -> assertTrue(r.start().aiSetupBlock().proceedButton().isShowing(), "the build button at 640 px"));
            shot(r.frame, "start-first-run-narrow");
            onEdt(() -> { r.frame.setSize(1200, 800); r.frame.validate(); });
            onEdt(() -> r.start().aiSetupBlock().notNowButton().doClick());
            r.await(s -> !s.firstRun(), "Not now");
            onEdt(() -> {
                AiSetupBlock b = r.start().aiSetupBlock();
                assertFalse(b.showsFirstRun());
                assertEquals("AI: not set up · Claude Code found", b.lineLabel().getText(), "★ the one AI line");
                assertEquals(AiSetupLines.SET_UP_LINK, b.linkButton().getText());
                String page = String.join("\n", all(r.start(), JComponent.class).stream()
                        .filter(Component::isShowing)
                        .map(c -> c instanceof javax.swing.JLabel l ? l.getText()
                                : c instanceof JTextComponent t ? t.getText() : "").toList());
                assertFalse(page.contains("No AI assistant can answer here yet"), "★ not beside the AI line");
                assertEquals(1, AssistantFirstRunFrameTest.occurrences(page, "AI: not set up"), "★ exactly one AI line");
            });
            shot(r.frame, "start-ai-line");
            assertTrue(((telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(r.frame, "config")).assistantSetupDismissed, "Not now is remembered on this machine");
            onEdt(() -> r.start().aiSetupBlock().linkButton().doClick());
            r.await(AiSetupState::open, "the start page's link opens the wizard");
            assertEquals(AiSetupState.Origin.START_LINE, r.setup().origin());
            onEdt(() -> r.window().cancelButton().doClick());
            r.await(s -> !s.open(), "Cancel");

            // the AI menu's first item
            onEdt(() -> {
                JMenu ai = null;
                for (int i = 0; i < r.frame.getJMenuBar().getMenuCount(); i++) {
                    JMenu m = r.frame.getJMenuBar().getMenu(i);
                    if (m != null && "AI".equals(m.getText())) ai = m;
                }
                assertNotNull(ai);
                assertEquals(AiSetupLines.SET_UP_LINK, ai.getItem(0).getText(), "★ AI ▸ Set up AI… is first");
                ai.getItem(0).doClick();
            });
            r.await(AiSetupState::open, "AI ▸ Set up AI…");
            onEdt(() -> r.window().cancelButton().doClick());
            r.await(s -> !s.open(), "Cancel");

            // Assistant ▾ ▸ Set up AI… — M77-4: the quick switch's last row
            onEdt(() -> r.assistant().profileButton().doClick());
            onEdt(() -> {
                JPopupMenu quick = r.assistant().quickSwitch().lastShown();
                assertNotNull(quick, "Assistant ▾ shows the quick switch");
                JMenuItem setUp = QuickSwitchFrameTest.setUpItem(quick);
                quick.setVisible(false);
                setUp.doClick();
            });
            r.await(AiSetupState::open, "★ Assistant ▾ ▸ Set up AI…");
            onEdt(() -> r.window().cancelButton().doClick());
            r.await(s -> !s.open(), "Cancel");

            // the panel: one line and the button
            onEdt(() -> {
                AssistantPanel a = r.assistant();
                assertEquals(AiSetupLines.PANEL_NOTHING_SET_UP, r.session().snapshot().profiles().status(),
                        "the panel's one line is the node's");
                a.setUpAiButton().doClick();
            });
            r.await(AiSetupState::open, "the panel's Set up AI…");
            assertEquals(AiSetupState.Origin.MENU, r.setup().origin());
            // a second request fronts the one window at its step
            onEdt(() -> r.assistant().setUpAiButton().doClick());
            onEdt(() -> {
                long windows = java.util.Arrays.stream(Window.getWindows())
                        .filter(x -> x instanceof AiSetupWindow && x.isDisplayable()).count();
                assertEquals(1, windows, "★ one instance");
            });
            onEdt(() -> r.window().cancelButton().doClick());
            r.await(s -> !s.open(), "Cancel");
        }
    }

    /**
     * OD-77.12 (M77-3): <i>Your own client</i> opens Screen 2b, and today's {@code McpSetupDialog} stays behind its
     * <i>More options…</i>. Witness: drop the {@code OpenClientSetupEffect} performer's dialog — no MCP set-up window
     * appears.
     */
    @Test
    @DisplayName("★ Your own client opens Screen 2b; today's MCP set-up dialog stays behind More options… (OD-77.12)")
    void yourOwnClientKeepsTodaysDialog(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Robot r = new Robot(tmp, 1200, 800)) {
            r.await(AiSetupState::firstRun, "first run");
            onEdt(() -> r.assistant().setUpAiButton().doClick());
            r.await(AiSetupState::open, "open");
            onEdt(() -> r.window().choiceRadio(Choice.CLIENT).doClick());
            r.await(s -> s.choice() == Choice.CLIENT, "chosen");
            onEdt(() -> r.window().primaryButton().doClick());
            r.await(s -> s.step() == Step.CLIENT, "Screen 2b");
            onEdt(() -> button(r.window().contentPanel(), AiSetupLines.MORE_OPTIONS).doClick());
            McpSetupDialog dialog = null;
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (dialog == null && System.nanoTime() < until) {
                for (Window w : Window.getWindows()) if (w instanceof McpSetupDialog d && d.isShowing()) dialog = d;
                Thread.sleep(40);
            }
            assertNotNull(dialog, "★ today's dialog opened from More options…");
            McpSetupDialog d = dialog;
            onEdt(d::dispose);
            assertTrue(r.setup().open(), "the wizard stays open behind it");
            assertEquals(Step.CLIENT, r.setup().step());
        }
    }

    // ---- ★ T17: every screen fits, in light and dark, at the default and the narrowest width ---------------------------

    /**
     * ★ T17. Each screen at 640 and 520 px wide (the minimum), light and dark: no clipped text and the primary button
     * wholly visible; focus lands on the screen's first control. Witness: drop the content's scroll pane
     * ({@code getContentPane().add(content)} in place of {@code scroll}) — at 520×480 the primary button or a line is cut.
     */
    @Test
    @DisplayName("★ T17: every screen at 640 and 520 px, light and dark — no clipped text, the primary button in view")
    void everyScreenFits(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        // the look and feel in force before, put back exactly (later suites in this JVM paint under it)
        javax.swing.LookAndFeel lafBefore = javax.swing.UIManager.getLookAndFeel();
        try (Robot r = new Robot(tmp, 1200, 800)) {
            r.await(AiSetupState::firstRun, "first run");
            for (String theme : List.of("Light", "Dark")) {
                onEdt(() -> {
                    ThemeManager.apply(theme);
                    for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
                });
                shot(r.frame, "start-first-run-" + theme.toLowerCase());
                for (int width : new int[]{AiSetupWindow.DEFAULT_WIDTH, AiSetupWindow.MIN_WIDTH}) {
                    String tag = theme.toLowerCase() + "-" + width;
                    onEdt(() -> r.assistant().setUpAiButton().doClick());
                    r.await(AiSetupState::open, "open");
                    AiSetupWindow w = r.window();
                    int height = width == AiSetupWindow.MIN_WIDTH ? AiSetupWindow.MIN_HEIGHT : AiSetupWindow.DEFAULT_HEIGHT;
                    onEdt(() -> { w.setSize(width, height); w.validate(); });
                    onEdt(() -> { });
                    onEdt(() -> r.window().choiceRadio(Choice.INSIDE).doClick());
                    r.await(s -> s.choice() == Choice.INSIDE, "Claude inside chosen");
                    checkScreen(w, "Screen 1 " + tag);
                    onEdt(() -> {
                        // M77-5's slot: under the selected card's title row, above its sentence; it holds the picture
                        JComponent slot = w.selectedOptionExtra();
                        Container card = slot.getParent();
                        assertNotNull(card, "the slot is on the selected card");
                        assertTrue(SwingUtilities.isDescendingFrom(w.choiceRadio(Choice.INSIDE), card),
                                "on the selected option's card");
                        int at = java.util.Arrays.asList(card.getComponents()).indexOf(slot);
                        assertEquals(1, at, "between the title row and the sentence");
                        assertTrue(SwingUtilities.isDescendingFrom(w.diagram, slot) && w.diagram.isShowing()
                                && slot.getHeight() > 0, "M77-5: the slot shows the selected option's picture");
                        assertTrue(card.getComponent(at + 1) instanceof WrapLine, "the sentence follows it");
                        long sentences = all(w.contentPanel(), WrapLine.class).stream()
                                .filter(t -> t.isShowing() && !t.getText().isBlank()).count();
                        assertTrue(sentences >= 5, "every card's sentence is visible, selected or not: " + sentences);
                        int h = w.contentPanel().getPreferredSize().height;
                        assertTrue(h <= 730, "Screen 1's height within budget: " + h);
                    });
                    shot(w, "screen1-" + tag);
                    onEdt(() -> w.primaryButton().doClick());
                    r.await(s -> s.signedIn().verdict() == AiSetupState.SignedIn.SIGNED_IN, "checked");
                    checkScreen(w, "Screen 2a " + tag);
                    shot(w, "screen2a-safe-" + tag);
                    onEdt(() -> {
                        for (javax.swing.JRadioButton b : all(w.contentPanel(), javax.swing.JRadioButton.class)) {
                            if (AiSetupLines.FULL_TITLE.equals(b.getText())) b.doClick();
                        }
                    });
                    r.await(s -> s.posture() == AiSetupState.Posture.FULL
                            && s.signedIn().verdict() == AiSetupState.SignedIn.SIGNED_IN, "Full chosen and checked again");
                    checkScreen(w, "Screen 2a Full " + tag);
                    onEdt(() -> assertEquals(AiSetupLines.CONFIRM_FULL_BUTTON, w.primaryButton().getText()));
                    shot(w, "screen2a-full-" + tag);
                    onEdt(() -> w.backButton().doClick());
                    r.await(s -> s.step() == Step.CHOICE, "Back");
                    onEdt(() -> {
                        AbstractButton other = button(w.contentPanel(), AiSetupLines.OTHER_OPTIONS_LINK);
                        assertNotNull(other);
                        other.doClick();
                    });
                    r.await(s -> s.step() == Step.OTHER, "Other options");
                    checkScreen(w, "Other options " + tag);
                    onEdt(() -> assertTrue(w.inApp.apiKey.isFocusOwner() || !w.isFocused(),
                            "the key field has the focus (when the window does)"));
                    shot(w, "other-" + tag);
                    onEdt(() -> w.inApp.apiKey.setText("sk-DEMO-not-a-key"));
                    onEdt(() -> w.primaryButton().doClick());
                    r.await(s -> s.step() == Step.SUMMARY, "the Summary (nothing waits)");
                    checkScreen(w, "Summary " + tag);
                    shot(w, "summary-" + tag);
                    onEdt(() -> w.primaryButton().doClick());           // Done
                    r.await(s -> !s.open(), "Done");
                }
            }
            shot(r.frame, "panel-in-app");
        } finally {
            onEdt(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(lafBefore);
                } catch (javax.swing.UnsupportedLookAndFeelException e) {
                    throw new AssertionError(e);
                }
                for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
            });
        }
    }

    static void checkScreen(AiSetupWindow w, String what) throws Exception {
        onEdt(() -> {
            w.validate();
            assertEquals(List.of(), clipped(w.getContentPane()), "★ " + what + ": no clipped text");
            assertWhollyInside(w.primaryButton(), w);
            assertWhollyInside(w.cancelButton().isVisible() ? w.cancelButton() : w.primaryButton(), w);
        });
    }
}
