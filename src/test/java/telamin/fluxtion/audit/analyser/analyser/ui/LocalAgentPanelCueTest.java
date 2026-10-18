package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;

import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner request, 2026-10-17 (§5.9 "The full-posture cue"), and M78 D1 (spec-quiet-assistant-panel.md §3, TQ2): the full
 * cue is ◆ on the mode control, the accent border on the composer and "◆ Send" (its accessible name "Send to full Claude
 * Code") — visible in the narrow dock — rendered from the snapshot alone; and the armed Start keeps its fixed words
 * (§5.2 item 6). Headless.
 */
class LocalAgentPanelCueTest {

    static LocalAgentState state(String phase, LocalAgentPosture running, LocalAgentPosture consent, String armed) {
        LocalAgentState i = LocalAgentState.IDLE;
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, phase, "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                false, 0, List.of(), i.answer(), running, 0, i.billing(), armed, consent);
    }

    /** The node's guide on the state, as the session publishes it (the chip word comes with it). */
    static LocalAgentState published(LocalAgentState s) {
        var x = s.extra();
        return s.withExtra(new LocalAgentState.Extra(x.setupDirRef(), x.setupDirShown(), x.unknownEvents(),
                x.unclassifiedCommands(), x.droppedLines(), x.turns(), x.trimmed(), x.attention(), x.restart(),
                telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.guide(s)));
    }

    @Test
    @DisplayName("Send names the full posture with ◆ (and in its accessible name); the accent border; the 13-word badge is gone")
    void badge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
            p.render(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.ANALYSER_ASSISTANT, null));
            assertFalse(p.fullBadge().isVisible());
            assertEquals("Send", p.sendButton().getText());
            assertEquals("Send", p.sendButton().getAccessibleContext().getAccessibleName());
            for (var m : LocalAgentPosture.FullMode.values()) {
                p.render(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.full(m), null));
                boolean strong = m == LocalAgentPosture.FullMode.AUTO || m == LocalAgentPosture.FullMode.ACCEPT_EDITS;
                assertFalse(p.fullBadge().isVisible(), "the badge is gone: its sentence is the mode control's (" + m + ")");
                assertEquals((strong ? "◆◆" : "◆") + " Send", p.sendButton().getText(), m.name());
                assertEquals(LocalAgentPanel.SEND_TO_FULL, p.sendButton().getAccessibleContext().getAccessibleName());
                assertInstanceOf(LineBorder.class, p.draftScroll().getBorder());
                assertEquals("Full Claude Code (" + m.cli() + ") — on: it runs at the next Start · your tools and "
                        + "permissions", LocalAgentPanel.fullBadgeText(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT,
                        LocalAgentPosture.full(m), null)), "the sentence, for the control's name and tooltip");
            }
            assertTrue(LocalAgentPanel.fullBadgeText(state("READY", LocalAgentPosture.full(LocalAgentPosture.FullMode.PLAN),
                    null, null)).contains("(plan) — running"));
            p.render(state("READY", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.full(LocalAgentPosture.FullMode.PLAN),
                    null));
            assertEquals("Send", p.sendButton().getText(), "running in the DEFAULT posture: no cue, whatever the settings say");
            assertFalse(p.draftScroll().getBorder() instanceof LineBorder lb
                    && lb.getLineColor().equals(TranscriptStyle.fullPosture(false, ThemeManager.isDark())));
        });
    }

    static void layout(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component x : k.getComponents()) layout(x);
        }
    }

    /**
     * ★ TQ2: the Full cue at the 200 px dock and at 460 px — ◆ on the mode control, the accent composer border, ◆ in Send
     * and "Send to full Claude Code" as its name — and NONE of them under Safe analysis. Witnesses: drop the border
     * ({@code surface.cueBorder(c, …)} in {@code renderFullCue}) — the border assertion fails; or show ◆ under Safe
     * ({@code cueMark} returning "◆" whatever the cue) — the Safe assertions fail.
     */
    @Test
    @DisplayName("★ TQ2: the Full cue at 200 and 460 px — ◆ on the control, the border, ◆ Send and its name; all absent under Safe")
    void theFullCueInTheNarrowDock() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript(), a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            a.renderProfiles(new ProfileState(AssistantProfile.FULL, false, "", 0, "", false));
            p.render(published(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT,
                    LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT), null)));
            for (int width : new int[]{200, 460}) {
                a.setSize(width, 431);
                layout(a);
                layout(a);
                String control = a.profileButton().getText();
                assertTrue(control.contains("◆"), "★ ◆ on the mode control at " + width + " px: " + control);
                assertTrue(a.profileButton().getAccessibleContext().getAccessibleName().contains("Full Claude Code (default)"),
                        "the badge's sentence is the control's name");
                assertTrue(p.draftScroll().getBorder() instanceof LineBorder lb
                        && lb.getLineColor().equals(TranscriptStyle.fullPosture(false, ThemeManager.isDark())),
                        "★ the accent composer border at " + width + " px");
                assertTrue(a.sendButton().getText().startsWith("◆"), "★ ◆ Send at " + width + " px");
                assertEquals(LocalAgentPanel.SEND_TO_FULL, a.sendButton().getAccessibleContext().getAccessibleName());
                assertEquals(AssistantSurface.FULL_PLACEHOLDER, a.surface().placeholder());
            }
            a.renderProfiles(new ProfileState(AssistantProfile.SAFE, false, "", 0, "", false));
            p.render(published(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.ANALYSER_ASSISTANT, null)));
            for (int width : new int[]{200, 460}) {
                a.setSize(width, 431);
                layout(a);
                layout(a);
                assertFalse(a.profileButton().getText().contains("◆"), "★ no ◆ under Safe: " + a.profileButton().getText());
                assertFalse(a.sendButton().getText().contains("◆"), "★ no ◆ in Send under Safe");
                assertEquals("Send", a.sendButton().getAccessibleContext().getAccessibleName());
                assertFalse(p.draftScroll().getBorder() instanceof LineBorder, "★ no accent border under Safe");
                assertEquals(AssistantSurface.COMPOSER_PLACEHOLDER, a.surface().placeholder());
            }
        });
    }

    @Test
    @DisplayName("robot walkthrough #3: an armed Start keeps its fixed label; the one instruction says Send asks the placed question")
    void armedStart() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
            LocalAgentState s = state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, null, "armed.ASK");
            p.render(s);
            assertEquals("Start Claude Code", p.startButton().getText(), "never \"Start — ask about this\"");
            assertTrue(p.startButton().isEnabled());
            assertEquals(telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.PLACED_NOT_RUNNING_TEXT,
                    telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.guide(s).instruction());
        });
    }
}
