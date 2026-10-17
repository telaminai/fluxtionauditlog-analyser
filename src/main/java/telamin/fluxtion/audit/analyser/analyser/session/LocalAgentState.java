package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/**
 * The local agent (Claude Code) as the {@code localAgentSession} node decided it (spec-claude-code-provider.md §4.1).
 * Published in the {@link SessionSnapshot}; the Assistant tab renders THIS and decides nothing, and the action server
 * reads {@link #admittedGeneration()} to decide nothing either — it only compares.
 *
 * <p><b>No words, no account, no path, no session id.</b> Entries name transcript references, kinds and statuses; the
 * header is a reference into the adapter's bounded store, where the account's e-mail and organisation never arrive
 * (they are dropped at the parse boundary).
 *
 * @param mode               the assistant mode in force
 * @param conversation       the conversation counter: New chat makes a new one
 * @param generation         the process generation: every spawn makes a new one
 * @param phase              {@code OFF}, {@code SPAWNING}, {@code HANDSHAKE}, {@code BINDING}, {@code READY}, {@code BUSY}
 *                           or {@code STOPPING}
 * @param verdict            the last refusal or handshake verdict by name ({@code NOT_LOGGED_IN}, {@code NONCE_MISMATCH},
 *                           {@code TOOLS_MISMATCH}, {@code BUSY}, {@code SLASH_BLOCKED}, …), or ""
 * @param reason             the node's own words for the last refusal, end or failure, or ""
 * @param openTicket         the turn in progress, or 0
 * @param admittedGeneration the generation whose hosted calls are admitted (bound AND posture checked), or 0
 * @param headerRef          the header record in the store, 0 before {@code initialize}
 * @param headerRevision     bumped whenever the header record changes (a turn's init names the model and CLI version)
 * @param detailRef          the words of a spawn failure (where {@code claude} was searched for), or 0
 * @param stderrRef          the last redacted stderr line of an exited process, or 0
 * @param bindingElapsedMs   how long the bridge has been connecting, for "connecting to the analyser bridge…"
 * @param spentMicros        the session total across this machine's conversations so far (§4.6)
 * @param budgetMicros       the budget setting the last start was made under; 0 when no limit is set
 * @param costUnderReported  a turn was interrupted, so the total may be low ("at least")
 * @param newChatOffered     the last refusal is answered by New chat (a posture change, {@code /clear})
 * @param resumable          a conversation exists that the next spawn resumes
 * @param pendingDraft       a draft accepted before the process was ready, sent when it is
 * @param entries            the conversation's entries, in order
 * @param answer             the answer to the last request that carried an id
 * @param posture            the posture the current (or last) process was started in (§3; P1b)
 * @param personServers      in the full posture, how many of the person's own MCP servers the CLI reports (every
 *                           server but the hosted bridge); 0 in the default posture
 * @param billing            a Claude plan or an API key (owner, 2026-10-17): the header shows a dollar figure only for
 *                           an API key
 * @param armedWhy           P2 (§5.2 item 6): Start is ARMED, with the bundle key of its words ({@code armed.ASK}, …), or
 *                           null; nothing ever starts because Start is armed
 * @param consentPosture     P2 (owner request 2026-10-17): the posture the person's settings name, as last observed in a
 *                           consent fact — null while starting Claude Code is switched off or not yet observed. With
 *                           {@link #posture} it decides the full-posture cue ({@link #fullCue()})
 */
