package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the M68.1 decision recorded in {@code EntryPointResolver.addSoleExportedService}: that caller keeps the
 * class-name fallback because the declared {@code fluxtion.framework} fact is NODE-scoped and is ignored for
 * EXPORT_SERVICE vertices. If the scope rule ever changes, this fails and the caller needs a new decision.
 */
class EntryPointAuthorshipTest {

    @Test
    void theNodeOnlyAndDeclaredFirstAnswersAgreeForEveryExportedService() throws Exception {
        List<ProcessorTopology> graphs = List.of(
                EvidenceIntegrityCoverageTest.packetGraph(),
                GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml")));
        int checked = 0;
        for (ProcessorTopology g : graphs) {
            for (ProcessorTopology.Node n : g.nodes()) {
                if (n.kind() != ProcessorTopology.Kind.EXPORT_SERVICE) continue;
                checked++;
                assertEquals(Scaffolding.isScaffolding(n), Scaffolding.isScaffolding(n, g.vocabulary()),
                        "exported service " + n.id() + " must classify identically on both overloads");
            }
        }
        assertTrue(checked >= 2, "the fixtures must actually contain exported services: " + checked);

        // a crafted service that DECLARES the opposite of its package is still not read
        var svc = new ProcessorTopology.Node("svc", "svc", "com.telamin.fluxtion.runtime.service.ServiceListener",
                ProcessorTopology.Kind.EXPORT_SERVICE, Map.of(Scaffolding.FRAMEWORK_FACT, "false"));
        var trusted = new GraphVocabulary(GraphVocabulary.Mode.PARALLEL, "1.0", Map.of());
        assertTrue(Scaffolding.isScaffolding(svc));
        assertTrue(Scaffolding.isScaffolding(svc, trusted), "node-scoped: the declaration is ignored here");
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = EntryPointAuthorshipTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
