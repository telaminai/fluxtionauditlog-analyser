package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.8 "A dropped prompt pack", owner request 2026-10-17): the strict Markdown grammar — what it accepts and
 * each refusal with its line. Every refusal has its witness: the case is refused only because of the one thing it breaks
 * (the same file without it is accepted, {@link #eachRefusalIsCausedByItsOneDefect}).
 */
class PromptPackTest {

    static final String GOOD = """
            # Incident triage
            Three questions our on-call asks first.

            1. **First breach** — Where did the application first log a breach, and what preceded it?
            2. **Quiet nodes** — Which nodes never logged in this log, and does the coverage verdict explain why?
            3. **Spread** — How does the spread move across the window, and where does it jump?
            """;

    static PromptPack.Result parse(String text) {
        return PromptPack.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a valid pack: its title, description and entries, in order")
    void accepts() {
        var r = parse(GOOD);
        assertTrue(r.ok(), () -> r.refusal().words());
        assertEquals("Incident triage", r.pack().title());
        assertEquals("Three questions our on-call asks first.", r.pack().description());
        assertEquals(3, r.pack().entries().size());
        assertEquals("Quiet nodes", r.pack().entries().get(1).label());
        assertTrue(r.pack().entries().get(2).text().startsWith("How does the spread"));
        assertTrue(parse("# T\n1. **A** — b\n").ok(), "the description is optional");
        assertEquals(PromptPack.Cause.HIDDEN_CHARACTER, parse("\uFEFF# T\n1. **A** — b\n").refusal().cause(),
                "a byte-order mark is a hidden character: refused, never silently dropped");
        assertFalse(r.pack().toString().contains("Incident"), "never printed with its words");
    }

    /**
     * ★ Each refusal is caused by its one defect: the defect alone added to {@link #GOOD} is refused with that cause and
     * line. Witness: drop any one check from the parser — its case is then accepted and this fails at that case.
     */
    @Test
    @DisplayName("★ oversize, ten entries, HTML, a missing bold label, extra content — each refused with one named cause")
    void eachRefusalIsCausedByItsOneDefect() {
        record Case(String text, PromptPack.Cause cause, int line) { }
        StringBuilder ten = new StringBuilder("# Ten\n");
        for (int i = 1; i <= 10; i++) ten.append(i).append(". **L").append(i).append("** — text\n");
        var cases = new Case[]{
                new Case("# Big\n1. **A** — " + "x".repeat(PromptPack.MAX_BYTES), PromptPack.Cause.TOO_LARGE, 0),
                new Case(ten.toString(), PromptPack.Cause.TOO_MANY, 11),
                new Case(GOOD.replace("asks first.", "asks <b>first</b>."), PromptPack.Cause.HTML, 2),
                new Case(GOOD.replace("2. **Quiet nodes** —", "2. **Quiet nodes —"), PromptPack.Cause.MISSING_LABEL, 5),
                new Case(GOOD + "\nAnd one more paragraph.\n", PromptPack.Cause.EXTRA_CONTENT, 8),
                new Case(GOOD.replace("# Incident triage", "Incident triage"), PromptPack.Cause.NO_TITLE, 1),
                new Case(GOOD.replace("# Incident triage", "# " + "T".repeat(61)), PromptPack.Cause.TITLE_TOO_LONG, 1),
                new Case(GOOD.replace("Three questions our on-call asks first.", "D".repeat(141)),
                        PromptPack.Cause.DESCRIPTION_TOO_LONG, 2),
                new Case(GOOD.replace("**First breach**", "**" + "L".repeat(61) + "**"), PromptPack.Cause.LABEL_TOO_LONG, 4),
                new Case(GOOD.replace("what preceded it?", "x".repeat(501)), PromptPack.Cause.TEXT_TOO_LONG, 4),
                new Case(GOOD.replace("2. **Quiet", "3. **Quiet"), PromptPack.Cause.OUT_OF_ORDER, 5),
                new Case(GOOD.replace("asks first.", "asks ![x](a.png)."), PromptPack.Cause.IMAGE, 2),
                new Case(GOOD.replace("asks first.", "asks [first](https://example.org)."), PromptPack.Cause.LINK, 2),
                new Case(GOOD.replace("what preceded it?", "what preceded it in {log}?"), PromptPack.Cause.PLACEHOLDER, 4),
                new Case(GOOD.replace("asks first.", "asks ‮first."), PromptPack.Cause.HIDDEN_CHARACTER, 2),
                new Case("# Empty\nNo entries here.\n", PromptPack.Cause.NO_ENTRIES, 3),
                new Case("# T\n1. **A** — \n", PromptPack.Cause.EMPTY_TEXT, 2),
        };
        for (Case c : cases) {
            var r = parse(c.text());
            assertFalse(r.ok(), c.cause() + " was accepted");
            assertEquals(c.cause(), r.refusal().cause(), c.text());
            assertEquals(c.line(), r.refusal().line(), c.cause() + ": " + r.refusal().words());
        }
        assertEquals(PromptPack.Cause.NOT_UTF8, PromptPack.parse(new byte[]{'#', ' ', (byte) 0xC3, (byte) 0x28}).refusal().cause());
        assertEquals("line 11 — a pack has at most 9 entries", parse(ten.toString()).refusal().words());
    }

    @Test
    @DisplayName("the sample pack the docs ship is itself a valid pack")
    void theDocsSampleIsValid() throws Exception {
        Path sample = Path.of("docs/site/user-guide/sample-prompt-pack.md");
        var r = PromptPack.parse(Files.readAllBytes(sample));
        assertTrue(r.ok(), () -> r.refusal().words());
        assertTrue(r.pack().entries().size() >= 3);
    }
}