public record LocalAgentState(AssistantMode mode, long conversation, long generation, String phase, String verdict,
                              String reason, long openTicket, long admittedGeneration, long headerRef,
                              long headerRevision, long detailRef, long stderrRef, long bindingElapsedMs,
                              long spentMicros, long budgetMicros, boolean costUnderReported, boolean newChatOffered,
                              boolean resumable, long pendingDraft, List<Entry> entries, Answer answer,
                              LocalAgentPosture posture, int personServers, SessionEvents.Billing billing,
                              String armedWhy, LocalAgentPosture consentPosture, Extra extra) {

    /**
     * M74 P2b's additions, in one component so the earlier shapes keep their constructors.
     *
     * @param setupDirRef        the set-up directory chosen for onboarding (§5.6 step 1), held for this run, by reference
     *                           into the frame's store (R6: no path in a fact or the node); 0 for none
     * @param setupDirShown      that directory as {@code …/name}, or null
     * @param unknownEvents      §5.2's diagnostics line: stream events of a kind this analyser does not know
     * @param unclassifiedCommands built-in commands the slash table does not classify
     * @param droppedLines       stdout lines over the size cap, dropped
     * @param turns              per answered turn (by ticket): its cost (micro-USD, -1 unknown) and time (ms, -1 unknown)
     * @param trimmed            entries trimmed from the start of this conversation (the node keeps 500)
     */
    public record Extra(long setupDirRef, String setupDirShown, int unknownEvents, int unclassifiedCommands, int droppedLines,
                        java.util.Map<Long, Turn> turns, int trimmed) {
        public Extra {
            turns = java.util.Map.copyOf(turns == null ? java.util.Map.of() : turns);
        }

        public static final Extra NONE = new Extra(0, null, 0, 0, 0, java.util.Map.of(), 0);

        /** Whether the diagnostics line shows (only when a count is non-zero). */
        public boolean diagnostics() {
            return unknownEvents > 0 || unclassifiedCommands > 0 || droppedLines > 0;
        }
    }

    /** One turn's cost and time, from its {@code result}: the cost is the difference of successive totals (§5.9). */
    public record Turn(long costMicros, long durationMs) {
    }

    /** The P2a shape: no P2b additions. */
    public LocalAgentState(AssistantMode mode, long conversation, long generation, String phase, String verdict,
                           String reason, long openTicket, long admittedGeneration, long headerRef,
                           long headerRevision, long detailRef, long stderrRef, long bindingElapsedMs,
                           long spentMicros, long budgetMicros, boolean costUnderReported, boolean newChatOffered,
                           boolean resumable, long pendingDraft, List<Entry> entries, Answer answer,
                           LocalAgentPosture posture, int personServers, SessionEvents.Billing billing,
                           String armedWhy, LocalAgentPosture consentPosture) {
        this(mode, conversation, generation, phase, verdict, reason, openTicket, admittedGeneration, headerRef,
                headerRevision, detailRef, stderrRef, bindingElapsedMs, spentMicros, budgetMicros, costUnderReported,
                newChatOffered, resumable, pendingDraft, entries, answer, posture, personServers, billing, armedWhy,
                consentPosture, Extra.NONE);
    }

    public static final String USER = "USER";
    public static final String ANSWER = "ANSWER";
    public static final String NOTE = "NOTE";

    /**
     * One entry: a person's sent turn ({@link #USER}, its draft reference), Claude's rendered answer ({@link #ANSWER},
     * its text reference and revision), or the node's own note ({@link #NOTE}, its words in {@code detail}).
     */
    public record Entry(long id, String kind, long ticket, String status, String detail, long revision,
                        long logGeneration) {
        public Entry {
            status = status == null ? "" : status;
            detail = detail == null ? "" : detail;
        }

        /**
         * An entry with no log generation recorded. P2 (§5.9): an ANSWER carries the log generation it was written
         * under, so the transcript shows its chips muted once the log has moved.
         */
        public Entry(long id, String kind, long ticket, String status, String detail, long revision) {
            this(id, kind, ticket, status, detail, revision, 0);
        }
    }

    /** Whether request {@code request} was accepted, and if not, why. */
    public record Answer(long request, boolean accepted, String reason) {
        public static final Answer NONE = new Answer(0, false, "");

        public Answer {
            reason = reason == null ? "" : reason;
        }
    }

    /** The P1b shape: nothing armed. */
    public LocalAgentState(AssistantMode mode, long conversation, long generation, String phase, String verdict,
                           String reason, long openTicket, long admittedGeneration, long headerRef,
                           long headerRevision, long detailRef, long stderrRef, long bindingElapsedMs,
                           long spentMicros, long budgetMicros, boolean costUnderReported, boolean newChatOffered,
                           boolean resumable, long pendingDraft, List<Entry> entries, Answer answer,
                           LocalAgentPosture posture, int personServers, SessionEvents.Billing billing) {
        this(mode, conversation, generation, phase, verdict, reason, openTicket, admittedGeneration, headerRef,
                headerRevision, detailRef, stderrRef, bindingElapsedMs, spentMicros, budgetMicros, costUnderReported,
                newChatOffered, resumable, pendingDraft, entries, answer, posture, personServers, billing, null, null);
    }

    /**
     * The full-posture cue (owner request, 2026-10-17): whether the person's own full Claude Code is RUNNING here, or is
     * switched ON and would run at the next Start, or neither. It follows the session's state, never the config file.
     */
    public enum FullCue { NONE, ON, RUNNING }

    public FullCue fullCue() {
        if (running()) return posture.isFull() ? FullCue.RUNNING : FullCue.NONE;
        return consentPosture != null && consentPosture.isFull() ? FullCue.ON : FullCue.NONE;
    }

    /** The posture the cue describes: the running one, else the one the settings name; null when there is no cue. */
    public LocalAgentPosture cuePosture() {
        return switch (fullCue()) {
            case RUNNING -> posture;
            case ON -> consentPosture;
            case NONE -> null;
        };
    }

    /** The stronger cue (owner request): the modes that ask least — {@code acceptEdits} and {@code auto}. */
    public boolean strongCue() {
        LocalAgentPosture p = cuePosture();
        return p != null && (p.fullMode() == LocalAgentPosture.FullMode.AUTO
                || p.fullMode() == LocalAgentPosture.FullMode.ACCEPT_EDITS);
    }

    public static final LocalAgentState IDLE = new LocalAgentState(AssistantMode.IN_APP, 1, 0, "OFF", "", "", 0, 0, 0, 0,
            0, 0, 0, 0, 0, false, false, false, 0, List.of(), Answer.NONE);

    /** The P1a shape: the default posture. */
    public LocalAgentState(AssistantMode mode, long conversation, long generation, String phase, String verdict,
                           String reason, long openTicket, long admittedGeneration, long headerRef,
                           long headerRevision, long detailRef, long stderrRef, long bindingElapsedMs,
                           long spentMicros, long budgetMicros, boolean costUnderReported, boolean newChatOffered,
                           boolean resumable, long pendingDraft, List<Entry> entries, Answer answer) {
        this(mode, conversation, generation, phase, verdict, reason, openTicket, admittedGeneration, headerRef,
                headerRevision, detailRef, stderrRef, bindingElapsedMs, spentMicros, budgetMicros, costUnderReported,
                newChatOffered, resumable, pendingDraft, entries, answer, LocalAgentPosture.ANALYSER_ASSISTANT, 0,
                SessionEvents.Billing.UNKNOWN);
    }

    public LocalAgentState {
        mode = mode == null ? AssistantMode.IN_APP : mode;
        phase = phase == null ? "OFF" : phase;
        verdict = verdict == null ? "" : verdict;
        reason = reason == null ? "" : reason;
        entries = List.copyOf(entries == null ? List.of() : entries);
        answer = answer == null ? Answer.NONE : answer;
        posture = posture == null ? LocalAgentPosture.ANALYSER_ASSISTANT : posture;
        billing = billing == null ? SessionEvents.Billing.UNKNOWN : billing;
        extra = extra == null ? Extra.NONE : extra;
    }

    /** A spend limit was set for the last start (owner, 2026-10-17: optional, off by default). */
    public boolean budgetLimited() {
        return budgetMicros > 0;
    }

    /** A turn is open: Send is refused (OD-74.7), Stop is allowed. */
    public boolean busy() {
        return openTicket != 0;
    }

    /** A process exists for the current generation. */
    public boolean running() {
        return !"OFF".equals(phase);
    }

    /** Starting: spawned and not yet bound. */
    public boolean starting() {
        return "SPAWNING".equals(phase) || "HANDSHAKE".equals(phase) || "BINDING".equals(phase);
    }
}
