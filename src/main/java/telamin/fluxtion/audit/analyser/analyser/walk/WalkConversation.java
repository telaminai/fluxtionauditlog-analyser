package telamin.fluxtion.audit.analyser.analyser.walk;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * OA-3 — reads and validates a walk's dialogue ({@code spec-onboard-assistant-journeys.md} §6.1). Pure: no frame, no store.
 * Everything wrong is refused BY NAME before anything is installed, so a half-written journey never exists.
 *
 * <p>The bounds are product limits, stated and checked; they are not a claim that the text holds no secret. What leaves
 * the machine is shown before it is exported (§7), and public examples are DEMO data.
 */
public final class WalkConversation {

    private WalkConversation() {
    }

    public static final int MAX_TURNS = 200;
    public static final int MAX_TURN_BYTES = 16 * 1024;
    public static final int MAX_TOTAL_BYTES = 512 * 1024;
    static final Set<String> FIELDS = Set.of("version", "kind", "author", "turns");
    static final Set<String> TURN_FIELDS = Set.of("id", "role", "text");
    static final Set<String> ROLES = Set.of("user", "assistant");
    static final Set<String> KINDS = Set.of(WalkSpec.SCRIPTED, WalkSpec.RECORDED, WalkSpec.EDITED_RECORDING);
    /** Ids are short, plain and stable: they are addresses, not prose. */
    static final Pattern ID = Pattern.compile("[A-Za-z0-9_.-]{1,40}");

