package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.QuickSwitch;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M77-4 (spec-ai-setup-wizard.md §14.3, OD-77.3) in the REAL frame: the Assistant ▾ quick switch that replaced the four
 * profile cards — ✓ Safe analysis · ◆ Full Claude Code… · In-app assistant · Off · ─── · Set up AI…. Each row writes the
 * same machine-tier settings Settings ▸ Assistant writes, through {@code aiSetup}'s effect; Full asks its confirmation
 * first (Cancel the default); the in-app assistant with no key opens Set up AI at Other options instead. Driven by
 * direct dispatch (doClick, setters), because posted key events and Robot presses are dropped on this Mac.
 *
 * <p>Successor of the cards' ProfileOverlayFrameTest: what it protected and still applies (each profile's settings,
 * Full's confirmation with Cancel the default, nothing stored deleted by Off, Settings' grouping) is kept here.
 */
class QuickSwitchFrameTest {

    static ProfileState profiles(MainFrame f) {
        return ((SessionDriver) field(f, "session")).snapshot().profiles();
    }

    static AiSetupState setup(MainFrame f) {
        return ((SessionDriver) field(f, "session")).snapshot().aiSetup();
    }

    static <T> T await(MainFrame f, java.util.function.Function<MainFrame, T> read, Predicate<T> done, String what)
            throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        AtomicReference<T> s = new AtomicReference<>();
        do {
            onEdt(() -> s.set(read.apply(f)));
            if (done.test(s.get())) return s.get();
            Thread.sleep(30);
        } while (System.nanoTime() < until);
        throw new AssertionError(what + ": " + s.get());
    }

    static ProfileState await(MainFrame f, Predicate<ProfileState> done, String what) throws Exception {
        return await(f, QuickSwitchFrameTest::profiles, done, what);
    }

    /** Review PR151 F4: Full Claude Code's confirmation, once it is showing (modeless in the test). */
    static SafeChoice.Asked awaitConfirmation(MainFrame f) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        AtomicReference<SafeChoice.Asked> a = new AtomicReference<>();
        while (System.nanoTime() < until) {
            onEdt(() -> a.set(f.fullProfileAsked != null && f.fullProfileAsked.dialog().isShowing() ? f.fullProfileAsked : null));
            if (a.get() != null) return a.get();
            Thread.sleep(30);
        }
        throw new AssertionError("the Full Claude Code confirmation never showed");
    }

    static AppConfig config(MainFrame f) {
        return (AppConfig) field(f, "config");
    }

    static List<JMenuItem> rows(JPopupMenu m) {
        List<JMenuItem> out = new ArrayList<>();
        for (Component c : m.getComponents()) if (c instanceof JMenuItem i) out.add(i);
        return out;
    }

    /** The row for {@code p} in a quick switch menu. */
    static JMenuItem item(JPopupMenu m, AssistantProfile p) {
        for (JMenuItem i : rows(m)) if (i.getClientProperty(QuickSwitch.PROFILE_KEY) == p) return i;
        throw new AssertionError("no row for " + p);
    }

    /** The quick switch's Set up AI… row. */
    static JMenuItem setUpItem(JPopupMenu m) {
        for (JMenuItem i : rows(m)) if (Boolean.TRUE.equals(i.getClientProperty(QuickSwitch.SET_UP_KEY))) return i;
        throw new AssertionError("no Set up AI… row");
    }

    /** Press Assistant ▾, then the row for {@code p} — as the person does. */
    static void choose(MainFrame f, AssistantPanel a, AssistantProfile p) throws Exception {
        onEdt(() -> a.profileButton().doClick());
        onEdt(() -> {
            JPopupMenu m = a.quickSwitch().lastShown();
            assertNotNull(m, "Assistant ▾ shows the quick switch");
            JMenuItem row = item(m, p);
            m.setVisible(false);
            row.doClick();
        });
    }

    /**
     * ★ Each row writes its settings; Full is confirmed first; In-app with no key opens Set up AI at Other options.
     * Witnesses: make {@code applyAssistantProfile} skip {@code AssistantProfiles.apply} — the config assertions fail;
     * drop the In-app check in {@code AiSetup.onProfileChosen} — In-app is written with no key and no wizard opens;
     * attach nothing to the header control (the cards' button) — no quick switch is shown.
     */
    @Test
    @DisplayName("★ Assistant ▾: Safe, ◆ Full (confirmed), In-app (its key through Set up AI) and Off each write their settings")
    void eachRowWritesItsSettings(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new AssistantFirstRunFrameTest.Fresh(tmp, 1300, 900)) {
            MainFrame frame = f.frame;
            AssistantPanel a = f.assistant();
            assertEquals(AssistantProfile.NONE, profiles(frame).profile(), "a fresh machine: nothing chosen");
            config(frame).claudeCodeFullMode = "acceptEdits";       // what Settings ▸ Assistant set; the switch keeps it
            onEdt(() -> {
                a.profileButton().doClick();
                JPopupMenu m = a.quickSwitch().lastShown();
                List<String> labels = rows(m).stream().map(JMenuItem::getText).toList();
                assertEquals(List.of("Safe analysis", "◆ Full Claude Code…", "In-app assistant", "Off", "Set up AI…"),
                        labels, "★ the quick switch's rows, in order");
                for (JMenuItem r : rows(m)) {
                    if (r instanceof JRadioButtonMenuItem rb) assertFalse(rb.isSelected(), "nothing chosen: no row ticked");
                }
                m.setVisible(false);
            });

            // Safe analysis
            choose(frame, a, AssistantProfile.SAFE);
            await(frame, s -> s.profile() == AssistantProfile.SAFE, "Safe applied");
            onEdt(() -> {
                AppConfig c = config(frame);
                assertTrue(c.claudeCodeAllowed, "Safe analysis allows Claude Code");
                assertFalse(c.claudeCodeFull, "and never the full posture");
                assertEquals("SAFE", c.assistantProfile);
                assertEquals(AssistantMode.LOCAL_AGENT, UnifiedAssistantFrameTest.mode(frame), "the tab is in Claude Code mode");
                assertEquals("Safe ▾", a.profileButton().getText(), "M78 D2: the mode chip");
                a.profileButton().doClick();
                JMenuItem safe = item(a.quickSwitch().lastShown(), AssistantProfile.SAFE);
                assertTrue(safe.isSelected(), "★ the row in force is ticked");
                assertEquals("Safe analysis, in force", safe.getAccessibleContext().getAccessibleName(),
                        "and says so in words");
                a.quickSwitch().lastShown().setVisible(false);
            });

            // ◆ Full Claude Code — review PR151 F4: confirmed as in Settings first, Cancel the default
            frame.fullProfileConfirmModal = false;
            choose(frame, a, AssistantProfile.FULL);
            SafeChoice.Asked asked = awaitConfirmation(frame);
            onEdt(() -> {
                assertSame(asked.safeButton(), asked.dialog().getRootPane().getDefaultButton(), "Cancel is the default");
                assertEquals(ConfigPanel.FULL_POSTURE_CANCEL, asked.safeButton().getText());
                assertFalse(asked.riskyButton().isDefaultCapable(), "Enter can never turn it on");
                assertTrue(profiles(frame).confirming(), "the node waits for the answer");
                assertFalse(config(frame).claudeCodeFull, "★ nothing is applied before the person confirms");
                asked.dialog().getRootPane().getActionMap().get("safe-choice").actionPerformed(
                        new java.awt.event.ActionEvent(asked.dialog(), 0, "safe-choice"));     // Escape chooses Cancel
            });
            await(frame, s -> !s.confirming(), "Cancel answered");
            onEdt(() -> {
                assertFalse(config(frame).claudeCodeFull, "★ Cancel leaves the posture unchanged");
                assertEquals(AssistantProfile.SAFE, profiles(frame).profile());
                frame.fullProfileAsked = null;
            });
            choose(frame, a, AssistantProfile.FULL);
            SafeChoice.Asked again = awaitConfirmation(frame);
            onEdt(() -> again.riskyButton().doClick());
            await(frame, s -> s.profile() == AssistantProfile.FULL, "Full applied");
            onEdt(() -> {
                AppConfig c = config(frame);
                assertTrue(c.claudeCodeAllowed && c.claudeCodeFull, "the full posture");
                assertEquals("acceptEdits", c.claudeCodeFullMode, "★ the mode Settings set is kept");
                assertEquals("FULL", c.assistantProfile);
            });

            // In-app with no key stored: Set up AI opens at Other options; nothing is written
            choose(frame, a, AssistantProfile.IN_APP);
            AiSetupState other = await(frame, QuickSwitchFrameTest::setup, AiSetupState::open, "Set up AI opens");
            assertEquals(AiSetupState.Step.OTHER, other.step(), "★ at the key field");
            onEdt(() -> {
                assertEquals("FULL", config(frame).assistantProfile, "★ nothing written without a key");
                AiSetupWindow w = frame.aiSetupWindow;
                w.inApp.apiKey.setText("sk-DEMO-quick");
                w.primaryButton().doClick();                          // Finish
            });
            await(frame, s -> s.profile() == AssistantProfile.IN_APP, "In-app applied by the wizard");
            onEdt(() -> {
                AppConfig c = config(frame);
                assertFalse(c.claudeCodeAllowed, "In-app: Claude Code is not started");
                assertEquals("sk-DEMO-quick", c.apiKey);
                assertEquals(AssistantMode.IN_APP, UnifiedAssistantFrameTest.mode(frame));
            });
            await(frame, QuickSwitchFrameTest::setup, s -> s.step() == AiSetupState.Step.SUMMARY || !s.open(), "Summary");
            onEdt(() -> { if (frame.aiSetupWindow.isShowing()) frame.aiSetupWindow.cancelButton().doClick(); });
            await(frame, QuickSwitchFrameTest::setup, s -> !s.open(), "closed");

            // Off: no AI, the key kept
            choose(frame, a, AssistantProfile.OFF);
            await(frame, s -> s.profile() == AssistantProfile.OFF, "Off applied");
            onEdt(() -> {
                assertEquals("sk-DEMO-quick", config(frame).apiKey, "nothing the person stored is deleted");
                assertEquals(AssistantPanel.OFF_LIMITS, a.limitsLine().getText(), "the panel says the assistant is off");
                assertFalse(a.sendButton().isEnabled(), "and nothing is sent");
            });

            // In-app again: a key is stored now, so the switch applies at once
            choose(frame, a, AssistantProfile.IN_APP);
            await(frame, s -> s.profile() == AssistantProfile.IN_APP, "In-app switched at once");
            onEdt(() -> assertFalse(setup(frame).open(), "no wizard: the switch could apply"));
        }
    }

    /**
     * ★ Settings ▸ Assistant keeps every option (§14.2), grouped by profile: a child control is enabled only while its
     * parent is on. Witness: drop the {@code childrenFollowParents()} listener on the Claude Code switch — the enabled
     * assertions after it fail.
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
                    // OD-77.12: the client dialog is behind Set up AI ▸ More options…, not a button on this page
                    for (javax.swing.AbstractButton b : UnifiedAssistantFrameTest.all(p.getContentPane(),
                            javax.swing.AbstractButton.class)) {
                        assertNotEquals("Connect an AI client…", b.getText(), "★ no second door to the client dialog");
                    }
                    assertTrue(UnifiedAssistantFrameTest.all(p.getContentPane(), javax.swing.AbstractButton.class).stream()
                            .anyMatch(b -> AiSetupLines.SET_UP_LINK.equals(b.getText())), "Set up AI… is the door");
                } finally {
                    p.dispose();
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }
}
