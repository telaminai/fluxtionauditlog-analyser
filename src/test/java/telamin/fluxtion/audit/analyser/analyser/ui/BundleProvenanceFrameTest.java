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
