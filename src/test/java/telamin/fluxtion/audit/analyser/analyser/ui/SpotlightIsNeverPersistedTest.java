package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M64 D-SP4 / acceptance 4 — a spotlight is TRANSIENT BY CONSTRUCTION: no field in config, no key in the
 * profile, nothing in a saved graph, nothing in a report. Pointing is a tutoring act; the artefacts that
 * carry findings (flags, notes, reports) already exist and stay the only durable form.
 *
 * <p>"By construction" is a claim about the code, so it is checked against the code: the packages that
 * PERSIST anything may not know the word. That is stronger than saving a config and grepping the output,
 * which would only prove that today's save path happens not to write one.
 */
class SpotlightIsNeverPersistedTest {

    private static final Path MAIN = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser");

    /** Everything that writes something which outlives the session. */
    private static final List<String> PERSISTING_PACKAGES = List.of("config", "report", "graph", "export", "session");

    @Test
    void noPackageThatPersistsAnythingKnowsTheWord() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String pkg : PERSISTING_PACKAGES) {
            Path dir = MAIN.resolve(pkg);
            assertTrue(Files.isDirectory(dir), "the package list has rotted — no such package: " + dir);
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    if (Files.readString(file).toLowerCase(Locale.ROOT).contains("spotlight")) {
                        offenders.add(MAIN.relativize(file).toString());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "a spotlight must exist ONLY in the overlay (D-SP4) — a persisting "
                + "package mentions it: " + offenders);
    }

    @Test
    void theConfigHasNoFieldForOne() {
        for (Field f : AppConfig.class.getDeclaredFields()) {
            assertTrue(!f.getName().toLowerCase(Locale.ROOT).contains("spotlight"),
                    "AppConfig." + f.getName() + " — a spotlight is never configuration");
        }
    }

    @Test
    void theOverlayHoldsItsStateInItsOwnFields_andNowhereElseInTheFrame() throws IOException {
        // the frame may HOLD the overlay and ASK it; it may not keep a second copy of what is lit
        String frame = Files.readString(MAIN.resolve("ui/MainFrame.java"));
        assertTrue(frame.contains("private final SpotlightOverlay spotlight"), "the overlay is the frame's one handle");
        for (String copy : List.of("String spotlightTarget", "String litTarget", "Rectangle spotlightBounds",
                "String spotlightCaption")) {
            assertTrue(!frame.contains(copy), "MainFrame keeps its own copy of spotlight state: '" + copy + "'");
        }
    }
}
