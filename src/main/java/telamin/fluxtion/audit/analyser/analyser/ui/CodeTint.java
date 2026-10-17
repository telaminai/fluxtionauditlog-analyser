package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * M74 P2 (spec §5.9): light syntax tinting for a code block in Claude's words — Java, XML, YAML and JSON: keywords,
 * strings, comments and numbers. One pass, no backtracking; the view shows a block over 64 KB or 2,000 lines, or in
 * another language, untinted. Tinting changes colour only: the characters are the block's, exactly.
 */
final class CodeTint {

    private CodeTint() {
    }

    enum Kind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER }

    record Token(Kind kind, String text) {
    }

    private static final Set<String> JAVA = Set.of("abstract", "boolean", "break", "byte", "case", "catch", "char",
            "class", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
            "if", "implements", "import", "instanceof", "int", "interface", "long", "new", "null", "package", "private",
            "protected", "public", "record", "return", "short", "static", "super", "switch", "this", "throw", "throws",
            "true", "false", "try", "var", "void", "volatile", "while", "yield");
    private static final Set<String> JSON = Set.of("true", "false", "null");

    static boolean supports(String language) {
        return switch (language == null ? "" : language.toLowerCase(Locale.ROOT)) {
            case "java", "xml", "yaml", "yml", "json" -> true;
            default -> false;
        };
    }

    static List<Token> lex(String code, String language) {
        String lang = language.toLowerCase(Locale.ROOT);
        List<Token> out = new ArrayList<>();
        int n = code.length();
        int i = 0;
        StringBuilder plain = new StringBuilder();
        while (i < n) {
            char c = code.charAt(i);
            int end = -1;
            Kind kind = null;
            if (lang.equals("java") && c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                end = lineEnd(code, i);
                kind = Kind.COMMENT;
            } else if (lang.equals("java") && c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                int close = code.indexOf("*/", i + 2);
                end = close < 0 ? n : close + 2;
                kind = Kind.COMMENT;
            } else if ((lang.equals("yaml") || lang.equals("yml")) && c == '#') {
                end = lineEnd(code, i);
                kind = Kind.COMMENT;
            } else if (lang.equals("xml") && code.startsWith("<!--", i)) {
                int close = code.indexOf("-->", i + 4);
                end = close < 0 ? n : close + 3;
                kind = Kind.COMMENT;
            } else if (c == '"' || (c == '\'' && !lang.equals("java") && !lang.equals("json"))) {
                end = stringEnd(code, i, c);
                kind = Kind.STRING;
            } else if (lang.equals("java") && c == '\'') {
                end = stringEnd(code, i, c);
                kind = Kind.STRING;
            } else if (Character.isDigit(c) && (i == 0 || !Character.isLetterOrDigit(code.charAt(i - 1)))) {
                end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '.'
                        || code.charAt(end) == '_')) {
                    end++;
                }
                kind = Kind.NUMBER;
            } else if (Character.isJavaIdentifierStart(c) && (i == 0 || !Character.isJavaIdentifierPart(code.charAt(i - 1)))) {
                int e = i;
                while (e < n && Character.isJavaIdentifierPart(code.charAt(e))) e++;
                String word = code.substring(i, e);
                boolean keyword = switch (lang) {
                    case "java" -> JAVA.contains(word);
                    case "json", "yaml", "yml" -> JSON.contains(word);
                    default -> false;
                };
                if (keyword) {
                    end = e;
                    kind = Kind.KEYWORD;
                } else {
                    plain.append(word);
                    i = e;
                    continue;
                }
            } else if (lang.equals("xml") && c == '<') {
                int e = i + 1;
                if (e < n && (code.charAt(e) == '/' || code.charAt(e) == '?')) e++;
                while (e < n && (Character.isLetterOrDigit(code.charAt(e)) || ":-_.".indexOf(code.charAt(e)) >= 0)) e++;
                end = e;
                kind = Kind.KEYWORD;
            }
            if (kind != null && end > i) {
                if (!plain.isEmpty()) {
                    out.add(new Token(Kind.PLAIN, plain.toString()));
                    plain.setLength(0);
                }
                out.add(new Token(kind, code.substring(i, end)));
                i = end;
            } else {
                plain.append(c);
                i++;
            }
        }
        if (!plain.isEmpty()) out.add(new Token(Kind.PLAIN, plain.toString()));
        return out;
    }

    private static int lineEnd(String s, int from) {
        int nl = s.indexOf('\n', from);
        return nl < 0 ? s.length() : nl;
    }

    private static int stringEnd(String s, int from, char quote) {
        int i = from + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') return Math.min(s.length(), i + 1);
            i++;
        }
        return s.length();
    }
}
