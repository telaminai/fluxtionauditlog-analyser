package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 UX (owner, 2026-10-17): how the assistant works on this machine, chosen as one of four cards instead of switch by
 * switch. Each profile names the same machine-tier settings {@code Settings ▸ Assistant} holds (the Claude Code switches
 * and mode, the in-app provider) and the assistant mode it puts the tab in; {@code assistantProfile} decides when the
 * choice is offered and whether it may be applied now.
 *
 * <p>{@link #NONE} is "not chosen yet": nothing on this machine makes an assistant able to answer and no card was
 * chosen. It is the only state in which the choices open by themselves (once, on the first Start or question).
 */
public enum AssistantProfile {
    /** Not chosen: no card was picked and no assistant is set up. */
    NONE,
    /**
     * Safe analysis (the default card): Claude Code reads your evidence; it changes what is open or writes a file only
     * after you approve each call — no shell, safe with untrusted logs.
     */
    SAFE,
    /** Full Claude Code: the person's own set-up, skills and tools, under their rules and the mode they choose. */
    FULL,
    /** The in-app assistant: an API key and a model; the analyser owns the conversation. */
    IN_APP,
    /** Off: no AI is used; everything else still works. */
    OFF;

    /** The assistant mode the tab is in under this profile ({@code NONE} and {@code OFF} leave it in-app). */
    public AssistantMode mode() {
        return this == SAFE || this == FULL ? AssistantMode.LOCAL_AGENT : AssistantMode.IN_APP;
    }

    /** Whether Claude Code may be started under this profile. */
    public boolean claudeCode() {
        return this == SAFE || this == FULL;
    }

    /** The card's title, as the overlay and Settings show it. */
    public String title() {
        return switch (this) {
            case NONE -> "Not chosen";
            case SAFE -> "Safe analysis";
            case FULL -> "Full Claude Code";
            case IN_APP -> "In-app assistant";
            case OFF -> "Off";
        };
    }

    /** The stored value ({@code assistant.claudeCode.profile}), or null for {@link #NONE}. */
    public String stored() {
        return this == NONE ? null : name();
    }

    /** A stored value read back: anything not a profile's name is {@link #NONE}. */
    public static AssistantProfile parse(String stored) {
        if (stored == null) return NONE;
        for (AssistantProfile p : values()) if (p != NONE && p.name().equals(stored.trim())) return p;
        return NONE;
    }
}
