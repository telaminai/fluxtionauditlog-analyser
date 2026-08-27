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
 * AND the class declares no supertype at all — with no superclass and no interface there is nowhere an
 * {@code auditLog} field could come from. Everything else, including every case where the source is
 * missing or the supertype cannot be recognised, is {@link Capability#UNKNOWN} and stays counted.
 * Assuming silence is the one error that cannot be spotted from the output.
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
    public static Capability of(String fqn, Function<String, Optional<String>> source) {
        if (fqn == null || fqn.isBlank() || source == null) return Capability.UNKNOWN;

        // a nested type lives in its outer class's file, and that is the file the resolver can find
        String outer = fqn.contains("$") ? fqn.substring(0, fqn.indexOf('$')) : fqn;
        Optional<String> text;
        try {
            text = source.apply(outer);
        } catch (RuntimeException e) {
            return Capability.UNKNOWN;                   // a resolver that throws is not evidence
        }
        if (text == null || text.isEmpty()) return Capability.UNKNOWN;

        String simple = fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1);
        String header = declarationHeaderOf(text.get(), simple);
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
        // No superclass and no interface: there is nowhere an inherited auditLog could come from. If
        // the body mentions one anyway, our reading of the file is wrong — prefer unknown to a wrong
        // exclusion.
        return bodyOf(text.get(), simple).contains("auditLog")
                ? Capability.UNKNOWN : Capability.SILENT_BY_CONSTRUCTION;
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
