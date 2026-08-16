package telamin.fluxtion.audit.analyser.analyser.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a fully-qualified class name to a {@code .java} file under one of the configured source
 * roots (spec §9). A root is a source directory such as {@code market-maker-lib/src/main/java}.
 */
public final class SourceRootResolver {

    private final List<Path> roots;

    public SourceRootResolver(List<String> roots) {
        this.roots = new ArrayList<>();
        if (roots != null) for (String r : roots) if (r != null && !r.isBlank()) this.roots.add(Path.of(r));
    }

    public List<Path> roots() {
        return roots;
    }

    /**
     * First existing {@code <root>/<pkg-as-path>/<Simple>.java} for the FQN, falling back to the
     * <b>enclosing</b> file for a nested class.
     *
     * <p>{@code com.acme.node.Nodes.QuotePublisher} lives in {@code Nodes.java}, not
     * {@code Nodes/QuotePublisher.java}. Nested node classes are common — the golden path's own demo
     * groups its nodes inside one holder — so without this, source navigation fails on exactly the shape
     * the framework's examples teach. Each trailing capitalised segment is dropped in turn, which stops
     * at the package (lower-case by convention) rather than walking off the top.
     */
    public Optional<Path> find(String fqn) {
        if (fqn == null || fqn.isBlank()) return Optional.empty();
        String name = fqn;
        while (true) {
            Optional<Path> hit = findExact(name);
            if (hit.isPresent()) return hit;
            int dot = name.lastIndexOf('.');
            if (dot < 0) return Optional.empty();
            String enclosing = name.substring(0, dot);
            int prev = enclosing.lastIndexOf('.');
            String last = prev < 0 ? enclosing : enclosing.substring(prev + 1);
            // only a type can enclose a type; a lower-case segment is the package, so stop
            if (last.isEmpty() || !Character.isUpperCase(last.charAt(0))) return Optional.empty();
            name = enclosing;
        }
    }

    private Optional<Path> findExact(String fqn) {
        String rel = fqn.replace('.', '/') + ".java";
        for (Path root : roots) {
            Path candidate = root.resolve(rel);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Reads the source for an FQN if present. */
    public Optional<String> read(String fqn) {
        return find(fqn).map(SourceRootResolver::readFile);
    }

    static String readFile(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
