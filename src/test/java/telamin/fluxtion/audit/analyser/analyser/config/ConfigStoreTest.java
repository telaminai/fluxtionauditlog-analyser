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
        store.save(c);

        AppConfig back = new ConfigStore(file).load();
        assertEquals(175, back.topologySpacingPercent);
        assertEquals(15, back.topologyTextSize);
        assertEquals(java.util.List.of("/tmp/one.graphml"), back.recentGraphml);
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
        assertTrue(back.recentGraphml.isEmpty());
    }

    @org.junit.jupiter.api.Test
    void displayPreferencesAreNeverExported() throws Exception {
        // they are a fact about this screen; the whitelist is opt-in, and this asserts they stayed out
        AppConfig c = new AppConfig();
        c.topologySpacingPercent = 250;
        c.topologyTextSize = 20;
        c.recentGraphml.add("/tmp/secret-path.graphml");
        String shared = new SettingsShare().export(c, java.util.EnumSet.allOf(SettingsShare.Category.class));

        assertFalse(shared.contains("topologySpacing"), shared);
        assertFalse(shared.contains("topologyTextSize"), shared);
        assertFalse(shared.contains("secret-path"), "recent paths must not leak either");
    }
}
