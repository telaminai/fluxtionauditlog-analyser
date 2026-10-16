package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M74 review S5 — the Claude Code header: the session's own launch folder, and nothing before the session is bound. */
class LocalAgentHeaderTest {

    static LocalAgentState state(String phase, long headerRef) {
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, phase, "", "", 0, 0, headerRef, 1, 0, 0, 0, 0, 0,
                false, false, false, 0, List.of(), LocalAgentState.Answer.NONE);
    }

    /** ★ Witness: return the header in {@code shownHeader} whatever the phase — the BINDING assertion fails. */
    @Test
    @DisplayName("★ S5: no header details before BOUND; once bound, the launch folder from the session's own record")
    void theHeaderIsTheSessionsOwn() {
        LocalAgentTranscript store = new LocalAgentTranscript();
        long ref = store.putHeader(new LocalAgentTranscript.Header("claude-sonnet-5", "2.1.282", "<subscription>",
                "firstParty", List.of(), "/work/launched-here"));
        for (String phase : List.of("SPAWNING", "HANDSHAKE", "BINDING")) {
            assertNull(LocalAgentPanel.shownHeader(state(phase, ref), store), phase);
            assertFalse(LocalAgentPanel.headerLine(null, state(phase, ref)).contains("launched-here"));
        }
        var shown = LocalAgentPanel.shownHeader(state("READY", ref), store);
        String line = LocalAgentPanel.headerLine(shown, state("READY", ref));
        assertTrue(line.contains("/work/launched-here"), line);
        assertTrue(line.contains("CLI 2.1.282") && line.contains("<subscription>") && line.contains("firstParty"), line);
    }
}
