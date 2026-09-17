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

    /** Verbs that change the view, and therefore end a spotlight (D-SP3): it would point at the wrong thing. */
    public static final List<String> VIEW_CHANGING_VERBS = List.of("open", "filter", "goto", "graph", "topology");
}
