package telamin.fluxtion.audit.analyser.analyser.session.view;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * View-model spike (2026-09-27): WHAT the log's status line states, as the session decided it — never how it is drawn.
 *
 * <p>No composed text and no layout: a backend turns this into pixels, a string, HTML or a recorded fact. Every field is
 * a value the session already owns (OpenLog, LogEvidence) or observed from the store ({@code LogShapeObserved}), so two
 * backends given the same view cannot disagree about the log — only about typography.
 *
 * @param generation         the log generation the line describes
 * @param following          whether Follow is on
 * @param location           the log's location as opened
 * @param provenance         the declared or inferred system the log came from, or null
 * @param records            the record count the session knows
 * @param firstLogTime       the earliest log time, or null for a log with no timestamps
 * @param lastLogTime        the latest log time, or null
 * @param knownComplete      the file claims it is whole (audit format 1.1 §1a)
 * @param timeOrderViolations how many time-order violations the last scan found
 * @param producerWarning    the KIND of the first producer warning, or null when there is none
 * @param pendingRecords     trailing records still being written
 * @param eofIncluded        records included at EOF with no closing separator
 * @param readFailure        why the last Follow read failed, or null
 * @param reopenedReason     why the log was reopened as a new generation, or null
 */
public record StatusLineView(long generation, boolean following, String location, String provenance, int records,
                             Long firstLogTime, Long lastLogTime, boolean knownComplete, int timeOrderViolations,
                             String producerWarning, int pendingRecords, int eofIncluded, String readFailure,
                             String reopenedReason) {

    /**
     * The fields that differ from {@code previous}, by name, in declaration order — everything when there is no
     * previous view. This is what the audit records: a render is described by what it CHANGED, so an append under Follow
     * costs one or two entries (records, lastLogTime) rather than the whole view.
     */
    public Map<String, Object> changedFrom(StatusLineView previous) {
        Map<String, Object> changed = new LinkedHashMap<>();
        Map<String, Object> now = fields();
        Map<String, Object> before = previous == null ? Map.of() : previous.fields();
        now.forEach((k, v) -> {
            if (previous == null || !Objects.equals(v, before.get(k))) changed.put(k, v);
        });
        return changed;
    }

    /** Every field by name — the order is the record's. Nulls are kept, so a cleared field shows as a change. */
    public Map<String, Object> fields() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("generation", generation);
        f.put("following", following);
        f.put("location", location);
        f.put("provenance", provenance);
        f.put("records", records);
        f.put("firstLogTime", firstLogTime);
        f.put("lastLogTime", lastLogTime);
        f.put("knownComplete", knownComplete);
        f.put("timeOrderViolations", timeOrderViolations);
        f.put("producerWarning", producerWarning);
        f.put("pendingRecords", pendingRecords);
        f.put("eofIncluded", eofIncluded);
        f.put("readFailure", readFailure);
        f.put("reopenedReason", reopenedReason);
        return f;
    }
}
