package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.LogObservationState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M79-3 (spec-first-minute §8.1) — <b>the open log's file observation</b>: whether its size, modification time and file
 * identity still match what was read. Before M79-3 the frame computed it afresh at every {@code context} build, Project
 * panel refresh and bundle request ({@code MainFrame.logFreshness()}), so each surface stated its own observation. Now
 * one value, observed on request and published in the snapshot, is what {@code context}, the Project panel and the
 * evidence chip render (rule 9).
 *
 * <p><b>When it asks.</b> Whenever the adapter reports that a surface is about to state the observation
 * ({@link SessionEvents.LogFileObservationDue}: the load installed, a {@code context} read, the window regained focus, the
 * details opened, a bundle requested) for the log in force. The file can change at any moment and a metadata check is
 * cheap, so every such moment observes again; an unchanged answer publishes nothing.
 *
 * <p><b>What it refuses.</b> A due report or an answer that names another generation: an observation of a log that has
 * since been replaced can never be shown over the new one. A new generation clears what was held.
 */
public class LogObservation implements EventLogSource {

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private long boundGeneration = -1;
    private LogObservationState observation = LogObservationState.NONE;

    public LogObservation(OpenLog openLog, EffectQueue effects) {
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** A different log, or none: the observation held described the previous one. */
    @OnTrigger
    public boolean onOpenLogChanged() {
        long generation = openLog.isOpen() ? openLog.generation() : -1;
        if (generation == boundGeneration) return false;
        boundGeneration = generation;
        boolean had = observation != LogObservationState.NONE;
        observation = LogObservationState.NONE;
        if (had) auditLog.info("logObservation", "cleared").info("reason", openLog.isOpen() ? "a different log" : "no log open");
        return had;
    }

    @OnEventHandler
    public boolean onLogFileObservationDue(SessionEvents.LogFileObservationDue event) {
        if (!current(event.generation(), "LogFileObservationDue")) return false;
        auditLog.info("decision", "observeLogFile").info("generation", event.generation()).info("why", event.why());
        effects.request(new SessionEffects.ObserveLogFileEffect(0L, event.generation()));
        return false;
    }

    @OnEventHandler
    public boolean onFileObservationObserved(SessionEvents.FileObservationObserved event) {
        if (!current(event.generation(), "FileObservationObserved")) return false;
        LogObservationState next = new LogObservationState(event.generation(), event.state(), event.basis(),
                event.freshnessRef(), event.readReported(), event.readIdentified());
        // nothing moved: what is published still describes the file. The adapter names an unchanged observation by the
        // same reference, so equal means equal in every member, not only in its state.
        if (observation.equals(next)) return false;
        boolean moved = !java.util.Objects.equals(observation.state(), next.state());
        observation = next;
        if (moved) auditLog.info("logFileState", next.state());
        return true;
    }

    private boolean current(long named, String what) {
        if (!openLog.isOpen()) {
            auditLog.info("noOp", what).info("reason", "no log open");
            return false;
        }
        if (named != openLog.generation()) {
            auditLog.warn("staleFact", what).warn("generation", named).warn("current", openLog.generation());
            return false;
        }
        return true;
    }

    /** The observation in force, or {@link LogObservationState#NONE}. */
    public LogObservationState observation() {
        return observation;
    }
}
