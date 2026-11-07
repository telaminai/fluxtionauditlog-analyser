package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Client;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Origin;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAPID-OFF (2026-11-07) — the legacy key route is closed, and a key file left on the machine is noticed ONCE, by the
 * {@code aiSetup} node, on the REAL generated processor (rule 9: a fact in, the snapshot out; no hand-placed write, no
 * modal). The fact rides the existing start-up event, so this needs no regeneration.
 */
class LegacyKeyNoticeTest {

    private static SessionDriver started(FakeSessionAdapter a, boolean legacyKeyFile) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AiSetupStartupObserved(false, Client.NONE, false, legacyKeyFile));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        d.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        return d;
    }

    /** Witness: publish "" from {@code AiSetup.setupState()} whatever {@code legacyKeyFile} says — this fails. */
    @Test
    @DisplayName("a leftover key file is stated once, in the node's words, pointing at compiler sign-in")
    void aLeftoverKeyFileIsNoticed() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = started(a, true);
        String notice = d.snapshot().aiSetup().keyNotice();
        assertEquals(AiSetupLines.LEGACY_KEY_NOTICE, notice);
        assertTrue(notice.contains("AI ▸ Compiler account…"), notice);
        assertTrue(notice.contains("mvn fluxtion:login"), notice);
        for (String leak : new String[] {"apiKeyFile", "~", "/", "\\"})
            assertFalse(notice.contains(leak), "the notice names no path: " + leak);
        // non-modal: the notice is a line in the snapshot, never a warning or a window of its own
        assertEquals(0, a.performed.stream().filter(e -> e instanceof SessionEffects.ShowWarningEffect).count());
        assertEquals(0, a.setupShows.size(), "the notice opens nothing");
        // it does not repeat or change as the session moves on
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        assertEquals(notice, d.snapshot().aiSetup().keyNotice());
    }

    /** Witness: publish the notice unconditionally — this fails. */
    @Test
    @DisplayName("no key file, no notice; the start-up event's old shape means none was observed")
    void noKeyFileNoNotice() {
        assertEquals("", started(new FakeSessionAdapter(), false).snapshot().aiSetup().keyNotice());
        SessionDriver d = new SessionDriver(new FakeSessionAdapter());
        d.post(new SessionEvents.AiSetupStartupObserved(false, Client.NONE, false));
        assertEquals("", d.snapshot().aiSetup().keyNotice());
        assertEquals("", AiSetupState.IDLE.keyNotice());
    }
}
