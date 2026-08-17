package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M20.2 — switching, auto-persist and the "no project" restore, all headless. */
class ProjectSessionTest {

    private final SettingsShare share = new SettingsShare("/home/tester");
    private int scheduled;

    private ProjectSession session(AppConfig c) {
        return new ProjectSession(c, share, () -> scheduled++);
    }

    private static AppConfig configWith(String root) {
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.add(root);
        return c;
    }

    // ---- auto-persist ---------------------------------------------------------------------------

    /**
     * The property the debounce exists for: a burst of edits is ONE write. A profile is often a
     * committed file, and fifteen writes is fifteen chances for a reviewer to stop reading the diff.
     */
    @Test
    void aBurstOfEditsIsOneWrite(@TempDir Path dir) throws Exception {
        AppConfig c = configWith("/work/src");
        ProjectSession s = session(c);
        s.create(ProjectProfile.pathFor(dir));
        int afterCreate = s.writeCount();

        for (int i = 0; i < 15; i++) {
            c.sourceRoots.add("/work/extra" + i);
            s.requestSave();
        }
        assertTrue(s.isDirty());
        assertEquals(afterCreate, s.writeCount(), "nothing is written until the timer fires");

        s.flush();
        assertEquals(afterCreate + 1, s.writeCount(), "fifteen edits, one write");
        assertFalse(s.isDirty());

        s.flush();
        assertEquals(afterCreate + 1, s.writeCount(), "flushing when clean writes nothing");
    }

    /** Edits arriving from a verb are edits. The funnel is shared, so this must not need special care. */
    @Test
    void requestSaveIsInertWithNoProjectOpen() {
        AppConfig c = configWith("/work/src");
        ProjectSession s = session(c);
        s.requestSave();
        assertFalse(s.isDirty(), "with no project the global config is already the right home");
        assertEquals(0, s.writeCount());
    }

    /** A debounce window is exactly when leaving a project would drop its last edits. */
    @Test
    void switchingFlushesPendingEditsToTheProjectBeingLeft(@TempDir Path dir) throws Exception {
        Path a = ProjectProfile.pathFor(dir.resolve("a"));
        Path b = ProjectProfile.pathFor(dir.resolve("b"));

        AppConfig c = configWith("/a/src");
        ProjectSession s = session(c);
        s.create(a);
        c.sourceRoots.add("/a/late-edit");
        s.requestSave();                       // pending, not yet written

        ProjectProfile.save(b, configWith("/b/src"), share);
        s.open(b);

        AppConfig reread = new AppConfig();
        ProjectProfile.load(a, reread, share);
        assertTrue(reread.sourceRoots.contains("/a/late-edit"),
                "leaving a project must not discard the edit you made a moment before");
    }

    @Test
    void closingFlushesToo(@TempDir Path dir) throws Exception {
        Path a = ProjectProfile.pathFor(dir);
        AppConfig c = configWith("/a/src");
        ProjectSession s = session(c);
        s.create(a);
        c.sourceRoots.add("/a/late");
        s.requestSave();
        s.close();

        AppConfig reread = new AppConfig();
        ProjectProfile.load(a, reread, share);
        assertTrue(reread.sourceRoots.contains("/a/late"));
    }

    /** A read-only checkout must cost you the file, not the session. */
    @Test
    void aFailedWriteKeepsTheAppUsableAndReportsOnce(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        AppConfig c = configWith("/work/src");
        ProjectSession s = session(c);
        s.create(file);

        // remove the file first, THEN make the directory unwritable, so the write has to create and
        // cannot. On a filesystem that ignores the permission bit the assertions below are skipped.
        Path parent = file.getParent();
        Files.deleteIfExists(file);
        boolean enforced = parent.toFile().setWritable(false);
        c.sourceRoots.add("/work/more");
        s.requestSave();
        s.flush();
        parent.toFile().setWritable(true);

        if (enforced) {
            String err = s.takeError();
            assertNotNull(err, "a write failure must be reportable");
            assertNull(s.takeError(), "taking the error clears it, so it is reported once not forever");
        }
        assertTrue(c.sourceRoots.contains("/work/more"), "the edit survives in memory regardless");
    }

