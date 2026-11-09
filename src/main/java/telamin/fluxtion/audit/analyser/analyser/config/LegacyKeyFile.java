package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RAPID-OFF (2026-11-07): the legacy Fluxtion API key file the retired AI ▸ Fluxtion API key… item used to write.
 *
 * <p>The key route to the hosted generator is closed; generation goes only through compiler sign-in. This class answers
 * one question for the start-up facts — is a file still at the legacy location? — so the {@code aiSetup} node can say,
 * once, that nothing reads it any more. <b>Existence only:</b> the file is never opened, read, parsed or displayed, and
 * its location is never reported (a path would carry the local account name into context and screenshots).
 */
public final class LegacyKeyFile {

    /** The legacy file's name under {@code ~/.fluxtion}. */
    static final String DIRECTORY = ".fluxtion";
    static final String FILE_NAME = "fluxtion.apiKeyFile";

    private LegacyKeyFile() {
    }

    /** True when a file exists at {@code <home>/.fluxtion/fluxtion.apiKeyFile}; its contents are never read. */
    public static boolean presentUnder(Path home) {
        if (home == null) return false;
        try {
            return Files.exists(home.resolve(DIRECTORY).resolve(FILE_NAME));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
