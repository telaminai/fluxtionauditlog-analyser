package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * S4 (2026-11-04, a gym run): a project granting only its {@code src/} roots could not open
 * {@code target/fluxtion-validation.json}, and the authoring-record checks read "unavailable". Granting the project root
 * instead broke discovery: every build copies the design to {@code target/classes/}, so two designs were found and the
 * zero-setup auto-open was lost. The analyser now reads a closed list of evidence files below the project root without
 * a root, and discovery never descends into build output or VCS directories.
 */
class AuthoringEvidenceTest {
    @TempDir Path tmp;
    Path project, sources, design;

    static final String VALIDATION = "{\"contractVersion\":\"1.0\",\"valid\":true,\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}";
    static final String SPRING = "<beans><bean id='cfg' class='com.acme.FluxtionSpringConfig'/></beans>\n";

    @BeforeEach void layout() throws Exception {
        project = Files.createDirectories(tmp.resolve("gym"));
        sources = Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(sources.resolve("Node.java"), "class Node {}\n");
        Path designs = Files.createDirectories(project.resolve("src/main/fluxtion"));
        design = Files.writeString(designs.resolve("application-context.xml"), SPRING);
        Path target = Files.createDirectories(project.resolve("target/classes"));
        Files.writeString(project.resolve("fluxtion-authoring.json"), "{\"sourceRoot\":\"src/main/java\"}");
        Files.writeString(project.resolve("target/fluxtion-validation.json"), VALIDATION);
        Files.writeString(project.resolve("target/fluxtion-reconciliation.json"), "{\"classes\":{}}");
        Files.writeString(project.resolve("target/fluxtion-run.json"), "{\"schemaVersion\":\"1.0\",\"stages\":{}}");
        Files.writeString(target.resolve("fluxtion-diagnostics.json"), "{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}");
        Files.writeString(target.resolve("application-context.xml"), SPRING); // the build's copy of the design
        Files.writeString(project.resolve(".env"), "SECRET=DEMO\n");
        Files.writeString(project.resolve("target/other.json"), "{}");
        Files.writeString(project.resolve("pom.xml"), "<project/>\n");
        Files.writeString(tmp.resolve("x"), "outside\n");
    }

    private DesignFiles srcOnly() {
        return new DesignFiles(List.of(sources.toString(), design.getParent().toString()), project);
    }

    @Test void everyEvidenceFileOpensWithOnlySourceRootsGranted() throws Exception {
        DesignFiles files = srcOnly();
        for (String name : DesignFiles.AUTHORING_EVIDENCE) {
            Path real = project.toRealPath().resolve(name);
            assertEquals(real, files.resolve(name), "relative to the project: " + name);
            assertEquals(real, files.resolve(project.resolve(name).toString()), "absolute: " + name);
            assertFalse(files.read(real).isBlank(), name);
        }
        assertEquals(5, DesignFiles.AUTHORING_EVIDENCE.size(), "the list is closed; a new entry is a decision");
        assertEquals(List.of(project.toRealPath().resolve("target/fluxtion-validation.json").toString(),
                        project.toRealPath().resolve("target/fluxtion-reconciliation.json").toString(),
                        project.toRealPath().resolve("target/classes/fluxtion-diagnostics.json").toString()),
                files.discoverDiagnostics(), "discovery lists the results without a project-root grant");
        assertEquals(List.of(sources.toString(), design.getParent().toString()), files.roots(), "no root was added");
    }

    @Test void nothingOutsideTheListBecomesReadable() throws Exception {
        DesignFiles files = srcOnly();
        for (String refused : List.of(".env", "target/other.json", "pom.xml", "target/classes/application-context.xml",
                "../x", project.resolve("../x").toString(), project.resolve("target/../.env").toString(),
                project.resolve("target").toString()))
            assertThrows(IOException.class, () -> files.resolve(refused), refused);
        assertThrows(IOException.class, () -> files.directory(project.resolve("target")), "no directory is granted");
        assertThrows(IOException.class, () -> files.directory(project), "the project root is not granted");
        DesignFiles noProject = new DesignFiles(List.of(sources.toString()), null);
        assertThrows(IOException.class, () -> noProject.resolve(project.resolve("target/fluxtion-validation.json").toString()),
                "with no project open nothing is evidence");
    }

