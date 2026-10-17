package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * External utility review (owner-approved 2026-10-17): the pack reader forgives FORMAT and refuses only what is UNSAFE or
 * out of bounds. Each common variation is read to the same pack as the canonical file and listed in the gentle note; each
 * unsafe case is still refused with its one cause.
 */
class PromptPackForgivingTest {

    static final String CANONICAL = """
            # Triage
            Questions to start with.

            1. **First breach** — Where did the application first log a breach?
            2. **Quiet nodes** — Which nodes never logged, and does the coverage explain why?
            """;

    static PromptPack.Result parse(String text) {
        return PromptPackTest.parse(text);
    }

    static List<PromptPack.Fix> fixes(PromptPack.Result r) {
        return r.pack().normalised().stream().map(PromptPack.Normalised::fix).distinct().toList();
    }

    @Test
    @DisplayName("the canonical file is read with no fixes and no note")
    void canonicalHasNoNote() {
        var r = parse(CANONICAL);
        assertTrue(r.ok());
        assertTrue(r.pack().normalised().isEmpty());
        assertEquals("", r.pack().normalisedWords());
    }

    /**
     * ★ Each variation reads to the canonical entries and names its fix. Witness: make the reader strict again (match only
     * {@code " — "} after the bold label) — the "-", "--", ":" and en-dash cases are refused as MISSING_LABEL.
     */
    @Test
    @DisplayName("★ '-', '--', '–' or ':' for the em dash, and '**Label:**', are read and noted")
    void otherDashes() {
        for (String sep : List.of(" - ", " -- ", " – ", ": ", " ", "—")) {
            var r = parse(CANONICAL.replace("** — ", "**" + sep));
            assertTrue(r.ok(), sep + ": " + (r.ok() ? "" : r.refusal().words()));
            assertEquals("First breach", r.pack().entries().get(0).label(), sep);
            assertEquals("Where did the application first log a breach?", r.pack().entries().get(0).text(), sep);
            assertEquals(List.of(PromptPack.Fix.DASH), fixes(r), sep);
            assertEquals(List.of(4, 5), r.pack().normalised().stream().map(PromptPack.Normalised::line).toList());
        }
        var colonInBold = parse(CANONICAL.replace("**First breach** —", "**First breach:**"));
        assertTrue(colonInBold.ok());
        assertEquals("First breach", colonInBold.pack().entries().get(0).label());
    }

    /** ★ Witness: refuse an entry with no bold label (the old MISSING_LABEL) — both cases fail. */
    @Test
    @DisplayName("★ a missing bold label: a plain 'Label — text' keeps its label; otherwise the first words become it")
    void missingBoldLabel() {
        var plain = parse(CANONICAL.replace("2. **Quiet nodes** —", "2. Quiet nodes —"));
        assertTrue(plain.ok(), () -> plain.refusal().words());
        assertEquals("Quiet nodes", plain.pack().entries().get(1).label());
        assertEquals("Which nodes never logged, and does the coverage explain why?", plain.pack().entries().get(1).text());
        assertEquals(List.of(PromptPack.Fix.LABEL_FROM_TEXT), fixes(plain));

        var bare = parse(CANONICAL.replace("2. **Quiet nodes** — ", "2. "));
        assertTrue(bare.ok(), () -> bare.refusal().words());
        assertEquals("Which nodes never logged, and does the coverage explain why?", bare.pack().entries().get(1).label(),
                "the first sentence, within the cap");
        assertEquals(bare.pack().entries().get(1).label(), bare.pack().entries().get(1).text());
        String longText = "Read every record between the first quote and the first fill and tell me which node changed the "
                + "spread and why it did so";
        var capped = parse("# T\n1. " + longText + "\n");
        assertTrue(capped.ok());
        String label = capped.pack().entries().get(0).label();
        assertTrue(label.length() <= PromptPack.MAX_LABEL && longText.startsWith(label) && !label.endsWith(" "), label);
        assertEquals(' ', longText.charAt(label.length()), "cut at a word boundary: " + label);
        assertEquals(longText, capped.pack().entries().get(0).text(), "the whole line is still the prompt");
    }

    /** ★ Witness: accept only "N." numbers — the bullet pack is refused as NOT_AN_ENTRY at line 4. */
    @Test
    @DisplayName("★ '-' or '*' bullets are read as 1, 2, 3 …")
    void bullets() {
        for (String bullet : List.of("- ", "* ", "-   ")) {
            var r = parse(CANONICAL.replace("1. ", bullet).replace("2. ", bullet));
            assertTrue(r.ok(), bullet + ": " + (r.ok() ? "" : r.refusal().words()));
            assertEquals(2, r.pack().entries().size());
            assertEquals("Quiet nodes", r.pack().entries().get(1).label());
            assertEquals(List.of(PromptPack.Fix.BULLET), fixes(r));
        }
        var mixed = parse(CANONICAL.replace("2. ", "- "));
        assertTrue(mixed.ok(), "a bullet after a number is the next number");
    }

