package telamin.fluxtion.audit.analyser.analyser.export;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.NodeLogTokenizer;

/** Human-facing evidence spelling, not a reloadable log format. All renderers share key identity. */
public final class EvidenceText {
    private EvidenceText() { }

    public static final String UNKEYED_MARKER = "@unkeyed";

    /** Only absence gets the bare marker; a business name containing it is quoted and escaped. */
    public static String name(String key) {
        if (key == null) return UNKEYED_MARKER;
        return NodeLogTokenizer.needsQuotingAsName(key) ? NodeLogTokenizer.quote(key) : key;
    }

    /**
     * A value is quoted only when the PARSED entry says the reader quoted it. Under the declared grammar the
     * reader already quotes every value that needs it; under the legacy grammar nothing is quoted and nothing
     * may be, or a value such as {@code C:\temp} would read {@code "C:\\temp"} in the logical view while the
     * raw view beside it says otherwise (review F1, 2026-09-15). Names keep their own rule above.
     */
    public static String value(KV kv) {
        String raw = kv.rawValue();
        if (raw == null) return "null";
        return kv.quoted() ? NodeLogTokenizer.quote(raw) : raw;
    }

    /** Shared by step status and both PDF evidence assembly paths. */
    public static String nodeLine(NodeLog node, String nodeSeparator, String entrySeparator) {
        StringBuilder out = new StringBuilder(name(node.instanceId()));
        for (int i = 0; i < node.entries().size(); i++) {
            KV kv = node.entries().get(i);
            out.append(i == 0 ? nodeSeparator : entrySeparator)
                    .append(name(kv.key())).append('=').append(value(kv));
        }
        return out.toString();
    }
}
