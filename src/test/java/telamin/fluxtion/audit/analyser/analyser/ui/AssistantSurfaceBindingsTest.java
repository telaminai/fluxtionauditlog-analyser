package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review S2 (P2, 2026-10-17): the composer keeps EVERY composed draft's highlight and binding, so each is checked at Send.
 * Headless: a JTextArea and its highlighter need no display.
 */
class AssistantSurfaceBindingsTest {

    /** ★ Witness: clear the earlier marks in {@code appendDraft} (the old surface) — only [22] is reported. */
    @Test
    @DisplayName("★ two appended drafts both stay bound; deleting one wholly drops only its binding")
    void everyDraftStaysBound() throws Exception {
        AssistantSurface s = new AssistantSurface();
        s.appendDraft(AssistantMode.LOCAL_AGENT, 21, "first question", null);
        int second = s.appendDraft(AssistantMode.LOCAL_AGENT, 22, "second question", null);
        assertEquals(List.of(21L, 22L), s.bindings(AssistantMode.LOCAL_AGENT));
        s.draft.getDocument().remove(0, "first question".length());
        assertEquals(List.of(22L), s.bindings(AssistantMode.LOCAL_AGENT), "the first draft's text is gone");
        s.accepted();
        assertEquals(List.of(), s.bindings(AssistantMode.LOCAL_AGENT));
        assertEquals(true, second > 0);
    }

    /** A draft placed for the other assistant is still reported, so the node in force refuses it as unknown. */
    @Test
    @DisplayName("a draft placed for the other mode is reported too, never dropped silently")
    void theOtherModesDraftIsReported() {
        AssistantSurface s = new AssistantSurface();
        s.appendDraft(AssistantMode.IN_APP, 7, "in-app question", null);
        assertEquals(List.of(7L), s.bindings(AssistantMode.LOCAL_AGENT));
    }
}