    /**
     * Found by driving the running app, not by a unit test: one AppConfig holds both tiers in memory,
     * so saving it wholesale wrote the open project's source roots into the GLOBAL file. Delete that
     * project directory afterwards and the user is left with a stale project's settings as their own,
     * with their pre-project configuration gone — the exact thing the spec promises survives.
     */
    @Test
    void anOpenProjectDoesNotLeakItsSettingsIntoTheGlobalFile(@TempDir Path dir) throws Exception {
        Path profile = ProjectProfile.pathFor(dir.resolve("proj"));
        ProjectProfile.save(profile, configWith("/project/only"), share);

        AppConfig c = configWith("/my/own/root");
        ProjectSession s = session(c);
        s.open(profile);
        assertEquals(List.of("/project/only"), c.sourceRoots, "the project is what is live");

        Path cfg = dir.resolve("config");
        new ConfigStore(cfg).save(c, s.globalTier());

        AppConfig reloaded = new ConfigStore(cfg).load();
        assertEquals(List.of("/my/own/root"), reloaded.sourceRoots,
                "the global file must still hold the user's own roots, not the open project's");
        assertEquals(profile.toString(), reloaded.activeProjectPath,
                "but it must remember which project was open");
    }

    @Test
    void withNoProjectOpenTheGlobalFileIsSavedAsBefore(@TempDir Path dir) {
        AppConfig c = configWith("/my/own/root");
        ProjectSession s = session(c);
        assertNull(s.globalTier(), "no project means: save exactly what is live, as the app always has");

        Path cfg = dir.resolve("config");
        new ConfigStore(cfg).save(c, s.globalTier());
        assertEquals(List.of("/my/own/root"), new ConfigStore(cfg).load().sourceRoots);
    }

    // ---- startup -------------------------------------------------------------------------------

    /**
     * The ordering bug this test exists for, found by driving the running app: if the profile is applied
     * before the session is built, there is nothing left to snapshot and the global file is later
     * overwritten with the project's settings.
     */
    @Test
    void startupSnapshotsTheUsersOwnSettingsBeforeApplyingTheProject(@TempDir Path dir) throws Exception {
        Path profile = ProjectProfile.pathFor(dir.resolve("proj"));
        ProjectProfile.save(profile, configWith("/project/only"), share);

        AppConfig c = configWith("/my/own/root");
        c.activeProjectPath = profile.toString();

        ProjectSession s = session(c);
        assertTrue(s.activateOnStartup().loaded());
        assertEquals(List.of("/project/only"), c.sourceRoots, "the project is live");

        Path cfg = dir.resolve("config");
        new ConfigStore(cfg).save(c, s.globalTier());
        assertEquals(List.of("/my/own/root"), new ConfigStore(cfg).load().sourceRoots,
                "the user's own roots must survive a restart into a project");

        s.close();
        assertEquals(List.of("/my/own/root"), c.sourceRoots, "and closing restores them live");
    }

    @Test
    void startupWithAMovedProjectClearsThePointerAndCarriesOn(@TempDir Path dir) {
        AppConfig c = configWith("/my/own/root");
        c.activeProjectPath = dir.resolve("moved/project.fluxtion-settings").toString();

        ProjectSession s = session(c);
        ProjectProfile.LoadResult r = s.activateOnStartup();

        assertFalse(r.loaded());
        assertTrue(r.message().contains("continuing without a project"), r.message());
        assertEquals("", c.activeProjectPath, "a stale pointer must not re-report on every launch");
        assertFalse(s.hasProject());
        assertEquals(List.of("/my/own/root"), c.sourceRoots,
                "the app opens exactly as it did before projects existed");
    }

