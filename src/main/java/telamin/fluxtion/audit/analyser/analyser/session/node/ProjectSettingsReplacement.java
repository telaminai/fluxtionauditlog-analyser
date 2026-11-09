package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.Objects;

/**
 * Follow-up to #198 (review F2): an accepted profile reload REPLACES the project's settings without being a session
 * boundary — the open log and graph stay, and nothing about the project's identity changes. What does change are the
 * views worked out from the old settings: Design discovery (from the source roots) and the project-output watch (from
 * the configured log folders).
 *
 * <p>This node owns that replacement. On a successful reload (the adapter reloads only the profile in force), it asks
 * the adapter what the reload replaced ({@link SessionEffects.DescribeSettingsReplacementEffect}). The answer
 * ({@link SessionEvents.ProjectSettingsReplaced}) is accepted only while it is the answer to the newest request, for the
 * same profile, in the same project generation; anything else is withheld. It then decides which bases changed —
 * a digest that differs, or one the adapter could not give — and {@link DesignSession} and {@link ProjectOutputs} each
 * act on that decision for their own view.
 *
 * <p><b>Why not the existing facts.</b> {@code SourceRootsObserved} is a person's root edit, and {@link BundleAnchor}
 * records it as the bundle's answer to where its code lives; a reload is not that choice. {@code ProfileApplied} is a
 * whole-session transition that clears and rebinds the session's context; a reload keeps the log and graph.
 */
public class ProjectSettingsReplacement implements EventLogSource {

    private final ActiveProject project;
    /** Pushed, as {@link ProjectOutputs} does: this node stays upstream of the queue, so the nodes that read it can too. */
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private long counter;
    /** The outstanding request, or 0. */
    private long ticket;
    private String profileKey;
    private long projectGeneration;
    /** The fact accepted in this dispatch, so the nodes acting on it can tell it from a withheld one. */
    private SessionEvents.ProjectSettingsReplaced accepted;
    private boolean sourceRootsChanged;
    private boolean logFoldersChanged;

    public ProjectSettingsReplacement(ActiveProject project, EffectQueue effects) {
        this.project = project;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** A reload of the profile in force succeeded: ask what it replaced. A failed reload replaced nothing. */
    @OnEventHandler
    public boolean onReloaded(SessionEvents.ProjectProfileReloaded e) {
        if (!e.ok() || e.profileKey() == null) return false;
        ticket = ++counter;
        profileKey = e.profileKey();
        projectGeneration = project.generation();
        effects.request(new SessionEffects.DescribeSettingsReplacementEffect(e.opId(), ticket, profileKey));
        auditLog.info("settingsReplacement", "requested").info("ticket", ticket);
        return true;
    }

    /** A project came into force or was left: an outstanding request belongs to the project that was there before. */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied e) {
        ticket = 0;
        return false;
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored e) {
        ticket = 0;
        return false;
    }

    @OnEventHandler
    public boolean onReplaced(SessionEvents.ProjectSettingsReplaced e) {
        accepted = null;
        if (ticket == 0 || e.ticket() != ticket || !Objects.equals(e.profileKey(), profileKey)
                || project.generation() != projectGeneration) {
            auditLog.info("staleFact", "ProjectSettingsReplaced").info("ticket", e.ticket());
            return false;
        }
        ticket = 0;
        accepted = e;
        sourceRootsChanged = replaced(e.sourceRootsBefore(), e.sourceRootsAfter());
        logFoldersChanged = replaced(e.logFoldersBefore(), e.logFoldersAfter());
        auditLog.info("settingsReplacement", "accepted").info("sourceRoots", sourceRootsChanged)
                .info("logFolders", logFoldersChanged);
        return true;
    }

    private static boolean replaced(String before, String after) {
        return before == null || after == null || !before.equals(after);
    }

    /** Whether {@code e} is the replacement this node accepted (a withheld one is not). */
    public boolean accepted(SessionEvents.ProjectSettingsReplaced e) {
        return e != null && e == accepted;
    }

    /** Of the accepted replacement: did the source roots change (Design discovery is stale)? */
    public boolean sourceRootsChanged() {
        return accepted != null && sourceRootsChanged;
    }

    /** Of the accepted replacement: did the configured log folders change (the output watch is stale)? */
    public boolean logFoldersChanged() {
        return accepted != null && logFoldersChanged;
    }
}
