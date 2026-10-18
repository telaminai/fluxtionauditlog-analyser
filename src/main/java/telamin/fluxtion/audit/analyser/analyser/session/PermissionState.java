package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 P3 (spec-claude-code-provider.md §5.7): the permission prompt as the {@code localAgentPermission} node decided it,
 * published in the {@link SessionSnapshot}. The tab renders its status line from this ("Claude Code is waiting for your
 * answer"); the dialog itself opens and closes only on the node's effects. Ids, a class and a vocabulary name only —
 * the request's words are in the adapter's bounded store.
 *
 * @param pendingRef   the request a dialog is open for, or 0
 * @param generation   the process generation it belongs to
 * @param toolClass    its class, or null when none is pending
 * @param name         the hosted verb or built-in's name (vocabulary), or null
 * @param deadlineMs   how long the person has to answer, from when it was asked
 * @param liveGrants   single-use grants issued and not yet consumed, expired or dropped
 * @param decisions    requests answered so far in this run (for the tests and the record), by the person or by policy
 * @param queued       requests waiting behind the open dialog (review M74 usability #10c), shown after it is answered
 * @param reportsGeneration the generation {@code sessionReports} belongs to (0: none)
 * @param sessionReports owner 2026-10-18: the active project's reports that generation's Claude Code session wrote without
 *                     an approval, name → the {@code createdAt} the report carried after the write
 */
public record PermissionState(long pendingRef, long generation, SessionEvents.ToolClass toolClass, String name,
                              long deadlineMs, int liveGrants, long decisions, int queued, long reportsGeneration,
                              java.util.Map<String, String> sessionReports) {

    public static final PermissionState NONE = new PermissionState(0, 0, null, null, 0, 0, 0, 0);

    public PermissionState {
        sessionReports = sessionReports == null ? java.util.Map.of() : java.util.Map.copyOf(sessionReports);
    }

    public PermissionState(long pendingRef, long generation, SessionEvents.ToolClass toolClass, String name,
                           long deadlineMs, int liveGrants, long decisions, int queued) {
        this(pendingRef, generation, toolClass, name, deadlineMs, liveGrants, decisions, queued, 0, java.util.Map.of());
    }

    public PermissionState(long pendingRef, long generation, SessionEvents.ToolClass toolClass, String name,
                           long deadlineMs, int liveGrants, long decisions) {
        this(pendingRef, generation, toolClass, name, deadlineMs, liveGrants, decisions, 0);
    }

    /**
     * Owner 2026-10-18: the {@code createdAt} report {@code name} carried after hosted generation {@code generation} wrote
     * it without an approval, or null — it was not that session's write.
     */
    public String sessionReport(long generation, String name) {
        return generation != 0 && generation == reportsGeneration ? sessionReports.get(name) : null;
    }

    /** Whether a dialog is open: the tab says Claude Code is waiting for the person's answer. */
    public boolean waiting() {
        return pendingRef != 0;
    }
}
