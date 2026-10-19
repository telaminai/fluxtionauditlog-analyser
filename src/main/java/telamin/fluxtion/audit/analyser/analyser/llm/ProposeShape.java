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
 * not an object; an unknown top-level key. Option 2 (owner, 2026-10-18): a {@code starter} item — create a project from
 * one of the analyser's starter templates — stands ALONE: two starters, or a starter beside any other kind, is refused
 * whole (the set-up items belong to the project the starter makes, which does not exist yet).
 */
public final class ProposeShape {

    private ProposeShape() {
    }

    public static final int MAX_ITEMS = 10;
    public static final int MAX_SUMMARY = 500;
    public static final int MAX_PATH = 1024;
    public static final int MAX_CLASS_NAME = 300;
    public static final int MAX_REASON = 500;
    /** A starter id and each of its optional names: the AskPointers identifier bound. */
    public static final int MAX_NAME = 100;
    /** The starter item's kind. */
    public static final String STARTER = "starter";

    private static final Set<String> TOP_LEVEL = Set.of("summary", "items");
    private static final Pattern QUALIFIED = Pattern.compile(
            "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");

    /**
     * One item as Claude sent it, with what was OBSERVED about its fields (the node decides what that means). A starter
     * item names {@code starter} (a catalogue id) and optionally {@code artifact}, {@code group} and {@code basePackage};
     * {@code namesMalformed} says one of the four does not match the identifier rules (AskPointers' bounded identifier,
     * and the scaffold's own artifact and Java-package forms).
     */
    public record Item(String kind, String path, String className, String reason, boolean tooLong,
                       boolean classNameMalformed, String starter, String artifact, String group, String basePackage,
                       boolean namesMalformed) {
        public Item(String kind, String path, String className, String reason, boolean tooLong,
                    boolean classNameMalformed) {
            this(kind, path, className, reason, tooLong, classNameMalformed, null, null, null, null, false);
        }
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
            String starter = text(item.get("starter"));
            String artifact = text(item.get("artifact"));
            String group = text(item.get("group"));
            String basePackage = text(item.get("basePackage"));
            boolean tooLong = over(path, MAX_PATH) || over(className, MAX_CLASS_NAME) || over(reason, MAX_REASON)
                    || over(starter, MAX_NAME) || over(artifact, MAX_NAME) || over(group, MAX_NAME)
                    || over(basePackage, MAX_NAME);
            boolean malformed = className != null && !QUALIFIED.matcher(className).matches();
            boolean namesMalformed = (starter != null && !telamin.fluxtion.audit.analyser.analyser.assistant.AskPointers
                    .isIdentifier(starter))
                    || (artifact != null && !(identifier(artifact) && TemplateClientRules.artifact(artifact)))
                    || (group != null && !(identifier(group) && TemplateClientRules.javaPackage(group)))
                    || (basePackage != null && !(identifier(basePackage) && TemplateClientRules.javaPackage(basePackage)));
            items.add(new Item(kind, path, className, reason, tooLong, malformed, starter, artifact, group, basePackage,
                    namesMalformed));
        }
        long starters = items.stream().filter(i -> STARTER.equals(i.kind())).count();
        if (starters > 1) {
            return refuse("a proposal holds at most one starter item");
        }
        if (starters == 1 && items.size() > 1) {
            return refuse("a starter item stands alone: propose the set-up items after the project exists");
        }
        String summary = text(m.get("summary"));
        if (over(summary, MAX_SUMMARY)) summary = summary.substring(0, MAX_SUMMARY);
        return new Shaped(new Call(summary, items), null);
    }

    private static boolean identifier(String s) {
        return telamin.fluxtion.audit.analyser.analyser.assistant.AskPointers.isIdentifier(s);
    }

    /** The scaffold's own forms for the optional names (TemplateClient validates them again at download). */
    private static final class TemplateClientRules {
        static boolean artifact(String s) {
            return telamin.fluxtion.audit.analyser.analyser.template.TemplateClient.validArtifact(s);
        }

        static boolean javaPackage(String s) {
            return telamin.fluxtion.audit.analyser.analyser.template.TemplateClient.validPackage(s);
        }
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
