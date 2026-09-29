package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/**
 * The onboard assistant as the {@code assistantLoop} node decided it (spec-onboard-assistant-journeys.md §4–§5, OA-1).
 * Published in the {@link SessionSnapshot}: the assistant panel, both of its hosts and {@code context.assistant} render
 * THIS and decide nothing.
 *
 * <p><b>No text and no credential.</b> Entries name transcript ids, kinds and statuses. The words live in the
 * append-only {@code AssistantTranscript}, which is outside the graph, so neither a question nor an answer ever enters
 * the session audit record, and a snapshot never grows with the conversation's bodies.
 *
 * @param conversation the conversation id: New chat, a handoff and a workspace change make a new one
 * @param ticket       the current turn ticket; any adapter answer naming another is stale
 * @param phase        {@code IDLE}, {@code PREPARING}, {@code REQUESTING}, {@code RUNNING_ACTION}, {@code COMPLETE},
 *                     {@code CANCELLED}, {@code SUPERSEDED}, {@code LIMIT_REACHED} or {@code FAILED}
 * @param round        the provider round within the turn (1-based; 0 before the first)
 * @param actionsRun   actions dispatched in this turn
 * @param runningVerb  the verb of the action being run, or null
 * @param entries      the visible and hidden entries of this conversation, in order
 * @param docked       true when the assistant is in its side tab; false when it is in its own window
 * @param frozen       the workspace changed since this thread's last turn: it is kept for reading, and Send needs a
 *                     new chat, so old history is never sent as if it described the new workspace
 * @param basis        a short label of the workspace this thread's turns were made against
 * @param reason       why the last request was refused, why the turn ended, or ""
 * @param answer       the answer to the last send request that carried an id
 */
public record AssistantState(long conversation, long ticket, String phase, int round, int actionsRun, String runningVerb,
                             List<Entry> entries, boolean docked, boolean frozen, String basis, String reason,
                             Answer answer) {

    /** Kinds of entry. VISIBLE kinds are rendered; hidden ones exist only for the provider history. */
    public static final String USER = "USER";
    public static final String ANSWER = "ANSWER";
    public static final String ACTION = "ACTION";
    public static final String NOTE = "NOTE";
    /** Hidden: the composed first-turn content (manifest, record context, question) actually sent. */
    public static final String PROMPT = "PROMPT";

    /**
     * One entry.
     *
     * @param id     the transcript key of its text; 0 for a NOTE, whose words are {@code detail}
     * @param kind   {@link #USER}, {@link #ANSWER}, {@link #ACTION}, {@link #NOTE} or {@link #PROMPT}
     * @param turn   the turn it belongs to
     * @param status for ACTION: {@code REQUESTED}, {@code OK}, {@code REFUSED}, {@code NOT_RUN} (a budget or a cancel
     *               stopped it); for ANSWER: {@code ACCEPTED}; otherwise ""
     * @param verb   for ACTION, the verb; otherwise ""
     * @param detail for a NOTE, the node's own words (a limit, a cancel, a failure) — never model or user text
     * @param result for an ACTION that ran, the transcript key of its actual result JSON; 0 otherwise
     */
    public record Entry(long id, String kind, long turn, String status, String verb, String detail, long result) {
        public Entry {
            status = status == null ? "" : status;
            verb = verb == null ? "" : verb;
            detail = detail == null ? "" : detail;
        }

        public boolean visible() {
            return !PROMPT.equals(kind);
        }
    }

    /** Whether send request {@code request} was accepted, and if not, why. */
    public record Answer(long request, boolean accepted, String reason) {
        public static final Answer NONE = new Answer(0, false, "");

        public Answer {
            reason = reason == null ? "" : reason;
        }
    }

    public static final AssistantState IDLE = new AssistantState(1, 0, "IDLE", 0, 0, null, List.of(), true, false, "", "",
            Answer.NONE);

    public AssistantState {
        phase = phase == null ? "IDLE" : phase;
        entries = List.copyOf(entries == null ? List.of() : entries);
        basis = basis == null ? "" : basis;
        reason = reason == null ? "" : reason;
        answer = answer == null ? Answer.NONE : answer;
    }

    /** A turn is in progress: Send is not allowed, Cancel is. */
    public boolean busy() {
        return "PREPARING".equals(phase) || "REQUESTING".equals(phase) || "RUNNING_ACTION".equals(phase);
    }

    /** The entries a person sees, in order. */
    public List<Entry> visibleEntries() {
        return entries.stream().filter(Entry::visible).toList();
    }
}
