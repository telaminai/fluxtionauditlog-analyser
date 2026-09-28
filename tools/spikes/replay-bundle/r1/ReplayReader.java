package com.acme.demo.replay;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads what ReplayCapture writes: each record's event, rebuilt through its canonical constructor, and its instant. */
public final class ReplayReader {
    public record Entry(Object event, long time, boolean raised) { }

    private static final Pattern EVENT = Pattern.compile("^event: !!(\\S+) \\{(.*)}$", Pattern.MULTILINE);
    private static final Pattern TIME = Pattern.compile("^wallClockTime: (-?\\d+)$", Pattern.MULTILINE);

    public static List<Entry> read(String yaml) throws ReflectiveOperationException {
        List<Entry> out = new ArrayList<>();
        for (String doc : yaml.split("(?m)^---$")) {
            if (doc.isBlank()) continue;
            Matcher e = EVENT.matcher(doc), t = TIME.matcher(doc);
            if (!e.find() || !t.find()) throw new IllegalArgumentException("not a replay record: " + doc.strip());
            out.add(new Entry(build(Class.forName(e.group(1)), e.group(2)), Long.parseLong(t.group(1)),
                    doc.contains("\n# raised\n") || doc.startsWith("# raised\n") || doc.startsWith("\n# raised\n")));
        }
        return out;
    }

    static Object build(Class<?> type, String body) throws ReflectiveOperationException {
        RecordComponent[] parts = type.getRecordComponents();
        List<String> values = split(body);
        if (values.size() != parts.length) throw new IllegalArgumentException(type.getName() + ": " + body);
        Class<?>[] types = new Class<?>[parts.length];
        Object[] args = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String kv = values.get(i);
            String name = kv.substring(0, kv.indexOf(':')).strip(), raw = kv.substring(kv.indexOf(':') + 1).strip();
            if (!name.equals(parts[i].getName())) throw new IllegalArgumentException("expected " + parts[i].getName() + ", got " + name);
            types[i] = parts[i].getType();
            args[i] = value(types[i], raw);
        }
        Constructor<?> c = type.getDeclaredConstructor(types);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static Object value(Class<?> t, String raw) {
        if (t == String.class) return raw.substring(1, raw.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        if (t == double.class || t == Double.class) return Double.parseDouble(raw);
        if (t == int.class || t == Integer.class) return Integer.parseInt(raw);
        if (t == long.class || t == Long.class) return Long.parseLong(raw);
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(raw);
        throw new IllegalArgumentException("unsupported type " + t);
    }

    /** Split "a: 1, b: \"x, y\"" at top-level commas, respecting quoted strings. */
    static List<String> split(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (quoted && ch == '\\') { cur.append(ch).append(body.charAt(++i)); continue; }
            if (ch == '"') quoted = !quoted;
            if (ch == ',' && !quoted) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}
