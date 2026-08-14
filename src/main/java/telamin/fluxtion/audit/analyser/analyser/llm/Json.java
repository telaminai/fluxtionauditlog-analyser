package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader/writer — just enough for the LLM request bodies and to pluck
 * text out of the responses (spec §10). Values map to {@code Map<String,Object>}, {@code List<Object>},
 * {@code String}, {@code Double}, {@code Boolean}, {@code null}.
 */
public final class Json {

    private Json() {
    }

    // ---------------- writing ----------------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case String s -> writeString(sb, s);
            case Boolean b -> sb.append(b.toString());
            case Number n -> sb.append(n.toString());
            case Map<?, ?> m -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(sb, String.valueOf(e.getKey()));
                    sb.append(':');
                    writeValue(sb, e.getValue());
                }
                sb.append('}');
            }
            case List<?> list -> {
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(',');
                    writeValue(sb, list.get(i));
                }
                sb.append(']');
            }
            default -> writeString(sb, v.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------------- reading ----------------

    public static Object parse(String s) {
        return new Parser(s).parseTop();
    }

    /** Convenience: follow a path of map keys / list indices; returns null if any hop is missing. */
    @SuppressWarnings("unchecked")
    public static Object at(Object root, Object... path) {
        Object cur = root;
        for (Object key : path) {
            if (cur instanceof Map<?, ?> m && key instanceof String k) {
                cur = m.get(k);
            } else if (cur instanceof List<?> list && key instanceof Integer i) {
                cur = (i >= 0 && i < list.size()) ? list.get(i) : null;
            } else {
                return null;
            }
            if (cur == null) return null;
        }
        return cur;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        Object parseTop() {
            Object v = parseValue();
            skipWs();
            return v;
        }

        private Object parseValue() {
            skipWs();
            if (i >= s.length()) throw err("unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBool();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                m.put(key, parseValue());
                skipWs();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw err("expected , or }");
            }
            return m;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw err("expected , or ]");
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseBool() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw err("invalid literal");
        }

        private Object parseNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw err("invalid literal");
        }

        private Object parseNumber() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() { return i < s.length() ? s.charAt(i) : '\0'; }
        private char next() { if (i >= s.length()) throw err("unexpected end"); return s.charAt(i++); }
        private void expect(char c) { if (next() != c) throw err("expected " + c); }
        private RuntimeException err(String m) { return new IllegalArgumentException("JSON @" + i + ": " + m); }
    }
}
