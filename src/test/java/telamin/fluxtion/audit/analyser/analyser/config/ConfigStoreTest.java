package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {

    @Test
    void roundTripsAllFields(@TempDir Path dir) {
        Path cfg = dir.resolve("config");
        ConfigStore store = new ConfigStore(cfg);

        AppConfig c = new AppConfig();
        c.logFile = "/logs/audit.yaml";
        c.apiKey = "sk-secret-123";
        c.llmProvider = "openai";
        c.llmModel = "gpt-x";
        c.sourceRoots.add("/src/market-maker-lib");
        c.sourceRoots.add("/src/trade-calculator-impl-lib");
        c.eventProcessorFqns.add("com.acme.marketmaker.strategy.DemoMarketMakerStrategy");
        c.memoryThresholdMb = 250;
        c.windowW = 1000;
        c.addRecent("/logs/audit.yaml");

        store.save(c);

        AppConfig d = store.load();
        assertEquals("/logs/audit.yaml", d.logFile);
        assertEquals("sk-secret-123", d.apiKey);
        assertEquals("openai", d.llmProvider);
        assertEquals("gpt-x", d.llmModel);
        assertEquals(List.of("/src/market-maker-lib", "/src/trade-calculator-impl-lib"), d.sourceRoots);
        assertEquals(250, d.memoryThresholdMb);
        assertEquals(1000, d.windowW);
        assertTrue(d.recentFiles.contains("/logs/audit.yaml"));
        assertEquals(List.of("com.acme.marketmaker.strategy.DemoMarketMakerStrategy"), d.eventProcessorFqns);
    }

    @Test
    void savedGraphsRoundTripWithNames(@TempDir Path dir) {
        Path cfg = dir.resolve("config");
        ConfigStore store = new ConfigStore(cfg);

        AppConfig c = new AppConfig();
        c.savedGraphs.add(new GraphSpec("Hedge vs position", List.of("hedgeNodeqty", "posNodepos")));
        c.savedGraphs.add(new GraphSpec("Spread", List.of("quoteNodespread")));
        store.save(c);

        AppConfig d = store.load();
        assertEquals(2, d.savedGraphs.size());
        assertEquals("Hedge vs position", d.savedGraphs.get(0).name());
        assertEquals(List.of("hedgeNodeqty", "posNodepos"), d.savedGraphs.get(0).series());
        assertEquals("Spread", d.savedGraphs.get(1).name());
    }

    @Test
    void savedGraphsRoundTripPinnedWindow(@TempDir Path dir) {
        Path cfg = dir.resolve("config");
        ConfigStore store = new ConfigStore(cfg);

        AppConfig c = new AppConfig();
        c.savedGraphs.add(new GraphSpec("Pick-off exposure", List.of("askqty"), 1_754_449_420_000L, 1_754_449_500_000L));
        c.savedGraphs.add(new GraphSpec("Following", List.of("posqty")));   // no pin → follows the filter
        store.save(c);

        AppConfig d = store.load();
        GraphSpec pinned = d.savedGraphs.get(0);
        assertTrue(pinned.isPinned());
        assertEquals(1_754_449_420_000L, pinned.from());
        assertEquals(1_754_449_500_000L, pinned.to());

        GraphSpec following = d.savedGraphs.get(1);
        assertFalse(following.isPinned(), "no from/to persisted → follows the filter");
        assertNull(following.from());
        assertNull(following.to());
    }

    @Test
    void savedGraphsRoundTripDerivedFormulas(@TempDir Path dir) {
        Path cfg = dir.resolve("config");
        ConfigStore store = new ConfigStore(cfg);

        AppConfig c = new AppConfig();
        c.savedGraphs.add(new GraphSpec("Spread", List.of("askqty"),
                List.of(new GraphSpec.ExprSpec("quoted spread", "askMakerOrder.price - bidMakerOrder.price", "LOCF")),
                null, null));
        store.save(c);

        AppConfig d = store.load();
        assertEquals(1, d.savedGraphs.get(0).exprs().size());
        GraphSpec.ExprSpec ex = d.savedGraphs.get(0).exprs().get(0);
        assertEquals("quoted spread", ex.label());
        assertEquals("askMakerOrder.price - bidMakerOrder.price", ex.expr());
        assertEquals("LOCF", ex.resolve());
        assertEquals(List.of("askqty"), d.savedGraphs.get(0).series());
    }

    @Test
    void savedGraphsRoundTripGuidesAndBands(@TempDir Path dir) {
        // M28.5/.6: a guide is a value+label+axis; a band persists its CONDITION, never its intervals
        Path cfg = dir.resolve("config");
        ConfigStore store = new ConfigStore(cfg);

        AppConfig c = new AppConfig();
        c.savedGraphs.add(new GraphSpec("Spread", List.of("quoteNodespread"), List.of(), null, null,
                null, null, List.of(), List.of(),
                List.of(new GraphSpec.GuideSpec(0.004, "4bp limit", false),
                        new GraphSpec.GuideSpec(150.0, "qty cap", true)),
                List.of(new GraphSpec.BandSpec("askMakerOrder.price - bidMakerOrder.price > 0.004", "in breach"))));
        store.save(c);

        AppConfig d = store.load();
        GraphSpec g = d.savedGraphs.get(0);
        assertEquals(2, g.guides().size());
        assertEquals(0.004, g.guides().get(0).value(), 1e-12);
        assertEquals("4bp limit", g.guides().get(0).label());
        assertTrue(g.guides().get(1).rightAxis(), "the axis choice survives");
        assertEquals(1, g.bands().size());
        assertEquals("askMakerOrder.price - bidMakerOrder.price > 0.004", g.bands().get(0).expr());
        assertEquals("in breach", g.bands().get(0).label());
    }

    @Test
    void apiKeyIsStoredInCleartext(@TempDir Path dir) throws IOException {
        Path cfg = dir.resolve("config");
        AppConfig c = new AppConfig();
        c.apiKey = "sk-plaintext-xyz";
        new ConfigStore(cfg).save(c);
        assertTrue(Files.readString(cfg).contains("sk-plaintext-xyz"), "key persisted in cleartext by decision");
    }

    @Test
    void missingFileYieldsDefaults(@TempDir Path dir) {
        AppConfig d = new ConfigStore(dir.resolve("does-not-exist")).load();
        assertEquals("com.acme.marketmaker.strategy.DemoMarketMakerStrategy", d.selectedEventProcessor);
        assertEquals(500, d.memoryThresholdMb);
        assertTrue(d.sourceRoots.isEmpty());
        assertEquals(List.of(AppConfig.defaultMavenRepo()), d.mavenRepos, "default ~/.m2/repository");
        assertTrue(d.searchMavenRepos);
    }

    @Test
    void mavenReposRoundTripIncludingCleared(@TempDir Path dir) {
        Path cfg = dir.resolve("config");
        AppConfig c = new AppConfig();
        c.mavenRepos.clear();
        c.mavenRepos.add("/custom/repo-a");
        c.mavenRepos.add("/custom/repo-b");
        c.searchMavenRepos = false;
        new ConfigStore(cfg).save(c);

        AppConfig d = new ConfigStore(cfg).load();
        assertEquals(List.of("/custom/repo-a", "/custom/repo-b"), d.mavenRepos);
        assertFalse(d.searchMavenRepos);

        // an explicitly emptied list must stay empty on reload, not fall back to the default
        d.mavenRepos.clear();
        new ConfigStore(cfg).save(d);
        assertTrue(new ConfigStore(cfg).load().mavenRepos.isEmpty());
    }

    // ---- topology display preferences (M22.29) ----------------------------------------------------

    @org.junit.jupiter.api.Test
    void topologyDisplayPreferencesSurviveARoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path file = dir.resolve("config");
        ConfigStore store = new ConfigStore(file);
        AppConfig c = new AppConfig();
        c.topologySpacingPercent = 175;
        c.topologyTextSize = 15;
        c.recentGraphml.add("/tmp/one.graphml");
        c.graphmlFile = "/tmp/showing.graphml";
        store.save(c);

        AppConfig back = new ConfigStore(file).load();
        assertEquals(175, back.topologySpacingPercent);
        assertEquals(15, back.topologyTextSize);
        assertEquals(java.util.List.of("/tmp/one.graphml"), back.recentGraphml);
        assertEquals("/tmp/showing.graphml", back.graphmlFile,
                "the topology showing at shutdown is restored beside the log");
    }

    @org.junit.jupiter.api.Test
    void defaultsApplyWhenTheKeysAreAbsent(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        // an older config file has neither key; it must load rather than yielding zeros
        java.nio.file.Path file = dir.resolve("config");
        java.nio.file.Files.writeString(file, "theme=Dark\n");
        AppConfig back = new ConfigStore(file).load();
        assertEquals(100, back.topologySpacingPercent);
        assertEquals(11, back.topologyTextSize);
        assertTrue(back.topologySyncSource, "tracking on is the default — it is what people expect");
        assertTrue(back.recentGraphml.isEmpty());
    }

    @org.junit.jupiter.api.Test
    void displayPreferencesAreNeverExported() throws Exception {
        // they are a fact about this screen; the whitelist is opt-in, and this asserts they stayed out
        AppConfig c = new AppConfig();
        c.topologySpacingPercent = 250;
        c.topologyTextSize = 20;
        c.recentGraphml.add("/tmp/secret-path.graphml");
        c.graphmlFile = "/tmp/secret-path.graphml";
        String shared = new SettingsShare().export(c, java.util.EnumSet.allOf(SettingsShare.Category.class));

        assertFalse(shared.contains("topologySpacing"), shared);
        assertFalse(shared.contains("topologyTextSize"), shared);
        assertFalse(shared.contains("secret-path"), "recent paths must not leak either");
    }

    @org.junit.jupiter.api.Test
    void theTopologyViewSurvivesARoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path file = dir.resolve("config");
        AppConfig c = new AppConfig();
        c.topologyZoom = 1.75;
        c.topologyPanX = -240.5;
        c.topologyPanY = 88.25;
        c.topologyOrientation = "LEFT_RIGHT";
        c.topologySyncSource = false;
        new ConfigStore(file).save(c);

        AppConfig back = new ConfigStore(file).load();
        assertEquals(1.75, back.topologyZoom, 0.0001);
        assertEquals(-240.5, back.topologyPanX, 0.0001, "a negative pan is normal — it is a scroll offset");
        assertEquals(88.25, back.topologyPanY, 0.0001);
        assertEquals("LEFT_RIGHT", back.topologyOrientation);
        assertFalse(back.topologySyncSource, "turning tracking off must survive a restart");
    }

    @org.junit.jupiter.api.Test
    void anUnsavedViewMeansFitRatherThanZeroZoom() {
        // 0 is the "never saved" marker; the panel fits the graph instead of applying a useless transform
        assertEquals(0d, new AppConfig().topologyZoom, 0.0001);
        assertEquals("TOP_DOWN", new AppConfig().topologyOrientation);
    }

    @org.junit.jupiter.api.Test
    void resettingTheTopologyViewRestoresEveryDisplayDefault() {
        AppConfig c = new AppConfig();
        c.topologyZoom = 3;
        c.topologyPanX = 10;
        c.topologyPanY = 20;
        c.topologySpacingPercent = 300;
        c.topologyTextSize = 20;
        c.topologyOrientation = "LEFT_RIGHT";
        c.topologySyncSource = false;

        c.clearTopologyView();

        AppConfig fresh = new AppConfig();
        assertEquals(fresh.topologyZoom, c.topologyZoom, 0.0001);
        assertEquals(fresh.topologyPanX, c.topologyPanX, 0.0001);
        assertEquals(fresh.topologyPanY, c.topologyPanY, 0.0001);
        assertEquals(fresh.topologySpacingPercent, c.topologySpacingPercent);
        assertEquals(fresh.topologyTextSize, c.topologyTextSize);
        assertEquals(fresh.topologyOrientation, c.topologyOrientation,
                "a reset that leaves one setting behind is worse than none — it looks broken");
        assertEquals(fresh.topologySyncSource, c.topologySyncSource);
    }

    @org.junit.jupiter.api.Test
    void chartAnnotationsSurviveARoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        // a note that does not survive a restart is one nobody bothers to write
        java.nio.file.Path file = dir.resolve("config");
        AppConfig c = new AppConfig();
        c.savedGraphs.add(new GraphSpec("Oversell", java.util.List.of("a.b"), java.util.List.of(),
                null, null, "why", "the shelf empties here",
                java.util.List.of(new GraphSpec.NoteSpec(1767258083000L, "first oversell", "a.b")),
                java.util.List.of("a.b")));
        new ConfigStore(file).save(c);

        AppConfig back = new ConfigStore(file).load();
        GraphSpec g = back.savedGraphs.get(0);
        assertEquals("the shelf empties here", g.explanation());
        assertEquals(1, g.notes().size());
        assertEquals(1767258083000L, g.notes().get(0).at(), "epoch millis must not be truncated");
        assertEquals("first oversell", g.notes().get(0).text());
        assertEquals(java.util.List.of("a.b"), g.rightAxis());
    }

    @org.junit.jupiter.api.Test
    void aGraphSavedBeforeAnnotationsExistedStillLoads(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path file = dir.resolve("config");
        java.nio.file.Files.writeString(file,
                "graph.count=1\ngraph.0.name=Old\ngraph.0.count=1\ngraph.0.0=a.b\n");
        GraphSpec g = new ConfigStore(file).load().savedGraphs.get(0);
        assertEquals("Old", g.name());
        assertEquals("", g.explanation(), "absent annotations are empty, not null");
        assertTrue(g.notes().isEmpty());
        assertTrue(g.rightAxis().isEmpty());
    }

    @Test
    void externalMarkerSourcesRoundTrip(@org.junit.jupiter.api.io.TempDir Path dir) {
        // M32.8: the CSV-sourced marker persists as its DEFINITION — path, columns, declared clock —
        // never its points, exactly like external series (M28.6's rule, third artifact)
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        AppConfig c = new AppConfig();
        var mk = new GraphSpec.MarkerSpec("venue fills", "triangleUp", null, null, null,
                "/data/fills.csv", "ts", "epochMillis", "UTC", "px", "ordId", 250L);
        c.savedGraphs.add(new GraphSpec("g", java.util.List.of("a.b"), java.util.List.of(), null, null,
                null, null, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(mk)));
        store.save(c);
        AppConfig back = store.load();
        assertEquals(java.util.List.of(mk), back.savedGraphs.get(0).markers());
        assertTrue(back.savedGraphs.get(0).markers().get(0).isExternal());
    }
}
