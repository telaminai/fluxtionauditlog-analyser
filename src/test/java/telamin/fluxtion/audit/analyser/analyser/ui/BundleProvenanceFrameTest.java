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
}