    @Test void aLinkNeverCarriesTheGrant() throws Exception {
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Path secret = Files.writeString(outside.resolve("fluxtion-validation.json"), VALIDATION);
        Path listed = project.resolve("target/fluxtion-validation.json");
        Files.delete(listed);
        Files.createSymbolicLink(listed, secret);
        assertThrows(IOException.class, () -> srcOnly().resolve("target/fluxtion-validation.json"), "file link out of the project");
        Files.delete(project.resolve("fluxtion-authoring.json"));
        Files.createSymbolicLink(project.resolve("fluxtion-authoring.json"), project.resolve(".env"));
        assertThrows(IOException.class, () -> srcOnly().resolve("fluxtion-authoring.json"), "file link to an unlisted project file");
        Path classes = project.resolve("target/classes");
        Path moved = Files.move(classes, tmp.resolve("classes-elsewhere"));
        Files.createSymbolicLink(classes, moved);
        assertThrows(IOException.class, () -> srcOnly().resolve("target/classes/fluxtion-diagnostics.json"), "directory link");
        Path alias = Files.createSymbolicLink(tmp.resolve("gym-alias"), project);
        assertEquals(project.toRealPath().resolve("target/fluxtion-run.json"),
                new DesignFiles(List.of(), alias).resolve("target/fluxtion-run.json"), "a project opened through an alias still reads its own files");
    }

    @Test void aHomeFolderProjectGrantsNothing() throws Exception {
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", project.toString());
        try {
            assertThrows(IOException.class, () -> new DesignFiles(List.of(), project).resolve("target/fluxtion-run.json"),
                    "tier 1: a project that is the home folder grants no evidence");
        } finally {
            System.setProperty("user.home", previous);
        }
        assertDoesNotThrow(() -> new DesignFiles(List.of(), project).resolve("target/fluxtion-run.json"), "control");
    }

    @Test void diagnosticsReadTheReceiptAndRecordWithOnlySourceRoots() throws Exception {
        var driver = new SessionDriver(effect -> { throw new AssertionError("no effects"); });
        DesignFiles files = srcOnly();
        var workspace = new DesignWorkspace(() -> files, () -> driver.processor().designSession, driver::submit);
        var result = workspace.diagnostics("target/fluxtion-validation.json");
        assertFalse(result.inputChecks().containsKey("receipt"), String.valueOf(result.inputChecks()));
        assertFalse(result.inputChecks().containsKey("sourceAndRecord"), String.valueOf(result.inputChecks()));
        assertEquals("sha256:" + DesignFiles.sha256(Files.readString(project.resolve("fluxtion-authoring.json"))),
                result.inputChecks().get("currentRecordHash"));
        assertTrue(String.valueOf(result.inputChecks().get("currentSourceHash")).startsWith("sha256:"));
    }

    @Test void aProjectRootGrantStillFindsExactlyOneDesignAndAutoOpensIt() throws Exception {
        Files.createDirectories(project.resolve(".git/objects"));
        Files.writeString(project.resolve(".git/objects/stale.xml"), SPRING);
        Files.createDirectories(project.resolve("node_modules/x"));
        Files.writeString(project.resolve("node_modules/x/copy.xml"), SPRING);
        Files.createDirectories(project.resolve("build"));
        Files.writeString(project.resolve("build/copy.xml"), SPRING);
        Files.createDirectories(project.resolve(".idea"));
        Files.writeString(project.resolve(".idea/copy.xml"), SPRING);
        var found = DesignDiscovery.scan(new DesignFiles(List.of(project.toString()), project));
        assertEquals(List.of(new DesignDiscovery.FileRef(design.toRealPath().toString(), "spring")), found.files(),
                "build output and VCS copies are not definitions");
        var presented = new ArrayList<SessionEffects.PresentDesignDiscoveryEffect>();
        var driver = new SessionDriver(effect -> {
            presented.add((SessionEffects.PresentDesignDiscoveryEffect) effect);
            return new SessionEvents.StatusShown(effect.opId(), "presentDesignDiscovery");
        });
        driver.submit(new DesignEvents.DiscoveryCompleted(found, driver.processor().designSession.discoveryGeneration()));
        assertEquals(1, presented.size());
        assertEquals(design.toRealPath().toString(), presented.getFirst().file(), "exactly one design: the zero-setup auto-open");
    }

