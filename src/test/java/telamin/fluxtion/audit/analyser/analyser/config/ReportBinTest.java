package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #33 review (owner, 2026-09-27): deleting a report is recoverable. A delete moves the report into a
 * machine-local bin; restore brings it back into the project it came from; the bin never reaches a profile.
 */
class ReportBinTest {

    private static ReportSpec report(String name) {
        return new ReportSpec(name, "Title of " + name, "2026-09-27T00:00:00Z", "notes on " + name, null, null,
                List.of(ReportSpec.SectionSpec.narrative("finding in " + name)));
    }

    private static AppConfig projectConfig(String profile, String... reports) {
        AppConfig c = new AppConfig();
        c.activeProjectPath = profile;
        for (String r : reports) c.reports.add(report(r));
        return c;
    }

    @Test
    @DisplayName("Delete then restore gives the report back unchanged, across a save and reload of the machine settings")
    void deleteThenRestoreRoundTripsThroughPersistence() {
        AppConfig c = projectConfig("/work/a/.analyser/project.fluxtion-settings", "keep", "doomed");
        ReportSpec before = c.reports.get(1);
        assertNotNull(ReportBin.delete(c, "doomed", "2026-09-27T10:00:00Z"), "control: the report existed");
        assertEquals(List.of("keep"), c.reports.stream().map(ReportSpec::name).toList(), "it left the live list");

        Properties p = new Properties();
        ConfigStore.writeDeletedReports(p, c.deletedReports);
        AppConfig reloaded = projectConfig(c.activeProjectPath, "keep");
        ConfigStore.readDeletedReports(p, reloaded.deletedReports);
        assertEquals(List.of("doomed"), ReportBin.restorable(reloaded), "the bin survives a reload");

        assertNull(ReportBin.restore(reloaded, "doomed"), "restore succeeds");
        ReportSpec after = reloaded.reports.stream().filter(r -> r.name().equals("doomed")).findFirst().orElseThrow();
        assertEquals(before, after, "the restored report is exactly the one deleted");
        assertTrue(ReportBin.restorable(reloaded).isEmpty(), "and it is no longer in the bin");
    }

    @Test
    @DisplayName("A report is restorable only into the project it was deleted from")
    void restoreIsScopedToTheProject() {
        AppConfig c = projectConfig("/work/a/.analyser/project.fluxtion-settings", "doomed");
        ReportBin.delete(c, "doomed", "now");
        c.activeProjectPath = "/work/b/.analyser/project.fluxtion-settings";
        assertTrue(ReportBin.restorable(c).isEmpty(), "another project does not see it");
        assertNotNull(ReportBin.restore(c, "doomed"), "and cannot restore it");
        assertTrue(c.reports.isEmpty(), "nothing was added to the other project");
        c.activeProjectPath = "/work/a/.analyser/project.fluxtion-settings";
        assertEquals(List.of("doomed"), ReportBin.restorable(c), "back in its own project, it is there");
    }

    @Test
    @DisplayName("The bin keeps the newest " + ReportBin.CAPACITY + ", dropping the oldest")
    void theBinIsBounded() {
        AppConfig c = projectConfig("/p");
        for (int i = 0; i < ReportBin.CAPACITY + 3; i++) {
            c.reports.add(report("r" + i));
            ReportBin.delete(c, "r" + i, "t" + i);
        }
        assertEquals(ReportBin.CAPACITY, c.deletedReports.size());
        assertEquals("r" + (ReportBin.CAPACITY + 2), ReportBin.restorable(c).get(0), "newest first");
        assertFalse(ReportBin.restorable(c).contains("r0"), "the oldest went first");
    }

    @Test
    @DisplayName("Restoring onto a name that is now taken is refused, and nothing changes")
    void restoreOntoATakenNameIsRefused() {
        AppConfig c = projectConfig("/p", "same");
        ReportBin.delete(c, "same", "now");
        c.reports.add(report("same"));                        // a new report took the name meanwhile
        String refused = ReportBin.restore(c, "same");
        assertNotNull(refused);
        assertTrue(refused.contains("already exists"), refused);
        assertEquals(1, c.reports.size(), "the live report is not replaced");
        assertEquals(List.of("same"), ReportBin.restorable(c), "the deleted one is still restorable");
    }

    @Test
    @DisplayName("A delete never writes the bin into the project profile, which is committed and shared")
    void theBinNeverReachesAProfile(@TempDir Path tmp) throws Exception {
        Path profile = tmp.resolve(".analyser/project.fluxtion-settings");
        Files.createDirectories(profile.getParent());
        AppConfig c = projectConfig(profile.toString(), "keep", "doomed");
        ReportBin.delete(c, "doomed", "now");
        ProjectProfile.save(profile, c, new SettingsShare("/home/tester"));
        String written = Files.readString(profile);
        assertFalse(written.contains("deletedReport"), "no bin key in the profile:\n" + written);
        assertFalse(written.contains("doomed"), "and not the deleted report under any key:\n" + written);
    }

    @Test
    @DisplayName("The machine settings' own save and load keep the bin, while a project is active")
    void theMachineSettingsSaveKeepsTheBin(@TempDir Path tmp) {
        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        AppConfig c = projectConfig("/work/a/.analyser/project.fluxtion-settings", "doomed");
        ReportBin.delete(c, "doomed", "2026-09-27T10:00:00Z");
        store.save(c);
        AppConfig reloaded = store.load();
        reloaded.activeProjectPath = c.activeProjectPath;
        assertEquals(List.of("doomed"), ReportBin.restorable(reloaded), "a restart does not empty the bin");
    }

    @Test
    void theGlobalTierSaveKeepsTheBinAndAnEmptySaveClearsItsKeys(@TempDir Path tmp) {
        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        AppConfig c = projectConfig("/work/demo/project.fluxtion-settings", "doomed");
        var global = ProjectProfile.snapshot(new AppConfig());
        ReportBin.delete(c, "doomed", "now");
        store.save(c, global);
        AppConfig reloaded = store.load();
        reloaded.activeProjectPath = c.activeProjectPath;
        assertEquals(List.of("doomed"), ReportBin.restorable(reloaded),
                "the active project's save must keep the bin despite restoring the global tier");
        assertNull(ReportBin.restore(reloaded, "doomed"), "the saved report can be restored");
        store.save(reloaded, global);
        assertTrue(store.load().deletedReports.isEmpty(), "owned deletedReport keys must not resurrect an emptied bin");
    }

    @Test
    void everyShareCategoryStillExcludesTheBinAndImportIgnoresIt() {
        AppConfig c = projectConfig("/work/demo/project.fluxtion-settings", "doomed");
        ReportBin.delete(c, "doomed", "now");
        var all = java.util.EnumSet.allOf(SettingsShare.Category.class);
        SettingsShare share = new SettingsShare("/home/demo");
        String text = share.export(c, all);
        assertFalse(text.contains("deletedReport"), "an all-category export must contain no bin keys");
        assertFalse(text.contains("doomed"), "an all-category export must contain no deleted report content");
        AppConfig imported = new AppConfig();
        share.apply(share.preview(text + "\ndeletedReport.count=1\ndeletedReport.0.name=injected\n", imported), all, imported);
        assertTrue(imported.deletedReports.isEmpty(), "an import must not populate the machine's bin");
    }
}
