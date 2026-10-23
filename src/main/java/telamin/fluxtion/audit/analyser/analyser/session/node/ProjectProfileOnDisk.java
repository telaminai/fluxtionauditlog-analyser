package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * #177: the open project's profile changed on disk — what that means, and what happens next.
 *
 * <p>The analyser auto-saves its in-memory profile, and {@code open {project}} on the active project is a no-op, so an
 * edit made to {@code .analyser/project.fluxtion-settings} by anyone else (an editor, an agent, a pull) was silently
 * overwritten by the next save. The adapter now refuses a write over content it did not read or write itself, and
 * reports what it sees of the file as a fact ({@link SessionEvents.ProjectProfileObserved}). This node decides: equal
 * digests are no change; a different one is a change, OFFERED to the person once per distinct content
 * ({@link SessionEffects.OfferProjectProfileReloadEffect}); and only their answer — or an explicit
 * {@code open {project, reload: true}} — reloads, or writes their version over the file. Never both, never neither
 * without saying so: until they choose, the file wins and the session's edits wait in memory.
 */
public class ProjectProfileOnDisk implements EventLogSource {

    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    /** The profile the last observation was about, by key (a digest of its path — never the path), or null. */
    private String path;
    private boolean changed;
    private boolean saveRefused;
    /** The on-disk content last offered, so one change is offered once. */
    private String offered;
    private boolean deferred;
    private String outcome = "";
    /** Where unsaved edits were last set aside, as {@code …/name}; survives the switch that caused it. */
    private String setAside;

    public ProjectProfileOnDisk(EffectQueue effects) {
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** A project came into force or was left: what was said about the previous profile no longer applies. */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        reset(null);                               // the adapter's next observation names the new profile by key
        return false;
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored event) {
        reset(null);
        return false;
    }

    private void reset(String now) {
        path = now;
        changed = false;
        saveRefused = false;
        offered = null;
        deferred = false;
        outcome = "";
    }

    @OnEventHandler
    public boolean onObserved(SessionEvents.ProjectProfileObserved e) {
        if (e.profileKey() == null) return false;
        if (!e.profileKey().equals(path)) reset(e.profileKey());
        if (e.sessionDigest() != null && e.sessionDigest().equals(e.onDiskDigest())) {
            if (!changed) return false;
            changed = false;                       // back in step (reverted on disk, or our own write landed)
            saveRefused = false;
            auditLog.info("profileOnDisk", "inStep");
            return true;
        }
        changed = true;
        saveRefused = saveRefused || e.saveRefused();
        String content = String.valueOf(e.onDiskDigest());
        if (content.equals(offered)) return true;  // this content was already offered: say it once
        offered = content;
        deferred = false;
        outcome = "";
        auditLog.info("profileOnDisk", "changed").info("saveRefused", saveRefused);
        effects.request(new SessionEffects.OfferProjectProfileReloadEffect(0L, path, reason()));
        return true;
    }

    /** The adapter set the window's unsaved edits aside while leaving; say where, once, on the status line. */
    @OnEventHandler
    public boolean onSetAside(SessionEvents.ProjectProfileSetAside e) {
        setAside = e.shown();
        auditLog.info("profileOnDisk", "setAside");
        effects.request(new SessionEffects.ShowStatusEffect(0L, "Unsaved project edits kept in " + setAside
                + " — the profile had changed on disk"));
        return true;
    }

    /**
     * #182: a write made the project's {@code analyser-exchange/} — a new folder in the person's repository, said ONCE on
     * the status line (this node owns what the session says about the project's files on disk).
     */
    @OnEventHandler
    public boolean onExchangeCreated(SessionEvents.ProjectExchangeCreated e) {
        auditLog.info("exchange", "created");
        effects.request(new SessionEffects.ShowStatusEffect(0L,
                telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir.CREATED_NOTE));
        return true;
    }

    /** The banner as decided here, for the snapshot: the open profile while its change waits for an answer, else null. */
    public telamin.fluxtion.audit.analyser.analyser.session.ProfileOnDiskState banner() {
        return changed && !deferred && path != null
                ? new telamin.fluxtion.audit.analyser.analyser.session.ProfileOnDiskState(path, saveRefused) : null;
    }

    @OnEventHandler
    public boolean onChoice(SessionEvents.ProjectProfileChoice e) {
        if (e.profileKey() == null || !e.profileKey().equals(path)) {
            // an explicit reload of the open profile is honoured even with no change observed (open {project, reload})
            if (e.choice() != SessionEvents.ProfileChoice.RELOAD || e.profileKey() == null) {
                auditLog.info("staleFact", "ProjectProfileChoice").info("path", String.valueOf(e.profileKey()));
                return false;
            }
            reset(e.profileKey());
        }
        auditLog.info("profileChoice", e.choice().name()).info("origin", String.valueOf(e.origin()));
        switch (e.choice()) {
            case RELOAD -> effects.request(new SessionEffects.ReloadProjectProfileEffect(0L, path, e.origin()));
            case KEEP_MINE -> {
                if (!changed) return false;        // nothing on disk to keep yours over
                effects.request(new SessionEffects.KeepProjectProfileEffect(0L, path));
            }
            case LATER -> deferred = true;
        }
        return true;
    }

    @OnEventHandler
    public boolean onReloaded(SessionEvents.ProjectProfileReloaded e) {
        if (!java.util.Objects.equals(e.profileKey(), path)) return false;
        if (e.ok()) {
            changed = false;
            saveRefused = false;
            deferred = false;
        }
        outcome = e.message();
        effects.request(new SessionEffects.ShowStatusEffect(e.opId(), outcome));
        return true;
    }

    @OnEventHandler
    public boolean onKept(SessionEvents.ProjectProfileKept e) {
        if (!java.util.Objects.equals(e.profileKey(), path)) return false;
        if (e.ok()) {
            changed = false;
            saveRefused = false;
            deferred = false;
        }
        outcome = e.message();
        effects.request(new SessionEffects.ShowStatusEffect(e.opId(), outcome));
        return true;
    }

    /** The node's sentence for the offer and for {@code context}. */
    public String reason() {
        return "Project settings changed on disk"
                + (saveRefused ? " — this session's edits were NOT saved over them" : "")
                + ". Reload from disk, or keep this session's version";
    }

    /** Whether the open profile's file differs from what this session read or wrote. */
    public boolean changed() {
        return changed;
    }

    /** Whether the offer for THIS content may still be presented (a later answer or change retires it). */
    public boolean mayOffer(String profileKey) {
        return changed && !deferred && java.util.Objects.equals(profileKey, path);
    }

    /** For {@code context.project.profileOnDisk}: null when the profile is in step and nothing was just done. */
    public java.util.Map<String, Object> state() {
        if (path == null || (!changed && outcome.isEmpty())) {
            return setAside == null ? null : new java.util.LinkedHashMap<>(java.util.Map.of("setAside", setAside));
        }
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("state", changed ? "changedOnDisk" : "inStep");
        if (changed) {
            out.put("reason", reason());
            out.put("saveRefused", saveRefused);
            out.put("reload", "open {project: \"<this profile>\", reload: true} takes the file's version (unsaved "
                    + "in-memory edits are discarded); the person can instead keep this session's version");
        }
        if (!outcome.isEmpty()) out.put("last", outcome);
        if (setAside != null) out.put("setAside", setAside);
        return out;
    }

}
