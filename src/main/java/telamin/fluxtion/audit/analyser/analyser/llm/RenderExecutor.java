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
}
