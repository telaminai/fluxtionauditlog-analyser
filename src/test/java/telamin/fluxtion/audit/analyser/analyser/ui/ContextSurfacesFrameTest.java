package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.awaitLoaded;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * readable-surfaces — the runtime half of step 1's evidence. {@code PublishedSurfacesTest} shows the SNAPSHOT carries the
 * views, and {@code ContextSectionsTest} shows statically that {@code surfaces} is a sectioned key. Neither executes
 * {@code context()}. This does, on a real frame with a real log: once the scan has settled, the live payload carries
 * {@code surfaces.statusLine} and {@code surfaces.identityBanner}, each exactly the published view's {@code fields()} —
 * the names the audit uses for the same render.
 */
class ContextSurfacesFrameTest {

    private static final String LOG = "src/main/resources/demo/demo-quote-audit.yaml";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> context(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        onEdt(() -> out.set((Map<String, Object>) render(f.ex, "context", Map.of()).get("context")));
        return out.get();
    }

    @Test
    @DisplayName("a live context payload carries surfaces.statusLine and surfaces.identityBanner, as the views' fields()")
    @SuppressWarnings("unchecked")
    void theLiveContextCarriesTheSurfaces(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(LOG).toAbsolutePath().toString())));
            awaitLoaded(f.ex);
            SessionDriver session = (SessionDriver) field(f.frame, "session");
            long deadline = System.currentTimeMillis() + 10_000;               // the scan settles on a later EDT turn
            AtomicReference<Boolean> settled = new AtomicReference<>(false);
            while (System.currentTimeMillis() < deadline && !settled.get()) {
                onEdt(() -> settled.set(session.snapshot().statusLine() != null && session.snapshot().identityBanner() != null));
                if (!settled.get()) Thread.sleep(50);
            }
            assertTrue(settled.get(), "control: the session published both views once the scan settled");

            Map<String, Object> ctx = context(f);
            assertTrue(ctx.containsKey("surfaces"), "the live payload has a surfaces section: " + ctx.keySet());
            Map<String, Object> surfaces = (Map<String, Object>) ctx.get("surfaces");
            AtomicReference<Map<String, Object>> line = new AtomicReference<>();
            AtomicReference<Map<String, Object>> banner = new AtomicReference<>();
            onEdt(() -> {
                line.set(session.snapshot().statusLine().fields());
                banner.set(session.snapshot().identityBanner().fields());
            });
            assertTrue(surfaces.containsKey("statusLine"), "surfaces.statusLine is published: " + surfaces.keySet());
            assertTrue(surfaces.containsKey("identityBanner"), "surfaces.identityBanner is published: " + surfaces.keySet());
            assertEquals(List.copyOf(line.get().keySet()), List.copyOf(((Map<String, Object>) surfaces.get("statusLine")).keySet()),
                    "surfaces.statusLine uses exactly the names StatusLineView.fields() publishes, in its order");
            assertEquals(List.copyOf(banner.get().keySet()), List.copyOf(((Map<String, Object>) surfaces.get("identityBanner")).keySet()),
                    "surfaces.identityBanner uses exactly the names IdentityBannerView.fields() publishes, in its order");
            assertEquals(line.get(), surfaces.get("statusLine"), "and the values are the published view's own");
            assertEquals(banner.get(), surfaces.get("identityBanner"));
        }
    }

    // ---- step 2: context's log.identity, branches 2 and 3 — characterised BEFORE the fold, unchanged after it ------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> identity(Map<String, Object> ctx) {
        return ctx.get("log") instanceof Map<?, ?> log ? (Map<String, Object>) ((Map<String, Object>) log).get("identity") : null;
    }

    @Test
    @DisplayName("branch 2: a session verdict is stated as log.identity {state, reason}, and the banner view agrees")
    @SuppressWarnings("unchecked")
    void aSessionVerdictIsStated(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(LOG).toAbsolutePath().toString())));
            awaitLoaded(f.ex);
            SessionDriver session = (SessionDriver) field(f.frame, "session");
            assertNull(identity(context(f)), "control: an unchanged file with no verdict states no identity at all");

            onEdt(() -> session.post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved(
                    session.snapshot().logGeneration(), "REPLACEMENT", "DEMO replaced on disk")));
            Map<String, Object> ctx = context(f);
            assertEquals(Map.of("state", "replacement", "reason", "DEMO replaced on disk"), identity(ctx),
                    "the session's verdict, as context has always worded it");
            var banner = (Map<String, Object>) ((Map<String, Object>) ctx.get("surfaces")).get("identityBanner");
            assertEquals("REPLACEMENT", banner.get("verdict"), "and the banner was told the same verdict");
        }
    }

    @Test
    @DisplayName("branch 3: a store that never looks at its file is stated 'not assessed', never read as a check that passed")
    void aStoreThatDoesNotLookIsNotAssessed(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        var reader = new AsyncOpenInterleavingFrameTest.DelayedReader(false, "DEMO_node");
        reader.release.countDown();                                        // no delay: read at once
        java.nio.file.Path file = java.nio.file.Files.writeString(tmp.resolve("demo.slow"), "placeholder");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp, reader)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> render(f.ex, "open", Map.of("log", file.toString())));
            awaitLoaded(f.ex);
            Map<String, Object> id = identity(context(f));
            assertNotNull(id, "a store that does not look must SAY so — its silence would read as a passed check");
            assertEquals("not assessed", id.get("state"));
            assertEquals("this log's reader does not report whether its file has changed since it was read, so no change "
                    + "being shown is not evidence that there was none", id.get("reason"));
            // step 2: the statement is a PROJECTION of the published view — the session now knows the store does not look
            @SuppressWarnings("unchecked")
            var banner = (Map<String, Object>) ((Map<String, Object>) context(f).get("surfaces")).get("identityBanner");
            assertEquals("NOT_ASSESSED", banner.get("verdict"), "the banner was told the same: not assessed");
            assertEquals(false, banner.get("shown"), "and it draws nothing — the screen speaks only of a change");
        }
    }
}
