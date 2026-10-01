package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedProfileContentTest {
    @Test void changedContentIsRefusedBeforeSettingsAreApplied(@TempDir Path tmp) throws Exception {
        Path profile = tmp.resolve("DEMO.fluxtion-settings");
        ProjectProfile.save(profile, new AppConfig(), new SettingsShare());
        var verified = ProjectProfile.load(profile, new AppConfig(), new SettingsShare());
        assertTrue(verified.loaded());
        Files.writeString(profile, "selectedEventProcessor=com.acme.Replacement\n");
        var current = new AppConfig();
        current.selectedEventProcessor = "com.acme.Original";
        var result = ProjectProfile.load(profile, current, new SettingsShare(), verified.contentDigest());
        assertFalse(result.loaded(), "replacementContentMustBeRefused");
        assertEquals("com.acme.Original", current.selectedEventProcessor, "refusalMustNotApplyReplacementSettings");
    }
}
