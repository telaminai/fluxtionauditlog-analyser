package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S1 (spec-spotlight-walks.md §3.1; review R1) — walks are stored like reports, and every path reports take a walk
 * takes too: the ConfigStore round trip, the project snapshot/restore/clear, the machine save while a project is
 * active, the share export/preview/apply on their own count, and a machine-local bin that never leaves.
 */
class WalkPersistenceTest {

    static WalkSpec sample(String name) {
        var step1 = new WalkSpec.Step("The alarm is raised on the third rejected row.",
                new WalkSpec.View("graph", new WalkSpec.Filter(10L, 90L, "RAW_EVENT", List.of("Tick", "Row"), "rejected"),
                        null, "Alarm lifecycle", null),
                List.of(new WalkSpec.Target("graph:Alarm lifecycle:note:1", "RAISED: 3 rejected",
                        new WalkSpec.Basis("chart", "sha256:c1", ""))));
        var step2 = new WalkSpec.Step("",
                new WalkSpec.View("topology", WalkSpec.Filter.ALL, 36, null, new WalkSpec.FocusRef("alarm path", "sha256:f1")),
                List.of(new WalkSpec.Target("records:row:36", "the reset", new WalkSpec.Basis("record", "sha256:r36", "HeapLogStore")),
                        new WalkSpec.Target("topology:node:feedAlarm", "", new WalkSpec.Basis("graph", "", ""))));
        return new WalkSpec(name, "The admin reset re-arms the alarm", WalkSpec.AUTHOR_ASSISTANT,
                "2026-09-27T18:02:11Z", "2026-09-27T18:05:00Z", new LogFingerprint("demo.yaml", 67, 1L, 9L),
                List.of("sha256:file1"), List.of(step1, step2), Map.of());
    }

    private static List<WalkSpec> roundTrip(List<WalkSpec> walks) {
        Properties p = new Properties();
        ConfigStore.writeWalks(p, walks);
        List<WalkSpec> back = new ArrayList<>();
        ConfigStore.readWalks(p, back);
        return back;
    }

    @Test
    @DisplayName("every field round-trips: view, complete filter, focus digest, targets and their bases")
    void configStoreRoundTripsEveryField() {
        assertEquals(List.of(sample("w1")), roundTrip(List.of(sample("w1"))));
    }

    @Test
    @DisplayName("a stated filter at its defaults survives as a stated filter, not as 'no filter change'")
    void aDefaultFilterIsStillStated() {
        WalkSpec back = roundTrip(List.of(sample("w1"))).get(0);
        assertEquals(WalkSpec.Filter.ALL, back.steps().get(1).view().filter(),
                "a step that SETS the filter to all must not come back as a step that leaves the filter as it is");
        assertNull(back.steps().get(0).view().focus(), "and an absent focus stays absent");
    }

    @Test
    @DisplayName("keys a newer version wrote under a walk are carried over on rewrite")
    void unknownKeysUnderAWalkSurvive() {
        Properties p = new Properties();
        ConfigStore.writeWalks(p, List.of(sample("w1")));
        p.setProperty("walk.0.s.0.view.window.from", "5");   // a future version's field
        p.setProperty("walk.0.voice", "calm");
        List<WalkSpec> back = new ArrayList<>();
        ConfigStore.readWalks(p, back);
        Properties again = new Properties();
        ConfigStore.writeWalks(again, back);
        assertEquals("5", again.getProperty("walk.0.s.0.view.window.from"));
        assertEquals("calm", again.getProperty("walk.0.voice"));
        List<WalkSpec> twice = new ArrayList<>();
        ConfigStore.readWalks(again, twice);
        assertEquals(back, twice, "and the known fields read exactly as before");
    }

    @Test
    @DisplayName("an unknown run basis stays unknown: no run keys, an empty basis back")
    void anUnknownRunBasisStaysUnknown() {
        WalkSpec w = sample("w1");
        WalkSpec unknown = new WalkSpec(w.name(), w.title(), w.author(), w.createdAt(), w.updatedAt(), w.fingerprint(),
                List.of(), w.steps(), Map.of());
        Properties p = new Properties();
        ConfigStore.writeWalks(p, List.of(unknown));
        assertNull(p.getProperty("walk.0.run.count"), "absence is how unknown is stored");
        assertEquals(List.of(), roundTrip(List.of(unknown)).get(0).runBasis());
    }

    @Test
    @DisplayName("the project tier: snapshot, restore and clear carry walks beside reports")
    void projectSnapshotRestoreAndClearCarryWalks() {
        AppConfig c = new AppConfig();
        c.walks.add(sample("mine"));
        ProjectProfile.Snapshot snap = ProjectProfile.snapshot(c);
        ProjectProfile.clearProjectScoped(c);
        assertTrue(c.walks.isEmpty(), "a project's walks are that project's: clearing removes them");
        ProjectProfile.restore(snap, c);
        assertEquals(List.of(sample("mine")), c.walks);
    }

    @Test
    @DisplayName("a machine save while a project is active writes the no-project walks, not the project's")
    void aMachineSaveWritesTheGlobalTiersWalks(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(sample("before-projects"));
        ProjectProfile.Snapshot globalTier = ProjectProfile.snapshot(c);
        ProjectProfile.clearProjectScoped(c);
        c.walks.add(sample("project-A"));                 // the active project's walk, held in memory
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        store.save(c, globalTier);
        AppConfig back = store.load();
        assertEquals(List.of("before-projects"), back.walks.stream().map(WalkSpec::name).toList(),
                "project A's walk must not leak into the person's own settings");
    }

