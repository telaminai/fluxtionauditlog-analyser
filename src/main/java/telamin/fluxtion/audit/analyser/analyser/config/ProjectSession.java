package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Which project is open, and what happens when you leave it (M20.2).
 *
 * <p>Holds the three things the UI would otherwise scatter: the active profile path, the settings that
 * were in play <b>before</b> any project was opened, and whether there are unsaved project edits.
 * Headless on purpose — a File menu should be a thin caller of this, not the place the rules live.
 *
 * <h2>Auto-persist, debounced</h2>
 *
 * <p>Edits to project-scoped settings write to the project file, the way today's app auto-saves its
 * config (spec O4 — explicit-save was rejected as a surprise). Writes are <b>coalesced</b>: a burst of
 * graph tweaks is one write, not fifteen. That is not a performance concern — a profile is often a
 * committed file, and a legible git diff is the difference between a teammate reviewing a change and
 * skipping it.
 *
 * <p>The coalescing lives here as {@link #requestSave()} / {@link #flush()} rather than in a timer, so
 * the property that matters — <em>many requests, one write</em> — is testable without waiting on wall
 * clock. The caller supplies the timer.
 *
 * <h2>Why the "before" snapshot exists</h2>
 *
 * <p>A user who has been configuring source roots for a year and then opens their first project must
 * not lose that configuration. The spec is explicit: those values are the <b>"no project" defaults</b>
 * and closing the project restores them. Without a snapshot taken at the moment of first opening, the
 * first switch would silently consume them.
 */
public final class ProjectSession {

    /** Told when project-scoped settings have changed and a write is due; typically restarts a timer. */
    @FunctionalInterface
    public interface SaveScheduler {
        void saveRequested();
    }

    private final AppConfig config;
    private final SettingsShare share;
    private final SaveScheduler scheduler;

    /** Null while no project is open. */
    private Path activeFile;
    /** The project-scoped settings as they were before any project was opened; null until first open. */
    private ProjectProfile.Snapshot noProjectDefaults;
    private boolean dirty;
    private int writes;
    /**
     * Runs immediately before every profile write, so live UI state (the open graph tabs) is captured
     * into {@code config} at the moment it is persisted — B-M20-3: {@code config.savedGraphs} was only
     * synced at export/exit, so every flush wrote a STALE graph list and project graph work was lost.
     * The change-listener path makes saves timely; this hook is the safety net that makes every write
     * current even if a mutation point forgot to notify.
     */
    private Runnable preSave;

    /** Set the pre-write sync hook (see {@link #preSave}). Null clears it. */
    public void setPreSave(Runnable preSave) {
        this.preSave = preSave;
    }

    public ProjectSession(AppConfig config, SettingsShare share, SaveScheduler scheduler) {
        this.config = config;
        this.share = share;
        this.scheduler = scheduler == null ? () -> { } : scheduler;
        if (config.activeProjectPath != null && !config.activeProjectPath.isBlank()) {
            this.activeFile = Path.of(config.activeProjectPath);
        }
    }

    public boolean hasProject() {
        return activeFile != null;
    }

    public Path activeFile() {
        return activeFile;
    }

    /** The project's display name — its directory, since every profile has the same file name. */
    public String activeName() {
        if (activeFile == null) {
            return "";
        }
        Path dir = activeFile.getParent();                       // .../.analyser
        Path root = dir == null ? null : dir.getParent();        // the project itself
        return root == null ? activeFile.toString() : root.getFileName().toString();
    }

    public int writeCount() {
        return writes;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Apply the active project recorded in the global config — the startup step.
     *
     * <p>Owned by the session rather than done before it exists, and that ordering is load-bearing: the
     * snapshot of "what the user had before any project" can only be taken while those values are still
     * in the config. Activating first and constructing the session afterwards leaves nothing to
     * snapshot, and the global file then gets overwritten with the open project's settings on the next
     * save — which is exactly the bug this ordering was changed to fix.
     *
     * <p>A pointer to a file that has gone clears the pointer and reports it. Startup never fails here.
     *
     * @return {@code null} when no project is configured — nothing happened, nothing to say
     */
    public ProjectProfile.LoadResult activateOnStartup() {
        if (activeFile == null) {
            return null;
        }
        noProjectDefaults = ProjectProfile.snapshot(config);   // BEFORE the profile overwrites them
        ProjectProfile.LoadResult result = ProjectProfile.load(activeFile, config, share);
        if (!result.loaded()) {
            // a stale pointer would re-report the same failure on every launch
            activeFile = null;
            config.activeProjectPath = "";
            noProjectDefaults = null;
            return new ProjectProfile.LoadResult(false, result.message() + " — continuing without a project");
        }
        return result;
    }

    // ---- switching ------------------------------------------------------------------------------

    /**
     * Open a project, replacing the project-scoped settings with its own.
     *
     * <p>Flushes any pending write to the <b>outgoing</b> project first — leaving a project must not
     * discard the edits you made in it, and a debounce window is exactly when that would happen.
     */
    public ProjectProfile.LoadResult open(Path file) {
        flush();
        rememberDefaultsOnce();
        ProjectProfile.LoadResult result = ProjectProfile.load(file, config, share);
        if (!result.loaded()) {
            return result;
        }
        activeFile = file;
        config.activeProjectPath = file.toString();
        ProjectProfile.addRecent(config.recentProjects, file.toString());
        dirty = false;
        return result;
    }

    /**
     * Start a new, empty project at {@code file} and make it active.
     *
     * <p>Empty means empty: a new project does not inherit the settings you happened to have open, or
     * "new project" would be a slow way to copy one. {@link #saveAs} is how you fork.
     */
    public ProjectProfile.LoadResult create(Path file) throws IOException {
        flush();
        rememberDefaultsOnce();
        ProjectProfile.clearProjectScoped(config);
        config.mavenRepos.add(AppConfig.defaultMavenRepo());   // a usable starting point, not a blank
        ProjectProfile.save(file, config, share);
        writes++;
        activeFile = file;
        config.activeProjectPath = file.toString();
        ProjectProfile.addRecent(config.recentProjects, file.toString());
        dirty = false;
        return new ProjectProfile.LoadResult(true, "new project: " + file);
    }

    /** Fork the current settings to a new path, which becomes active. There is no plain "save". */
    public void saveAs(Path file) throws IOException {
        ProjectProfile.save(file, config, share);
        writes++;
        activeFile = file;
        config.activeProjectPath = file.toString();
        ProjectProfile.addRecent(config.recentProjects, file.toString());
        dirty = false;
    }

    /**
     * Leave the project and restore what was in play before any project was opened.
     *
     * <p>The app returns to being exactly today's single-config app, which is the backward-compatibility
     * promise in the spec.
     */
    public void close() {
        flush();
        if (noProjectDefaults != null) {
            ProjectProfile.restore(noProjectDefaults, config);
        }
        activeFile = null;
        config.activeProjectPath = "";
        dirty = false;
    }

    // ---- persistence ----------------------------------------------------------------------------

    /**
     * A project-scoped setting changed. Cheap and idempotent — called from the single config-change
     * funnel, so it fires for verb-driven edits ({@code source_root}, {@code open {processor}}) exactly
     * as it does for the Settings dialog. Hanging persistence off dialog-close would silently lose
     * every scripted edit.
     */
    public void requestSave() {
        if (activeFile == null) {
            return;             // no project open: the global config is already the right home
        }
        dirty = true;
        scheduler.saveRequested();
    }

    /** Write now if anything is pending. Safe to call when clean; that is what makes coalescing work. */
    public void flush() {
        if (activeFile == null || !dirty) {
            return;
        }
        if (preSave != null) preSave.run();   // capture live state (open graphs) before writing (B-M20-3)
        try {
            ProjectProfile.save(activeFile, config, share);
            writes++;
            dirty = false;
        } catch (IOException e) {
            // a read-only checkout or a deleted directory: keep the edit in memory and keep the app
            // usable. Losing the file is not a reason to lose the session.
            lastError = "could not write " + activeFile + ": " + e.getMessage();
        }
    }

    private String lastError;

    /** The last write failure, or null. Cleared by reading it, so a caller reports it once. */
    public String takeError() {
        String e = lastError;
        lastError = null;
        return e;
    }

    /**
     * The project-scoped values the GLOBAL config should keep holding — the user's own settings from
     * before any project was opened. Null when no project is active, meaning "save the live config".
     */
    public ProjectProfile.Snapshot globalTier() {
        return activeFile == null ? null : noProjectDefaults;
    }

    private void rememberDefaultsOnce() {
        if (noProjectDefaults == null && activeFile == null) {
            noProjectDefaults = ProjectProfile.snapshot(config);
        }
    }
}
