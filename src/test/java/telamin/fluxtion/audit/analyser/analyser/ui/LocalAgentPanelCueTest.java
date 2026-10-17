package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner request, 2026-10-17 (§5.9 "The full-posture cue"): the Claude Code composer, its badge and Send carry the cue,
 * rendered from the snapshot alone; and the armed Start reads its words (§5.2 item 6). Headless.
 */
class LocalAgentPanelCueTest {

    static LocalAgentState state(String phase, LocalAgentPosture running, LocalAgentPosture consent, String armed) {
        LocalAgentState i = LocalAgentState.IDLE;
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, phase, "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                false, 0, List.of(), i.answer(), running, 0, i.billing(), armed, consent);
    }

    @Test
    @DisplayName("badge text per mode (no preview label from P3, OD-74.26); running vs switched on; Send names the full posture; border token")
    void badge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
            p.render(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.ANALYSER_ASSISTANT, null));
            assertFalse(p.fullBadge().isVisible());
            assertEquals("Send", p.sendButton().getText());
            for (var m : LocalAgentPosture.FullMode.values()) {
                p.render(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.full(m), null));
                assertTrue(p.fullBadge().isVisible(), m.name());
                assertEquals((m == LocalAgentPosture.FullMode.AUTO || m == LocalAgentPosture.FullMode.ACCEPT_EDITS ? "◆◆ " : "◆ ")
                        + "Full Claude Code (" + m.cli() + ") — on: it runs at the next Start · your tools and "
                        + "permissions", p.fullBadge().getText());
                assertEquals("Send to full Claude Code", p.sendButton().getText());
                assertNotNull(p.fullBadge().getAccessibleContext().getAccessibleName());
                assertInstanceOf(LineBorder.class, p.draftScroll().getBorder());
            }
            p.render(state("READY", LocalAgentPosture.full(LocalAgentPosture.FullMode.PLAN), null, null));
            assertTrue(p.fullBadge().getText().contains("(plan) — running"), "the state first: " + p.fullBadge().getText());
            p.render(state("READY", LocalAgentPosture.ANALYSER_ASSISTANT, LocalAgentPosture.full(LocalAgentPosture.FullMode.PLAN),
                    null));
            assertFalse(p.fullBadge().isVisible(), "running in the DEFAULT posture: no cue, whatever the settings now say");
        });
    }

    @Test
    @DisplayName("an armed Start reads its words; nothing starts because it is armed")
    void armedStart() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
            p.render(state("OFF", LocalAgentPosture.ANALYSER_ASSISTANT, null, "armed.ASK"));
            assertEquals("Start — ask about this", p.startButton().getText());
            assertTrue(p.startButton().isEnabled());
        });
    }
}
