package telamin.fluxtion.audit.analyser.analyser.source;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helpers that drive click-to-source navigation (spec §9). Kept free of Swing so they can be
 * unit-tested:
 * <ul>
 *   <li>{@link #methodDeclOffset} — where a method is declared in a source file (to scroll to it);</li>
 *   <li>{@link #resolveAt} — the identifier (and optional {@code receiver.method}) under a caret, for
 *       Ctrl-click navigation in the source viewer;</li>
 *   <li>{@link #parseNodeLogLine} — the {@code instanceId} and driving method-key of a clicked
 *       {@code nodeLogs} line in the detail viewer.</li>
 * </ul>
 */
public final class SourceNavigation {

    /** A token under the caret: the identifier, an optional {@code receiver} before a {@code .}, and whether it is a call. */
    public record Ref(String identifier, String receiver, boolean methodCall) {
    }

    /** A parsed node-log line: the node {@code instanceId} and the first key (the method that ran), if any. */
    public record NodeRef(String instanceId, String methodKey) {
    }

    private SourceNavigation() {
    }

    /** Offset of the declaration of {@code method} (preferring a real decl over a call), or -1. */
    public static int methodDeclOffset(String source, String method) {
        if (source == null || method == null || method.isBlank()) return -1;
        Pattern decl = Pattern.compile("(?m)^[ \\t]*(?:(?:public|private|protected|static|final|synchronized|abstract|native|default)\\s+)*"
                + "[\\w.$<>\\[\\]]+\\s+" + Pattern.quote(method) + "\\s*\\(");
        Matcher m = decl.matcher(source);
        if (m.find()) return m.start();
        Matcher call = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(source);
        return call.find() ? call.start() : -1;
    }

    /**
     * Offset of the declaration of a <b>type</b> by simple name, or -1.
     *
     * <p>Needed because a Fluxtion graph's node classes are commonly nested in one holder — the
     * framework's own examples do it — so opening {@code Nodes.java} and landing at line 1 leaves the
     * reader to find {@code QuotePublisher} among a dozen siblings. When the file holds a single
     * top-level type this finds it at the top anyway, so the caller needs no special case.
     */
    public static int typeDeclOffset(String source, String simpleName) {
        if (source == null || simpleName == null || simpleName.isBlank()) return -1;
        Matcher m = Pattern.compile("(?m)^[ \\t]*(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed)\\s+)*"
                + "(?:class|interface|enum|record)\\s+" + Pattern.quote(simpleName) + "\\b").matcher(source);
        return m.find() ? m.start() : -1;
    }

    /**
     * Offset of the generated processor's {@code handleEvent} overload for {@code eventSimpleName}, or -1.
     *
     * <p>A generated EventProcessor has one {@code handleEvent} per event type, so
     * {@link #methodDeclOffset} would land on whichever came first — almost never the one asked for.
     * Matching on the parameter type is what makes clicking an event node land on the dispatch that
     * actually handles it.
     */
    public static int eventHandlerOffset(String source, String eventSimpleName) {
        if (source == null || eventSimpleName == null || eventSimpleName.isBlank()) return -1;
        Matcher m = Pattern.compile("(?m)^[ \\t]*(?:(?:public|private|protected|static|final)\\s+)*"
                + "\\w+\\s+handleEvent\\s*\\(\\s*(?:[\\w.$]+\\.)?" + Pattern.quote(eventSimpleName)
                + "\\s+\\w+\\s*\\)").matcher(source);
        return m.find() ? m.start() : -1;
    }

    /** The identifier under {@code offset}, plus a preceding {@code receiver.} and whether a {@code (} follows. */
    public static Ref resolveAt(String s, int offset) {
        if (s == null || offset < 0 || offset > s.length()) return null;
        int a = offset, b = offset;
        while (a > 0 && isIdent(s.charAt(a - 1))) a--;
        while (b < s.length() && isIdent(s.charAt(b))) b++;
        if (a >= b) return null;
        String ident = s.substring(a, b);

        String receiver = null;
        int p = a - 1;
        while (p >= 0 && s.charAt(p) == ' ') p--;
        if (p >= 0 && s.charAt(p) == '.') {
            int q = p - 1;
            while (q >= 0 && s.charAt(q) == ' ') q--;
            int end = q + 1;
            while (q >= 0 && isIdent(s.charAt(q))) q--;
            String r = s.substring(q + 1, end);
            if (!r.isBlank()) receiver = r;
        }

        int n = b;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        boolean call = n < s.length() && s.charAt(n) == '(';
        return new Ref(ident, receiver, call);
    }

    /** Parses a {@code - instanceId: { key: value, ... }} node-log line; null if the line isn't one. */
    public static NodeRef parseNodeLogLine(String line) {
        if (line == null) return null;
        String t = line.strip();
        if (!t.startsWith("- ")) return null;
        t = t.substring(2).strip();
        int colon = t.indexOf(':');
        if (colon <= 0) return null;
        String inst = t.substring(0, colon).trim();
        if (!isIdentifier(inst)) return null;
        String rest = t.substring(colon + 1).strip();
        String method = null;
        if (rest.startsWith("{")) {
            Matcher m = Pattern.compile("\\{\\s*([A-Za-z_$][\\w$]*)\\s*:").matcher(rest);
            if (m.find()) method = m.group(1);
        }
        return new NodeRef(inst, method);
    }

    /** The full text of the line containing {@code offset}. */
    public static String lineAt(String s, int offset) {
        if (s == null || offset < 0 || offset > s.length()) return "";
        int start = offset;
        while (start > 0 && s.charAt(start - 1) != '\n') start--;
        int end = offset;
        while (end < s.length() && s.charAt(end) != '\n') end++;
        return s.substring(start, end);
    }

    private static boolean isIdent(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty() || !(Character.isLetter(s.charAt(0)) || s.charAt(0) == '_' || s.charAt(0) == '$')) return false;
        for (int i = 1; i < s.length(); i++) if (!isIdent(s.charAt(i))) return false;
        return true;
    }
}
