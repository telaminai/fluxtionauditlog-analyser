package telamin.fluxtion.audit.analyser.analyser.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOutputScannerTest {
    @Test void graphAndOnlyScopedLogsAreObservedWithoutOpeningThem(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        Path logs = Files.createDirectories(root.resolve("logs"));
        Path other = Files.createDirectories(root.resolve("other"));
        Files.writeString(root.resolve("DEMO.graphml"), "partial graph — the watcher never certifies syntax");
        Files.writeString(logs.resolve("DEMO.yml"), "partial log — the watcher never certifies completeness");
        Files.writeString(other.resolve("not-a-log.yml"), "configuration");
        var first = ProjectOutputScanner.scan(root, List.of(logs), null);
        assertTrue(first.complete());
        assertEquals(List.of("DEMO.graphml", "DEMO.yml"), first.files().stream().map(f -> f.realPath().getFileName().toString()).toList());
        var withCurrent = ProjectOutputScanner.scan(root, List.of(), logs.resolve("DEMO.yml"));
        assertEquals(first.files(), withCurrent.files(), "the currently open local log contributes only its directory");
        var seen = first.files().getFirst();
        Files.writeString(seen.realPath(), "rewritten graph");
        assertNotEquals(seen.revision(), ProjectOutputScanner.observe(seen.realPath(), seen.kind(), root).revision());
    }

    @Test void externalLinksAndEmptyOrUnresolvedFilesCannotBorrowProjectIdentity(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        Path outside = Files.writeString(temp.resolve("outside.graphml"), "DEMO");
        Files.createSymbolicLink(root.resolve("escape.graphml"), outside);
        Files.createFile(root.resolve("empty.graphml"));
        assertTrue(ProjectOutputScanner.scan(root, List.of(), null).files().isEmpty(), "a symlink's apparent name is not project output identity");
        assertNull(ProjectOutputScanner.observe(root.resolve("escape.graphml"),
                telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState.Kind.GRAPHML, root));
    }

    @Test void candidateBoundsDoNotMistakeOtherFilesForOverflow(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        for (int i = 0; i < ProjectOutputScanner.MAX_FILES; i++)
            Files.writeString(root.resolve("DEMO-" + i + ".graphml"), "DEMO");
        Files.writeString(root.resolve("README.md"), "not an output");
        var exact = ProjectOutputScanner.scan(root, List.of(), null);
        assertTrue(exact.complete(), "one hundred candidates plus unrelated files is within the candidate bound");
        assertEquals(ProjectOutputScanner.MAX_FILES, exact.files().size());
        Files.writeString(root.resolve("overflow.graphml"), "DEMO");
        assertFalse(ProjectOutputScanner.scan(root, List.of(), null).complete(), "overflow cannot certify a stable complete scan");
    }

    @Test void ignoredTreesAreNeverTraversedForOutputCandidates(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        for (String ignored : List.of(".git", ".claude", "node_modules"))
            Files.writeString(Files.createDirectories(root.resolve(ignored)).resolve("DEMO.graphml"), "DEMO");
        assertTrue(ProjectOutputScanner.scan(root, List.of(), null).files().isEmpty());
    }

    @Test void buildCopiesOfOneGraphShareALogicalPathAndContentDigest(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        List<String> places = List.of("src/main/resources", "target/classes", "target/fluxtion-staging/resources");
        for (String place : places)
            Files.writeString(Files.createDirectories(root.resolve(place).resolve("com/acme")).resolve("P.graphml"), "<graphml/>");
        var scan = ProjectOutputScanner.scan(root, List.of(), null);
        assertEquals(3, scan.files().size(), "every copy is still observed; the node decides they are one graph");
        assertEquals(List.of("|main|com/acme/P.graphml"), scan.files().stream().map(ProjectOutputScanner.Observed::logicalPath).distinct().toList());
        assertEquals(1, scan.files().stream().map(o -> o.revision().contentDigest()).distinct().count());
        assertEquals(List.of(false, true, true), scan.files().stream().map(ProjectOutputScanner.Observed::buildCopy).toList());
        Path copy = root.resolve("target/classes/com/acme/P.graphml");
        Files.writeString(copy, "<graphml/>");
        Files.setLastModifiedTime(copy, java.nio.file.attribute.FileTime.fromMillis(1_000));
        var recopied = ProjectOutputScanner.observe(copy, telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState.Kind.GRAPHML, root);
        assertEquals(scan.files().getFirst().revision().contentDigest(), recopied.revision().contentDigest(),
                "a fresh copy of the same bytes keeps its content digest");
        Files.writeString(copy, "<graphml>changed</graphml>");
        assertNotEquals(recopied.revision().contentDigest(), ProjectOutputScanner.observe(copy,
                telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState.Kind.GRAPHML, root).revision().contentDigest());
    }

    @Test void logsKeepMetadataIdentityOnly(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        Path logs = Files.createDirectories(root.resolve("logs"));
        Files.writeString(logs.resolve("DEMO.yml"), "DEMO");
        var log = ProjectOutputScanner.scan(root, List.of(logs), null).files().getFirst();
        assertNull(log.revision().contentDigest(), "a log's bytes are never read by the watcher");
        assertNull(log.logicalPath());
    }

    @Test void logicalPathsSeparateModulesAndSourceSets() {
        java.util.function.Function<String, String> l = p -> ProjectOutputScanner.logicalPath(Path.of(p));
        assertEquals(l.apply("src/main/resources/com/acme/P.graphml"), l.apply("target/classes/com/acme/P.graphml"));
        assertEquals(l.apply("src/main/resources/com/acme/P.graphml"), l.apply("target/fluxtion-staging/resources/com/acme/P.graphml"));
        assertNotEquals(l.apply("module-a/src/main/resources/com/acme/P.graphml"), l.apply("module-b/src/main/resources/com/acme/P.graphml"));
        assertEquals(l.apply("module-a/src/main/resources/com/acme/P.graphml"), l.apply("module-a/target/classes/com/acme/P.graphml"));
        assertNotEquals(l.apply("src/main/resources/com/acme/P.graphml"), l.apply("src/test/resources/com/acme/P.graphml"));
        assertEquals(l.apply("src/test/resources/com/acme/P.graphml"), l.apply("target/test-classes/com/acme/P.graphml"));
    }

    @Test void outputRootsAreRecognisedByShapeAcrossBuildTools(@TempDir Path temp) throws Exception {
        java.util.function.Function<String, String> l = p -> ProjectOutputScanner.logicalPath(Path.of(p));
        assertEquals(l.apply("src/main/resources/com/acme/P.graphml"), l.apply("build/resources/main/com/acme/P.graphml"), "Gradle");
        assertEquals(l.apply("src/main/resources/com/acme/P.graphml"), l.apply("build/classes/java/main/com/acme/P.graphml"), "Gradle classes");
        assertEquals(l.apply("src/test/resources/com/acme/P.graphml"), l.apply("build/resources/test/com/acme/P.graphml"));
        assertEquals(l.apply("src/main/resources/com/acme/P.graphml"), l.apply("out/production/demo/com/acme/P.graphml"),
                "IntelliJ, single module: the module name is not a directory of the project");
        Path root = Files.createDirectories(temp.resolve("DEMO"));
        Files.createDirectories(root.resolve("app/src/main/resources"));
        assertEquals(ProjectOutputScanner.logicalPath(Path.of("app/src/main/resources/com/acme/P.graphml"), root),
                ProjectOutputScanner.logicalPath(Path.of("out/production/app/com/acme/P.graphml"), root), "IntelliJ, multi-module");
        assertNotEquals(l.apply("src/main/resources/com/acme/test/P.graphml"), l.apply("src/test/resources/com/acme/test/P.graphml"),
                "a package directory named test is not the test source set");
        assertEquals(l.apply("src/main/resources/com/acme/test/P.graphml"), l.apply("target/classes/com/acme/test/P.graphml"));
        assertEquals(l.apply("src/main/resources/x/resources/P.graphml"), l.apply("target/classes/x/resources/P.graphml"),
                "a package directory named resources is part of the path, not a root");
    }
}
