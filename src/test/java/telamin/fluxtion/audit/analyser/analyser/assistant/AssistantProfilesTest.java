package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentConsents;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentProvider;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 UX (owner, 2026-10-17): what each assistant profile WRITES, and what that yields — the consent the frame reports
 * and the Claude Code command line the adapter builds from the posture the node decides. The profile overlay writes
 * through {@link AssistantProfiles#apply} and nothing else, so this is the overlay's whole effect on the settings.
 */
class AssistantProfilesTest {

    @TempDir
    Path tmp;

    /** The consent the frame would report for {@code c}, in the confirmed scratch folder (no hooks, no servers there). */
    SessionEvents.LocalAgentConsent consent(AppConfig c) throws Exception {
        Path scratch = Files.createDirectories(tmp.resolve("scratch"));
        return LocalAgentConsents.of(scratch, scratch, tmp.resolve("home"),
                new LocalAgentConsents.Settings(c.claudeCodeAllowed, c.claudeCodeFull, c.claudeCodeFullMode,
                        c.claudeCodeTrustedFolders, c.claudeCodeTrustedFullFolders, c.claudeCodeBudgetUsd), p -> false);
    }

    /** The command line the adapter builds for the posture {@code consent} names. */
    static List<String> line(SessionEvents.LocalAgentConsent consent, Path dir) {
        LocalAgentPosture posture = consent.full() ? LocalAgentPosture.full(consent.fullMode())
                : LocalAgentPosture.ANALYSER_ASSISTANT;
        return new ClaudeCodeProvider().command(new LocalAgentProvider.LaunchSpec(Path.of("claude"),
                "00000000-0000-4000-8000-000000000000", false, "sonnet", dir, List.of("java", "-jar", "analyser.jar"),
                dir.resolve("endpoint"), 0, posture));
    }

    /**
     * ★ Each card yields its consent and its launch line. Witness — the overlay writing the wrong posture: make
     * {@code apply(SAFE)} set {@code claudeCodeFull = true} — Safe analysis then launches without {@code --restricted}
     * and with the person's tools, and the SAFE row fails; or drop the FULL mode — the {@code acceptEdits} row fails.
     */
    @Test
    @DisplayName("★ each profile yields the right consent and the right Claude Code command line")
    void eachProfileYieldsItsConsentAndLaunchLine() throws Exception {
        Path dir = tmp.resolve("scratch");

        AppConfig safe = new AppConfig();
        AssistantProfiles.apply(AssistantProfile.SAFE, new AssistantProfiles.Details(null, "opus", null, null, null), safe);
        var c = consent(safe);
        assertTrue(c.allowed(), "Safe analysis allows Claude Code");
        assertFalse(c.full(), "and never the full posture");
        assertTrue(c.folderConfirmed(), "control: the scratch folder is confirmed");
        List<String> l = line(c, dir);
        assertTrue(l.contains("--restricted") && l.contains("--strict-mcp-config"), "reads the evidence only: " + l);
        assertEquals("Read,Grep,Glob", l.get(l.indexOf("--tools") + 1), "no shell, no edits, no web");
        assertEquals("default", l.get(l.indexOf("--permission-mode") + 1));
        assertEquals("LOCAL_AGENT", safe.claudeCodeLastMode, "the tab is put in Claude Code mode");
        assertEquals("opus", safe.claudeCodeModel);

        AppConfig full = new AppConfig();
        AssistantProfiles.apply(AssistantProfile.FULL, new AssistantProfiles.Details("acceptEdits", null, null, null, null),
                full);
        c = consent(full);
        assertTrue(c.allowed() && c.full(), "Full Claude Code allows the full posture");
        assertEquals(LocalAgentPosture.FullMode.ACCEPT_EDITS, c.fullMode());
        l = line(c, dir);
        assertFalse(l.contains("--restricted") || l.contains("--tools") || l.contains("--strict-mcp-config"),
                "the person's own set-up and tools: " + l);
        assertEquals("acceptEdits", l.get(l.indexOf("--permission-mode") + 1), "the mode the card chose");
        assertTrue(l.containsAll(LocalAgentPosture.PROMPT_FLAGS), "approvals are asked in the analyser (P3)");

        AppConfig inApp = new AppConfig();
        inApp.claudeCodeAllowed = true;                    // was on: In-app turns Claude Code off
        AssistantProfiles.apply(AssistantProfile.IN_APP, new AssistantProfiles.Details(null, null, "openai", "gpt-x",
                "sk-DEMO"), inApp);
        assertFalse(consent(inApp).allowed(), "In-app assistant: Claude Code is not started");
        assertEquals("openai", inApp.llmProvider);
        assertEquals("gpt-x", inApp.llmModel);
        assertEquals("sk-DEMO", inApp.apiKey);
        assertEquals("IN_APP", inApp.claudeCodeLastMode);
        assertEquals(AssistantProfile.IN_APP, AssistantProfiles.inForce(inApp));

        AppConfig off = new AppConfig();
        off.apiKey = "sk-DEMO";
        off.claudeCodeAllowed = true;
        off.claudeCodeFull = true;
        AssistantProfiles.apply(AssistantProfile.OFF, AssistantProfiles.Details.NONE, off);
        assertFalse(consent(off).allowed() || consent(off).full(), "Off: Claude Code is not started");
        assertTrue(AssistantProfiles.off(off), "and the in-app assistant does not answer, though the key is kept");
        assertEquals("sk-DEMO", off.apiKey, "nothing the person stored is deleted");
    }

    @Test
    @DisplayName("a blank key keeps the stored one; an invalid Claude Code model is never written")
    void detailsNeverDestroyOrInject() {
        AppConfig c = new AppConfig();
        c.apiKey = "sk-kept";
        AssistantProfiles.apply(AssistantProfile.IN_APP, new AssistantProfiles.Details(null, null, "anthropic", "", ""), c);
        assertEquals("sk-kept", c.apiKey);
        AssistantProfiles.apply(AssistantProfile.SAFE, new AssistantProfiles.Details(null, "--dangerously", null, null,
                null), c);
        assertEquals("sonnet", c.claudeCodeModel, "a flag-shaped model is refused (review S7)");
        assertThrows(IllegalArgumentException.class, () -> AssistantProfiles.apply(AssistantProfile.NONE, null, c));
    }

    @Test
    @DisplayName("the profile in force follows the switches: nothing chosen, legacy set-ups, Off, and Settings changes")
    void inForceFollowsTheSwitches() {
        AppConfig c = new AppConfig();
        assertEquals(AssistantProfile.NONE, AssistantProfiles.inForce(c), "a fresh machine: nothing chosen");
        c.claudeCodeAllowed = true;
        assertEquals(AssistantProfile.SAFE, AssistantProfiles.inForce(c), "Claude Code set up before profiles existed");
        c.claudeCodeFull = true;
        assertEquals(AssistantProfile.FULL, AssistantProfiles.inForce(c));
        c = new AppConfig();
        c.apiKey = "sk-DEMO";
        assertEquals(AssistantProfile.IN_APP, AssistantProfiles.inForce(c), "an API key set before profiles existed");
        AssistantProfiles.apply(AssistantProfile.OFF, null, c);
        assertEquals(AssistantProfile.OFF, AssistantProfiles.inForce(c), "Off stays off with a key stored");
        c.claudeCodeAllowed = true;                         // Settings turned Claude Code on
        assertEquals(AssistantProfile.SAFE, AssistantProfiles.inForce(c), "a switch turned on ends Off");
        c = new AppConfig();
        AssistantProfiles.apply(AssistantProfile.SAFE, null, c);
        c.claudeCodeAllowed = false;                        // Settings turned it off again, no key
        assertEquals(AssistantProfile.OFF, AssistantProfiles.inForce(c), "chosen, then switched off: Off, not unchosen");
        assertEquals(AssistantMode.LOCAL_AGENT, AssistantProfile.FULL.mode());
        assertEquals(AssistantMode.IN_APP, AssistantProfile.OFF.mode());
    }

    @Test
    @DisplayName("the stored profile is machine tier: it round-trips in ConfigStore and nothing else reads it")
    void theStoredProfileRoundTrips() throws Exception {
        AppConfig c = new AppConfig();
        AssistantProfiles.apply(AssistantProfile.FULL, new AssistantProfiles.Details("plan", null, null, null, null), c);
        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        store.save(c);
        AppConfig back = store.load();
        assertEquals("FULL", back.assistantProfile);
        assertEquals("plan", back.claudeCodeFullMode);
        Files.writeString(tmp.resolve("config"), Files.readString(tmp.resolve("config"))
                .replace("assistant.claudeCode.profile=FULL", "assistant.claudeCode.profile=bypassPermissions"));
        assertNull(new ConfigStore(tmp.resolve("config")).load().assistantProfile, "anything else reads as not chosen");
    }
}
