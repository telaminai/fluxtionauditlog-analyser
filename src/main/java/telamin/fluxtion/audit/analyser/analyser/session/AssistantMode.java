package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 (spec-claude-code-provider.md §4.1): which assistant the Assistant tab drives. One at a time, chosen by the person
 * on the tab and held by the session ({@code assistantMode}). Each assistant's node enforces it in its OWN handlers —
 * {@code assistantLoop} refuses a Send and ignores a handoff under {@link #LOCAL_AGENT}, {@code localAgentSession}
 * refuses under {@link #IN_APP} — so a disabled button is never the only thing in the way [R2-7].
 */
public enum AssistantMode {
    /** The analyser's own assistant: an API key, and the analyser owns the conversation. The default. */
    IN_APP,
    /** The person's own Claude Code CLI, started by the analyser and driving it over the hosted bridge. */
    LOCAL_AGENT
}
