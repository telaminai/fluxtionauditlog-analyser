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
    void relativePathLandsInsideTheExportDir() throws Exception {
        var r = ExportGuard.resolve("sub/finding.pdf", true, dir.toString());
        assertTrue(r.ok());
        assertEquals(dir.toRealPath().resolve("sub/finding.pdf"), r.path(), "the output uses the checked directory");
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
    void escapeAttemptsAreRefusedNamingTheDirectory() throws Exception {
        var r = ExportGuard.resolveRead("../../etc/passwd", true, dir.toString(), java.util.Set.of());
        assertNotNull(r.error());
        assertTrue(r.error().contains(dir.toRealPath().toString()),
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

    @Test
    void aNestedLinkCannotWidenReadsOrWrites() throws Exception {
        Path exchange = Files.createDirectory(dir.resolve("exchange"));
        Path outside = Files.createDirectory(dir.resolve("outside"));
        Files.writeString(outside.resolve("existing.csv"), "DEMO");
        Files.createSymbolicLink(exchange.resolve("nested"), outside);
        assertFalse(ExportGuard.resolve("nested/new.pdf", true, exchange.toString()).ok(),
                "a nested outside link must not grant a write outside the exchange directory");
        assertFalse(ExportGuard.resolveRead("nested/existing.csv", true, exchange.toString(), java.util.Set.of()).ok(),
                "a nested outside link must not grant a read outside the exchange directory");
    }

    @Test
    void aFileLinkCannotWidenReads() throws Exception {
        Path exchange = Files.createDirectory(dir.resolve("exchange"));
        Path outside = Files.writeString(dir.resolve("outside.csv"), "DEMO");
        Files.createSymbolicLink(exchange.resolve("linked.csv"), outside);
        assertFalse(ExportGuard.resolveRead("linked.csv", true, exchange.toString(), java.util.Set.of()).ok(),
                "the read target itself must be contained, not just its parent");
    }

    @Test
    void anInternalLinkAllowsNewDescendantsAndReturnsTheCheckedLocation() throws Exception {
        Path real = Files.createDirectory(dir.resolve("real"));
        Path alias = Files.createSymbolicLink(dir.resolve("alias"), real);
        var target = ExportGuard.resolve("new/sub/output.pdf", true, alias.toString());
        assertTrue(target.ok(), "new descendant directories remain usable: " + target.error());
        assertEquals(real.toRealPath().resolve("new/sub/output.pdf"), target.path(),
                "return the checked location, not an alias that could be swapped");
        Files.delete(alias);
        Path elsewhere = Files.createDirectory(dir.resolve("elsewhere"));
        Files.createSymbolicLink(alias, elsewhere);
        Files.createDirectories(target.path().getParent());
        Files.writeString(target.path(), "DEMO");
        assertFalse(Files.exists(elsewhere.resolve("new/sub/output.pdf")),
                "swapping the original exchange alias must not redirect an already resolved output");
    }
    @Test
    void absoluteAliasRequestsUseTheCanonicalExchangeLocation() throws Exception {
        Path real = Files.createDirectory(dir.resolve("exchange"));
        Path alias = Files.createSymbolicLink(dir.resolve("alias"), real);
        assertAbsoluteExchangePaths(alias, real.toRealPath(), real.toRealPath());
        // macOS also exposes temporary directories through the system's directory alias.
        Path canonical = real.toRealPath();
        Path privateVar = Path.of("/private/var");
        if (Files.isDirectory(privateVar) && canonical.startsWith(privateVar)) {
            assertAbsoluteExchangePaths(Path.of("/var").resolve(privateVar.relativize(canonical)),
                    canonical, canonical);
        }
    }

    @Test
    void absoluteRequestsThroughALinkedProjectRootAreAccepted() throws Exception {
        Path root = Files.createDirectory(dir.resolve("project"));
        Files.createDirectories(root.resolve(".analyser"));
        Path exchange = Files.createDirectory(root.resolve("exchange"));
        Path alias = Files.createSymbolicLink(dir.resolve("project-alias"), root);
        var config = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        config.activeProjectPath = alias.resolve(".analyser/project.fluxtion-settings").toString();
        config.assistantExports = true;
        config.projectExchangeDir = "exchange";
        var effective = telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir.of(config);
        assertTrue(effective.fromProject(), "the linked project supplies its exchange directory");
        assertAbsoluteExchangePaths(alias.resolve("exchange"), Path.of(effective.dir()), exchange.toRealPath());
    }

    @Test
    void machineExchangeAliasAcceptsCanonicalRequests() throws Exception {
        Path real = Files.createDirectory(dir.resolve("exchange"));
        Path alias = Files.createSymbolicLink(dir.resolve("alias"), real);
        var config = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        config.assistantExports = true;
        config.assistantExportDir = alias.toString();
        var effective = telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir.of(config);
        assertFalse(effective.fromProject(), "fixture uses the machine tier");
        assertAbsoluteExchangePaths(real.toRealPath(), Path.of(effective.dir()), real.toRealPath());
    }

    private static void assertAbsoluteExchangePaths(Path requestedDir, Path configuredDir, Path canonical) throws Exception {
        Files.writeString(canonical.resolve("input.csv"), "DEMO");
        var write = ExportGuard.resolve(requestedDir.resolve("new/output.pdf").toString(), true, configuredDir.toString());
        var read = ExportGuard.resolveRead(requestedDir.resolve("input.csv").toString(), true,
                configuredDir.toString(), java.util.Set.of());
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertTrue(write.ok(), "absolute alias write must be accepted: " + write.error()),
                () -> assertEquals(canonical.resolve("new/output.pdf"), write.path(), "write returns the canonical target"),
                () -> assertTrue(read.ok(), "absolute alias read must be accepted: " + read.error()),
                () -> assertEquals(canonical.resolve("input.csv"), read.path(), "read returns the canonical target"));
    }

    @Test
    void danglingFileAndDirectoryLinksAreRefused() throws Exception {
        Path exchange = Files.createDirectory(dir.resolve("exchange"));
        Files.createSymbolicLink(exchange.resolve("file.pdf"), dir.resolve("missing.pdf"));
        Files.createSymbolicLink(exchange.resolve("directory"), dir.resolve("missing-directory"));
        for (String requested : java.util.List.of("file.pdf", "directory/new/output.pdf")) {
            var result = ExportGuard.resolve(requested, true, exchange.toString());
            assertFalse(result.ok(), "a dangling symbolic link cannot grant a write: " + requested);
            assertTrue(result.error().contains("symbolic link does not resolve"),
                    "the refusal explains the unresolved link: " + result.error());
        }
    }

}
