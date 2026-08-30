package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AX1c — the agreed reference set is STRUCTURALLY sound, offline.
 *
 * <h2>What this can and cannot see</h2>
 * Reachability is checked by {@code tools/bench/reference-set-bench.py} and deliberately not here: a
 * network fetch in {@code mvn test} makes the build flake on someone else's outage. So this test proves
 * the file is well-formed and self-consistent, and <b>proves nothing about whether the URLs resolve</b> —
 * which is exactly the blind-spot family this project keeps rediscovering, written down rather than
 * assumed.
 */
class ReferenceSetTest {

    private static final Path FILE = Path.of("src/main/resources/reference-set.json");
    private static final Set<String> STATUSES = Set.of("proposed", "agreed", "excluded");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resources() throws IOException {
        Map<String, Object> root = (Map<String, Object>) Json.parse(Files.readString(FILE));
        return (List<Map<String, Object>>) root.get("resources");
    }

    @Test
    void everyEntryCarriesWhatAReaderAndTheBenchBothNeed() throws IOException {
        List<Map<String, Object>> resources = resources();
        assertTrue(resources.size() >= 4, "found only " + resources.size()
                + " — has the set moved? A checker that checks nothing passes for the wrong reason.");

        List<String> problems = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> r : resources) {
            String id = String.valueOf(r.get("id"));
            if (!ids.add(id)) problems.add(id + ": duplicate id");
            Object url = r.get("url");
            if (!(url instanceof String u) || !u.startsWith("https://")) {
                problems.add(id + ": url must be https — a set member is fetched by whoever reads it");
            }
            // the one-line reason is not decoration: D-AX1c requires it, because a bare list of five
            // links gets skipped and the point is that a reader knows WHY to open one
            Object why = r.get("why");
            if (!(why instanceof String w) || w.isBlank()) problems.add(id + ": missing 'why'");
            if (!STATUSES.contains(String.valueOf(r.get("status")))) {
                problems.add(id + ": status must be one of " + STATUSES);
            }
        }
        assertTrue(problems.isEmpty(), "reference-set.json:\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyExclusionSaysWHY_soItCannotOutliveItsReason() throws IOException {
        for (Map<String, Object> r : resources()) {
            if (!"excluded".equals(r.get("status"))) continue;
            Object note = r.get("note");
            assertTrue(note instanceof String s && !s.isBlank(),
                    r.get("id") + " is excluded with no reason. An unexplained exclusion is indistinguishable"
                            + " from an oversight, and will be silently reinstated by the next reader.");
        }
    }

    @Test
    void nothingIsAGREEDwhileTheSignOffIsStillOpen() throws IOException {
        // D-AX1c is an owner decision. This test does NOT assert the set stays empty forever — it asserts
        // that when entries flip to 'agreed', the spec's open question has been answered, and whoever
        // flips them has to come here and delete this test on purpose rather than by accident.
        long agreed = resources().stream().filter(r -> "agreed".equals(r.get("status"))).count();
        if (agreed == 0) return;
        String spec = Files.readString(Path.of("docs/specs/spec-authoring-experience.md"));
        assertFalse(spec.contains("**Owner decision needed: which files are in the set.**"),
                "entries are marked 'agreed' but spec-authoring-experience.md still records the decision as"
                        + " open. Resolve D-AX1c in the spec in the same change.");
    }

    @Test
    void theBenchReadsTHISfileRatherThanItsOwnCopy() throws IOException {
        String bench = Files.readString(Path.of("tools/bench/reference-set-bench.py"));
        assertTrue(bench.contains("reference-set.json"),
                "the bench must read docs/reference-set.json — a second hardcoded list is how the set and"
                        + " its checker drift apart, which is the exact failure D-AX1c exists to prevent");
        assertFalse(bench.contains("fluxtion-playground.dev") || bench.contains("githubusercontent"),
                "the bench hardcodes a set URL — a second list is how the set and its checker drift apart,"
                        + " which is the exact failure D-AX1c exists to prevent");
    }
}
