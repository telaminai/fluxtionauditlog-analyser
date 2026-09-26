package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolving duplicate chart names, as a pure function (owner decision, 2026-09-24).
 *
 * <p>When a project or the global config holds two charts under one name, the analyser withholds both and
 * refuses to guess. That is correct, but it left the person with no way out except closing the app and
 * editing a file by hand. This is the policy half of the in-app repair: which definitions are ambiguous,
 * what each one CONTAINS, and what a set of choices does to the saved list.
 *
 * <p>It lives here, separate from the dialog, for the reason this whole round of work keeps relearning:
 * logic inside a Swing surface cannot be tested, and the one previous attempt to protect a rule of this
 * kind was reverted to its broken form with the entire suite still green.
 *
 * <p><b>Nothing is chosen by default.</b> {@link #apply} refuses a set of choices that does not resolve
 * every ambiguous definition, so a caller cannot half-repair a profile, and a dialog cannot ship a
 * silent winner by forgetting a case.
 */
public final class DuplicateChartRepair {

    private DuplicateChartRepair() {
    }

    /** What to do with one ambiguous definition. */
    public enum Action {
        /** Remove this definition and everything on it. Unrecoverable. */
        DELETE,
        /** Keep it under a new, unused name. */
        RENAME
    }

    /**
     * A choice for one ambiguous definition, identified by its index in the saved list.
     *
     * @param action what to do
     * @param newName the replacement name; required for {@link Action#RENAME}, ignored for DELETE
     */
    public record Choice(Action action, String newName) {
    }

    /**
     * What a dialog row is showing, as an index into the three choices a person can pick from.
     *
     * <p>R13-5: this mapping used to live inside the dialog, where nothing could reach it. Mutating it so
     * an unanswered row meant DELETE left all 25 tests green, because every frame test injects the chooser
     * and the combo is never read. The refusal of a partial repair is worthless if the dialog quietly
     * fills the gap, so the step that turns a row into a choice belongs out here with the rest of the
     * policy.
     */
    public static final int UNANSWERED = 0, RENAME_SELECTED = 1, DELETE_SELECTED = 2;

    /**
     * The choice a row represents, or null when the person has not answered it.
     *
     * <p>Null is the point. It reaches {@link #apply} as a missing entry, which refuses the whole repair
     * and names the row — rather than a default that silently destroys or renames something.
     */
    public static Choice choiceFor(int selectedIndex, String typedName) {
        return switch (selectedIndex) {
            case RENAME_SELECTED -> new Choice(Action.RENAME, typedName);
            case DELETE_SELECTED -> new Choice(Action.DELETE, null);
            default -> null;
        };
    }

    /** One contested name and the definitions competing for it, in saved order. */
    public record Duplicate(String name, List<Integer> indices) {
        public Duplicate {
            indices = List.copyOf(indices);
        }
    }

    /** Every name held by more than one definition, in the order the names first appear. */
    public static List<Duplicate> find(List<GraphSpec> saved) {
        Map<String, List<Integer>> byName = new LinkedHashMap<>();
        if (saved != null) {
            for (int i = 0; i < saved.size(); i++) {
                GraphSpec g = saved.get(i);
                if (g != null && g.name() != null) byName.computeIfAbsent(g.name(), k -> new ArrayList<>()).add(i);
            }
        }
        List<Duplicate> out = new ArrayList<>();
        byName.forEach((name, indices) -> {
            if (indices.size() > 1) out.add(new Duplicate(name, indices));
        });
        return out;
    }

    /**
     * A one-line summary of what a definition holds, for a person deciding whether to destroy it.
     *
     * <p>A confirmation that does not say what is being lost is not a confirmation — the lesson of the
     * chart-loss defects this repair exists to clean up after.
     */
    public static String describe(GraphSpec g) {
        if (g == null) return "(missing)";
        List<String> parts = new ArrayList<>();
        int series = (g.series() == null ? 0 : g.series().size()) + g.exprs().size();
        parts.add(series + (series == 1 ? " series" : " series"));
        if (!g.notes().isEmpty()) parts.add(g.notes().size() + (g.notes().size() == 1 ? " note" : " notes"));
        if (!g.explanation().isBlank()) parts.add("an explanation");
        if (!g.markers().isEmpty()) parts.add(g.markers().size() + " markers");
        if (!g.rightAxis().isEmpty()) parts.add("a right axis");
        if (g.isPinned()) parts.add("a pinned window");
        parts.add(g.open() ? "open" : "closed");
        return String.join(", ", parts);
    }

    /**
     * Apply a complete set of choices, returning the repaired list. The input is never modified.
     *
     * @throws IllegalArgumentException if any ambiguous definition has no choice, a rename is blank or
     *         collides with a name that survives, or the result would still be ambiguous
     */
    public static List<GraphSpec> apply(List<GraphSpec> saved, Map<Integer, Choice> choices) {
        return apply(saved, choices, Set.of());
    }

    /**
     * As above, with names that are taken by something this list cannot see — open tabs that have no saved
     * definition yet, because nothing is persisted while definitions are withheld.
     *
     * <p>R13-2b: without this, renaming a duplicate onto an unsaved chart's name produced a repaired list
     * holding that name, the caller's carry-forward skipped the live tab as "already present", and the
     * person's unsaved work was destroyed by an operation they asked to be a rename.
     */
    public static List<GraphSpec> apply(List<GraphSpec> saved, Map<Integer, Choice> choices,
                                        Set<String> alsoTaken) {
        List<Duplicate> duplicates = find(saved);
        Set<Integer> contested = new HashSet<>();
        for (Duplicate d : duplicates) contested.addAll(d.indices());
        if (contested.isEmpty()) throw new IllegalArgumentException("nothing is ambiguous; there is nothing to repair");

        Map<Integer, Choice> given = choices == null ? Map.of() : choices;
        List<Integer> unresolved = contested.stream().filter(i -> given.get(i) == null).sorted().toList();
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException("every ambiguous chart needs a choice; missing for index(es) "
                    + unresolved + " — a partial repair would leave the project ambiguous");
        }
        if (!contested.containsAll(given.keySet())) {
            throw new IllegalArgumentException("a choice was given for a chart that is not ambiguous: "
                    + given.keySet().stream().filter(i -> !contested.contains(i)).sorted().toList());
        }

        List<GraphSpec> out = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            Choice choice = given.get(i);
            if (choice == null) {
                out.add(saved.get(i));                       // never contested, carried through untouched
                continue;
            }
            if (choice.action() == Action.DELETE) continue;   // the person asked for this one to go
            String to = choice.newName() == null ? "" : choice.newName().trim();
            if (to.isEmpty()) throw new IllegalArgumentException("a renamed chart needs a name (index " + i + ")");
            // M68.6 (D-E5): the repair is a naming entrance too — it renames saved definitions directly, past the UI's
            // rename — so it applies the same rule, or it would recreate the unaddressable names M68.6 refuses
            String problem = ChartNames.problem(to);
            if (problem != null) throw new IllegalArgumentException("'" + to + "' cannot be used: " + problem);
            out.add(saved.get(i).withName(to));
        }

        Set<String> seen = new HashSet<>();
        for (GraphSpec g : out) {
            if (!seen.add(g.name())) {
                throw new IllegalArgumentException("that would leave two charts called '" + g.name()
                        + "' — choose a different name");
            }
        }
        // R13-2b: a name held by an open, unsaved chart is just as taken as one in the list, and losing
        // that chart to a rename is worse than being told to pick another name.
        if (alsoTaken != null) {
            for (int i = 0; i < saved.size(); i++) {
                Choice choice = given.get(i);
                if (choice == null || choice.action() != Action.RENAME) continue;
                String to = choice.newName().trim();
                if (alsoTaken.contains(to)) {
                    throw new IllegalArgumentException("an unsaved chart is already called '" + to
                            + "' — renaming onto it would destroy it; choose a different name");
                }
            }
        }
        return out;
    }
}
