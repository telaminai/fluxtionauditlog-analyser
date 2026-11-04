package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flag kind default change: {@code flag} without a {@code kind} saved a FAULT, so an agent's evidence of a
 * correct refusal was recorded as a fault. The external API's ONLY delta is the {@code flag} schema's {@code kind}
 * gaining a third value, {@code note}, which is now the default for a new finding, plus the wording of the
 * {@code kind}/{@code note}/{@code fix} descriptions and the in-process manifest's flag line that state it. No verb or
 * parameter was added or renamed.
 *
 * <p>This is the LATEST layer of the pin chain: {@link SpotlightLineRangeApiDeltaTest#withoutLineRange} peels it off
 * first, so every older {@code *ApiDeltaTest} reaches its own pins unchanged. {@link #BEFORE} is the state just before
 * this change (the pins {@link WorkModeKeepsTheApiTest#PINNED} held at 1.40.3); {@link #AFTER} is what it holds now.
 *
 * <p>Witness: change any other word of the flag schema, or another verb's schema — the reverted digest no longer
 * matches {@link #BEFORE} and the named surface fails.
 */
class FlagKindApiDeltaTest {

    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "9c13fb070c6c6be1b20e65e1359824264d685494b6e9388fcde8d2727983aa54",
            "VerbSchemas.hosted", "686b70bf0308d32fa7fe640efbe7034c768d4f6284c1ccdf9c6510e0e68c21cc",
            "McpTools.list", "be6bcdfa779d941fe0f6f2ba2a62fa76040daf11bee3a36b0d230e28835488aa",
            "McpTools.hostedList", "54c1cd7bbd4304dc0277584f7e1eaef1ec0c2ed2fdab209f2b32e2ecdf5be6cd",
            "PromptBuilder.inProcessActionManifest", "8dae33cf340c384a1bd16508e8b4fe05c3e9a45130003c08459a675d9f782979");

    static final Map<String, String> AFTER = Map.of(
            "VerbSchemas.all", "1200748e764954a8ac229ae9d5c4297000a361e44e7bf8a6259dec70560c1c12",
            "VerbSchemas.hosted", "5218b99c3052899b848104c9c55c6cc523a51f3201d044d194308369a974c57f",
            "McpTools.list", "bfdb1203d7486c5ff0cf1e6d19b8648c5cbac2d8203cee6d8c8c880cf0901872",
            "McpTools.hostedList", "413f8954eb879f9e0e1e0077db9701a965a4f01aff63c23f8a52675ce8e46885",
            "PromptBuilder.inProcessActionManifest", "edbf0498f259e609e7e7b8f03eaad26106df929d008e9232fc31001899a14a46");

    /** Each edited text, new → old. */
    static final Map<String, String> TEXT = Map.of(
            "finding category: 'fault' for behaviour that is wrong (What is wrong / Likely cause), 'confirmation' for "
                    + "behaviour that is correct, e.g. a correct refusal (Observation / Assessment), 'note' for a neutral "
                    + "record of evidence (Note / Assessment). Omitted: a NEW finding is saved as 'note' — never as a fault; "
                    + "an existing finding keeps its category. Say 'fault' only when the record shows something wrong.",
            "finding category; new findings default to fault. Omission preserves an existing category. Confirmation "
                    + "labels are Observation / Assessment.",
            "what is wrong (fault), what you observed (confirmation), or the note itself (note)",
            "what you observed (confirmation), or what is wrong (fault)",
            "likely cause / suggested fix (fault), or your assessment (confirmation, note)",
            "your assessment (confirmation), or likely cause / suggested fix (fault)",
            "          kind: note (the default for a new finding; neutral), fault (wrong behavior) or confirmation "
                    + "(correct behavior); note/fix label Note/Assessment for note, Observation/Assessment for confirmation; "
                    + "omissions keep existing fields\n",
            "          kind: fault (default) or confirmation; note/fix label Observation/Assessment for confirmation; "
                    + "omissions keep existing fields\n");

    static final List<String> NEW_KINDS = List.of("fault", "confirmation", "note");
    static final List<String> OLD_KINDS = List.of("fault", "confirmation");

    /** Remove only this change, recursively over any surface shape (String/Map/List). */
    @SuppressWarnings("unchecked")
    static <T> T withoutFlagKind(T value) {
        if (value instanceof String text) {
            for (var e : TEXT.entrySet()) text = text.replace(e.getKey(), e.getValue());
            return (T) text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> out.put(key, withoutFlagKind(entry)));
            return (T) out;
        }
        if (value instanceof List<?> list) {
            if (list.equals(NEW_KINDS)) return (T) OLD_KINDS;
            return (T) list.stream().map(FlagKindApiDeltaTest::withoutFlagKind).toList();
        }
        return value;
    }

    @Test
    @DisplayName("★ flag gains only the neutral 'note' kind and its wording: undone, every surface is as before")
    void theOnlyDeltaIsTheNeutralFlagKind() throws Exception {
        Set<String> changed = new TreeSet<>();
        for (var surface : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            String key = surface.getKey();
            String reverted = WorkModeKeepsTheApiTest.sha(withoutFlagKind(surface.getValue()));
            String live = WorkModeKeepsTheApiTest.sha(surface.getValue());
            if (BEFORE.containsKey(key)) {
                assertEquals(BEFORE.get(key), reverted, "only the flag-kind delta may change: " + key);
                assertEquals(AFTER.get(key), live, "the pin now: " + key);
                assertEquals(AFTER.get(key), WorkModeKeepsTheApiTest.PINNED.get(key), "PINNED holds it: " + key);
            }
            if (!live.equals(reverted)) changed.add(key);
        }
        assertEquals(BEFORE.keySet(), changed, "no unrelated API surface moves");
    }

    @Test
    @DisplayName("kind gains one value; no verb or flag parameter was added")
    void theKindIsAdditiveOnly() {
        assertEquals(18, VerbSchemas.all().size(), "no verb was added");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) VerbSchemas.all().get("flag")).get("properties");
        assertEquals(Set.of("byteOffsets", "recordIndexes", "kind", "note", "fix"), props.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> kind = (Map<String, Object>) props.get("kind");
        assertEquals(NEW_KINDS, kind.get("enum"));
    }
}