    @Test void aJavaPackageNamedBuildOrTargetIsStillScanned() throws Exception {
        Path pkg = Files.createDirectories(sources.resolve("com/acme/build"));
        Path other = Files.createDirectories(sources.resolve("com/acme/target"));
        Path builder = Files.writeString(pkg.resolve("DEMOBuilder.java"), DesignDiscoveryTest.BUILDER);
        Path xml = Files.writeString(other.resolve("design.xml"), SPRING);
        var found = DesignDiscovery.scan(new DesignFiles(List.of(project.toString()), project));
        assertEquals(Set.of(builder.toRealPath().toString(), xml.toRealPath().toString(), design.toRealPath().toString()),
                new HashSet<>(found.files().stream().map(DesignDiscovery.FileRef::path).toList()),
                "build output is skipped only beside a build file; a package of that name is source");
    }

    @Test void aRootThatIsItselfASkippedNameIsStillWalked() throws Exception {
        Path build = Files.createDirectories(tmp.resolve("build"));
        Path xml = Files.writeString(build.resolve("design.xml"), SPRING);
        assertEquals(List.of(new DesignDiscovery.FileRef(xml.toRealPath().toString(), "spring")),
                DesignDiscovery.scan(new DesignFiles(List.of(build.toString()), null)).files());
    }

    @Test void aFileDeletedMidWalkDoesNotFailDiscovery() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("walk"));
        Path kept = Files.writeString(root.resolve("a-kept.xml"), SPRING);
        Path doomed = Files.writeString(root.resolve("b-doomed.xml"), SPRING);
        var found = DesignDiscovery.scan(new DesignFiles(List.of(root.toString()), null), entry -> {
            if (entry.getFileName().equals(doomed.getFileName())) try { Files.delete(doomed); } catch (IOException e) { throw new AssertionError(e); }
        });
        assertEquals("source-scan", found.source());
        assertEquals(List.of(new DesignDiscovery.FileRef(kept.toRealPath().toString(), "spring")), found.files());
        assertEquals(List.of(), found.unreadableFiles(), "a vanished file is gone, not unreadable");
    }

    /** The concurrent-build case: entries already listed by the walk vanish before they are visited. */
    @Test void entriesListedThenRemovedDoNotFailDiscovery() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("busy"));
        List<Path> entries = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Path dir = Files.createDirectories(root.resolve("d" + i));
            Files.writeString(dir.resolve("design.xml"), SPRING);
            entries.add(dir);
            entries.add(Files.writeString(root.resolve("f" + i + ".txt"), "DEMO"));
        }
        boolean[] first = {true};
        var found = DesignDiscovery.scan(new DesignFiles(List.of(root.toString()), null), entry -> {
            if (entry.getFileName().equals(root.getFileName()) || !first[0]) return;
            first[0] = false;
            for (Path other : entries) if (!other.getFileName().equals(entry.getFileName())) deleteTree(other);
        });
        assertFalse(found.source().startsWith("unavailable"), found.source());
        assertTrue(found.files().size() <= 1, String.valueOf(found.files()));
        assertEquals(List.of(), found.unreadableFiles());
    }

    @Test void anUnopenableDirectoryIsNamedAndTheScanContinues() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("locked"));
        Path kept = Files.writeString(root.resolve("design.xml"), SPRING);
        Path closed = Files.createDirectories(root.resolve("closed"));
        Files.writeString(closed.resolve("inner.xml"), SPRING);
        Assumptions.assumeTrue(closed.toFile().setReadable(false, false) && !Files.isReadable(closed),
                "needs a filesystem that enforces permissions (not root)");
        try {
            var found = DesignDiscovery.scan(new DesignFiles(List.of(root.toString()), null));
            assertEquals("source-scan", found.source());
            assertEquals(List.of(new DesignDiscovery.FileRef(kept.toRealPath().toString(), "spring")), found.files());
            assertEquals(1, found.unreadableFiles().size(), String.valueOf(found.unreadableFiles()));
            assertTrue(found.unreadableFiles().getFirst().endsWith("closed"));
        } finally {
            closed.toFile().setReadable(true, false);
        }
    }

    private static void deleteTree(Path path) {
        try (var walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
