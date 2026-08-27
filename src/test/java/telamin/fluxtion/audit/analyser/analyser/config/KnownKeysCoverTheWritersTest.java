package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M38.7's family lists must keep up with the writers that fill them.
 *
 * <p>D-C10 rewrites the families this version OWNS and preserves everything else. That is safe in the
 * direction that matters — a family the list forgets is carried over rather than lost, and
 * {@link KnownKeys#preserve} only fills keys the writer did not set, so a stale value can never clobber
 * a fresh one. But a forgotten family still has a cost: a setting in it can no longer be CLEARED,
 * because the old value is preserved every time the new file omits it.
 *
 * <p>The lists are hand-maintained, so they drift the moment someone adds a key and not its family. This
 * test reads the writers' own source and fails if a family is written but unowned — the same shape as
 * the M37 parity test, which reads {@code context()}'s source to prove the panel consumes nothing the
 * method does not put. Enforce the constraint rather than remember it.
 */
class KnownKeysCoverTheWritersTest {

    private static final Pattern[] WRITES = {
            Pattern.compile("\\bput\\(p,\\s*\"([A-Za-z][\\w.]*)\""),
            Pattern.compile("\\bwriteList\\(p,\\s*\"([A-Za-z][\\w.]*)\""),
            Pattern.compile("p\\.setProperty\\(\"([A-Za-z][\\w.]*)\""),
    };

    private static Set<String> familiesWrittenBy(String javaFile) throws IOException {
        String src = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/config/"
                + javaFile));
        Set<String> families = new LinkedHashSet<>();
        for (Pattern p : WRITES) {
            Matcher m = p.matcher(src);
            while (m.find()) families.add(KnownKeys.family(m.group(1)));
        }
        assertTrue(families.size() > 5, javaFile + ": found almost no writes — has the writer changed "
                + "shape? A test that silently matches nothing proves nothing.");
        return families;
    }

    @Test
    void everyFamilyConfigStoreWritesIsOneItOwns() throws IOException {
        Set<String> missing = new LinkedHashSet<>(familiesWrittenBy("ConfigStore.java"));
        missing.removeAll(KnownKeys.CONFIG_FAMILIES);
        assertTrue(missing.isEmpty(), "ConfigStore writes these families but KnownKeys.CONFIG_FAMILIES "
                + "does not own them, so a value in one can never be cleared: " + missing);
    }

    @Test
    void everyFamilySettingsShareWritesIsOneItOwns() throws IOException {
        Set<String> missing = new LinkedHashSet<>(familiesWrittenBy("SettingsShare.java"));
        missing.removeAll(KnownKeys.PROFILE_FAMILIES);
        assertTrue(missing.isEmpty(), "SettingsShare writes these families but KnownKeys.PROFILE_FAMILIES "
                + "does not own them: " + missing);
    }

    @Test
    void theProfileFamiliesAreASubsetOfTheConfigFamilies() {
        // the own-settings file holds everything a profile can, plus the machine tier — if that ever
        // stops being true, a profile key would be preserved-as-unknown in the config and never rewritten
        Set<String> missing = new LinkedHashSet<>(KnownKeys.PROFILE_FAMILIES);
        missing.removeAll(KnownKeys.CONFIG_FAMILIES);
        assertTrue(missing.isEmpty(), "profile families absent from the config families: " + missing);
    }
}
