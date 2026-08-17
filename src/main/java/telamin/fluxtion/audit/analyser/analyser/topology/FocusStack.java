package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Focus as a <b>filter context</b>, not a view toggle (M27, owner model correction 2026-08-17).
 *
 * <p>Applying focus pushes a {@link Context} — an induced subgraph — and that context <i>becomes the
 * whole graph</i> for every subsequent operation: scope-cycling, counts, layout, "all". Contexts nest
 * (each push intersects with the current world, so drilling down can only narrow). Clearing selection
 * or dimming never touches the stack — filter and dimming must never share an exit gesture; leaving a
 * context is explicit: {@link #pop()} (Esc) or {@link #popToFull()} (Show all).
 *
 * <p>The mental model is the records table transplanted: the shared filter narrows the record universe
 * and search/selection operate within it. Same here, for nodes.
 *
 * <p><b>Boundary honesty</b>: execution shading is computed on the full graph and displayed within the
 * context; {@link #outsideWorld(Collection)} reports what a cycle touched beyond the context so the
 * view can say "n nodes outside this view ran" rather than silently cropping a propagation.
 *
 * <p>Pure and headless — the canvas renders what this class answers.
 */
public final class FocusStack {

    /** One filter level: the node ids it admits and how it was derived (for the breadcrumb/picker). */
    public record Context(String label, Set<String> ids) {
        public Context {
            ids = Set.copyOf(ids);
        }
    }

    private final ProcessorTopology topology;
    private final Deque<Context> stack = new ArrayDeque<>();

    public FocusStack(ProcessorTopology topology) {
        this.topology = topology == null ? ProcessorTopology.empty() : topology;
    }

    // ---- the world -------------------------------------------------------------------------------

    /** The node ids of the current context — the "whole graph" as far as the UI is concerned. */
    public Set<String> world() {
        return stack.isEmpty() ? Set.copyOf(topology.ids()) : stack.peek().ids();
    }

    /** True when no context is applied — the world is the full topology. */
    public boolean atFull() {
        return stack.isEmpty();
    }

    public int depth() {
        return stack.size();
    }

    // ---- entering and leaving contexts -----------------------------------------------------------

    /**
     * Push a context of {@code ids} (intersected with the current world — nesting only narrows;
     * an id outside the current world cannot be smuggled in by a deeper focus). An empty or
     * fully-out-of-world set is refused: a world with nothing in it is never what anyone meant.
     *
     * @return true if pushed
     */
    public boolean push(Collection<String> ids, String label) {
        if (ids == null) return false;
        Set<String> world = world();
        Set<String> kept = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && world.contains(id)) kept.add(id);
        }
        if (kept.isEmpty()) return false;
        stack.push(new Context(label == null || label.isBlank() ? kept.size() + " nodes" : label, kept));
        return true;
    }

    /** Leave the top context (Esc). @return true if a level was popped. */
    public boolean pop() {
        if (stack.isEmpty()) return false;
        stack.pop();
        return true;
    }

    /** Leave every context (Show all) — back to the full graph. */
    public void popToFull() {
        stack.clear();
    }

    /** Pop until {@code depth} levels remain (breadcrumb click). No-op if already at or below it. */
    public void popTo(int depth) {
        while (stack.size() > Math.max(0, depth)) stack.pop();
    }

    // ---- context-relative operations -------------------------------------------------------------

    /**
     * {@link TopologyFocus#expand} confined to the current world: neighbours/routes cannot escape the
     * context, and {@link TopologyFocus.Scope#ALL} means <i>all of this context</i>, not the full graph.
     */
    public Set<String> expandInWorld(Collection<String> seeds, TopologyFocus.Scope scope) {
        Set<String> world = world();
        if (scope == TopologyFocus.Scope.ALL) return world;
        Set<String> raw = TopologyFocus.expand(topology, seeds, scope);
        // NEIGHBOURS is one hop and prunes cleanly; ROUTES must be recomputed with edges CONFINED to
        // the world — pruning the full-graph closure afterwards would keep nodes only reachable via
        // paths that leave and re-enter the context, which is exactly what "within this context" excludes
        if (scope == TopologyFocus.Scope.ROUTES) {
            raw = reachWithin(seeds, world);
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : raw) {
            if (world.contains(id)) out.add(id);
        }
        return out;
    }

    /** Transitive closure both ways, walking only edges whose BOTH ends are inside {@code world}. */
    private Set<String> reachWithin(Collection<String> seeds, Set<String> world) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String s : seeds) {
            if (s != null && world.contains(s) && topology.contains(s) && seen.add(s)) queue.add(s);
        }
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            for (String next : topology.parentsOf(id)) {
                if (world.contains(next) && seen.add(next)) queue.addLast(next);
            }
            for (String next : topology.childrenOf(id)) {
                if (world.contains(next) && seen.add(next)) queue.addLast(next);
            }
        }
        return seen;
    }

    /** The ids in {@code executed} that lie OUTSIDE the current world — the boundary-honesty count. */
    public Set<String> outsideWorld(Collection<String> executed) {
        Set<String> out = new LinkedHashSet<>();
        if (executed == null || atFull()) return out;
        Set<String> world = world();
        for (String id : executed) {
            if (id != null && !world.contains(id) && topology.contains(id)) out.add(id);
        }
        return out;
    }

    // ---- orientation ------------------------------------------------------------------------------

    /** {@code All (62) ▸ hedge path (12) ▸ neighbours of x (5)} — oldest first, sizes included. */
    public String breadcrumb() {
        StringBuilder sb = new StringBuilder("All (").append(topology.ids().size()).append(")");
        for (Context c : contextsOldestFirst()) {
            sb.append(" ▸ ").append(c.label()).append(" (").append(c.ids().size()).append(")");
        }
        return sb.toString();
    }

    /** The stack oldest-first (for breadcrumb rendering / click-to-pop). */
    public List<Context> contextsOldestFirst() {
        List<Context> list = new ArrayList<>(stack);
        java.util.Collections.reverse(list);
        return list;
    }
}
