package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-set key ({@code docs/specs/spec-evidence-set-key.md} §4.5): THE one judgement of whether a graph has the
 * shape of the graph that wrote a log. The session's {@code evidenceSet} node, graph discovery and {@code --verify} all
 * call it, so the three can never disagree about the same log and graph (independent review F2, F3).
 *
 * <p>Two steps. {@link #shape} reads what the LOG states — every fingerprint its headers state, which records no header
 * covers (before the first header, and after a stream break with none following it: review F1), and whether any header
 * is unreadable. {@link #judge} compares that with one GRAPH's fingerprint.
 *
 * <ul>
 *   <li>{@code MISMATCH}: a header and the graph both state a fingerprint and they differ. A proven difference outranks
 *       missing evidence. When the header's builder ({@code toolchainVersion}) and the graph's are both known and
 *       differ, the reason says the difference may be in how the shape was digested rather than in the shape (F5).</li>
 *   <li>{@code UNVERIFIED}: no header; an unreadable header; records no header covers; a header or the graph stating no
 *       fingerprint. Missing evidence is never a mismatch.</li>
 *   <li>{@code MATCH}: otherwise — every header states the graph's fingerprint, and every record is under a header.</li>
 * </ul>
 * The words are "graph shape", never "same build" (D4); nothing here refuses anything (D2).
 */
public final class EvidenceVerdict {

    private EvidenceVerdict() {
    }

    public enum Verdict { MATCH, MISMATCH, UNVERIFIED }

    /**
     * What a log states about the graph that wrote it.
     *
     * @param headers       how many stream headers it carries
     * @param fingerprints  every distinct fingerprint its readable headers state, in order; a trailing null when one
     *                      stated none
     * @param toolchains    the builder versions of the headers, by fingerprint they state (null key for none)
     * @param latest        the latest readable header's fingerprint (it describes the records being written), or null
     * @param unverified    why the log alone cannot be verified (unreadable header, uncovered records, a null
     *                      fingerprint, no header), or null when every record is under a header that states one
     * @param segments      its runs ({@link RunSegments})
     */
    public record LogShape(int headers, List<String> fingerprints, java.util.Map<String, String> toolchains,
                           String latest, String unverified, List<RunSegments.Segment> segments) {
        public LogShape {
            fingerprints = java.util.Collections.unmodifiableList(new ArrayList<>(fingerprints));
            toolchains = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(toolchains));
            segments = List.copyOf(segments);
        }

        /** The fingerprints a header stated, without the "stated none" marker. */
        public List<String> stated() {
            return fingerprints.stream().filter(Objects::nonNull).toList();
        }
    }

    /** The judgement and its reason, in a person's words. */
    public record Judgement(Verdict verdict, String reason) { }

    /**
     * Read a log's shape.
     *
     * @param breaks rows at which a stream ended and another began ({@link LogStore#streamBreaks()}); may be empty
     */
    public static LogShape shape(List<StreamHeader> headers, int records, Collection<Integer> breaks) {
        List<StreamHeader> hs = headers == null ? List.of() : headers;
        List<RunSegments.Segment> segments = RunSegments.of(hs, records, breaks);
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        java.util.Map<String, String> toolchains = new java.util.LinkedHashMap<>();
        boolean unstated = false;
        boolean unreadable = false;
        String latest = null;
        for (StreamHeader h : hs) {
            if (!h.valid()) { unreadable = true; continue; }
            if (h.sourceFingerprint() == null) unstated = true;
            else {
                distinct.add(h.sourceFingerprint());
                toolchains.putIfAbsent(h.sourceFingerprint(), h.toolchainVersion());
            }
            latest = h.sourceFingerprint();
        }
        List<String> prints = new ArrayList<>(distinct);
        if (unstated) prints.add(null);
        String why = null;
        if (hs.isEmpty()) {
            why = "the log carries no stream header (written by Fluxtion runtime 1.1.4 and later), so nothing in it "
                    + "states which graph shape wrote it";
        } else if (unreadable) {
            why = "a stream header in the log is not well formed, so the shape of the graph that wrote the records "
                    + "after it is not stated";
        } else {
            RunSegments.Segment bare = segments.stream()
                    .filter(s -> s.kind() == RunSegments.Kind.UNHEADED && s.records() > 0).findFirst().orElse(null);
            if (bare != null) {
                boolean one = bare.records() == 1;
                why = bare.span() + (bare.firstRecord() == 0 ? (one ? " comes" : " come") + " before the first stream header"
                        : (one ? " follows" : " follow") + " a stream end or the start of a file with no stream header")
                        + ", so no header states which graph shape wrote them";
            } else if (unstated) {
                why = "the log's stream header states no graph fingerprint (an interpreted processor, or a producer "
                        + "with no descriptor)";
            }
        }
        return new LogShape(hs.size(), prints, toolchains, latest, why, segments);
    }

    /**
     * Judge a log's shape against one graph.
     *
     * @param graphFingerprint the graph's {@code fluxtion.sourceFingerprint}, or null
     * @param graphToolchain   the graph's {@code fluxtion.toolchainVersion}, or null
     */
    public static Judgement judge(LogShape log, String graphFingerprint, String graphToolchain) {
        List<String> differing = graphFingerprint == null ? List.of()
                : log.stated().stream().filter(f -> !f.equals(graphFingerprint)).toList();
        if (!differing.isEmpty()) {
            LinkedHashSet<String> builders = new LinkedHashSet<>();
            boolean known = graphToolchain != null && !graphToolchain.isBlank();
            for (String f : differing) {
                String t = log.toolchains().get(f);
                if (t == null || t.isBlank()) known = false;
                else builders.add(t);
            }
            boolean otherBuilder = known && builders.stream().anyMatch(t -> !t.equals(graphToolchain));
            String reason = otherBuilder
                    ? "the fingerprints differ (log " + abbreviate(String.join(", ", differing)) + ", graph "
                    + abbreviate(graphFingerprint) + "); the log and the graph were digested by two builder versions ("
                    + String.join(", ", builders) + " vs " + graphToolchain + "), so the difference may be in how the "
                    + "shape was digested rather than in the shape"
                    : "the fingerprints differ (log " + abbreviate(String.join(", ", differing)) + ", graph "
                    + abbreviate(graphFingerprint) + "): the graph on screen is not the shape of the graph that wrote "
                    + "the log, so absences and coverage may describe the wrong graph";
            return new Judgement(Verdict.MISMATCH, reason);
        }
        if (log.unverified() != null) return new Judgement(Verdict.UNVERIFIED, log.unverified());
        if (graphFingerprint == null) {
            return new Judgement(Verdict.UNVERIFIED,
                    "the graph states no fingerprint (fluxtion.sourceFingerprint), so it cannot be compared");
        }
        return new Judgement(Verdict.MATCH, "the log's stream header" + (log.headers() == 1 ? " states" : "s state")
                + " graph shape " + abbreviate(graphFingerprint) + ", the shape of the graph on screen: the same nodes, "
                + "classes, callbacks and edges. Method bodies and configured values are not in the fingerprint, so it "
                + "says nothing about them");
    }

    static String abbreviate(String fingerprint) {
        if (fingerprint == null) return "(none)";
        return fingerprint.length() > 12 && !fingerprint.contains(",") ? fingerprint.substring(0, 12) + "…" : fingerprint;
    }
}
