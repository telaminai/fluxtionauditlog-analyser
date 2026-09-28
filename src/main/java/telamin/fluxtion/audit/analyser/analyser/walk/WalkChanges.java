package telamin.fluxtion.audit.analyser.analyser.walk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review PR57 R6, second round — what changed in a list of walks, as facts an adapter can report.
 *
 * <p>The verb reports its own mutations one at a time, because it knows which one it made. The paths that replace
 * walks in BULK cannot: a Settings import replaces by name inside the reports category, and a project transition
 * clears the list and refills it from the incoming profile. Neither knew what it had changed, so neither told the
 * session, and a walk being shown from a definition that had just been replaced or removed carried on as though
 * nothing had happened — the scattered decision {@code WalkDefinitionChanged} exists to remove, in the two places
 * the first round did not look.
 *
 * <p>Pure, and it decides nothing: a rename is indistinguishable from a delete and an add when all you have is two
 * lists, so it is reported as both, and what either means for a showing walk stays the node's decision.
 */
public final class WalkChanges {

    private WalkChanges() {
    }

    /**
     * One walk that is not what it was: {@code now} is its new definition, or null when it is gone.
     */
    public record Change(String name, WalkSpec now) {
    }

    /**
     * Every name whose definition differs between {@code before} and {@code after}, in a stable order: the ones that
     * changed or were added first, in {@code after}'s order, then the ones that are gone, in {@code before}'s.
     *
     * <p>A walk present in both with an equal definition is not a change and is not reported — an import that
     * re-states what was already there must not end a walk that is showing, exactly as an unchanged save does not.
     */
    public static List<Change> between(List<WalkSpec> before, List<WalkSpec> after) {
        Map<String, WalkSpec> was = byName(before);
        Map<String, WalkSpec> is = byName(after);
        List<Change> changes = new ArrayList<>();
        is.forEach((name, now) -> {
            if (!now.equals(was.get(name))) changes.add(new Change(name, now));
        });
        was.forEach((name, gone) -> {
            if (!is.containsKey(name)) changes.add(new Change(name, null));
        });
        return List.copyOf(changes);
    }

    private static Map<String, WalkSpec> byName(List<WalkSpec> walks) {
        Map<String, WalkSpec> out = new LinkedHashMap<>();
        if (walks != null) for (WalkSpec w : walks) if (w != null) out.put(w.name(), w);
        return out;
    }
}
