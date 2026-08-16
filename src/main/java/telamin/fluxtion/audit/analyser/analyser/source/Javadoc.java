package telamin.fluxtion.audit.analyser.analyser.source;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the doc comment off a type declaration, for showing what a node <em>is</em> without leaving the
 * view you are in.
 *
 * <p>Deliberately a small text scan rather than a parse. The analyser reads whatever source it is pointed
 * at — including generated processors and classes whose dependencies are absent — so anything that needs
 * a resolvable compilation unit would fail on exactly the files most worth reading. A scan that
 * occasionally returns nothing is a better trade than one that occasionally throws.
 */
public final class Javadoc {

    private Javadoc() { }

    /** Matches the declaration of a top-level or nested type by simple name. */
    private static Pattern declaration(String simpleName) {
        return Pattern.compile("\\b(?:class|interface|enum|record)\\s+" + Pattern.quote(simpleName)
                               + "\\b");
    }

    /**
     * The doc comment attached to {@code simpleName}'s declaration, as plain text with the comment
     * markers and leading asterisks removed — or {@code null} when there is none.
     *
     * <p>Annotations and modifiers may sit between the comment and the declaration, so the search skips
     * back over them; a comment that is <em>not</em> adjacent (an unrelated one earlier in the file with
     * code in between) is correctly not picked up.
     */
    public static String forType(String source, String simpleName) {
        if (source == null || simpleName == null || simpleName.isBlank()) return null;

        Matcher m = declaration(simpleName).matcher(source);
        while (m.find()) {
            String doc = docEndingBefore(source, m.start());
            if (doc != null) return doc;
        }
        return null;
    }

    /**
     * The doc comment immediately preceding {@code declStart}, skipping back over modifiers and
     * annotations. Returns null when what precedes the declaration is anything else — code, a line
     * comment, or nothing.
     */
    private static String docEndingBefore(String source, int declStart) {
        int i = declStart - 1;
        // walk back over "public final @Foo(...)" style prefixes: identifiers, @, whitespace, and the
        // parentheses/strings an annotation argument can contain
        while (i >= 0) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c) || Character.isJavaIdentifierPart(c) || c == '@' || c == '.') {
                i--;
            } else if (c == ')') {
                int depth = 0;
                while (i >= 0) {
                    char d = source.charAt(i);
                    if (d == ')') depth++;
                    else if (d == '(' && --depth == 0) { i--; break; }
                    i--;
                }
            } else {
                break;
            }
        }
        if (i < 1 || source.charAt(i) != '/' || source.charAt(i - 1) != '*') return null;

        int end = i + 1;                                  // just past the closing "*/"
        int start = source.lastIndexOf("/**", end - 3);
        if (start < 0) return null;
        // a "/*" (not "/**") comment is not javadoc, and lastIndexOf("/**") could have skipped over one
        int plain = source.lastIndexOf("/*", end - 3);
        if (plain > start) return null;

        return clean(source.substring(start + 3, end - 2));
    }

    /** Strip the leading asterisks and collapse the comment into readable prose. */
    private static String clean(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("*")) trimmed = trimmed.substring(1).strip();
            if (trimmed.startsWith("@")) break;           // stop at the block tags; they are not prose
            sb.append(trimmed).append('\n');
        }
        String text = sb.toString()
                .replaceAll("\\{@link\\s+([^}]+)}", "$1")
                .replaceAll("\\{@code\\s+([^}]+)}", "$1")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{2,}", "\n")
                .strip();
        return text.isEmpty() ? null : text;
    }

    /**
     * The first sentence, for a tooltip. Javadoc's own summary convention: up to the first full stop
     * followed by whitespace.
     */
    public static String summary(String doc) {
        if (doc == null) return null;
        Matcher m = Pattern.compile("\\.(\\s|$)").matcher(doc);
        String first = m.find() ? doc.substring(0, m.start() + 1) : doc;
        return first.replace('\n', ' ').strip();
    }
}
