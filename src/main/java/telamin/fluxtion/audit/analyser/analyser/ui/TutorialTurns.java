package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.List;

/**
 * M74 P2 (spec §5.5; owner decision 2026-10-17, the pop-up cut): under a recorded tutorial — a walk with a conversation —
 * a catalogue question does not draft for any model. It REVEALS the next recorded user turn instead, by playing the step
 * whose {@code through} reaches it: no model is contacted, no process starts, nothing is sent. Pure: it reads the
 * snapshot's walk and says which step that is.
 */
public final class TutorialTurns {

    private TutorialTurns() {
    }

    /** The words when the recorded conversation has no further turn. */
    public static final String NO_MORE = "The recorded conversation has no further turn — end the walk to ask yourself";

    /** The next recorded user turn and the step that reveals it. */
    public record NextTurn(String label, String text, int step) {
    }

    /** Whether the showing walk carries a conversation: then a catalogue question is the tutorial's, never a model's. */
    public static boolean tutorial(WalkPlaybackState w) {
        return w != null && w.showing() && w.definition() != null && w.definition().conversation() != null
                && w.definition().conversation().supported() && !w.definition().conversation().turns().isEmpty();
    }

    /** The first user turn after the current step's {@code through}, and the step whose {@code through} reveals it. */
    public static NextTurn next(WalkPlaybackState w) {
        if (!tutorial(w)) return null;
        WalkSpec walk = w.definition();
        WalkSpec.Conversation c = walk.conversation();
        List<WalkSpec.Step> steps = walk.steps();
        int current = Math.max(0, Math.min(w.step(), steps.size() - 1));
        String through = steps.get(current).through();
        int from = through == null ? -1 : c.indexOf(through);
        for (int t = from + 1; t < c.turns().size(); t++) {
            WalkSpec.Turn turn = c.turns().get(t);
            if (!"user".equals(turn.role())) continue;
            for (int s = current + 1; s < steps.size(); s++) {
                String th = steps.get(s).through();
                if (th != null && c.indexOf(th) >= t) return new NextTurn(c.label(), turn.text(), s);
            }
            return null;
        }
        return null;
    }
}
