package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.Runbooks;
import telamin.fluxtion.audit.analyser.analyser.config.SkillDiscovery;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M19.13's day-two offer: facts already present in a chosen project directory, with no selection.
 *
 * <p>Discovery and adoption are separate types because that is the safety property. {@link #discover}
 * can walk and describe; it cannot mutate. A {@link Selection} begins empty and only the confirmation
 * dialog can populate it. {@link #apply} accepts that explicit selection and nothing else.
 */
public final class NewProjectDiscovery {

    private NewProjectDiscovery() {
    }

    public record Offer(Path root, List<Path> sourceRoots, SkillDiscovery.Found skills,
                        GraphmlDiscovery.Result graphs) {
        public Offer {
            sourceRoots = List.copyOf(sourceRoots);
        }

        public boolean empty() {
            return sourceRoots.isEmpty() && skills.candidates().isEmpty() && graphs.candidates().isEmpty();
        }
    }

    public record Selection(Set<Path> sourceRoots, Set<String> skillPaths, Path graph) {
        public Selection {
            sourceRoots = Set.copyOf(sourceRoots == null ? Set.of() : sourceRoots);
            skillPaths = Set.copyOf(skillPaths == null ? Set.of() : skillPaths);
        }

        public static Selection empty() {
            return new Selection(Set.of(), Set.of(), null);
        }
    }

    /** An empty or unreadable directory is an empty offer, never an error. */
    public static Offer discover(Path root) {
        Path normalized = root == null ? null : root.toAbsolutePath().normalize();
        List<Path> sourceRoots = ConfigPanel.detectSourceRoots(normalized);
        SkillDiscovery.Found skills = SkillDiscovery.find(normalized, Map.of());
        List<String> graphRoots = new ArrayList<>(sourceRoots.stream().map(Path::toString).toList());
        // AOT GraphML is a Maven resource, not Java source. The playground's M19 bundle puts the
        // committed graph here; scanning only detected src/main/java roots made day-two discovery
        // miss the exact bundle shape it is meant to teach users to reproduce.
        if (normalized != null) {
            Path resources = normalized.resolve("src/main/resources");
            if (Files.isDirectory(resources)) graphRoots.add(resources.toString());
        }
        GraphmlDiscovery.Result graphs = GraphmlDiscovery.scan(graphRoots, Set.of());
        return new Offer(normalized, sourceRoots, skills, graphs);
    }

    /** Apply only boxes a person checked. The graph remains a UI open, returned in the selection. */
    public static void apply(Offer offer, Selection selection, AppConfig config) {
        if (offer == null || selection == null || config == null) return;
        Set<Path> offeredRoots = new LinkedHashSet<>(offer.sourceRoots());
        for (Path selected : selection.sourceRoots()) {
            Path normalized = selected.toAbsolutePath().normalize();
            if (offeredRoots.contains(normalized) && !config.sourceRoots.contains(normalized.toString())) {
                config.sourceRoots.add(normalized.toString());
            }
        }
        for (SkillDiscovery.Candidate candidate : offer.skills().candidates()) {
            if (!selection.skillPaths().contains(candidate.path())) continue;
            if (candidate.declared()) continue;
            if (Runbooks.refuse(candidate.name(), candidate.path()).isPresent()
                    || Runbooks.refuseDescription(candidate.name(), candidate.description()).isPresent()) continue;
            // Duplicate declared names are not silently replaced; the first explicit offered choice wins.
            config.runbooks.putIfAbsent(candidate.name(),
                    new Runbooks.Pointer(candidate.path(), candidate.description()));
        }
    }
}
