package telamin.fluxtion.audit.analyser.analyser.session.node;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one node the driver reads. Decisions push effect requests here; the driver drains them
 * <b>after</b> {@code onEvent} returns and performs them outside Fluxtion dispatch.
 *
 * <p>This exists so that no graph node ever calls Swing, the filesystem or {@code onEvent}. Letting a
 * node reach an adapter directly would recreate the hidden orchestration M44 removes, and letting one
 * call {@code onEvent} would make re-entrancy part of the model by accident.
 *
 * <p>It has no event handlers of its own: it is a collaborator, reached through a
 * {@code @PushReference} so that the event wave visits the decision <em>before</em> the queue. That
 * annotation is what makes the emitted GraphML show effects descending <b>from</b> the decision rather
 * than feeding into it — the picture M44 §10 asks a reader to be able to check.
 */
public class EffectQueue {

    /** Node-local and not final: see the comment in {@link OperationGate}. */
    private List<SessionEffects> pending = new ArrayList<>();

    /** Called by a decision node during dispatch. Appending to a list is not an effect. */
    public void request(SessionEffects effect) {
        pending.add(effect);
    }

    /**
     * Take everything requested by the dispatch that just finished, and empty the queue.
     *
     * @return an immutable snapshot; the driver performs these in order
     */
    public List<SessionEffects> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<SessionEffects> batch = Collections.unmodifiableList(new ArrayList<>(pending));
        pending.clear();
        return batch;
    }

    /** For assertions and for the "a request produced no effects" case. */
    public boolean isEmpty() {
        return pending.isEmpty();
    }
}
