package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** M68.5 (spec-evidence-integrity acceptance 8): a pointer that fails names the root that was tried. */
class RunbooksResolutionTest {

    @Test
    @DisplayName("a pointer that lands on a file: no problem")
    void found(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("ops"));
        Files.writeString(root.resolve("ops/deploy.md"), "x");
        var r = Runbooks.resolution(root, "ops/deploy.md");
        assertTrue(r.exists());
        assertNull(r.problem());
    }

    @Test
    @DisplayName("no file: the diagnosis names the root tried AND where it landed")
    void notFoundNamesTheRoot(@TempDir Path root) {
        var r = Runbooks.resolution(root, "ops/restart.md");
        assertFalse(r.exists());
        String norm = root.toAbsolutePath().normalize().toString();
        assertTrue(r.problem().contains("project root " + norm), r.problem());
        assertTrue(r.problem().contains(root.resolve("ops/restart.md").toAbsolutePath().normalize().toString()), r.problem());
    }

    @Test
    @DisplayName("a path out of the root is refused, naming the root — it used to be silent")
    void escapeIsRefused(@TempDir Path root) {
        var r = Runbooks.resolution(root, "../elsewhere/secret.md");
        assertNull(r.resolved());
        assertTrue(r.problem().startsWith("refused"), r.problem());
        assertTrue(r.problem().contains(root.toAbsolutePath().normalize().toString()), r.problem());
    }

    @Test
    @DisplayName("no project root: says so, and says what to do")
    void noRoot() {
        var r = Runbooks.resolution(null, "ops/deploy.md");
        assertTrue(r.problem().contains("no project root"), r.problem());
    }
}
