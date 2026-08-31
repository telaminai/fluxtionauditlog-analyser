package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule 1, applied to a surface the sweep had never had to cover before: <b>code we did not write.</b>
 *
 * <p>The first regeneration of {@code SessionProcessor} put a real name into this public repository, and
 * the sweep caught it before the commit. The Fluxtion generator stamps every file it emits with a
 * copyright line carrying a personal address on an employer domain — the fourth sweep term — followed by
 * a notice declaring the file confidential. Neither belongs on generated code that its author is
 * expected to check in, which is why the confidentiality half is filed upstream as
 * <a href="https://github.com/telaminai/fluxtion/issues/24">fluxtion#24</a>; the same strip was applied
 * by hand to the demo fixtures long before this milestone.
 *
 * <p><b>By hand is the problem.</b> A regeneration happens on someone else's schedule, months later,
 * possibly by someone who has never read this paragraph — and the header comes back every time. So the
 * check is a test rather than a line in a runbook: regenerate, forget to strip, and the build fails
 * before the commit rather than the sweep failing before a release, or nobody failing at all.
 *
 * <p>The banned terms are <b>read from CLAUDE.md's own sweep line</b>, never spelled here. A test that
 * spelled them would itself be a sweep hit — the M36 review found exactly that — and reading them means
 * this follows the rule when the rule changes. Same mechanism as {@code DemoAssetsTest}.
 */
class GeneratedSourceIsPublishableTest {

    /** Everything the generator wrote and we committed. */
    private static final List<Path> GENERATED = List.of(
            Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.java"),
            Path.of("src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.java"),
            Path.of("src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.graphml"));

    @Test
    @DisplayName("no generated file carries a real name into a public repository")
    void generatedSourcesCarryNoRealNames() throws IOException {
        List<String> banned = rule1SweepTerms();
        assertTrue(banned.size() >= 4,
                "rule 1's sweep line was not found in CLAUDE.md, or has fewer than four terms: " + banned);

        for (Path file : GENERATED) {
            assertTrue(Files.isRegularFile(file), file + " is committed generated output and must exist");
            String body = Files.readString(file).toLowerCase(Locale.ROOT);
            for (String term : banned) {
                assertFalse(body.contains(term),
                        file + " carries a real name the generator stamped on it. Regenerating restores "
                                + "the copyright line; strip it before committing — that is why this "
                                + "test exists rather than a note in the spec.");
            }
        }
    }

    @Test
    @DisplayName("and the analyser's own graph is still the one its nodes describe")
    void theCommittedGraphMatchesTheCommittedProcessor() throws IOException {
        String processor = Files.readString(GENERATED.get(0));
        String graphml = Files.readString(GENERATED.get(2));
        // Cheap, but it catches the failure that matters: a processor regenerated and committed while
        // its GraphML was not, which is the same build-mismatch defect the analyser exists to warn other
        // people about.
        for (String node : new String[]{"operationGate", "sessionBoundary", "effectQueue", "effectOutcomes",
                "activeProject", "openLog", "openGraph"}) {
            assertTrue(processor.contains(node), "processor is missing " + node);
            assertTrue(graphml.contains(node), "GraphML is missing " + node + " — regenerated one and "
                    + "not the other?");
        }
    }

    /** The sweep terms, parsed from CLAUDE.md so they are never written down here. */
    private static List<String> rule1SweepTerms() {
        try {
            for (String line : Files.readAllLines(Path.of("CLAUDE.md"))) {
                Matcher m = Pattern.compile("grep -ri \"([^\"]+)\"").matcher(line);
                if (m.find()) {
                    return Arrays.stream(m.group(1).split("\\\\\\|"))
                            .map(t -> t.trim().toLowerCase(Locale.ROOT))
                            .filter(t -> !t.isEmpty())
                            .toList();
                }
            }
        } catch (IOException e) {
            throw new AssertionError("CLAUDE.md must be readable from the project root", e);
        }
        return new ArrayList<>();
    }
}
