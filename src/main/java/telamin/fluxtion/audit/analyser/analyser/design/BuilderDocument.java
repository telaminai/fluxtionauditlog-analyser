package telamin.fluxtion.audit.analyser.analyser.design;

import java.util.*;

/** Bounded lexical wiring index, not a Java parser or proof that a constructor's object belongs to the runtime graph. */
public record BuilderDocument(String file, String text, String revision, List<NodeAnchor> nodes) {
    public record NodeAnchor(String name, int line, boolean anchored, String kind) { }
    public BuilderDocument { nodes = List.copyOf(nodes); }
    public static BuilderDocument parse(String file, String text) {
        if (text.length() > DesignFiles.MAX_BYTES || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > DesignFiles.MAX_BYTES)
            throw new IllegalArgumentException("builder exceeds the 2 MiB source-view limit");
        List<Token> tokens = lex(text);
        int[] closing = closingDelimiters(tokens);
        int begin = -1, end = -1;
        for (int i = 0; i + 2 < tokens.size(); i++) {
            if (!tokens.get(i).value.equals("void") || !tokens.get(i + 1).value.equals("buildGraph") || !tokens.get(i + 2).value.equals("(")) continue;
            int close = closing[i + 2];
            if (close < 0) break;
            int body = close + 1;
            while (body < tokens.size() && !Set.of("{", ";", "=").contains(tokens.get(body).value)) body++;
            if (body < tokens.size() && tokens.get(body).value.equals("{")) {
                begin = body + 1; end = closing[body]; break;
            }
        }
        List<Located> anchors = new ArrayList<>(); Set<Integer> direct = new HashSet<>();
        if (begin >= 0 && end >= begin) {
            for (int i = begin; i < end - 1; i++) {
                Token call = tokens.get(i);
                if (!Set.of("addNode", "addPublicNode").contains(call.value) || !tokens.get(i + 1).value.equals("(")) continue;
                int close = closing[i + 1];
                if (close < 0 || close >= end) continue;
                List<Span> args = arguments(tokens, closing, i + 2, close);
                // Only the two-argument named overload has a name. A trailing string in Object... is NOT one.
                String name = args.size() == 2 && args.getLast().to - args.getLast().from == 1
                        ? tokens.get(args.getLast().from).literal : null;
                anchors.add(new Located(call.start, new NodeAnchor(name, call.line, name != null, call.value)));
                if (!args.isEmpty() && args.getFirst().from < args.getFirst().to) {
                    int first = args.getFirst().from, last = args.getFirst().to;
                    while (last - first > 2 && tokens.get(first).value.equals("(") && closing[first] == last - 1) {
                        first++; last--;
                    }
                    if (tokens.get(first).value.equals("new")) direct.add(tokens.get(first).start);
                }
            }
            // A malformed draft can contain a long identifier-like run after every `new`. Index that
            // suffix once too, rather than repeatedly scanning the same tokens for a constructor.
            int[] typeEnd = new int[tokens.size() + 1]; typeEnd[tokens.size()] = tokens.size();
            for (int i = tokens.size() - 1; i >= 0; i--) {
                String value = tokens.get(i).value;
                typeEnd[i] = Character.isJavaIdentifierStart(value.charAt(0)) || Set.of(".", "<", ">", ",", "?").contains(value)
                        ? typeEnd[i + 1] : i;
            }
            for (int i = begin; i < end - 2; i++) {
                Token t = tokens.get(i);
                if (!t.value.equals("new") || direct.contains(t.start)) continue;
                int next = typeEnd[i + 1];
                if (next < end && tokens.get(next).value.equals("("))
                    anchors.add(new Located(t.start, new NodeAnchor(null, t.line, false, "discovered")));
            }
        }
        anchors.sort(Comparator.comparingInt(Located::start));
        return new BuilderDocument(file, text, DesignFiles.sha256(text), anchors.stream().map(Located::anchor).toList());
    }
    public List<NodeAnchor> named(String name) { return nodes.stream().filter(n -> n.anchored() && Objects.equals(name, n.name())).toList(); }
    public int lines() { return 1 + (int) text.chars().filter(c -> c == '\n').count(); }
    private record Located(int start, NodeAnchor anchor) { }
    private record Token(String value, int start, int line, String literal) { }
    private record Span(int from, int to) { }
    private static int[] closingDelimiters(List<Token> tokens) {
        int[] closing = new int[tokens.size()]; Arrays.fill(closing, -1);
        Deque<Integer> parens = new ArrayDeque<>(), braces = new ArrayDeque<>(), brackets = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            String value = tokens.get(i).value;
            Deque<Integer> stack = switch (value) {
                case "(", ")" -> parens; case "{", "}" -> braces; case "[", "]" -> brackets; default -> null;
            };
            if (stack == null) continue;
            if (Set.of("(", "{", "[").contains(value)) stack.push(i);
            else if (!stack.isEmpty()) closing[stack.pop()] = i;
        }
        return closing;
    }
    private static List<Span> arguments(List<Token> tokens, int[] closing, int start, int end) {
        List<Span> out = new ArrayList<>(); int from = start, angles = 0;
        for (int i = start; i < end; i++) {
            String v = tokens.get(i).value;
            if (Set.of("(", "[", "{").contains(v) && closing[i] >= i && closing[i] < end) {
                i = closing[i];
                continue;
            }
            switch (v) {
                case "<" -> angles++; case ">" -> angles = Math.max(0, angles - 1);
                case "," -> { if (angles == 0) { out.add(new Span(from, i)); from = i + 1; } }
            }
        }
        if (from < end) out.add(new Span(from, end));
        return out;
    }
    private static List<Token> lex(String text) {
        List<Token> out = new ArrayList<>(); int line = 1;
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i); int start = i, at = line;
            if (Character.isWhitespace(c)) { if (c == '\n') line++; i++; continue; }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') i++; continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i += 2;
                while (i < text.length() && !(text.charAt(i) == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/')) { if (text.charAt(i++) == '\n') line++; }
                i = Math.min(text.length(), i + 2); continue;
            }
            if (c == '"' && text.startsWith("\"\"\"", i)) {
                i += 3;
                while (i < text.length()) {
                    if (text.charAt(i) == '\\') {
                        if (i + 1 < text.length() && text.charAt(i + 1) == '\n') line++;
                        i = Math.min(text.length(), i + 2); continue;
                    }
                    if (text.startsWith("\"\"\"", i)) { i += 3; break; }
                    if (text.charAt(i++) == '\n') line++;
                }
                out.add(new Token("@text-block", start, at, null)); continue;
            }
            if (c == '"' || c == '\'') {
                StringBuilder value = new StringBuilder(); boolean closed = false, valid = true; i++;
                while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                    char ch = text.charAt(i++);
                    if (ch == c) { closed = true; break; }
                    if (ch != '\\') { value.append(ch); continue; }
                    if (i == text.length()) { valid = false; break; }
                    char escaped = text.charAt(i++);
                    switch (escaped) {
                        case 'n' -> value.append('\n'); case 'r' -> value.append('\r'); case 't' -> value.append('\t');
                        case 'b' -> value.append('\b'); case 'f' -> value.append('\f'); case 's' -> value.append(' ');
                        case '\\', '\'', '"' -> value.append(escaped);
                        case 'u' -> {
                            while (i < text.length() && text.charAt(i) == 'u') i++;
                            if (i + 4 > text.length()) { valid = false; break; }
                            try { value.append((char) Integer.parseInt(text.substring(i, i + 4), 16)); i += 4; }
                            catch (NumberFormatException ex) { valid = false; }
                        }
                        default -> {
                            if (escaped >= '0' && escaped <= '7') {
                                int octal = escaped - '0', limit = escaped <= '3' ? 2 : 1;
                                while (limit-- > 0 && i < text.length() && text.charAt(i) >= '0' && text.charAt(i) <= '7') octal = octal * 8 + text.charAt(i++) - '0';
                                value.append((char) octal);
                            } else valid = false;
                        }
                    }
                }
                out.add(new Token(c == '"' ? "@string" : "@char", start, at, c == '"' && closed && valid ? value.toString() : null)); continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                i++;
                while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) i++;
                out.add(new Token(text.substring(start, i), start, at, null));
            } else { out.add(new Token(String.valueOf(c), start, at, null)); i++; }
        }
        return out;
    }
}
