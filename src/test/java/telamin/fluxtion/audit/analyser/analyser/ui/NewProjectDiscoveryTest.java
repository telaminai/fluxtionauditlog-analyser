package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewProjectDiscoveryTest {

    @TempDir Path root;

    @Test
    void emptyDirectoryProducesAnEmptyOffer_notAnError() {
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        assertTrue(offer.empty());
        assertTrue(offer.sourceRoots().isEmpty());
        assertTrue(offer.skills().candidates().isEmpty());
        assertTrue(offer.graphs().candidates().isEmpty());
        assertEquals(NewProjectDiscovery.Selection.empty(), NewProjectDiscovery.Selection.empty(),
                "the dialog's semantic default is an explicit empty selection");
    }

    @Test
    void discoversTheExistingThreeBoundedScanners_butAdoptsNothingByDefault() throws Exception {
        Path source = root.resolve("src/main/java");
        Files.createDirectories(source.resolve("com/acme/demo"));
        Path skill = root.resolve(".claude/skills/load-log/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "---\nname: load-log\ndescription: Open this project's exported audit log.\n---\n");
        Path graph = source.resolve("demo.graphml");
        Files.writeString(graph, graphml());

        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        assertEquals(java.util.List.of(source.toAbsolutePath().normalize()), offer.sourceRoots());
        assertEquals(java.util.List.of("load-log"),
                offer.skills().candidates().stream().map(c -> c.name()).toList());
        assertEquals(java.util.List.of(graph.toAbsolutePath().normalize()),
                offer.graphs().candidates().stream().map(c -> c.file().toAbsolutePath().normalize()).toList());

        AppConfig untouched = new AppConfig();
        NewProjectDiscovery.apply(offer, NewProjectDiscovery.Selection.empty(), untouched);
        assertTrue(untouched.sourceRoots.isEmpty(), "finding is not adding (D-AI5)");
        assertTrue(untouched.runbooks.isEmpty(), "finding is not declaring a runbook");
    }

    @Test
    void discoversTheCommittedAotGraphUnderMavenResources() throws Exception {
        Path source = root.resolve("src/main/java");
        Files.createDirectories(source);
        Path graph = root.resolve("src/main/resources/com/acme/generated/DemoProcessor.graphml");
        Files.createDirectories(graph.getParent());
        Files.writeString(graph, graphml());

        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);

        assertEquals(java.util.List.of(graph.toAbsolutePath().normalize()),
                offer.graphs().candidates().stream()
                        .map(c -> c.file().toAbsolutePath().normalize()).toList());
        assertTrue(offer.graphs().notes().isEmpty());
    }

    @Test
    void onlyAnExplicitSelectionIsApplied() throws Exception {
        Path source = root.resolve("src/main/java");
        Files.createDirectories(source);
        Path skill = root.resolve(".claude/skills/replay/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "---\nname: replay\ndescription: Replay this example.\n---\n");
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);

        AppConfig config = new AppConfig();
        NewProjectDiscovery.apply(offer, new NewProjectDiscovery.Selection(
                Set.of(source), Set.of(".claude/skills/replay/SKILL.md"), null), config);

        assertEquals(java.util.List.of(source.toAbsolutePath().normalize().toString()), config.sourceRoots);
        assertEquals(".claude/skills/replay/SKILL.md", config.runbooks.get("replay").path());
        assertEquals("Replay this example.", config.runbooks.get("replay").description());
    }

    @Test
    void confirmationControlsAreNeverPreselected() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/NewProjectOfferDialog.java"));
        assertFalse(source.contains("setSelected(true)"));
        assertTrue(source.contains("setSelected(false)"));
        assertNull(NewProjectDiscovery.Selection.empty().graph());
    }

    private static String graphml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:jGraph="http://www.jgraph.com/">
                  <key id="vertex_label" for="node" attr.name="nodeData" attr.type="string"/>
                  <graph edgedefault="undirected">
                    <node id="risk"><data key="vertex_label"><jGraph:ShapeNode>
                      <jGraph:label text="id:risk&#10;class:com.acme.demo.RiskNode"/>
                      <jGraph:Style properties="NODE"/>
                    </jGraph:ShapeNode></data></node>
                  </graph>
                </graphml>
                """;
    }
}
