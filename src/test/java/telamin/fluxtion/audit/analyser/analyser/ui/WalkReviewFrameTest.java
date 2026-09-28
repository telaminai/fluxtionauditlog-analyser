package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * Review PR57 (implementation findings) — the cross-component checks the node tests could not make, on a REAL frame:
 * the real session, the real presenter, the real overlay and the real selection. Each test is a reviewer probe
 * scenario, turned into a regression.
 */
class WalkReviewFrameTest {

    static Map<String, Object> target(String name, String caption) {
        return Map.of("target", name, "caption", caption);
    }

    static void call(AsyncOpenInterleavingFrameTest.Frame f, String verb, Map<String, Object> params) throws Exception {
        onEdt(() -> render(f.ex, verb, params));
    }

    @Test
    @DisplayName("R3: an unavailable target 1 and an available target 2 — the overlay draws 2, not 1")
    void theOverlayKeepsTheSessionsNumbers(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var partial = Map.of("view", Map.of("tab", "topology"), "targets", List.of(
                    target("topology:node:DEMO_missing", "unavailable first"), target("topology:node:priceListener", "second")));
            call(f, "walk", Map.of("name", "DEMO_partial", "steps", List.of(partial)));
            call(f, "walk", Map.of("name", "DEMO_partial", "play", true));
            await("partly shown and lit", () -> "PARTLY_SHOWN".equals(walk(f).phase()) && overlay.isLit());
            onEdt(() -> {
                assertEquals(List.of(1, 2), walk(f).targets().stream().map(t -> t.n()).toList(), "control: the session numbers 1 and 2");
                assertFalse(walk(f).targets().get(0).available(), "control: target 1 is not available");
                List<SpotlightOverlay.Lit> lit = overlay.lit();
                assertEquals(1, lit.size(), "only the available target is lit");
                assertEquals("topology:node:priceListener", lit.get(0).target());
                assertEquals(2, lit.get(0).n(), "the overlay must draw the number the strip and context state: " + lit);
            });
        }
    }

    @Test
    @DisplayName("R5: a record the step's filter hides is not SHOWN, and a detail target that depends on it is not available")
    void aHiddenRecordIsNotClaimedAsShown(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var hidden = Map.of("view", Map.of("tab", "summary", "record", 1, "filter", Map.of("dimensions", List.of())),
                    "targets", List.of(target("detail", "record one claim")));
            call(f, "walk", Map.of("name", "DEMO_hidden", "steps", List.of(hidden)));
            call(f, "walk", Map.of("name", "DEMO_hidden", "play", true));
            await("the step decided", () -> walk(f).showing() && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                int[] selected = ((LogTablePanel) field(f.frame, "tablePanel")).selectedModelRows();
                assertEquals(0, selected.length, "control: the real selection is empty — record 1 is hidden");
                assertNotEquals("SHOWN", walk(f).phase(), "a hidden record must not be reported as shown");
                assertFalse(walk(f).targets().get(0).available(),
                        "the detail target depends on the requested record, which is not shown: " + walk(f).targets());
                assertTrue(walk(f).targets().get(0).reason().contains("record 1"), walk(f).targets().get(0).reason());
                assertFalse(overlay.isLit(), "and nothing is lit that would point at the wrong record");
            });
        }
    }

    @Test
    @DisplayName("R5 / §3.4: a step whose record is not in the log is refused whole — the previous step stays on screen")
    void aRefusedViewLeavesThePreviousStep(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var first = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "first")));
            var beyond = Map.of("view", Map.of("tab", "summary", "record", 999_999, "filter", Map.of("text", "DEMO_changed")),
                    "targets", List.of(target("status", "beyond")));
            call(f, "walk", Map.of("name", "DEMO_refused", "steps", List.of(first, beyond)));
            call(f, "walk", Map.of("name", "DEMO_refused", "play", true));
            await("step 1 lit", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
            var filter = (telamin.fluxtion.audit.analyser.analyser.filter.FilterState) field(f.frame, "filter");
            String textBefore = filter.text();

            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            await("step 2 decided", () -> walk(f).step() == 1 && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                assertEquals("NOT_SHOWN", walk(f).phase(), "the step is refused");
                assertTrue(walk(f).reason().contains("999999") || walk(f).reason().contains("999,999"), walk(f).reason());
                assertEquals(textBefore, filter.text(), "nothing of the refused view was applied");
                assertTrue(overlay.lit().stream().anyMatch(l -> l.target().equals("topology:node:priceListener")),
                        "the previous step stays on screen: " + overlay.lit());
            });
        }
    }

    @Test
    @DisplayName("R6: replacing the SHOWING walk is decided by the session — it ends, and nothing of either version is left lit")
    void replacingTheShowingWalkIsTheSessionsDecision(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var structural = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "original")));
            call(f, "walk", Map.of("name", "DEMO_edit", "steps", List.of(structural, structural)));
            call(f, "walk", Map.of("name", "DEMO_edit", "play", true));
            await("original shown", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());

            call(f, "walk", Map.of("name", "DEMO_edit", "steps", List.of(Map.of("targets", List.of(target("status", "replacement"))))));

            await("the session decided", () -> !walk(f).showing() || walk(f).count() == 1);
            onEdt(() -> {
                assertFalse(walk(f).showing(), "a replaced definition ends its showing: published count=" + walk(f).count());
                assertTrue(walk(f).reason().contains("changed"), walk(f).reason());
                assertFalse(overlay.lit().stream().anyMatch(l -> "original".equals(l.caption())),
                        "the old version's caption is not left lit: " + overlay.lit());
            });
        }
    }

    @Test
    @DisplayName("R6: a rename keeps the frozen version showing under its new name; a delete ends it")
    void renameKeepsItAndDeleteEndsIt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var one = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "one")));
            var two = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:quotePublisher", "two")));
            call(f, "walk", Map.of("name", "DEMO_a", "steps", List.of(one, two)));
            call(f, "walk", Map.of("name", "DEMO_a", "play", true));
            await("shown", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());

            call(f, "walk", Map.of("name", "DEMO_a", "rename", "DEMO_b"));
            onEdt(() -> {
                assertTrue(walk(f).showing(), "a rename does not change what is shown");
                assertEquals("DEMO_b", walk(f).walk(), "the showing walk carries its new name");
            });
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            await("step 2 of the frozen version", () -> walk(f).step() == 1 && "SHOWN".equals(walk(f).phase()));

            call(f, "walk", Map.of("name", "DEMO_b", "delete", true));
            onEdt(() -> assertFalse(walk(f).showing(), "a deleted walk is not left showing"));
        }
    }

    @Test
    @DisplayName("R7: while a walk is showing, a refused play of it (step 99) is reported as refused, not as success")
    void aRefusedPlayIsReportedAsRefused(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var one = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "one")));
            call(f, "walk", Map.of("name", "DEMO_edit", "steps", List.of(one)));
            call(f, "walk", Map.of("name", "DEMO_edit", "play", true));
            await("shown", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());

            var result = new java.util.concurrent.atomic.AtomicReference<telamin.fluxtion.audit.analyser.analyser.llm.ActionResult>();
            onEdt(() -> result.set(f.ex.render("walk", new java.util.LinkedHashMap<>(Map.of("name", "DEMO_edit", "play", true, "step", 99)))));
            assertFalse(result.get().ok(), "the node refused step 99; the reply must say so: " + result.get().toMap());
            assertTrue(String.valueOf(result.get().error()).contains("no step 99"), String.valueOf(result.get().error()));
            onEdt(() -> assertTrue(walk(f).showing(), "and the walk that was showing keeps showing"));

            var again = new java.util.concurrent.atomic.AtomicReference<telamin.fluxtion.audit.analyser.analyser.llm.ActionResult>();
            onEdt(() -> again.set(f.ex.render("walk", new java.util.LinkedHashMap<>(Map.of("name", "DEMO_edit", "play", true, "step", 1)))));
            assertTrue(again.get().ok(), "a valid replay of the showing walk is accepted: " + again.get().toMap());
        }
    }

    @Test
    @DisplayName("R1: when the session's identity becomes UNVERIFIED, a record target is re-resolved UNRESOLVED, and not lit")
    void aDegradedIdentityConstrainsTheTargets(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session");
            var record = Map.of("view", Map.of("tab", "summary", "record", 0), "targets", List.of(target("records:row:0", "record claim")));
            call(f, "walk", Map.of("name", "DEMO_record", "steps", List.of(record)));
            call(f, "walk", Map.of("name", "DEMO_record", "play", true));
            await("record lit", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
            long ticket = walk(f).ticket();

            onEdt(() -> session.post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved(
                    session.snapshot().logGeneration(), "UNVERIFIED", "DEMO identity probe")));
            await("re-resolved", () -> walk(f).ticket() > ticket && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                assertEquals("UNVERIFIED", session.snapshot().logIdentity(), "control: the session's verdict moved");
                var t = walk(f).targets().get(0);
                assertEquals("UNRESOLVED", t.state(), "a record read from a file that changed cannot be certified: " + t);
                assertFalse(t.available(), "so it is not available: " + t);
                assertFalse(overlay.lit().stream().anyMatch(l -> l.target().equals("records:row:0")),
                        "and not lit: " + overlay.lit());
            });
        }
    }
}
