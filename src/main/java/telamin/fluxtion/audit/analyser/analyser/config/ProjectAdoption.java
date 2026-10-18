package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M73 D-73.3 — the ONE mapping from an approved selection to configuration, for every surface that creates a
 * project: <i>Project ▸ New project…</i> and {@code open {project, create}} both reach it through the one effect
 * performer, so there is no second adoption to drift.
 *
 * <p>Moved from {@code ui.NewProjectDiscovery.apply}, with one change: it iterates the {@link Plan}'s order, which is
 * the OFFER's order, so a created profile is deterministic (G3 — the old code iterated a {@code Set.copyOf} of the
 * selection, whose order changes from run to run).
 *
 * <p>It adopts exactly what it is given. Whether an item may be adopted at all — is it in the offer, does it resolve
 * inside the project — is decided before this, by {@code sessionBoundary} (D-73.4). What it still refuses is what any
 * pointer refuses ({@link Runbooks#refuse}), and a skill whose name is already taken: the first choice wins, and each
 * one not adopted is REPORTED with its reason, so an echo never claims an adoption that did not happen.
 */
public final class ProjectAdoption {

    private ProjectAdoption() {
    }

    /** One skill pointer to adopt: the path is project-relative, the form a pointer is stored in. */
    public record Skill(String path, String name, String description, boolean declared) {
    }

    /**
     * What to adopt, in offer order.
     *
     * @param root        the project directory
     * @param sourceRoots absolute root strings
     */
    public record Plan(Path root, List<String> sourceRoots, List<Skill> skills) {
        public Plan {
            sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
            skills = skills == null ? List.of() : List.copyOf(skills);
        }

        public static Plan empty(Path root) {
            return new Plan(root, List.of(), List.of());
        }
    }

    /** A skill pointer that was adopted, with where it resolves. */
    public record Adopted(String path, String resolved, String description) {
    }

    /** A skill pointer that was chosen and NOT adopted, and why. */
    public record NotAdopted(String path, String why) {
    }

    /** What actually happened — the echo's source, never the request's. */
    public record Report(List<String> sourceRoots, List<Adopted> skills, List<NotAdopted> skillsNotAdopted) {
        public Report {
            sourceRoots = List.copyOf(sourceRoots);
            skills = List.copyOf(skills);
            skillsNotAdopted = List.copyOf(skillsNotAdopted);
        }
    }

    /** Adopt {@code plan} into {@code config}, in plan order. */
    public static Report apply(Plan plan, AppConfig config) {
        List<String> roots = new ArrayList<>();
        List<Adopted> adopted = new ArrayList<>();
        List<NotAdopted> notAdopted = new ArrayList<>();
        if (plan == null || config == null) return new Report(roots, adopted, notAdopted);
        for (String root : plan.sourceRoots()) {
            String normalized = Path.of(root).toAbsolutePath().normalize().toString();
            if (SourceRootPolicy.tier1(Path.of(normalized), SourceRootPolicy.home()) != null) continue;   // P5
            if (!config.sourceRoots.contains(normalized)) {
                config.sourceRoots.add(normalized);
                roots.add(normalized);
            }
        }
        for (Skill skill : plan.skills()) {
            if (skill.declared()) {
                notAdopted.add(new NotAdopted(skill.path(), "already declared"));
                continue;
            }
            var refused = Runbooks.refuse(skill.name(), skill.path())
                    .or(() -> Runbooks.refuseDescription(skill.name(), skill.description()));
            if (refused.isPresent()) {
                notAdopted.add(new NotAdopted(skill.path(), refused.get()));
                continue;
            }
            // Duplicate names are not silently replaced: the first explicit offered choice wins.
            if (config.runbooks.containsKey(skill.name())) {
                notAdopted.add(new NotAdopted(skill.path(), "duplicate name '" + skill.name() + "'"));
                continue;
            }
            config.runbooks.put(skill.name(), new Runbooks.Pointer(skill.path(), skill.description()));
            Path resolved = plan.root() == null ? null : plan.root().resolve(skill.path()).normalize();
            adopted.add(new Adopted(skill.path(), resolved == null ? null : resolved.toString(), skill.description()));
        }
        return new Report(roots, adopted, notAdopted);
    }
}
