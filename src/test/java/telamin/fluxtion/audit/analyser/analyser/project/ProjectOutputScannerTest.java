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

    @Test void oneGeneratedGraphWithTwoMavenCopiesProducesOneCandidate(@TempDir Path root) throws Exception {
        Path source = writeGraph(root, "src/main/resources/com/example/Processor.graphml", "graph-v1");
        writeGraph(root, "target/classes/com/example/Processor.graphml", "graph-v1");
        writeGraph(root, "target/fluxtion-staging/resources/com/example/Processor.graphml", "graph-v1");
        var scan = ProjectOutputScanner.scan(root, List.of(), null);
        assertTrue(scan.complete());
        assertEquals(List.of(source.toRealPath()), scan.files().stream().map(ProjectOutputScanner.Observed::realPath).toList());
    }

    @Test void differentOrStandaloneBuildGraphsAreNotHidden(@TempDir Path root) throws Exception {
        writeGraph(root, "src/main/resources/com/example/Processor.graphml", "graph-v1");
        writeGraph(root, "target/classes/com/example/Processor.graphml", "graph-v2");
        writeGraph(root, "target/fluxtion-staging/resources/Other.graphml", "graph-v1");
        writeGraph(root, "other/Processor.graphml", "graph-v1");
        assertEquals(4, ProjectOutputScanner.scan(root, List.of(), null).files().size());
    }

    @Test void sourceOutsideTheProjectCannotSuppressAnInProjectBuildGraph(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("project"));
        Path outside = writeGraph(temp, "outside/Processor.graphml", "graph-v1");
        Path source = root.resolve("src/main/resources/Processor.graphml");
        Files.createDirectories(source.getParent());
        Files.createSymbolicLink(source, outside);
        Path built = writeGraph(root, "target/classes/Processor.graphml", "graph-v1");
        assertEquals(List.of(built.toRealPath()), ProjectOutputScanner.scan(root, List.of(), null).files()
                .stream().map(ProjectOutputScanner.Observed::realPath).toList());
    }

    @Test void sourceBeyondScanDepthCannotHideTheDiscoverableBuildGraph(@TempDir Path root) throws Exception {
        String nested = "a/b/c/d/e/f/g/h/i/Processor.graphml";
        writeGraph(root, "src/main/resources/" + nested, "graph-v1");
        Path built = writeGraph(root, "target/classes/" + nested, "graph-v1");
        var scan = ProjectOutputScanner.scan(root, List.of(), null);
        assertTrue(scan.complete());
        assertEquals(List.of(built.toRealPath()), scan.files().stream().map(ProjectOutputScanner.Observed::realPath).toList());
    }

    @Test void sourceInIgnoredDirectoryCannotHideTheDiscoverableBuildGraph(@TempDir Path root) throws Exception {
        Path hidden = writeGraph(root, ".git/resources/Processor.graphml", "graph-v1");
        Files.createDirectories(root.resolve("src/main"));
        Files.createSymbolicLink(root.resolve("src/main/resources"), hidden.getParent());
        Path built = writeGraph(root, "target/classes/Processor.graphml", "graph-v1");
        var scan = ProjectOutputScanner.scan(root, List.of(), null);
        assertTrue(scan.complete());
        assertEquals(List.of(built.toRealPath()), scan.files().stream().map(ProjectOutputScanner.Observed::realPath).toList());
    }

    @Test void sourceAliasOfBuildOutputCannotSuppressItself(@TempDir Path root) throws Exception {
        Path built = writeGraph(root, "target/classes/Processor.graphml", "graph-v1");
        Files.createDirectories(root.resolve("src/main"));
        Files.createSymbolicLink(root.resolve("src/main/resources"), built.getParent());
        var scan = ProjectOutputScanner.scan(root, List.of(), null);
        assertTrue(scan.complete());
        assertEquals(List.of(built.toRealPath()), scan.files().stream().map(ProjectOutputScanner.Observed::realPath).toList());
    }

    private static Path writeGraph(Path root, String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }
}
