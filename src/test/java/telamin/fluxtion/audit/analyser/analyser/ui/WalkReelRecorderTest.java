package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #82 — the recorder's settle rules, driven off a scripted playback state. No display: what is asserted here
 * is WHEN a frame is taken, which is not a pixel question.
 *
 * <p>Leaving {@code PREPARING} is necessary and not sufficient. The node marks a step shown and only then asks the
 * frame to light it, and a Java-source step is read off the event thread (#72) and lights later still — so both
 * conditions have their own case, and each fails on its own.
 */
class WalkReelRecorderTest {

    private static WalkSpec walk(int steps) {
        List<WalkSpec.Step> list = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            list.add(new WalkSpec.Step("step " + (i + 1), WalkSpec.View.NONE,
                    List.of(new WalkSpec.Target("tab:records", "here", WalkSpec.Basis.NONE))));
        }
        return new WalkSpec("tour", "A tour", WalkSpec.AUTHOR_PERSON, "2026-09-30T09:00:00Z",
                "2026-09-30T09:00:00Z", null, List.of(), list, Map.of());
    }

    /** A playback the test scripts: each step reports PREPARING, then shown-with-lighting-pending, then settled. */
    private static final class Script implements WalkReelRecorder.Frame {
        final int count;
        final int preparingTicks;
        final int lightingTicks;
        int step = -1;                 // no step requested yet
        int ticksOnStep;
        boolean lighting;
        boolean endedWith;
        /** What was true at each capture — the whole point of this test. */
        final List<Boolean> capturedWhilePreparing = new ArrayList<>();
        final List<Boolean> capturedWhileLighting = new ArrayList<>();
        final List<Integer> capturedSteps = new ArrayList<>();

        Script(int count, int preparingTicks, int lightingTicks) {
            this.count = count;
            this.preparingTicks = preparingTicks;
            this.lightingTicks = lightingTicks;
        }

        private boolean preparing() {
            return ticksOnStep < preparingTicks;
        }

        @Override public WalkPlaybackState playback() {
            if (step < 0) return WalkPlaybackState.IDLE;
            ticksOnStep++;
            lighting = !preparing() && ticksOnStep < preparingTicks + lightingTicks;
            return new WalkPlaybackState("tour", step, count, preparing() ? "PREPARING" : "SHOWN", "", 1,
                    List.of(new SessionEvents.WalkTargetState(1, "tab:records", "here", "CURRENT", true, "")),
                    Map.of(), walk(count), WalkPlaybackState.Answer.NONE, step);
        }

        @Override public boolean lightingPending() {
            return lighting;
        }

        @Override public String play(String walk, int at) {
            step = at;
            ticksOnStep = 0;
            lighting = false;
            return null;
        }

        @Override public void end(String reason) {
            endedWith = true;
        }

        @Override public BufferedImage compose() {
            capturedWhilePreparing.add(preparing());
            capturedWhileLighting.add(lighting);
            capturedSteps.add(step);
            return new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        }
    }

    /** Record on the event thread, as the frame does, and wait for the one answer. */
    private static WalkReelRecorder.Recording record(WalkReelRecorder.Frame frame, WalkSpec walk) throws Exception {
        var answers = new ArrayBlockingQueue<WalkReelRecorder.Recording>(1);
        var recorder = new WalkReelRecorder(frame);
        SwingUtilities.invokeAndWait(() -> recorder.start(walk, answers::add));
        WalkReelRecorder.Recording out = answers.poll(30, TimeUnit.SECONDS);
        assertNotNull(out, "the recorder answered");
        return out;
    }

    @Test
    @DisplayName("#82: a step is not photographed while it is still PREPARING")
    void aStepIsNotPhotographedWhilePreparing() throws Exception {
        Script s = new Script(3, 4, 0);
        WalkReelRecorder.Recording out = record(s, walk(3));
        assertTrue(out.ok(), out.error());
        assertEquals(List.of(false, false, false), s.capturedWhilePreparing,
                "no frame was taken before the session said the step had settled");
        assertEquals(3, out.frames().size());
        assertTrue(out.frames().stream().noneMatch(f -> "PREPARING".equals(f.state())),
                "and no frame carries PREPARING onto the page");
    }

    @Test
    @DisplayName("#82: a step is not photographed while its spotlights are still being lit")
    void aStepIsNotPhotographedWhileItsLightingIsInFlight() throws Exception {
        Script s = new Script(2, 2, 3);
        WalkReelRecorder.Recording out = record(s, walk(2));
        assertTrue(out.ok(), out.error());
        assertEquals(List.of(false, false), s.capturedWhileLighting,
                "a Java-source step lights after the node calls it shown; the frame waits for that too");
    }

    @Test
    @DisplayName("#82: each frame is stamped from the snapshot, in step order, and the walk is ended afterwards")
    void everyStepIsCapturedOnceInOrder() throws Exception {
        Script s = new Script(4, 1, 1);
        WalkReelRecorder.Recording out = record(s, walk(4));
        assertTrue(out.ok(), out.error());
        assertEquals(List.of(0, 1, 2, 3), s.capturedSteps, "each step once, in order");
        assertEquals(List.of(1, 2, 3, 4), out.frames().stream().map(f -> f.number()).toList(),
                "numbered from the snapshot's step, not from a counter kept here");
        assertEquals("step 3", out.frames().get(2).caption());
        assertEquals(List.of("here"), out.frames().get(2).targets());
        assertEquals(4, out.frames().get(0).width());
        assertTrue(s.endedWith, "the walk does not stay on the person's screen after the reel is recorded");
    }

    @Test
    @DisplayName("#82: a walk that ends under the recorder produces no reel, and says why")
    void aWalkThatEndsAbandonsTheReel() throws Exception {
        WalkReelRecorder.Recording out = record(new WalkReelRecorder.Frame() {
            @Override public WalkPlaybackState playback() {
                return WalkPlaybackState.IDLE;                  // ended by a click, a new log, anything
            }
            @Override public boolean lightingPending() { return false; }
            @Override public String play(String walk, int step) { return null; }
            @Override public void end(String reason) { }
            @Override public BufferedImage compose() { return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB); }
        }, walk(2));
        assertFalse(out.ok());
        assertTrue(out.error().contains("the walk ended before the reel was finished"), out.error());
        assertTrue(out.frames().isEmpty(), "a half reel is not offered as a whole one");
    }

    @Test
    @DisplayName("#82: a refused play is the reel's refusal, not a silent short recording")
    void aRefusedPlayRefusesTheReel() throws Exception {
        WalkReelRecorder.Recording out = record(new WalkReelRecorder.Frame() {
            @Override public WalkPlaybackState playback() { return WalkPlaybackState.IDLE; }
            @Override public boolean lightingPending() { return false; }
            @Override public String play(String walk, int step) { return "no walk called 'tour'"; }
            @Override public void end(String reason) { }
            @Override public BufferedImage compose() { return null; }
        }, walk(1));
        assertFalse(out.ok());
        assertEquals("no walk called 'tour'", out.error());
    }

    @Test
    @DisplayName("#82: a walk with no steps is refused before anything is played")
    void aWalkWithNoStepsIsRefused() throws Exception {
        WalkReelRecorder.Recording out = record(new Script(0, 0, 0), walk(0));
        assertFalse(out.ok());
        assertTrue(out.error().contains("has no steps"), out.error());
    }
}
