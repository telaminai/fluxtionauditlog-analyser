package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * M74 P2 (spec §5.6): the WHOLE-CALL shape of a hosted {@code propose}, decided here as M73 decides a create's shape —
 * pure, before anything reaches the session. A call that passes is carried to the {@code onboardingProposal} node as
 * plain data; every ITEM-level refusal (an unknown kind, a missing path, a field over its length, a bad class name, a
 * duplicate, a second processor or log) is the node's, from the per-item facts this class observes and never decides.
 *
 * <p>Whole-call refusals: params not an object; {@code items} missing, not an array, empty or over 10; an item that is
 * not an object; an unknown top-level key.
 */
public final class ProposeShape {

    private ProposeShape() {
    }

    public static final int MAX_ITEMS = 10;
    public static final int MAX_SUMMARY = 500;
    public static final int MAX_PATH = 1024;
    public static final int MAX_CLASS_NAME = 300;
    public static final int MAX_REASON = 500;

    private static final Set<String> TOP_LEVEL = Set.of("summary", "items");
    private static final Pattern QUALIFIED = Pattern.compile(
            "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");

    /** One item as Claude sent it, with what was OBSERVED about its fields (the node decides what that means). */
    public record Item(String kind, String path, String className, String reason, boolean tooLong,
                       boolean classNameMalformed) {
    }

    /** A call whose shape is acceptable. */
    public record Call(String summary, List<Item> items) {
        public Call {
            items = List.copyOf(items);
            summary = summary == null ? "" : summary;
        }
    }

    /** Exactly one of the two is non-null. */
    public record Shaped(Call call, String refusal) {
    }

    public static Shaped of(Object params) {
        if (!(params instanceof Map<?, ?> m)) return refuse("propose needs an object with 'items'");
        for (Object k : m.keySet()) {
            if (!TOP_LEVEL.contains(String.valueOf(k))) {
                return refuse("propose does not take '" + k + "': only 'summary' and 'items'");
            }
        }
        Object raw = m.get("items");
        if (raw == null) return refuse("propose needs 'items': 1 to " + MAX_ITEMS + " set-up items");
        if (!(raw instanceof List<?> list)) return refuse("'items' must be an array of 1 to " + MAX_ITEMS + " objects");
        if (list.isEmpty()) return refuse("'items' is empty: propose at least one item");
        if (list.size() > MAX_ITEMS) return refuse("'items' has " + list.size() + " entries; at most " + MAX_ITEMS);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> item)) return refuse("items[" + i + "] is not an object");
            String kind = text(item.get("kind"));
            String path = text(item.get("path"));
            String className = text(item.get("className"));
            String reason = text(item.get("reason"));
            boolean tooLong = over(path, MAX_PATH) || over(className, MAX_CLASS_NAME) || over(reason, MAX_REASON);
            boolean malformed = className != null && !QUALIFIED.matcher(className).matches();
            items.add(new Item(kind, path, className, reason, tooLong, malformed));
        }
        String summary = text(m.get("summary"));
        if (over(summary, MAX_SUMMARY)) summary = summary.substring(0, MAX_SUMMARY);
        return new Shaped(new Call(summary, items), null);
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static boolean over(String s, int max) {
        return s != null && s.codePointCount(0, s.length()) > max;
    }

    private static Shaped refuse(String why) {
        return new Shaped(null, why + " — nothing was recorded");
    }
}
