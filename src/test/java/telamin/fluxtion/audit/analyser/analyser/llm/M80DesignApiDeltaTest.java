package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Owner-approved M80 metadata delta: one spotlight synonym and the Java-builder selector descriptions only. */
class M80DesignApiDeltaTest {
    static final String OLD_TARGET = "source:design:bean:<id>";
    static final String NEW_TARGET = "source:design:bean:<id>|source:design:node:<id>";
    static final String ADDED_DESCRIPTION = "The bean selector also matches literal Java-builder node names. ";
    static final String OLD_BEAN = "Bean id in the session design, or the specified XML file; duplicates are refused";
    static final String NEW_BEAN = "Bean id in XML, or literal node name in a Java builder; uses the session design or specified file; duplicates are refused";

    // Literal pins BEFORE M80. Never derived from the updated WorkModeKeepsTheApiTest pins.
    static final Map<String, String> BEFORE = Map.ofEntries(
            Map.entry("VerbSchemas.all", "7c0cfc883f7b0dbb52a94f972f4b676d0cdeb7541ae40d30a4096459e5e93772"),
            Map.entry("VerbSchemas.hosted", "e939a7ab31794f339718b9cf03e9d2b31c8257fee288eab5293039f86f5d40ce"),
            Map.entry("McpTools.list", "ee123c503dc3225505b0bacccb0f24335617bf0698a138801cfc6ea76f4c79b3"),
            Map.entry("McpTools.hostedList", "e4d2cb45af519154c41289bc5d564bc183bc8ac202681f13bc88253d667cc251"),
            Map.entry("McpBridge.INSTRUCTIONS", "fdcf8b03dab8d45d9c197d230961a41518faa0319b6f5a9cc6a51662cfdf144a"),
            Map.entry("PromptBuilder.inProcessActionManifest", "01a161d22a7ea6dca8d83a0e4a0446d68236028603112c7597b989b49d3cae7e"),
            Map.entry("PromptBuilder.restActionManifest", "b6a4c625c3ecba2fa55bc9001d97fe71ab53ffd899c8c4a37e4850178e99aea8"),
            Map.entry("context.handoff derived, project", "591833ea9ec7b553f868403da4ba5d909b2e45b628c93b43fd70dcb170426bb1"),
            Map.entry("context.handoff derived, none", "1623b4af915aef11455cfc7a7587804fa90504f869dde67de3f5177393a04679"),
            Map.entry("context.handoff set research by you", "8037233aed4d5153383d60bb0e73e2924a0828785867e83a5d54be0becc493d1"),
            Map.entry("context.handoff set authoring by socket", "6f810064351561ccd3026f4ca1f2d062e26540d4eb5d8d01dad6c05d8fedfe32"));

    /** Remove only the three literal edits. No field, whole description, schema or parameter is replaced. */
    @SuppressWarnings("unchecked")
    static <T> T withoutM80(T value) {
        if (value instanceof String text) return (T) text.replace(NEW_TARGET, OLD_TARGET)
                .replace(ADDED_DESCRIPTION, "").replace(NEW_BEAN, OLD_BEAN);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> out.put(key, withoutM80(entry)));
            return (T) out;
        }
        if (value instanceof List<?> list) return (T) list.stream().map(M80DesignApiDeltaTest::withoutM80).toList();
        return value;
    }

    @Test
    void undoingOnlyTheApprovedMetadataRestoresEveryPreviousHash() throws Exception {
        Set<String> changed = new TreeSet<>();
        for (var surface : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            assertEquals(BEFORE.get(surface.getKey()), WorkModeKeepsTheApiTest.sha(withoutM80(surface.getValue())),
                    "only the approved M80 delta may change: " + surface.getKey());
            if (!BEFORE.get(surface.getKey()).equals(WorkModeKeepsTheApiTest.sha(surface.getValue()))) changed.add(surface.getKey());
        }
        assertEquals(Set.of("VerbSchemas.all", "VerbSchemas.hosted", "McpTools.list", "McpTools.hostedList",
                "PromptBuilder.inProcessActionManifest"), changed, "no unrelated API surface moves");
    }

    @Test
    void nodeIsAnAdvertisedSynonymNotANewFamilyOrParameter() {
        assertEquals(NEW_TARGET, SpotlightTarget.Family.DESIGN_BEAN.form());
        assertEquals(SpotlightTarget.parse("source:design:bean:demoNode").target().family(),
                SpotlightTarget.parse("source:design:node:demoNode").target().family());
        assertTrue(SpotlightVocabulary.TEXT.contains(NEW_TARGET));
        String schema = Json.write(VerbSchemas.all().get("source"));
        assertTrue(schema.contains(ADDED_DESCRIPTION.strip()));
        assertTrue(schema.contains(NEW_BEAN));
    }
}
