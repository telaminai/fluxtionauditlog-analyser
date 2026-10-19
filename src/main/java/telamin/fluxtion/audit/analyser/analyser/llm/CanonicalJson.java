package telamin.fluxtion.audit.analyser.analyser.llm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * M74 P3 (spec-claude-code-provider.md §5.7, OD-74.17): ONE canonical form of a tool's input, so the input the person
 * was shown (the CLI's {@code can_use_tool}) and the arguments the analyser's hosted bridge later presents
 * ({@code tools/call}, which carries the host's {@code updatedInput} [P3-0, P3-3]) digest to the same value exactly when
 * they are the same JSON. Keys are sorted; an integral number is written as an integer whichever side parsed it as a
 * double (the bridge narrows integral doubles to {@code long}, the parser reads every number as {@code Double}); strings
 * are written by {@link Json#write}.
 *
 * <p>Pure. The digest is a short sha-256 (16 hex) — an id the session compares, never words.
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    /** The most nesting the dialog shows; deeper is denied without a dialog (§5.7). */
    public static final int MAX_DEPTH = 32;
    /** The largest canonical input the dialog shows, in UTF-8 bytes; larger is denied without a dialog (§5.7). */
    public static final int MAX_BYTES = 64 * 1024;

    /** The canonical text of {@code value}. */
    public static String canonical(Object value) {
        return Json.write(normalise(value));
    }

    /** The digest of {@code value}'s canonical text; {@code null} for none. */
    public static String digest(Object value) {
        String c = canonical(value);
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(c.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** How deeply {@code value} nests (a scalar is 0, an object of scalars 1). */
    public static int depth(Object value) {
        return switch (value) {
            case Map<?, ?> m -> {
                int d = 0;
                for (Object v : m.values()) d = Math.max(d, depth(v));
                yield d + 1;
            }
            case List<?> l -> {
                int d = 0;
                for (Object v : l) d = Math.max(d, depth(v));
                yield d + 1;
            }
            case null, default -> 0;
        };
    }

    /**
     * What a raw input says, judged for the dialog: whether it parses, how deep it is, how large its canonical form is,
     * and its digest. A raw text that is not JSON, nests past {@link #MAX_DEPTH} or is over {@link #MAX_BYTES} canonical
     * is not showable — it is denied, never truncated (§5.7).
     */
    public record Judged(boolean showable, String digest, int canonicalBytes, int depth, String why) {
    }

    public static Judged judge(String raw) {
        if (raw == null) return new Judged(false, null, 0, 0, "no input");
        if (raw.length() > 4 * MAX_BYTES) return new Judged(false, null, raw.length(), 0, "over 64 KB");
        Object parsed;
        try {
            parsed = Json.parse(raw);
        } catch (RuntimeException | StackOverflowError e) {
            return new Judged(false, null, raw.length(), 0, "not JSON");
        }
        int depth;
        try {
            depth = depth(parsed);
        } catch (StackOverflowError e) {
            return new Judged(false, null, raw.length(), MAX_DEPTH + 1, "nested too deeply");
        }
        if (depth > MAX_DEPTH) return new Judged(false, null, raw.length(), depth, "nested deeper than 32 levels");
        String c = canonical(parsed);
        int bytes = c.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) return new Judged(false, null, bytes, depth, "over 64 KB");
        return new Judged(true, digest(parsed), bytes, depth, null);
    }

    private static Object normalise(Object v) {
        return switch (v) {
            case Double d when d == Math.rint(d) && !d.isInfinite() && Math.abs(d) <= 9.007199254740992E15 -> (long) (double) d;
            case Float f when f == Math.rint(f) && !f.isInfinite() -> (long) (float) f;
            case Integer i -> (long) i;
            case Short s -> (long) s;
            case Byte b -> (long) b;
            case Map<?, ?> m -> {
                TreeMap<String, Object> out = new TreeMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), normalise(e.getValue()));
                yield out;
            }
            case List<?> l -> {
                List<Object> out = new ArrayList<>(l.size());
                for (Object o : l) out.add(normalise(o));
                yield out;
            }
            case null, default -> v;
        };
    }
}
