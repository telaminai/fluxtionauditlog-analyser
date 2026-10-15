package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet;
import telamin.fluxtion.audit.analyser.analyser.config.SkillDiscovery;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * M19.13's day-two offer: facts already present in a chosen project directory, with no selection.
 *
 * <p>Discovery and adoption are separate types because that is the safety property. {@link #discover}
 * can walk and describe; it cannot mutate. A {@link Selection} begins empty and only the confirmation
 * dialog can populate it. Adoption is {@code config.ProjectAdoption}'s (M73 D-73.3), reached only through the
 * session's create effect, so the menu and the socket share one adoption.
 */
public final class NewProjectDiscovery {

    private NewProjectDiscovery() {
    }

    public record Offer(Path root, List<Path> sourceRoots, SkillDiscovery.Found skills,
                        GraphmlDiscovery.Result graphs, ReferenceSet.Outcome referenceGuide,
                        String projectKind) {
        public Offer {
            sourceRoots = List.copyOf(sourceRoots);
        }

        /**
         * Whether the PROJECT contained anything to adopt. Deliberately excludes the reference guide:
         * that is offered by the analyser, not discovered in the directory, and an empty directory is an
         * ordinary empty offer (M19.13). The dialog is shown either way.
         */
        public boolean empty() {
            return sourceRoots.isEmpty() && skills.candidates().isEmpty() && graphs.candidates().isEmpty();
        }
    }

    public record Selection(Set<Path> sourceRoots, Set<String> skillPaths, Path graph,
                            boolean createReferenceGuide) {
        public Selection {
            sourceRoots = Set.copyOf(sourceRoots == null ? Set.of() : sourceRoots);
            skillPaths = Set.copyOf(skillPaths == null ? Set.of() : skillPaths);
        }

        public Selection(Set<Path> sourceRoots, Set<String> skillPaths, Path graph) {
            this(sourceRoots, skillPaths, graph, false);
        }

        public static Selection empty() {
            return new Selection(Set.of(), Set.of(), null, false);
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
        return new Offer(normalized, sourceRoots, skills, graphs,
                ReferenceSet.offer(normalized), detectKind(normalized));
    }

    /**
     * D-AX1d — write the reference guide, and ONLY on an explicit selection.
     *
     * <p>Separate from adoption on purpose: adoption mutates configuration, this touches the
     * user's repository, and the analyser has never written documentation there before. Keeping the two
     * apart means a caller cannot reach the filesystem by accident, and the failure is returned rather
     * than thrown into a dialog.
     *
     * @return an error to show the user, or empty when nothing was written or the write succeeded.
     */
    public static Optional<String> writeReferenceGuide(Offer offer, Selection selection) {
        if (offer == null || selection == null || !selection.createReferenceGuide()) return Optional.empty();
        // No pre-check. The offer was computed before the dialog opened and is stale by now; checking it
        // here only creates a window in which the answer can change again. create() checks once and says
        // what it did, so the message is the truth rather than an inference about a boolean.
        try {
            return switch (ReferenceSet.create(offer.root(), offer.projectKind())) {
                case WROTE -> Optional.empty();
                case ALREADY_EXISTS ->
                        Optional.of(ReferenceSet.FILE_NAME + " already exists — left untouched.");
                case NOTHING_AGREED ->
                        Optional.of("nothing to write: no reference resources are agreed yet");
            };
        } catch (java.io.IOException e) {
            return Optional.of("could not write " + ReferenceSet.FILE_NAME + ": " + e.getMessage());
        }
    }

    /**
     * M73 — the same write, for a create the session approved: called by the effect performer ONLY after the
     * profile write succeeded, so a guide failure is reported and never rolls the create back [R r2-2].
     *
     * @return WROTE, EXISTS, or "FAILED: reason" — the echo's {@code referenceGuide} value
     */
    static String writeReferenceGuide(Path root, String projectKind) {
        try {
            return switch (ReferenceSet.create(root, projectKind)) {
                case WROTE -> "WROTE";
                case ALREADY_EXISTS -> "EXISTS";
                case NOTHING_AGREED -> "FAILED: nothing to write: no reference resources are agreed yet";
            };
        } catch (java.io.IOException | RuntimeException e) {
            return "FAILED: could not write " + ReferenceSet.FILE_NAME + ": " + e.getMessage();
        }
    }

    /**
     * Which reference links apply. Conservative by design: when unsure, return null and ship only the
     * always-on set. An omitted link is a smaller harm than an irrelevant one in a file that costs every
     * turn, and the guide explicitly invites project-specific content below it.
     */
    static String detectKind(Path root) {
        if (root == null) return null;
        for (String candidate : new String[]{
                "src/main/fluxtion/designer/application-context.xml",
                "src/main/resources/application-context.xml",
                "application-context.xml"}) {
            if (java.nio.file.Files.isRegularFile(root.resolve(candidate))) return "spring";
        }
        return null;
    }
}
