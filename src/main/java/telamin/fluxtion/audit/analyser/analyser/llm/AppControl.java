package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.List;

/**
 * The seam for verbs that change <b>what the app is pointed at</b> rather than how it is displayed —
 * opening a log or a topology, and configuring source roots.
 *
 * <p>Kept separate from {@link RenderExecutor} because the two are different in kind, and the difference
 * is worth being able to see. Render verbs rearrange what is already loaded and are reversible from the
 * UI. These reach the filesystem: {@code open} points the app at any readable path, and a source root
 * grants {@code read}-style access to every {@code .java} file beneath it. Over the localhost REST
 * transport that is a meaningful capability, so it lives behind its own interface — an embedder that
 * wants the render verbs without the environment ones simply does not supply an implementation, and the
 * dispatcher reports the verb as unavailable rather than half-working.
 */
public interface AppControl {

    /** Open an audit log from a path (or {@code s3://…}); returns the echo or a structured error. */
    ActionResult openLog(String path);

    /** Open with an explicit reader format (M31); default falls back to sniff-free canOpen routing. */
    default ActionResult openLog(String path, String format) {
        return openLog(path);
    }

    /**
     * Open with a declared provenance (§E) in the SAME call (M35.9). The executor calls this form;
     * the default routes through {@link #setProvenance} + {@link #openLog(String, String)} so an
     * implementor written before M35.9 sees exactly what it used to. Passing the declaration with
     * the request is what lets an implementation carry it through an asynchronous load without a
     * field set beforehand and consumed afterwards — the shape that failed four times in M35.
     */
    default ActionResult openLog(String path, String format, String provenance) {
        setProvenance(provenance);
        return openLog(path, format);
    }

    /**
     * Open an explicit rolled set (M30 D-R5): the caller DECLARES the member list; content decides the
     * order; the echo carries the order chosen and the TimeOrderReport. Default: not supported.
     */
    default ActionResult openLogs(java.util.List<String> paths) {
        return ActionResult.error("'logs' is not enabled here");
    }

    /** As {@link #openLogs(java.util.List)} with the provenance in the same call (M35.9). */
    default ActionResult openLogs(java.util.List<String> paths, String provenance) {
        setProvenance(provenance);
        return openLogs(paths);
    }

    /** Open a processor {@code .graphml}. */
    ActionResult openGraphml(String path);

    /**
     * Declare WHERE the log about to be opened came from (§E). Free text. Since M35.9 the executor
     * passes provenance WITH the open ({@link #openLog(String, String, String)}); this remains on the
     * published surface so implementors written against the earlier protocol keep receiving it via
     * the defaults. Never inferred — a guessed system name is worse than none.
     *
     * <p><b>The analyser's own implementation does not override this and keeps no state for it</b>
     * (M35.9 review N1): {@code MainFrame} implements the three-argument form directly and builds
     * its {@code OpenRequest} from the call. Do not look for the field this used to set — there is
     * none; the method exists only so foreign implementors written before M35.9 keep working.
     */
    default void setProvenance(String provenance) {
    }

    /**
     * List the .graphml files under the source roots, ranked against the open log (M35.4).
     * Returns candidates and opens NOTHING — picking is the caller's act.
     */
    default ActionResult discoverGraphs() {
        return ActionResult.error("'discover' is not enabled here");
    }

    /**
     * Close what is open (M35.1): {@code "log"}, {@code "graph"} or {@code "all"}. Agents switching
     * between servers need this as much as a human does — under the M18 alternative it is a
     * per-minute operation, and without it a second log inherits the first log's topology.
     */
    default ActionResult close(String what) {
        return ActionResult.error("'close' is not enabled here");
    }

    /**
     * Open a project (M35.8) — {@code path} is its {@code .analyser/project.fluxtion-settings} or the
     * project directory. APPLIES, never asks: a modal cannot be answered at the socket (M35.7), so the
     * safety is the ECHO, which names everything the switch replaced (before/after counts), what it
     * closed (M35.5 — a project is a session boundary) and how to put the previous settings back.
     * The MCP client's per-call approval is the human gate. Default: not supported.
     */
    default ActionResult openProject(String path) {
        return ActionResult.error("'project' is not enabled here");
    }

    /**
     * Select the EventProcessor whose source backs node → class resolution.
     *
     * <p>Needed because inference only runs over candidates found in the package of the
     * <em>currently</em> selected processor, so a fresh root containing a differently-packaged processor
     * is invisible to it. Without this, every source navigation reports "no source mapping" with the
     * source sitting right there.
     */
    ActionResult selectProcessor(String fqn);

    /** The configured source roots, in order. */
    List<String> sourceRoots();

    /** Add a source root; a duplicate is a no-op. Returns false when the path is not a usable root. */
    boolean addSourceRoot(String path);

    /** Remove a source root; returns false when it was not configured. */
    boolean removeSourceRoot(String path);

    /**
     * Paint the app's window (or one named panel) to a PNG.
     *
     * <p>The app paints <b>itself</b> rather than asking the OS for a screen grab, which matters on
     * macOS: a screen capture needs the Screen Recording permission, and a headless caller cannot grant
     * it. Painting has no such gate, is deterministic, and captures exactly the state the other verbs
     * just set up. What it cannot show is the native title bar, since that is drawn by the window server
     * and not by the application.
     */
    ActionResult screenshot(String path, String scope);

    /**
     * Build or replace a named investigation report from typed sections (M33.3), assemble a table
     * section to CSV, or render the report to PDF — {@code resolvedPath} is non-null only when the
     * caller already passed the path through the export guard. Default: not supported.
     */
    default ActionResult report(java.util.Map<String, Object> params, String resolvedPath) {
        return ActionResult.error("'report' sections are not enabled here");
    }

    /**
     * What the user is currently looking at, as data.
     *
     * <p>The other verbs let an assistant <b>change</b> the view; none of them let it <b>see</b> one. That
     * asymmetry is why the workflow needed a copied prompt: the human's filtering, flagging and graphing
     * — the expensive, judgement-laden part of an investigation — was invisible unless pasted in.
     *
     * <p>Returns <b>pointers, not payloads</b>: record indexes and byte offsets rather than record text,
     * so the answer stays small and the caller fetches only what it needs through the rate-limited
     * {@code read}. The {@code filter} it reports is in the shape {@code aggregate} accepts, so scoping a
     * query to the user's own filter is passing it straight back rather than reconstructing it.
     */
    ActionResult context();

    /** Bring a named side tab to the front ({@code Summary|Source|Graph|Topology|Analyser assistant}). */
    boolean showTab(String name);

    /**
     * Write one record's finding out as a PDF: the explanation, the event, the node log, a picture of the
     * graph, and — when named — a relevant plot.
     *
     * <p>The diagnosis is already assembled on screen by the time this is called; this is the step that
     * makes it leave the machine. A finding that can only be seen by driving the analyser is a finding
     * that never reaches the person who has to act on it.
     *
     * @param path        where to write the .pdf
     * @param recordIndex which record, or {@code null} for whatever is selected
     * @param title       the headline; {@code null} derives one from the record
     * @param graph       the name of a plot to include, or {@code null} for none
     * @param withTopology include a picture of the graph
     */
    ActionResult exportFinding(String path, Integer recordIndex, String title, String graph,
                               boolean withTopology);
}
