package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trailing whitespace is a defect in source and prose — and <b>format-faithful evidence</b> in a log
 * fixture.
 *
 * <p>The audit writer emits trailing spaces, the shipped reader is specified against them, and the
 * conformance corpus tests them. A reviewer flagged this before it could bite: <b>a blanket whitespace
 * gate would silently rewrite evidence</b> and could change what the conformance suite is asserting.
 * So the exclusions below are not tidiness — they are the point of the test, and removing one means
 * deciding to alter committed evidence.
 */
class TrailingWhitespaceTest {

    /**
     * Paths whose bytes are evidence. Never "cleaned".
     *
     * <p>The rule generalises: <b>an audit log is format-faithful wherever it lives</b> — the shipped
     * demo log, the conformance corpus, the committed experiment references. The writer emits trailing
     * spaces and the reader is specified against them, so stripping any of these changes what the
     * format tests assert. Generated sources and diffs are excluded for the same reason: their bytes
     * are produced by something else and are not ours to reformat.
     */
    private static final List<String> EVIDENCE = List.of(
            "src/test/resources/formula-golden/",
            "/generated/",
            ".diff");

    private static final List<String> TEXT = List.of(".java", ".md", ".py", ".sh", ".xml", ".yml", ".yaml", ".txt");

    @Test
    void noTrailingWhitespaceOutsideEvidenceFixtures() throws IOException {
        List<String> offenders = new ArrayList<>();
        // GIT-TRACKED files only. Walking the tree picks up .venv, target and anything else a
        // contributor happens to have on disk -- none of which this repo controls.
        List<String> tracked;
        try {
            Process git = new ProcessBuilder("git", "ls-files").redirectErrorStream(true).start();
            tracked = new java.io.BufferedReader(new java.io.InputStreamReader(git.getInputStream()))
                    .lines().toList();
        } catch (IOException noGit) {
            throw new AssertionError("git ls-files failed, so this gate would silently pass: " + noGit);
        }
        assertTrue(tracked.size() > 100,
                "git ls-files returned " + tracked.size() + " paths — the gate is not seeing the repo "
                + "and would pass vacuously");
        {
            for (String rel : tracked) {
                Path p = Path.of(rel);
                if (!Files.isRegularFile(p)) continue;
                String s = rel.replace('\\', '/');
                if (TEXT.stream().noneMatch(s::endsWith)) continue;
                if (EVIDENCE.stream().anyMatch(s::contains)) continue;
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                // CONTENT, not path: an audit log is format-faithful wherever it lives, and a
                // path list is whack-a-mole -- three separate directories held one before this
                // rule replaced them.
                // A LOG, not a file that MENTIONS logs. The earlier rule exempted any file with
                // "eventLogRecord:" in its first 40 lines, which silently excused Java sources,
                // the Python tools and the format documentation.
                if (isAuditLog(lines)) continue;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!line.isEmpty() && (line.endsWith(" ") || line.endsWith("\t"))) {
                        offenders.add(s + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "trailing whitespace outside evidence fixtures:\n  " + String.join("\n  ",
                        offenders.subList(0, Math.min(20, offenders.size()))));
    }

    /** A log is records, not prose about records: the FIRST content line opens one. */
    private static boolean isAuditLog(List<String> lines) {
        for (String l : lines) {
            String t = l.strip();
            if (t.isEmpty() || t.startsWith("#") || t.equals("---")) continue;
            return t.startsWith("eventLogRecord:");
        }
        return false;
    }

    /** Every byte-sensitive fixture, pinned. A missing one is a FAILURE, not a skip. */
    private static final List<String> BYTE_SENSITIVE = List.of(
            "docs/experience/runs/round-49/expected.txt",
            "docs/experience/runs/round-49/expected.conforming.txt",
            "docs/experience/runs/round-57/m48-11-real-audit.txt",
            "src/main/resources/demo/demo-quote-audit-traced.yaml",
            "src/test/resources/topology/demo-quote-audit-traced.yaml");

    @Test
    void everyByteSensitiveFixtureStillCarriesItsTrailingBytes() throws IOException {
        List<String> lost = new ArrayList<>();
        for (String rel : BYTE_SENSITIVE) {
            Path p = Path.of(rel);
            assertTrue(Files.exists(p), "pinned evidence fixture is MISSING: " + rel
                    + " — a silent skip here is how evidence disappears unnoticed");
            boolean any = Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                    .anyMatch(l -> !l.isEmpty() && (l.endsWith(" ") || l.endsWith("\t")));
            if (!any) lost.add(rel);
        }
        assertTrue(lost.isEmpty(), "these fixtures lost their format-faithful trailing whitespace — "
                + "evidence was rewritten, most likely by a formatter or a gate missing its exclusion: "
                + lost);
    }
}
