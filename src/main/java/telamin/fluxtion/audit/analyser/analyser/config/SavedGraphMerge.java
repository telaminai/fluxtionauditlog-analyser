package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles the charts a person has OPEN with the charts the project has SAVED (M68.3, M68.5).
 *
 * <p>This is the single rule standing between a user and silent chart loss, so it lives here as a pure
 * function rather than inside {@code MainFrame}. It was written there first, where nothing could reach it:
 * {@code MainFrame} is not headless-constructible, so the only way to "test" it was to transcribe it into
 * a test and check the copy — which proves the copy. Reverting the real method to its destructive form
 * left the whole suite green. A rule this load-bearing has to be callable by a test.
 *
 * <p>The rule: an open tab is live state and wins; a saved chart that is no longer a tab is KEPT and
 * marked closed, never dropped; a tab that is new is appended. Order follows the saved list so charts do
 * not shuffle on every save. Deletion is not expressed here — it is an explicit act that removes the
 * definition before this runs.
 */
public final class SavedGraphMerge {

    private SavedGraphMerge() {
    }

    /**
     * @param saved the project's chart definitions, in profile order (open and closed)
     * @param openTabs the charts currently open, as the tabs report them
     * @return the definitions to persist
     */
    public static List<GraphSpec> merge(List<GraphSpec> saved, List<GraphSpec> openTabs) {
        Map<String, GraphSpec> open = new LinkedHashMap<>();
        if (openTabs != null) {
            for (GraphSpec g : openTabs) if (g != null && g.name() != null) open.put(g.name(), g);
        }

        List<GraphSpec> merged = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        if (saved != null) {
            for (GraphSpec existing : saved) {
                if (existing == null || existing.name() == null || !placed.add(existing.name())) {
                    continue;   // a duplicate name in the profile would otherwise be merged twice
                }
                GraphSpec live = open.get(existing.name());
                merged.add(live != null ? live.withOpen(true) : existing.withOpen(false));
            }
        }
        for (GraphSpec g : open.values()) {
            if (placed.add(g.name())) merged.add(g.withOpen(true));
        }
        return merged;
    }
}
