package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M20.1 — the config tier, and switching between projects.
 *
 * <p>The acceptance criteria in {@code spec-project-profiles.md} are written as a two-project story, so
 * they are tested as one: open A, work, switch to B, and assert that A's settings are <b>gone</b> rather
 * than underneath B's. That distinction — replace, not merge — is the entire milestone, and it is the
 * one an additive implementation would still pass a shallower test on.
 */
class ProjectProfileTest {

    private final SettingsShare share = new SettingsShare("/home/tester");

    private static AppConfig configWith(String root, String ep, String graphName) {
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.add(root);
        c.eventProcessorFqns.clear();
        c.eventProcessorFqns.add(ep);
        c.selectedEventProcessor = ep;
        c.savedGraphs.clear();
        c.savedGraphs.add(new GraphSpec(graphName, List.of("node.value"), List.of(),
                null, null, null, null, List.of(), List.of()));
        return c;
    }

    // ---- the tier -------------------------------------------------------------------------------

    /**
     * The brief calls the project tier "the M15 shareable whitelist". That shorthand is one category
     * too broad in two places, and this pins the difference: assistant caps and LLM provider are
     * shareable with a colleague but are not facts about a project.
     *
     * <p>Was five; REPORTS joined in M33.4 — an investigation is a fact about a project if anything
     * is, and D-I4 gives it its own category (own consent) rather than a seat inside GRAPHS. The
     * pin's point is unchanged: machine settings stay out.
     */
    @Test
    void theProjectTierIsEightCategoriesNotTheWholeShareableWhitelist() {
        assertEquals(8,   // M38.1 added RUNBOOKS, M38.2 VOCABULARY (tier-1 context, project-scoped by design)
                 ProjectProfile.PROJECT_SCOPED.size());
        assertTrue(ProjectProfile.PROJECT_SCOPED.containsAll(List.of(
                SettingsShare.Category.SOURCE_ROOTS, SettingsShare.Category.MAVEN_REPOS,
                SettingsShare.Category.EVENT_PROCESSORS, SettingsShare.Category.GRAPHS,
                SettingsShare.Category.REPORTS, SettingsShare.Category.VIEW)));
        assertFalse(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.ASSISTANT),
                "assistant caps are machine settings — the spec's tier table lists them as global");
        assertFalse(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.LLM),
                "LLM provider/model is a machine setting; the API key can never be in a profile at all");
    }

    /** The guarantee a team commits a profile on. Not filtered twice — the key is not in the tier. */
    @Test
    void aWrittenProfileCannotContainTheApiKey(@TempDir Path dir) throws Exception {
        AppConfig c = configWith("/work/a/src", "com.acme.A", "throughput");
        c.apiKey = "sk-do-not-leak-this";
        c.awsProfile = "prod";
        c.theme = "Dark";

        Path file = ProjectProfile.pathFor(dir);
        ProjectProfile.save(file, c, share);
        String text = Files.readString(file);

        assertFalse(text.contains("sk-do-not-leak-this"), "the API key must never reach a project file");
        assertFalse(text.contains("apiKey"));
        assertFalse(text.contains("prod"), "AWS profile is global");
        assertFalse(text.contains("Dark"), "theme is global");
        assertTrue(text.contains("com.acme.A"), "the project's own settings must be there");
    }

    // ---- switching ------------------------------------------------------------------------------

    /** The acceptance story: B's settings replace A's rather than piling on top of them. */
    // ---- M35.10: what "relative" is relative to ---------------------------------------------------

    @Test
    void aCanonicalProfilesRelativeRootsAnchorAtTheProjectRoot_notAtDotAnalyser(@TempDir Path dir)
            throws Exception {
        // the M19 bundle contract: sourceRoot.0=src/main/java lands at <bundle>/src/main/java. Before
        // M35.10 load() handed the importer the file's OWN directory, so it landed at
        // <bundle>/.analyser/src/main/java — a directory that does not exist.
        Path project = dir.resolve("bundle");
        Path file = ProjectProfile.pathFor(project);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "share.version=1\nsourceRoot.count=2\nsourceRoot.0=src/main/java\n"
                + "sourceRoot.1=/opt/abs/src\nmavenRepo.count=1\nmavenRepo.0=libs\n");
        AppConfig config = new AppConfig();

        assertTrue(ProjectProfile.load(file, config, new SettingsShare()).loaded());

        assertEquals(List.of(project.resolve("src/main/java").toString(), "/opt/abs/src"), config.sourceRoots,
                "project-relative, and the absolute one untouched");
        assertEquals(List.of(project.resolve("libs").toString()), config.mavenRepos);
    }

    @Test
    void aLooseSettingsFileStaysRelativeToItsOwnDirectory(@TempDir Path dir) throws Exception {
        // an exported file imported from wherever it was saved has no project root to speak of; its own
        // directory is the only anchor it has — the pre-M35.10 behaviour, kept for exactly that case
        Path file = dir.resolve("team.fluxtion-settings");
        Files.writeString(file, "share.version=1\nsourceRoot.count=1\nsourceRoot.0=src\n");
        AppConfig config = new AppConfig();

        assertTrue(ProjectProfile.load(file, config, new SettingsShare()).loaded());

        assertEquals(List.of(dir.resolve("src").toString()), config.sourceRoots);
    }

    @Test
    void baseDirForDistinguishesTheCanonicalProfileFromALookalike(@TempDir Path dir) {
        Path project = dir.resolve("p");
        assertEquals(project.toAbsolutePath().normalize(),
                ProjectProfile.baseDirFor(ProjectProfile.pathFor(project)));
        // a differently named file inside .analyser/ is not the profile — its own directory, as for any
        // loose file; the rule is about the canonical path, not the folder name alone
        assertEquals(project.resolve(".analyser").toAbsolutePath().normalize(),
                ProjectProfile.baseDirFor(project.resolve(".analyser/other.fluxtion-settings")));
        assertEquals(dir.toAbsolutePath().normalize(),
                ProjectProfile.baseDirFor(dir.resolve("loose.fluxtion-settings")));
        assertNull(ProjectProfile.baseDirFor(null));
    }

    // ---- M35.11: a committed profile round-trips byte-for-byte ------------------------------------

    @Test
    void pathsUnderTheProjectAreWrittenProjectRelative_andReadBackWhereTheyWere(@TempDir Path dir)
            throws Exception {
        Path project = dir.resolve("home/work/proj");
        Path file = ProjectProfile.pathFor(project);
        // the project sits INSIDE the home directory: project-relative must win over ~-relative, or a
        // teammate whose clone lives elsewhere reads ~/work/proj/src and finds nothing
        SettingsShare share = new SettingsShare(dir.resolve("home").toString());
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.addAll(List.of(project.resolve("src/main/java").toString(),
                dir.resolve("home/lib").toString(), "/opt/abs/src"));

        assertTrue(ProjectProfile.save(file, c, share));
        String text = Files.readString(file);

        assertTrue(text.contains("sourceRoot.0=src/main/java"), text);
        assertTrue(text.contains("sourceRoot.1=~/lib"), "outside the project, under home: still ~-relative");
        assertTrue(text.contains("sourceRoot.2=/opt/abs/src"), "outside both: verbatim");
        assertFalse(text.contains("exportedAt"), "a timestamp is a diff on every write");
        assertFalse(text.lines().anyMatch(l -> l.matches("#[A-Z][a-z]{2} [A-Z][a-z]{2} \\d{2} .*\\d{4}")),
                "and so is Properties.store's date comment: " + text);

        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(file, back, share).loaded());
        assertEquals(c.sourceRoots, back.sourceRoots, "what was written is what is read, on this machine");
    }

    @Test
    void writingWhatTheFileAlreadySaysIsNotAWrite(@TempDir Path dir) throws Exception {
        Path project = dir.resolve("proj");
        Path file = ProjectProfile.pathFor(project);
        SettingsShare share = new SettingsShare();
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.add(project.resolve("src").toString());

        assertTrue(ProjectProfile.save(file, c, share), "first write happens");
        String first = Files.readString(file);
        var mtime = Files.getLastModifiedTime(file);

        // the O2 sequence: load it, change nothing, save it back
        AppConfig again = new AppConfig();
        assertTrue(ProjectProfile.load(file, again, share).loaded());
        assertFalse(ProjectProfile.save(file, again, share), "identical content: no write");
        assertEquals(first, Files.readString(file));
        assertEquals(mtime, Files.getLastModifiedTime(file), "and the file was not even touched");
    }

    @Test
    void aShareExportIsUnchanged_itStillCarriesItsProvenance() {
        // the committed-profile rules are for committed profiles; a one-off share keeps its timestamp
        String text = new SettingsShare("/home/tester").export(new AppConfig(), ProjectProfile.PROJECT_SCOPED);
        assertTrue(text.contains("share.exportedAt="), text);
    }

    @Test
    void switchingProjectsReplacesRatherThanMerges(@TempDir Path dir) throws Exception {
        Path aFile = ProjectProfile.pathFor(dir.resolve("projectA"));
        Path bFile = ProjectProfile.pathFor(dir.resolve("projectB"));
        ProjectProfile.save(aFile, configWith("/work/a/src", "com.acme.A", "graphA"), share);
        ProjectProfile.save(bFile, configWith("/work/b/src", "com.acme.B", "graphB"), share);

        AppConfig live = new AppConfig();
        assertTrue(ProjectProfile.load(aFile, live, share).loaded());
        assertEquals(List.of("/work/a/src"), live.sourceRoots);
        assertEquals("com.acme.A", live.selectedEventProcessor);

        assertTrue(ProjectProfile.load(bFile, live, share).loaded());
        assertEquals(List.of("/work/b/src"), live.sourceRoots,
                "A's root must be GONE, not listed underneath B's — that pile-up is what M20 removes");
        assertEquals("com.acme.B", live.selectedEventProcessor,
                "a stale selected processor names a class that need not exist in the new project");
        assertEquals(List.of("graphB"), live.savedGraphs.stream().map(GraphSpec::name).toList());
    }

    /** Global settings are a different tier and switching must not touch them. */
    @Test
    void switchingLeavesGlobalSettingsAlone(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        ProjectProfile.save(file, configWith("/work/a/src", "com.acme.A", "g"), share);

        AppConfig live = new AppConfig();
        live.apiKey = "sk-mine";
        live.theme = "Darcula";
        live.assistantExportDir = "/exports";
        live.recentFiles.add("/logs/yesterday.yaml");

        ProjectProfile.load(file, live, share);

        assertEquals("sk-mine", live.apiKey);
        assertEquals("Darcula", live.theme);
        assertEquals("/exports", live.assistantExportDir);
        assertEquals(List.of("/logs/yesterday.yaml"), live.recentFiles);
    }

    /**
     * Opening a project must not consume what the user had before projects existed — those values are
     * the "no project" defaults and switching back restores them.
     */
    @Test
    void switchingBackToNoProjectRestoresWhatWasThereBefore(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir);
        ProjectProfile.save(file, configWith("/work/a/src", "com.acme.A", "g"), share);

        AppConfig live = configWith("/before/src", "com.acme.Before", "beforeGraph");
        ProjectProfile.Snapshot before = ProjectProfile.snapshot(live);

        ProjectProfile.load(file, live, share);
        assertEquals(List.of("/work/a/src"), live.sourceRoots);

        ProjectProfile.restore(before, live);
        assertEquals(List.of("/before/src"), live.sourceRoots);
        assertEquals("com.acme.Before", live.selectedEventProcessor);
        assertEquals(List.of("beforeGraph"), live.savedGraphs.stream().map(GraphSpec::name).toList());
    }

    // ---- degrading, never failing ---------------------------------------------------------------

    /** A moved repository must degrade to global-only with a reason, never stop the app starting. */
    @Test
    void aMissingProfileIsReportedNotThrown(@TempDir Path dir) {
        AppConfig live = configWith("/keep/me", "com.acme.Keep", "keep");
        ProjectProfile.LoadResult r = ProjectProfile.load(dir.resolve("gone/project.fluxtion-settings"),
                live, share);

        assertFalse(r.loaded());
        assertTrue(r.message().contains("not found"), r.message());
        assertEquals(List.of("/keep/me"), live.sourceRoots,
                "a failed load must not have half-cleared the working set");
    }

    @Test
    void anUnreadableProfileIsReportedNotThrown(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("project.fluxtion-settings");
        Files.writeString(file, "share.version=999\nsourceRoot.count=1\nsourceRoot.0=/x\n");  // future format
        AppConfig live = new AppConfig();
        ProjectProfile.LoadResult r = ProjectProfile.load(file, live, share);
        assertFalse(r.loaded(), "a version this build cannot read must be refused, not guessed at");
        assertNotNull(r.message());
    }

    /**
     * An empty Maven-repo list disables source lookup for every dependency, silently. A profile that
     * does not mention repos means "I did not say", so the default is seeded rather than lost.
     */
    @Test
    void aProfileWithoutMavenReposKeepsTheDefaultRatherThanDisablingLookup(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("project.fluxtion-settings");
        Files.writeString(file, "share.version=1\nsourceRoot.count=1\nsourceRoot.0=/work/src\n");

        AppConfig live = new AppConfig();
        assertTrue(ProjectProfile.load(file, live, share).loaded());
        assertEquals(List.of("/work/src"), live.sourceRoots);
        assertFalse(live.mavenRepos.isEmpty(),
                "an empty repo list is a silently broken source lookup, not a configured preference");
    }

    // ---- discovery ------------------------------------------------------------------------------

    /** The M19 zero-setup hook: a log deep inside a repo still finds the profile at its root. */
    @Test
    void findNearWalksUpwardsFromALogToTheProjectRoot(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("repo");
        Path profile = ProjectProfile.pathFor(root);
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "share.version=1\n");
        Path log = root.resolve("build/logs/run/audit.yaml");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "eventLogRecord:\n");

        assertEquals(profile, ProjectProfile.findNear(log));
        assertNull(ProjectProfile.findNear(dir.resolve("elsewhere/audit.yaml")));
        assertNull(ProjectProfile.findNear(null));
    }

    @Test
    void recentProjectsAreMostRecentFirstDedupedAndCapped() {
        List<String> recents = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) {
            ProjectProfile.addRecent(recents, "/p/" + i);
        }
        assertEquals(10, recents.size());
        assertEquals("/p/13", recents.get(0));

        ProjectProfile.addRecent(recents, "/p/9");
        assertEquals("/p/9", recents.get(0), "re-opening a project moves it to the front");
        assertEquals(10, recents.size());
        assertEquals(10, java.util.Set.copyOf(recents).size(), "no duplicates");
    }
}
