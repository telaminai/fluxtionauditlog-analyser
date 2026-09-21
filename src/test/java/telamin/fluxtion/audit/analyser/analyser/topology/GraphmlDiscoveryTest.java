package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** M35.4 — discovery OFFERS and never selects; the ranking is checkable and the bounds are honest. */
class GraphmlDiscoveryTest {

    private static final Path EVIDENCE = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures");

    private static Path fixture(Path root, String directory, String source) throws Exception {
        Path target = Files.createDirectories(root.resolve(directory)).resolve("MarketProcessor.graphml");
        return Files.copy(EVIDENCE.resolve(source), target);
    }

    @Test
    void committedCopiesDisagreeWithoutALogAndRankByLoggedEvidence(@TempDir Path root) throws Exception {
        Path current = fixture(root, "source", "MarketProcessor.src-round3.graphml");
        Path stale = fixture(root, "classes", "MarketProcessor.target-stale.graphml");
        var noLog = GraphmlDiscovery.scan(List.of(root.toString()), Set.of());
        assertEquals(1, noLog.copyGroups().size());
        assertEquals("disagree", noLog.copyGroups().get(0).agreement());
        assertTrue(noLog.candidates().stream().allMatch(c -> c.pairing() == null));
        var result = GraphmlDiscovery.scan(List.of(root.toString()), Set.of("rootNode", "eodReportPublisher"));
        assertEquals(current, result.candidates().get(0).file());
        var source = result.candidates().get(0);
        var older = result.candidates().stream().filter(c -> c.file().equals(stale)).findFirst().orElseThrow();
        assertEquals(23, source.toMap().get("graphNodes"));
        assertEquals(20, older.toMap().get("graphNodes"));
        assertEquals("4ecd613398d340719081dfe71143287e96bb3ae7284819687853a0679914c9d9", source.fingerprint());
        assertEquals("f6ae6f84383bd1c7f063f61db2cea505a26a4291226f02ff1124f654513dc576", older.fingerprint());
        assertNotNull(source.modifiedTime());
        assertNotNull(older.modifiedTime());
        assertEquals(Set.of("eodReportPublisher"), older.loggedButNotDeclared());
        assertTrue(older.describe().contains("eodReportPublisher"));
        assertEquals("disagree", result.copyGroups().get(0).toMap().get("agreement"));
    }

    @Test
    void identicalCommittedCopiesAgreeAndMissingMetadataIsUnknown(@TempDir Path root) throws Exception {
        fixture(root, "a", "MarketProcessor.src-round3.graphml");
        fixture(root, "b", "MarketProcessor.src-round3.graphml");
        assertEquals("agree", GraphmlDiscovery.scan(List.of(root.toString()), Set.of()).copyGroups().get(0).agreement());
        // Constructed metadata-absence case; it is not a replay of the reported session.
        for (var file : List.of(root.resolve("a/MarketProcessor.graphml"), root.resolve("b/MarketProcessor.graphml"))) {
            Files.writeString(file, Files.readString(file).replaceAll("<data key=\"fluxtion.sourceFingerprint\">[^<]+</data>", ""));
        }
        assertEquals("unknown", GraphmlDiscovery.scan(List.of(root.toString()), Set.of()).copyGroups().get(0).agreement());
    }

    @Test
    void declaredProcessorGroupsRenamedCopiesAndNodeSetsAreCompared(@TempDir Path root) throws Exception {
        // Constructed metadata variants of the committed graph: the producer packet has no processorClass.
        String original = Files.readString(EVIDENCE.resolve("MarketProcessor.src-round3.graphml"));
        String withClass = original.replace("<graph edgedefault=\"directed\">", "<graph edgedefault=\"directed\">"
                + "<data key=\"fluxtion.processorClass\">example.MarketProcessor</data>");
        Files.writeString(root.resolve("one.graphml"), withClass);
        Files.writeString(root.resolve("two.graphml"), withClass);
        assertEquals("agree", GraphmlDiscovery.scan(List.of(root.toString()), Set.of()).copyGroups().get(0).agreement());
        // Equal counts and fingerprints cannot conceal a different node id.
        Files.writeString(root.resolve("two.graphml"), withClass.replace("eodReportPublisher", "otherPublisher"));
        assertEquals("disagree", GraphmlDiscovery.scan(List.of(root.toString()), Set.of()).copyGroups().get(0).agreement());
    }

