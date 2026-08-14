package telamin.fluxtion.audit.analyser.analyser.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fallback source lookup over local Maven repositories: when an FQN has no {@code .java} under the
 * configured source roots, search the repos' {@code *-sources.jar} files for it. Disabled by the
 * "don't search local repos" setting.
 *
 * <p>Cost model: the jar list is discovered once per session (a filesystem walk, lazy, then cached);
 * a lookup opens jar central directories until a hit. Jars whose repo-relative group path shares the
 * FQN's leading package segments are tried first (group ids usually prefix their packages), so the
 * common case touches a handful of jars. Both hits and misses are cached per FQN.
 */
public final class MavenSourceResolver {

    private final List<Path> repos;
    private final boolean enabled;
    private volatile List<Path> jars;                                       // lazy; null until first use
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public MavenSourceResolver(List<String> repos, boolean enabled) {
        this.repos = new ArrayList<>();
        if (repos != null) {
            for (String r : repos) if (r != null && !r.isBlank()) this.repos.add(Path.of(r));
        }
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Force jar discovery now (call off-EDT) so the first user-facing lookup doesn't pay the walk. */
    public void warm() {
        if (enabled) jarList();
    }

    /** The source text for an FQN from the first {@code *-sources.jar} containing it, cached. */
    public Optional<String> read(String fqn) {
        if (!enabled || fqn == null || fqn.isBlank()) return Optional.empty();
        return cache.computeIfAbsent(fqn, this::lookup);
    }

    private Optional<String> lookup(String fqn) {
        String rel = fqn.replace('.', '/') + ".java";
        for (Path jar : orderedCandidates(fqn)) {
            try (ZipFile zf = new ZipFile(jar.toFile())) {
                ZipEntry e = zf.getEntry(rel);
                if (e != null) {
                    try (InputStream in = zf.getInputStream(e)) {
                        return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ignore) {
                // unreadable/corrupt jar — skip it
            }
        }
        return Optional.empty();
    }

    /** All sources jars, those whose group path matches the FQN's leading package segments first. */
    private List<Path> orderedCandidates(String fqn) {
        List<Path> all = jarList();
        String pkgPath = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')).replace('.', '/') : "";
        List<Path> preferred = new ArrayList<>();
        List<Path> rest = new ArrayList<>();
        for (Path jar : all) {
            (matchesGroupPrefix(jar, pkgPath) ? preferred : rest).add(jar);
        }
        preferred.addAll(rest);
        return preferred;
    }

    /** True when the jar's repo-relative group path (2+ leading segments) prefixes the package path. */
    private boolean matchesGroupPrefix(Path jar, String pkgPath) {
        if (pkgPath.isEmpty()) return false;
        for (Path repo : repos) {
            if (!jar.startsWith(repo)) continue;
            Path relPath = repo.relativize(jar);
            // <group…>/<artifact>/<version>/<x-sources.jar> → drop the last three segments
            int groupSegments = relPath.getNameCount() - 3;
            if (groupSegments < 1) return false;
            StringBuilder group = new StringBuilder();
            for (int i = 0; i < groupSegments; i++) {
                if (i > 0) group.append('/');
                group.append(relPath.getName(i));
            }
            String g = group.toString();
            // match on the first two segments (or all of a single-segment group)
            String[] gs = g.split("/");
            String prefix = gs.length >= 2 ? gs[0] + "/" + gs[1] : gs[0];
            return pkgPath.equals(prefix) || pkgPath.startsWith(prefix + "/");
        }
        return false;
    }

    private List<Path> jarList() {
        List<Path> j = jars;
        if (j != null) return j;
        synchronized (this) {
            if (jars == null) jars = discoverJars();
            return jars;
        }
    }

    private List<Path> discoverJars() {
        List<Path> out = new ArrayList<>();
        for (Path repo : repos) {
            if (!Files.isDirectory(repo)) continue;
            try (Stream<Path> walk = Files.walk(repo)) {
                walk.filter(p -> p.getFileName().toString().endsWith("-sources.jar"))
                        .filter(Files::isRegularFile)
                        .forEach(out::add);
            } catch (IOException ignore) {
                // unreadable repo — search what we found
            }
        }
        return out;
    }
}
