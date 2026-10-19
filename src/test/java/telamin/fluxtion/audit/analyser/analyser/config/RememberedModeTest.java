package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.8, OD-74.23, §10): the assistant mode the person last chose is remembered on THIS MACHINE, so a Claude
 * Code user relaunching with no API key is not sent to the start page's no-AI column — and it never travels.
 */
class RememberedModeTest {

    /** ★ OD-74.23. Witness: write lastMode in SettingsShare.export's ASSISTANT branch — the share assertion fails. */
    @Test
    @DisplayName("★ the remembered mode survives a relaunch on this machine and never reaches a profile, share or bundle")
    void theRememberedModeIsMachineTier(@TempDir Path tmp) throws Exception {
        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        AppConfig c = new AppConfig();
        assertEquals("IN_APP", c.claudeCodeLastMode, "in-app is the default");
        c.claudeCodeLastMode = "LOCAL_AGENT";
        store.save(c);
        assertEquals("LOCAL_AGENT", new ConfigStore(tmp.resolve("config")).load().claudeCodeLastMode, "survives a relaunch");
        Files.writeString(tmp.resolve("config"), Files.readString(tmp.resolve("config"))
                .replace("assistant.claudeCode.lastMode=LOCAL_AGENT", "assistant.claudeCode.lastMode=bypassPermissions"));
        assertEquals("IN_APP", new ConfigStore(tmp.resolve("config")).load().claudeCodeLastMode, "anything else is in-app");

        Path file = ProjectProfile.pathFor(tmp.resolve("p"));
        Files.createDirectories(file.getParent());
        ProjectProfile.save(file, c, new SettingsShare(tmp.toString()));
        assertFalse(Files.readString(file).contains("lastMode"), "never in a profile");
        String share = new SettingsShare(tmp.toString()).export(c, java.util.EnumSet.allOf(SettingsShare.Category.class));
        assertFalse(share.contains("lastMode"), "never in a share, whatever is ticked");
        AppConfig fromShare = new AppConfig();
        var share2 = new SettingsShare(tmp.toString());
        share2.apply(share2.preview("assistant.claudeCode.lastMode=LOCAL_AGENT\n", fromShare),
                java.util.EnumSet.allOf(SettingsShare.Category.class), fromShare);
        assertEquals("IN_APP", fromShare.claudeCodeLastMode, "a share cannot set it");
        assertFalse(telamin.fluxtion.audit.analyser.bundle.BundleProfile.CATEGORIES.contains(SettingsShare.Category.ASSISTANT),
                "and a bundle carries no assistant category at all");
    }
}