    @Test
    @DisplayName("extra blank lines, trailing whitespace and a missing description are read")
    void blankLinesTrailingSpaceNoDescription() {
        var blanks = parse("\n\n# Triage\n\n\n\nQuestions to start with.\n\n\n1. **First breach** — a\n\n\n\n2. **Quiet** — b\n\n\n");
        assertTrue(blanks.ok(), () -> blanks.refusal().words());
        assertEquals(2, blanks.pack().entries().size());
        assertTrue(blanks.pack().normalised().isEmpty(), "blank lines change nothing worth a note");

        var trailing = parse("# Triage   \nQuestions.\t\n1. **First breach** — a  \n2. **Quiet** — b\n   \n");
        assertTrue(trailing.ok(), () -> trailing.refusal().words());
        assertEquals("Triage", trailing.pack().title());
        assertEquals("a", trailing.pack().entries().get(0).text());
        assertEquals(List.of(PromptPack.Fix.TRAILING_WHITESPACE), fixes(trailing));
        assertEquals(List.of(1, 2, 3), trailing.pack().normalised().stream().map(PromptPack.Normalised::line).toList(),
                "a whitespace-only line is blank, not a fix");

        var noDescription = parse("# Triage\n- First breach - a\n");
        assertTrue(noDescription.ok());
        assertEquals("", noDescription.pack().description());
    }

    @Test
    @DisplayName("the note lists what was changed, by kind and line, in the analyser's words only")
    void theNote() {
        var r = parse("# Triage \n- **First breach** - a\n- Quiet nodes: b\n");
        assertTrue(r.ok(), () -> r.refusal().words());
        String note = r.pack().normalisedWords();
        assertEquals("Read with small fixes: a \"-\", \"--\", \"–\" or \":\" read as the dash between label and text "
                + "(line 2); an entry with no bold label labelled by its first words (line 3); a \"-\" or \"*\" bullet read "
                + "as the next number (lines 2, 3); trailing spaces removed (line 1).", note);
        assertFalse(note.contains("First breach") || note.contains("Triage"), "never the file's words");
    }

    /**
     * ★ What is unsafe or out of bounds is still refused, through every variation. Witness: drop the loose-example check
     * in the pre-scan — a bullet-framed example line is then refused as LINK, not by name, and its EXAMPLE_MALFORMED
     * assertion fails.
     */
    @Test
    @DisplayName("★ HTML, images, hidden characters, caps, links and loose example entries are still refused")
    void unsafeStillRefused() {
        String url = "https://github.com/telaminai/demo/archive/" + "a".repeat(40) + ".zip";
        record Case(String text, PromptPack.Cause cause) { }
        StringBuilder ten = new StringBuilder("# Ten\n");
        for (int i = 1; i <= 10; i++) ten.append("- L").append(i).append(" - text\n");
        for (Case c : List.of(
                new Case("# T\n- **A** - <script>x</script>\n", PromptPack.Cause.HTML),
                new Case("# T\n- A - ![x](a.png)\n", PromptPack.Cause.IMAGE),
                new Case("# T\n- A - see ‮this\n", PromptPack.Cause.HIDDEN_CHARACTER),
                new Case("# T\n- A - read https://evil.example/ first\n", PromptPack.Cause.LINK),
                new Case("# T\n* [A](https://evil.example) - b\n", PromptPack.Cause.LINK),
                new Case(ten.toString(), PromptPack.Cause.TOO_MANY),
                new Case("# T\n- **" + "L".repeat(61) + "** - b\n", PromptPack.Cause.LABEL_TOO_LONG),
                new Case("# T\n- A - " + "x".repeat(501) + "\n", PromptPack.Cause.TEXT_TOO_LONG),
                new Case("# T\n- **A** — example: " + url + "\n", PromptPack.Cause.EXAMPLE_MALFORMED),
                new Case("# T\n1. **A** - example: " + url + "\n", PromptPack.Cause.EXAMPLE_MALFORMED),
                new Case("# T\n1. A — example: " + url + "\n", PromptPack.Cause.EXAMPLE_MALFORMED),
                new Case("# T\n1. **A** — example: https://github.com/telaminai/demo/archive/main.zip\n",
                        PromptPack.Cause.EXAMPLE_MOVING_REF),
                new Case("# T\n1. **A** — example: " + url + " sha256:abc\n", PromptPack.Cause.EXAMPLE_DIGEST),
                new Case("# T\n1. **A** — example: " + url.replace("github.com", "evil.example") + "\n",
                        PromptPack.Cause.EXAMPLE_HOST),
                new Case("# T\n- **A - b\n", PromptPack.Cause.MISSING_LABEL),
                new Case("# T\n- A - b {log}\n", PromptPack.Cause.PLACEHOLDER))) {
            var r = parse(c.text());
            assertFalse(r.ok(), c.cause() + " was accepted: " + c.text());
            assertEquals(c.cause(), r.refusal().cause(), c.text());
        }
        assertTrue(parse("# T\n1. **A** — example: " + url + "\n").ok(), "control: the strict example shape is accepted");
        assertTrue(PromptPack.parse(new byte[PromptPack.MAX_BYTES + 1]).refusal().cause() == PromptPack.Cause.TOO_LARGE);
    }
}
