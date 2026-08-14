package telamin.fluxtion.audit.analyser.analyser.graph;

/**
 * A graphable series key: a node {@code instanceId} plus a nodeLogs {@code key} (spec §8.7).
 * Kept as a pair (not a dotted string) so keys that themselves contain dots — e.g.
 * {@code liverOrder.leavesQuantity} — are unambiguous.
 */
public record GraphKey(String instanceId, String key) {
    public String display() {
        return instanceId + "." + key;
    }

    /**
     * Parse a {@code "instanceId.key"} display string. The instanceId is a single node field name (never
     * dotted), so we split on the <b>first</b> dot — everything after it is the key (which may itself
     * contain dots). Returns null if there is no interior dot.
     */
    public static GraphKey fromDisplay(String display) {
        if (display == null) return null;
        int dot = display.indexOf('.');
        if (dot <= 0 || dot >= display.length() - 1) return null;
        return new GraphKey(display.substring(0, dot), display.substring(dot + 1));
    }
}
