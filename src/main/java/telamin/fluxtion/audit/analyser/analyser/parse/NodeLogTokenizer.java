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
 *
 * <p><b>Quoted scalars</b> (format-spec §3). An instance id, key or value that is entirely a
 * double-quoted string — {@code "…"} with the escapes {@code \\ \" \n \r \t} — is decoded and marked
 * {@link KV#quoted() quoted}. This is the one lossless spelling for text that would otherwise BE
 * syntax: a logged String {@code "ok, price: 42.0"} written bare reads as two entries, the second a
 * numeric figure the producer never published. The binary reader emits the quoted form for exactly
 * those strings; the text runtime never has, and every unquoted value reads as it always did.
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
            instanceId = unquote(s).text;
            body = "";
        } else {
            instanceId = unquote(s.substring(0, colon).strip()).text;
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
            return new KV(unquote(seg).text, null);   // bare flag/token
        }
        String key = unquote(seg.substring(0, colon).strip()).text;
        Scalar value = unquote(seg.substring(colon + 2).strip());
        return new KV(key, value.text, value.quoted);
    }

    /** A decoded scalar: its text, and whether it arrived quoted (so it is a string, not a figure). */
    record Scalar(String text, boolean quoted) {
    }

    /**
     * Decodes {@code s} when it is entirely one double-quoted scalar; returns it untouched otherwise.
     * Untouched means untouched: a value that merely starts with a quote, or carries text after the
     * closing one, is the raw {@code toString()} it always was.
     */
    static Scalar unquote(String s) {
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') {
            return new Scalar(s, false);
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                if (i + 1 >= s.length() - 1) return new Scalar(s, false);   // dangling escape: not ours
                char e = s.charAt(++i);
                switch (e) {
                    case '\\': out.append('\\'); break;
                    case '"': out.append('"'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    default: out.append(c).append(e);   // unknown escape kept verbatim
                }
            } else if (c == '"') {
                return new Scalar(s, false);   // an unescaped quote inside: not one quoted scalar
            } else {
                out.append(c);
            }
        }
        return new Scalar(out.toString(), true);
    }

    /**
     * The quoted spelling of {@code s}: the inverse of {@link #unquote}. Emitters that construct
     * record text (the binary reader) use this for any string the tokenizer would otherwise split,
     * end early, or type as a figure, flag or null.
     */
    public static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /**
     * True when {@code s}, written bare as a VALUE, would not read back as the same string: it is
     * empty, it has whitespace the tokenizer strips or a control character the record framer would
     * take as a line, it contains a character the tokenizer splits or nests on, or it spells
     * something the tokenizer types — {@code null}, a boolean, or a number.
     */
    public static boolean needsQuoting(String s) {
        if (s.isEmpty() || !s.strip().equals(s)) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < ' ' || c == ',' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']'
                    || c == '(' || c == ')' || c == '"' || c == '\'' || c == '\\') {
                return true;
            }
        }
        KV bare = new KV(null, s);
        return bare.isNull() || bare.asBoolean() != null || bare.numeric().isPresent();
    }

    /**
     * True when {@code s} is not a plain identifier and so must be quoted as a KEY or instance id.
     * Names come from code — a field, a log key — so anything outside {@code [A-Za-z0-9_$.-]} is
     * quoted rather than trusted.
     */
    public static boolean needsQuotingAsName(String s) {
        if (s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '$' || c == '.' || c == '-';
            if (!plain) return true;
        }
        return false;
    }

    /** Index of the first top-level {@code ": "} (colon+space) separator, or -1. */
    static int indexOfSep(String s) {
        int depth = 0;
        boolean inS = false, inD = false;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (inS) { if (c == '\'') inS = false; continue; }
            if (inD) { if (c == '\\') i++; else if (c == '"') inD = false; continue; }
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
            if (inD) { if (c == '\\') i++; else if (c == '"') inD = false; continue; }
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
