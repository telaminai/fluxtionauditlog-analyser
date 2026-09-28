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
}
