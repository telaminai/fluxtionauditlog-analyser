package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M74 (spec §4.1; the scorecard's R6): what a FACT may say about a place or in the analyser's own words. The session
 * processor records every fact in its audit record, so a fact that carries a model's words or an absolute path carries
 * them into a record that is exported and shared. Facts therefore name a folder by a store REFERENCE (the frame resolves
 * it) and show it as {@code …/name}; and an analyser-written reason passes {@link #withoutPaths} before it becomes a
 * field. Model-authored words (a proposal's summary, an item's reason, path or class name) are never a field at all: they
 * live in the bounded transcript store, named by reference.
 */
public final class FactWords {

    private FactWords() {
    }

    /** {@code …/name}: the last segment of {@code path}, as the tab shows a folder; null for none. */
    public static String shown(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.replace('\\', '/');
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int at = p.lastIndexOf('/');
        String name = at < 0 ? p : p.substring(at + 1);
        return name.isEmpty() ? "…/" : "…/" + name;
    }

    /** An absolute POSIX or home path ({@code /a/b}, {@code ~/a}), or a Windows drive path ({@code C:\a}). */
    private static final Pattern PATH = Pattern.compile(
            "(?<![\\w.])(?:~/|/(?=[^\\s/]+/)|[A-Za-z]:[\\\\/])[^\\s'\"`,;:()<>\\[\\]{}]*");

    /**
     * {@code words} with every absolute path replaced by its {@code …/name} form — for an analyser-written reason (an
     * exception's message names the file it failed on) that becomes a fact field. A word that is not a path is kept.
     */
    public static String withoutPaths(String words) {
        if (words == null) return null;
        Matcher m = PATH.matcher(words);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(shown(m.group())));
        m.appendTail(out);
        return out.toString();
    }

    /** Whether {@code words} still hold something that reads as an absolute path. */
    public static boolean holdsPath(String words) {
        return words != null && PATH.matcher(words).find();
    }
}
