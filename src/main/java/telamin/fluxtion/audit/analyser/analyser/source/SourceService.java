package telamin.fluxtion.audit.analyser.analyser.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Facade over source roots + the selected EventProcessor (spec §9): discovers candidate processors,
 * infers the best match for a log, resolves node instanceIds to source files, and caches the parsed
 * model of the selected processor.
 */
public final class SourceService {

    private SourceRootResolver resolver = new SourceRootResolver(List.of());
    private MavenSourceResolver maven = new MavenSourceResolver(List.of(), false);
    private String selectedFqn;
    private EventProcessorModel selectedModel;

    public void configure(List<String> roots, String selectedFqn) {
        configure(roots, selectedFqn, List.of(), false);
    }

    /** Full configuration: source roots + the maven repos searched for {@code *-sources.jar} fallbacks. */
    public void configure(List<String> roots, String selectedFqn, List<String> mavenRepos, boolean searchMaven) {
        this.resolver = new SourceRootResolver(roots);
        this.maven = new MavenSourceResolver(mavenRepos, searchMaven);
        this.selectedFqn = selectedFqn;
        this.selectedModel = null;   // invalidate cache
    }

    /** Pre-index the maven repos (a filesystem walk) — call off-EDT so first lookups don't stall the UI. */
    public void warmMavenIndex() {
        maven.warm();
    }

    public SourceRootResolver resolver() {
        return resolver;
    }

    public String selectedFqn() {
        return selectedFqn;
    }

    public void select(String fqn) {
        if (!java.util.Objects.equals(fqn, selectedFqn)) {
            this.selectedFqn = fqn;
            this.selectedModel = null;
        }
    }

    /** The parsed model of the selected processor, or empty if its source can't be found. */
    public Optional<EventProcessorModel> selectedModel() {
        if (selectedModel != null) return Optional.of(selectedModel);
        if (selectedFqn == null) return Optional.empty();
        Optional<String> src = sourceForFqn(selectedFqn);
        selectedModel = src.map(s -> EventProcessorModel.parse(selectedFqn, s)).orElse(null);
        return Optional.ofNullable(selectedModel);
    }

    /** Source for an FQN: the source roots first, then the maven-repo {@code *-sources.jar} fallback. */
    public Optional<String> sourceForFqn(String fqn) {
        Optional<String> fromRoots = resolver.read(fqn);
        return fromRoots.isPresent() ? fromRoots : maven.read(fqn);
    }

    /** FQN of the declared type of a node instanceId in the selected processor, or null. */
    public String fqnForInstance(String instanceId) {
        return selectedModel().map(m -> m.fieldTypeFqn(instanceId)).orElse(null);
    }

    /** Lists candidate EventProcessor FQNs by scanning a package directory under each root. */
    public static List<String> discover(SourceRootResolver resolver, String packageName) {
        Set<String> out = new TreeSet<>();
        String rel = packageName.replace('.', '/');
        for (Path root : resolver.roots()) {
            Path dir = root.resolve(rel);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".java"))
                        .map(p -> p.getFileName().toString())
                        .filter(n -> !n.equals("package-info.java"))
                        .forEach(n -> out.add(packageName + "." + n.substring(0, n.length() - ".java".length())));
            } catch (IOException ignore) {
                // skip unreadable roots
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Infers the best-matching processor by scoring each candidate's field set against the log's
     * observed node instanceIds (spec §9). Ties / no coverage fall back to {@code fallback}.
     */
    public static String infer(SourceRootResolver resolver, Collection<String> candidateFqns,
                               Set<String> observedInstanceIds, String fallback) {
        String best = fallback;
        int bestScore = 0;
        for (String fqn : candidateFqns) {
            Optional<String> src = resolver.read(fqn);
            if (src.isEmpty()) continue;
            int score = EventProcessorModel.parse(fqn, src.get()).coverage(observedInstanceIds);
            if (score > bestScore) {
                bestScore = score;
                best = fqn;
            }
        }
        return best;
    }
}
