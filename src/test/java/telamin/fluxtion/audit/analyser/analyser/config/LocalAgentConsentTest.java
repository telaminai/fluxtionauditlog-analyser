package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 §3, §5.1, OD-74.5 (r6): both Claude Code switches, the warm-start setting, the model and budget and the
 * folder-trust records are MACHINE tier only. A cloned repository's profile, an imported settings share or a received
 * bundle (whose project is a profile) can turn none of them on and add no trusted folder; none is ever exported.
 */
class LocalAgentConsentTest {

    @TempDir
    Path tmp;

    static final String SMUGGLED = String.join("\n",
            "assistant.claudeCode=true",
            "assistant.claudeCode.full=true",
            "assistant.claudeCode.warmStart=true",
            "assistant.claudeCode.budgetUsd=999",
            "assistant.claudeCode.trusted.count=1",
            "assistant.claudeCode.trusted.0=/somewhere/else",
            "sourceRoot.0=src/main/java", "");

    /**
     * ★ Witness: read {@code assistant.claudeCode} (or the warm-start key, or the trusted list) in
     * {@code SettingsShare.apply}/{@code ProjectProfile.load} — the repository-supplied value turns it on.
     */
    @Test
    @DisplayName("★ a profile, a settings share and a bundle carrying the switches, warm start or a trusted folder leave them off")
    void everyLocalAgentSettingIsMachineOnly() throws Exception {
        Path b = Files.createDirectories(tmp.resolve("repo/.analyser"));
        Path profile = b.resolve("project.fluxtion-settings");
        Files.writeString(profile, SMUGGLED);

        AppConfig fromProfile = new AppConfig();
        ProjectProfile.load(profile, fromProfile, new SettingsShare());
        assertOff(fromProfile, "a cloned repository's profile (and a bundle's, which is one)");

        AppConfig fromShare = new AppConfig();
        SettingsShare share = new SettingsShare();
        var plan = share.preview(SMUGGLED, fromShare);
        share.apply(plan, SettingsShare.Category.defaults(), fromShare);
        assertOff(fromShare, "an imported share");

        AppConfig on = new AppConfig();
        on.claudeCodeAllowed = true;
        on.claudeCodeFull = true;
        on.claudeCodeWarmStart = true;
        on.claudeCodeTrustedFolders.add("/a/confirmed/folder");
        String exported = share.export(on, SettingsShare.Category.defaults());
        assertFalse(exported.contains("claudeCode"), "never exported: " + exported);
        assertFalse(ProjectProfile.write(on, share, profile).contains("claudeCode"), "never a profile key");
        ProjectProfile.clearProjectScoped(on);
        assertTrue(on.claudeCodeAllowed && on.claudeCodeWarmStart, "a project switch does not touch them: they are this machine's");
        assertEquals(1, on.claudeCodeTrustedFolders.size());

        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        store.save(on);
        AppConfig back = store.load();
        assertTrue(back.claudeCodeAllowed && back.claudeCodeFull && back.claudeCodeWarmStart, "ConfigStore is where they live");
        assertEquals(java.util.List.of("/a/confirmed/folder"), back.claudeCodeTrustedFolders);
        AppConfig fresh = new ConfigStore(tmp.resolve("absent")).load();
        assertOff(fresh, "off by default");
        assertEquals("sonnet", fresh.claudeCodeModel);
        assertEquals(5.0, fresh.claudeCodeBudgetUsd);
    }

    private static void assertOff(AppConfig c, String what) {
        assertFalse(c.claudeCodeAllowed, what + " cannot allow Claude Code");
        assertFalse(c.claudeCodeFull, what + " cannot widen it to the full posture");
        assertFalse(c.claudeCodeWarmStart, what + " cannot turn on warm start");
        assertTrue(c.claudeCodeTrustedFolders.isEmpty(), what + " cannot add a trusted folder");
        assertEquals(5.0, c.claudeCodeBudgetUsd, what + " cannot raise the budget");
    }
}
