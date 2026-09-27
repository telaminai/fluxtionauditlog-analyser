package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;
import java.util.Map;

/**
 * M69 — the walk the session is showing, as the {@code walkPlayback} node decided it (the M69 walk spec, §3.8).
 * Published in the {@link SessionSnapshot}: the strip, the Reports tab and {@code context.walks} render THIS, and
 * none of them decide anything about playback.
 *
 * @param walk      the showing walk, or null when none is
 * @param step      0-based index of the current step
 * @param count     the walk's step count
 * @param phase     {@code IDLE}, {@code PREPARING}, {@code SHOWN}, {@code PARTLY_SHOWN} or {@code NOT_SHOWN}
 * @param reason    why the step is not (fully) shown, or why the last walk ended; "" when there is nothing to say
 * @param ticket    the node's current ticket: any adapter answer naming another is stale
 * @param targets   the current step's targets and their states
 * @param lastShown walk name → the last step shown (0-based), for Play from step N — session memory, never saved
 */
public record WalkPlaybackState(String walk, int step, int count, String phase, String reason, long ticket,
                                List<SessionEvents.WalkTargetState> targets, Map<String, Integer> lastShown) {

    public static final WalkPlaybackState IDLE = new WalkPlaybackState(null, 0, 0, "IDLE", "", 0, List.of(), Map.of());

    public WalkPlaybackState {
        phase = phase == null ? "IDLE" : phase;
        reason = reason == null ? "" : reason;
        targets = List.copyOf(targets == null ? List.of() : targets);
        lastShown = Map.copyOf(lastShown == null ? Map.of() : lastShown);
    }

    public boolean showing() {
        return walk != null;
    }
}
