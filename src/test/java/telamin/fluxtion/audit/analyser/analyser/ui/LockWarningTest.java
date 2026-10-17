package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Holistic review L-e (2026-10-17): the "don't resume this in a terminal" warning shows only when a conversation exists. */
class LockWarningTest {

    static LocalAgentState state(boolean resumable) {
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 0, "OFF", "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                resumable, 0, List.of(), null);
    }

    /** ★ Witness: the always-on warning (drop the setVisible in render) — it shows before any conversation exists. */
    @Test
    @DisplayName("★ the fork warning is hidden until there is a conversation to resume, then shown")
    void onlyAfterAConversationExists() {
        LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
        p.render(state(false));
        assertFalse(p.lockWarningLine().isVisible(), "nothing to resume: no warning");
        p.render(state(true));
        assertTrue(p.lockWarningLine().isVisible(), "a conversation exists: a terminal could fork it");
    }
}
