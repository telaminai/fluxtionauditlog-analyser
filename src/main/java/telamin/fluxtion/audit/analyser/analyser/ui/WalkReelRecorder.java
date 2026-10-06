package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkReel;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import javax.swing.Timer;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Issue #82 — plays a saved walk and photographs each step, so the walk can be sent to somebody who does not have
 * the analyser.
 *
 * <p><b>It decides nothing about the walk.</b> It asks for a step through the same {@code play} entrance the
 * Reports tab and the verb use, reads the step it got from the published {@link WalkPlaybackState}, and captures
 * what is on the screen. Which step is current, whether it is settled, and when the walk ends are the
 * {@code walkPlayback} node's, as they are for every other surface (CLAUDE.md rule 9). The counter here is a
 * REQUEST, not a second copy of the session's step: every frame is stamped from the snapshot.
 *
 * <p><b>When a step has settled.</b> Leaving {@code PREPARING} is necessary and not sufficient. The node marks a
 * step shown and only THEN asks the frame to light it, and a step pointing at Java source is read off the event
 * thread (#72) and lights later still — so a capture taken on the first non-{@code PREPARING} tick photographs a
 * step whose spotlights are not there yet. The second condition is therefore the frame's own
 * {@link Frame#lightingPending()}: the walk's lighting is in flight. Both, polled on a Swing timer, exactly as
 * {@link WalkPresenter} waits for a chart — never a busy-wait, and never blocking the event thread.
 */
final class WalkReelRecorder {

    /** How often the settle conditions are re-read. The same cadence {@link WalkPresenter} polls a chart at. */
    static final int POLL_MS = 50;
    /**
     * How long one step may take to arrive and settle. Above {@link WalkPresenter#PREPARE_BOUND_MS} (5 s, a chart's
     * bound) plus the 10 s a Java source preparation is allowed, so a step that hits its OWN bound still reports its
     * own reason rather than being cut short by this one.
     */
    static final int STEP_BOUND_MS = 17_000;

    /** What the recorder needs from the frame; every call is a primitive, none is a decision. */
    interface Frame {
        WalkPlaybackState playback();

        /** True while the walk's own spotlight is being prepared or applied — the step is not photographable yet. */
        boolean lightingPending();

        /** Ask for {@code step} (0-based) of {@code walk}; null, or why the request could not be made. */
        String play(String walk, int step);

        void end(String reason);

        /** The analyser's window as the {@code screenshot} verb composes it: content, menus and the live spotlight. */
        BufferedImage compose();
    }

    /** What a finished recording produced: the frames, or why there are none. */
    record Recording(List<WalkReel.Frame> frames, String error) {
        boolean ok() {
            return error == null;
        }
    }

    private final Frame frame;
    private Timer timer;
    private WalkSpec walk;
    private Consumer<Recording> done;
    private List<WalkReel.Frame> frames;
    private int wanted;
    private long deadline;

    WalkReelRecorder(Frame frame) {
        this.frame = frame;
    }

    boolean recording() {
        return timer != null;
    }

    /**
     * Start recording {@code walk}. On the event thread; {@code onDone} is called there too, once, when every step
     * has been captured or the recording was given up on.
     */
    void start(WalkSpec walk, Consumer<Recording> onDone) {
        if (recording()) {
            onDone.accept(new Recording(List.of(), "a reel is already being recorded"));
            return;
        }
        if (walk.steps().isEmpty()) {
            onDone.accept(new Recording(List.of(), "the walk '" + walk.name() + "' has no steps"));
            return;
        }
        this.walk = walk;
        this.done = onDone;
        this.frames = new ArrayList<>();
        this.wanted = 0;
        String refused = frame.play(walk.name(), 0);
        if (refused != null) {
            finish(new Recording(List.of(), refused));
            return;
        }
        this.deadline = System.currentTimeMillis() + STEP_BOUND_MS;
        timer = new Timer(POLL_MS, e -> tick());
        timer.setRepeats(true);
        timer.start();
    }

    /** Give up on a recording in flight — a project transition, a new log, a person closing the window. */
    void cancel(String why) {
        if (recording()) finish(new Recording(List.of(), why));
    }

    private void tick() {
        WalkPlaybackState s = frame.playback();
        if (!s.showing() || !walk.name().equals(s.walk())) {
            finish(new Recording(List.of(), "the walk ended before the reel was finished"
                                            + (s.reason() == null || s.reason().isBlank() ? "" : ": " + s.reason())));
            return;
        }
        boolean settled = s.step() == wanted && !"PREPARING".equals(s.phase()) && !frame.lightingPending();
        if (!settled) {
            if (System.currentTimeMillis() > deadline) {
                finish(new Recording(List.of(), "step " + (wanted + 1) + " of '" + walk.name()
                                                + "' did not settle within " + STEP_BOUND_MS / 1000 + "s"));
            }
            return;
        }
        frames.add(capture(s));
        wanted++;
        if (wanted >= s.count()) {
            List<WalkReel.Frame> taken = List.copyOf(frames);
            frame.end("the reel finished");
            finish(new Recording(taken, null));
            return;
        }
        String refused = frame.play(walk.name(), wanted);
        if (refused != null) {
            finish(new Recording(List.of(), refused));
            return;
        }
        deadline = System.currentTimeMillis() + STEP_BOUND_MS;
    }

    /** One frame, stamped from the snapshot — never from this class's own idea of where the walk is. */
    private WalkReel.Frame capture(WalkPlaybackState s) {
        WalkSpec definition = s.definition() == null ? walk : s.definition();
        int index = s.step();
        WalkSpec.Step step = index < definition.steps().size() ? definition.steps().get(index) : null;
        List<String> captions = new ArrayList<>();
        for (SessionEvents.WalkTargetState t : s.targets()) {
            String caption = t.caption() == null || t.caption().isBlank() ? t.target() : t.caption();
            captions.add(t.available() ? caption
                    : caption + " — not shown: " + (t.reason() == null || t.reason().isBlank() ? t.state() : t.reason()));
        }
        BufferedImage img = frame.compose();
        byte[] png = img == null ? new byte[0] : Png.bytes(img);
        return new WalkReel.Frame(index + 1, step == null ? "" : step.caption(), captions, s.phase(), s.reason(),
                img == null ? 0 : img.getWidth(), img == null ? 0 : img.getHeight(),
                revealedAt(definition, index), png);
    }

    /** The turns this step adds to what the previous one had already revealed. */
    static List<WalkSpec.Turn> revealedAt(WalkSpec walk, int step) {
        List<WalkSpec.Turn> upTo = WalkConversation.prefix(walk, step);
        List<WalkSpec.Turn> before = WalkConversation.prefix(walk, step - 1);
        return upTo.size() <= before.size() ? List.of() : List.copyOf(upTo.subList(before.size(), upTo.size()));
    }

    private void finish(Recording result) {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        Consumer<Recording> callback = done;
        done = null;
        walk = null;
        frames = null;
        if (callback != null) callback.accept(result);
    }
}
