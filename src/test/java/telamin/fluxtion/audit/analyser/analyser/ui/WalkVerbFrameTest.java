package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

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
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * M69 S4 (spec-spotlight-walks.md §3.9, §3.10; W-A1, W-A10, W-A17 UI half) — on a REAL frame: a walk the assistant
 * saved through the verb is listed in the Reports tab and counted in the Project panel; the tab's Play and Play from
 * step play it through the session; {@code context.walks} reports the states the strip shows; the tab's Delete and
 * Restore are the verb's bin, and deleting the showing walk ends it.
 */
class WalkVerbFrameTest {

    private static final List<Object> STEPS = List.of(
            Map.of("caption", "every price arrives here", "view", Map.of("tab", "topology"),
                    "targets", List.of(Map.of("target", "topology:node:priceListener", "caption", "first"))),
            Map.of("caption", "and leaves here", "view", Map.of("tab", "topology"),
                    "targets", List.of(Map.of("target", "topology:node:quotePublisher", "caption", "last"))));

    @Test
    @DisplayName("W-A10/W-A17: verb-saved walk → Reports tab → Play → context states = strip states → Delete ends it → Restore")
    @SuppressWarnings("unchecked")
    void theVerbAndTheTabAreOnePath(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            onEdt(() -> render(f.ex, "walk", Map.of("name", "tour", "title", "The price path", "steps", STEPS)));
            AppConfig config = (AppConfig) field(f.frame, "config");
            assertEquals(WalkSpec.AUTHOR_ASSISTANT, config.walks.get(0).author(), "the verb's save says who saved it");

            WalksPanel panel = f.frame.walksPanel;
            onEdt(() -> assertEquals("tour", panel.selectedName(), "W-A1: the Reports tab lists the saved walk"));
            onEdt(() -> assertTrue(panel.detailText().contains("by the assistant"), panel.detailText()));
            onEdt(() -> {
                var reports = ProjectModel.from((Map<String, Object>) render(f.ex, "context", Map.of()).get("context"))
                        .sections().stream().filter(s -> s.title().equals(ProjectModel.REPORTS)).findFirst().orElseThrow();
                assertTrue(reports.rows().stream().anyMatch(r -> r.primary().equals("1 spotlight walk")),
                        "the Project panel counts the walk beside the reports: " + reports.rows());
            });

            onEdt(panel.play::doClick);
            await("step 1 shown", () -> walk(f).showing() && walk(f).step() == 0 && "SHOWN".equals(walk(f).phase()));
            await("step 1 lit", () -> overlay.lit().stream().anyMatch(l -> l.target().equals("topology:node:priceListener")));

            AtomicReference<Map<String, Object>> ctx = new AtomicReference<>();
            onEdt(() -> ctx.set((Map<String, Object>) render(f.ex, "context", Map.of()).get("context")));
            Map<String, Object> showing = (Map<String, Object>) ((Map<String, Object>) ctx.get().get("walks")).get("showing");
            assertEquals("tour", showing.get("walk"));
            List<Map<String, Object>> targets = (List<Map<String, Object>>) showing.get("targets");
            List<SessionEvents.WalkTargetState> published = walk(f).targets();
            assertEquals(published.size(), targets.size());
            for (int i = 0; i < published.size(); i++) {
                assertEquals(published.get(i).state(), targets.get(i).get("state"), "context reports the published state");
                assertEquals(published.get(i).available(), targets.get(i).get("available"));
            }
            onEdt(() -> assertEquals("step " + showing.get("step") + " of " + showing.get("of"), overlay.strip().position(),
                    "and the strip shows the same step"));

            onEdt(() -> panel.selectStep(2));
            onEdt(panel.playFrom::doClick);
            await("played from step 2", () -> walk(f).step() == 1 && "SHOWN".equals(walk(f).phase()));

            panel.confirmDelete = w -> true;
            onEdt(panel.delete::doClick);
            await("deleting the showing walk ends it", () -> !walk(f).showing());
            assertTrue(config.walks.isEmpty());
            onEdt(() -> assertNull(overlay.strip(), "and its strip goes with it"));

            panel.restoreChooser = names -> names.get(0);
            onEdt(panel.restore::doClick);
            assertEquals(List.of("tour"), config.walks.stream().map(WalkSpec::name).toList(), "W-A9: restored from the tab");
            onEdt(() -> assertEquals("tour", panel.selectedName()));
        }
    }
}
