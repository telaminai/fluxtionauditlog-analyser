package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #5: a tool line's mark — what the line shows and a screen reader speaks — names WHO decided, in
 * the person's words: you, the 120 s deadline, the profile, the mode. Never "the posture".
 */
class DenialWordsTest {

    /** ★ Witness: return one word for every POLICY denial (the old "denied: not asked in this posture") — this fails. */
    @Test
    @DisplayName("★ each denial's mark says who decided: you, the deadline, Safe analysis, the mode")
    void eachDenialSaysWhoDecided() {
        assertEquals("denied by you", LocalAgentAdapter.markWords(false, PermissionBy.PERSON, null));
        assertEquals("denied: not answered within 120 s", LocalAgentAdapter.markWords(false, PermissionBy.TIMEOUT, null));
        assertEquals("denied: not allowed in Safe analysis", LocalAgentAdapter.markWords(false, PermissionBy.POLICY,
                LocalAgentPermission.DENIED_DEFAULT_POSTURE));
        assertEquals("denied: not available in this Full Claude Code mode", LocalAgentAdapter.markWords(false,
                PermissionBy.POLICY, LocalAgentPermission.DENIED_HIDDEN));
        assertTrue(LocalAgentAdapter.markWords(false, PermissionBy.POLICY, LocalAgentPermission.DENIED_FILE_EXCHANGE)
                .startsWith("denied: file exchange is off"));
        assertEquals("approved by you", LocalAgentAdapter.markWords(true, PermissionBy.PERSON, null));
        assertEquals("allowed: it changes nothing", LocalAgentAdapter.markWords(true, PermissionBy.NON_MUTATING, null));
        for (PermissionBy by : PermissionBy.values()) {
            assertFalse(LocalAgentAdapter.markWords(false, by, null).contains("posture"), by.name());
        }
        assertEquals("denied by Claude Code's permission mode", StreamJson.DenialReason.MODE.words());
    }
}