    @Test
    @DisplayName("a project profile round-trips its walks through save and load")
    void aProjectProfileCarriesItsWalks(@TempDir Path dir) throws Exception {
        Path profile = dir.resolve(".analyser").resolve("project.fluxtion-settings");
        Files.createDirectories(profile.getParent());
        AppConfig c = new AppConfig();
        c.walks.add(sample("in-profile"));
        ProjectProfile.save(profile, c, new SettingsShare());
        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(profile, back, new SettingsShare()).loaded());
        assertEquals(List.of(sample("in-profile")), back.walks);
    }

    @Test
    @DisplayName("sharing: a walk-only file is REPORTS, and applying it leaves the receiver's reports alone")
    void aWalkOnlyShareLeavesReportsAlone() {
        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.walks.add(sample("shared"));
        String text = share.export(sender, EnumSet.of(SettingsShare.Category.REPORTS));
        AppConfig receiver = new AppConfig();
        ReportSpec theirs = new ReportSpec("their-report", "t", "", "", null, FilterSnapshot.all(), List.of());
        receiver.reports.add(theirs);
        var plan = share.preview(text, receiver);
        assertTrue(plan.present().contains(SettingsShare.Category.REPORTS));
        assertTrue(plan.summary().get(SettingsShare.Category.REPORTS).contains("spotlight walk"),
                "the consent summary names the walks: " + plan.summary());
        share.apply(plan, EnumSet.of(SettingsShare.Category.REPORTS), receiver);
        assertEquals(List.of(theirs), receiver.reports, "a walk-only import must not remove or replace reports");
        assertEquals(List.of(sample("shared")), receiver.walks);
    }

    @Test
    @DisplayName("sharing: a report-only file leaves the receiver's walks alone")
    void aReportOnlyShareLeavesWalksAlone() {
        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.reports.add(new ReportSpec("r", "t", "", "", null, FilterSnapshot.all(), List.of()));
        String text = share.export(sender, EnumSet.of(SettingsShare.Category.REPORTS));
        assertFalse(text.contains("walk."), "no walks, no walk keys");
        AppConfig receiver = new AppConfig();
        receiver.walks.add(sample("kept"));
        share.apply(share.preview(text, receiver), EnumSet.of(SettingsShare.Category.REPORTS), receiver);
        assertEquals(List.of(sample("kept")), receiver.walks);
    }

    @Test
    @DisplayName("the walk bin is machine-local: never exported, never in a profile")
    void theBinNeverLeaves(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(sample("doomed"));
        c.walks.add(sample("kept"));
        assertNotNull(WalkBin.delete(c, "doomed", "2026-09-27T19:00:00Z"));
        String text = new SettingsShare().export(c, EnumSet.allOf(SettingsShare.Category.class));
        assertFalse(text.contains("deletedWalk"), "the bin must never cross the share boundary");
        Path profile = dir.resolve(".analyser").resolve("project.fluxtion-settings");
        Files.createDirectories(profile.getParent());
        ProjectProfile.save(profile, c, new SettingsShare());
        assertFalse(Files.readString(profile).contains("deletedWalk"), "nor sit in a committed profile");
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        store.save(c, null);
        AppConfig back = store.load();
        assertEquals(List.of("doomed"), back.deletedWalks.stream().map(d -> d.walk().name()).toList(),
                "it is the machine's, and survives a restart there");
        assertFalse(KnownKeys.PROFILE_FAMILIES.contains("deletedWalk"));
        assertTrue(KnownKeys.CONFIG_FAMILIES.contains("deletedWalk"));
        assertTrue(KnownKeys.PROFILE_FAMILIES.contains("walk"));
    }

    @Test
    @DisplayName("the bin: delete, restore, rename, and down to an empty bin")
    void deleteRestoreRename() {
        AppConfig c = new AppConfig();
        c.walks.add(sample("a"));
        assertNotNull(WalkBin.delete(c, "a", "now"));
        assertTrue(c.walks.isEmpty());
        assertEquals(List.of("a"), WalkBin.restorable(c));
        assertNull(WalkBin.restore(c, "a"));
        assertTrue(c.deletedWalks.isEmpty(), "down to an empty bin");
        assertNull(WalkBin.rename(c, "a", "b"));
        assertEquals(sample("a").steps(), c.walks.get(0).steps(), "a rename keeps its steps");
        assertEquals("b", c.walks.get(0).name());
        c.walks.add(sample("c"));
        assertNotNull(WalkBin.rename(c, "b", "c"), "a rename onto an existing walk is refused");
        assertNotNull(WalkBin.rename(c, "b", "x:y"), "and onto a name an address cannot carry");
        WalkBin.delete(c, "c", "now");
        c.walks.add(sample("c"));
        assertNotNull(WalkBin.restore(c, "c"), "restoring never replaces a walk of the same name");
    }

    @Test
    @DisplayName("definition digests: a chart's excludes open/closed, and changes with its definition")
    void definitionDigests() {
        GraphSpec open = new GraphSpec("g", List.of("a\u0001x"), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", true);
        GraphSpec closed = new GraphSpec("g", List.of("a\u0001x"), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", false);
        GraphSpec other = new GraphSpec("g", List.of("a\u0001y"), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", true);
        assertEquals(ConfigStore.chartDefinitionDigest(open), ConfigStore.chartDefinitionDigest(closed),
                "closing a chart does not change what it is");
        assertNotEquals(ConfigStore.chartDefinitionDigest(open), ConfigStore.chartDefinitionDigest(other),
                "a different series is a different chart");
        FocusSpec f1 = new FocusSpec("path", "why", List.of("a", "b"));
        FocusSpec f2 = new FocusSpec("path", "why", List.of("a", "c"));
        assertNotEquals(ConfigStore.focusDefinitionDigest(f1), ConfigStore.focusDefinitionDigest(f2),
                "the same name over different nodes is a different focus (R6)");
    }
}
