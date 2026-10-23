package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiamondAssetsTest {
    static byte[] bytes(String name) throws Exception {
        try (var in = DiamondAssets.class.getResourceAsStream("/demo/diamond/" + name)) {
            assertNotNull(in, "every manifest member must ship: " + name);
            return in.readAllBytes();
        }
    }
    static String text(String name) throws Exception { return new String(bytes(name), StandardCharsets.UTF_8); }

    @Test void everyMemberMatchesThePinnedManifestAndCarriesItsOriginalRepositoryPath() throws Exception {
        assertEquals(DiamondDefinition.PIN, DiamondAssets.manifest().getProperty("commit"));
        assertEquals(14, DiamondAssets.files().size());
        for (var file : DiamondAssets.files()) {
            assertFalse(file.source().isBlank());
            assertEquals(file.sha256(), DiamondAssets.sha256(bytes(file.file())), file.file());
            String source = text(file.file()).toLowerCase(java.util.Locale.ROOT);
            for (String term : DemoAssetsTest.rule1SweepTerms()) assertFalse(source.contains(term), file.file());
            assertFalse(source.contains("/users/"), "shipped evidence must not contain a private machine path");
        }
        assertEquals(DiamondDefinition.LOG_SHA256, DiamondAssets.sha256(bytes("diamond-spring-audit.yaml")));
        assertEquals(DiamondDefinition.GRAPH_SHA256, DiamondAssets.sha256(bytes("DiamondSpringProcessor.graphml")));
        assertEquals(DiamondDefinition.DESIGN_SHA256, DiamondAssets.sha256(bytes("diamond.xml")));
        assertTrue(DiamondAssets.manifest().getProperty("sourceNotice").contains("neutral headers"));
        assertEquals(DiamondDefinition.WALK_SHA256, DiamondAssets.sha256(bytes("diamond.fluxtion-settings")));
        assertEquals(DiamondDefinition.WALK_SHA256, DiamondAssets.manifest().getProperty("walk.sha256"));
    }

    @Test void thePinnedSpringRunHasTheExactRecordBasesAndTracedAbsenceTheWalkClaims() throws Exception {
        var store = new HeapLogStore(text("diamond-spring-audit.yaml"));
        assertEquals(6, store.size());
        assertEquals(DiamondDefinition.RECORD_2, WalkIdentity.recordDigest(store.rawText(2)));
        assertEquals(DiamondDefinition.RECORD_3, WalkIdentity.recordDigest(store.rawText(3)));
        assertEquals(DiamondDefinition.RECORD_4, WalkIdentity.recordDigest(store.rawText(4)));
        for (int i = 2; i <= 5; i++) assertTrue(AuditTrace.tracesEveryInvocation(store.record(i)), "record " + i);
        assertEquals(4, store.record(2).nodeLogs().size());
        assertEquals(1, store.record(4).nodeLogs().size());
        assertTrue(store.rawText(4).contains("changed: false"));
        assertEquals(15, GraphMlParser.parse(text("DiamondSpringProcessor.graphml")).nodes().size());
        var walk = DiamondDefinition.walk();
        assertEquals(4, walk.steps().size());
        assertEquals(4, walk.steps().get(2).view().record());
        assertTrue(walk.steps().getFirst().targets().stream().anyMatch(t -> t.target().equals("source:design:bean:d")));
    }

    @Test void aSameSizeEditedExtractionIsReplacedNotReusedByPath(@TempDir Path tmp) throws Exception {
        DiamondAssets.install(tmp);
        byte[] original = bytes("diamond-spring-audit.yaml");
        byte[] edited = original.clone(); edited[10] ^= 1;
        Files.write(tmp.resolve("diamond-spring-audit.yaml"), edited);
        assertEquals(original.length, Files.size(tmp.resolve("diamond-spring-audit.yaml")), "control: size cannot detect this edit");
        DiamondAssets.install(tmp);
        assertArrayEquals(original, Files.readAllBytes(tmp.resolve("diamond-spring-audit.yaml")),
                "an edited extracted log must heal from the pinned resource, not be reused because it is nonempty");
    }

    @Test void extractionCannotFollowAnExistingParentSymlinkOutsideItsOwnRoot(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("diamond"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.createSymbolicLink(root.resolve("source"), outside);
        assertThrows(java.io.IOException.class, () -> DiamondAssets.install(root));
        try (var files = Files.list(outside)) { assertEquals(List.of(), files.toList(), "no source bytes are written outside the extraction"); }
    }
}
