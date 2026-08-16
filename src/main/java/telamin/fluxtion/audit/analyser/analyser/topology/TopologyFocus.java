package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The exploration model for a large graph (M22.2): a selection, a <b>scope</b> that widens around it, and
 * the set of nodes that scope covers.
 *
 * <p>A 300-node processor does not fit on a screen in a form anyone can read, and the question being
 * asked is almost never "show me everything" — it is "what feeds this" or "what does this affect". Rather
 * than a dialog of options, clicking the same node again widens the scope one step, so the answer is
 * found by repeating one gesture and the current width is always visible.
 *
 * <p>The steps are chosen to match the questions in the order people ask them:
 * <ol>
 *   <li>{@link Scope#NODE} — this node;</li>
 *   <li>{@link Scope#NEIGHBOURS} — and what feeds it and what it feeds, one hop: the local picture;</li>
 *   <li>{@link Scope#ROUTES} — every route in (<em>all</em> ancestors) and everything downstream of it
 *       (all transitive children): the blast radius, in both directions;</li>
 *   <li>{@link Scope#ALL} — the whole graph, and back round.</li>
 * </ol>
 *
 * <p>Pure: takes a topology and ids, returns ids. The canvas decides whether to <em>hide</em> what falls
 * outside the scope or merely dim it — a separate choice, made by the Focus toggle.
 */
public final class TopologyFocus {

    private TopologyFocus() { }

    /** How far around the selection the scope reaches. */
    public enum Scope {
        NODE, NEIGHBOURS, ROUTES, ALL;

        /** The next scope in the click cycle; {@link #ALL} wraps back to {@link #NODE}. */
        public Scope next() {
            return switch (this) {
                case NODE -> NEIGHBOURS;
                case NEIGHBOURS -> ROUTES;
                case ROUTES -> ALL;
                case ALL -> NODE;
            };
        }

        /** Wording for the toolbar, so the current width is readable rather than guessed at. */
        public String label() {
            return switch (this) {
                case NODE -> "node";
                case NEIGHBOURS -> "+ neighbours";
                case ROUTES -> "+ all routes";
                case ALL -> "whole graph";
            };
        }
    }

    /**
     * The node ids this scope covers around {@code seeds}. An empty or unknown selection yields an empty
     * set — <b>not</b> the whole graph, so a caller filtering by it cannot accidentally show everything
     * when it meant to show one thing.
     */
    public static Set<String> expand(ProcessorTopology topology, Collection<String> seeds, Scope scope) {
        Set<String> out = new LinkedHashSet<>();
        if (topology == null || topology.isEmpty()) return out;
        if (scope == Scope.ALL) {
            out.addAll(topology.ids());
            return out;
        }
        if (seeds == null) return out;

        Set<String> known = new LinkedHashSet<>();
        for (String seed : seeds) {
            if (seed != null && topology.contains(seed)) known.add(seed);
        }
        if (known.isEmpty()) return out;
        out.addAll(known);

        switch (scope) {
            case NODE -> { }
            case NEIGHBOURS -> {
                for (String id : known) {
                    out.addAll(topology.parentsOf(id));
                    out.addAll(topology.childrenOf(id));
                }
            }
            case ROUTES -> {
                out.addAll(reach(topology, known, true));
                out.addAll(reach(topology, known, false));
            }
            default -> { }
        }
        return out;
    }

    /** Transitive closure upstream ({@code up}) or downstream, cycle-safe. */
    private static Set<String> reach(ProcessorTopology topology, Set<String> seeds, boolean up) {
        Set<String> seen = new LinkedHashSet<>(seeds);
        Deque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            for (String next : up ? topology.parentsOf(id) : topology.childrenOf(id)) {
                // a processor graph is acyclic by construction, but a hand-edited or partial graphml
                // need not be, and a stack overflow is a poor way to report that
                if (seen.add(next)) queue.addLast(next);
            }
        }
        return seen;
    }

    /**
     * The nodes a view should show, given both filters: framework scaffolding hidden or not, and the
     * focus scope applied or not. Intersecting rather than layering matters — with focus on and
     * scaffolding hidden, a scaffolding node inside the scope must still stay hidden.
     */
    public static Set<String> visible(ProcessorTopology topology, boolean showScaffolding,
                                      Collection<String> focused) {
        Set<String> out = new LinkedHashSet<>();
        if (topology == null) return out;
        Set<String> allowed = showScaffolding ? null : Scaffolding.authoredNodes(topology);
        for (String id : topology.ids()) {
            if (allowed != null && !allowed.contains(id)) continue;
            if (focused != null && !focused.contains(id)) continue;
            out.add(id);
        }
        return out;
    }
}
