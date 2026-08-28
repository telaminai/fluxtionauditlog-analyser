package telamin.fluxtion.audit.analyser.analyser.mcp;

/**
 * D-AI9 — what the AI status light says, as data, so the wording is pinned by test rather than by
 * whoever last edited a Swing class.
 *
 * <h2>The claim it must not make</h2>
 * {@link McpSetupState}'s own javadoc names the trap: <i>"a green local state never gets misrepresented
 * as 'an AI client is connected'"</i>. Whether this window is SERVING and whether a client is TALKING to
 * it are two different facts. The first is knowable continuously and for free; the second needs a probe
 * and is true only at the instant it is measured. The light reports the first, and its words say so —
 * <b>"MCP ready"</b>, never "connected".
 *
 * <h2>Four states, not two</h2>
 * Collapsing to green/red would lose {@code OTHER_INSTANCE}, which is the whole reason the light earns
 * its space: another analyser window owns the endpoint, so the user's AI client is answering questions
 * about a different log than the one on screen. It is invisible today and it is the state most likely to
 * cost someone an hour.
 *
 * <p>And OFF is grey, not red. The transport being off is a choice, not a fault; colouring a deliberate
 * decision as an error is how people learn to ignore an indicator — the same reasoning that keeps the
 * coverage caveat off a perfect score.
 */
public final class McpIndicator {

    private McpIndicator() {
    }

    /** How the light should read. Not a colour — the panel maps this to the theme's palette. */
    public enum Level {
        /** Serving: a client can reach this window. */
        GOOD,
        /** Worth a look: starting, or another window owns the endpoint. */
        ATTENTION,
        /** Off by choice — never an error. */
        NEUTRAL
    }

    /**
     * @param level   how to colour it
     * @param label   the short text beside the dot
     * @param detail  the tooltip — {@link McpSetupState.LocalReadiness#detail()} verbatim where there is
     *                one, so there is ONE wording rather than a second that can drift from it (D-AI2)
     */
    public record View(Level level, String label, String detail) {
    }

    public static View of(McpSetupState.LocalReadiness readiness) {
        if (readiness == null) {
            return new View(Level.NEUTRAL, "MCP off", "Local transport state is not known in this window.");
        }
        return switch (readiness.status()) {
            // "ready", not "connected": this window is serving; whether an AI client is actually talking
            // to it is a separate fact this light cannot see.
            case READY -> new View(Level.GOOD, "MCP ready", readiness.detail()
                    + " This says the analyser is reachable, not that a client is connected.");
            case STARTING -> new View(Level.ATTENTION, "MCP starting", readiness.detail());
            case OTHER_INSTANCE -> new View(Level.ATTENTION, "MCP elsewhere", readiness.detail()
                    + " An AI client using it is reading that window's log, not this one.");
            // grey, not red: not set up is a state to act on, not a fault to alarm about, so the
            // tooltip carries the REMEDY rather than a diagnosis
            case OFF -> new View(Level.NEUTRAL, "MCP off", readiness.detail()
                    + " Nothing is wrong — set it up with AI \u25b8 Connect an AI client.");
        };
    }
}
