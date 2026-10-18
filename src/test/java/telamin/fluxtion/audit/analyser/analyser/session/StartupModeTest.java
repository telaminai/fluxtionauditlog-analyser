package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77 (spec-ai-setup-wizard.md §8.5, review R1-3, ★ T21): the assistant mode a session starts in is DERIVED from the
 * profile in force, for every profile — not posted only when the remembered mode says Claude Code.
 *
 * <p>Deviation recorded in the PR: P-2 (merged before M77) holds a question asked before set-up unbound
 * ({@code AssistantDraftPending}), which is the "unbound no-assistant-yet state" §8.5 says M77 uses when P-2 has one; so
 * {@code AssistantMode.NONE} is not added here (OD-77.6 places it with P-2's first-run fixes). NONE and Off start in the
 * in-app mode, where the in-app node refuses a Send for want of a key or under Off, in its own handler, as before.
 */
class StartupModeTest {

    /** The start-up mode the frame posts, for {@code c} (the same expression {@code postStartupFacts} uses). */
    static AssistantMode startupMode(AppConfig c) {
        return AiSetupDerived.startupMode(AssistantProfiles.inForce(c));
    }

    /** ★ T21. Witness: keep the LOCAL_AGENT-only start-up post — the first two configurations start in the wrong mode. */
    @Test
    @DisplayName("★ T21: the start-up mode follows the profile in force, whatever the remembered mode says")
    void theStartupModeIsDerived() throws Exception {
        // an upgrader: a key stored before profiles, and a remembered mode from a Claude Code trial long ago
        AppConfig upgrader = new AppConfig();
        upgrader.apiKey = "sk-DEMO-not-a-key";
        upgrader.claudeCodeLastMode = "LOCAL_AGENT";
        assertEquals(AssistantProfile.IN_APP, AssistantProfiles.inForce(upgrader));
        assertEquals(AssistantMode.IN_APP, startupMode(upgrader), "★ the in-app assistant keeps answering");

        // Safe analysis chosen, remembered mode in-app (switched tab once), no key: Claude Code is what can answer
        AppConfig safe = new AppConfig();
        safe.claudeCodeAllowed = true;
        safe.assistantProfile = "SAFE";
        safe.claudeCodeLastMode = "IN_APP";
        assertEquals(AssistantMode.LOCAL_AGENT, startupMode(safe), "★ the profile's mode");

        AppConfig nothing = new AppConfig();
        assertEquals(AssistantProfile.NONE, AssistantProfiles.inForce(nothing));
        assertEquals(AssistantMode.IN_APP, startupMode(nothing), "nothing set up: the in-app mode, which refuses with no key");
        AppConfig off = new AppConfig();
        off.assistantProfile = "OFF";
        assertEquals(AssistantMode.IN_APP, startupMode(off));

        // and the frame posts exactly that, for every profile — no remembered-mode branch left
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        String body = telamin.fluxtion.audit.analyser.analyser.ui.OneDispatchModelTestAccess.bodyOf(frame,
                "private void postStartupFacts(");
        assertTrue(body.contains("AiSetupDerived.startupMode("), "★ the start-up post is the derived mode");
        assertFalse(body.contains("claudeCodeLastMode"), "★ no LOCAL_AGENT-only branch on the remembered mode");
    }

    /** The upgrader's first Send, end to end on the processor: accepted by the in-app assistant. */
    @Test
    @DisplayName("★ T21: an upgrader with a stored key and a stale remembered mode can Send on first launch")
    void anUpgraderCanSend() {
        AppConfig upgrader = new AppConfig();
        upgrader.apiKey = "sk-DEMO-not-a-key";
        upgrader.claudeCodeLastMode = "LOCAL_AGENT";
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantModeChosen(startupMode(upgrader), "derived from the profile in force"));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfiles.inForce(upgrader)));
        d.post(new SessionEvents.StartPageAvailability(false, false, true, false));
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(d.snapshot().assistant().busy(), "★ the turn runs: " + d.snapshot().assistant().reason());
        assertEquals(1, a.assistantContexts.size());
    }
}