    @Test
    void noActiveProjectIsSilentAtStartup() {
        assertNull(session(new AppConfig()).activateOnStartup());
    }

    // ---- switching ------------------------------------------------------------------------------

    @Test
    void openingRecordsTheActivePathAndRecentList(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir.resolve("myproject"));
        ProjectProfile.save(file, configWith("/p/src"), share);

        AppConfig c = new AppConfig();
        ProjectSession s = session(c);
        assertTrue(s.open(file).loaded());

        assertTrue(s.hasProject());
        assertEquals(file.toString(), c.activeProjectPath);
        assertEquals(List.of(file.toString()), c.recentProjects);
        assertEquals("myproject", s.activeName(), "the project is named by its directory, not the file");
    }

    /** The backward-compatibility promise: leave a project and the app is exactly what it was. */
    @Test
    void closingRestoresTheSettingsInPlaceBeforeAnyProjectWasOpened(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        ProjectProfile.save(file, configWith("/project/src"), share);

        AppConfig c = configWith("/my/longstanding/root");
        ProjectSession s = session(c);

        s.open(file);
        assertEquals(List.of("/project/src"), c.sourceRoots);

        s.close();
        assertEquals(List.of("/my/longstanding/root"), c.sourceRoots,
                "a year of configuration must not be consumed by opening one project");
        assertFalse(s.hasProject());
        assertEquals("", c.activeProjectPath);
    }

    /** New means new. Otherwise "New project" is a slow way to copy the one you had. */
    @Test
    void aNewProjectStartsEmptyRatherThanInheritingWhatWasOpen(@TempDir Path dir) throws Exception {
        AppConfig c = configWith("/inherited/src");
        c.savedGraphs.add(new GraphSpec("carried", List.of("a.b"), List.of(),
                null, null, null, null, List.of(), List.of()));
        ProjectSession s = session(c);

        s.create(ProjectProfile.pathFor(dir.resolve("fresh")));

        assertTrue(c.sourceRoots.isEmpty(), "a new project does not inherit the last one's roots");
        assertTrue(c.savedGraphs.isEmpty());
        assertFalse(c.mavenRepos.isEmpty(), "but it is usable — the default Maven repo is seeded");
    }

    @Test
    void saveAsForksToANewPathAndMakesItActive(@TempDir Path dir) throws Exception {
        Path a = ProjectProfile.pathFor(dir.resolve("a"));
        Path b = ProjectProfile.pathFor(dir.resolve("b"));
        AppConfig c = configWith("/shared/src");
        ProjectSession s = session(c);
        s.create(a);
        c.sourceRoots.add("/shared/extra");
        s.saveAs(b);

        assertEquals(b, s.activeFile());
        AppConfig reread = new AppConfig();
        ProjectProfile.load(b, reread, share);
        assertTrue(reread.sourceRoots.contains("/shared/extra"));
    }

    /** A pointer to a project that has moved must not leave the session half-switched. */
    @Test
    void openingAMissingProjectChangesNothing(@TempDir Path dir) throws Exception {
        Path good = ProjectProfile.pathFor(dir.resolve("good"));
        ProjectProfile.save(good, configWith("/good/src"), share);

        AppConfig c = new AppConfig();
        ProjectSession s = session(c);
        s.open(good);

        ProjectProfile.LoadResult r = s.open(dir.resolve("gone/project.fluxtion-settings"));
        assertFalse(r.loaded());
        assertEquals(good, s.activeFile(), "a failed open leaves the working project in place");
        assertEquals(List.of("/good/src"), c.sourceRoots);
    }

    @Test
    void aSessionResumesTheActiveProjectRecordedInGlobalConfig(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        AppConfig c = new AppConfig();
        c.activeProjectPath = file.toString();

        ProjectSession s = session(c);
        assertTrue(s.hasProject(), "restart must resume the project, not forget it");
        assertEquals(file, s.activeFile());
    }
}
