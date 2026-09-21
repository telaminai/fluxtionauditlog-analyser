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

    @Test
    void thePublishedMarkerExampleIsRecognisedAsAMarker() throws IOException {
        List<String> examples = markerExamples(SPEC);
        assertFalse(examples.isEmpty(), "§1a must show a marker; none found in " + SPEC);
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
     * Every state the reader can produce is named on the page {@code context}'s own comment points at.
     * The first review found that pointer naming a page which never mentioned the feature.
     */
    @Test
    void theHumanFacingPageNamesEveryStateTheReaderCanReport() throws IOException {
        String page = Files.readString(ASSISTANT, StandardCharsets.UTF_8);
        assertTrue(page.contains("log.streamEnd"),
                "the page must name the context key a reader will search for");
        for (StreamEnd.State s : StreamEnd.State.values()) {
            String wire = s.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(page.contains(wire),
                    () -> "state '" + wire + "' can appear in `context` and is not explained in "
                            + ASSISTANT + ". A state a reader meets and cannot look up is worse than "
                            + "no state at all.");
        }
    }
}
