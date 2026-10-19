package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holistic review B8 (2026-10-17): CI's ui-frame job names its frame suites TWICE — the {@code -Dtest} list that runs them
 * and the guard loop that rejects a skipped or empty one — and every {@code *FrameTest} in the source must be in both. A
 * suite in one list only either never runs or is never checked for a silent skip. This is the headless half of
 * {@code tools/verify_project_chart_review.py}'s check, so it fails in {@code mvn test}, not only on the display job.
 */
class CiFrameListsTest {

    static List<String> lists(String ci, Pattern p) {
        Matcher m = p.matcher(ci);
        assertTrue(m.find(), "the list moved in ci.yml: " + p);
        return Arrays.asList(m.group(1).trim().split(p.pattern().contains("-Dtest") ? "," : "\\s+"));
    }

    /** ★ Witness: drop a suite from the guard loop only — "the two lists are identical" fails. */
    @Test
    @DisplayName("★ the ui-frame job's run list and guard list are identical, and hold every *FrameTest in the source")
    void theTwoFrameListsAreOne() throws Exception {
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        List<String> run = lists(ci, Pattern.compile("-Dtest='([^']+)'"));
        List<String> guard = lists(ci, Pattern.compile("for c in (.*?); do"));
        assertEquals(run, guard, "the two lists are identical, in the same order");
        assertEquals(run.size(), new TreeSet<>(run).size(), "no suite listed twice");
        Set<String> source;
        try (var files = Files.walk(Path.of("src/test/java"))) {
            source = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("FrameTest.java"))
                    .map(n -> n.substring(0, n.length() - ".java".length())).collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(source, new TreeSet<>(run), "every *FrameTest in the source is registered, and nothing else");
    }
}
