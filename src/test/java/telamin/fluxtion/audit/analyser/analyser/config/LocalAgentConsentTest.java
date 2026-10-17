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
            "assistant.claudeCode.full.mode=acceptEdits",
            "assistant.claudeCode.trustedFull.count=1",
            "assistant.claudeCode.trustedFull.0=/somewhere/else|0123456789abcdef0123456789abcdef",
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
        on.claudeCodeFullMode = "plan";
        on.claudeCodeTrustedFullFolders.add("/a/full/folder|00112233445566778899aabbccddeeff");
        on.claudeCodeBudgetUsd = 7.5;
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
        assertEquals("plan", back.claudeCodeFullMode);
        assertEquals(java.util.List.of("/a/full/folder|00112233445566778899aabbccddeeff"), back.claudeCodeTrustedFullFolders);
        assertEquals(7.5, back.claudeCodeBudgetUsd);
        AppConfig fresh = new ConfigStore(tmp.resolve("absent")).load();
        assertOff(fresh, "off by default");
        assertEquals("sonnet", fresh.claudeCodeModel);
        assertNull(fresh.claudeCodeBudgetUsd, "no spend limit by default (owner, 2026-10-17)");
    }

    private static void assertOff(AppConfig c, String what) {
        assertFalse(c.claudeCodeAllowed, what + " cannot allow Claude Code");
        assertFalse(c.claudeCodeFull, what + " cannot widen it to the full posture");
        assertFalse(c.claudeCodeWarmStart, what + " cannot turn on warm start");
        assertTrue(c.claudeCodeTrustedFolders.isEmpty(), what + " cannot add a trusted folder");
        assertTrue(c.claudeCodeTrustedFullFolders.isEmpty(), what + " cannot add a full-posture trusted folder");
        assertEquals("default", c.claudeCodeFullMode, what + " cannot choose the full posture's mode");
        assertNull(c.claudeCodeBudgetUsd, what + " cannot set a spend limit");
    }

    /**
     * ★ §5.1: bypassPermissions, dontAsk and anything unknown read as default, and Settings is told. Witness: pass the
     * stored string through in {@code ConfigStore.fullMode} — {@code bypassPermissions} comes back and the first
     * assertion fails.
     */
    @Test
    @DisplayName("★ a stored full mode the analyser does not offer reads as default, and says so")
    void unofferedModesReadAsDefault() throws Exception {
        for (String stored : new String[]{"bypassPermissions", "dontAsk", "manual", "yolo", "  PLAN  "}) {
            Path dir = Files.createDirectories(tmp.resolve("m-" + stored.trim()));
            Files.writeString(dir.resolve("config"), "assistant.claudeCode.full=true\nassistant.claudeCode.full.mode="
                    + stored + "\n");
            AppConfig c = new ConfigStore(dir.resolve("config")).load();
            if (stored.trim().equalsIgnoreCase("plan")) {
                assertEquals("plan", c.claudeCodeFullMode, "an offered mode, in any case, is kept");
                assertFalse(c.claudeCodeFullModeReadAsDefault);
            } else {
                assertEquals("default", c.claudeCodeFullMode, stored + " is never passed through");
                assertTrue(c.claudeCodeFullModeReadAsDefault, "Settings says the stored mode read as default");
            }
        }
        assertEquals(java.util.List.of("default", "acceptEdits", "auto", "plan"), ConfigStore.FULL_MODES);
    }

    /**
     * ★ owner, 2026-10-17: the budget is optional. Witness: read a P1a-written {@code 5.0} as a limit — the second
     * assertion fails; or read P1b's blank as 5.0 — the first fails.
     */
    @Test
    @DisplayName("★ the spend limit: blank or absent is none; P1a's unreleased default 5.0 reads as none; P1b's own 5.0 is kept")
    void theSpendLimitIsOptional() throws Exception {
        assertNull(ConfigStore.budget(null, null));
        assertNull(ConfigStore.budget("", "2"), "P1b writes blank for no limit");
        assertNull(ConfigStore.budget("5.0", null), "a config written by P1a carried its unreleased default, 5.0");
        assertEquals(12.0, ConfigStore.budget("12.0", null), "any other P1a value is honoured as the person's limit");
        assertEquals(5.0, ConfigStore.budget("5.0", "2"), "a limit of 5.0 chosen in P1b is kept");
        assertNull(ConfigStore.budget("-1", "2"));
        assertNull(ConfigStore.budget("x", "2"));

        Path dir = Files.createDirectories(tmp.resolve("budget"));
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        AppConfig c = new AppConfig();
        store.save(c);
        assertNull(store.load().claudeCodeBudgetUsd, "none round-trips as none");
        c.claudeCodeBudgetUsd = 5.0;
        store.save(c);
        assertEquals(5.0, store.load().claudeCodeBudgetUsd, "a deliberate 5.0 round-trips");
    }
}
