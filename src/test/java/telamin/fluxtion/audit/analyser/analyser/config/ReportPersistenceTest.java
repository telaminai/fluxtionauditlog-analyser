package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.ColumnSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M33.4 — the F1 share-surface checklist for reports, asserted: ConfigStore round-trip, project
 * snapshot/restore/clear, SettingsShare ride-along under their OWN category (D-I4), and the
 * export-side whitelist (no category ticked → no report keys leave).
 */
class ReportPersistenceTest {

    private static ReportSpec sample() {
        return new ReportSpec("inv-1", "Oversell investigation", "2026-08-20T10:00:00Z",
                "prose about the report",
                new LogFingerprint("demo.yaml", 726, 100L, 900L),
                new FilterSnapshot(50L, 800L, Set.of("RiskBreachEvent"), "breach",
                        FilterState.GroupMode.RAW_EVENT),
                List.of(
                        SectionSpec.finding(3),
                        SectionSpec.record(5, "m.log.2"),
                        SectionSpec.chart("spread"),
                        SectionSpec.topology("risk path"),
                        SectionSpec.series(Map.of("expr", "a.x - b.y")),
                        SectionSpec.table(Map.of("verb", "read", "fields", "book.mid"),
                                List.of(new ColumnSpec("book.mid", "mid", "0.00", "right", "bold", 80)),
                                "book.mid > 17", "in breach"),
                        SectionSpec.narrative("the account")));
    }

    @Test
    void configStoreRoundTripsEveryField() {
        Properties p = new Properties();
        ConfigStore.writeReports(p, List.of(sample()));
        java.util.List<ReportSpec> back = new java.util.ArrayList<>();
        ConfigStore.readReports(p, back);
        assertEquals(List.of(sample()), back,
                "records compare by value — one assert covers fingerprint, filter, sections, columns");
    }

    @Test
    void aNestedReportCallRoundTripsAsStructuredData() {
        Map<String, Object> filter = Map.of(
                "from", 1_000.0,
                "to", 2_000.0,
                "dimensions", List.of("Quote", "Fill"));
        ReportSpec report = new ReportSpec("scoped", "Scoped", "", "", null, FilterSnapshot.all(),
                List.of(SectionSpec.table(Map.of(
                        "verb", "aggregate", "groupBy", "dimension", "filter", filter),
                        List.of(), null, null)));
        Properties p = new Properties();

        ConfigStore.writeReports(p, List.of(report));

        String savedFilter = null;
        for (int i = 0; i < 4; i++) {
            if ("filter".equals(p.getProperty("report.0.s.0.call." + i + ".key"))) {
                savedFilter = p.getProperty("report.0.s.0.call." + i + ".val");
                break;
            }
        }
        assertNotNull(savedFilter);
        assertTrue(savedFilter.startsWith("{") && savedFilter.endsWith("}"),
                "the existing flat call.N.val slot carries compact JSON only for a structured value");
        java.util.List<ReportSpec> back = new java.util.ArrayList<>();
        ConfigStore.readReports(p, back);

        Object restored = back.get(0).sections().get(0).call().get("filter");
        assertInstanceOf(Map.class, restored);
        assertEquals(filter, restored, "the verb can receive its nested filter again after a restart");
    }

    @Test
    void aPreM337ScalarOnlyReportStillReadsAsBareStrings() {
        Properties p = new Properties();
        p.setProperty("report.count", "1");
        p.setProperty("report.0.name", "old-report");
        p.setProperty("report.0.s.count", "1");
        p.setProperty("report.0.s.0.kind", "TABLE");
        p.setProperty("report.0.s.0.call.count", "3");
        p.setProperty("report.0.s.0.call.0.key", "verb");
        p.setProperty("report.0.s.0.call.0.val", "read");
        p.setProperty("report.0.s.0.call.1.key", "recordIndex");
        p.setProperty("report.0.s.0.call.1.val", "0");
        p.setProperty("report.0.s.0.call.2.key", "fields");
        p.setProperty("report.0.s.0.call.2.val", "book.mid");
        p.setProperty("report.0.s.0.col.count", "0");
        java.util.List<ReportSpec> back = new java.util.ArrayList<>();

        ConfigStore.readReports(p, back);

        assertEquals(Map.of("verb", "read", "recordIndex", "0", "fields", "book.mid"),
                back.get(0).sections().get(0).call(),
                "old scalar values keep their old spelling instead of being guessed as JSON");
    }

    @Test
    void aNullDimensionSetSurvives_theAllMeaning() {
        ReportSpec allDims = new ReportSpec("r", "t", "", "", null, FilterSnapshot.all(),
                List.of(SectionSpec.narrative("x")));
        Properties p = new Properties();
        ConfigStore.writeReports(p, List.of(allDims));
        java.util.List<ReportSpec> back = new java.util.ArrayList<>();
        ConfigStore.readReports(p, back);
        assertNull(back.get(0).filter().dimensions(),
                "null = ALL is FilterState's own rule; persistence must not collapse it to empty");
    }

    @Test
    void reportsRideProjectSnapshotRestoreAndClear() {
        AppConfig c = new AppConfig();
        c.reports.add(sample());
        var snap = ProjectProfile.snapshot(c);
        ProjectProfile.clearProjectScoped(c);
        assertTrue(c.reports.isEmpty(), "reports are project-tier: clear empties them");
        ProjectProfile.restore(snap, c);
        assertEquals(List.of(sample()), c.reports, "restore brings them back exactly");
    }

    @Test
    void reportsShareUnderTheirOwnCategory_neverAPassengerOnGraphs() {
        AppConfig c = new AppConfig();
        c.reports.add(sample());
        SettingsShare share = new SettingsShare("/home/t");

        String withoutCategory = share.export(c, Set.of(SettingsShare.Category.GRAPHS));
        assertFalse(withoutCategory.contains("report."),
                "D-I4: a shared report carries prose an agent wrote about the sender's data — it "
                        + "must not leave the machine under any other category's consent");

        String withCategory = share.export(c, Set.of(SettingsShare.Category.REPORTS));
        assertTrue(withCategory.contains("report.0.name"));

        var plan = share.preview(withCategory, new AppConfig());
        assertTrue(plan.present().contains(SettingsShare.Category.REPORTS));
        assertTrue(plan.summary().get(SettingsShare.Category.REPORTS).contains("narrative"),
                "the import summary names the cargo the consent is for: "
                        + plan.summary().get(SettingsShare.Category.REPORTS));

        AppConfig target = new AppConfig();
        target.reports.add(new ReportSpec("inv-1", "stale", "", "", null, null,
                List.of(SectionSpec.narrative("old"))));
        share.apply(plan, Set.of(SettingsShare.Category.REPORTS), target);
        assertEquals(1, target.reports.size(), "replace-by-name, like graphs and focuses");
        assertEquals("Oversell investigation", target.reports.get(0).title());
    }

    @Test
    void anUnknownSectionKindFromANewerBuildIsSkippedNotFatal() {
        Properties p = new Properties();
        ConfigStore.writeReports(p, List.of(sample()));
        p.setProperty("report.0.s.0.kind", "HOLOGRAM");   // a future build's section kind
        java.util.List<ReportSpec> back = new java.util.ArrayList<>();
        ConfigStore.readReports(p, back);
        assertEquals(6, back.get(0).sections().size(),
                "the unknown section drops; its siblings and the report survive");
    }
}