    /** A minimal Fluxtion-shaped graphml with the given node ids. */
    private static String graphml(String... ids) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:jGraph="http://www.jgraph.com/">
                  <key id="vertex_label" for="node" attr.name="nodeData" attr.type="string"/>
                  <graph edgedefault="undirected">
                """);
        for (String id : ids) {
            sb.append("""
                        <node id="%s"><data key="vertex_label"><jGraph:ShapeNode>
                          <jGraph:label text="id:%s&#10;class:com.acme.%s"/>
                          <jGraph:Style properties="NODE"/>
                        </jGraph:ShapeNode></data></node>
                    """.formatted(id, id, id));
        }
        return sb.append("  </graph>\n</graphml>\n").toString();
    }

    private static Path write(Path dir, String name, String... ids) throws Exception {
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.writeString(f, graphml(ids));
        return f;
    }

    @Test
    void theBestFitIsRankedFirst_butNothingIsSelected(@TempDir Path root) throws Exception {
        write(root.resolve("a"), "wrong.graphml", "alpha", "beta");
        Path right = write(root.resolve("b"), "right.graphml", "book", "risk", "orders");

        var r = GraphmlDiscovery.scan(List.of(root.toString()), Set.of("book", "risk", "orders"));

        assertEquals(2, r.candidates().size());
        assertEquals(right.getFileName(), r.candidates().get(0).file().getFileName(),
                "best fit first — and FIRST is all it is; the caller opens it");
        assertTrue(r.candidates().get(0).pairing().applies());
        assertFalse(r.candidates().get(1).pairing().applies());
    }

    @Test
    void everyCandidateCarriesItsNumbers_soARankingCanBeDisagreedWith() throws Exception {
        var tmp = Files.createTempDirectory("m354");
        write(tmp, "g.graphml", "a", "b", "c");
        var r = GraphmlDiscovery.scan(List.of(tmp.toString()), Set.of("a", "b", "zzz"));
        var c = r.candidates().get(0);
        assertEquals(3, c.nodes());
        assertEquals(2, c.pairing().matched());
        assertEquals(3, c.pairing().logged());
        assertTrue(c.describe().contains("3 node(s)"), c.describe());
        assertTrue(c.describe().contains("2 of the 3"), c.describe());
    }

    @Test
    void withNoLogTheCandidatesAreListedButNotJudged() throws Exception {
        var tmp = Files.createTempDirectory("m354b");
        write(tmp, "g.graphml", "a");
        var r = GraphmlDiscovery.scan(List.of(tmp.toString()), Set.of());
        assertEquals(1, r.candidates().size());
        assertNull(r.candidates().get(0).pairing(),
                "with nothing to fit, inventing an order would be a recommendation nobody earned");
    }

    @Test
    void aFileThatIsNotAGraphIsReportedRatherThanCrashingTheScan(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("broken.graphml"), "this is not xml at all");
        write(root, "good.graphml", "a");
        var r = GraphmlDiscovery.scan(List.of(root.toString()), Set.of("a"));
        assertEquals(2, r.candidates().size(), "the bad one is listed, not silently dropped");
        assertEquals("good.graphml", r.candidates().get(0).file().getFileName().toString());
        assertTrue(r.notes().stream().anyMatch(n -> n.contains("broken.graphml")), r.notes().toString());
    }

    @Test
    void aMissingRootIsNamedNotIgnored() {
        var r = GraphmlDiscovery.scan(List.of("/no/such/place"), Set.of("a"));
        assertTrue(r.candidates().isEmpty());
        assertTrue(r.notes().get(0).contains("not a directory"), r.notes().toString());
    }

    @Test
    void aTruncatedScanSaysSo_ratherThanPresentingAPartialListAsTheAnswer(@TempDir Path root)
            throws Exception {
        for (int i = 0; i < GraphmlDiscovery.MAX_CANDIDATES + 5; i++) {
            write(root, "g" + i + ".graphml", "n" + i);
        }
        var r = GraphmlDiscovery.scan(List.of(root.toString()), Set.of("n1"));
        assertTrue(r.truncated());
        assertEquals(GraphmlDiscovery.MAX_CANDIDATES, r.candidates().size());
        assertTrue(r.notes().stream().anyMatch(n -> n.contains("not a ranking of everything")),
                r.notes().toString());
    }

    @Test
    void theSameFileReachedByTwoRootsIsCountedOnce(@TempDir Path root) throws Exception {
        write(root.resolve("nested"), "g.graphml", "a");
        var r = GraphmlDiscovery.scan(
                List.of(root.toString(), root.resolve("nested").toString()), Set.of("a"));
        assertEquals(1, r.candidates().size(), "overlapping roots must not double-list a graph");
    }
}
