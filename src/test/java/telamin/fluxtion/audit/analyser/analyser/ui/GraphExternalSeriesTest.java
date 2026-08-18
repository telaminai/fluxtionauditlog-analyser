package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The synchronous half of M29.2's panel wiring — spec state, replace semantics, and the
 * persistable-mutation contract. Loading and painting are async/Swing and live on the eyeball list;
 * the loader itself is fully covered by {@code ExternalCsvLoaderTest}.
 */
class GraphExternalSeriesTest {

    private static GraphSpec.ExternalSpec spec(String label) {
        return new GraphSpec.ExternalSpec("/tmp/x.csv", label, "ts", "epochMillis", null, "mid", 0);
    }

    @Test
    void setExternalReplacesTheSetAndFiresTheMutationContract() {
        GraphPanel panel = new GraphPanel();
        int[] mutations = {0};
        panel.setOnMutation(() -> mutations[0]++);

        panel.setExternal(List.of(spec("venue mid")));
        assertEquals(1, panel.externalSpecs().size());
        assertEquals(1, mutations[0], "external series are persistable state — B-M20-3's contract applies");

        panel.setExternal(List.of());
        assertTrue(panel.externalSpecs().isEmpty(), "present-means-replace, like guides and bands");
        assertEquals(2, mutations[0]);
    }

    @Test
    void externalSpecsRoundTripThroughConfigStore() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("cfg");
        var store = new telamin.fluxtion.audit.analyser.analyser.config.ConfigStore(dir.resolve("config"));
        var cfg = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        cfg.savedGraphs.add(new GraphSpec("g", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new GraphSpec.ExternalSpec("/data/venue.csv", "venue mid", "ts", "iso8601",
                        "Europe/London", "mid", 250))));
        store.save(cfg);
        var back = store.load();
        GraphSpec.ExternalSpec e = back.savedGraphs.get(0).external().get(0);
        assertEquals("/data/venue.csv", e.path());
        assertEquals("venue mid", e.label());
        assertEquals("iso8601", e.timeFormat());
        assertEquals("Europe/London", e.zone());
        assertEquals(250, e.offsetMillis());
    }

    @Test
    void externalPathsTravelHomeRelativeThroughShares() {
        var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare("/home/tester");
        var sender = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        sender.savedGraphs.add(new GraphSpec("g", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new GraphSpec.ExternalSpec("/home/tester/runs/venue.csv", "venue mid", "ts",
                        "epochMillis", null, "mid", 0))));
        String text = share.export(sender,
                java.util.EnumSet.of(telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category.GRAPHS));
        assertTrue(text.contains("~/runs/venue.csv"), "home paths are written portable (D-F5)");

        var receiver = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        share.apply(share.preview(text, receiver),
                java.util.EnumSet.of(telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category.GRAPHS),
                receiver);
        assertEquals("/home/tester/runs/venue.csv",
                receiver.savedGraphs.get(0).external().get(0).path(), "expanded on the receiving side");
    }

    @Test
    void restoreAppliesExternalSpecsWithoutEchoingAnEdit() {
        GraphTabs tabs = new GraphTabs();
        tabs.bind(new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(
                telamin.fluxtion.audit.analyser.analyser.parse.Samples.sample()),
                new telamin.fluxtion.audit.analyser.analyser.filter.FilterState());
        int[] fired = {0};
        tabs.setChangeListener(() -> fired[0]++);
        tabs.restore(List.of(new GraphSpec("g", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new GraphSpec.ExternalSpec("/nope/missing.csv", "gone", "ts", "epochMillis",
                        null, "mid", 0)))));
        assertEquals(0, fired[0], "restoring persisted state is not a user edit");
        assertEquals(1, tabs.specs().get(0).external().size(), "the DEFINITION survives a missing file (D-F5)");
    }

    @Test
    void externalSpecsAreImmutableSnapshots() {
        GraphPanel panel = new GraphPanel();
        panel.setExternal(new java.util.ArrayList<>(List.of(spec("a"))));
        assertThrows(UnsupportedOperationException.class, () -> panel.externalSpecs().add(spec("b")));
    }
}
