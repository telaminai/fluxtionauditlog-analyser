package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 P2 (spec §5.8): the start page's prompt as the {@code startPagePrompt} node decided it. The start page RENDERS this
 * — the route the button takes, whether Claude Code was not found, the fallback after a press and the node's note — and
 * decides nothing: choosing an entry is local to the page and posts nothing; the button posts
 * {@link SessionEvents.StartPagePromptSent}. The list itself is the catalogue's built-in S1–S6 (the configurable list is
 * deferred by the owner until after P3).
 *
 * @param route              where a question goes now (OD-74.23)
 * @param claudeCodeNotFound the switch is on but the resolver did not find {@code claude}: the no-AI column shows its line
 * @param fallbackEntry      the entry whose no-AI fallback buttons are shown after a press, or null
 * @param note               the node's words about the last press, or ""
 * @param answeredRequest    the last request the node answered
 * @param packRef            the dropped prompt pack in force (its words in the frame's session-only store), or 0 for the
 *                           built-in list
 * @param packRefusal        why the last dropped pack was not used, or null
 * @param packRefusalLine    its line (0 for the file as a whole)
 * @param noLogHeld          robot re-walk #3: the press held because it is about evidence and no log is open (the page
 *                           shows "No log is open — the answer will be general" and its three actions), or 0
 * @param noLogOpening       the person chose to open the sample or a log for it, which has not opened yet
 */
public record StartPageState(Route route, boolean claudeCodeNotFound, String fallbackEntry, String note,
                             long answeredRequest, long packRef,
                             telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Cause packRefusal,
                             int packRefusalLine, long noLogHeld, boolean noLogOpening) {

    /** The shape before robot re-walk #3: nothing held for want of a log. */
    public StartPageState(Route route, boolean claudeCodeNotFound, String fallbackEntry, String note, long answeredRequest,
                          long packRef, telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Cause packRefusal,
                          int packRefusalLine) {
        this(route, claudeCodeNotFound, fallbackEntry, note, answeredRequest, packRef, packRefusal, packRefusalLine, 0,
                false);
    }

    public StartPageState(Route route, boolean claudeCodeNotFound, String fallbackEntry, String note, long answeredRequest) {
        this(route, claudeCodeNotFound, fallbackEntry, note, answeredRequest, 0, null, 0);
    }

    /** Where the start page's question goes (§5.8 "AI configured"). */
    public enum Route {
        /** the mode in force is Claude Code, and it can answer */
        CLAUDE_CODE,
        /** the mode in force is the in-app assistant, and it can answer */
        IN_APP,
        /** the mode in force cannot answer and Claude Code can: the button reads "Ask with Claude Code" */
        SWITCH_TO_CLAUDE_CODE,
        /** the mode in force cannot answer and the in-app assistant can */
        SWITCH_TO_IN_APP,
        /** neither can: the no-AI column */
        NO_AI;

        public boolean canAnswer() {
            return this != NO_AI;
        }

        public AssistantMode target() {
            return switch (this) {
                case CLAUDE_CODE, SWITCH_TO_CLAUDE_CODE -> AssistantMode.LOCAL_AGENT;
                case IN_APP, SWITCH_TO_IN_APP -> AssistantMode.IN_APP;
                case NO_AI -> null;
            };
        }

        /** Whether the question goes to Claude Code — what the page's cue and button words follow (never the mode). */
        public boolean toClaudeCode() {
            return this == CLAUDE_CODE || this == SWITCH_TO_CLAUDE_CODE;
        }

        public boolean switchesMode() {
            return this == SWITCH_TO_CLAUDE_CODE || this == SWITCH_TO_IN_APP;
        }
    }

    public StartPageState {
        route = route == null ? Route.NO_AI : route;
        note = note == null ? "" : note;
    }

    public static final StartPageState IDLE = new StartPageState(Route.NO_AI, false, null, "", 0);

    /** The refusal's words: "line 7 — the label is over 60 characters", or "" with none. */
    public String packRefusalWords() {
        return packRefusal == null ? "" : new telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Refusal(
                packRefusalLine, packRefusal).words();
    }
}
