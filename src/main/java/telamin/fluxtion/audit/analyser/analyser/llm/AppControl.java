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

    /** Open a processor {@code .graphml}. */
    ActionResult openGraphml(String path);

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

    /** Bring a named side tab to the front ({@code Summary|Source|Graph|Topology|Analyser assistant}). */
    boolean showTab(String name);
}
