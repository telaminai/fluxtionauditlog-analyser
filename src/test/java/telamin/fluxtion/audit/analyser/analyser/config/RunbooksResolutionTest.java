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

    // ---- set 13, D: the other pointers a project declares — an environment's logDir, a report destination's directory

    @Test
    @DisplayName("D: a directory pointer that lands on a directory has no problem; a missing one names the root tried")
    void aDirectoryPointerIsDiagnosed(@TempDir Path root) throws Exception {
        // witness: directoryResolution not checking the directory exists
        Files.createDirectories(root.resolve("logs/prod"));
        assertNull(Runbooks.directoryResolution(root, "logs/prod").problem());
        var missing = Runbooks.directoryResolution(root, "logs/staging");
        String norm = root.toAbsolutePath().normalize().toString();
        assertNotNull(missing.problem(), "D: a missing directory is said");
        assertTrue(missing.problem().contains("project root " + norm), missing.problem());
        Files.writeString(root.resolve("logs/file.txt"), "x");
        assertNotNull(Runbooks.directoryResolution(root, "logs/file.txt").problem(), "a file is not a directory");
    }

    @Test
    @DisplayName("D: with no project root, and outside it, a directory pointer says which — as the file pointer does")
    void aDirectoryPointerWithoutARootOrOutsideItSaysSo(@TempDir Path root) {
        assertTrue(Runbooks.directoryResolution(null, "logs").problem().contains("no project root"));
        assertTrue(Runbooks.directoryResolution(root, "../elsewhere").problem().startsWith("refused"));
    }

    @Test
    @DisplayName("D: a destination directory is checked; a remote one is stated as not checked, never as fine")
    void aDestinationIsDiagnosedByItsKind(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("out"));
        assertNull(Runbooks.destinationProblem(root, new ReportDestination("local", "out")));
        assertNotNull(Runbooks.destinationProblem(root, new ReportDestination("gone", "missing/dir")));
        assertNotNull(Runbooks.destinationProblem(root, new ReportDestination("abs", root.resolve("nope").toString())),
                "an absolute directory that is not there is said too");
        assertNull(Runbooks.destinationProblem(root, new ReportDestination("abs", root.resolve("out").toString())));
        assertTrue(Runbooks.destinationNote(new ReportDestination("bucket", "s3://reports/demo")).contains("not checked"));
        assertNull(Runbooks.destinationNote(new ReportDestination("local", "out")), "a directory is checked, so no note");
    }
}
