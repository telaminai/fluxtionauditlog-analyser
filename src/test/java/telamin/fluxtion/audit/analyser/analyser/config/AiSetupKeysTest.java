package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77 (spec-ai-setup-wizard.md §14.1, S9, ★ T14): the two keys the wizard adds — {@code assistant.setup.dismissed} and
 * {@code assistant.setup.client} — are machine tier: they survive a relaunch on this machine and never reach a profile,
 * a share or a bundle.
 */
class AiSetupKeysTest {

    /** ★ T14. Witness: write either key in {@code SettingsShare.export}'s ASSISTANT branch — the share assertion fails. */
    @Test
    @DisplayName("★ T14: the wizard's keys survive a relaunch and never reach a profile, a share or a bundle")
    void theWizardsKeysAreMachineTier(@TempDir Path tmp) throws Exception {
        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        AppConfig c = new AppConfig();
        assertFalse(c.assistantSetupDismissed, "not dismissed by default");
        assertNull(c.assistantSetupClient, "no client by default");
        c.assistantSetupDismissed = true;
        c.assistantSetupClient = "CODEX";
        c.assistantProfile = "SAFE";
        c.claudeCodeAllowed = true;
        store.save(c);
        AppConfig back = new ConfigStore(tmp.resolve("config")).load();
        assertTrue(back.assistantSetupDismissed, "survives a relaunch");
        assertEquals("CODEX", back.assistantSetupClient);
        Files.writeString(tmp.resolve("config"), Files.readString(tmp.resolve("config"))
                .replace("assistant.setup.client=CODEX", "assistant.setup.client=../../etc"));
        assertNull(new ConfigStore(tmp.resolve("config")).load().assistantSetupClient, "anything else is no client");

        Path file = ProjectProfile.pathFor(tmp.resolve("p"));
        Files.createDirectories(file.getParent());
        ProjectProfile.save(file, c, new SettingsShare(tmp.toString()));
        assertFalse(Files.readString(file).contains("assistant.setup"), "★ never in a profile");
        String share = new SettingsShare(tmp.toString()).export(c, java.util.EnumSet.allOf(SettingsShare.Category.class));
        assertFalse(share.contains("assistant.setup"), "★ never in a share, whatever is ticked");
        AppConfig fromShare = new AppConfig();
        var share2 = new SettingsShare(tmp.toString());
        share2.apply(share2.preview("assistant.setup.dismissed=true\nassistant.setup.client=CODEX\n", fromShare),
                java.util.EnumSet.allOf(SettingsShare.Category.class), fromShare);
        assertFalse(fromShare.assistantSetupDismissed, "★ a share cannot set it");
        assertNull(fromShare.assistantSetupClient);
        assertFalse(telamin.fluxtion.audit.analyser.bundle.BundleProfile.CATEGORIES.contains(SettingsShare.Category.ASSISTANT),
                "and a bundle carries no assistant category at all");
    }
}
