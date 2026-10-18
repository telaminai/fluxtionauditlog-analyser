package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner decision 2026-10-18: a saved chart is never destroyed irrecoverably. The bin's rules, headless — as
 * {@link ReportBinTest} pins the report bin's.
 */
class ChartBinTest {

    static GraphSpec chart(String name, String note) {
        return new GraphSpec(name, List.of("bid\u0001price"), List.of(new GraphSpec.ExprSpec("spread", "a.b - c.d", null)),
                1_000L, 9_000L, "why " + name, "the reading of " + name,
                List.of(new GraphSpec.NoteSpec(2_000L, note, null)), List.of("spread"),
                List.of(new GraphSpec.GuideSpec(0.004, "limit", false)), List.of(new GraphSpec.BandSpec("a.b > 1", "over")),
                List.of(), List.of(), "line", true);
    }

    static AppConfig project(String path, GraphSpec... charts) {
        AppConfig c = new AppConfig();
        c.activeProjectPath = path;
        c.savedGraphs.addAll(List.of(charts));
        return c;
    }

    /** ★ Witness: drop writeDeletedCharts from ConfigStore's save — the reloaded bin is empty and restore fails. */
    @Test
    @DisplayName("★ delete then restore gives the exact definition back, across a save and reload of the machine settings")
    void deleteThenRestoreRoundTrips() {
        GraphSpec before = chart("g", "first breach");
        AppConfig c = project("/work/a/project.fluxtion-settings", before, chart("keep", "x"));
        assertNotNull(ChartBin.delete(c, "g", "2026-10-18T10:00:00Z"));
        assertEquals(List.of("keep"), c.savedGraphs.stream().map(GraphSpec::name).toList());

        Properties p = new Properties();
        ConfigStore.writeDeletedCharts(p, c.deletedCharts);
        AppConfig reloaded = project(c.activeProjectPath, chart("keep", "x"));
        ConfigStore.readDeletedCharts(p, reloaded.deletedCharts);
        assertEquals(List.of("g"), ChartBin.restorableNames(reloaded));
        assertEquals(ChartBin.id(c.deletedCharts.get(0)), ChartBin.id(reloaded.deletedCharts.get(0)), "the id is stable");

        var restored = ChartBin.restore(reloaded, "g", n -> false);
        assertTrue(restored.ok(), restored.refusal());
        assertEquals(ConfigStore.chartDefinitionDigest(before), ConfigStore.chartDefinitionDigest(restored.chart()),
                "exactly the definition that was deleted");
        assertEquals(before, restored.chart(), "every component, including the style and the open flag");
        assertTrue(ChartBin.restorable(reloaded).isEmpty(), "and it left the bin");
    }

    /** ★ Witness: in ChartBin.keep, drop the trim loop — the bin grows past its bound. */
    @Test
    @DisplayName("★ the bin keeps the newest " + ChartBin.CAPACITY + " (the report and walk bins' bound), dropping the oldest")
    void theBinIsBounded() {
        AppConfig c = project("/p");
        for (int i = 0; i < ChartBin.CAPACITY + 3; i++) ChartBin.keep(c, chart("c" + i, "n"), ChartBin.Reason.DELETED, "t" + i);
        assertEquals(ChartBin.CAPACITY, c.deletedCharts.size());
        assertEquals(ReportBin.CAPACITY, ChartBin.CAPACITY, "bounded the same way as the other bins");
        assertEquals("c" + (ChartBin.CAPACITY + 2), ChartBin.restorableNames(c).get(0), "newest first");
        assertFalse(ChartBin.restorableNames(c).contains("c0"), "the oldest went first");
        assertFalse(ChartBin.restorableNames(c).contains("c2"));
    }

    @Test
    @DisplayName("a chart is restorable only into its own project, and never over a chart that has its name now")
    void scopedAndNeverReplaces() {
        AppConfig c = project("/a", chart("g", "n"));
        ChartBin.delete(c, "g", "now");
        c.activeProjectPath = "/b";
        assertTrue(ChartBin.restorable(c).isEmpty(), "another project does not see it");
        c.activeProjectPath = "/a";
        var refused = ChartBin.restore(c, "g", n -> true);
        assertFalse(refused.ok());
        assertTrue(refused.refusal().contains("never replaces"), refused.refusal());
        assertEquals(1, c.deletedCharts.size(), "a refusal changes nothing");
    }

    @Test
    @DisplayName("two versions of one name: the name restores the newest, an id restores that version")
    void restoreByIdPicksTheVersion() {
        AppConfig c = project("/a");
        ChartBin.keep(c, chart("g", "older"), ChartBin.Reason.REPLACED, "t1");
        ChartBin.keep(c, chart("g", "newer"), ChartBin.Reason.DELETED, "t2");
        var rows = ChartBin.listing(c);
        assertEquals(List.of("deleted", "replaced"), rows.stream().map(r -> r.get("reason")).toList());
        assertNotEquals(rows.get(0).get("id"), rows.get(1).get("id"));
        var older = ChartBin.restore(c, String.valueOf(rows.get(1).get("id")), n -> false);
        assertEquals("older", older.chart().notes().get(0).text());
        var newer = ChartBin.restore(c, "g", n -> false);
        assertEquals("newer", newer.chart().notes().get(0).text());
    }

    @Test
    @DisplayName("an empty definition is nothing to lose; any series, note or annotation is")
    void emptyDefinitions() {
        assertTrue(ChartBin.isEmptyDefinition(new GraphSpec("Graph 1", List.of(), List.of(), null, null, null, "",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, true)));
        assertFalse(ChartBin.isEmptyDefinition(chart("g", "n")));
    }

    /** ★ Witness: drop the keep loop in SettingsShare.apply — the replaced chart is gone from everywhere. */
    @Test
    @DisplayName("★ an import that replaces a chart by name keeps the previous definition in the bin")
    void anImportReplacementIsKept() {
        GraphSpec mine = chart("g", "mine");
        AppConfig target = project("/a", mine);
        var plan = new SettingsShare().preview(shareWith(chart("g", "theirs")), target);
        new SettingsShare().apply(plan, java.util.Set.of(SettingsShare.Category.GRAPHS), target);
        assertEquals("theirs", target.savedGraphs.get(0).notes().get(0).text(), "the import replaced it");
        assertEquals(1, target.deletedCharts.size());
        assertEquals(ChartBin.Reason.REPLACED, target.deletedCharts.get(0).reason());
        assertEquals(ConfigStore.chartDefinitionDigest(mine),
                ConfigStore.chartDefinitionDigest(target.deletedCharts.get(0).chart()), "mine, kept exactly");
    }

    private static String shareWith(GraphSpec g) {
        AppConfig src = project("/src", g);
        return new SettingsShare().export(src, java.util.EnumSet.of(SettingsShare.Category.GRAPHS));
    }
}
