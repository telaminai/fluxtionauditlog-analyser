package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class FirstMinuteConfigTest {
    @Test void firstMinuteIsMachineOnlyAndNotAnActionableSavedWalkField(@TempDir Path tmp) throws Exception {
        var config = new AppConfig(); config.firstMinutePlayed = true; config.firstMinuteDomain = "generic";
        config.walks.add(telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition.walk());
        var files = new ConfigStore(tmp.resolve("config")); files.save(config);
        assertTrue(files.load().firstMinutePlayed);
        assertEquals("generic", files.load().firstMinuteDomain);
        assertTrue(KnownKeys.CONFIG_FAMILIES.contains("firstMinute"));
        assertFalse(KnownKeys.PROFILE_FAMILIES.contains("firstMinute"));
        String share = new SettingsShare("").export(config, EnumSet.allOf(SettingsShare.Category.class));
        assertFalse(share.contains("firstMinute."), "the machine's played/domain fields never enter a share");
        assertFalse(share.contains("walk.0.stage="), "a shipped layout is not a new saved-walk field");
        var target = new AppConfig();
        var protocol = new SettingsShare("");
        var plan = protocol.preview("share.version=1\nfirstMinute.played=true\nfirstMinute.domain=trading\n", target);
        protocol.apply(plan, plan.present(), target);
        assertFalse(target.firstMinutePlayed, "a profile cannot set the machine's played bit");
        assertEquals("generic", target.firstMinuteDomain);
    }

    @Test void aPreviousProfileCannotCarryTheMachineFamilyAsUnknownKeys() {
        var previous = new Properties();
        previous.setProperty("firstMinute.played", "true"); previous.setProperty("firstMinute.domain", "trading");
        previous.setProperty("future.setting", "DEMO");
        String exported = new SettingsShare("").export(new AppConfig(),
                EnumSet.allOf(SettingsShare.Category.class), null, previous);
        assertFalse(exported.contains("firstMinute."), "known machine-only firstMinute keys must not escape as unknown profile keys");
        assertTrue(exported.contains("future.setting=DEMO"), "genuinely unknown families still survive");
    }
}
