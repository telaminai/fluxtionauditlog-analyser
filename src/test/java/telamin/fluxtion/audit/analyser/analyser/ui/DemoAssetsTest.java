package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.NodeCoverage;
import telamin.fluxtion.audit.analyser.analyser.topology.Scaffolding;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M36 — the demo set is SHIPPED CONTENT, so the claims the start page makes about it are asserted
 * here rather than trusted. Each test is one sentence the page is allowed to say.
 */
class DemoAssetsTest {

    private static String resource(String name) {
        try (InputStream in = DemoAssets.class.getResourceAsStream("/demo/" + name)) {
            assertNotNull(in, "not in the jar: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Set<String> loggedIds(String yaml) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^\\s*-\\s+([A-Za-z_][\\w]*):\\s*\\{").matcher(yaml);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyFileTheLoaderPromisesIsActuallyInTheJar() {
        for (String name : DemoAssets.files()) resource(name);
    }

    @Test
    void theWalkthroughLogAndTheGraphACTUALLYpair() {
        // the whole reason the first draft's single-log bundle was wrong: a demo whose graph does
        // not match its log cannot show topology, step-through, dispatch order or coverage
        Set<String> logged = loggedIds(resource("demo-quote-audit.yaml"));
        Set<String> declared = Scaffolding.authoredNodes(
                GraphMlParser.parse(resource("demo-quote-processor.graphml")));
        assertFalse(logged.isEmpty(), "the walkthrough log must log something");
        assertTrue(declared.containsAll(logged),
                "every node the log writes must be in the graph, or the topology is a different "
                        + "system: logged=" + logged + " declared=" + declared);
    }

    @Test
    void coverageHasAREALanswer_notAVacuous100Percent() {
        // "which nodes never ran" is the strongest claim on the page. It needs a graph with nodes
        // the log does NOT reach, or the demo proves the feature works by never exercising it.
        Set<String> logged = loggedIds(resource("demo-quote-audit.yaml"));
        Set<String> declared = Scaffolding.authoredNodes(
                GraphMlParser.parse(resource("demo-quote-processor.graphml")));
        NodeCoverage cov = NodeCoverage.of(declared, logged, Set.of());
        assertFalse(cov.uncovered().isEmpty(),
                "the demo must have nodes that never ran, or the coverage action shows nothing");
        assertFalse(cov.covered().isEmpty(), "and nodes that did, or it shows nothing either");
    }

    @Test
    void theTracedLogReallyIsTraced_soDidNotRunIsPROOF() {
        var store = new HeapLogStore(resource("demo-quote-audit-traced.yaml"));
        assertTrue(store.size() > 0);
        assertTrue(telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace
                        .tracesEveryInvocation(store.record(0).nodeLogs()),
                "without tracing the page could describe \"did not run\" but never show it");
    }

    @Test
    void theSeriesLogHasEnoughPointsToBeAChart() {
        var store = new HeapLogStore(resource("demo-quote-series.yaml"));
        assertTrue(store.size() > 100, "ten points is not a chart; got " + store.size());
    }

    @Test
    void theSourceCoversTheNodesTheTopologyShows_soAnodeClickResolves() {
        String nodes = resource("com/acme/demo/node/Nodes.java");
        Set<String> logged = loggedIds(resource("demo-quote-audit.yaml"));
        assertFalse(logged.isEmpty());
        // the node ids are field names in the builder; the classes they resolve to live in Nodes.java
        assertTrue(nodes.contains("class ") || nodes.contains("record "),
                "the demo source must declare the node types a topology click opens");
    }

    @Test
    void theShippedDemoCarriesNoRealNames() {
        // rule 1, on the most-seen surface in the app — and the one likeliest to reach a screenshot
        for (String name : DemoAssets.files()) {
            String body = resource(name).toLowerCase(java.util.Locale.ROOT);
            for (String banned : new String[]{"aquis", "talos", "nonco", "v12technology"}) {
                assertFalse(body.contains(banned), name + " carries a real name: " + banned);
            }
        }
    }
}
