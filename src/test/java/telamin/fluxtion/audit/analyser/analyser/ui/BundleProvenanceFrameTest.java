package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.awaitDecided;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.exchange;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.openLog;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.shown;

/**
 * #76 on a REAL frame, by round trip: capture a bundle, then open that same bundle and ask the window and the
 * Project panel's payload what is loaded. Before this, both said "a project" — a bundle supplies a profile, so
 * received evidence was indistinguishable from the person's own work the moment the open dialog was dismissed.
 */
class BundleProvenanceFrameTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> projectContext(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        onEdt(() -> {
            var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", List.of("project"))).get("context");
            out.set((Map<String, Object>) ctx.get("project"));
        });
        return out.get();
    }

    /** Open a bundle the way the Start page and a drop do, and wait for the provenance to settle. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> openBundleAndWait(AsyncOpenInterleavingFrameTest.Frame f, Path fexp)
            throws Exception {
        onEdt(() -> {
            try {
                var m = MainFrame.class.getDeclaredMethod("loadExperiment", Path.class);
                m.setAccessible(true);
                m.invoke(f.frame, fexp);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        for (int i = 0; i < 200; i++) {
            Map<String, Object> proj = projectContext(f);
            if (proj != null && proj.get("bundle") != null) return (Map<String, Object>) proj.get("bundle");
            Thread.sleep(50);
        }
        return (Map<String, Object>) projectContext(f).get("bundle");
    }

    @Test
    @DisplayName("after a bundle is opened, the window and the project payload both say it is evidence, and which")
    void anOpenedBundleSaysSoForTheSessionsLife(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "evidence.fexp"))));
            Map<String, Object> written = awaitDecided(f);
            assertEquals("WRITTEN", written.get("phase"), "the fixture bundle was written: " + written);
            String identity = String.valueOf(written.get("identity"));
            Path fexp = dir.resolve("evidence.fexp");

            Map<String, Object> bundle = openBundleAndWait(f, fexp);

            assertNotNull(bundle, "projectPayloadNamesTheBundle");
            assertEquals(identity, bundle.get("identity"), "identityIsPublishedToTheProjectPayload");
            assertEquals(fexp.toString(), bundle.get("source"),
                    "sourceIsTheFexpThatWasOpened, not the unpacked profile inside it");
            assertEquals(Boolean.TRUE, bundle.get("verified"));

            // the start page's progress blurb is not left behind: the Project panel carries these facts
            // permanently now, so the "Verified …, the audit log is loading" text is a stale duplicate
            AtomicReference<String> blurb = new AtomicReference<>("");
            onEdt(() -> {
                var sp = (StartPanel) field(f.frame, "startPanel");
                var fb = (javax.swing.JComponent) field(sp, "operationFeedback");
                blurb.set(fb.isVisible() ? "VISIBLE" : "");
            });
            assertEquals("", blurb.get(), "theStartPageProgressBlurbIsCleared");

            AtomicReference<String> title = new AtomicReference<>();
            onEdt(() -> title.set(f.frame.getTitle()));
            String bare = identity.startsWith("sha256:") ? identity.substring(7) : identity;
            assertTrue(title.get().contains("[evidence bundle " + bare.substring(0, 12) + "]"),
                    "titleSaysWhichBundle, was: " + title.get());

            var rows = ProjectModel.from(Map.of("project", projectContext(f))).sections().stream()
                    .flatMap(s -> s.rows().stream()).toList();
            assertTrue(rows.stream().anyMatch(r -> r.primary().startsWith("Evidence bundle ")),
                    "projectPanelHasAnEvidenceRow: " + rows.stream().map(ProjectModel.Row::primary).toList());
        }
    }

    @Test
    @DisplayName("#73: an opened bundle joins a recents list that says what it is, and reopens from it")
    void anOpenedBundleIsDiscoverableAfterwards(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle",
                    Map.of("path", "discoverable.fexp", "notes", "DEMO what the sender said this is"))));
            Map<String, Object> written = awaitDecided(f);
            assertEquals("WRITTEN", written.get("phase"), "fixture written: " + written);
            Path fexp = dir.resolve("discoverable.fexp");
            openBundleAndWait(f, fexp);

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            assertEquals(1, config.recentBundles.size(), "bundleIsRemembered: " + config.recentBundles);
            var remembered = config.recentBundles.getFirst();
            assertEquals(fexp.toString(), remembered.path());
            assertEquals(written.get("identity"), remembered.identity(), "recentRemembersWhatItVerifiedAs");
            assertEquals("DEMO what the sender said this is", remembered.notes(),
                    "recentSaysWhatTheSenderClaimed");

            @SuppressWarnings("unchecked")
            var ctx = (Map<String, Object>) onEdtGet(() ->
                    render(f.ex, "context", Map.of("sections", List.of("project"))).get("context"));
            @SuppressWarnings("unchecked")
            var bundles = (Map<String, Object>) ctx.get("bundles");
            assertNotNull(bundles, "recentsArePublishedForDiscovery");
            @SuppressWarnings("unchecked")
            var recent = (List<Map<String, Object>>) bundles.get("recent");
            assertEquals(fexp.toString(), recent.getFirst().get("path"));
            assertEquals(Boolean.TRUE, recent.getFirst().get("present"));
            assertEquals("DEMO what the sender said this is", recent.getFirst().get("notes"));
        }
    }

    @Test
    @DisplayName("a project RESTORED at startup has its source roots in force, not just in the config")
    void aRestoredProjectHasItsSourceInForce(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
        Path src = java.nio.file.Files.createDirectories(mine.resolve("src/main/java"));
        Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
        java.nio.file.Files.createDirectories(profile.getParent());
        java.nio.file.Files.writeString(profile, "sourceRoot.0=" + src + "\nsourceRoot.count=1\n");

        // The state a restart finds: this project was in force when the app last closed.
        Path cfg = tmp.resolve("home").resolve(".fluxtion-analyser").resolve("config");
        java.nio.file.Files.createDirectories(cfg.getParent());
        java.nio.file.Files.writeString(cfg, "activeProjectPath=" + profile.toString().replace(":", "\\:") + "\n");

        try (var f = shown(tmp)) {
            Thread.sleep(500);
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            assertEquals(List.of(src.toString()), List.copyOf(config.sourceRoots),
                    "precondition: the restored project's roots are in the config");

            assertEquals(List.of(src.toString()), sourceRootsOf(f.frame),
                    "theSourceServiceHasThemToo — a project restored at startup put its roots in the "
                            + "config and never configured source, so every processor read 'source not found'");
        }
    }

    @Test
    @DisplayName("a project restored at startup is asked what to open, like any other way one arrives")
    void aRestoredProjectIsAlsoOffered(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
        Path src = java.nio.file.Files.createDirectories(mine.resolve("src/main/java"));
        Path log = java.nio.file.Files.copy(EvidenceCaptureFrameTest.DEMO_LOG, mine.resolve("run.yaml"));
        Path graph = java.nio.file.Files.writeString(mine.resolve("t.graphml"), "<graphml/>");
        Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
        java.nio.file.Files.createDirectories(profile.getParent());
        java.nio.file.Files.writeString(profile, "sourceRoot.0=" + src + "\nsourceRoot.count=1\n");

        // The state a restart finds: this project in force, and these opened inside it.
        String esc = profile.toString().replace(":", "\\:");
        Path cfg = tmp.resolve("home").resolve(".fluxtion-analyser").resolve("config");
        java.nio.file.Files.createDirectories(cfg.getParent());
        java.nio.file.Files.writeString(cfg, "activeProjectPath=" + esc + "\n"
                + "recentFile.0=" + log.toString().replace(":", "\\:") + "\nrecentFile.count=1\n"
                + "recentGraphml.0=" + graph.toString().replace(":", "\\:") + "\nrecentGraphml.count=1\n");

        var asked = new java.util.concurrent.atomic.AtomicReference<
                telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen>();
        setChooser((label, candidates) -> {        // BEFORE the frame: the offer can fire as it starts
            asked.set(candidates);
            return null;
        });
        try (var f = shown(tmp)) {
            // the offer is deferred to after the window is up, so it cannot block construction
            for (int i = 0; i < 100 && asked.get() == null; i++) Thread.sleep(50);

            assertNotNull(asked.get(), "aRestoredProjectIsAsked — it comes up as empty as any other");
            assertEquals(List.of(log.toString()), asked.get().logs(), "itsOwnLog");
            assertEquals(List.of(graph.toString()), asked.get().topologies(), "andItsOwnTopology");
        }
    }

    @Test
    @DisplayName("a project opened AFTER an experiment still gets its own source roots")
    void aProjectAfterAnExperimentKeepsItsSource(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
            Path src = java.nio.file.Files.createDirectories(mine.resolve("src/main/java"));
            Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
            java.nio.file.Files.createDirectories(profile.getParent());
            java.nio.file.Files.writeString(profile, "sourceRoot.0=" + src + "\nsourceRoot.count=1\n");

            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "after.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            openBundleAndWait(f, dir.resolve("after.fexp"));

            // ...and now open your own project, the way the person did.
            openAsAPerson(f.frame, profile);
            Thread.sleep(600);

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            assertEquals(List.of(src.toString()), List.copyOf(config.sourceRoots),
                    "theProjectsOwnRootsAreInForce");

            @SuppressWarnings("unchecked")
            var ctx = (Map<String, Object>) onEdtGet(() ->
                    render(f.ex, "context", Map.of("sections", List.of("source"))).get("context"));
            @SuppressWarnings("unchecked")
            var source = (Map<String, Object>) ctx.get("source");
            @SuppressWarnings("unchecked")
            var roots = (List<String>) source.get("roots");

            assertEquals(List.of(src.toString()), roots,
                    "andTheSourceServiceHasThem — empty here is why every processor showed red");
        }
    }

    @Test
    @DisplayName("opening a project configures the source service with that project's roots")
    void openingAProjectConfiguresSource(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
            Path src = java.nio.file.Files.createDirectories(mine.resolve("src/main/java"));
            Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
            java.nio.file.Files.createDirectories(profile.getParent());
            java.nio.file.Files.writeString(profile,
                    "sourceRoot.0=" + src + "\nsourceRoot.count=1\n");

            openAsAPerson(f.frame, profile);
            Thread.sleep(400);

            @SuppressWarnings("unchecked")
            var ctx = (Map<String, Object>) onEdtGet(() ->
                    render(f.ex, "context", Map.of("sections", List.of("source"))).get("context"));
            @SuppressWarnings("unchecked")
            var source = (Map<String, Object>) ctx.get("source");
            @SuppressWarnings("unchecked")
            var roots = (List<String>) source.get("roots");

            assertEquals(List.of(src.toString()), roots,
                    "theSourceServiceKnowsTheProjectsRoots — without these every processor reads 'source not found'");
        }
    }

    @Test
    @DisplayName("the offer stays out of the way while a spotlight is showing something")
    void theOfferYieldsToAWalkthrough(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
            Path src = java.nio.file.Files.createDirectories(mine.resolve("src/main/java"));
            Path log = java.nio.file.Files.copy(EvidenceCaptureFrameTest.DEMO_LOG, mine.resolve("run.yaml"));
            Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
            java.nio.file.Files.createDirectories(profile.getParent());
            java.nio.file.Files.writeString(profile, "sourceRoot.0=" + src + "\nsourceRoot.count=1\n");
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            config.addRecent(log.toString());

            var offers = new java.util.concurrent.atomic.AtomicInteger();
            setChooser((label, candidates) -> {
                offers.incrementAndGet();
                return null;
            });

            // something is being SHOWN: a lit spotlight is the walkthrough's whole point, and the
            // modal took the click that dismissed it, killing the walk with the light.
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "spotlight", Map.of("target", "project")));

            openAsAPerson(f.frame, profile);
            Thread.sleep(400);

            assertEquals(0, offers.get(), "aLitSpotlightIsNotInterrupted");
        }
    }

    @Test
    @DisplayName("O3: opening a project offers its logs and topologies, and a bundle is never asked")
    void openingAProjectOffersWhatBelongsToIt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine"));
            Path log = java.nio.file.Files.copy(EvidenceCaptureFrameTest.DEMO_LOG, mine.resolve("run.yaml"));
            Path graph = java.nio.file.Files.writeString(mine.resolve("t.graphml"), "<graphml/>");
            Path src = java.nio.file.Files.createDirectories(mine.resolve("src/main/java"));
            Path profile = mine.resolve(".analyser").resolve("project.fluxtion-settings");
            java.nio.file.Files.createDirectories(profile.getParent());
            java.nio.file.Files.writeString(profile, "sourceRoot.0=" + src + "\nsourceRoot.count=1\n");

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            config.addRecent(log.toString());
            config.addRecentGraphml(graph.toString());

            // The seam: a person answers a dialog, a test answers this. What it is ASKED is the assertion.
            var asked = new java.util.concurrent.atomic.AtomicReference<
                    telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen>();
            var offers = new java.util.concurrent.atomic.AtomicInteger();
            var settledWhenAsked = new java.util.concurrent.atomic.AtomicBoolean();
            setChooser((label, candidates) -> {
                offers.incrementAndGet();
                asked.set(candidates);
                // THE INVARIANT. This is a MODAL question: everything after it waits for a human. If it
                // is asked mid-transition, the rest of the transition is stuck behind it -- which is how
                // a project came up with every processor red and no source (found in use, 2026-09-30).
                settledWhenAsked.set(!sourceRootsOf(f.frame).isEmpty());
                return null;        // "Not now" -- the offer is what is under test, not the opening
            });

            // The PERSON's entrance. Every project entrance declares its audience through
            // requestProject's `interactive` argument, and the offer is a modal question: the socket
            // verb declares false and is asserted below to stay silent.
            openAsAPerson(f.frame, profile);
            for (int i = 0; i < 100 && offers.get() == 0; i++) Thread.sleep(50);

            assertEquals(1, offers.get(), "openingAProjectOffersOnce");
            assertTrue(settledWhenAsked.get(),
                    "theTransitionIsFinishedBeforeTheQuestion — a modal asked mid-transition strands the "
                            + "source service unconfigured and every processor reads red");
            assertEquals(List.of(log.toString()), asked.get().logs(), "itsOwnLogIsOffered");
            assertEquals(List.of(graph.toString()), asked.get().topologies(), "andItsOwnTopology");

            // An assistant opening a project over the socket must not stop on a dialog nobody can see.
            offers.set(0);
            onEdt(() -> render(f.ex, "open", Map.of("close", "project")));
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            Thread.sleep(300);
            assertEquals(0, offers.get(), "theSocketIsNeverAskedToAnswerAModal");

            // A bundle brings its own evidence; being offered a choice at that moment is nonsense.
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "offer.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            offers.set(0);
            openBundleAndWait(f, dir.resolve("offer.fexp"));
            Thread.sleep(300);

            assertEquals(0, offers.get(), "aBundleIsNeverAskedWhatToReopen");
        }
    }

    /** The roots the SOURCE SERVICE actually has — not the config's copy, which is set earlier. */
    private static List<String> sourceRootsOf(MainFrame frame) {
        try {
            var field = MainFrame.class.getDeclaredField("sourceService");
            field.setAccessible(true);
            var service = (telamin.fluxtion.audit.analyser.analyser.source.SourceService) field.get(frame);
            return service.resolver().roots().stream().map(Object::toString).toList();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What a person's Open project does: declare the audience and request the transition. */
    private static void openAsAPerson(MainFrame frame, Path profile) throws Exception {
        var method = MainFrame.class.getDeclaredMethod("requestProject", Path.class,
                telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.class, String.class, boolean.class);
        method.setAccessible(true);
        onEdt(() -> {
            try {
                method.invoke(frame, profile,
                        telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH,
                        "test-person", true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void setChooser(java.util.function.BiFunction<String,
            telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen, Object> chooser) throws Exception {
        var field = MainFrame.class.getDeclaredField("reopenChooser");
        field.setAccessible(true);
        field.set(null, chooser);
    }

    @org.junit.jupiter.api.AfterEach
    void noStrayChooser() throws Exception {
        setChooser(null);       // static seam: never leak one test's answer into the next
    }

    @Test
    @DisplayName("a bundle's anchored source does not leak into the project you open next")
    void anchoredSourceDoesNotLeakIntoTheNextProject(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            Path mine = java.nio.file.Files.createDirectories(tmp.resolve("mine/src/main/java"));
            String myRoot = mine.toAbsolutePath().normalize().toString();
            Path bundleCode = java.nio.file.Files.createDirectories(tmp.resolve("demo-src"));
            String anchored = bundleCode.toAbsolutePath().normalize().toString();

            Path profile = tmp.resolve("mine").resolve(".analyser").resolve("project.fluxtion-settings");
            java.nio.file.Files.createDirectories(profile.getParent());
            java.nio.file.Files.writeString(profile, "sourceRoot.0=" + myRoot + "\nsourceRoot.count=1\n");
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            onEdt(() -> render(f.ex, "source_root", Map.of("add", List.of(myRoot))));
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            assertTrue(config.sourceRoots.contains(myRoot), "precondition: the project has its own root");

            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "leak.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            Path fexp = dir.resolve("leak.fexp");

            openBundleAndWait(f, fexp);
            onEdt(() -> render(f.ex, "source_root", Map.of("add", List.of(anchored))));
            assertEquals(List.of(anchored), config.bundleSourceRoots(fexp.toString()),
                    "precondition: the bundle is anchored to its own source");

            // Back to your own project. The bundle's source is a fact about the BUNDLE; it has no
            // business in a profile you commit.
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            for (int i = 0; i < 100 && config.sourceRoots.contains(anchored); i++) Thread.sleep(50);

            assertFalse(config.sourceRoots.contains(anchored),
                    "theBundlesSourceIsNotInTheProjectsRoots");
            assertTrue(java.nio.file.Files.readString(profile).lines()
                            .noneMatch(line -> line.contains(anchored)),
                    "andItIsNotWrittenToTheProfileOnDisk");
        }
    }

    @Test
    @DisplayName("#76: the title names the BUNDLE, not the temp folder it was unpacked into")
    void theTitleNamesTheBundle(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "named-run.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            Path fexp = dir.resolve("named-run.fexp");

            openBundleAndWait(f, fexp);

            String title = onEdtGet(f.frame::getTitle).toString();
            assertTrue(title.contains("named-run.fexp"),
                    "theBundleIsNamedByItsOwnFile — the working copy is a temp directory called "
                            + "bundle-<hex>-<random>, which nobody can follow: " + title);
            assertTrue(title.contains("evidence bundle"), "andItSaysWhatItIs: " + title);
            assertFalse(title.contains("bundle-6f"),
                    "notTheUnpackedFoldersName: " + title);
        }
    }

    @Test
    @DisplayName("#75: deleting a bundle's source root sticks — it does not come back on reopen")
    void deletingTheAnchorSticks(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            Path code = java.nio.file.Files.createDirectories(tmp.resolve("checkout/src/main/java"));
            String canonical = code.toAbsolutePath().normalize().toString();

            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "sticky.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            Path fexp = dir.resolve("sticky.fexp");
            openBundleAndWait(f, fexp);

            onEdt(() -> render(f.ex, "source_root", Map.of("add", List.of(canonical))));
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            assertEquals(List.of(canonical), config.bundleSourceRoots(fexp.toString()),
                    "precondition: the bundle is anchored");

            // THE PERSON DELETES IT. Not a transient emptiness during a transition -- an explicit act,
            // through the same funnel the Project panel's Remove and the Settings dialog use.
            onEdt(() -> {
                try {
                    var m = MainFrame.class.getDeclaredMethod("removeSourceRoot", String.class);
                    m.setAccessible(true);
                    m.invoke(f.frame, canonical);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            assertFalse(config.sourceRoots.contains(canonical), "precondition: the root is gone");

            onEdt(() -> render(f.ex, "open", Map.of("close", "project")));
            Thread.sleep(300);
            openBundleAndWait(f, fexp);
            Thread.sleep(400);

            assertEquals(List.of(), config.bundleSourceRoots(fexp.toString()),
                    "theAnchorIsForgottenWhenYouDeleteIt");
            assertFalse(config.sourceRoots.contains(canonical),
                    "andItDoesNotComeBackOnReopen — deleting it must mean something");
        }
    }

    @Test
    @DisplayName("#75: a bundle arrives with no source; anchoring one is remembered and put back on reopen")
    void anchoringABundleToASourceTreeIsRemembered(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            Path code = java.nio.file.Files.createDirectories(tmp.resolve("checkout/src/main/java"));
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "anchored.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            Path fexp = dir.resolve("anchored.fexp");

            Map<String, Object> before = openBundleAndWait(f, fexp);
            assertEquals("none", before.get("sourceAnchor"), "aBundleArrivesWithNoSource");
            assertNotNull(before.get("sourceAnchorNote"));

            String canonical = code.toAbsolutePath().normalize().toString();
            // THE PATH A PERSON TAKES. ConfigPanel.saveToConfig rebuilds config.sourceRoots directly and
            // never calls addSourceRoot, so hanging the anchor off that method caught the verb and missed
            // the Settings dialog the Project panel's own row sends you to. Found in use, 2026-09-30:
            // every test went through the verb. This one goes through the dialog's effect on the config.
            onEdt(() -> {
                var cfg = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
                cfg.sourceRoots.clear();
                cfg.sourceRoots.add(canonical);
                try {
                    var m = MainFrame.class.getDeclaredMethod("onConfigChanged");
                    m.setAccessible(true);
                    m.invoke(f.frame);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            assertEquals(List.of(canonical), config.bundleSourceRoots(fexp.toString()),
                    "anchorIsRememberedAgainstTheBundle, however the root was added");

            // forget the root the way a fresh machine would, then reopen the same bundle.
            // The barrier has to be the THING ASSERTED: openBundleAndWait polls for a bundle in the payload,
            // and one is already there from the first open, so it returns immediately and the assertion
            // races the background unpack. A review called this green-by-timing; it then failed for real
            // under load, in a full-suite run, having passed alone many times.
            onEdt(() -> config.sourceRoots.remove(canonical));
            openBundleAndWait(f, fexp);
            boolean restored = false;
            for (int i = 0; i < 200 && !restored; i++) {
                restored = config.sourceRoots.contains(canonical);
                if (!restored) Thread.sleep(50);
            }
            assertTrue(restored, "anchorIsRestoredOnReopen (waited for the reopen to land)");

            @SuppressWarnings("unchecked")
            var ctx = (Map<String, Object>) onEdtGet(() ->
                    render(f.ex, "context", Map.of("sections", List.of("project"))).get("context"));
            @SuppressWarnings("unchecked")
            var proj = (Map<String, Object>) ctx.get("project");
            @SuppressWarnings("unchecked")
            var bundle = (Map<String, Object>) proj.get("bundle");
            assertEquals(canonical, bundle.get("sourceAnchor"), "anchorIsPublished");
        }
    }

    @Test
    @DisplayName("notes carry an author's own structure verbatim — no schema is needed to keep an experiment's shape")
    void notesCarryWhateverStructureTheAuthorWrites(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        String account = """
                ## Hypothesis
                DEMO rounding the mid to 4dp changes nothing a recipient can see.

                ## Outcome
                It moves three values across two records, out of seven inputs.
                """;
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle",
                    Map.of("path", "structured.fexp", "notes", account))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));

            try (var zip = new java.util.zip.ZipFile(dir.resolve("structured.fexp").toFile())) {
                var entry = zip.getEntry("notes/NOTES.md");
                assertNotNull(entry, "theAccountIsPacked");
                String packed = new String(zip.getInputStream(entry).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                // contains() would survive collapsing the newlines or reordering the sections, which is
                // exactly what "verbatim" rules out. The fixture already ends in a newline.
                assertEquals(account, packed, "theAccountIsPackedVerbatim");
            }
        }
    }

    @Test
    @DisplayName("closing a bundle whose render then fails still drops the claim — the project is already gone")
    void aCloseWhoseRenderFailsDropsTheClaimOnTheRealFrame(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "closing.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            openBundleAndWait(f, dir.resolve("closing.fexp"));

            AtomicReference<String> lit = new AtomicReference<>();
            onEdt(() -> lit.set(f.frame.getTitle()));
            assertTrue(lit.get().contains("evidence bundle"), "precondition: the window says so");

            // ProjectSession.close() is the real half and runs first; only the render throws.
            MainFrame.beforeProjectRender = () -> { throw new IllegalStateException("DEMO the render threw"); };
            try {
                onEdt(() -> render(f.ex, "open", Map.of("close", "project")));
                for (int i = 0; i < 100 && lit.get().contains("evidence bundle"); i++) {
                    onEdt(() -> lit.set(f.frame.getTitle()));
                    if (!lit.get().contains("evidence bundle")) break;
                    Thread.sleep(50);
                }
            } finally {
                MainFrame.beforeProjectRender = () -> { };
            }

            assertFalse(lit.get().contains("evidence bundle"),
                    "titleDropsTheClaimWhenTheProjectIsGone, was: " + lit.get());

            // F5: a render failure is NOT a transition failure. Opening a project whose render throws must
            // still report the project as opened, or its caller silently drops the discovery selection.
            Path own = java.nio.file.Files.createDirectories(tmp.resolve("own/.analyser")).resolve("project.fluxtion-settings");
            java.nio.file.Files.writeString(own, "logFile=\n");
            MainFrame.beforeProjectRender = () -> { throw new IllegalStateException("DEMO the render threw"); };
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            try {
                onEdt(() -> echo.set(f.ex.render("open",
                        new java.util.LinkedHashMap<>(Map.of("project", own.toString()))).toMap()));
            } finally {
                MainFrame.beforeProjectRender = () -> { };
            }
            assertEquals(Boolean.TRUE, echo.get().get("ok"),
                    "aRenderFailureIsNotATransitionFailure: " + echo.get());
        }
    }

    @Test
    @DisplayName("a bundle names the event processor its log came from, and a recipient with none adopts it")
    void aBundleCarriesItsEventProcessor(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        String fqn = "com.acme.demo.generated.DemoQuoteRecordedProcessor";
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            onEdt(() -> config.selectedEventProcessor = fqn);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "withproc.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));

            // it is in the manifest, as a class name -- the graph never names the processor, only its nodes
            try (var zip = new java.util.zip.ZipFile(dir.resolve("withproc.fexp").toFile())) {
                String manifest = new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(manifest.contains("\"processor\":\"" + fqn + "\""),
                        "theManifestNamesTheProcessor: " + manifest);
            }

            // a recipient who has none adopts it; without this the Source tab opens empty
            onEdt(() -> { config.selectedEventProcessor = ""; config.eventProcessorFqns.clear(); });
            openBundleAndWait(f, dir.resolve("withproc.fexp"));
            assertEquals(fqn, config.selectedEventProcessor, "aRecipientWithNoneAdoptsIt");
            assertTrue(config.eventProcessorFqns.contains(fqn));

            // and the recipient can still choose differently: the selection is project-scoped, so it holds
            // for the session and is replaced by the NEXT project switch, exactly as any project setting is.
            // (A previous project's choice does not survive opening a bundle, because applying the bundle's
            // profile clears project-scoped settings first — that is a project switch, not an override.)
            onEdt(() -> config.selectedEventProcessor = "com.example.MyOwn");
            assertEquals("com.example.MyOwn", config.selectedEventProcessor,
                    "theRecipientCanStillChooseWithinTheSession");
        }
    }

    @Test
    @DisplayName("closing a bundle does not overwrite its anchor with the roots of the project you return to")
    void closingDoesNotPoisonTheAnchor(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            Path code = java.nio.file.Files.createDirectories(tmp.resolve("checkout/src/main/java"));
            String canonical = code.toAbsolutePath().normalize().toString();
            Path other = java.nio.file.Files.createDirectories(tmp.resolve("someone-elses/src/main/java"));
            String otherRoot = other.toAbsolutePath().normalize().toString();

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            // YOUR OWN settings hold your other project's roots. Without these the close restores an EMPTY
            // set and the empty-guard alone would hide the defect — which is why an earlier version of this
            // test passed with the fix removed.
            onEdt(() -> render(f.ex, "source_root", Map.of("add", List.of(otherRoot))));
            assertTrue(config.sourceRoots.contains(otherRoot), "precondition: your own settings have a root");

            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "poison.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            Path fexp = dir.resolve("poison.fexp");
            openBundleAndWait(f, fexp);

            onEdt(() -> render(f.ex, "source_root", Map.of("add", List.of(canonical))));
            assertEquals(List.of(canonical), config.bundleSourceRoots(fexp.toString()),
                    "precondition: the bundle is anchored to its own source");

            // Closing restores the person's OWN settings first — their other project's roots — and the
            // render that follows runs while openBundle still reports the bundle as in force. That wrote
            // the wrong roots over the anchor, and a reopen then could not find the bundle's source.
            onEdt(() -> render(f.ex, "open", Map.of("close", "project")));
            for (int i = 0; i < 100; i++) {
                if (!config.bundleSourceRoots(fexp.toString()).contains(otherRoot)) break;
                Thread.sleep(50);
            }

            assertEquals(List.of(canonical), config.bundleSourceRoots(fexp.toString()),
                    "theAnchorSurvivesTheClose, un-poisoned by the project returned to");
        }
    }

    private static Object onEdtGet(java.util.function.Supplier<Object> body) throws Exception {
        AtomicReference<Object> out = new AtomicReference<>();
        onEdt(() -> out.set(body.get()));
        return out.get();
    }

    @Test
    @DisplayName("an ordinary project does not claim to be evidence")
    void anOrdinaryLogSaysNothingAboutBundles(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            Map<String, Object> proj = projectContext(f);
            assertNull(proj.get("bundle"), "noBundleClaimWithoutABundle");
            AtomicReference<String> title = new AtomicReference<>();
            onEdt(() -> title.set(f.frame.getTitle()));
            assertFalse(title.get().contains("evidence bundle"), "titleClaimsNothing: " + title.get());
        }
    }

    /**
     * #80, found by CI (mutation shard 0, 2026-09-30): the listing of what this project holds walked the
     * exchange directory two levels deep, which took it INSIDE a capture's {@code .capture-…} working
     * folder. Nothing in there is evidence anybody can open, and the folder is created, written and deleted
     * under the walk, so {@code Files.walk} raised an {@code UncheckedIOException} — which is not an
     * {@code IOException}, so the catch never saw it — and killed the {@code context} call on the event
     * thread whenever a capture happened to be in flight. This pins the rule that removes the race: a
     * working folder is not entered at all.
     */
    @Test
    @DisplayName("#80: a capture's working folder is not this project's evidence")
    @SuppressWarnings("unchecked")
    void aWorkingFolderIsNeverListedAsEvidence(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "real.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));

            // a capture in flight, as BundleWriter leaves the exchange directory while it works: an owner
            // marker, the payload folder, and something that reads as evidence but is half a file
            Path working = java.nio.file.Files.createDirectories(dir.resolve(".capture-1234567890"));
            java.nio.file.Files.writeString(working.resolve(".owner"), "DEMO-host");
            java.nio.file.Files.createDirectories(working.resolve("bundle/log"));
            java.nio.file.Files.write(working.resolve("half-written.fexp"), new byte[]{1, 2, 3});

            var ctx = (Map<String, Object>) onEdtGet(() ->
                    render(f.ex, "context", Map.of("sections", List.of("project"))).get("context"));
            var bundles = (Map<String, Object>) ctx.get("bundles");
            assertNotNull(bundles, "the project's evidence is published: " + ctx.keySet());
            var inProject = (List<Map<String, Object>>) bundles.get("inProject");
            assertEquals(List.of(dir.resolve("real.fexp").toString()),
                    inProject.stream().map(b -> b.get("path")).toList(),
                    "aWorkingFolderIsNeverListedAsEvidence: " + inProject);
        }
    }
}
