package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * Holistic review B5/B2 (2026-10-17): the action listener as {@code actionListener} decided it — what the panel renders.
 *
 * @param restOn        the person's REST switch (external MCP / REST access)
 * @param listening     a listener is up (the last configuration reported it)
 * @param external      it serves external clients (the well-known endpoint file is published)
 * @param bridgeFor     the Claude Code generation whose hosted bridge needs it, or 0
 * @param fullSession   that session runs the person's full Claude Code
 * @param closedForSession the person closed external access while that session runs
 * @param offerClose    B2: a full session runs with external access open to its own tools — offer to close it
 */
public record ListenerState(boolean restOn, boolean listening, boolean external, long bridgeFor, boolean fullSession,
                            boolean closedForSession, boolean offerClose) {
    public static final ListenerState NONE = new ListenerState(false, false, false, 0, false, false, false);
}
