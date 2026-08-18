package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The export policy: opt-in, confined to one directory, never overwrites. */
class ExportGuardTest {

    @TempDir
    Path dir;

    @Test
    void disabledIsRefusedWithTheSettingsHint() {
        var r = ExportGuard.resolve("shot.png", false, dir.toString());
        assertFalse(r.ok());
        assertTrue(r.error().contains("Allow assistant file exchange"), "the widened consent label (M29 F1)");
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

    // ---- the read counterpart (M29 D-F4) --------------------------------------------------------

    @Test
    void readsAreRefusedWhenExchangeIsDisabled_namingTheSettingAndTheCoupling() {
        var r = ExportGuard.resolveRead("/anywhere/x.csv", false, "", java.util.Set.of());
        assertNotNull(r.error());
        assertTrue(r.error().contains("Allow assistant file exchange"), r.error());
        assertTrue(r.error().contains("reads and writes share"), "the coupling is stated: " + r.error());
    }

    @Test
    void readsInsideTheExchangeDirectoryResolve() throws Exception {
        java.nio.file.Path f = dir.resolve("venue.csv");
        java.nio.file.Files.writeString(f, "ts,mid\n1,2\n");
        var abs = ExportGuard.resolveRead(f.toString(), true, dir.toString(), java.util.Set.of());
        assertNull(abs.error());
        var rel = ExportGuard.resolveRead("venue.csv", true, dir.toString(), java.util.Set.of());
        assertNull(rel.error(), "relative names resolve inside the exchange directory");
    }

    @Test
    void escapeAttemptsAreRefusedNamingTheDirectory() {
        var r = ExportGuard.resolveRead("../../etc/passwd", true, dir.toString(), java.util.Set.of());
        assertNotNull(r.error());
        assertTrue(r.error().contains(dir.toAbsolutePath().normalize().toString()),
                "the refusal names the confined directory: " + r.error());
    }

    @Test
    void aChooserGrantAdmitsExactlyThatFile_evenWithExchangeOff() {
        java.nio.file.Path granted = dir.resolve("picked.csv").toAbsolutePath().normalize();
        var ok = ExportGuard.resolveRead(granted.toString(), false, "", java.util.Set.of(granted));
        assertNull(ok.error(), "the chooser IS the grant");
        var sibling = ExportGuard.resolveRead(dir.resolve("other.csv").toString(), false, "",
                java.util.Set.of(granted));
        assertNotNull(sibling.error(), "the grant is the FILE, never its directory");
    }
}
