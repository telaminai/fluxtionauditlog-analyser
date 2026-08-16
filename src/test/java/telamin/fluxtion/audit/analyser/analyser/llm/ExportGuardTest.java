package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The export policy: opt-in, confined to one directory, never overwrites. */
class ExportGuardTest {

    @TempDir
    Path dir;

    @Test
    void disabledIsRefusedWithTheSettingsHint() {
        var r = ExportGuard.resolve("shot.png", false, dir.toString());
        assertFalse(r.ok());
        assertTrue(r.error().contains("Allow file exports"));
    }

    @Test
    void noDirectoryConfiguredIsRefused() {
        assertFalse(ExportGuard.resolve("shot.png", true, "").ok());
        assertFalse(ExportGuard.resolve("shot.png", true, null).ok());
    }

    @Test
    void relativePathLandsInsideTheExportDir() {
        var r = ExportGuard.resolve("sub/finding.pdf", true, dir.toString());
        assertTrue(r.ok());
        assertEquals(dir.resolve("sub/finding.pdf").toAbsolutePath().normalize(), r.path());
    }

    @Test
    void absolutePathInsideTheDirIsAccepted() {
        var r = ExportGuard.resolve(dir.resolve("a.png").toString(), true, dir.toString());
        assertTrue(r.ok());
    }

    @Test
    void escapeAttemptsAreConfined() {
        assertFalse(ExportGuard.resolve("../outside.png", true, dir.toString()).ok());
        assertFalse(ExportGuard.resolve("/tmp/anywhere.png", true, dir.toString()).ok());
        assertFalse(ExportGuard.resolve(dir + "/../sibling.png", true, dir.toString()).ok());
    }

    @Test
    void existingFilesAreNeverOverwritten() throws Exception {
        Files.writeString(dir.resolve("taken.png"), "x");
        var r = ExportGuard.resolve("taken.png", true, dir.toString());
        assertFalse(r.ok());
        assertTrue(r.error().contains("never overwrite"));
    }

    @Test
    void blankPathStillReportsPathRequired() {
        assertEquals("'path' is required", ExportGuard.resolve(" ", true, dir.toString()).error());
    }
}
