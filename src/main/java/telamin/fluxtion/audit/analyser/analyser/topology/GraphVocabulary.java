package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.Map;

/**
 * What a GraphML file says about itself — M45 D-V1 and D-V2.
 *
 * <p>The compiler can emit a {@code fluxtion.*} vocabulary that answers, as fact, several questions
 * this analyser otherwise answers by heuristic. Whether a given file carries it is a property of
 * <b>the builder version its author pinned</b>, not of anything we control: the GraphML is emitted
 * client-side, and we open files produced by strangers. There is therefore no upgrade moment and no
 * point at which the heuristics can be deleted — this type exists so every consumer can ask
 * <i>"was I told, or did I work it out?"</i> and say which.
 *
 * <h2>Why AGGREGATED is refused</h2>
 * The emitter offers three shapes. {@code PARALLEL} carries one edge per relationship with its own
 * exact facts. {@code AGGREGATED} keeps the legacy one-edge-per-pair shape and merges the facts —
 * and, in the emitter's own words, <i>"the lists are sets, not index-aligned tuples"</i>: a pair with
 * a filtered handler and a default case yields {@code filterType="matched,defaultCase"} and one
 * {@code filterValue}, with nothing saying which went with which.
 *
 * <p>Since upstream began answering propagation per relationship, two edges on one pair can also
 * legitimately <em>disagree</em> about whether an update crosses them — so aggregation now loses a
 * fact rather than merely blurring one. A reader that rendered a merged value against the wrong
 * relationship would be <b>wrong without being able to detect it</b>, which is the class of defect
 * this product exists to refuse. So an aggregated file is read as if it carried no vocabulary at all,
 * and {@link #whyNot()} says so rather than leaving a silent downgrade.
 */
public record GraphVocabulary(Mode mode, String metaVersion, Map<String, String> graphFacts) {

    /** The MAJOR of the vocabulary this build understands: a 1.x reader accepts every 1.y. */
    public static final int SUPPORTED_MAJOR = 1;

    public enum Mode {
        /** No {@code fluxtion.*} keys — every file produced before the vocabulary existed. */
        NONE,
        /** One edge per relationship, exact facts. The only shape whose facts are usable. */
        PARALLEL,
        /** Legacy shape, facts merged into sets. Present, and deliberately not trusted. */
        AGGREGATED
    }

    public GraphVocabulary {
        graphFacts = graphFacts == null ? Map.of() : Map.copyOf(graphFacts);
    }

    public static GraphVocabulary none() {
        return new GraphVocabulary(Mode.NONE, null, Map.of());
    }

    /**
     * @param graphFacts          graph-level {@code fluxtion.*} data
     * @param sawRelationshipCount whether any edge carried {@code fluxtion.relationshipCount}, which is
     *                             the emitter's own marker for the aggregated shape
     * @param sawAnyFact          whether any {@code fluxtion.*} datum appeared anywhere
     */
    public static GraphVocabulary of(Map<String, String> graphFacts, boolean sawRelationshipCount,
                                     boolean sawAnyFact) {
        if (!sawAnyFact) {
            return none();
        }
        String version = graphFacts == null ? null : graphFacts.get("fluxtion.metaVersion");
        return new GraphVocabulary(sawRelationshipCount ? Mode.AGGREGATED : Mode.PARALLEL,
                version, graphFacts);
    }

    /** True when a file carries the vocabulary at all, whatever we then decide to do with it. */
    public boolean present() {
        return mode != Mode.NONE;
    }

    /**
     * The MAJOR this file declares, or {@code -1} when it declares nothing readable.
     *
     * <p>An unparseable or absent version is {@code -1} rather than an error: a file from the future
     * is still a file someone needs to open, and refusing to open it would be a worse failure than
     * falling back to the heuristics we already have.
     */
    public int major() {
        if (metaVersion == null || metaVersion.isBlank()) return -1;
        int dot = metaVersion.indexOf('.');
        String head = dot < 0 ? metaVersion : metaVersion.substring(0, dot);
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Whether a surface may state a fact from this file as <b>DECLARED</b>.
     *
     * <p>Three conditions, and each rules out a different way of being confidently wrong: the
     * vocabulary is present, its shape is per-relationship, and its MAJOR is one this build knows how
     * to read.
     */
    public boolean trusted() {
        return mode == Mode.PARALLEL && major() == SUPPORTED_MAJOR;
    }

    /**
     * Why the facts in this file are not being used — {@code null} when they are.
     *
     * <p>Always a reason a person can act on, never a bare refusal: an aggregated file is regenerable
     * with a different switch, and a future MAJOR means this analyser is the thing that is behind.
     */
    public String whyNot() {
        if (trusted()) {
            return null;
        }
        return switch (mode) {
            case NONE -> "this graph carries no fluxtion.* metadata — it was emitted by a builder from "
                    + "before the vocabulary existed, or with it switched off";
            case AGGREGATED -> "this graph is in the AGGREGATED shape, where per-relationship facts are "
                    + "merged into sets — a filter value cannot be matched back to the handler it "
                    + "belongs to, so the facts are read as absent rather than half-trusted. "
                    + "Regenerate with -Dfluxtion.graphml.metadata=PARALLEL to use them";
            case PARALLEL -> "this graph declares metadata version " + metaVersion + "; this build reads "
                    + SUPPORTED_MAJOR + ".x, so its facts are read as absent rather than guessed at";
        };
    }

    /** The declared node total, or {@code -1}. A reader can use it to detect a truncated file. */
    public int declaredNodeCount() {
        String raw = graphFacts.get("fluxtion.nodeCount");
        try {
            return raw == null ? -1 : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