    /** A parse: the conversation, or the first refusal with its path. */
    public record Parsed(WalkSpec.Conversation conversation, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** Parse the verb's {@code conversation} object. Null in, null out (no dialogue). */
    public static Parsed parse(Object raw) {
        if (raw == null) return new Parsed(null, null);
        if (!(raw instanceof Map<?, ?> m)) return refuse("'conversation' is an object {version, kind, author?, turns}");
        for (Object k : m.keySet()) {
            if (!FIELDS.contains(String.valueOf(k))) {
                return refuse("'" + k + "' is not a conversation field in version " + WalkSpec.CONVERSATION_VERSION
                        + " — a conversation has version, kind, author, turns");
            }
        }
        Object v = m.get("version");
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            return refuse("'conversation.version' is required, a whole number");
        }
        if (n.doubleValue() != WalkSpec.CONVERSATION_VERSION) {
            return refuse("conversation version " + n + " is not supported by this analyser (it reads version "
                    + WalkSpec.CONVERSATION_VERSION + "): upgrade to author it");
        }
        String kind = m.get("kind") == null ? "" : String.valueOf(m.get("kind"));
        if (!KINDS.contains(kind)) return refuse("'conversation.kind' is one of " + KINDS + " — '" + kind + "' was given");
        if (!(m.get("turns") instanceof List<?> list)) return refuse("'conversation.turns' is a list of {id, role, text}");
        if (list.isEmpty()) return refuse("a conversation needs at least one turn");
        if (list.size() > MAX_TURNS) return refuse("at most " + MAX_TURNS + " turns — " + list.size() + " were given");
        List<WalkSpec.Turn> turns = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String at = "turn " + (i + 1) + ": ";
            if (!(list.get(i) instanceof Map<?, ?> t)) return refuse(at + "a turn is an object {id, role, text}");
            for (Object k : t.keySet()) {
                if (!TURN_FIELDS.contains(String.valueOf(k))) {
                    return refuse(at + "'" + k + "' is not a turn field — a turn has id, role, text (and nothing that runs)");
                }
            }
            turns.add(new WalkSpec.Turn(str(t.get("id")), str(t.get("role")), str(t.get("text"))));
        }
        WalkSpec.Conversation c = new WalkSpec.Conversation(n.intValue(), kind,
                m.get("author") == null ? "" : String.valueOf(m.get("author")), turns);
        String problem = problem(c);
        return problem == null ? new Parsed(c, null) : refuse(problem);
    }

    /** What is wrong with {@code c} on its own, or null. */
    public static String problem(WalkSpec.Conversation c) {
        if (c == null) return null;
        if (!c.supported()) return "conversation version " + c.version() + " is not supported by this analyser";
        if (!KINDS.contains(c.kind())) return "conversation kind '" + c.kind() + "' is not one of " + KINDS;
        if (c.turns().isEmpty()) return "a conversation needs at least one turn";
        if (c.turns().size() > MAX_TURNS) return "at most " + MAX_TURNS + " turns";
        Set<String> ids = new HashSet<>();
        long total = 0;
        for (int i = 0; i < c.turns().size(); i++) {
            WalkSpec.Turn t = c.turns().get(i);
            String at = "turn " + (i + 1) + ": ";
            if (!ID.matcher(t.id()).matches()) return at + "id '" + t.id() + "' must be 1–40 letters, digits, '.', '_' or '-'";
            if (!ids.add(t.id())) return at + "id '" + t.id() + "' is used twice";
            if (!ROLES.contains(t.role())) return at + "role is 'user' or 'assistant' — '" + t.role() + "' was given";
            if (t.text().isBlank()) return at + "has no text";
            int bytes = t.text().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_TURN_BYTES) return at + "is " + bytes + " bytes; a turn holds at most " + MAX_TURN_BYTES;
            total += bytes + t.id().length() + t.role().length();
        }
        // The documented budget is serialized UTF-8 JSON, including author, field names and escaping.
        var json = new java.util.LinkedHashMap<String, Object>();
        json.put("version", c.version()); json.put("kind", c.kind()); json.put("author", c.author());
        json.put("turns", c.turns().stream().map(t -> Map.of("id", t.id(), "role", t.role(), "text", t.text())).toList());
        total = telamin.fluxtion.audit.analyser.analyser.llm.Json.write(json).getBytes(StandardCharsets.UTF_8).length;
        if (total > MAX_TOTAL_BYTES) return "the conversation is " + total + " bytes; at most " + MAX_TOTAL_BYTES;
        return null;
    }

    /**
     * What is wrong with {@code steps} as bound to {@code c}, or null (§6.1): each step that names a turn names one that
     * exists, reveals never go backwards along the walk, and step ids are unique. A walk with dialogue gives every step
     * an id, so the binding survives a reorder.
     */
    public static String bindingProblem(WalkSpec.Conversation c, List<WalkSpec.Step> steps) {
        if (c == null) {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).through() != null) {
                    return "step " + (i + 1) + " reveals turn '" + steps.get(i).through() + "' but the walk has no conversation";
                }
            }
            return null;
        }
        Set<String> ids = new HashSet<>();
        int last = -1;
        for (int i = 0; i < steps.size(); i++) {
            WalkSpec.Step s = steps.get(i);
            String at = "step " + (i + 1) + ": ";
            if (s.id() == null) return at + "a walk with dialogue gives every step an id";
            if (!ID.matcher(s.id()).matches()) return at + "id '" + s.id() + "' must be 1–40 letters, digits, '.', '_' or '-'";
            if (!ids.add(s.id())) return at + "id '" + s.id() + "' is used twice";
            if (s.through() == null) continue;
            int at2 = c.indexOf(s.through());
            if (at2 < 0) return at + "reveals turn '" + s.through() + "', which the conversation does not have";
            if (at2 < last) {
                return at + "reveals up to turn '" + s.through() + "', before what an earlier step already revealed — a "
                        + "walk's conversation only grows as it goes";
            }
            last = at2;
        }
        return null;
    }

    /** Give every step an id when the walk has dialogue: kept ids stay, missing ones are {@code s1}, {@code s2}, … unused. */
    public static List<WalkSpec.Step> withIds(List<WalkSpec.Step> steps) {
        Set<String> used = new HashSet<>();
        for (WalkSpec.Step s : steps) if (s.id() != null) used.add(s.id());
        List<WalkSpec.Step> out = new ArrayList<>();
        int n = 1;
        for (WalkSpec.Step s : steps) {
            if (s.id() != null) {
                out.add(s);
                continue;
            }
            while (used.contains("s" + n)) n++;
            used.add("s" + n);
            out.add(s.withBinding("s" + n, s.through()));
        }
        return out;
    }

    /** The turns visible at step {@code step}: every turn up to the last one any step so far revealed. */
    public static List<WalkSpec.Turn> prefix(WalkSpec walk, int step) {
        WalkSpec.Conversation c = walk.conversation();
        if (c == null || !c.supported() || step < 0) return List.of();
        int last = -1;
        for (int i = 0; i <= step && i < walk.steps().size(); i++) {
            String through = walk.steps().get(i).through();
            if (through != null) last = Math.max(last, c.indexOf(through));
        }
        return last < 0 ? List.of() : c.turns().subList(0, last + 1);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Parsed refuse(String why) {
        return new Parsed(null, why);
    }
}
