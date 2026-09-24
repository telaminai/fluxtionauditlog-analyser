package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The whole-log and narrower qualifications held against ONE published pairing (M68.1 round 3, N2).
 *
 * <p>The rule, which the previous plain overwrite broke: <b>a narrower comparison never replaces a wider one.</b>
 * <ul>
 *   <li>A whole-log comparison becomes the widest, and drops any earlier narrower one. It dominates it: every id a
 *       filter can find undeclared, the whole log finds too, and an earlier filter's result would otherwise go on
 *       describing a view that may no longer be current.</li>
 *   <li>A filtered comparison is kept beside the widest, as the latest narrower one. It never replaces the widest,
 *       because the widest may be the only thing that has proved the sample on open wrong.</li>
 * </ul>
 * Before this, looking at the whole log and then narrowing the view put the superseded sample back in the lead of the
 * Topology status line and erased the whole-log finding from {@code context}.
 *
 * <p>Pure, so the rule is tested without a window. The caller binds the holder to one pairing and resets it when that
 * pairing is replaced for a different log or graph.
 */
public final class PairingQualifications {

    private PairingQualification widest;
    private PairingQualification narrower;

    /** Record the comparison just made. Returns the sentence the coverage reply states for it. */
    public String record(PairingQualification q) {
        if (q.wholeLog()) {
            widest = q;
            narrower = null;
            return q.note();
        }
        narrower = q;
        return widest == null ? q.note()
                : q.note() + " · still in force from the whole log: " + widest.headline();
    }

    public void clear() {
        widest = null;
        narrower = null;
    }

    public boolean isEmpty() {
        return widest == null && narrower == null;
    }

    /** The widest comparison, read at the log's current size so a grown log shows it as stale. */
    public PairingQualification widest(int logRecordsNow) {
        return widest == null ? null : widest.atLogSize(logRecordsNow);
    }

    public PairingQualification narrower(int logRecordsNow) {
        return narrower == null ? null : narrower.atLogSize(logRecordsNow);
    }

    /** {@code context.graphPairing.qualifiedBy}: the widest leads, and a narrower one rides beside it. */
    public Map<String, Object> toMap(int logRecordsNow) {
        PairingQualification w = widest(logRecordsNow);
        PairingQualification n = narrower(logRecordsNow);
        if (w == null && n == null) return null;
        Map<String, Object> out = new LinkedHashMap<>((w != null ? w : n).toMap());
        if (w != null && n != null) out.put("narrower", n.toMap());
        return out;
    }

    public String panelNote(GraphPairing published, int logRecordsNow) {
        return PairingQualification.panelNote(published, widest(logRecordsNow), narrower(logRecordsNow));
    }
}
