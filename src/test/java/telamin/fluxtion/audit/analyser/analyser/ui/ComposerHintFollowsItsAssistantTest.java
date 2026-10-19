package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robot re-walk #4 (2026-10-18), headless: a question placed for Claude Code lit the composer with "Press Send to ask
 * Claude", and the hint stayed after the In-app card was applied. The hint names the assistant its draft was placed for,
 * so it shows only while that assistant is the one shown, and never while suppressed (Send cannot be pressed).
 */
class ComposerHintFollowsItsAssistantTest {

    /** ★ Witness: make {@code renderHint} ignore {@code hintMode} — the in-app assertion fails. */
    @Test
    @DisplayName("★ \"Press Send to ask Claude\" shows only while Claude Code is the assistant shown, and never when suppressed")
    void theHintFollowsItsAssistant() {
        AssistantSurface s = new AssistantSurface();
        s.appendDraft(AssistantMode.LOCAL_AGENT, 7, "What happened here?", "Press Send to ask Claude — nothing has been sent");
        assertTrue(s.composerHint.isVisible(), "placed for Claude Code: the hint shows");
        s.renderHint(AssistantMode.IN_APP, false);
        assertFalse(s.composerHint.isVisible(), "★ the in-app assistant is shown: the hint is not its words");
        s.renderHint(AssistantMode.LOCAL_AGENT, false);
        assertTrue(s.composerHint.isVisible(), "back to Claude Code: the hint is true again");
        s.renderHint(AssistantMode.LOCAL_AGENT, true);
        assertFalse(s.composerHint.isVisible(), "★ suppressed (a refusal, or Send cannot be pressed): hidden");
        s.clearDraftMarks();
        s.renderHint(AssistantMode.LOCAL_AGENT, false);
        assertFalse(s.composerHint.isVisible(), "no draft, no hint");
    }
}
