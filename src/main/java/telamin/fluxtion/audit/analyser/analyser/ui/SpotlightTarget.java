package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * M64 — WHAT a spotlight points at: a fixed, small vocabulary named as a person would say it
 * ({@code spec-spotlight.md} D-SP2), and the pure resolution of a name to a rectangle (D-SP3).
 *
 * <h2>Why the vocabulary is closed</h2>
 * Every panel could expose every widget; that is a registry nobody maintains and a vocabulary no skill
 * remembers. So a target is one of the families below and nothing else, and a family is added only when a
 * beat of a runbook needs a target it does not have. A name outside the vocabulary is a plain error that
 * NAMES the vocabulary — never a spotlight on nothing.
 *
 * <h2>Why resolution is separated from the frame</h2>
 * {@link #resolve} is a pure function over a {@link Surface} — the frame's answer to "where is this, and
 * can you bring it on screen?". The frame implements it with Swing; the tests implement it with a map.
 * That is what lets every vocabulary entry, and the not-visible and unknown cases, be tested headless.
 */
public record SpotlightTarget(Family family, String argument, String name) {

    /** The eight families of D-SP2, split where a family has a whole-and-part form. */
    public enum Family {
        TAB("tab:<summary|source|graph|topology|reports|assistant>", true),
        RECORDS("records", false),
        RECORDS_ROW("records:row:<recordIndex>", true),
        DETAIL("detail", false),
        DETAIL_NODE("detail:node:<instanceId>", true),
        TOPOLOGY("topology", false),
        TOPOLOGY_NODE("topology:node:<instanceId>", true),
        COVERAGE("coverage", false),
        GRAPH("graph", false),
        GRAPH_NOTE("graph:note:<n>", true),
        GRAPH_SERIES("graph:series:<label>", true),
        PROJECT("project", false),
        PROJECT_ROW("project:<log|graph|processors|roots>", true),
        TOOLBAR("toolbar:<open|flag|explain|follow>", true),
        STATUS("status", false);

        private final String form;
        private final boolean takesArgument;

        Family(String form, boolean takesArgument) {
            this.form = form;
            this.takesArgument = takesArgument;
        }

        public String form() {
            return form;
        }
    }

    public static final Set<String> TABS = Set.of("summary", "source", "graph", "topology", "reports", "assistant");
    public static final Set<String> PROJECT_ROWS = Set.of("log", "graph", "processors", "roots");
    public static final Set<String> TOOLBAR_BUTTONS = Set.of("open", "flag", "explain", "follow");

    /** The whole vocabulary, as the error for an unknown target prints it. */
    public static String vocabulary() {
        StringBuilder out = new StringBuilder();
        for (Family f : Family.values()) out.append(out.length() == 0 ? "" : " · ").append(f.form());
        return out.toString();
    }

    /** A parsed name, or the reason it is not in the vocabulary. Exactly one is present. */
    public record Parsed(SpotlightTarget target, String error) {
        public boolean ok() {
            return target != null;
        }
    }

    /**
     * Parse a target name. Family words are case-insensitive; an ARGUMENT keeps its case, because an
     * instanceId and a series label are the user's own spelling.
     */
    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) return unknown("'target' is required");
        String name = raw.trim();
        int first = name.indexOf(':');
        String head = (first < 0 ? name : name.substring(0, first)).toLowerCase(Locale.ROOT);
        String rest = first < 0 ? null : name.substring(first + 1);
        return switch (head) {
            case "tab" -> member(Family.TAB, rest, TABS, name);
            case "toolbar" -> member(Family.TOOLBAR, rest, TOOLBAR_BUTTONS, name);
            case "status" -> bare(Family.STATUS, rest, name);
            case "coverage" -> bare(Family.COVERAGE, rest, name);
            case "records" -> rest == null ? ok(Family.RECORDS, null, name)
                    : sub(rest, "row", Family.RECORDS_ROW, name, true);
            case "detail" -> rest == null ? ok(Family.DETAIL, null, name)
                    : sub(rest, "node", Family.DETAIL_NODE, name, false);
            case "topology" -> rest == null ? ok(Family.TOPOLOGY, null, name)
                    : sub(rest, "node", Family.TOPOLOGY_NODE, name, false);
            case "graph" -> {
                if (rest == null) yield ok(Family.GRAPH, null, name);
                String part = rest.toLowerCase(Locale.ROOT);
                if (part.startsWith("note")) yield sub(rest, "note", Family.GRAPH_NOTE, name, true);
                if (part.startsWith("series")) yield sub(rest, "series", Family.GRAPH_SERIES, name, false);
                yield unknown("'" + name + "' is not graph, " + Family.GRAPH_NOTE.form() + " or " + Family.GRAPH_SERIES.form());
            }
            case "project" -> rest == null ? ok(Family.PROJECT, null, name)
                    : member(Family.PROJECT_ROW, rest, PROJECT_ROWS, name);
            default -> unknown("unknown spotlight target '" + name + "'");
        };
    }

    private static Parsed member(Family family, String rest, Set<String> allowed, String name) {
        String arg = rest == null ? "" : rest.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(arg)) {
            return unknown("'" + name + "' is not a " + family.form() + " target");
        }
        return ok(family, arg, name);
    }

    private static Parsed bare(Family family, String rest, String name) {
        return rest == null ? ok(family, null, name)
                : unknown("'" + name + "' takes no argument — it is just '" + family.form() + "'");
    }

    /** {@code <keyword>:<argument>} — e.g. {@code row:42}, {@code node:priceListener}. */
    private static Parsed sub(String rest, String keyword, Family family, String name, boolean numeric) {
        int colon = rest.indexOf(':');
        String key = (colon < 0 ? rest : rest.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
        String arg = colon < 0 ? "" : rest.substring(colon + 1).trim();
        if (!key.equals(keyword) || arg.isEmpty()) {
            return unknown("'" + name + "' is not of the form " + family.form());
        }
        if (numeric) {
            try {
                if (Integer.parseInt(arg) < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                return unknown("'" + name + "' needs a non-negative number — the form is " + family.form());
            }
        }
        return ok(family, arg, name);
    }

    private static Parsed ok(Family family, String argument, String name) {
        return new Parsed(new SpotlightTarget(family, argument, name), null);
    }

    private static Parsed unknown(String why) {
        return new Parsed(null, why + ". The vocabulary is: " + vocabulary());
    }

    /** The argument as a number, for the two numeric families. */
    public int number() {
        return Integer.parseInt(argument);
    }

    // ---- resolution ---------------------------------------------------------------------------------

    /** The frame's half: bring a target on screen, and say where it is. */
    public interface Surface {
        /**
         * Reveal the target with the same moves the existing verbs use — select its tab, scroll to its
         * row, centre its node. Best effort; whether it worked is what {@link #bounds} then reports.
         */
        void reveal(SpotlightTarget target);

        /** Where the target is, in the overlay's coordinates; empty when it is not on screen or does not exist. */
        Optional<Rectangle> bounds(SpotlightTarget target);

        /** Why {@link #bounds} was empty, in words a tutor can act on. */
        String whyNotVisible(SpotlightTarget target);
    }

    public enum Outcome { LIT, NOT_VISIBLE, UNKNOWN }

    /** A typed answer: never a spotlight on nothing. */
    public record Resolution(Outcome outcome, SpotlightTarget target, Rectangle bounds, String reason) {
        public boolean lit() {
            return outcome == Outcome.LIT;
        }
    }

    /**
     * Resolve a name: parse it, reveal it, ask where it is. A target that is off screen is revealed
     * FIRST and only then measured (D-SP2); one that still has no area is NOT_VISIBLE with the surface's
     * reason; one outside the vocabulary is UNKNOWN and the surface is never consulted.
     */
    public static Resolution resolve(String name, Surface surface) {
        Parsed parsed = parse(name);
        if (!parsed.ok()) return new Resolution(Outcome.UNKNOWN, null, null, parsed.error());
        SpotlightTarget target = parsed.target();
        surface.reveal(target);
        Optional<Rectangle> bounds = surface.bounds(target);
        if (bounds.isEmpty() || bounds.get().width <= 0 || bounds.get().height <= 0) {
            return new Resolution(Outcome.NOT_VISIBLE, target, null, surface.whyNotVisible(target));
        }
        return new Resolution(Outcome.LIT, target, new Rectangle(bounds.get()), null);
    }

    // ---- several at once (M64.6) --------------------------------------------------------------------

    public static final int MAX_LIT = telamin.fluxtion.audit.analyser.analyser.llm.SpotlightVocabulary.MAX_LIT;
    public static final int MAX_CAPTION = telamin.fluxtion.audit.analyser.analyser.llm.SpotlightVocabulary.MAX_CAPTION;

    /** One thing to light, and the callout that goes with it (may be null: a cut-out needs no words). */
    public record Request(String target, String caption) {
    }

    /**
     * What a {@code spotlight} call asked for. {@code add} keeps what is already lit; without it the call
     * REPLACES the lit set. Exactly one of {@code requests} / {@code error} is meaningful.
     */
    public record Requests(List<Request> requests, boolean add, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /**
     * Read a call's parameters: either {@code target} (+ {@code caption}), or {@code targets} — a list whose
     * entries are {@code {target, caption?}} objects or bare target names. Pure, so the verb's whole input
     * grammar is tested headless. Nothing is resolved here; this only says what was asked.
     */
    public static Requests requests(java.util.Map<String, Object> params) {
        boolean add = Boolean.TRUE.equals(params.get("add"));
        Object one = params.get("target");
        Object many = params.get("targets");
        if (one != null && many != null) {
            return refuse("give 'target' (one) or 'targets' (several), not both");
        }
        List<Request> out = new java.util.ArrayList<>();
        if (many == null) {
            out.add(new Request(one == null ? null : one.toString(), text(params.get("caption"))));
        } else {
            if (!(many instanceof List<?> list) || list.isEmpty()) {
                return refuse("'targets' is a non-empty list of {target, caption?}");
            }
            if (params.get("caption") != null) {
                return refuse("with 'targets' each entry carries its own 'caption' — a top-level caption would belong to none of them");
            }
            for (Object entry : list) {
                if (entry instanceof java.util.Map<?, ?> m) {
                    for (Object key : m.keySet()) {
                        if (!"target".equals(key) && !"caption".equals(key)) {
                            return refuse("a 'targets' entry has only 'target' and 'caption' — not '" + key + "'");
                        }
                    }
                    Object t = m.get("target");
                    out.add(new Request(t == null ? null : t.toString(), text(m.get("caption"))));
                } else if (entry instanceof String name) {
                    out.add(new Request(name, null));
                } else {
                    return refuse("a 'targets' entry is {target, caption?} or a target name");
                }
            }
        }
        if (out.size() > MAX_LIT) {
            return refuse("at most " + MAX_LIT + " spotlights at once — " + out.size() + " were asked for. "
                    + "More than that and nothing on screen is being pointed at any more");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Request r : out) {
            String why = captionError(r.caption());
            if (why != null) return refuse(why);
            if (r.target() != null && !seen.add(r.target().trim().toLowerCase(Locale.ROOT))) {
                return refuse("'" + r.target().trim() + "' is named twice — one target carries one callout");
            }
        }
        return new Requests(List.copyOf(out), add, null);
    }

    /**
     * Everything about a call that can be judged WITHOUT touching the screen — judged first, so that a call
     * refused for any of these reasons has moved nothing (review of M64.6, F2). Returns the reason, or null.
     *
     * <p>{@link #resolveAll} already parsed every name before its first reveal, but the verb's real entrance
     * reveals a {@code records:row} through {@code goto}'s path BEFORE it gets that far — and that reveal
     * relaxes the filter and changes the selection. So {@code spotlight {targets: ["records:row:15",
     * "not-a-target"]}} was refused and had still erased the person's filter; likewise a seventh target
     * added to six. The grammar, every name, and the bound over the union of what is lit and what is asked
     * are therefore checked here, by the executor, before it reveals anything.
     *
     * <p>What this deliberately does NOT cover: a well-formed call whose target turns out not to be on screen
     * (a node the graph does not have; two things on different tabs). Finding that out IS the reveal, and
     * D-SP6 permits its side effects. The line is: a call that is WRONG touches nothing; a call that is
     * right but cannot be shown may have brought another view forward, and says so.
     */
    public static String precheck(Requests asked, java.util.Collection<String> litNow) {
        return precheck(asked, litNow, -1);
    }

    /**
     * As above, and: a {@code records:row:<n>} must be a record this log HAS (re-review R5).
     * {@code recordCount} is the open log's size, or negative when no log is open (the surface then refuses the
     * row with "no log is open", having touched nothing, because there is nothing to reveal).
     *
     * <p>I had put an out-of-range row on the other side of the line — "well-formed, finding out is the
     * reveal". It is not: whether record 99999 exists is a fact about the store, knowable without the screen.
     * And the reveal did not merely fail to find it — {@code goto} CLAMPS an index to the last record, so
     * {@code spotlight {target: "records:row:99999"}} relaxed the person's filter, selected the LAST record,
     * and then refused without mentioning either. goto's clamping is goto's contract and stays; the spotlight
     * simply never asks goto for a row that does not exist.
     */
    public static String precheck(Requests asked, java.util.Collection<String> litNow, int recordCount) {
        if (!asked.ok()) return asked.error();
        java.util.Set<String> union = new java.util.HashSet<>();
        if (asked.add() && litNow != null) for (String name : litNow) union.add(name.trim().toLowerCase(Locale.ROOT));
        int standing = union.size();
        for (Request r : asked.requests()) {
            Parsed parsed = parse(r.target());
            if (!parsed.ok()) return parsed.error();
            if (recordCount >= 0 && parsed.target().family() == Family.RECORDS_ROW && parsed.target().number() >= recordCount) {
                return "'" + parsed.target().name() + "': there is no record " + parsed.target().number() + " — this log has "
                        + recordCount + (recordCount == 0 ? " records" : " (0 to " + (recordCount - 1) + ")")
                        + ". Nothing was changed: your filter, selection and spotlights are as they were";
            }
            union.add(parsed.target().name().toLowerCase(Locale.ROOT));
        }
        if (asked.add() && union.size() > MAX_LIT) {
            return "at most " + MAX_LIT + " spotlights at once — " + standing + " are lit. Put one out first "
                    + "({clear: true, target: …}), or light a new set without 'add'";
        }
        return null;
    }

    /** Why a caption is refused, or null when it is fine (null and blank captions are fine: no callout). */
    public static String captionError(String caption) {
        if (caption == null) return null;
        if (caption.length() > MAX_CAPTION || caption.chars().anyMatch(ch -> ch == '\n' || ch == '\r')) {
            return "'caption' is ONE short line (at most " + MAX_CAPTION + " characters) — the sentence belongs in "
                    + "your chat, where it is clearly yours; the caption only says why to look here";
        }
        return null;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Requests refuse(String why) {
        return new Requests(List.of(), false, why);
    }

    /** The answer for a SET: every member lit, or none — with the one reason. */
    public record SetResolution(List<Resolution> lit, String reason) {
        public boolean ok() {
            return reason == null;
        }
    }

    /**
     * Resolve several names as ONE request: all of them light, or none does (validate everything before
     * anything changes — the same rule the canvas handoff applies to a record).
     *
     * <p>Every name is parsed before the surface is touched, so a misspelling in the third entry reveals
     * nothing. Then each is revealed and measured in order, and after each reveal every EARLIER target is
     * measured again — because revealing a later target can hide an earlier one (a topology node and a
     * chart note live on different tabs). Two things that cannot be on screen together are refused, naming
     * the pair, rather than lit half-true.
     */
    public static SetResolution resolveAll(List<String> names, Surface surface) {
        if (names == null || names.isEmpty()) return new SetResolution(List.of(), parse(null).error());
        List<SpotlightTarget> targets = new java.util.ArrayList<>();
        for (String name : names) {
            Parsed parsed = parse(name);
            if (!parsed.ok()) return new SetResolution(List.of(), parsed.error());
            targets.add(parsed.target());
        }
        for (int i = 0; i < targets.size(); i++) {
            SpotlightTarget target = targets.get(i);
            surface.reveal(target);
            if (measured(surface, target).isEmpty()) {
                return new SetResolution(List.of(), (targets.size() > 1 ? "'" + target.name() + "': " : "")
                        + surface.whyNotVisible(target));
            }
            for (int j = 0; j < i; j++) {
                if (measured(surface, targets.get(j)).isEmpty()) {
                    return new SetResolution(List.of(), "'" + targets.get(j).name() + "' and '" + target.name()
                            + "' cannot be on screen at the same time — bringing the second into view hid the "
                            + "first. Light them one after the other");
                }
            }
        }
        List<Resolution> lit = new java.util.ArrayList<>();
        for (SpotlightTarget target : targets) {                 // measured LAST: a later reveal may have scrolled an earlier one
            Optional<Rectangle> bounds = measured(surface, target);
            if (bounds.isEmpty()) return new SetResolution(List.of(), surface.whyNotVisible(target));
            lit.add(new Resolution(Outcome.LIT, target, new Rectangle(bounds.get()), null));
        }
        return new SetResolution(List.copyOf(lit), null);
    }

    private static Optional<Rectangle> measured(Surface surface, SpotlightTarget target) {
        Optional<Rectangle> bounds = surface.bounds(target);
        return bounds.isEmpty() || bounds.get().width <= 0 || bounds.get().height <= 0 ? Optional.empty() : bounds;
    }

    /** Verbs that change the view, and therefore end a spotlight (D-SP3): it would point at the wrong thing. */
    public static final List<String> VIEW_CHANGING_VERBS = List.of("open", "filter", "goto", "graph", "topology");
}
