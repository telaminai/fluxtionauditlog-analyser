package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.parse.EvidenceVerdict;
import telamin.fluxtion.audit.analyser.analyser.parse.RunSegments;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamHeader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Evidence-set key ({@code docs/specs/spec-evidence-set-key.md} §4.5): whether the graph on screen has the SHAPE of the
 * graph that wrote the open log, as the {@code evidenceSet} node decided it, and the log's runs.
 *
 * <p><b>Graph shape, never build (D4).</b> A {@code sourceFingerprint} digests node names, classes, callbacks and edges.
 * Equal fingerprints mean the same graph shape; method bodies and configured values are not in the digest, so no
 * wording here or on any surface says "same build".
 *
 * <p><b>Three verdicts, and a warning is never a refusal (D2).</b>
 * <ul>
 *   <li>{@link Verdict#MATCH}: every stream header in the log states a fingerprint, and each equals the graph's.</li>
 *   <li>{@link Verdict#MISMATCH}: a header states a fingerprint, the graph states one, and they differ — including a
 *       graph the person opened by hand. A persistent warning; the pairing stays as the person made it.</li>
 *   <li>{@link Verdict#UNVERIFIED}: nothing proves either — no header (a log from before runtime 1.1.4), a malformed
 *       header, a header or graph that states no fingerprint (an interpreted processor; a key type the compiler now
 *       refuses, FLX-1010), or records before the first header. Missing evidence is never a mismatch.</li>
 * </ul>
 * A proven difference outranks missing evidence: a log with one header that differs and one that states nothing is a
 * MISMATCH, because the difference is a fact.
 *
 * @param verdict           null when there is no pair to judge (no log, no graph) or the log's headers are not read yet
 * @param logFingerprint    the latest header's fingerprint (it describes the records being written), or null
 * @param logFingerprints   every distinct fingerprint the log's headers state, in order; null for a header stating none
 * @param graphFingerprint  the graph's {@code fluxtion.sourceFingerprint}, or null
 * @param reason            why, in words a person reads; never "same build"
 * @param headers           how many stream headers the log carries; -1 before they are read
 * @param segments          the log's runs by run key; empty when it has no headers
 * @param shape             what the log states, as the shared judgement read it; null before the headers are read
 * @param logGeneration     the log the verdict is about
 * @param graphRevision     the graph the verdict is about
 */
public record EvidenceSetState(Verdict verdict, String logFingerprint, List<String> logFingerprints,
                               String graphFingerprint, String reason, int headers,
                               List<RunSegments.Segment> segments, long logGeneration, long graphRevision,
                               EvidenceVerdict.LogShape shape) {

    public enum Verdict { MATCH, MISMATCH, UNVERIFIED }

    public static final EvidenceSetState NONE =
            new EvidenceSetState(null, null, List.of(), null, null, -1, List.of(), -1, -1, null);

    public EvidenceSetState {
        logFingerprints = java.util.Collections.unmodifiableList(new ArrayList<>(logFingerprints == null ? List.of() : logFingerprints));
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    /** The status line's words: exactly one of three, or null when there is no pair. */
    public String line() {
        if (verdict == null) return null;
        return switch (verdict) {
            case MATCH -> "graph shape matches";
            case MISMATCH -> "graph shape does not match";
            case UNVERIFIED -> "graph not verified";
        };
    }

    /** As {@link #decide(boolean, long, List, int, List, boolean, long, String, String)} for one stream, builder unknown. */
    public static EvidenceSetState decide(boolean logOpen, long generation, List<StreamHeader> headers, int records,
                                          boolean graphOpen, long revision, String graphFingerprint) {
        return decide(logOpen, generation, headers, records, List.of(), graphOpen, revision, graphFingerprint, null);
    }

    /**
     * The decision, pure: {@link EvidenceVerdict} — the one judgement discovery and {@code --verify} use too.
     *
     * @param headers the log's stream headers, or null when they have not been read for this generation
     * @param breaks  rows where one stream ended and another began ({@code LogStore.streamBreaks()})
     */
    public static EvidenceSetState decide(boolean logOpen, long generation, List<StreamHeader> headers, int records,
                                          List<Integer> breaks, boolean graphOpen, long revision,
                                          String graphFingerprint, String graphToolchain) {
        if (!logOpen) return NONE;
        if (headers == null) {
            return new EvidenceSetState(null, null, List.of(), graphFingerprint, null, -1, List.of(), generation, revision, null);
        }
        EvidenceVerdict.LogShape shape = EvidenceVerdict.shape(headers, records, breaks);
        if (!graphOpen) {
            // no pair: the runs and the log's own statement are still published
            return new EvidenceSetState(null, shape.latest(), shape.fingerprints(), null, null, shape.headers(),
                    shape.segments(), generation, revision, shape);
        }
        EvidenceVerdict.Judgement j = EvidenceVerdict.judge(shape, graphFingerprint, graphToolchain);
        return new EvidenceSetState(Verdict.valueOf(j.verdict().name()), shape.latest(), shape.fingerprints(),
                graphFingerprint, j.reason(), shape.headers(), shape.segments(), generation, revision, shape);
    }

    /** What {@code context} and every result over a pair carry: the verdict and both fingerprints. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceSet", verdict == null ? null : verdict.name());
        m.put("evidenceSetStatus", line());
        m.put("logSourceFingerprint", logFingerprint);
        if (logFingerprints.size() > 1) m.put("logSourceFingerprints", logFingerprints);
        m.put("graphSourceFingerprint", graphFingerprint);
        m.put("evidenceSetNote", reason);
        m.put("evidenceSetMeaning", "a sourceFingerprint digests the graph's SHAPE (node names, classes, callbacks, edges); "
                + "equal means the same graph shape, never the same build");
        return m;
    }

    /** The runs, as {@code context.log.segments} carries them. */
    public List<Map<String, Object>> segmentMaps() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RunSegments.Segment s : segments) out.add(s.toMap());
        return out;
    }

}
