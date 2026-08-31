package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * What project is <b>in force</b> — not what was asked for, and not what parsed.
 *
 * <p>It advances on {@link SessionEvents.ProfileApplied}, which is the adapter saying the settings are
 * genuinely applied. Deliberately <b>not</b> on {@code ProfileLoaded}: that only means the file read
 * without error. And deliberately not on {@code OpenProjectRequested}, which means nothing at all yet.
 *
 * <p>The distinction is not fastidiousness. All three project operations are fallible, and the code
 * this replaces already gets the order right — {@code ProjectSession} closes the current session only
 * after {@code ProjectProfile.load} succeeds, so a bad path closes nothing. Advancing on the request
 * would have made the processor a worse recorder than the callbacks it is replacing.
 */
public class ActiveProject implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private String profilePath;
    private String name;

    public ActiveProject(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        if (!gate.accepted()) {
            return false;
        }
        profilePath = event.profilePath();
        name = event.name();
        auditLog.info("activeProject", name).info("path", profilePath);
        return true;
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored event) {
        if (!gate.accepted()) {
            return false;
        }
        profilePath = null;
        name = null;
        auditLog.info("activeProject", "none");
        return true;
    }

    public boolean isActive() {
        return profilePath != null;
    }

    /** Whether the given profile is the one already in force — the input to the no-op rule. */
    public boolean isAt(String candidatePath) {
        return profilePath != null && profilePath.equals(candidatePath);
    }

    public String profilePath() {
        return profilePath;
    }

    public String name() {
        return name;
    }
}
