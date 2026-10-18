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
     * @param attention          whether the operator details (CLI version, endpoint, PATH, diagnostics, the last error)
     *                           need the person's attention — the node's decision; the panel's <i>Details</i> disclosure
     *                           opens by itself only when this is not {@link Attention#NONE}
     * @param restart            owner, 2026-10-18: a settings change the running session has not taken yet, and what
     *                           the person said about restarting for it — the node's decision; the panel renders it
     */
    public record Extra(long setupDirRef, String setupDirShown, int unknownEvents, int unclassifiedCommands, int droppedLines,
                        java.util.Map<Long, Turn> turns, int trimmed, Attention attention, Restart restart, Guide guide) {
        public Extra {
            turns = java.util.Map.copyOf(turns == null ? java.util.Map.of() : turns);
            attention = attention == null ? Attention.NONE : attention;
            restart = restart == null ? Restart.NONE : restart;
            guide = guide == null ? Guide.NONE : guide;
        }

        /** The shape before the one primary action and instruction (robot walkthrough #3). */
        public Extra(long setupDirRef, String setupDirShown, int unknownEvents, int unclassifiedCommands, int droppedLines,
                     java.util.Map<Long, Turn> turns, int trimmed, Attention attention, Restart restart) {
            this(setupDirRef, setupDirShown, unknownEvents, unclassifiedCommands, droppedLines, turns, trimmed, attention,
                    restart, Guide.NONE);
        }

        /** The shape before the restart offer: nothing waits for a restart. */
        public Extra(long setupDirRef, String setupDirShown, int unknownEvents, int unclassifiedCommands, int droppedLines,
                     java.util.Map<Long, Turn> turns, int trimmed, Attention attention) {
            this(setupDirRef, setupDirShown, unknownEvents, unclassifiedCommands, droppedLines, turns, trimmed, attention,
                    Restart.NONE);
        }

        /** The shape before the Details disclosure: nothing needs attention. */
        public Extra(long setupDirRef, String setupDirShown, int unknownEvents, int unclassifiedCommands, int droppedLines,
                     java.util.Map<Long, Turn> turns, int trimmed) {
            this(setupDirRef, setupDirShown, unknownEvents, unclassifiedCommands, droppedLines, turns, trimmed,
                    Attention.NONE);
        }

        public static final Extra NONE = new Extra(0, null, 0, 0, 0, java.util.Map.of(), 0);

        /** Whether the diagnostics line shows (only when a count is non-zero). */
        public boolean diagnostics() {
            return unknownEvents > 0 || unclassifiedCommands > 0 || droppedLines > 0;
        }
    }

    /**
     * Robot walkthrough #3 (2026-10-18): what the panel tells the person to do — ONE primary action and ONE instruction
     * line, decided by {@code localAgentSession}. It replaced four cues that disagreed ("press Start, then Send", "Press
     * Send", "Start — ask your question", a disabled "Running…" beside "Ready"). Send alone starts Claude Code when it is
     * not running, so Send is the primary action unless a turn or a start can be stopped.
     *
     * @param primary     {@link #SEND} or {@link #STOP}
     * @param instruction the one status line: the phase, or — while a refusal stands — the refusal alone
     * @param action      {@link #ASK_AGAIN} when the composer holds a question drafted for the other assistant, else ""
     * @param refusal     the instruction is a refusal (the composer's "Press Send" hint is then not shown beside it)
     * @param chipWord    M78 D1 (spec-quiet-assistant-panel.md §5.1): the one or two words the header's mode control
     *                    carries — {@link #CHIP_READY} (nothing), {@link #CHIP_STARTING}, {@link #CHIP_ANSWERING},
     *                    {@link #CHIP_NEEDS_YOU} or {@link #CHIP_STOPPED}; a closed set, chosen here, never by the panel
     * @param banner      M78 D1 (§2.3): the words of the conditional banner — a refusal or an end that the person must
     *                    read — or "" when there is none. The routine phase ("Ready", "Claude is answering…", "Not
     *                    running …") is never a banner: the chip word carries it
     */
    public record Guide(String primary, String instruction, String action, boolean refusal, String chipWord,
                        String banner) {
        public static final String SEND = "SEND";
        public static final String STOP = "STOP";
        public static final String ASK_AGAIN = "ASK_AGAIN";
        /** M78 D1 §5.1: the chip words, a closed set. Ready says nothing. */
        public static final String CHIP_READY = "";
        public static final String CHIP_STARTING = "starting…";
        public static final String CHIP_ANSWERING = "answering…";
        public static final String CHIP_NEEDS_YOU = "needs you";
        public static final String CHIP_STOPPED = "stopped";
        public static final java.util.List<String> CHIP_WORDS = java.util.List.of(CHIP_READY, CHIP_STARTING,
                CHIP_ANSWERING, CHIP_NEEDS_YOU, CHIP_STOPPED);
        public static final Guide NONE = new Guide(SEND, "", "", false);

        public Guide {
            primary = primary == null ? SEND : primary;
            instruction = instruction == null ? "" : instruction;
            action = action == null ? "" : action;
            chipWord = chipWord == null ? CHIP_READY : chipWord;
            banner = banner == null ? "" : banner;
        }

        /** The shape before M78 D1: no chip word, no banner. */
        public Guide(String primary, String instruction, String action, boolean refusal) {
            this(primary, instruction, action, refusal, CHIP_READY, "");
        }
    }

    /** A copy with {@code extra} — the node publishes the guide computed from the state it describes. */
    public LocalAgentState withExtra(Extra extra) {
        return new LocalAgentState(mode, conversation, generation, phase, verdict, reason, openTicket, admittedGeneration,
                headerRef, headerRevision, detailRef, stderrRef, bindingElapsedMs, spentMicros, budgetMicros,
                costUnderReported, newChatOffered, resumable, pendingDraft, entries, answer, posture, personServers, billing,
                armedWhy, consentPosture, extra);
    }

    /**
     * Owner, 2026-10-18 ("update the assistant now? yes / no"): a change in Settings, or from the profile cards, that the
     * running Claude Code session has not taken. Only the changes that may wait are offered — a change that takes powers
     * away (consent withdrawn, Full switched off, a mode that asks more, the folder's trust gone) ends the session at
     * once and is never offered.
     *
     * @param offer   the offer's id; an answer naming another is stale
     * @param state   {@link #OFFERED} (the prompt shows), {@link #DEFERRED} (the person chose Later: the chip shows),
     *                {@link #AFTER_TURN} (the person chose Restart while Claude was answering), or "" for none
     * @param changed what changed, in words ("the model (sonnet → opus)")
     * @param newChat whether restarting begins a new chat (a conversation is never resumed under another posture)
     */
    public record Restart(long offer, String state, String changed, boolean newChat) {
        public static final String OFFERED = "OFFERED";
        public static final String DEFERRED = "DEFERRED";
        public static final String AFTER_TURN = "AFTER_TURN";
        public static final Restart NONE = new Restart(0, "", "", false);

        public Restart {
            state = state == null ? "" : state;
            changed = changed == null ? "" : changed;
        }

        public boolean offered() {
            return OFFERED.equals(state);
        }

        public boolean deferred() {
            return DEFERRED.equals(state);
        }

        public boolean afterTurn() {
            return AFTER_TURN.equals(state);
        }

        public boolean any() {
            return !state.isEmpty();
        }
    }

    /**
     * External utility review (owner-approved 2026-10-17): whether the operator details need the person — decided by the
     * {@code localAgentSession} node (rule 9), never by the panel. {@link #MISMATCH}: the session did not match how
     * Claude Code was set up; {@link #REFUSAL}: Claude Code could not start, or was refused; {@link #WARNING}: the stream
     * held something unexpected, or the process left an error line.
     */
    public enum Attention { NONE, WARNING, REFUSAL, MISMATCH }

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
