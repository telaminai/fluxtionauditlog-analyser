package telamin.fluxtion.audit.analyser.analyser.topology;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Find the {@code .graphml} files under the configured source roots, and rank them against the open
 * log (M35.4).
 *
 * <p><b>It offers. It never selects.</b> That restraint is the whole slice, and it is the one most
 * easily lost by being helpful: a graph auto-picked from a directory listing is a graph nobody chose,
 * and the moment it is wrong the analyser is confidently describing a system it is not looking at —
 * the exact failure M35 exists to prevent, arriving through the front door of a convenience feature.
 * So this returns a ranked list with every candidate's numbers, and the caller decides. When the best
 * candidate is a perfect fit it is still only <em>first</em>, never loaded.
 *
 * <p>The ranking reuses {@link GraphPairing}, which reuses {@link NodeCoverage} — one comparison for
 * "does this graph describe this log", used by M35.2's re-pair, M35.3's switch, and here. A second
 * scorer would be a second answer to one question.
 *
 * <p>Bounded on purpose: source trees contain build output, and an unbounded walk over a monorepo is
 * a hang. Depth, file count and per-file size are all capped, and a truncated scan says so rather
 * than presenting a partial list as the whole answer.
 */
public final class GraphmlDiscovery {

    /** Directory depth to descend from each root. */
    public static final int MAX_DEPTH = 12;

    /** Most candidates to return; beyond this the result is marked truncated. */
    public static final int MAX_CANDIDATES = 25;

    /** A .graphml larger than this is not one of ours — the AOT output for 300 nodes is ~1 MB. */
    public static final long MAX_BYTES = 32L * 1024 * 1024;

    private GraphmlDiscovery() {
    }

    /**
     * One candidate and how well it fits.
     *
     * @param file    the .graphml
     * @param nodes   how many authored nodes it declares, or 0 when it did not parse
     * @param pairing its verdict against the log, or null when there is no log to judge against
     */
    public record Candidate(Path file, int nodes, GraphPairing pairing) {

        /** Best first: fits, then how much of the log it explains, then the bigger graph. */
        static final Comparator<Candidate> RANK = Comparator
                .comparing((Candidate c) -> c.pairing != null && c.pairing.applies()).reversed()
                .thenComparing(c -> c.pairing == null ? 0 : -c.pairing.matched())
                .thenComparing(c -> -c.nodes)
                .thenComparing(c -> c.file.toString());

        public String describe() {
            String base = file.getFileName() + " · " + nodes + " node(s)";
            return pairing == null ? base : base + " · " + pairing.reason();
        }
    }

    /** The ranked candidates, plus whether the scan saw everything. */
    public record Result(List<Candidate> candidates, boolean truncated, List<String> notes) {
        public Result {
            candidates = List.copyOf(candidates);
            notes = List.copyOf(notes);
        }
    }

    /**
     * @param roots      configured source roots
     * @param loggedIds  instanceIds the open log writes, or empty/null when no log is open — with no
     *                   log the candidates are listed but NOT ranked by fit, because there is nothing
     *                   to fit and inventing an order would be a recommendation nobody earned
     */
    public static Result scan(List<String> roots, Set<String> loggedIds) {
        List<Candidate> out = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        boolean truncated = false;

        for (String root : roots == null ? List.<String>of() : roots) {
            Path dir;
            try {
                dir = Path.of(root);
            } catch (RuntimeException e) {
                notes.add("skipped '" + root + "': not a path");
                continue;
            }
            if (!Files.isDirectory(dir)) {
                notes.add("skipped '" + root + "': not a directory");
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir, MAX_DEPTH)) {
                for (Path p : walk.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".graphml")).toList()) {
                    if (!seen.add(p.toAbsolutePath().normalize())) continue;
                    if (out.size() >= MAX_CANDIDATES) {
                        truncated = true;
                        break;
                    }
                    out.add(judge(p, loggedIds, notes));
                }
            } catch (IOException | RuntimeException e) {
                notes.add("could not scan '" + root + "': " + e.getMessage());
            }
        }
        if (truncated) {
            notes.add("more than " + MAX_CANDIDATES + " .graphml files found — the list is the first "
                    + MAX_CANDIDATES + ", not a ranking of everything");
        }
        out.sort(Candidate.RANK);
        return new Result(out, truncated, notes);
    }

    private static Candidate judge(Path file, Set<String> loggedIds, List<String> notes) {
        try {
            if (Files.size(file) > MAX_BYTES) {
                notes.add(file.getFileName() + ": larger than " + (MAX_BYTES / 1024 / 1024)
                        + " MB — not read");
                return new Candidate(file, 0, null);
            }
            ProcessorTopology topology = GraphMlParser.parse(file);
            if (topology.isEmpty()) {
                // the parser returns an empty topology rather than throwing, so a file that is not
                // a graph at all would otherwise be offered as "0 node(s)" with no reason — a
                // candidate the caller cannot tell from a real but empty processor
                notes.add(file.getFileName() + ": did not parse as a Fluxtion .graphml");
                return new Candidate(file, 0, null);
            }
            // the same authored-vs-scaffolding split coverage uses — a graph is judged
            // on the nodes someone wrote, not on the dispatcher plumbing
            Set<String> declared = Scaffolding.authoredNodes(topology);
            GraphPairing pairing = loggedIds == null || loggedIds.isEmpty()
                    ? null : GraphPairing.of(declared, loggedIds);
            return new Candidate(file, declared.size(), pairing);
        } catch (RuntimeException | IOException e) {
            notes.add(file.getFileName() + ": did not parse as a Fluxtion .graphml");
            return new Candidate(file, 0, null);
        }
    }
}
