package com.acme.demo.replay;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what {@link ReplayCapture} writes, against the event types THIS build handles. A type outside them is
 * refused and never loaded: a replay file is untrusted, and the reader's own build is the allow-list.
 */
public final class ReplayReader {
    public record Entry(Object event, long time) { }

    private static final Pattern EVENT = Pattern.compile("^event: !!([\\w.$]+) \\{(.*)}$");
    private static final Pattern TIME = Pattern.compile("^wallClockTime: (-?\\d+)$");

    private ReplayReader() { }

    public static List<Entry> read(String yaml, Set<Class<?>> handled) throws ReflectiveOperationException {
        Map<String, Class<?>> byName = new HashMap<>();
        handled.forEach(c -> byName.put(c.getName(), c));
        List<Entry> out = new ArrayList<>();
        for (String[] r : records(yaml)) {
            Class<?> type = byName.get(r[0]);
            if (type == null) {
                throw new IllegalArgumentException("the replay names a type this build does not handle: " + r[0]);
            }
            out.add(new Entry(build(type, r[1]), Long.parseLong(r[2])));
        }
        return out;
    }

    /**
     * The replay-record grammar, every line of it, as the recipient's runner reads it: blank lines, then per record
     * exactly {@code ---}, the header, one event line and one time line. Anything else is refused, naming the line.
     * One byte-order-mark rule and either line ending. Each result is {class name, component body, time}.
     */
    static List<String[]> records(String yaml) {
        String text = (yaml.startsWith("\uFEFF") ? yaml.substring(1) : yaml).replace("\r\n", "\n");
        String[] lines = text.split("\n", -1);
        List<String[]> out = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            if (lines[i].isBlank()) {
                i++;
                continue;
            }
            if (!lines[i].equals("---")) throw new IllegalArgumentException("line " + (i + 1) + " is not part of a replay record");
            if (i + 3 >= lines.length) throw new IllegalArgumentException("the replay record at line " + (i + 1) + " is cut off");
            Matcher e = EVENT.matcher(lines[i + 2]);
            Matcher t = TIME.matcher(lines[i + 3]);
            if (!lines[i + 1].equals(HEADER) || !e.matches() || !t.matches()) {
                throw new IllegalArgumentException("the replay record at line " + (i + 1) + " is malformed");
            }
            out.add(new String[]{e.group(1), e.group(2), t.group(1)});
            i += 4;
        }
        return out;
    }

    static final String HEADER = "!!com.telamin.fluxtion.runtime.event.ReplayRecord";

    static Object build(Class<?> type, String body) throws ReflectiveOperationException {
        RecordComponent[] parts = type.getRecordComponents();
        List<String> values = split(body);
        if (values.size() != parts.length) throw new IllegalArgumentException(type.getName() + ": " + body);
        Class<?>[] types = new Class<?>[parts.length];
        Object[] args = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String kv = values.get(i);
            int colon = kv.indexOf(':');
            String name = kv.substring(0, colon).strip();
            if (!name.equals(parts[i].getName())) {
                throw new IllegalArgumentException(type.getName() + ": expected " + parts[i].getName() + ", got " + name);
            }
            types[i] = parts[i].getType();
            args[i] = value(types[i], kv.substring(colon + 1).strip());
        }
        Constructor<?> c = type.getDeclaredConstructor(types);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static Object value(Class<?> t, String raw) {
        if (raw.equals("null")) {
            if (t.isPrimitive()) throw new IllegalArgumentException("null for a primitive " + t.getName());
            return null;
        }
        if (t == String.class || t == char.class || t == Character.class) {
            String s = unquote(raw);
            if (t == String.class) return s;
            if (s.length() != 1) throw new IllegalArgumentException("not one character: " + raw);
            return s.charAt(0);
        }
        if (t == double.class || t == Double.class) return Double.parseDouble(raw);
        if (t == float.class || t == Float.class) return Float.parseFloat(raw);
        if (t == int.class || t == Integer.class) return Integer.parseInt(raw);
        if (t == long.class || t == Long.class) return Long.parseLong(raw);
        if (t == short.class || t == Short.class) return Short.parseShort(raw);
        if (t == byte.class || t == Byte.class) return Byte.parseByte(raw);
        if (t == boolean.class || t == Boolean.class) {
            if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException("not a boolean: " + raw);
            return Boolean.parseBoolean(raw);
        }
        throw new IllegalArgumentException("unsupported type " + t);
    }

    /** One quoted token, unescaped left to right: the inverse of {@code ReplayCapture.quote}. Unquoted is refused. */
    static String unquote(String raw) {
        if (raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"') {
            throw new IllegalArgumentException("a string must be quoted: " + raw);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < raw.length() - 1; i++) {
            char ch = raw.charAt(i);
            if (ch != '\\') {
                out.append(ch);
                continue;
            }
            if (++i >= raw.length() - 1) throw new IllegalArgumentException("a dangling escape: " + raw);
            char e = raw.charAt(i);
            switch (e) {
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= raw.length() - 1) throw new IllegalArgumentException("a short \\u escape: " + raw);
                    out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("an unknown escape \\" + e + ": " + raw);
            }
        }
        return out.toString();
    }

    /** Split {@code a: 1, b: "x, y"} at top-level commas, respecting quoted strings. */
    static List<String> split(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (quoted && ch == '\\') {
                cur.append(ch).append(body.charAt(++i));
                continue;
            }
            if (ch == '"') quoted = !quoted;
            if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}
