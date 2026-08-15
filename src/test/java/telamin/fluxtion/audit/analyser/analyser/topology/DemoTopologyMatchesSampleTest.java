package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps {@code demo-marketmaker.graphml} in step with {@code sample.yml}.
 *
 * <p>That pair is what the user guide's topology screenshot is made from, and the page states the two
 * match. If someone adds a node to the sample log and not to the demo topology, the screenshot quietly
 * starts depicting a build mismatch — the exact failure {@link ProcessorTopology.Match} exists to warn
 * about. Cheaper to fail here than to ship a picture that argues against its own caption.
 */
class DemoTopologyMatchesSampleTest {

    private static ProcessorTopology demoTopology() throws IOException {
        try (InputStream in = DemoTopologyMatchesSampleTest.class
                .getResourceAsStream("/topology/demo-marketmaker.graphml")) {
            assertNotNull(in, "demo topology fixture missing");
            return GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Set<String> sampleInstanceIds() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        Set<String> ids = new LinkedHashSet<>();
        for (int row = 0; row < store.size(); row++) {
            for (NodeLog node : store.record(row).nodeLogs()) ids.add(node.instanceId());
        }
        return ids;
    }

    @Test
    void everyNodeInTheSampleLogExistsInTheDemoTopology() throws IOException {
        ProcessorTopology.Match match = demoTopology().match(sampleInstanceIds());
        assertTrue(match.complete(),
                "the docs screenshot claims these match — " + match.describe());
        assertEquals(1.0, match.coverage());
    }

    @Test
    void theDemoTopologyIsWiredNotJustAPileOfNodes() throws IOException {
        ProcessorTopology topology = demoTopology();
        assertTrue(topology.nodeCount() >= sampleInstanceIds().size());
        assertTrue(topology.edgeCount() > topology.nodeCount() / 2, "a graph, not a list");
        assertFalse(topology.roots().isEmpty(), "something has to be an entry point");
        assertTrue(topology.roots().size() < topology.nodeCount(),
                "if everything is a root, nothing is connected");
    }
}
