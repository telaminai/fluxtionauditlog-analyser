package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * <b>What the Records view shows, and what each filter hides</b> — the counts behind "showing 3 of 21", the Records
 * title's "18 hidden by event types", the Event types toggle's "1 of 7", each checklist row's "shown / total" and the
 * empty table's list of filters to undo.
 *
 * <p><b>Why a node, and why it decides WHEN</b> (rule 9). The "showing N of M" label was composed in the frame's
 * filter listener from the table's row count, at a call site of its own; a filter set through the action socket
 * changed the table but nothing told the person which filter had done it. Now one value, measured on request and
 * published in the snapshot, is what every one of those surfaces renders.
 *
 * <p><b>When it asks.</b> When the view of a log is installed (a new log opened under the same unconstrained filter
 * moves no filter key); when the filter key moves; when Follow appends records; and when flags change while "flagged
 * only" is on, or "flagged only" is turned on or off. A new log generation clears what it held: the counts described
 * the previous log.
 *
 * <p><b>What it refuses.</b> A measurement that names another generation, and one the adapter could not make (no
 * installed view), which is recorded and changes nothing.
 */
public class RecordsView implements EventLogSource {

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private long boundGeneration = -1;
    private long installedGeneration = -1;
    private String filterKey;
    private boolean flaggedOnly;
    private RecordsViewCounts counts;

    public RecordsView(OpenLog openLog, EffectQueue effects) {
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** A different log, or none: the counts held described the previous one. */
    @OnTrigger
    public boolean onOpenLogChanged() {
        long generation = openLog.isOpen() ? openLog.generation() : -1;
        if (generation == boundGeneration) return false;
        boolean had = counts != null;
        counts = null;
        installedGeneration = -1;
        flaggedOnly = false;                               // flags are per log: a new one opens with none
        boundGeneration = generation;
        if (had) auditLog.info("recordsView", "cleared").info("reason", openLog.isOpen() ? "a different log" : "no log open");
        return had;
    }

    @OnEventHandler
    public boolean onRecordsViewInstalled(SessionEvents.RecordsViewInstalled event) {
        if (!current(event.generation(), "RecordsViewInstalled")) return false;
        installedGeneration = event.generation();
        requestMeasure("view installed");
        return false;
    }

    @OnEventHandler
    public boolean onViewFilterChanged(SessionEvents.ViewFilterChanged event) {
        boolean moved = !java.util.Objects.equals(filterKey, event.filterKey());
        filterKey = event.filterKey();
        if (moved && installed()) requestMeasure("filter changed");
        return false;
    }

    @OnEventHandler
    public boolean onLogAppended(SessionEvents.LogAppended event) {
        if (!current(event.generation(), "LogAppended") || !installed()) return false;
        requestMeasure("records appended");
        return false;
    }

    @OnEventHandler
    public boolean onRecordFlagsChanged(SessionEvents.RecordFlagsChanged event) {
        if (!current(event.generation(), "RecordFlagsChanged") || !installed()) return false;
        boolean was = flaggedOnly;
        flaggedOnly = event.flaggedOnly();
        if (was != flaggedOnly || flaggedOnly) {
            requestMeasure(was != flaggedOnly ? "flagged only " + (flaggedOnly ? "on" : "off") : "flags changed");
        } else {
            auditLog.info("noOp", "RecordFlagsChanged").info("reason", "flagged only is off: a flag hides nothing");
        }
        return false;
    }

    @OnEventHandler
    public boolean onRecordsViewMeasured(SessionEvents.RecordsViewMeasured event) {
        if (!current(event.generation(), "RecordsViewMeasured")) return false;
        if (event.counts() == null) {
            auditLog.info("noOp", "RecordsViewMeasured").info("reason", "no installed view to measure");
            return false;
        }
        if (event.counts().equals(counts)) return false;
        counts = event.counts();
        auditLog.info("shown", counts.shown()).info("total", counts.total())
                .info("hiddenByTypes", counts.hiddenByTypes()).info("hiddenByText", counts.hiddenByText())
                .info("hiddenByTime", counts.hiddenByTime()).info("hiddenByFlags", counts.hiddenByFlags());
        return true;
    }

    private boolean installed() {
        return openLog.isOpen() && installedGeneration == openLog.generation();
    }

    private void requestMeasure(String why) {
        long generation = openLog.generation();
        auditLog.info("decision", "measureRecordsView").info("generation", generation).info("why", why);
        effects.request(new SessionEffects.MeasureRecordsViewEffect(0L, generation));
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

    /** The records view of the open log as last measured, or null before the first measurement (or with no log). */
    public RecordsViewCounts counts() {
        return counts;
    }
}
