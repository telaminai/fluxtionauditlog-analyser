package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M74 UX (owner, 2026-10-17) in the REAL frame: the profile overlay — four cards instead of switch-by-switch set-up. It
 * opens by itself the first time the person asks a question with no profile chosen, and from the assistant header any
 * time; choosing a card shows that card's details; Apply writes the same machine-tier settings Settings ▸ Assistant
 * writes, through the session's effect, and the consent follows. Driven by direct dispatch (doClick, setters), because
 * posted key events and Robot presses are dropped on this Mac.
 */
class ProfileOverlayFrameTest {

    static ProfileState profiles(MainFrame f) {
        return ((SessionDriver) field(f, "session")).snapshot().profiles();
    }

    static ProfileState await(MainFrame f, Predicate<ProfileState> done, String what) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        AtomicReference<ProfileState> s = new AtomicReference<>();
        do {
            onEdt(() -> s.set(profiles(f)));
            if (done.test(s.get())) return s.get();
            Thread.sleep(30);
        } while (System.nanoTime() < until);
        throw new AssertionError(what + ": " + s.get());
    }

    static AppConfig config(MainFrame f) {
        return (AppConfig) field(f, "config");
    }

    /**
     * ★ The first question with nothing chosen opens the cards; each card's Apply writes its settings. Witnesses: make
     * {@code applyAssistantProfile} skip {@code AssistantProfiles.apply} — the config assertions fail; show the
     * transcript in place of the cards (drop the PROFILES card in {@code renderProfiles}) — the overlay is not showing.
     */
    @Test
    @DisplayName("★ the first question opens the cards by themselves; Safe, Full, In-app and Off each write their settings; no auto-open after")
    void theCardsOpenOnceAndEachWritesItsSettings(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            f.dialogs.stop();
            onEdt(() -> { f.frame.setSize(1300, 900); f.frame.setVisible(true); });
            assertEquals(AssistantProfile.NONE, profiles(f.frame).profile(), "a fresh machine: nothing chosen");
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                assertTrue(a.sendButton().isEnabled(), "with nothing chosen Send is pressable: it opens the cards");
                a.composerArea().setText("What is in this log?");
                a.sendButton().doClick();
            });
            ProfileState first = await(f.frame, ProfileState::open, "the first question opens the cards");
            assertEquals(ProfileState.FIRST_USE, first.why());
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                ProfileOverlay o = a.profileOverlay();
                assertTrue(o.isShowing(), "the cards are in the panel, where the transcript was");
                assertFalse(a.conversationView().isShowing(), "in its place");
                assertEquals(AssistantProfile.SAFE, o.selected(), "Safe analysis is the default card");
                assertTrue(o.card(AssistantProfile.SAFE).isSelected());
                assertEquals("What is in this log?", a.draftText(), "the question is kept for after");
                o.card(AssistantProfile.SAFE).doClick();
                o.applyButton().doClick();
            });
            ProfileState safe = await(f.frame, s -> !s.open() && s.profile() == AssistantProfile.SAFE, "Safe applied");
            assertEquals("", safe.refusal());
            onEdt(() -> {
                AppConfig c = config(f.frame);
                assertTrue(c.claudeCodeAllowed, "Safe analysis allows Claude Code");
                assertFalse(c.claudeCodeFull, "and never the full posture");
                assertEquals("LOCAL_AGENT", c.claudeCodeLastMode);
                assertEquals("SAFE", c.assistantProfile);
                assertEquals(AssistantMode.LOCAL_AGENT, UnifiedAssistantFrameTest.mode(f.frame), "the tab is in Claude Code mode");
                assertTrue(AssistantLiveFrameTest.panel(f.frame).conversationView().isShowing(), "the transcript is back");
                assertEquals("Profile: Safe analysis…", AssistantLiveFrameTest.panel(f.frame).profileButton().getText());
            });

            // no auto-open once a profile is chosen: another refused question leaves the cards shut
            onEdt(() -> ((SessionDriver) field(f.frame, "session")).post(new SessionEvents.AssistantSendRequested(
                    90, 1, new SessionEvents.AssistantRoute("anthropic", "", "", false, true, 3, 20, 30))));
            onEdt(() -> assertFalse(profiles(f.frame).open(), "chosen: the cards never open by themselves again"));

            // Full Claude Code from the header, with its mode
            onEdt(() -> AssistantLiveFrameTest.panel(f.frame).profileButton().doClick());
            assertEquals(ProfileState.ASKED, await(f.frame, ProfileState::open, "the header opens the cards").why());
            onEdt(() -> {
                ProfileOverlay o = AssistantLiveFrameTest.panel(f.frame).profileOverlay();
                assertEquals(AssistantProfile.SAFE, o.selected(), "they open on the profile in force");
                o.card(AssistantProfile.FULL).doClick();
                assertEquals(ConfigPanel.FULL_POSTURE_TURN_ON, o.applyButton().getText(),
                        "Full Claude Code's Apply is the warning's own confirmation");
                o.fullModeCombo().setSelectedItem("acceptEdits");
                o.applyButton().doClick();
            });
            await(f.frame, s -> !s.open() && s.profile() == AssistantProfile.FULL, "Full applied");
            onEdt(() -> {
                AppConfig c = config(f.frame);
                assertTrue(c.claudeCodeAllowed && c.claudeCodeFull, "the full posture");
                assertEquals("acceptEdits", c.claudeCodeFullMode, "with the mode the card chose");
                assertEquals("FULL", c.assistantProfile);
            });

            // In-app, with a key typed into the card
            onEdt(() -> AssistantLiveFrameTest.panel(f.frame).profileButton().doClick());
            await(f.frame, ProfileState::open, "open again");
            onEdt(() -> {
                ProfileOverlay o = AssistantLiveFrameTest.panel(f.frame).profileOverlay();
                o.card(AssistantProfile.IN_APP).doClick();
                o.providerCombo().setSelectedItem("openai");
                o.inAppModelField().setText("gpt-DEMO");
                o.apiKeyField().setText("sk-DEMO-overlay");
                o.applyButton().doClick();
            });
            await(f.frame, s -> !s.open() && s.profile() == AssistantProfile.IN_APP, "In-app applied");
            onEdt(() -> {
                AppConfig c = config(f.frame);
                assertFalse(c.claudeCodeAllowed, "In-app: Claude Code is not started");
                assertEquals("openai", c.llmProvider);
                assertEquals("gpt-DEMO", c.llmModel);
                assertEquals("sk-DEMO-overlay", c.apiKey);
                assertEquals(AssistantMode.IN_APP, UnifiedAssistantFrameTest.mode(f.frame));
                assertFalse(((javax.swing.JPanel) field(AssistantLiveFrameTest.panel(f.frame), "noProvider")).isVisible(),
                        "the in-app assistant has a key now");
            });

            // Off: no AI, the key kept
            onEdt(() -> AssistantLiveFrameTest.panel(f.frame).profileButton().doClick());
            await(f.frame, ProfileState::open, "open again");
            onEdt(() -> {
                ProfileOverlay o = AssistantLiveFrameTest.panel(f.frame).profileOverlay();
                assertEquals(AssistantProfile.IN_APP, o.selected());
                o.card(AssistantProfile.OFF).doClick();
                o.applyButton().doClick();
            });
            await(f.frame, s -> !s.open() && s.profile() == AssistantProfile.OFF, "Off applied");
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                assertEquals("sk-DEMO-overlay", config(f.frame).apiKey, "nothing the person stored is deleted");
                assertEquals(AssistantPanel.OFF_LIMITS, a.limitsLine().getText(), "the panel says the assistant is off");
                assertFalse(a.sendButton().isEnabled(), "and nothing is sent");
            });

            // Close leaves everything as it was
            onEdt(() -> AssistantLiveFrameTest.panel(f.frame).profileButton().doClick());
            await(f.frame, ProfileState::open, "open again");
            onEdt(() -> AssistantLiveFrameTest.panel(f.frame).profileOverlay().closeButton().doClick());
            await(f.frame, s -> !s.open() && s.profile() == AssistantProfile.OFF, "closed, unchanged");
        }
    }

    /**
     * ★ Settings ▸ Assistant, regrouped by profile: a child control is enabled only while its parent is on (the holistic
     * review found the full switch, the model and the spend limit live with Claude Code not allowed). Witness: drop the
     * {@code childrenFollowParents()} listener on the Claude Code switch — the enabled assertions after it fail.
     */
    @Test
    @DisplayName("★ Settings ▸ Assistant is grouped by profile, and each child is disabled while its parent is off")
    void settingsChildrenFollowTheirParents(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        onEdt(() -> {
            try {
                var ctor = ConfigPanel.class.getDeclaredConstructor(JFrame.class, AppConfig.class, Runnable.class);
                ctor.setAccessible(true);
                ConfigPanel p = ctor.newInstance(null, new AppConfig(), (Runnable) () -> { });
                try {
                    List<String> titles = UnifiedAssistantFrameTest.all(p.getContentPane(), JComponent.class).stream()
                            .filter(c -> c.getBorder() instanceof javax.swing.border.CompoundBorder cb
                                    && cb.getOutsideBorder() instanceof TitledBorder)
                            .map(c -> ((TitledBorder) ((javax.swing.border.CompoundBorder) c.getBorder()).getOutsideBorder())
                                    .getTitle()).toList();
                    assertEquals(List.of("Safe analysis — Claude Code reads your evidence",
                            "Full Claude Code — your own set-up, skills and tools", "In-app assistant — your API key",
                            "Every assistant — the bridge, files and projects"), titles, "grouped by profile");
                    JCheckBox allowed = (JCheckBox) field(p, "claudeCodeToggle");
                    JCheckBox full = (JCheckBox) field(p, "claudeCodeFullToggle");
                    JTextField model = (JTextField) field(p, "claudeCodeModelField");
                    JCheckBox budget = (JCheckBox) field(p, "claudeCodeBudgetToggle");
                    assertFalse(allowed.isSelected(), "control: off by default");
                    assertFalse(full.isEnabled(), "the full switch needs Claude Code allowed");
                    assertFalse(model.isEnabled(), "so does the model");
                    assertFalse(budget.isEnabled(), "and the spend limit");
                    allowed.doClick();
                    assertTrue(full.isEnabled() && model.isEnabled() && budget.isEnabled(), "on: the children follow");
                    assertFalse(((javax.swing.JSpinner) field(p, "claudeCodeBudgetSpinner")).isEnabled(),
                            "the amount still needs its box");
                    allowed.doClick();
                    assertFalse(full.isEnabled() || model.isEnabled() || budget.isEnabled(), "off again: disabled again");
                } finally {
                    p.dispose();
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    /**
     * ★ The start page is where a new person first asks: with nothing chosen and nothing open, its question opens the
     * cards in the assistant's own window (the start page hides the docked tab), and no no-AI fallback beside them.
     * Witness: drop {@code popOut} from the first-use effect — the assistant window never shows.
     */
    @Test
    @DisplayName("★ the start page's first question pops the assistant out with the cards open")
    void theStartPagesFirstQuestionPopsOutTheCards(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        String before = System.getProperty("user.home");
        System.setProperty("user.home", java.nio.file.Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> {
                MainFrame f = new MainFrame();
                f.setSize(1200, 800);
                f.setVisible(true);
                frame.set(f);
            });
            onEdt(() -> {
                StartPanel start = StartPromptFrameTest.startPanel(frame.get());
                start.prompt().box().setText("What does this tool do with my logs?");
                start.prompt().actionButton().doClick();
            });
            ProfileState s = await(frame.get(), ProfileState::open, "the start page's question opens the cards");
            assertEquals(ProfileState.FIRST_USE, s.why());
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            AtomicReference<Boolean> popped = new AtomicReference<>(false);
            while (!popped.get() && System.nanoTime() < until) {
                onEdt(() -> {
                    javax.swing.JFrame w = (javax.swing.JFrame) field(frame.get(), "assistantWindow");
                    popped.set(w != null && w.isShowing()
                            && AssistantLiveFrameTest.panel(frame.get()).profileOverlay().isShowing());
                });
                Thread.sleep(30);
            }
            assertTrue(popped.get(), "the assistant's own window shows the cards");
            onEdt(() -> assertEquals(telamin.fluxtion.audit.analyser.analyser.session.node.AssistantProfileChoice.FIRST_USE_NOTE,
                    ((SessionDriver) field(frame.get(), "session")).snapshot().startPage().note(),
                    "the start page says why, and shows no fallback"));
        } finally {
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
            System.setProperty("user.home", before);
        }
    }
}
