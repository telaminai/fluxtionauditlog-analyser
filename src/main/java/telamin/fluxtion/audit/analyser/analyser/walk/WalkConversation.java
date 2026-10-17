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
    /** M74 P2b (§6): v2 adds {@code source}, and a turn's {@code truncated}. */
    static final Set<String> FIELDS_V2 = Set.of("version", "kind", "author", "turns", "source");
    static final Set<String> TURN_FIELDS_V2 = Set.of("id", "role", "text", "truncated");
    static final Set<String> SOURCE_FIELDS = Set.of("kind", "model", "cliVersion");
    /** Review P2 N-g: a source's model and CLI version are short names — the models of a recording joined by ", ". */
    static final int MAX_SOURCE_CHARS = 200;
    static final java.util.regex.Pattern SOURCE_TEXT = java.util.regex.Pattern.compile("[A-Za-z0-9 .,_:\\[\\]-]*");
    /** What a truncated turn ends with (§6). */
    public static final String TRUNCATED_MARK = "[truncated by the analyser]";
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
        Object v = m.get("version");
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            return refuse("'conversation.version' is required, a whole number");
        }
        boolean v2 = n.doubleValue() == WalkSpec.CONVERSATION_V2;
        if (n.doubleValue() != WalkSpec.CONVERSATION_VERSION && !v2) {
            return refuse("conversation version " + n + " is not supported by this analyser (it reads versions "
                    + WalkSpec.CONVERSATION_VERSION + " and " + WalkSpec.CONVERSATION_V2 + "): upgrade to author it");
        }
        for (Object k : m.keySet()) {
            if (!(v2 ? FIELDS_V2 : FIELDS).contains(String.valueOf(k))) {
                return refuse("'" + k + "' is not a conversation field in version " + n.intValue()
                        + (v2 ? " — a conversation has version, kind, author, turns, source"
                        : " — a conversation has version, kind, author, turns"));
            }
        }
        WalkSpec.Source source = null;
        if (v2) {
            if (!(m.get("source") instanceof Map<?, ?> src)) {
                return refuse("a version 2 conversation names its 'source': {kind: \"claude-code\", model, cliVersion}");
            }
            for (Object k : src.keySet()) {
                if (!SOURCE_FIELDS.contains(String.valueOf(k))) {
                    return refuse("'source." + k + "' is not a source field — a source has kind, model, cliVersion "
                            + "(never a session id, path, account, cost or budget)");
                }
            }
            source = new WalkSpec.Source(str(src.get("kind")), str(src.get("model")), str(src.get("cliVersion")));
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
                if (!(v2 ? TURN_FIELDS_V2 : TURN_FIELDS).contains(String.valueOf(k))) {
                    return refuse(at + "'" + k + "' is not a turn field — a turn has id, role, text"
                            + (v2 ? ", truncated" : "") + " (and nothing that runs)");
                }
            }
            Object truncated = t.get("truncated");
            if (truncated != null && !(truncated instanceof Boolean)) {
                return refuse(at + "'truncated' is true or false — " + truncated + " was given");   // review P2 N-g
            }
            turns.add(new WalkSpec.Turn(str(t.get("id")), str(t.get("role")), str(t.get("text")),
                    Boolean.TRUE.equals(truncated)));
        }
        WalkSpec.Conversation c = new WalkSpec.Conversation(n.intValue(), kind,
                m.get("author") == null ? "" : String.valueOf(m.get("author")), turns, source);
        String problem = problem(c);
        return problem == null ? new Parsed(c, null) : refuse(problem);
    }

    /** What is wrong with {@code c} on its own, or null. */
    public static String problem(WalkSpec.Conversation c) {
        if (c == null) return null;
        if (!c.supported()) return "conversation version " + c.version() + " is not supported by this analyser";
        // §6: v2 is written ONLY with a source, and a source only in v2 — so a walk without one stays v1 byte-for-byte
        if (c.version() == WalkSpec.CONVERSATION_V2) {
            if (c.source() == null || !WalkSpec.CLAUDE_CODE_SOURCE.equals(c.source().kind())) {
                return "a version 2 conversation's source kind is '" + WalkSpec.CLAUDE_CODE_SOURCE + "'";
            }
            // review P2 N-g: what a source may say is bounded — names, not prose — on every path in (verb, file, share)
            for (String[] f : new String[][]{{"model", c.source().model()}, {"cliVersion", c.source().cliVersion()}}) {
                String v = f[1] == null ? "" : f[1];
                if (v.length() > MAX_SOURCE_CHARS || !SOURCE_TEXT.matcher(v).matches()) {
                    return "'source." + f[0] + "' is at most " + MAX_SOURCE_CHARS + " letters, digits, spaces, '.', ',', "
                            + "'_', ':', '[', ']' or '-'";
                }
            }
        } else if (c.source() != null) {
            return "a version 1 conversation has no source; a recorded source is version 2";
        }
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
        if (c.source() != null) {
            json.put("source", Map.of("kind", c.source().kind(), "model", c.source().model(), "cliVersion",
                    c.source().cliVersion()));
        }
        json.put("turns", c.turns().stream().map(t -> t.truncated()
                ? Map.<String, Object>of("id", t.id(), "role", t.role(), "text", t.text(), "truncated", true)
                : Map.<String, Object>of("id", t.id(), "role", t.role(), "text", t.text())).toList());
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

    /** A walk whose dangling bindings were dropped on load, and what was said about each (§6, [R2-9]). */
    public record Revalidated(WalkSpec walk, List<String> notes) {
    }

    /**
     * §6 [R2-9]: on load, a step's {@code conversationThrough} is checked against the steps and the turn ids. A binding
     * that no longer matches — an older analyser edited the steps while writing a v2 walk's keys back as extras — is
     * dropped and reported, never played: <i>"step 4's conversation binding named a turn that no longer exists; it was
     * removed"</i>.
     */
    public static Revalidated revalidate(WalkSpec walk) {
        WalkSpec.Conversation c = walk.conversation();
        // v2 only: the risk is an OLDER analyser editing a v2 walk's steps while it carried the v2 keys as extras. A v1
        // walk with a bad binding is still refused whole where it always was (a share's preview, the walk verb)
        if (c == null || c.version() != WalkSpec.CONVERSATION_V2) return new Revalidated(walk, List.of());
        List<String> notes = new ArrayList<>();
        List<WalkSpec.Step> steps = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < walk.steps().size(); i++) {
            WalkSpec.Step s = walk.steps().get(i);
            if (s.through() != null && c.indexOf(s.through()) < 0) {
                notes.add("step " + (i + 1) + "'s conversation binding named a turn that no longer exists; it was removed");
                steps.add(s.withBinding(s.id(), null));
                changed = true;
            } else {
                steps.add(s);
            }
        }
        return new Revalidated(changed ? walk.withSteps(steps, walk.updatedAt()) : walk, notes);
    }

    /** One offered turn from a local-agent transcript (§6 candidates): who, the words, and that turn's model or null. */
    public record Candidate(String role, String text, String model) {
    }

    /** The turns offered for a recording, after §6's limits; and how many of the oldest were left out. */
    public record Offer(List<WalkSpec.Turn> turns, int leftOut) {
    }

    /**
     * §6 limits on a local-agent recording: a turn over 16 KB is truncated at a character boundary with
     * {@value #TRUNCATED_MARK} appended and marked {@code truncated}; over 200 turns or 512 KB, the MOST RECENT turns that
     * fit are offered, and the number left out is said.
     */
    public static Offer limit(List<Candidate> candidates) {
        List<WalkSpec.Turn> all = new ArrayList<>();
        int n = 0;
        for (Candidate c : candidates) {
            n++;
            String text = c.text() == null ? "" : c.text();
            boolean cut = text.getBytes(StandardCharsets.UTF_8).length > MAX_TURN_BYTES;
            if (cut) text = truncate(text, MAX_TURN_BYTES - (" " + TRUNCATED_MARK).getBytes(StandardCharsets.UTF_8).length)
                    + " " + TRUNCATED_MARK;
            all.add(new WalkSpec.Turn("t" + n, c.role(), text, cut));
        }
        // the most recent that fit: from the end, within 200 turns and the serialized budget (with headroom for the
        // envelope: kind, author, source and the field names)
        long budget = MAX_TOTAL_BYTES - 4 * 1024;
        long used = 0;
        int from = all.size();
        while (from > 0 && all.size() - from < MAX_TURNS) {
            WalkSpec.Turn t = all.get(from - 1);
            long cost = t.text().getBytes(StandardCharsets.UTF_8).length * 2L + 64;   // escaping can double a byte
            if (used + cost > budget) break;
            used += cost;
            from--;
        }
        List<WalkSpec.Turn> kept = new ArrayList<>();
        int id = 1;
        for (WalkSpec.Turn t : all.subList(from, all.size())) kept.add(new WalkSpec.Turn("t" + id++, t.role(), t.text(), t.truncated()));
        return new Offer(kept, from);
    }

    /** {@code text} cut to at most {@code maxBytes} of UTF-8, at a character (code point) boundary. */
    static String truncate(String text, int maxBytes) {
        int bytes = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int len = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            if (bytes + len > maxBytes) break;
            bytes += len;
            i += Character.charCount(cp);
        }
        return text.substring(0, i);
    }

    /** §6: the model of every recorded turn when they agree, otherwise the distinct models in order, joined by ", ". */
    public static String models(List<String> perTurn) {
        java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<>();
        for (String m : perTurn) if (m != null && !m.isBlank()) distinct.add(m.strip());
        return String.join(", ", distinct);
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
