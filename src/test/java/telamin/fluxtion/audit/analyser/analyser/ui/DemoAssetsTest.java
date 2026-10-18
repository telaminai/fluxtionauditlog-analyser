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
                        .tracesEveryInvocation(store.record(0)),
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
        // rule 1, on the most-seen surface in the app — and the one likeliest to reach a screenshot.
        // The banned strings are READ FROM CLAUDE.md's own sweep line rather than spelled here: a test
        // that spells them is itself a sweep hit (the M36 review found exactly that), and reading them
        // means this test follows the rule when the rule changes.
        // all four terms now come from CLAUDE.md: the fourth was added to the sweep on 2026-08-25, so
        // the local concatenation that stood in for it is gone. Nothing here spells a banned term.
        java.util.List<String> banned = rule1SweepTerms();
        assertTrue(banned.size() >= 4, "rule 1's sweep line was not found in CLAUDE.md, or has fewer "
                + "than the four terms this test expects: " + banned);
        for (String name : DemoAssets.files()) {
            String body = resource(name).toLowerCase(java.util.Locale.ROOT);
            for (String term : banned) {
                assertFalse(body.contains(term), name + " carries a real name: " + term);
            }
        }
    }

    /** Runs {@code body} with {@code user.home} at {@code home}, so the demo set unpacks there. */
    private static void underHome(java.nio.file.Path home, org.junit.jupiter.api.function.Executable body) throws Throwable {
        String was = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            body.execute();
        } finally {
            System.setProperty("user.home", was);
        }
    }

    private static telamin.fluxtion.audit.analyser.analyser.config.AppConfig loadSample(java.nio.file.Path profile) {
        var c = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        assertTrue(telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.load(profile, c,
                new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare()).loaded());
        return c;
    }

    /**
     * ★ Owner hands-on test (2026-10-18): the sample project's selected processor is a class the demo set SHIPS, found
     * under {@link DemoAssets#sourceRoot()} — so its source opens and {@code source {bean}} can name a node's class.
     * Witness: the placeholder the profile used to carry does not resolve there (asserted below); write
     * {@code new AppConfig()} unchanged in {@code ensureSampleProfile} — the selected class is blank and the test fails.
     */
    @Test
    void theSampleProjectsProcessorResolvesUnderTheDemoSourceRoot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home)
            throws Throwable {
        underHome(home, () -> {
            java.nio.file.Path root = DemoAssets.install();
            var sample = loadSample(DemoAssets.ensureSampleProfile(root));
            var resolver = new telamin.fluxtion.audit.analyser.analyser.source.SourceRootResolver(
                    java.util.List.of(DemoAssets.sourceRoot().toString()));
            assertEquals(DemoAssets.PROCESSOR, sample.selectedEventProcessor);
            assertTrue(resolver.find(sample.selectedEventProcessor).isPresent(),
                    "the selected processor ships in the demo set: " + sample.selectedEventProcessor);
            for (String fqn : sample.eventProcessorFqns) {
                assertTrue(resolver.find(fqn).isPresent(), "every configured processor ships: " + fqn);
            }
            assertTrue(sample.eventProcessorFqns.contains(DemoAssets.TRACED_PROCESSOR), "the traced log's own processor");
            // the wrong result this replaces: the placeholder never resolved
            assertFalse(resolver.find(DemoAssets.LEGACY_PLACEHOLDER).isPresent());
            assertTrue(DemoAssets.needsProcessorHeal(new telamin.fluxtion.audit.analyser.analyser.config.AppConfig()),
                    "AppConfig's default names no processor (owner decision): a fresh profile is filled in");
            assertEquals("", new telamin.fluxtion.audit.analyser.analyser.config.AppConfig().selectedEventProcessor);
        });
    }

    /**
     * ★ An existing home's sample profile written with the placeholder is HEALED on the next open, keeping what else it
     * holds. Witness: return early when the profile exists (the old {@code if (!Files.isRegularFile(profile))}) — the
     * placeholder stays selected.
     */
    @Test
    void anOlderSampleProfileWithThePlaceholderIsHealed(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home)
            throws Throwable {
        underHome(home, () -> {
            java.nio.file.Path root = DemoAssets.install();
            java.nio.file.Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(root);
            var old = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
            old.sourceRoots.add(root.toString());
            old.selectedEventProcessor = DemoAssets.LEGACY_PLACEHOLDER;
            old.hiddenColumns.clear();
            old.hiddenColumns.add("eventTime");
            old.hiddenColumnsSet = true;
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, old,
                    new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
            assertEquals(DemoAssets.LEGACY_PLACEHOLDER, loadSample(profile).selectedEventProcessor);

            DemoAssets.ensureSampleProfile(root);
            var healed = loadSample(profile);
            assertEquals(DemoAssets.PROCESSOR, healed.selectedEventProcessor);
            assertFalse(healed.eventProcessorFqns.contains(DemoAssets.LEGACY_PLACEHOLDER));
            assertEquals(java.util.List.of("eventTime"), healed.hiddenColumns, "the rest of the profile is kept");
            assertEquals(1, healed.sourceRoots.size());

            // a healthy profile is left alone (not rewritten: its bytes do not change)
            String before = java.nio.file.Files.readString(profile);
            DemoAssets.ensureSampleProfile(root);
            assertEquals(before, java.nio.file.Files.readString(profile));
        });
    }

    /** The terms of rule 1's `grep -ri "a\\|b\\|c"` sweep, parsed from CLAUDE.md so they are never spelled here. */
    private static java.util.List<String> rule1SweepTerms() {
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("CLAUDE.md"))) {
                Matcher m = Pattern.compile("grep -ri \"([^\"]+)\"").matcher(line);
                if (m.find()) {
                    return java.util.Arrays.stream(m.group(1).split("\\\\\\|"))
                            .map(t -> t.trim().toLowerCase(java.util.Locale.ROOT)).filter(t -> !t.isEmpty()).toList();
                }
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("CLAUDE.md must be readable from the project root", e);
        }
        return java.util.List.of();
    }
}
