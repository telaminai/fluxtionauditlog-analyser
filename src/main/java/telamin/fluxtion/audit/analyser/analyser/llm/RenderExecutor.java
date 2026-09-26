package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.Map;

/**
 * The seam for <b>render</b> verbs (filter / graph / goto / flag) that mutate the UI — implemented in the
 * ui layer and marshalled to the EDT there, so the transport-agnostic {@link ActionDispatcher} stays
 * UI-free (spec-assistant-actions §8). Query verbs never reach this; render verbs route here when present.
 */
public interface RenderExecutor {

    /** Apply a render {@code action} with its {@code params}; return the echo (or a structured error). */
    ActionResult render(String action, Map<String, Object> params);

    /**
     * M68.5 (D-E6): what changed about the open log's file, observed now, before a record-reading verb is served —
     * or null when no change was observed. A change that {@code suspendsReads()} refuses the verb; one whose bytes are
     * retained labels the reply. The default observes nothing, for executors with no file behind them.
     */
    default telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity readIdentity() {
        return null;
    }
}
