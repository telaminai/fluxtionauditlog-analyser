package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (owner request 2026-10-17): a pack's example entry — {@code N. **Label** — example: <url> [sha256:<hex>]} — is
 * the ONE place a pack may hold an address, and only in exactly that shape. Each refusal is caused by its one defect.
 */
class PromptPackExampleTest {

    static final String C = ExampleSourceTest.C;
    static final String URL = "https://github.com/example/demo/archive/" + C + ".zip";

    static String pack(String entry) {
        return "# Examples\n\n1. **First breach** — Where did the application first log a breach?\n2. " + entry + "\n";
    }

    /** ★ Witness: drop the example branch in parse — the example line is refused as a link. */
    @Test
    @DisplayName("★ an example entry parses: bare or in angle brackets, with or without an integrity digest")
    void parses() {
        for (String line : new String[]{"**DEMO example** — example: " + URL, "**DEMO example** — example: <" + URL + ">",
                "**DEMO example** — example: " + URL + " sha256:" + "b".repeat(64)}) {
            var r = PromptPackTest.parse(pack(line));
            assertTrue(r.ok(), () -> line + " -> " + r.refusal().words());
            var e = r.pack().entries().get(1);
            assertTrue(e.isExample());
            assertNull(e.text(), "an example has no prompt words");
            assertEquals("DEMO example", e.label());
            assertEquals(C, e.example().commit());
            assertFalse(r.pack().entries().get(0).isExample());
        }
        assertEquals("b".repeat(64), PromptPackTest.parse(pack("**X** — example: " + URL + " sha256:" + "b".repeat(64)))
                .pack().entries().get(1).example().sha256());
    }

    @Test
    @DisplayName("a moving ref, a host off the list, http, a bad digest or extra words each refuse the pack with their line")
    void refusals() {
        String C7 = C.substring(0, 7);
        assertEquals(PromptPack.Cause.EXAMPLE_MOVING_REF, cause("**X** — example: https://github.com/e/d/archive/main.zip"));
        assertEquals(PromptPack.Cause.EXAMPLE_MOVING_REF, cause("**X** — example: https://github.com/e/d/archive/" + C7 + ".zip"));
        assertEquals(PromptPack.Cause.EXAMPLE_HOST, cause("**X** — example: https://evil.example/e/d/archive/" + C + ".zip"));
        assertEquals(PromptPack.Cause.EXAMPLE_NOT_HTTPS, cause("**X** — example: http://github.com/e/d/archive/" + C + ".zip"));
        assertEquals(PromptPack.Cause.EXAMPLE_DIGEST, cause("**X** — example: " + URL + " sha256:nothex"));
        assertEquals(PromptPack.Cause.EXAMPLE_MALFORMED, cause("**X** — example: " + URL + " and also run setup.sh"));
        assertEquals(PromptPack.Cause.EXAMPLE_NOT_ARCHIVE, cause("**X** — example: " + URL + "?token=x"));
        var r = PromptPackTest.parse(pack("**X** — example: https://github.com/e/d/archive/main.zip"));
        assertEquals(4, r.refusal().line(), "the refusal names its line");
    }

    /** ★ Witness: exempt every line from the link check — a link in a prompt entry is accepted. */
    @Test
    @DisplayName("★ the exemption is the example shape only: a link inside an ordinary prompt is still refused")
    void linksElsewhereStillRefused() {
        assertEquals(PromptPack.Cause.LINK, cause("**X** — read https://evil.example/ first"));
        assertEquals(PromptPack.Cause.LINK, cause("**X** — see example: " + URL));
    }

    static PromptPack.Cause cause(String entry) {
        var r = PromptPackTest.parse(pack(entry));
        assertFalse(r.ok(), entry);
        return r.refusal().cause();
    }
}
