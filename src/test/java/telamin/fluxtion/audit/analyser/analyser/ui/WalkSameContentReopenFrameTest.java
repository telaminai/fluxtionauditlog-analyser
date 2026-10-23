package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.awaitLoaded;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * #179 on a REAL frame, both ways. A walk saved while the log was followed, then the IDENTICAL file reopened cold — a
 * new generation, the same bytes, one more record served (Follow held the unterminated last record back) — is current:
 * no "saved against a different run" and its record target lit CURRENT. Change one record and reopen: historical, as the
 * M68/M69 integrity rule requires, and the changed record's target is HISTORICAL and not lit.
 */
class WalkSameContentReopenFrameTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> savedWalk(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Object> out = new AtomicReference<>();
        onEdt(() -> out.set(((Map<String, Object>) render(f.ex, "context", Map.of("sections", List.of("project")))
                .get("context")).get("walks")));
        return ((List<Map<String, Object>>) ((Map<String, Object>) out.get()).get("saved")).get(0);
    }

    static SessionEvents.WalkTargetState played(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        onEdt(() -> f.ex.render("walk", new LinkedHashMap<>(Map.of("name", "tour", "play", true))));
        WalkPlaybackFrameTest.await("the step is prepared", () -> {
            var s = WalkPlaybackFrameTest.walk(f);
            return s.showing() && !"PREPARING".equals(s.phase()) && !s.targets().isEmpty();
        });
        var target = WalkPlaybackFrameTest.walk(f).targets().get(0);
        onEdt(() -> f.ex.render("walk", new LinkedHashMap<>(Map.of("end", true))));
        WalkPlaybackFrameTest.await("the walk ended", () -> !WalkPlaybackFrameTest.walk(f).showing());
        return target;
    }

    static void reopenCold(AsyncOpenInterleavingFrameTest.Frame f, Path log) throws Exception {
        onEdt(() -> render(f.ex, "open", Map.of("close", "log")));
        awaitLoaded(f.ex);
        EvidenceCaptureFrameTest.openLog(f, log);
    }

    @Test
    @DisplayName("★ #179: saved under Follow, the identical file reopened cold is CURRENT; one record changed and reopened is HISTORICAL")
    void sameContentIsCurrentAndAChangeIsHistorical(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.copy(EvidenceCaptureFrameTest.DEMO_LOG, Files.createDirectories(tmp.resolve("logs")).resolve("demo.yaml"));
        try (var f = EvidenceCaptureFrameTest.shown(tmp)) {
            EvidenceCaptureFrameTest.openLog(f, log);
            onEdt(() -> render(f.ex, "open", Map.of("follow", true)));
            awaitLoaded(f.ex);
            AtomicReference<Map<String, Object>> saved = new AtomicReference<>();
            onEdt(() -> saved.set(f.ex.render("walk", new LinkedHashMap<>(Map.of("name", "tour", "steps", List.of(
                    Map.of("caption", "the fourth quote", "targets",
                            List.of(Map.of("target", "records:row:3", "caption", "this quote"))))))).toMap()));
            assertEquals(Boolean.TRUE, saved.get().get("ok"), String.valueOf(saved.get()));
            assertNull(savedWalk(f).get("warning"), "control: current against the read it was saved on");
            onEdt(() -> render(f.ex, "open", Map.of("follow", false)));

            reopenCold(f, log);
            assertNull(savedWalk(f).get("warning"),
                    "★ the identical file reopened is current (red before #179: 'saved against a different run')");
            var same = played(f);
            assertEquals("CURRENT", same.state(), "and its record target is current: " + same);
            assertTrue(same.available(), "and lit: " + same);

            String text = Files.readString(log);
            assertTrue(text.contains("logTime: 1767258000180"), "control: record 3 is the one changed");
            Files.writeString(log, text.replace("logTime: 1767258000180", "logTime: 1767258000181"));
            reopenCold(f, log);
            assertNotNull(savedWalk(f).get("warning"), "★ a changed file is historical — the integrity rule stands");
            var changed = played(f);
            assertEquals("HISTORICAL", changed.state(), "the changed record is historical: " + changed);
            assertFalse(changed.available(), "and never pointed at: " + changed);
        }
    }
}
