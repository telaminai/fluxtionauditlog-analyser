package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-in assistant's manifest is the one verb list written by HAND, and it drifted twice in a day: M48.7
 * added a canvas verb and M64 added {@code spotlight}; REST and MCP — derived from {@link VerbSchemas} — offered
 * both, and the built-in assistant was told about neither (review of M48.7, F1; the M48.7 report even claimed
 * the opposite). So this is an INVENTORY, of verbs AND of parameters: M48.7's canvas write now lives on
 * {@code open} as two parameters, and a parameter is exactly as easy to forget as a verb was.
 */
class InProcessManifestNamesEveryVerbTest {

    private static final String MANIFEST = PromptBuilder.inProcessActionManifest(20);

    /** verb → its lines: the {@code "  verb …"} line and the deeper-indented lines that continue it. */
    private static Map<String, String> sections() {
        Map<String, String> out = new LinkedHashMap<>();
        Pattern verbLine = Pattern.compile("^  ([a-z_]+) .*");
        String current = null;
        for (String line : MANIFEST.split("\n")) {
            var m = verbLine.matcher(line);
            if (m.matches()) {
                current = m.group(1);
                assertFalse(out.containsKey(current), "'" + current + "' is described twice");
                out.put(current, line);
            } else if (current != null && line.startsWith("   ")) {
                out.merge(current, line, (a, b) -> a + "\n" + b);
            } else {
                current = null;                                   // prose between the verb lists
            }
        }
        return out;
    }

    @Test
    void everyPublishedVerbHasALineInTheBuiltInAssistantsManifest() {
        assertEquals(VerbSchemas.all().keySet(), sections().keySet().stream()
                        .filter(VerbSchemas.all()::containsKey).collect(java.util.stream.Collectors.toSet()),
                "a verb published to REST and MCP clients that the built-in assistant is never told exists. Describe "
                        + "it by hand in inProcessActionManifest (and add it to HAND_DESCRIBED), or let otherVerbs() derive it");
        for (String verb : PromptBuilder.HAND_DESCRIBED) {
            assertTrue(VerbSchemas.all().containsKey(verb), "'" + verb + "' is hand-described but is not a verb any more");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyPublishedPARAMETERIsNamedInItsVerbsSection_aParameterIsAsEasyToForgetAsAVerb() {
        Map<String, String> sections = sections();
        List<String> missing = new ArrayList<>();
        for (var e : VerbSchemas.all().entrySet()) {
            Object props = ((Map<String, Object>) e.getValue()).get("properties");
            if (!(props instanceof Map<?, ?> m)) continue;
            String section = sections.getOrDefault(e.getKey(), "");
            for (Object param : m.keySet()) {
                if (!declared(section, param.toString())) missing.add(e.getKey() + "." + param);
            }
        }
        assertEquals(List.of(), missing, "published parameters the built-in assistant is never told about");
    }

    /**
     * Is {@code param} DECLARED in this section — written as a parameter, not merely present as a word?
     *
     * <p>The first version asked only for the word, and ordinary prose satisfied it (re-review R6): with
     * {@code to?} deleted from graph's argument list, "from PINs the graph to a fixed window" still passed for
     * {@code graph.to}. A declaration is the name followed by what a parameter is followed by in this
     * manifest's own notation: {@code ?} (optional), {@code []}, or — after optional space — one of
     * {@code : , } ] | = (}. Prose words are followed by other words.
     */
    static boolean declared(String section, String param) {
        return Pattern.compile("(?<![A-Za-z.])" + Pattern.quote(param) + "(?:\\?|\\[\\]|\\s*[:,}\\]|=(])")
                .matcher(section).find();
    }

    @Test
    void aWordInProseIsNotADeclaration_theCounterexampleFromTheReReview() {
        String prose = "  graph  {name, from?} -> from PINs the graph to a fixed window";
        assertFalse(declared(prose, "to"), "'to' appears only as an English word");
        assertTrue(declared(prose, "from"));
        assertTrue(declared("  graph  {name, from?, to?, newTab?}", "to"));
        assertTrue(declared("  read {recordIndex | byteOffset (+ file? on a rolled set) | at (epoch ms)}", "at"));
        assertTrue(declared("  x {a, b} -> first sentence.", "b"), "the derived lines' form: {a, b}");
        assertFalse(declared("  flag {note?} -> what you found and a fix to try", "fix"));
    }

    @Test
    void theSharedCanvasIsTaughtWithItsContract_notJustNamed() {
        String open = sections().get("open");
        assertTrue(open.contains("{posture: research|authoring|derived}"), "posture and its three values");
        assertTrue(open.contains("{record: {branch, modes[]") && open.contains("refused WHOLE"), "the record, all-or-nothing");
        assertTrue(open.contains("{close: \"handoff\"}"), "and how to take it back");
        assertTrue(open.contains("goes ALONE"), "a canvas write is never half of a larger open");
        assertTrue(open.contains("attributed to you"), "a write to shared state says who wrote it");
        assertTrue(open.contains("context.handoff"), "and where to read it back");
        assertFalse(VerbSchemas.all().containsKey("handoff"), "the one-day verb is gone — it is `open` now");
    }
}
