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
    void theProjectTierIsElevenCategoriesNotTheWholeShareableWhitelist() {
        assertEquals(11,   // M38.1 RUNBOOKS, .2 VOCABULARY, .3 ENVIRONMENTS, .4 ANALYSES, .5 DESTINATIONS (project context by design)
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

    @Test
    void skillsSourceIsRefusedButSanitisedProvenanceIsPreservedAndReadable(@TempDir Path dir)
            throws Exception {
        Path file = ProjectProfile.pathFor(dir.resolve("bundle"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "share.version=1\nskills.source=https://instructions.invalid/override\n"
                + "skills.provenance=canonical@rev-42\n");

        ProjectProfile.LoadResult loaded = ProjectProfile.load(file, new AppConfig(), share);
        assertTrue(loaded.loaded());
        assertTrue(loaded.message().contains("skills.source REFUSED"), loaded.message());
        assertEquals("canonical@rev-42", ProjectProfile.skillsProvenance(file).orElseThrow());

        ProjectProfile.save(file, new AppConfig(), share);
        String saved = Files.readString(file);
        assertFalse(saved.contains("skills.source"), "a forbidden project retrieval control is not carried over");
        assertTrue(saved.contains("skills.provenance=canonical@rev-42"), saved);
    }

    @Test
    void credentialCapableOrMalformedSkillProvenanceIsNeverEchoed(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profile.fluxtion-settings");
        for (String unsafe : List.of(
                "mirror:https://user:password@example.invalid/skills@rev",
                "mirror:https://example.invalid/skills?token=x@rev",
                "mirror:https://example.invalid/skills#fragment@rev",
                "file:/Users/someone/private@rev",
                "canonical@bad/revision")) {
            Files.writeString(file, "skills.provenance=" + unsafe + "\n");
            assertTrue(ProjectProfile.skillsProvenance(file).isEmpty(), unsafe);
        }
        Files.writeString(file, "skills.provenance=mirror:https://mirror.example/fluxtion/skills@abc_123\n");
        assertTrue(ProjectProfile.skillsProvenance(file).isPresent());
        Files.writeString(file, "skills.provenance=none\n");
        assertEquals("none", ProjectProfile.skillsProvenance(file).orElseThrow());
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

    /**
     * M19 bundle contract v3 — pin the generator-facing ABI through the real importer. The v2 table
     * accidentally described one-based list members and a singular eventProcessorFqn property; both
     * are ignored by ConfigStore, so a profile can look plausible while onboarding nothing.
     */
    @Test
    void generatedBundleProfileUsesTheRealZeroBasedConfigStoreFamilies(@TempDir Path dir) throws Exception {
        Path project = dir.resolve("demo-bundle");
        Path file = ProjectProfile.pathFor(project);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                share.version=1
                sourceRoot.count=1
                sourceRoot.0=src/main/java
                eventProcessorFqn.count=1
                eventProcessorFqn.0=com.acme.demo.generated.DemoProcessor
                selectedEventProcessor=com.acme.demo.generated.DemoProcessor
                runbook.count=2
                runbook.0.name=load-audit-log
                runbook.0.path=.claude/skills/load-audit-log/SKILL.md
                runbook.0.description=Open this bundle's exported audit log.
                runbook.1.name=run-mongoose-server
                runbook.1.path=.claude/skills/run-mongoose-server/SKILL.md
                runbook.1.description=Run, export and stop this bundle's server.
                skills.provenance=canonical@rev-42
                """);

        AppConfig config = new AppConfig();
        ProjectProfile.LoadResult loaded = ProjectProfile.load(file, config, new SettingsShare());

        assertTrue(loaded.loaded(), loaded.message());
        assertEquals(List.of(project.resolve("src/main/java").toString()), config.sourceRoots);
        assertEquals(List.of("com.acme.demo.generated.DemoProcessor"), config.eventProcessorFqns);
        assertEquals("com.acme.demo.generated.DemoProcessor", config.selectedEventProcessor);
        assertEquals(List.of("load-audit-log", "run-mongoose-server"),
                config.runbooks.keySet().stream().toList());
        assertEquals("Open this bundle's exported audit log.",
                config.runbooks.get("load-audit-log").description());
        assertEquals("canonical@rev-42", ProjectProfile.skillsProvenance(file).orElseThrow());
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

    /**
     * A NAMED profile — {@code project.<name>.fluxtion-settings} beside the canonical one — is the same
     * project's file and anchors to the same project root. Matching on the canonical file name alone
     * anchored it at {@code .analyser/}, one directory too deep, so every relative path in the profile
     * resolved wrong: {@code src/main/java} became {@code <project>/.analyser/src/main/java} and
     * {@code ../sibling} became {@code <project>/sibling}. Symptom in the app: that profile reported
     * every event processor "source not found" and every runbook {@code exists:false}, while the
     * canonical profile beside it — same roots, same values — resolved fine.
     */
    @Test
    void baseDirForAnchorsNamedProfilesToTheProjectRoot(@TempDir Path dir) {
        Path project = dir.resolve("p");
        Path root = project.toAbsolutePath().normalize();

        assertEquals(root, ProjectProfile.baseDirFor(project.resolve(".analyser/project.ws-feed.fluxtion-settings")));
        assertEquals(root, ProjectProfile.baseDirFor(project.resolve(".analyser/project.reciprocal.fluxtion-settings")));
        // the canonical form keeps working
        assertEquals(root, ProjectProfile.baseDirFor(ProjectProfile.pathFor(project)));

        // and the name rule itself
        assertTrue(ProjectProfile.isProjectProfileFileName("project.fluxtion-settings"));
        assertTrue(ProjectProfile.isProjectProfileFileName("project.ws-feed.fluxtion-settings"));
        assertFalse(ProjectProfile.isProjectProfileFileName("other.fluxtion-settings"));
        assertFalse(ProjectProfile.isProjectProfileFileName("projectX.fluxtion-settings"));
        assertFalse(ProjectProfile.isProjectProfileFileName("project.fluxtion-settings.bak"));
        assertFalse(ProjectProfile.isProjectProfileFileName(null));
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
    // ---- edit-loop spec §E: the profile's creation nonce ------------------------------------------

    /**
     * PR #28 review findings 1–2: the recovery identity is a creation NONCE written into the profile, not a
     * file-system property (an inode is reused on Linux; NTFS tunnels creation times on Windows). This pins
     * the chosen lifecycle: minted when the file is created, kept by every save, never carried by a share
     * export, ignored by an import, and a known family so neither load nor save warns about it.
     */
    @Test
    void theCreationNonceIsMintedOnceKeptBySavesAndNeverShared(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir.resolve("p"));
        AppConfig c = configWith("/src/a", "com.acme.A", "g");
        assertTrue(ProjectProfile.save(file, c, share));
        String nonce = ProjectProfile.nonce(file).orElse(null);
        assertNotNull(nonce, "creating a profile mints its nonce");

        c.sourceRoots.add("/src/b");
        assertTrue(ProjectProfile.save(file, c, share), "a real change is written");
        assertEquals(nonce, ProjectProfile.nonce(file).orElse(null), "a save keeps the profile's creation nonce");
        String bytes = Files.readString(file);
        assertFalse(ProjectProfile.save(file, c, share), "a no-op save still writes nothing (M35.11)");
        assertEquals(bytes, Files.readString(file));

        AppConfig loaded = new AppConfig();
        ProjectProfile.LoadResult result = ProjectProfile.load(file, loaded, share);
        assertTrue(result.loaded(), result.message());
        assertEquals(nonce, result.nonce(), "loading reports the profile's nonce");
        assertFalse(result.message().contains("⚠"), "the nonce is not refused or warned about: " + result.message());
        assertEquals(bytes, ProjectProfile.write(loaded, share, file), "load then write round-trips byte-for-byte");
        assertTrue(KnownKeys.PROFILE_FAMILIES.contains(KnownKeys.family(ProjectProfile.NONCE_KEY)),
                "an owned family: the writer decides it, preserve-unknown never copies it about");

        String shared = share.export(loaded, SettingsShare.Category.defaults());
        assertFalse(shared.contains(ProjectProfile.NONCE_KEY), "a share export never carries the nonce: " + shared);

        // importing another profile's text into this project changes its settings, not which profile it is
        Path other = ProjectProfile.pathFor(dir.resolve("q"));
        ProjectProfile.save(other, configWith("/src/q", "com.acme.Q", "h"), share);
        String otherNonce = ProjectProfile.nonce(other).orElseThrow();
        assertNotEquals(nonce, otherNonce, "a different profile has its own nonce");
        SettingsShare.ImportPlan plan = share.preview(Files.readString(other), loaded, ProjectProfile.baseDirFor(other));
        share.apply(plan, ProjectProfile.PROJECT_SCOPED, loaded);
        assertTrue(ProjectProfile.save(file, loaded, share));
        assertEquals(nonce, ProjectProfile.nonce(file).orElse(null), "an import does not transfer the other profile's nonce");

        Path fork = ProjectProfile.pathFor(dir.resolve("fork"));
        ProjectProfile.save(fork, loaded, share);
        assertNotEquals(nonce, ProjectProfile.nonce(fork).orElse(null), "save-as to a new file is a new profile");
    }

    /**
     * A profile with no nonce (written before this key, or shipped in a bundle) is adopted on its first REAL
     * write — never on a no-op, which must stay a no-op for a committed file (M35.11).
     */
    @Test
    void aProfileWithoutANonceAdoptsOneOnlyWhenItIsWrittenAnyway(@TempDir Path dir) throws Exception {
        Path file = ProjectProfile.pathFor(dir.resolve("bundle"));
        Files.createDirectories(file.getParent());
        AppConfig c = configWith("/src/a", "com.acme.A", "g");
        String withoutNonce = share.export(c, ProjectProfile.PROJECT_SCOPED, ProjectProfile.baseDirFor(file));
        Files.writeString(file, withoutNonce);
        assertTrue(ProjectProfile.nonce(file).isEmpty());

        AppConfig loaded = new AppConfig();
        assertNull(ProjectProfile.load(file, loaded, share).nonce());
        assertFalse(ProjectProfile.save(file, loaded, share), "no-op: nothing written, so no nonce is added");
        assertEquals(withoutNonce, Files.readString(file));

        loaded.sourceRoots.add("/src/b");
        assertTrue(ProjectProfile.save(file, loaded, share));
        assertTrue(ProjectProfile.nonce(file).isPresent(), "a real write adopts a nonce for a profile that had none");
    }
}
