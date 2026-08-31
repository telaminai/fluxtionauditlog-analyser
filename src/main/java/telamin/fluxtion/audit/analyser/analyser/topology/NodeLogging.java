package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M40.2b — can this node write audit output AT ALL?
 *
 * <p>A node with no way to reach an {@code EventLogger} is silent by construction: its absence from a
 * log is not evidence of anything, and counting it as "never ran" reports a category error as a low
 * score — the same defect M40.2a fixed for event classes, one level deeper.
 *
 * <h2>What actually makes a node able to log (rule 6: READ it, never infer)</h2>
 * The tracker recorded this slice as "a node that does not extend {@code EventLogNode} is silent by
 * construction". <b>That premise is wrong</b>, and the shipped demo contains the counterexample:
 * {@code RiskMonitor extends SingleNamedNode} — not {@code EventLogNode} — and calls
 * {@code auditLog.info(…)} on line 108. Read from the runtime jar rather than assumed, the real
 * contract is the interface {@link #EVENT_LOG_SOURCE} ({@code setLogger(EventLogger)});
 * {@code EventLogNode} is only a convenience base that implements it, and nine further framework
 * classes extend that base transitively. Had this been built on the tracker's wording, a node that
 * demonstrably logs would have been dropped from the denominator — a false exclusion, which flatters
 * the score and is the harder error to notice.
 *
 * <h2>Only exclude when CERTAIN</h2>
 * Excluding shrinks the denominator and improves the number, so the burden of proof sits on exclusion.
 * A verdict of {@link Capability#SILENT_BY_CONSTRUCTION} is returned only when the source is in hand
 * AND the class declares no supertype at all AND its body names neither the logger type nor the
 * contract. Everything else, including every case where the source is missing or the supertype cannot
 * be recognised, is {@link Capability#UNKNOWN} and stays counted. Assuming silence is the one error that
 * cannot be spotted from the output.
 *
 * <h2>The honest size of the claim (review N1)</h2>
 * What this proves is <b>"this file gives no way to reach a logger"</b>, not "this class cannot log".
 * One file is the whole evidence base, so a class that logs through a helper which never names the type
 * — {@code Loggers.forNode(this).info(…)} — still reads as silent, and no text check closes that; only
 * following the helper would. The gap is narrow and the shape unusual, but callers should quote the
 * verdict at its real width rather than the wider one.
 */
public final class NodeLogging {

    private NodeLogging() {
    }

    public static final String EVENT_LOG_SOURCE = "EventLogSource";

    /**
     * Types whose presence in an {@code extends}/{@code implements} clause proves the node can log.
     *
     * <p>MEASURED, not guessed: every class in {@code fluxtion-runtime} was walked to its root and
     * kept if it reached {@code EventLogNode} or implemented {@code EventLogSource}. Simple names,
     * because that is what an {@code extends} clause in source text gives us. If the framework adds a
     * base, an unknown one lands in UNKNOWN and stays counted — the safe direction.
     */
    static final Set<String> AUDIT_CAPABLE = Set.of(
            "EventLogNode", EVENT_LOG_SOURCE,
            "AbstractNode", "BaseNode", "NamedBaseNode", "SingleNamedNode",
            "ObjectEventHandlerNode", "ServiceRegistryNode", "NamedNodeSimple",
            "BatchDtoHandler", "InstanceCallbackEvent");

    public enum Capability {
        /** Reaches an EventLogger — a gap in its coverage is a real observation. */
        CAN_LOG,
        /** Cannot reach one: absence from the log says nothing about whether it ran. */
        SILENT_BY_CONSTRUCTION,
        /** No evidence either way. Stays in the denominator — never assume silence. */
        UNKNOWN
    }

    /**
     * @param fqn    the node's class, as the graph declares it ({@code a.b.Outer$Inner} for a nested type)
     * @param source fqn → source text, or empty when it cannot be found (typically
     *               {@code SourceService::sourceForFqn}). Null is treated as "no source available".
     */
    /**
     * How an answer was reached — M45 D-V2. A surface that changes answer with the vocabulary must
     * never let a measured fact and a heuristic one look alike.
     */
    public enum Basis {
        /** The compiler said so, in the graph. */
        DECLARED,
        /** We worked it out by reading source, with all the limits stated above. */
        INFERRED
    }

    /** A capability and how it was established. */
    public record Answer(Capability capability, Basis basis) {

        public boolean canLog() {
            return capability == Capability.CAN_LOG;
        }

        /** For a surface that has to justify itself: "declared by the graph" / "read from source". */
        public String because() {
            return basis == Basis.DECLARED
                    ? "declared by the processor's own graph (fluxtion.auditCapable)"
                    : "read from the node's source — the graph did not say";
        }
    }

    /**
     * <b>Ask the graph first.</b> {@code fluxtion.auditCapable} is the compiler's own answer to the
     * question the source-reading below can only approximate, and it needs no source at all — which
     * matters because the honest limit of the text check is that it fails closed to
     * {@link Capability#UNKNOWN} whenever the source is missing, and missing source is the normal
     * case for a log someone else produced.
     *
     * <p>Only consulted when {@link GraphVocabulary#trusted()}: an aggregated file or an unreadable
     * MAJOR falls through to the source check rather than being half-believed.
     *
     * @param node       the node as the graph declared it, or {@code null}
     * @param vocabulary what the file said about itself
     */
    public static Answer of(ProcessorTopology.Node node, GraphVocabulary vocabulary,
                            SourceResolver source) {
        if (node != null && vocabulary != null && vocabulary.trustedForNodeFacts()) {
            String declared = node.fact("fluxtion.auditCapable");
            if ("true".equalsIgnoreCase(declared)) {
                return new Answer(Capability.CAN_LOG, Basis.DECLARED);
            }
            if ("false".equalsIgnoreCase(declared)) {
                // The compiler knows the node's whole type hierarchy, which the text check does not.
                // This is the case that was previously unreachable: a node the analyser could only
                // ever call UNKNOWN, now stated.
                return new Answer(Capability.SILENT_BY_CONSTRUCTION, Basis.DECLARED);
            }
        }
        return new Answer(of(node == null ? null : node.className(), source), Basis.INFERRED);
    }

    public static Capability of(String fqn, SourceResolver source) {
        if (fqn == null || fqn.isBlank() || source == null) return Capability.UNKNOWN;

        // a nested type lives in its outer class's file, and that is the file the resolver can find
        String outer = fqn.contains("$") ? fqn.substring(0, fqn.indexOf('$')) : fqn;
        Optional<String> text;
        try {
            text = source.sourceFor(outer);
        } catch (RuntimeException e) {
            return Capability.UNKNOWN;                   // a resolver that throws is not evidence
        }
        if (text == null || text.isEmpty()) return Capability.UNKNOWN;

        // Walk the nesting chain (Outer$Inner$Deeper), narrowing to each enclosing class's BODY in turn.
        // Searching the whole file for the first `class <Simple>` reads the wrong declaration when a file
        // holds two classes of that name at different depths (review N2) — and reading the wrong class can
        // return SILENT for a node that logs, which is the direction that flatters the score.
        String scope = text.get();
        String[] chain = fqn.substring(fqn.lastIndexOf('.') + 1).split("\\$");
        for (int i = 0; i < chain.length - 1; i++) {
            String enclosing = bodyOf(scope, chain[i]);
            if (enclosing.isEmpty()) return Capability.UNKNOWN;   // cannot place it — do not guess
            scope = enclosing;
        }
        String simple = chain[chain.length - 1];
        String header = declarationHeaderOf(scope, simple);
        if (header == null) return Capability.UNKNOWN;   // could not find it — say so, do not guess

        for (String type : typesNamedIn(header)) {
            if (AUDIT_CAPABLE.contains(type)) return Capability.CAN_LOG;
        }
        boolean hasSupertype = header.contains(" extends ") || header.contains(" implements ");
        if (hasSupertype) {
            // extends something we do not recognise: it may well reach an EventLogger further up, and
            // following the chain needs the supertype's own source. Unknown, therefore counted.
            return Capability.UNKNOWN;
        }
        // No superclass and no interface: there is nowhere an INHERITED logger could come from. But a
        // logger can still be handed in or held under any field name — review 2026-08-27 probed
        // `private EventLogger log; log.info(…)` and the first cut excluded it, a false exclusion, the
        // direction that flatters the score. So any mention of the logger TYPE or the contract in the
        // body, not just the conventional field name, is evidence enough to stay counted.
        String body = bodyOf(scope, simple);
        boolean mentionsLogging = body.contains("auditLog") || body.contains("EventLogger") || body.contains(EVENT_LOG_SOURCE);
        return mentionsLogging ? Capability.UNKNOWN : Capability.SILENT_BY_CONSTRUCTION;
    }

    /** The text from {@code class Name} up to its opening brace — the extends/implements clause. */
    private static String declarationHeaderOf(String src, String simpleName) {
        Matcher m = Pattern.compile("\\b(?:class|interface|enum|record)\\s+" + Pattern.quote(simpleName)
                + "\\b([^{]*)\\{").matcher(src);
        return m.find() ? " " + m.group(1).trim() + " " : null;
    }

    /** Identifier-shaped tokens in the header, minus the keywords and generic noise. */
    private static Set<String> typesNamedIn(String header) {
        Set<String> out = new java.util.LinkedHashSet<>();
        Matcher m = Pattern.compile("[A-Za-z_][\\w.]*").matcher(header);
        while (m.find()) {
            String t = m.group();
            if (t.equals("extends") || t.equals("implements")) continue;
            out.add(t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t);   // strip any package
        }
        return out;
    }

    /** Best-effort body text for the named class, for the belt-and-braces auditLog check. */
    private static String bodyOf(String src, String simpleName) {
        Matcher m = Pattern.compile("\\b(?:class|interface|enum|record)\\s+" + Pattern.quote(simpleName)
                + "\\b[^{]*\\{").matcher(src);
        if (!m.find()) return "";
        int depth = 0;
        for (int i = m.end() - 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(m.end(), i);
        }
        return src.substring(m.end());                   // unbalanced — hand back what we have
    }
}
