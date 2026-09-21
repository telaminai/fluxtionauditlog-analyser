package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The published spec's own examples, run through the reader that implements it.
 *
 * <p><b>Why this exists.</b> Two of the first review's findings were documentation drifting from code:
 * the design spec still drew a nested payload after the implementation had gone flat, and §1a's example
 * carried a {@code logTime} the same section told writers to omit. Both were fixed by hand, and under
 * rule 8 a finding is not closed until the check that would catch it next time exists. Re-review
 * suggested exactly this check.
 *
 * <p>An example in a normative specification is a promise. If it does not parse as what the surrounding
 * prose says it is, the specification is wrong in the one place an adapter author is most likely to copy
 * from.
 */
class PublishedSpecExamplesTest {

    private static final Path SPEC = Path.of("docs/site/format-spec.md");
    private static final Path DESIGN = Path.of("docs/specs/spec-audit-stream-end.md");
    private static final Path ASSISTANT = Path.of("docs/site/user-guide/assistant.md");

    /** Every fenced yaml block in a page that contains a stream-end key. */
    private static List<String> markerExamples(Path page) throws IOException {
        String text = Files.readString(page, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("```yaml\\n(.*?)```", Pattern.DOTALL).matcher(text);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            if (m.group(1).contains("streamEnd")) out.add(m.group(1));
        }
        return out;
    }

    /** The example block as the framer would see it, split on separators. */
    private static List<String> records(String block) {
        List<RawRecord> raw = new ArrayList<>();
        RecordFramer.frame(block, raw::add, false);
        return raw.stream().map(RawRecord::text).toList();
    }

    /**
     * Both pages, not just the published one.
     *
     * <p>Re-review found this test would NOT have caught round-one finding 8 — the DESIGN spec still
     * drawing a nested payload after the code went flat — because recognition ran only over
     * {@code format-spec.md}. A design spec that describes a different marker from the one the code
     * reads is the same defect wherever it lives.
     */
    @Test
    void everyPublishedMarkerExampleIsRecognisedAsAMarker() throws IOException {
        List<String> examples = new java.util.ArrayList<>(markerExamples(SPEC));
        assertFalse(examples.isEmpty(), "§1a must show a marker; none found in " + SPEC);
        examples.addAll(markerExamples(DESIGN));
        for (String block : examples) {
            List<String> recs = records(block);
            assertEquals(1, recs.size(), () -> "the example should be one record:\n" + block);
            var marker = StreamEndMarker.of(recs.get(0));
            assertTrue(marker.isPresent(),
                    () -> "the published example does not parse as a marker, so an adapter author "
                            + "copying it would emit an ordinary record:\n" + block);
            assertEquals("normal", marker.get().reason(), block);
            assertTrue(marker.get().records() > 0,
                    () -> "the example should show a usable count:\n" + block);
        }
    }

    /**
     * §1a says a writer SHOULD omit {@code logTime}, measured against released 1.16.0, where a timed
     * marker widened that reader's time range. The example must not do the thing the prose discourages —
     * which it did, and which was fixed by hand with nothing to stop it returning.
     */
    @Test
    void thePublishedExamplesPractiseWhatTheSectionAsks() throws IOException {
        for (Path page : List.of(SPEC, DESIGN)) {
            for (String block : markerExamples(page)) {
                assertFalse(block.contains("logTime"),
                        () -> page + " shows a marker carrying logTime, which the same section tells "
                                + "writers to omit:\n" + block);
            }
        }
    }

    /**
     * Every state the reader can produce has its own ROW in the page's state table.
     *
     * <p>The first review found {@code context}'s docs pointer naming a page that never mentioned the
     * feature. Re-review then found the first version of this check tautological: it searched the whole
     * page, and "complete" and "unknown" are ordinary English that appear in the surrounding prose, so
     * only the three compound names were really guarded. Matching a table row fixes that — a state is
     * explained when it has a line of its own saying what it means, not when its name happens to occur.
     */
    @Test
    void theHumanFacingPageGivesEveryStateItsOwnRow() throws IOException {
        String page = Files.readString(ASSISTANT, StandardCharsets.UTF_8);
        assertTrue(page.contains("log.streamEnd"),
                "the page must name the context key a reader will search for");
        for (StreamEnd.State s : StreamEnd.State.values()) {
            String row = "| `" + s.name().toLowerCase(java.util.Locale.ROOT) + "` |";
            assertTrue(page.contains(row),
                    () -> "state " + row + " can appear in `context` and has no row in " + ASSISTANT
                            + "'s state table. A state a reader meets and cannot look up is worse than "
                            + "no state at all.");
        }
    }

    /**
     * The recognition rule in §1a is a TABLE, and this test cannot read it.
     *
     * <p>Said out loud because re-review's blocker lived exactly there: a reader implemented from the
     * prose disagreed with the analyser on two shapes, and nothing here saw it. What closes that gap is
     * not a cleverer test but the fixtures — `c22-marker-syntax.yaml` pins both shapes — so this asserts
     * the fixtures exist and stay wired, and names the limit for whoever reads this next.
     */
    @Test
    void theRecognitionRulesEdgeCasesArePinnedByFixturesBecauseThisTestCannotReadTheTable() {
        assertTrue(Files.exists(Path.of("src/test/resources/conformance/c22-marker-syntax.yaml")),
                "the syntax edges where prose and code once disagreed must stay pinned by a fixture");
        assertTrue(Files.exists(Path.of("src/test/resources/conformance/c21-real-export.yaml")),
                "and the real producer's own layout must stay pinned by its own bytes");
    }
}
