package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/**
 * Evidence bundle capture, as the {@code evidenceCapture} node decided it. Published in the {@link SessionSnapshot}:
 * {@code context.capture} and the {@code report {bundle}} echo render THIS, and decide nothing.
 *
 * @param phase    {@code IDLE}, {@code WRITING}, {@code WRITTEN} or {@code REFUSED}
 * @param path     the bundle's path, while writing or once written
 * @param identity {@code sha256:} of the manifest, once written
 * @param reason   why a capture was refused, by name; "" otherwise
 * @param lines    what the author must see: left out, dangling, redacted, excerpt
 * @param answer   the node's answer to the last request that carried an id
 */
public record CaptureState(String phase, long ticket, String path, String identity, String reason, List<String> lines,
                           Answer answer) {

    /** Whether request {@code request} was accepted, and if not, why. */
    public record Answer(long request, boolean accepted, String reason) {
        public static final Answer NONE = new Answer(0, false, "");

        public Answer {
            reason = reason == null ? "" : reason;
        }
    }

    public static final CaptureState IDLE = new CaptureState("IDLE", 0, null, null, "", List.of(), Answer.NONE);

    public CaptureState {
        phase = phase == null ? "IDLE" : phase;
        reason = reason == null ? "" : reason;
        lines = List.copyOf(lines == null ? List.of() : lines);
        answer = answer == null ? Answer.NONE : answer;
    }
}
