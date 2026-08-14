package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Lenient tokenizer for the {@code nodeLogs} block. Node-log values are arbitrary Java
 * {@code toString()}s, so this is NOT YAML parsing: it splits only on <b>top-level</b> separators,
 * respecting nesting of {@code () [] {}} and quotes, and keeps values as raw strings.
 *
 * <p>Handles the awkward real cases: {@code MutableOrder(clOrdId=1, venue=null)} (inner commas
 * protected), {@code connectedVenues: [a, b]}, {@code hedgeQuantity: NaN},
 * {@code venueStatus: connected=true requiredOrderVenues=[x]} (spaces/=), and duplicate keys/ids.
 * Every fallback is silent and lossless.
 */
public final class NodeLogTokenizer {

    private NodeLogTokenizer() {
    }

    /**
     * Parses a whole {@code nodeLogs} block (the lines after {@code nodeLogs:} up to {@code endTime}).
     * Lines starting with {@code - } begin a new item; other non-blank lines are treated as
     * continuations of the current item (wrapped {@code toString()}s).
     */
    public static List<NodeLog> parseBlock(String block) {
        List<NodeLog> out = new ArrayList<>();
        if (block == null || block.isBlank()) return out;
        StringBuilder current = null;
        for (String rawLine : block.split("\n", -1)) {
            String line = stripCr(rawLine);
            String t = line.strip();
            if (t.isEmpty()) continue;
            if (t.startsWith("- ") || t.equals("-")) {
                if (current != null) out.add(parseItem(current.toString()));
                current = new StringBuilder(t.length() >= 2 ? t.substring(2) : "");
            } else if (current != null) {
                current.append(' ').append(t);   // continuation of a wrapped value
            } else {
                current = new StringBuilder(t);   // lenient: item without a leading dash
            }
        }
        if (current != null) out.add(parseItem(current.toString()));
        return out;
    }

    /**
     * Parses one node-log item body (already stripped of the leading {@code - }), e.g.
     * {@code bidMakerOrder: { orderStatus: NEW, price: 19.977}}.
     */
    public static NodeLog parseItem(String item) {
        String s = item.strip();
        int colon = indexOfSep(s);
        String instanceId;
        String body;
        if (colon < 0) {
            instanceId = s;
            body = "";
        } else {
            instanceId = s.substring(0, colon).strip();
            body = s.substring(colon + 2).strip();
        }
        List<KV> entries = new ArrayList<>();
        if (body.startsWith("{") && body.endsWith("}")) {
            String inner = body.substring(1, body.length() - 1).strip();
            if (!inner.isEmpty()) {
                for (String seg : splitTopLevel(inner, ',')) {
                    entries.add(parsePair(seg));
                }
            }
        } else if (!body.isEmpty()) {
            // lenient: unstructured value with no braces -> single keyless entry
            entries.add(new KV(null, body));
        }
        return new NodeLog(instanceId, entries);
    }

    private static KV parsePair(String segment) {
        String seg = segment.strip();
        int colon = indexOfSep(seg);
        if (colon < 0) {
            return new KV(seg, null);   // bare flag/token
        }
        String key = seg.substring(0, colon).strip();
        String value = seg.substring(colon + 2).strip();
        return new KV(key, value);
    }

    /** Index of the first top-level {@code ": "} (colon+space) separator, or -1. */
    static int indexOfSep(String s) {
        int depth = 0;
        boolean inS = false, inD = false;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (inS) { if (c == '\'') inS = false; continue; }
            if (inD) { if (c == '"') inD = false; continue; }
            switch (c) {
                case '\'': inS = true; break;
                case '"': inD = true; break;
                case '(': case '[': case '{': depth++; break;
                case ')': case ']': case '}': if (depth > 0) depth--; break;
                default:
                    if (depth == 0 && c == ':' && s.charAt(i + 1) == ' ') return i;
            }
        }
        return -1;
    }

    /**
     * Splits {@code s} on {@code delim} only where nesting depth of {@code () [] {}} is zero and not
     * inside single/double quotes.
     */
    static List<String> splitTopLevel(String s, char delim) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        boolean inS = false, inD = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inS) { if (c == '\'') inS = false; continue; }
            if (inD) { if (c == '"') inD = false; continue; }
            switch (c) {
                case '\'': inS = true; break;
                case '"': inD = true; break;
                case '(': case '[': case '{': depth++; break;
                case ')': case ']': case '}': if (depth > 0) depth--; break;
                default:
                    if (c == delim && depth == 0) {
                        parts.add(s.substring(start, i));
                        start = i + 1;
                    }
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private static String stripCr(String s) {
        return (!s.isEmpty() && s.charAt(s.length() - 1) == '\r') ? s.substring(0, s.length() - 1) : s;
    }
}
